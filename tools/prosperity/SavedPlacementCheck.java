import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.RegistryNamespaced;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.*;

/** Actual saved-placement admission, population and NBT over one loaded city road slice. */
public final class SavedPlacementCheck {
    private static int checks;

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }

    private static List<NBTTagCompound> records(RemasterData data) {
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        NBTTagList sites = saved.getTagList("sites", 10);
        List<NBTTagCompound> result = new ArrayList<>();
        for (int i = 0; i < sites.tagCount(); i++) result.add(sites.getCompoundTagAt(i));
        return result;
    }

    private static Map<String, String> childRecords(RemasterData data) {
        Map<String, String> result = new HashMap<>();
        for (NBTTagCompound record : records(data)) {
            RemasterSite site = RemasterSite.read(record);
            if ("city-plot".equals(site.layout)) result.put(site.id(), record.toString());
        }
        return result;
    }

    private static void assertFlatRoad(RemasterRuntimeCheck.W world, RemasterSite parent,
        Block brick, int columns, String label) {
        check(world.blocks.size() == columns, label + " nonempty expected column count");
        for (Map.Entry<String, Block> entry : world.blocks.entrySet()) {
            String[] xyz = entry.getKey().split(",");
            check(Integer.parseInt(xyz[1]) == parent.y - 1 && entry.getValue() == brick
                && world.metas.get(entry.getKey()) == 0, label + " flat savedY-1 brick " + entry.getKey());
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("one report path required");
        NativeTerrainFixture.initialize();
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        RemasterRuntimeCheck.u = (sun.misc.Unsafe) unsafe.get(null);
        Constructor<BlockAir> air = BlockAir.class.getDeclaredConstructor();
        air.setAccessible(true);
        RemasterRuntimeCheck.setBlock("air", air.newInstance());
        BlockStoneBrick brick = new BlockStoneBrick();
        RemasterRuntimeCheck.setBlock("stonebrick", brick);
        // Standalone MC has no FML LaunchClassLoader. Only material registration is adapted.
        Field blockRegistry = Block.class.getDeclaredField("blockRegistry");
        RemasterRuntimeCheck.u.putObject(RemasterRuntimeCheck.u.staticFieldBase(blockRegistry),
            RemasterRuntimeCheck.u.staticFieldOffset(blockRegistry), new RegistryNamespaced());
        Block.blockRegistry.addObject(98, "minecraft:stonebrick", brick);

        RemasterRuntimeCheck.W majorWorld = RemasterRuntimeCheck.isolatedWorld(0);
        majorWorld.loaded = false;
        RemasterData major = RemasterData.get(majorWorld);
        RemasterSite old = new RemasterSite("sniper_watch", 1, 0, -1506, 65, -820);
        major.register(old);
        major.flag(old.id(), "claimed:sentinel", true);
        List<RemasterSite> predicted = RemasterPlanner.cell(0, 1, -3, -2);
        check(predicted.size() == 1, "seed0 current replacement fixture must exist");
        RemasterSite replacement = predicted.get(0);
        check(!old.id().equals(replacement.id()), "replacement has a different site identity");
        check(RemasterWorldgen.allowed(majorWorld, old), "actual allowed old true");
        check(!RemasterWorldgen.allowed(majorWorld, replacement), "actual allowed replacement false");
        int cx = -83, cz = -52;
        check(old.intersects(cx, cz) && replacement.intersects(cx, cz), "Major owner intersects both sites");
        major.flag(old.id(), "geom:" + cx + ":" + cz, true);
        check(RemasterWorldgen.generate(majorWorld, cx, cz), "actual generate old Major owner");
        check(records(major).size() == 1 && major.site(replacement.id()) == null,
            "actual generate admits one saved site");
        check(major.flag(old.id(), "claimed:sentinel") && majorWorld.writes == 0,
            "Major claimed and completed geometry preserved");

        // Persisted v1.20.66 inputs verified by the earlier independent old-planner fixture.
        RemasterSite parent = new RemasterSite("prosperity_city_full", 0, 0, -1296, 71, 2160, "city-grid");
        RemasterSite child1 = new RemasterSite("city_titan_gearworks", 2, 0, -1276, 76, 2184, "city-plot");
        RemasterSite child2 = new RemasterSite("city_watch_tower", 2, 0, -1180, 68, 2183, "city-plot");
        check(RemasterPlanner.cell(0, 0, -1, 1).isEmpty(), "new city filter rejects the saved parent cell");
        RemasterRuntimeCheck.W cityWorld = RemasterRuntimeCheck.isolatedWorld(parent.seed);
        RemasterData city = RemasterData.get(cityWorld);
        city.register(parent);
        city.register(child1);
        city.register(child2);
        city.flag(child1.id(), "claimed:sentinel", true);
        city.flag(child2.id(), "claimed:sentinel", true);
        city.state(child1.id()).setString("opaqueLegacy", "keep-one");
        city.state(child2.id()).setString("opaqueLegacy", "keep-two");
        Map<String, String> existingChildren = childRecords(city);
        check(existingChildren.size() == 2, "partial saved city has exactly two preexisting children");
        int pcx = parent.x >> 4, pcz = parent.z >> 4;
        check(RemasterWorldgen.generate(cityWorld, pcx, pcz), "actual generate first old-city road owner");
        check(records(city).size() == 29, "actual generate persists parent plus 28 child plans");
        check(city.flag(parent.id(), "city-plots-planned"), "city plan marker persisted");
        Set<String> expectedPrefabs = new HashSet<>();
        for (JsonElement definition : RemasterCatalog.config().getAsJsonArray("structures")) {
            JsonObject d = definition.getAsJsonObject();
            if ("city_variant".equals(d.get("category").getAsString())) expectedPrefabs.add(d.get("id").getAsString());
        }
        check(expectedPrefabs.size() == 28, "catalog city roster is nonempty and exactly 28");
        Set<String> actualPrefabs = new HashSet<>();
        int untouchedPlans = 0;
        for (NBTTagCompound record : records(city)) {
            RemasterSite child = RemasterSite.read(record);
            if ("city-grid".equals(child.layout)) continue;
            check("city-plot".equals(child.layout), "recovered record is a city child");
            check(actualPrefabs.add(child.prefab), "each authored child prefab occurs once");
            check(child.roadVersion == 0, "legacy child road version retained");
            if (existingChildren.containsKey(child.id())) {
                check(record.toString().equals(existingChildren.get(child.id())), "saved child complete NBT preserved");
            } else {
                check(child.y == parent.y, "unpopulated legacy child uses original parent Y");
                check(record.getCompoundTag("state").func_150296_c().isEmpty(), "child plan has no geometry node or reward state");
                untouchedPlans++;
            }
        }
        check(actualPrefabs.equals(expectedPrefabs) && untouchedPlans == 26, "all 28 real children and 26 state-empty plans");
        assertFlatRoad(cityWorld, parent, brick, 192, "legacy first owner");
        Map<String, String> allChildrenBefore = childRecords(city);
        NBTTagCompound parentStateBefore = (NBTTagCompound) city.state(parent.id()).copy();
        check(RemasterWorldgen.generate(cityWorld, pcx + 1, pcz), "actual generate second road owner");
        check(city.flag(parent.id(), "city-plots-planned") && records(city).size() == 29,
            "second owner retains plan marker and record count");
        check(childRecords(city).equals(allChildrenBefore), "second owner leaves all child anchors claims and states unchanged");
        NBTTagCompound parentStateAfter = (NBTTagCompound) city.state(parent.id()).copy();
        check(parentStateAfter.getBoolean("geom:" + (pcx + 1) + ":" + pcz), "second owner completes only its geometry");
        parentStateAfter.removeTag("geom:" + (pcx + 1) + ":" + pcz);
        check(parentStateAfter.equals(parentStateBefore), "second owner adds no plan node or reward state");
        assertFlatRoad(cityWorld, parent, brick, 320, "legacy two owners");
        int writesBefore = cityWorld.writes;
        Map<String, Block> blocksBefore = new HashMap<>(cityWorld.blocks);
        Map<String, Integer> metasBefore = new HashMap<>(cityWorld.metas);
        NBTTagCompound savedBefore = new NBTTagCompound();
        city.writeToNBT(savedBefore);
        RemasterSite wrongY = new RemasterSite(parent.prefab, parent.variant, parent.seed,
            parent.x, parent.y + 40, parent.z, parent.layout, 1);
        RemasterWorldgen.placeChunk(cityWorld, wrongY, pcx, pcz);
        check(RemasterWorldgen.generate(cityWorld, pcx + 1, pcz), "second owner repeat remains discoverable");
        NBTTagCompound savedAfter = new NBTTagCompound();
        city.writeToNBT(savedAfter);
        check(savedBefore.equals(savedAfter), "same ID wrongY new road and repeat owner preserve complete NBT");
        check(cityWorld.writes == writesBefore && cityWorld.blocks.equals(blocksBefore)
            && cityWorld.metas.equals(metasBefore), "same ID wrongY and repeat owner do not rewrite geometry");
        NativeTerrainFixture.assertNormal();
        JsonObject report = new JsonObject();
        report.addProperty("status", "PASS");
        report.addProperty("assertions", checks);
        report.addProperty("majorRecords", records(major).size());
        report.addProperty("savedCityRecords", records(city).size());
        report.addProperty("realChildPlans", actualPrefabs.size());
        report.addProperty("untouchedChildPlans", untouchedPlans);
        report.addProperty("legacyFlatRoadColumns", cityWorld.blocks.size());
        report.addProperty("oldCityParent", parent.id());
        report.addProperty("fixtureAdapters", "isolated MC World MapStorage and local block map; Major completed owner unloaded; city two loaded road chunks; actual BlockStoneBrick; vanilla registry substitutes FML material bootstrap only");
        Files.write(Paths.get(args[0]), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
        System.out.println("SAVED_PLACEMENT_PASS assertions=" + checks + " realChildren=" + actualPrefabs.size());
    }
}
