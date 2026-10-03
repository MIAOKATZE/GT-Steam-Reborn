import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import com.google.gson.*;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;

/** Read-only production planner/geometry export; no World, registration or FML emulation. */
public final class RemasterCityPreviewExport {
    public static void main(String[] args) throws Exception {
        NativeTerrainFixture.initialize();
        long seed = 20261001L;
        List<RemasterSite> sites = null;
        int gx = 0, gz = 0;
        outer: for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            List<RemasterSite> candidate = RemasterPlanner.cell(seed, 0, x, z);
            if (candidate.stream().anyMatch(s -> "city-grid".equals(s.layout))) {
                sites = candidate; gx = x; gz = z; break outer;
            }
        }
        if (sites == null || sites.size() != 29) throw new AssertionError("No complete production city");
        RemasterSite parent = sites.get(0);
        if (!"city-grid".equals(parent.layout) || parent.roadVersion != 1 || !RemasterCityBridge.viable(parent))
            throw new AssertionError("Preview must capture a current complete bridge city");
        JsonArray connections = new JsonArray();
        for (RemasterCityBridge.Connection connection : RemasterCityBridge.connections(parent)) {
            if (connection.points.size() < 2) throw new AssertionError("Missing physical entrance connection");
            JsonObject c = new JsonObject();
            c.addProperty("plot", connection.plot.prefab);
            c.addProperty("variant", connection.plot.variant);
            c.add("points", new Gson().toJsonTree(connection.points));
            connections.add(c);
        }
        if (connections.size() != 28) throw new AssertionError("Incomplete bridge connections");
        final Map<Long, String> cells = new HashMap<>();
        JsonArray placements = new JsonArray();
        for (RemasterSite s : sites) {
            JsonObject p = new JsonObject();
            p.addProperty("id", s.prefab); p.addProperty("variant", s.variant); p.addProperty("layout", s.layout);
            JsonArray at = new JsonArray(); at.add(new JsonPrimitive(s.x)); at.add(new JsonPrimitive(s.y)); at.add(new JsonPrimitive(s.z));
            p.add("anchor", at); placements.add(p);
            BlockSink sink = (x, y, z, block, meta, flags) -> {
                long key = ((long)(x & 0xFFFFFF) << 32) | ((long)(z & 0xFFFFFF) << 8) | y;
                if (block.toString().equals("minecraft:air")) cells.remove(key);
                else cells.put(key, block.toString() + "#" + meta);
                return true;
            };
            RemasterPrefab prefab = "city-grid".equals(s.layout) ? null : s.plan();
            for (int cx = s.minX() >> 4; cx <= s.maxX() >> 4; cx++)
                for (int cz = s.minZ() >> 4; cz <= s.maxZ() >> 4; cz++) {
                    RemasterTerrain.build(s, sink, cx, cz);
                    RemasterWorldgen.geometry(s, prefab, sink, cx, cz);
                }
        }
        List<long[]> points = new ArrayList<>();
        List<String> palette = new ArrayList<>(new TreeSet<>(cells.values()));
        Map<String,Integer> indexes = new HashMap<>();
        for (int i=0; i<palette.size(); i++) indexes.put(palette.get(i), i);
        int[] min = {Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE}, max = {Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};
        for (Map.Entry<Long,String> e: cells.entrySet()) {
            long key=e.getKey(); int x=(int)(key>>32), z=(int)((key>>8)&0xFFFFFF), y=(int)(key&255);
            if (x>=0x800000) x-=0x1000000; if(z>=0x800000) z-=0x1000000;
            int[] xyz={x,y,z}; for(int i=0;i<3;i++){min[i]=Math.min(min[i],xyz[i]);max[i]=Math.max(max[i],xyz[i]);}
            points.add(new long[]{x,y,z,indexes.get(e.getValue())});
        }
        points.sort(Comparator.<long[]>comparingLong(a->a[1]).thenComparingLong(a->a[2]).thenComparingLong(a->a[0]));
        List<long[]> runs = new ArrayList<>();
        for(long[] a:points){
            long[] last=runs.isEmpty()?null:runs.get(runs.size()-1);
            if(last!=null&&last[1]==a[1]&&last[2]==a[2]&&last[4]==a[3]&&last[0]+last[3]==a[0])last[3]++;
            else runs.add(new long[]{a[0],a[1],a[2],1,a[3]});
        }
        Gson gson=new Gson(); JsonObject output=new JsonObject();
        output.addProperty("seed",seed); output.addProperty("cellX",gx); output.addProperty("cellZ",gz);
        output.addProperty("count",cells.size()); output.add("placements",placements);
        output.addProperty("roadVersion", parent.roadVersion); output.add("connections", connections);
        output.add("palette",gson.toJsonTree(palette));output.add("runs",gson.toJsonTree(runs));
        output.add("min",gson.toJsonTree(min));output.add("max",gson.toJsonTree(max));
        Files.write(Paths.get(args[0]),gson.toJson(output).getBytes(StandardCharsets.UTF_8));
        System.out.println("PASS: production planner city: seed="+seed+" cell="+gx+","+gz+" / 28 independent plot anchors / "+cells.size()+" solid cells");
    }
}
