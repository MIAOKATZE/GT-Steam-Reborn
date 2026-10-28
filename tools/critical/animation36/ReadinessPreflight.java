package audit;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** CLI invokes the exact runtime helper readiness predicate on real native NBT compounds. */
public final class ReadinessPreflight {
    static Object tag(JsonObject json)throws Exception{
        Object n=Mui2ClientQa.type("net.minecraft.nbt.NBTTagCompound").getDeclaredConstructor().newInstance();
        for(String key:new String[]{"geometryVersion","v2LayoutRevision","state"})if(json.has(key))Mui2ClientQa.call(n,"setInteger|func_74768_a",key,json.get(key).getAsInt());
        if(json.has("valid"))Mui2ClientQa.call(n,"setBoolean|func_74757_a","valid",json.get("valid").getAsInt()!=0);
        for(String key:new String[]{"structureId","visualHostId"})if(json.has(key))Mui2ClientQa.call(n,"setString|func_74778_a",key,json.get(key).getAsString());
        if(json.has("config")){Object c=Mui2ClientQa.type("net.minecraft.nbt.NBTTagCompound").getDeclaredConstructor().newInstance();for(String key:new String[]{"tier","kind"})Mui2ClientQa.call(c,"setInteger|func_74768_a",key,json.getAsJsonObject("config").get(key).getAsInt());Mui2ClientQa.call(n,"setTag|func_74782_a","config",c);}
        return n;
    }
    public static void main(String[] args)throws Exception{
        JsonObject server=new JsonParser().parse(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("visual");Object expected=tag(server);JsonArray cases=new JsonArray();
        if(!UiGalleryBatchAnimation36.initialReady(tag(server),expected))throw new AssertionError("Saved real server positive rejected");
        JsonObject positive=new JsonObject();positive.addProperty("case","saved-R3-server-anchor-positive");positive.addProperty("ready",true);cases.add(positive);
        for(String name:new String[]{"empty-client-bootstrap","default-v1","geometryVersion","v2LayoutRevision","state","valid","structureId","visualHostId","tier","kind"}){
            JsonObject actual=name.equals("empty-client-bootstrap")?new JsonObject():new JsonParser().parse(server.toString()).getAsJsonObject();
            if(name.equals("default-v1")){actual.addProperty("geometryVersion",1);actual.addProperty("state",0);actual.addProperty("valid",0);actual.addProperty("structureId","");actual.addProperty("visualHostId","");}
            else if(name.equals("tier")||name.equals("kind"))actual.getAsJsonObject("config").addProperty(name,99);
            else if(name.equals("structureId")||name.equals("visualHostId"))actual.addProperty(name,"wrong-owned-scene");
            else if(!name.equals("empty-client-bootstrap"))actual.addProperty(name,name.equals("valid")?0:99);
            boolean ready=UiGalleryBatchAnimation36.initialReady(tag(actual),expected);if(ready)throw new AssertionError("Rejected negative accepted: "+name);
            JsonObject row=new JsonObject();row.addProperty("case",name);row.addProperty("ready",false);cases.add(row);
        }
        JsonObject result=new JsonObject();result.addProperty("status","PASS");result.addProperty("realNativeNBT",true);result.addProperty("exactHelperPredicate",true);result.addProperty("MinecraftOrFMLInitialized",false);result.add("cases",cases);Files.write(Paths.get(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));System.out.println(result);
    }
}
