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
import com.miaokatze.gtsr.register.CreativeTabManager;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraft.client.renderer.texture.TextureMap;

/** Actual candidate Forge/creative/NEI exposure and typed atlas lookup; no GL. */
public final class MaterialsProbe {
    static Set<String> geometry(String slug) {
        Set<String> ids=new TreeSet<>();
        JsonObject palette=CriticalGeometry.readJson("materials.json");
        for(JsonElement e:CriticalGeometry.readJson("models/"+slug+".geometry.json").getAsJsonArray("blocks"))
            ids.add(palette.getAsJsonObject(e.getAsJsonArray().get(3).getAsString()).get("id").getAsString());
        ids.remove("v2_gt_controller");ids.remove("v2_loom_machine_controller");return ids;
    }
    static Set<String> ids(Collection<ItemStack> items) {
        Set<String> names=new TreeSet<>();
        for(ItemStack s:items) {
            Block b=Block.getBlockFromItem(s.getItem());
            if(b instanceof CriticalMaterials.MaterialBlock)names.add(((CriticalMaterials.MaterialBlock)b).materialId);
        }
        return names;
    }
    static List<ItemStack> creative() {
        List<ItemStack> items=new ArrayList<>();CreativeTabManager.CREATIVE_TAB.displayAllReleventItems(items);return items;
    }
    static TextureMap atlas(int type)throws Exception {
        TextureMap map=(TextureMap)U.allocateInstance(TextureMap.class);set(map,"textureType",type);return map;
    }
    static void stitch(CriticalMaterialTextures listener,TextureMap map,boolean post) {
        if(post)listener.afterStitch(new TextureStitchEvent.Post(map));
        else listener.beforeStitch(new TextureStitchEvent.Pre(map));
    }
    static void removedMappings()throws Exception {
        Set<String> removed=(Set<String>)get(CriticalMaterials.class,"REMOVED_REGISTRATIONS");
        check(removed.size()==105,"105 intentionally removed duplicate registrations remain targeted");
        com.google.common.collect.ListMultimap<String,cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping> rows=com.google.common.collect.ArrayListMultimap.create();
        int id=1000;for(String name:removed) {
            for(char type:new char[]{1,2})rows.put("gtsr",new cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping(type+name,id));
            id++;
        }
        rows.put("gtsr",new cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping("\1gtsr:unrelated_missing",1234));
        cpw.mods.fml.common.event.FMLMissingMappingsEvent event=new cpw.mods.fml.common.event.FMLMissingMappingsEvent(rows);
        cpw.mods.fml.common.ModMetadata data=new cpw.mods.fml.common.ModMetadata();data.modId="gtsr";
        event.applyModContainer(new cpw.mods.fml.common.DummyModContainer(data));CriticalMaterials.ignoreRemovedMappings(event);
        List<cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping> ignored=new ArrayList<>();
        for(cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping row:event.get()) {
            check(row.getAction()==(removed.contains(row.name)?cpw.mods.fml.common.event.FMLMissingMappingsEvent.Action.IGNORE:cpw.mods.fml.common.event.FMLMissingMappingsEvent.Action.DEFAULT),"targeted Forge missing mapping "+row.type+":"+row.name);
            if(removed.contains(row.name))ignored.add(row);
        }
        Constructor<cpw.mods.fml.common.registry.GameData> ctor=cpw.mods.fml.common.registry.GameData.class.getDeclaredConstructor();ctor.setAccessible(true);
        cpw.mods.fml.common.registry.GameData scratch=ctor.newInstance();
        check(cpw.mods.fml.common.registry.GameData.processIdRematches(ignored,true,scratch,new HashMap<>()).isEmpty(),"actual Forge rematch accepts intentional removals");
        check(((Set<?>)get(scratch,"blockedIds")).size()==105,"Forge reserves105 removed numeric IDs");
    }
    public static void main(String[] args)throws Exception {
        LegacyMaterialReference.initialize();
        removedMappings();
        JsonObject baseline=new JsonParser().parse(new String(Files.readAllBytes(Paths.get(args[1])),"UTF-8")).getAsJsonObject();
        List<CriticalMaterials.MaterialBlock> materials=LegacyMaterialReference.materials();
        check(materials.size()==65,"65 real Forge material registrations");
        check(CriticalMaterialDefinitions.catalog().aliases().size()==371,"371 historical aliases remain registered");
        Set<String> manual=geometry("v2_loom"),expectedCreative=new TreeSet<>(manual);
        for(String slug:new String[]{"v2_base_t3","v2_solar","v2_turbine","v2_sun","v2_battery","v2_plugin_parallel"})expectedCreative.addAll(geometry(slug));
        check(manual.size()==15&&expectedCreative.size()==24,"independent model resources define manual15 and physical24");
        List<ItemStack> displayed=creative();
        check(displayed.size()==24&&ids(displayed).equals(expectedCreative),"actual CreativeTabs.displayAllReleventItems exposes exactly24");
        SparseWorld world=world();int nativeVariants=0;
        JsonObject propertyMismatches=new JsonObject();
        for(CriticalMaterials.MaterialBlock b:materials) {
            check(b.getCreativeTabToDisplayOn()==(expectedCreative.contains(b.materialId)?CreativeTabManager.CREATIVE_TAB:null),"native creative tab property "+b.materialId);
            List<ItemStack> nativeItems=new ArrayList<>();Item item=Item.getItemFromBlock(b);item.getSubItems(item,null,nativeItems);nativeVariants+=nativeItems.size();
            check(nativeItems.size()==1,"native ItemBlock retains placeable identity "+b.materialId);
            check(b.canPlaceBlockAt(world,0,64,0)&&b.getCollisionBoundingBoxFromPool(world,0,64,0)!=null&&b.getBlockHardness(world,0,64,0)==5F,"native placement collision hardness "+b.materialId);
            JsonObject props=CriticalMaterials.properties(b.materialId);
            if(!props.equals(baseline.getAsJsonObject("properties").get(b.materialId)))propertyMismatches.add(b.materialId,props);
            check(LegacyMaterialReference.blockProperties(b).equals(baseline.getAsJsonObject("blocks").get(b.materialId)),"old/new actual registered block attributes "+b.materialId);
        }
        check(propertyMismatches.entrySet().isEmpty(),"all65 old/new material JSON properties equal");
        for(Map.Entry<String,String> alias:CriticalMaterialDefinitions.catalog().aliases().entrySet()) {
            Block canonical=CriticalMaterials.block(alias.getValue(),false);
            check(CriticalMaterials.block(alias.getKey(),false)==canonical&&CriticalMaterials.block(alias.getKey(),true)==canonical&&CriticalMaterials.loomBlock(alias.getKey(),false)==canonical&&CriticalMaterials.loomBlock(alias.getKey(),true)==canonical,"all four roles share alias identity "+alias.getKey());
        }
        CriticalMaterials.register();check(LegacyMaterialReference.materials().size()==65&&creative().size()==24,"repeated registration preserves65 and creative24");
        Method permutations=codechicken.nei.ItemListLoader.class.getDeclaredMethod("getPermutations",Item.class);permutations.setAccessible(true);
        int beforeHide=0;for(CriticalMaterials.MaterialBlock b:materials)beforeHide+=((List<?>)permutations.invoke(null,Item.getItemFromBlock(b))).size();
        check(beforeHide==65,"actual NEI getPermutations sees all65 before production hide registration");
        Method hide=com.miaokatze.gtsr.main.ClientProxy.class.getDeclaredMethod("hideCriticalMaterialItems");hide.setAccessible(true);hide.invoke(null);
        Set<String> nei=new TreeSet<>();int neiCount=0;
        for(CriticalMaterials.MaterialBlock b:materials) {
            List<ItemStack> stacks=(List<ItemStack>)permutations.invoke(null,Item.getItemFromBlock(b));neiCount+=stacks.size();nei.addAll(ids(stacks));
            check(codechicken.nei.api.ItemInfo.isHidden(new ItemStack(b))==!expectedCreative.contains(b.materialId),"production API.hideItem reaches NEI ItemInfo filter "+b.materialId);
        }
        check(neiCount==24&&nei.equals(expectedCreative),"real NEI getPermutations hides41 and exposes exactly current24");
        hide.invoke(null);check(ids(creative()).equals(expectedCreative),"repeated NEI hiding leaves actual creative exposure24");
        Map<CriticalMaterialCatalog.Usage,Integer> usage=new EnumMap<>(CriticalMaterialCatalog.Usage.class);
        for(CriticalMaterialCatalog.Usage u:CriticalMaterialCatalog.current().usages().values())usage.put(u,usage.getOrDefault(u,0)+1);
        check(usage.get(CriticalMaterialCatalog.Usage.LEGACY_V1)==35&&usage.get(CriticalMaterialCatalog.Usage.FROZEN_ONLY)==2&&usage.get(CriticalMaterialCatalog.Usage.ORPHAN)==2&&usage.get(CriticalMaterialCatalog.Usage.GT_CONTROLLER_MARKER)==2&&usage.get(CriticalMaterialCatalog.Usage.AUTOMATIC_BODY_ONLY)==9,"hidden categories35+2+2+2 and auto-only9");
        Constructor<CriticalMaterialTextures> ctor=CriticalMaterialTextures.class.getDeclaredConstructor();ctor.setAccessible(true);CriticalMaterialTextures listener=ctor.newInstance();
        TextureMap terrain=atlas(0),items=atlas(1);int iconChecks=0;
        JsonObject expectedIcons=baseline.getAsJsonObject("icons");Map<String,IIcon> firstGeneration=new HashMap<>();
        for(int generation=1;generation<=2;generation++) {
            final int round=generation;
            for(CriticalMaterials.MaterialBlock b:materials)b.registerBlockIcons(name->LegacyMaterialReference.icon(name+"@"+round));
            stitch(listener,terrain,false);check(((Map<?,?>)get(CriticalMaterialTextures.class,"tables")).isEmpty(),"terrain Pre invalidates published typed table generation"+round);
            stitch(listener,terrain,true);Object published=get(CriticalMaterialTextures.class,"tables");
            stitch(listener,items,false);stitch(listener,items,true);check(get(CriticalMaterialTextures.class,"tables")==published,"item atlas stitch leaves terrain table generation"+round);
            for(CriticalMaterials.MaterialBlock b:materials)if(b.materialId.startsWith("v2_"))
                for(int side=0;side<6;side++)for(int mask=0;mask<16;mask++)for(boolean emission:new boolean[]{false,true})for(boolean frozen:new boolean[]{false,true}) {
                    String key=LegacyMaterialReference.key(b.materialId,side,mask,emission,frozen);
                    JsonElement expected=expectedIcons.get(key);String want=expected.isJsonNull()?null:expected.getAsString()+"@"+round;
                    IIcon actual=CriticalMaterialTextures.icon(b.materialId,side,mask,emission,frozen);String got=actual==null?null:actual.getIconName();
                    if(!Objects.equals(want,got))throw new AssertionError("old/new actual IIcon lookup "+key+": "+want+" != "+got);
                    if(generation==1)firstGeneration.put(key,actual);
                    else if(actual!=null&&firstGeneration.get(key)==actual)throw new AssertionError("stale atlas IIcon survived reload "+key);
                    iconChecks++;
                }
            for(Map.Entry<String,String> alias:CriticalMaterialDefinitions.catalog().aliases().entrySet())if(alias.getValue().startsWith("v2_"))
                check(CriticalMaterialTextures.icon(alias.getKey(),3,0,false,false)==CriticalMaterialTextures.icon(alias.getValue(),3,0,false,false),"typed aliases share exact stitched IIcon "+alias.getKey()+" generation"+round);
        }
        check(iconChecks==23040,"all old/new V2 face mask emission frozen lookups agree over two atlas generations");
        JsonObject result=new JsonObject();result.addProperty("status","PASS");result.addProperty("registered",65);result.addProperty("aliases",371);
        result.addProperty("nativeEnumerated",nativeVariants);result.addProperty("creative",displayed.size());result.addProperty("neiBeforeHide",beforeHide);result.addProperty("neiEnumerated",neiCount);
        result.addProperty("iconChecks",iconChecks);result.addProperty("checks",checks);result.addProperty("product",source(CriticalMaterials.class));result.addProperty("definitions",source(CriticalMaterialDefinitions.class));
        result.addProperty("catalog",source(CriticalMaterialCatalog.class));result.addProperty("textures",source(CriticalMaterialTextures.class));result.addProperty("clientProxy",source(com.miaokatze.gtsr.main.ClientProxy.class));
        result.addProperty("NEI",source(codechicken.nei.ItemListLoader.class));result.add("usage",new Gson().toJsonTree(usage));result.add("creativeIds",new Gson().toJsonTree(expectedCreative));result.add("neiIds",new Gson().toJsonTree(nei));
        result.addProperty("Core",source(com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog.class));
        result.addProperty("boundary","Actual Forge registration, CreativeTabs display, ClientProxy production hide helper, NEI2.8.145 getPermutations/ItemInfo.isHidden, and old/new JAR-executed IIcon lookup. Named IIcons and allocated TextureMap events do not run GL or pixel rendering. Historical private ownership fixture belongs to world/anchor runtime contracts, not material exposure.");
        Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes("UTF-8"));
    }
}
