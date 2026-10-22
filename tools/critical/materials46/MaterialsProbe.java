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
    static void verifyPositionOwnership() throws Exception {
        net.minecraft.client.multiplayer.WorldClient client=(net.minecraft.client.multiplayer.WorldClient)U.allocateInstance(net.minecraft.client.multiplayer.WorldClient.class);set(client,"isRemote",true);
        set(net.minecraft.client.Minecraft.getMinecraft(),"theWorld",client);
        CriticalGeometry.Voxel voxel=null;
        for(CriticalGeometry.Voxel candidate:CriticalGeometry.getModel("v2_loom").voxels)
            if(CriticalGeometry.isDynamic(candidate.model,candidate.group)
                && !((CriticalMaterials.MaterialBlock)candidate.block).transparent) {voxel=candidate;break;}
        check(voxel!=null,"ownership fixture selects a moving opaque loom cell");
        final Block material=voxel.block;
        net.minecraft.world.IBlockAccess access=(net.minecraft.world.IBlockAccess)Proxy.newProxyInstance(
            MaterialsProbe.class.getClassLoader(),new Class[]{net.minecraft.world.IBlockAccess.class},(proxy,method,args)->{
                if(method.getName().equals("getBlock"))return material;
                if(method.getReturnType()==int.class)return 0;
                if(method.getReturnType()==boolean.class)return false;
                return null;
            });
        check(!CriticalMaterials.isAnimatedAt(access,1,64,1),"unowned canonical material remains static");
        Class<?> registrationClass=Class.forName(CriticalLoomProxyVisibility.class.getName()+"$Registration");
        Constructor<?> registrationConstructor=registrationClass.getDeclaredConstructors()[0];registrationConstructor.setAccessible(true);
        Object registration=registrationConstructor.newInstance(client,0,0,0,0,0,net.minecraftforge.common.util.ForgeDirection.NORTH,"fixture",Collections.singletonList(voxel));
        set(registration,"activeGroups",Collections.singleton(voxel.model+":"+voxel.group));
        TileEntityCriticalRenderAnchor anchor=new TileEntityCriticalRenderAnchor();anchor.setWorldObj(client);
        net.minecraft.nbt.NBTTagCompound descriptor=new net.minecraft.nbt.NBTTagCompound();descriptor.setBoolean("loomVisible",true);set(anchor,"descriptor",descriptor);
        Class<?> entryClass=Class.forName(CriticalLoomProxyVisibility.class.getName()+"$Entry");Constructor<?> entryConstructor=entryClass.getDeclaredConstructors()[0];entryConstructor.setAccessible(true);
        Object entry=entryConstructor.newInstance(anchor,voxel,registration);
        Map<Long,Object> positions=new HashMap<>();positions.put(64L<<8|1L<<4|1L,entry);
        Map<Long,Map<Long,Object>> chunks=new HashMap<>();chunks.put(0L,positions);
        Map worlds=(Map)get(CriticalLoomProxyVisibility.class,"WORLDS");worlds.put(client,chunks);
        check(CriticalMaterials.isAnimatedAt(access,1,64,1),"owned canonical cell moves only after its group is published");
        check(!CriticalMaterials.isAnimatedAt(access,2,64,1),"same registered block stays static at an unowned coordinate");
        check(!CriticalLoomProxyVisibility.animated(client,1,64,1,material,1),"wrong metadata cannot hide a world cell");
        CriticalMaterials.MaterialBlock block=(CriticalMaterials.MaterialBlock)material;
        check(block.shouldSideBeRendered(access,1,64,1,2),"static neighbor keeps its exposed face next to an owned moving cell");
        check(!block.shouldSideBeRendered(access,2,64,1,2),"static opaque material neighbor remains culled");
        set(registration,"activeGroups",Collections.emptySet());
        check(!CriticalMaterials.isAnimatedAt(access,1,64,1),"unpublished animation group restores canonical world cell");
        worlds.remove(client);set(net.minecraft.client.Minecraft.getMinecraft(),"theWorld",null);
    }
    public static void main(String[] args) throws Exception {
        bootstrap();
        set(gregtech.GTMod.proxy,"mCTMBlockCache",new gregtech.api.util.GTBlockMap<Boolean>());
        cpw.mods.fml.common.Loader loader=cpw.mods.fml.common.Loader.instance();
        cpw.mods.fml.common.ModMetadata data=new cpw.mods.fml.common.ModMetadata();
        data.modId="gtsr";data.name="GTSR materials fixture";data.version="unified-materials";
        set(get(loader,"modController"),"activeContainer",new cpw.mods.fml.common.DummyModContainer(data));
        CriticalMaterials.register();
        com.google.common.collect.ListMultimap<String,cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping> missingMappings=com.google.common.collect.ArrayListMultimap.create();
        Set<String> removed=(Set<String>)get(CriticalMaterials.class,"REMOVED_REGISTRATIONS");
        check(removed.size()==105,"only the 105 removed legacy registry names are ignored");
        int removedId=1000;
        for(String name:removed) {
            for(char type:new char[]{1,2}) missingMappings.put("gtsr",new cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping(type+name,removedId));
            removedId++;
        }
        for(String name:new String[]{"gtsr:critical_v2_reinforced_panel","gtsr:critical_proxy_unknown","gtsr:unrelated_missing"})
            missingMappings.put("gtsr",new cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping("\1"+name,1234));
        cpw.mods.fml.common.event.FMLMissingMappingsEvent missingEvent=new cpw.mods.fml.common.event.FMLMissingMappingsEvent(missingMappings);
        missingEvent.applyModContainer(new cpw.mods.fml.common.DummyModContainer(data));
        CriticalMaterials.ignoreRemovedMappings(missingEvent);
        for(cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping mapping:missingEvent.get())
            check(mapping.getAction()==(removed.contains(mapping.name)?cpw.mods.fml.common.event.FMLMissingMappingsEvent.Action.IGNORE:cpw.mods.fml.common.event.FMLMissingMappingsEvent.Action.DEFAULT),
                "targeted removed mapping action "+mapping.type+":"+mapping.name);
        List<cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping> ignoredMappings=new ArrayList<>();
        for(cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping mapping:missingEvent.get())
            if(removed.contains(mapping.name))ignoredMappings.add(mapping);
        Constructor<cpw.mods.fml.common.registry.GameData> gameDataConstructor=cpw.mods.fml.common.registry.GameData.class.getDeclaredConstructor();gameDataConstructor.setAccessible(true);
        cpw.mods.fml.common.registry.GameData scratch=gameDataConstructor.newInstance();
        check(cpw.mods.fml.common.registry.GameData.processIdRematches(ignoredMappings,true,scratch,new HashMap<>()).isEmpty(),
            "actual Forge missing-ID processing accepts all intentional removals without default confirmation");
        check(((Set)get(scratch,"blockedIds")).size()==105,"Forge reserves each removed numeric ID against reuse");
        JsonArray blocks=new JsonArray(), mismatches=new JsonArray(), earlyNulls=new JsonArray();
        List<CriticalMaterials.MaterialBlock> materials=new ArrayList<>();
        for(Object obj:Block.blockRegistry) if(obj instanceof CriticalMaterials.MaterialBlock b) materials.add(b);
        for(CriticalMaterials.MaterialBlock b:materials) {
            b.registerBlockIcons(name -> icon(name));
            if(b.materialId.startsWith("v2_")) for(int side=0;side<6;side++) if(b.getIcon(side,0)==null)
                earlyNulls.add(new JsonPrimitive(b.materialId+":"+side));
        }
        int exposed=0, missing=0, iconChecks=0;
        Set<String> exposedNames=new TreeSet<>(), expectedNames=new TreeSet<>(), registeredNames=new TreeSet<>();
        for(Map.Entry<String,JsonElement> entry:CriticalGeometry.readJson("materials.json").entrySet())
            expectedNames.add("gtsr:critical_"+entry.getValue().getAsJsonObject().get("id").getAsString());
        SparseWorld placementWorld=world();
        for(CriticalMaterials.MaterialBlock b:materials) {
            List<ItemStack> variants=new ArrayList<>();
            Item item=Item.getItemFromBlock(b);
            item.getSubItems(item,null,variants);
            exposed+=variants.size();
            for(ItemStack variant:variants) exposedNames.add(Item.itemRegistry.getNameForObject(variant.getItem()));
            JsonObject row=new JsonObject();row.addProperty("registry",Block.blockRegistry.getNameForObject(b));
            registeredNames.add(Block.blockRegistry.getNameForObject(b));
            row.addProperty("numericBlockId",Block.getIdFromBlock(b));row.addProperty("numericItemId",Item.getIdFromItem(item));
            row.addProperty("id",b.materialId);
            check(b.canPlaceBlockAt(placementWorld,0,64,0),"material permits manual placement "+b.materialId);
            check(b.getCollisionBoundingBoxFromPool(placementWorld,0,64,0)!=null,"material keeps native collision "+b.materialId);
            check(b.getBlockHardness(placementWorld,0,64,0)==5F,"unclaimed material is removable "+b.materialId);
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
        verifyPositionOwnership();
        int registered=materials.size(), creativeBefore=com.miaokatze.gtsr.register.CreativeTabManager.getItemsToAdd().size();
        CriticalMaterials.register();int after=0;
        for(Object obj:Block.blockRegistry)if(obj instanceof CriticalMaterials.MaterialBlock)after++;
        check(registered==65&&after==registered,"65 stable unique Forge material registrations; repeated init is idempotent");
        check(creativeBefore==com.miaokatze.gtsr.register.CreativeTabManager.getItemsToAdd().size(),"repeated registration does not duplicate creative entries");
        check(missing==0,"all V2 exposed face/mask/frozen albedos resolve after complete stitch");
        Set<String> creativeNames=new TreeSet<>();
        for(ItemStack stack:com.miaokatze.gtsr.register.CreativeTabManager.getItemsToAdd()) creativeNames.add(Item.itemRegistry.getNameForObject(stack.getItem()));
        boolean allVisible=exposed==registered&&exposedNames.equals(registeredNames);
        boolean creativeValid=creativeBefore==65&&creativeNames.equals(expectedNames);
        for(Map.Entry<String,JsonElement> entry:CriticalGeometry.readJson("materials.json").entrySet()) for(boolean dynamic:new boolean[]{false,true})
            check(CriticalMaterials.block(entry.getKey(),dynamic)==CriticalMaterials.block(entry.getValue().getAsJsonObject().get("id").getAsString(),dynamic),"canonical alias identity "+entry.getKey()+":"+dynamic);
        for(String name:expectedNames) {
            String alias=name.substring("gtsr:critical_".length());
            check(CriticalMaterials.block(alias,false)==CriticalMaterials.block(alias,true)
                && CriticalMaterials.block(alias,false)==CriticalMaterials.loomBlock(alias,false)
                && CriticalMaterials.block(alias,false)==CriticalMaterials.loomBlock(alias,true),
                "all four prior roles share canonical identity "+alias);
        }
        check(gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId(15872)!=null&&gregtech.api.enums.Textures.BlockIcons.getCasingTextureForId(15873)!=null,"legacy and V2 controller casing texture slots initialize");
        try { com.miaokatze.gtsr.client.critical.CriticalMaterialRenderer.class.getMethod("hideInternalItems").invoke(null); }
        catch(NoSuchMethodException allMaterialsVisible) { /* No material visibility filter is required. */ }
        Method permutations=codechicken.nei.ItemListLoader.class.getDeclaredMethod("getPermutations",Item.class);permutations.setAccessible(true);
        Set<String> neiNames=new TreeSet<>();int neiCount=0;
        for(CriticalMaterials.MaterialBlock b:materials) {
            List<ItemStack> stacks=(List<ItemStack>)permutations.invoke(null,Item.getItemFromBlock(b));neiCount+=stacks.size();
            for(ItemStack stack:stacks)neiNames.add(Item.itemRegistry.getNameForObject(stack.getItem()));
        }
        boolean neiAllVisible=neiCount==registered&&neiNames.equals(registeredNames);
        JsonArray missingCanonical=new JsonArray();
        Set<String> canonicalIds=new TreeSet<>();
        for(Map.Entry<String,JsonElement> entry:CriticalGeometry.readJson("materials.json").entrySet())
            canonicalIds.add(entry.getValue().getAsJsonObject().get("id").getAsString());
        for(String canonical:canonicalIds) if(!neiNames.contains("gtsr:critical_"+canonical)) missingCanonical.add(new JsonPrimitive(canonical));
        JsonObject result=new JsonObject();result.addProperty("status",mismatches.size()==0&&allVisible&&creativeValid&&neiAllVisible&&missingCanonical.size()==0?"PASS":"REPRODUCED");
        result.addProperty("registered",registered);result.addProperty("enumerated",exposed);result.addProperty("creative",com.miaokatze.gtsr.register.CreativeTabManager.getItemsToAdd().size());
        result.addProperty("iconChecks",iconChecks);result.addProperty("checks",checks);result.addProperty("product",source(CriticalMaterials.class));result.addProperty("textures",source(CriticalMaterialTextures.class));
        result.add("blocks",blocks);result.add("iconMismatches",mismatches);result.add("transientRegistrationNulls",earlyNulls);
        result.addProperty("allRegisteredMaterialsVisible",allVisible);
        result.addProperty("creativeCanonicalMaterials",creativeValid);
        result.addProperty("neiEnumerated",neiCount);result.addProperty("allRegisteredMaterialsVisibleInNei",neiAllVisible);result.addProperty("NEI",source(codechicken.nei.ItemListLoader.class));
        result.add("missingCanonicalNeiMaterials",missingCanonical);
        result.addProperty("boundary","Actual Forge GameRegistry + native ItemBlock.getSubItems + NEI 2.8.145 ItemListLoader.getPermutations (damageSearch and hidden filters) and production icon lookup; mock named IIcons, FML/StructureLib seams, seeded client ownership-index entry. No full anchor lifecycle, client atlas/GL or screenshot.");
        Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes("UTF-8"));
    }
}
