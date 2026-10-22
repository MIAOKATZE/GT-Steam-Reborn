package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.ProbeSupport.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import com.miaokatze.gtsr.client.critical.CriticalMaterialTextures;

/** Compiled and executed separately against the immutable pre-change dev JAR. */
public final class LegacyMaterialReference {
    static IIcon icon(String name) {
        return (IIcon) Proxy.newProxyInstance(IIcon.class.getClassLoader(),new Class[]{IIcon.class},(p,m,a)->{
            if(m.getName().equals("getIconName")) return name;
            if(m.getName().equals("hashCode")) return System.identityHashCode(p);
            if(m.getName().equals("equals")) return p==a[0];
            if(m.getReturnType()==int.class)return 16;
            if(m.getReturnType()==float.class)return 0F;
            return null;
        });
    }
    static void initialize() throws Exception {
        bootstrap();
        set(gregtech.GTMod.proxy,"mCTMBlockCache",new gregtech.api.util.GTBlockMap<Boolean>());
        cpw.mods.fml.common.ModMetadata data=new cpw.mods.fml.common.ModMetadata();
        data.modId="gtsr";data.name="GTSR materials fixture";data.version="materials-acceptance";
        set(get(cpw.mods.fml.common.Loader.instance(),"modController"),"activeContainer",new cpw.mods.fml.common.DummyModContainer(data));
        CriticalMaterials.register();
    }
    static List<CriticalMaterials.MaterialBlock> materials() {
        List<CriticalMaterials.MaterialBlock> result=new ArrayList<>();
        for(Object obj:Block.blockRegistry)if(obj instanceof CriticalMaterials.MaterialBlock)result.add((CriticalMaterials.MaterialBlock)obj);
        return result;
    }
    static JsonObject blockProperties(CriticalMaterials.MaterialBlock b) {
        JsonObject row=new JsonObject();
        row.addProperty("registry",Block.blockRegistry.getNameForObject(b));
        row.addProperty("unlocalizedName",b.getUnlocalizedName());
        row.addProperty("transparent",b.transparent);row.addProperty("animated",b.animated);
        row.addProperty("emissive",b.emissive);row.addProperty("lightLevel",b.lightLevel);
        row.addProperty("lightOpacity",b.getLightOpacity());row.addProperty("lightValue",b.getLightValue());
        row.addProperty("renderPass",b.getRenderBlockPass());row.addProperty("opaque",b.isOpaqueCube());
        row.addProperty("normalBlock",b.renderAsNormalBlock());row.addProperty("resistance",b.getExplosionResistance(null));
        return row;
    }
    static String key(String id,int side,int mask,boolean emission,boolean frozen) {
        return id+"/"+side+"/"+mask+"/"+emission+"/"+frozen;
    }
    static JsonObject iconNames(List<CriticalMaterials.MaterialBlock> materials) {
        JsonObject names=new JsonObject();
        for(CriticalMaterials.MaterialBlock b:materials)if(b.materialId.startsWith("v2_"))
            for(int side=0;side<6;side++)for(int mask=0;mask<16;mask++)
                for(boolean emission:new boolean[]{false,true})for(boolean frozen:new boolean[]{false,true}) {
                    IIcon icon=CriticalMaterialTextures.icon(b.materialId,side,mask,emission,frozen);
                    names.addProperty(key(b.materialId,side,mask,emission,frozen),icon==null?null:icon.getIconName());
                }
        return names;
    }
    public static void main(String[] args)throws Exception {
        initialize();List<CriticalMaterials.MaterialBlock> materials=materials();
        JsonObject properties=new JsonObject(),blocks=new JsonObject();
        for(CriticalMaterials.MaterialBlock b:materials) {
            b.registerBlockIcons(LegacyMaterialReference::icon);
            properties.add(b.materialId,CriticalMaterials.properties(b.materialId));
            blocks.add(b.materialId,blockProperties(b));
        }
        JsonObject result=new JsonObject();result.addProperty("product",source(CriticalMaterials.class));
        result.addProperty("textures",source(CriticalMaterialTextures.class));
        result.add("properties",properties);result.add("blocks",blocks);result.add("icons",iconNames(materials));
        Files.write(Paths.get(args[0]),new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(result).getBytes("UTF-8"));
    }
}
