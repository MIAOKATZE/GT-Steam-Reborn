package com.miaokatze.gtsr.common.critical;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import net.minecraft.block.Block;
import net.minecraft.nbt.*;
import com.google.gson.*;

/** Two-process, real-jar layout comparison. No product classes or geometry algorithms are overlaid. */
public final class CriticalLayoutFidelityProbe {
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    @SuppressWarnings("unchecked")
    private static void materials() throws Exception {
        Class.forName(Block.class.getName(), true, Block.class.getClassLoader());
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
        Field registry = Block.class.getDeclaredField("blockRegistry");
        Field field = CriticalMaterials.class.getDeclaredField("MATERIALS"); field.setAccessible(true);
        Map<String, Block> map = (Map<String, Block>) field.get(null);
        JsonObject palette = CriticalGeometry.readJson("materials.json");
        TreeMap<String, JsonObject> definitions = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : palette.entrySet()) {
            JsonObject row = entry.getValue().getAsJsonObject(); definitions.put(row.get("id").getAsString(), row);
        }
        Constructor<?> constructor;
        Method definition = null;
        try {
            constructor = CriticalMaterials.MaterialBlock.class.getDeclaredConstructor(String.class, boolean.class, boolean.class, boolean.class);
        } catch (NoSuchMethodException typed) {
            Class<?> definitionsClass = Class.forName("com.miaokatze.gtsr.common.critical.CriticalMaterialDefinitions");
            definition = definitionsClass.getMethod("definition", String.class);
            constructor = CriticalMaterials.MaterialBlock.class.getDeclaredConstructor(definition.getReturnType());
        }
        constructor.setAccessible(true);
        for (Map.Entry<String, JsonObject> entry : definitions.entrySet()) {
            JsonObject row = entry.getValue(); String id = entry.getKey();
            Block block = (Block) (definition == null ? constructor.newInstance(id,
                row.has("transparent") && row.get("transparent").getAsBoolean(),
                row.has("animated") && row.get("animated").getAsBoolean(),
                row.has("emissive") && row.get("emissive").getAsBoolean())
                : constructor.newInstance(definition.invoke(null, id)));
            map.put(id, block);
        }
        unsafe.putObject(unsafe.staticFieldBase(registry), unsafe.staticFieldOffset(registry), new net.minecraft.util.RegistryNamespaced());
        int registryId = 3000;
        for (String id : definitions.keySet()) Block.blockRegistry.addObject(registryId++, "gtsr:critical_" + id, map.get(id));
    }
    @SuppressWarnings("unchecked")
    private static List<CriticalGeometry.Voxel> geometry(CriticalMachineState state) throws Exception {
        Method method = CriticalMachineState.class.getDeclaredMethod("allVoxels"); method.setAccessible(true);
        return (List<CriticalGeometry.Voxel>) method.invoke(state);
    }
    private static NBTTagCompound seed(int version, int revision, boolean owner, CriticalMachineKind kind, int p, int s, int e) {
        NBTTagCompound n = new NBTTagCompound(), config = new NBTTagCompound();
        config.setInteger("tier", CriticalTier.T3.ordinal()); config.setInteger("kind", kind.ordinal());
        config.setInteger("parallel", p); config.setInteger("speed", s); config.setInteger("economy", e);
        n.setTag("config", config); n.setInteger("geometryVersion", version); n.setInteger("v2LayoutRevision", revision);
        n.setBoolean("loomController", owner); n.setBoolean("loomJournalOwner", owner);
        n.setInteger("structureFacing", 2); n.setInteger("state", 2); n.setInteger("stage", 3); n.setInteger("cursor", 17);
        n.setString("structureId", "11111111-1111-1111-1111-111111111111");
        n.setString("ownerUuid", "layout-fidelity"); n.setString("jobId", "paid-old-index-snapshot");
        n.setLong("constructionWorkTicks", 37); n.setLong("constructionTotalWorkTicks", 89);
        return n;
    }
    private static String voxel(CriticalMachineState state, int index, CriticalGeometry.Voxel v) {
        String id = ((CriticalMaterials.MaterialBlock) v.block).materialId;
        return index + "\t" + v.x + "\t" + v.y + "\t" + v.z + "\t" + v.material + "\t" + id
            + "\t" + Block.blockRegistry.getNameForObject(v.block) + "\t" + Block.getIdFromBlock(v.block)
            + "\t" + v.metadata + "\t" + v.stage + "\t" + v.group + "\t" + v.model
            + "\t" + CriticalLoomStructure.isLoomVoxel(v) + "\t" + CriticalStructures.isHatchPosition(state, v)
            + "\t" + CriticalStructures.isPaidChamberPosition(v);
    }
    private static int dump(Path out, String name, CriticalMachineState state, List<CriticalGeometry.Voxel> voxels) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(out.resolve(name + ".tsv.gz"))), StandardCharsets.UTF_8))) {
            for (int i = 0; i < voxels.size(); i++) { writer.write(voxel(state, i, voxels.get(i))); writer.newLine(); }
        }
        return voxels.size();
    }
    private static String bytes(NBTTagCompound n, String key) { return Base64.getEncoder().encodeToString(n.getByteArray(key)); }
    private static JsonObject inspect(Path out, Path seeds, boolean create, String name, NBTTagCompound input) throws Exception {
        Path saved = seeds.resolve(name + ".nbt");
        if (create) {
            CriticalMachineState old = new CriticalMachineState(); old.readFromNBT(input);
            List<CriticalGeometry.Voxel> all = geometry(old); set(old, "jobFingerprint", field(old, "modelFingerprint"));
            BitSet placed = new BitSet();
            for (int i : new int[]{0, 1, 7, 8, 63, 64, 127, all.size() / 2, all.size() - 1}) placed.set(i);
            set(old, "placed", placed); BitSet repair = new BitSet(); repair.set(64); repair.set(all.size() - 1); set(old, "repairTargets", repair);
            NBTTagCompound snapshot = new NBTTagCompound(); old.writeToNBT(snapshot);
            try (OutputStream stream = Files.newOutputStream(saved)) { CompressedStreamTools.writeCompressed(snapshot, stream); }
        }
        NBTTagCompound snapshot;
        try (InputStream stream = Files.newInputStream(saved)) { snapshot = CompressedStreamTools.readCompressed(stream); }
        CriticalMachineState state = new CriticalMachineState(); state.readFromNBT(snapshot);
        List<CriticalGeometry.Voxel> all = geometry(state);
        JsonObject result = new JsonObject();
        result.addProperty("voxels", dump(out, name + "-all", state, all));
        result.addProperty("hatchPorts", dump(out, name + "-hatches", state, state.getHatchPorts()));
        result.addProperty("resourcePorts", dump(out, name + "-resources", state, state.getResourceHatchPorts()));
        result.addProperty("optionalPorts", dump(out, name + "-optional", state, CriticalGeometry.optionalWorkPorts(state)));
        result.addProperty("plugins", dump(out, name + "-plugins", state, state.getGeometryVersion() == 2 ? CriticalGeometry.physicalPlugins(state) : Collections.emptyList()));
        result.addProperty("modelFingerprint", String.valueOf(field(state, "modelFingerprint")));
        result.addProperty("jobFingerprint", String.valueOf(field(state, "jobFingerprint")));
        result.addProperty("geometryVersion", state.getGeometryVersion()); result.addProperty("revision", state.getV2LayoutRevision());
        result.addProperty("legacy", state.usesLegacyV2Geometry()); result.addProperty("loomSlug", CriticalGeometry.visualLoomSlug(state));
        result.addProperty("baseSlug", CriticalGeometry.visualBaseSlug(state)); result.addProperty("machineSlug", CriticalGeometry.visualMachineSlug(state));
        result.addProperty("controller", state.getControllerX() + "," + state.getControllerY() + "," + state.getControllerZ());
        result.addProperty("pluginsConfig", state.getParallelPlugins() + "," + state.getSpeedPlugins() + "," + state.getEfficiencyPlugins());
        NBTTagCompound roundtrip = new NBTTagCompound(); state.writeToNBT(roundtrip);
        result.addProperty("placed", bytes(roundtrip, "placed")); result.addProperty("repairTargets", bytes(roundtrip, "repairTargets"));
        result.addProperty("stage", roundtrip.getInteger("stage")); result.addProperty("cursor", roundtrip.getInteger("cursor"));
        BitSet placed = BitSet.valueOf(roundtrip.getByteArray("placed")); JsonArray paid = new JsonArray();
        for (int i = placed.nextSetBit(0); i >= 0; i = placed.nextSetBit(i + 1)) paid.add(new JsonPrimitive(voxel(state, i, all.get(i))));
        result.add("paidIndices", paid);
        // Construction routes use a different funding port set before recipe state 2.
        set(state, "state", 1); result.addProperty("constructionResourcePorts", dump(out, name + "-construction-resources", state, state.getResourceHatchPorts()));
        System.out.println(name + " voxels=" + all.size());
        return result;
    }
    public static void main(String[] args) throws Exception {
        Path jar = Paths.get(args[0]).toRealPath(), out = Paths.get(args[1]), seeds = Paths.get(args[2]); boolean create = Boolean.parseBoolean(args[3]);
        Files.createDirectories(out); Files.createDirectories(seeds); JsonObject origins = new JsonObject();
        for (String name : new String[]{"CriticalGeometry", "CriticalMaterials", "CriticalMachineState", "CriticalConfiguration", "CriticalStructures", "CriticalLoomStructure", "CriticalTier", "CriticalMachineKind", "CriticalStructureAlignment"}) {
            Class<?> type = Class.forName("com.miaokatze.gtsr.common.critical." + name);
            Path origin = Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if (!origin.equals(jar)) throw new AssertionError("Product overlay: " + name + "=" + origin);
            origins.addProperty(name, origin.toString());
        }
        materials(); JsonObject cases = new JsonObject();
        cases.add("v1-sun", inspect(out, seeds, create, "v1-sun", seed(1, 25, false, CriticalMachineKind.SUN, 0, 0, 0)));
        cases.add("legacy-r25", inspect(out, seeds, create, "legacy-r25", seed(2, 25, false, CriticalMachineKind.SUN, 0, 0, 0)));
        for (int revision : new int[]{25, 27, 30}) {
            String name = "r" + revision + "-zero"; cases.add(name, inspect(out, seeds, create, name, seed(2, revision, true, CriticalMachineKind.SUN, 0, 0, 0)));
        }
        for (CriticalMachineKind kind : CriticalMachineKind.releasedValues()) {
            String name = "current-" + kind.key; cases.add(name, inspect(out, seeds, create, name, seed(2, 31, true, kind, 8, 0, 0)));
        }
        int[][] edges = {{25, 8, 0, 0}, {27, 0, 8, 0}, {30, 0, 0, 8}, {30, 3, 2, 3}};
        for (int[] edge : edges) {
            String name = "r" + edge[0] + "-plugins-" + edge[1] + edge[2] + edge[3];
            cases.add(name, inspect(out, seeds, create, name, seed(2, edge[0], true, CriticalMachineKind.SOLAR, edge[1], edge[2], edge[3])));
        }
        JsonObject report = new JsonObject(); report.add("codeSources", origins); report.add("cases", cases);
        Files.write(out.resolve("actual.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
    }
}
