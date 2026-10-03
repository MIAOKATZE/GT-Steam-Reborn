import java.io.*;
import java.util.*;
import com.google.gson.*;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;

/** Isolated actual planner/terrain/production-air capture for the two watch arrival anchors. */
public final class WatchArrivalProfileCapture {
    static int[] point(JsonArray p) {return new int[]{p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()};}
    static JsonArray point(int[] p) {JsonArray a=new JsonArray();for(int n:p)a.add(new JsonPrimitive(n));return a;}
    static String key(int x,int y,int z){return x+","+y+","+z;}
    static List<RemasterSite> cases() {
        List<RemasterSite> out=new ArrayList<>();
        for(long seed:new long[]{20261001L,20261003L}) {
            Map<Integer,RemasterSite> selected=new LinkedHashMap<>();
            outer:for(int gx=-12;gx<=12;gx++)for(int gz=-12;gz<=12;gz++)for(RemasterSite s:RemasterPlanner.cell(seed,0,gx,gz)) {
                if(!s.prefab.equals("city_watch_tower")||s.variant==0)continue;
                // Historical EMPTY-ledger fixtures were v1(-24429,-19225), v2(-23548,-21049).
                // These are historical coordinates, not normal six-biome candidate requirements.
                selected.putIfAbsent(s.variant,s);
                if(selected.size()==2)break outer;
            }
            if(selected.size()!=2)throw new AssertionError("missing normal-six-biome watch variants for seed "+seed+" in cells[-12,12]^2: admitted="+selected.keySet());
            out.addAll(selected.values());
        }
        return out;
    }
    public static void main(String[] args)throws Exception {
        NativeTerrainFixture.initialize();
        JsonArray inputs=new JsonParser().parse(new FileReader("temp/watch-arrival-inputs.json")).getAsJsonArray(),fixtures=new JsonArray(),evidence=new JsonArray();
        for(RemasterSite s:cases()) {
            int top=ProsperityTerrainProfile.heightAt(s.seed,s.entryX(),s.entryZ());
            if(s.entryX()!=s.x+18||s.entryZ()!=s.z||s.y!=top||s.entryY()!=top+1)throw new AssertionError("entry floor/feet anchor mismatch");
            if(ChunkProviderProsperityRuins.naturalWaterTopAt(s.seed,s.entryX(),s.entryZ())>=top)throw new AssertionError("new entry at natural water");
            RemasterSite saved=new RemasterSite(s.prefab,s.variant,s.seed,s.x,s.variant==1?77:81,s.z,s.layout);
            if(RemasterSite.read(saved.save()).y!=saved.y)throw new AssertionError("saved anchor was rebuilt");
            for(JsonElement element:inputs) {
                JsonObject route=element.getAsJsonObject();if(route.get("variant").getAsInt()!=s.variant)continue;
                JsonArray points=new JsonArray();
                for(JsonElement p:route.getAsJsonArray("points"))points.add(p);
                Set<String> extent=new HashSet<>();Map<String,String> cells=new HashMap<>();
                int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=minX,maxZ=maxX;
                JsonArray heights=new JsonArray();
                for(JsonElement p:points) {
                    int[] q=point(p.getAsJsonArray());heights.add(point(new int[]{q[0],ProsperityTerrainProfile.heightAt(s.seed,s.x+q[0],s.z+q[2])-s.y,q[2]}));
                    for(int x=q[0]-3;x<=q[0]+3;x++)for(int z=q[2]-3;z<=q[2]+3;z++) {
                        minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                        int natural=ProsperityTerrainProfile.heightAt(s.seed,s.x+x,s.z+z)-s.y;
                        for(int y=q[1]-7;y<=q[1]+7;y++){String k=key(x,y,z);extent.add(k);if(y<=natural)cells.put(k,"minecraft:stone#0");}
                    }
                }
                // Include actual natural forecourt beside the original entrance.
                for(int x=6;x<=30;x++)for(int z=-12;z<=2;z++){
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                    int natural=ProsperityTerrainProfile.heightAt(s.seed,s.x+x,s.z+z)-s.y;
                    for(int y=-7;y<=7;y++){String k=key(x,y,z);extent.add(k);if(y<=natural)cells.put(k,"minecraft:stone#0");}
                }
                BlockSink sink=(x,y,z,b,meta,flags)->{String k=key(x-s.x,y-s.y,z-s.z);if(extent.contains(k)){String id=b.toString();if(id.startsWith("minecraft:air"))cells.remove(k);else cells.put(k,id+"#"+meta);}return true;};
                for(int cx=(s.x+minX)>>4;cx<=(s.x+maxX)>>4;cx++)for(int cz=(s.z+minZ)>>4;cz<=(s.z+maxZ)>>4;cz++){
                    RemasterTerrain.build(s,sink,cx,cz);RemasterWorldgen.geometry(s,s.plan(),sink,cx,cz);
                }
                ArrayDeque<int[]> queue=new ArrayDeque<>();Map<String,int[]> back=new HashMap<>();queue.add(new int[]{18,0,0});back.put(key(18,0,0),null);int[] arrival=null;
                while(!queue.isEmpty()){
                    int[] p=queue.remove();if("minecraft:stone#0".equals(cells.get(key(p[0],0,p[2])))&&ProsperityTerrainProfile.heightAt(s.seed,s.x+p[0],s.z+p[2])-s.y==0&&ChunkProviderProsperityRuins.naturalWaterTopAt(s.seed,s.x+p[0],s.z+p[2])<s.y){arrival=p;break;}
                    for(int[] dir:new int[][]{{0,-1},{-1,0},{1,0},{0,1}}){int x=p[0]+dir[0],z=p[2]+dir[1];String k=key(x,0,z),floor=cells.get(k);if(x<6||x>30||z<-12||z>2||back.containsKey(k)||floor==null||!floor.matches("minecraft:stone#0|gtsr:ProsperityStone#0|gtsr:ruins_(mossroot_paving|sootstone_tiles|slag_masonry)#0")||cells.containsKey(key(x,1,z))||cells.containsKey(key(x,2,z)))continue;back.put(k,p);queue.add(new int[]{x,0,z});}
                }
                if(arrival==null)throw new AssertionError("actual natural field cannot join original entrance without an added step "+s.id());
                JsonArray joined=new JsonArray();for(int[] p=arrival;p!=null;p=back.get(key(p[0],p[1],p[2])))joined.add(point(p));for(int i=1;i<points.size();i++)joined.add(points.get(i));points=joined;heights=new JsonArray();for(JsonElement p:points){int[] q=point(p.getAsJsonArray());heights.add(point(new int[]{q[0],ProsperityTerrainProfile.heightAt(s.seed,s.x+q[0],s.z+q[2])-s.y,q[2]}));}
                for(JsonElement p:points){int[] q=point(p.getAsJsonArray());if(cells.containsKey(key(q[0],q[1]+1,q[2]))||cells.containsKey(key(q[0],q[1]+2,q[2])))throw new AssertionError("actual production soil/geometry seals route feet/head "+Arrays.toString(q));}
                JsonObject fixture=new JsonObject();fixture.addProperty("kind","actual-natural-arrival-proof");fixture.addProperty("id",s.prefab);fixture.addProperty("variant",s.variant);
                fixture.addProperty("route",route.get("route").getAsString()+":seed"+s.seed);fixture.addProperty("width",1);fixture.add("points",points);
                fixture.addProperty("reason","real planner entry floor matches height field; continuous actual natural ground -> unchanged paver -> original room and loot, production explicit-air and foundation");
                JsonArray geometry=new JsonArray();for(Map.Entry<String,String> cell:cells.entrySet()){JsonArray c=new JsonArray();for(String n:cell.getKey().split(","))c.add(new JsonPrimitive(Integer.parseInt(n)));c.add(new JsonPrimitive(cell.getValue()));geometry.add(c);}fixture.add("cells",geometry);fixtures.add(fixture);
                JsonObject proof=new JsonObject();proof.addProperty("seed",s.seed);proof.addProperty("variant",s.variant);proof.addProperty("siteX",s.x);proof.addProperty("siteY",s.y);proof.addProperty("siteZ",s.z);
                proof.addProperty("entryFloor",top);proof.addProperty("entryFeet",s.entryY());proof.addProperty("waterTop",ChunkProviderProsperityRuins.naturalWaterTopAt(s.seed,s.entryX(),s.entryZ()));
                proof.addProperty("savedSiteYUnchanged",saved.y);proof.add("arrivalLocalFloor",point(arrival));proof.add("naturalLocalTopByWaypoint",heights);proof.addProperty("route",fixture.get("route").getAsString());evidence.add(proof);
            }
        }
        if(fixtures.size()!=24)throw new AssertionError("expected 4 actual anchors x 6 original complete routes");
        try(Writer out=new FileWriter("temp/watch-arrival-fixtures.json")){new Gson().toJson(fixtures,out);}
        try(Writer out=new FileWriter("temp/watch-arrival-fields.json")){new GsonBuilder().setPrettyPrinting().create().toJson(evidence,out);}
        System.out.println("captured 24 actual natural-ground arrivals through unchanged doors/decks at four admitted planner anchors");
    }
}
