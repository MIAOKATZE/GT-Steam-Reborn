package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.ProbeSupport.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.util.IIcon;
import com.miaokatze.gtsr.client.critical.CriticalMaterialTextures;

/** Real Forge registrations and production icon selection; no GL or complete mod lifecycle. */
public final class MaterialsProbe {
    static IIcon icon(String name) {
        return (IIcon) Proxy.newProxyInstance(IIcon.class.getClassLoader(), new Class[] { IIcon.class }, (p,m,a) -> {
            if (m.getName().equals("getIconName")) return name;
            if (m.getName().equals("hashCode")) return name.hashCode();
            if (m.getReturnType() == int.class) return 16;
            if (m.getReturnType() == float.class) return 0F;
            return null;
        });
    }
    public static void main(String[] args) throws Exception {
        bootstrap();
        set(gregtech.GTMod.proxy,"mCTMBlockCache",new gregtech.api.util.GTBlockMap<Boolean>());
        cpw.mods.fml.common.Loader loader=cpw.mods.fml.common.Loader.instance();
        cpw.mods.fml.common.ModMetadata data=new cpw.mods.fml.common.ModMetadata();
        data.modId="gtsr";data.name="GTSR materials fixture";data.version="1.21.46";
        set(get(loader,"modController"),"activeContainer",new cpw.mods.fml.common.DummyModContainer(data));
        CriticalMaterials.register();
        JsonArray blocks=new JsonArray(), mismatches=new JsonArray(), earlyNulls=new JsonArray();
        List<CriticalMaterials.MaterialBlock> materials=new ArrayList<>();
        for(Object obj:Block.blockRegistry) if(obj instanceof CriticalMaterials.MaterialBlock b) materials.add(b);
        for(CriticalMaterials.MaterialBlock b:materials) {
            b.registerBlockIcons(name -> icon(name));
            if(b.materialId.startsWith("v2_")) for(int side=0;side<6;side++) if(b.getIcon(side,0)==null)
                earlyNulls.add(new JsonPrimitive(b.materialId+":"+side));
        }
        int exposed=0, missing=0, iconChecks=0;
        Set<String> exposedNames=new TreeSet<>(), expectedNames=new TreeSet<>();
        JsonObject currentLoom=CriticalGeometry.readJson("models/v2_loom.geometry.json");
        for(JsonElement element:currentLoom.getAsJsonArray("blocks")) {
            JsonArray voxel=element.getAsJsonArray();
            boolean dynamic=currentLoom.getAsJsonObject("animationGroups").has(voxel.get(4).getAsString());
            expectedNames.add("gtsr:critical_loom_"+(dynamic?"proxy_":"")+CriticalMaterials.properties(voxel.get(3).getAsString()).get("id").getAsString());
        }
        for(CriticalMaterials.MaterialBlock b:materials) {
            List<ItemStack> variants=new ArrayList<>();
            Item item=Item.getItemFromBlock(b);
            item.getSubItems(item,null,variants);
            exposed+=variants.size();
            for(ItemStack variant:variants) exposedNames.add(Item.itemRegistry.getNameForObject(variant.getItem()));
            JsonObject row=new JsonObject();row.addProperty("registry",Block.blockRegistry.getNameForObject(b));
            row.addProperty("numericBlockId",Block.getIdFromBlock(b));row.addProperty("numericItemId",Item.getIdFromItem(item));
            row.addProperty("id",b.materialId);row.addProperty("proxy",b.proxy);row.addProperty("loom",b.loom);
            row.addProperty("key",new ItemStack(b).getUnlocalizedName()+".name");
            row.addProperty("enumerated",variants.size());blocks.add(row);
            if(!b.materialId.startsWith("v2_"))continue;
            JsonObject props=CriticalMaterials.properties(b.materialId);
            for(int side=0;side<6;side++)for(int mask=0;mask<16;mask++)for(boolean frozen:new boolean[]{false,true})for(boolean emission:new boolean[]{false,true}) {
                String face=CriticalMaterialTextures.FACE_NAMES[side],kind=frozen?(emission?"frozenEmission":"frozenAlbedo"):(emission?"emission":"albedo");
                JsonObject pair;
                if(props.has("ctm")) {
                    JsonObject ctm=props.getAsJsonObject("ctm");String group=ctm.getAsJsonObject("faceLookup").get(face).getAsString();
                    int allowed=ctm.getAsJsonObject("faceAllowedMask").get(face).getAsInt();
                    pair=ctm.getAsJsonObject("sprites").getAsJsonObject(group).getAsJsonObject(""+(mask&allowed));
                } else if(props.has("faceSprites")) {
                    JsonObject link=props.getAsJsonObject("faceSprites").getAsJsonObject(face);
                    pair=CriticalMaterials.properties("v2_"+link.get("material").getAsString()).getAsJsonObject("sprites").getAsJsonObject(link.get("group").getAsString());
                } else pair=props.getAsJsonObject("sprites").getAsJsonObject(side<2?"top":"side");
                JsonElement expected=pair.get(kind);String wanted=expected==null||expected.isJsonNull()?null:expected.getAsString();
                IIcon actual=CriticalMaterialTextures.icon(b.materialId,side,mask,emission,frozen);String got=actual==null?null:actual.getIconName();
                iconChecks++;
                if(!Objects.equals(wanted,got)){JsonObject failure=new JsonObject();failure.addProperty("id",b.materialId);failure.addProperty("side",side);failure.addProperty("mask",mask);failure.addProperty("kind",kind);failure.addProperty("expected",wanted);failure.addProperty("actual",got);mismatches.add(failure);}
                if(!emission&&actual==null) missing++;
            }
        }
        int registered=materials.size(), creativeBefore=com.miaokatze.gtsr.register.CreativeTabManager.getItemsToAdd().size();
        CriticalMaterials.register();int after=0;
        for(Object obj:Block.blockRegistry)if(obj instanceof CriticalMaterials.MaterialBlock)after++;
        check(registered==170&&after==registered,"170 stable unique Forge material registrations; repeated init is idempotent");
        check(creativeBefore==com.miaokatze.gtsr.register.CreativeTabManager.getItemsToAdd().size(),"repeated registration does not duplicate creative entries");
        check(missing==0,"all V2 exposed face/mask/frozen albedos resolve after complete stitch");
        Set<String> creativeNames=new TreeSet<>();
        for(ItemStack stack:com.miaokatze.gtsr.register.CreativeTabManager.getItemsToAdd()) creativeNames.add(Item.itemRegistry.getNameForObject(stack.getItem()));
        boolean whitelist=exposed==22&&exposedNames.equals(expectedNames)&&creativeBefore==22&&creativeNames.equals(expectedNames);
        for(Map.Entry<String,JsonElement> entry:CriticalGeometry.readJson("materials.json").entrySet()) for(boolean dynamic:new boolean[]{false,true})
            check(CriticalMaterials.block(entry.getKey(),dynamic)==CriticalMaterials.block(entry.getValue().getAsJsonObject().get("id").getAsString(),dynamic),"canonical alias identity "+entry.getKey()+":"+dynamic);
        check(gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId(15872)!=null&&gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId(15873)!=null,"legacy and V2 controller casing texture slots initialize");
        try { com.miaokatze.gtsr.client.critical.CriticalMaterialRenderer.class.getMethod("hideInternalItems").invoke(null); }
        catch(NoSuchMethodException baseline) { /* preserved pre-fix product has no explicit NEI filtering */ }
        Method permutations=codechicken.nei.ItemListLoader.class.getDeclaredMethod("getPermutations",Item.class);permutations.setAccessible(true);
        Set<String> neiNames=new TreeSet<>();int neiCount=0;
        for(CriticalMaterials.MaterialBlock b:materials) {
            List<ItemStack> stacks=(List<ItemStack>)permutations.invoke(null,Item.getItemFromBlock(b));neiCount+=stacks.size();
            for(ItemStack stack:stacks)neiNames.add(Item.itemRegistry.getNameForObject(stack.getItem()));
        }
        boolean neiWhitelist=neiCount==22&&neiNames.equals(expectedNames);
        JsonObject result=new JsonObject();result.addProperty("status",mismatches.size()==0&&whitelist&&neiWhitelist?"PASS":"REPRODUCED");
        result.addProperty("registered",registered);result.addProperty("enumerated",exposed);result.addProperty("creative",com.miaokatze.gtsr.register.CreativeTabManager.getItemsToAdd().size());
        result.addProperty("iconChecks",iconChecks);result.addProperty("checks",checks);result.addProperty("product",source(CriticalMaterials.class));result.addProperty("textures",source(CriticalMaterialTextures.class));
        result.add("blocks",blocks);result.add("iconMismatches",mismatches);result.add("transientRegistrationNulls",earlyNulls);
        result.addProperty("exactCurrentLoomWhitelist",whitelist);
        result.addProperty("neiEnumerated",neiCount);result.addProperty("exactNeiWhitelist",neiWhitelist);result.addProperty("NEI",source(codechicken.nei.ItemListLoader.class));
        result.addProperty("boundary","Actual Forge GameRegistry + native ItemBlock.getSubItems + NEI 2.8.145 ItemListLoader.getPermutations (damageSearch and hidden filters) and production icon lookup; mock named IIcons, FML/StructureLib seams. No complete client atlas/GL or screenshot.");
        Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes("UTF-8"));
    }
}
