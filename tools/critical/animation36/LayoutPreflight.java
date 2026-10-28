import com.google.gson.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Candidate layout selection and packaged model metadata; no Minecraft/FML initialization. */
public final class LayoutPreflight {
    public static void main(String[] args)throws Exception{
        Class<?> layouts=Class.forName("com.miaokatze.gtsr.common.critical.CriticalLayoutDefinition"),tiers=Class.forName("com.miaokatze.gtsr.common.critical.CriticalTier"),kinds=Class.forName("com.miaokatze.gtsr.common.critical.CriticalMachineKind");
        Object tier=Enum.valueOf((Class)tiers,"T3"),kind=Enum.valueOf((Class)kinds,"SOLAR");Method select=layouts.getMethod("select",int.class,int.class,boolean.class,tiers,kinds);
        Object current=select.invoke(null,2,31,false,tier,kind),frozen=select.invoke(null,2,30,false,tier,kind);String slug=(String)layouts.getField("machineSlug").get(current),old=(String)layouts.getField("machineSlug").get(frozen);
        if(!slug.equals("v2_solar")||!old.equals("v2_v30_solar"))throw new AssertionError(slug+" / "+old);
        JsonObject model=new JsonParser().parse(new InputStreamReader(layouts.getResourceAsStream("/assets/gtsr/critical/models/"+slug+".geometry.json"),StandardCharsets.UTF_8)).getAsJsonObject();int work=0;for(java.util.Map.Entry<String,JsonElement> entry:model.getAsJsonObject("animationGroups").entrySet()){JsonObject g=entry.getValue().getAsJsonObject();if(g.has("workMotion")||g.has("workSpin"))work++;}
        if(work!=26||model.getAsJsonArray("blocks").size()!=43118)throw new AssertionError("Unexpected current packaged Solar model");
        JsonObject result=new JsonObject();result.addProperty("status","PASS");result.addProperty("candidateLayoutCodeSource",layouts.getProtectionDomain().getCodeSource().getLocation().toString());result.addProperty("currentRevision31Slug",slug);result.addProperty("frozenRevision30Slug",old);result.addProperty("currentModelBlocks",43118);result.addProperty("currentModelWorkGroups",work);result.addProperty("currentModelDesignFX",model.getAsJsonObject("meta").has("designFX"));result.addProperty("MinecraftOrFMLInitialized",false);Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));System.out.println(result);
    }
}
