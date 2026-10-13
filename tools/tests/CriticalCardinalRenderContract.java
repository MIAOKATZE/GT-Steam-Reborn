package com.miaokatze.gtsr.client.critical;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalTier;

/** Source-compiled render math and captured native vertices. Never creates GL, Minecraft, or FML. */
public final class CriticalCardinalRenderContract {

    private static final ForgeDirection[] FACES = { ForgeDirection.NORTH, ForgeDirection.EAST,
        ForgeDirection.SOUTH, ForgeDirection.WEST };
    private static final double[] IDENTITY = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
    private static long assertions, cubes, fxPoints, lightQueries;
    private static double maximumError;

    private static void check(boolean pass, String message) {
        assertions++;
        if (!pass) throw new AssertionError(message);
    }

    private static void equal(double actual, double expected, String message) {
        double error = Math.abs(actual - expected);
        check(Double.isFinite(error) && error < 1e-8, message + ": " + actual + " != " + expected);
        maximumError = Math.max(maximumError, error);
    }

    private static CriticalMachineState state(int revision, boolean selector, ForgeDirection facing) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("geometryVersion", 2);
        nbt.setInteger("v2LayoutRevision", revision);
        nbt.setBoolean("loomController", selector);
        nbt.setBoolean("loomJournalOwner", selector);
        nbt.setBoolean("structureFacingLocked", true);
        nbt.setInteger("structureFacing", facing.ordinal());
        nbt.setInteger("state", 1);
        nbt.setString("jobId", "render-contract");
        nbt.setTag("config", new CriticalConfiguration(CriticalTier.T1, CriticalMachineKind.SOLAR, 0, 0, 0).write());
        CriticalMachineState result = new CriticalMachineState();
        result.readFromNBT(nbt);
        result.xCoord = 80;
        result.yCoord = 70;
        result.zCoord = -96;
        return result;
    }

    /** Independent expected basis; does not call the production alignment or inverse. */
    private static double[] expected(CriticalMachineState state, boolean selector, ForgeDirection facing, double[] p) {
        double dx = p[0] - 56.5, dz = p[2] - (selector ? 47.5 : 35.5);
        double x, z;
        switch (facing) {
            case EAST -> { x = -dz; z = dx; }
            case SOUTH -> { x = -dx; z = -dz; }
            case WEST -> { x = dz; z = -dx; }
            default -> { x = dx; z = dz; }
        }
        return new double[] { state.xCoord + .5 + x, state.yCoord - 4 + p[1], state.zCoord + .5 + z };
    }

    /** Emulates only the two documented outer GL operations with Java trig, without calling GL. */
    private static double[] rendered(CriticalMachineState state, double[] p) {
        double angle = Math.toRadians(state.getStructureYawDegrees()), c = Math.cos(angle), s = Math.sin(angle);
        return new double[] { state.getMeshOriginX() + c * p[0] + s * p[2], state.getOriginY() + p[1],
            state.getMeshOriginZ() - s * p[0] + c * p[2] };
    }

    private static double[] posed(double[] matrix, double[] p) {
        double[] out = new double[3];
        for (int row = 0; row < 3; row++)
            out[row] = matrix[row] * p[0] + matrix[4 + row] * p[1] + matrix[8 + row] * p[2] + matrix[12 + row];
        return out;
    }

    private static void point(double[] actual, double[] expected, String message) {
        for (int axis = 0; axis < 3; axis++) equal(actual[axis], expected[axis], message + " axis " + axis);
    }

    private static void cubes(Path models) throws Exception {
        Method face = CriticalMaterialRenderer.class.getDeclaredMethod("face", Tessellator.class, double.class,
            double.class, double.class, int.class, IIcon.class, double.class);
        face.setAccessible(true);
        Field corners = CriticalMeshCache.class.getDeclaredField("CORNERS");
        corners.setAccessible(true);
        double[][][] nativeCorners = (double[][][]) corners.get(null);
        IIcon icon = (IIcon) Proxy.newProxyInstance(IIcon.class.getClassLoader(), new Class<?>[] { IIcon.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getMinU", "getMinV" -> 0F;
                case "getMaxU", "getMaxV" -> 1F;
                case "getInterpolatedU", "getInterpolatedV" -> ((Double) args[0]).floatValue() / 16F;
                case "getIconWidth", "getIconHeight" -> 16;
                case "getIconName" -> "detached-cube";
                default -> throw new UnsupportedOperationException(method.getName());
            });
        for (String slug : List.of("v2_loom", "v2_base_t3", "v2_solar", "v2_sun")) {
            JsonArray blocks = read(models, slug).getAsJsonArray("blocks");
            for (boolean selector : new boolean[] { false, true }) for (ForgeDirection facing : FACES) {
                CriticalMachineState state = state(27, selector, facing);
                for (int i = 0; i < blocks.size(); i += 97) {
                    JsonArray voxel = blocks.get(i).getAsJsonArray();
                    int vx = voxel.get(0).getAsInt(), vy = voxel.get(1).getAsInt(), vz = voxel.get(2).getAsInt();
                    double[] worldCenter = expected(state, selector, facing, new double[] { vx + .5, vy + .5, vz + .5 });
                    int wx = (int) Math.floor(worldCenter[0]), wy = (int) Math.floor(worldCenter[1]),
                        wz = (int) Math.floor(worldCenter[2]);
                    check(state.blockWorldX(vx, vz) == wx && state.blockWorldY(vy) == wy
                        && state.blockWorldZ(vx, vz) == wz, "Physical voxel world min " + facing);
                    Set<String> nativeVertices = new HashSet<>(), chunkVertices = new HashSet<>();
                    for (int side = 0; side < 6; side++) {
                        for (double[] vertex : nativeCorners[side]) {
                            double[] local = { vx + vertex[0], vy + vertex[1], vz + vertex[2] };
                            double[] rendered = rendered(state, local);
                            point(rendered, expected(state, selector, facing, local), "Native cube world vertex " + facing);
                            nativeVertices.add(rounded(rendered));
                        }
                        Capture tessellator = new Capture();
                        face.invoke(null, tessellator, (double) wx, (double) wy, (double) wz, side, icon, 0D);
                        for (double[] vertex : tessellator.vertices) chunkVertices.add(rounded(vertex));
                    }
                    check(nativeVertices.equals(chunkVertices), "Native transformed cube equals actual chunk geometry " + facing);
                    int[] light = CriticalLightCache.sampleBlock(new double[] { vx + .5, vy + .5, vz + .5 }, IDENTITY,
                        state.getMeshOriginX(), state.getOriginY(), state.getMeshOriginZ(), facing);
                    check(Arrays.equals(light, new int[] { wx, wy, wz }), "Idle sample uses same physical cell " + facing);
                    cubes++;
                }
            }
        }
    }

    private static String rounded(double[] p) {
        return Math.round(p[0]) + ":" + Math.round(p[1]) + ":" + Math.round(p[2]);
    }

    private static void mechanicalAndCamera() throws Exception {
        JsonObject animation = JsonParser.parseString("{\"pivot\":[3,4,5],\"axis\":\"y\",\"cycle\":{\"keyframes\":["
            + "{\"phase\":0,\"angle\":0,\"offset\":[0,0,0]},"
            + "{\"phase\":1,\"angle\":1.5707963267948966,\"offset\":[2,-1,3]}]}}").getAsJsonObject();
        CriticalControllerRenderer renderer = new CriticalControllerRenderer();
        Method pose = CriticalControllerRenderer.class.getDeclaredMethod("pose", JsonObject.class, double.class,
            double.class, String.class);
        pose.setAccessible(true);
        double[] matrix = (double[]) pose.invoke(renderer, animation, 1D, 0D, "build");
        double[] p = { 8.5, 7.5, 12.5 }, expectedPose = { 12.5, 6.5, 3.5 };
        point(posed(matrix, p), expectedPose, "Independent quarter-turn around JSON center pivot plus translation");
        for (boolean selector : new boolean[] { false, true }) for (ForgeDirection facing : FACES) {
            CriticalMachineState state = state(27, selector, facing);
            point(rendered(state, posed(matrix, p)), expected(state, selector, facing, expectedPose),
                "Owner rotation follows mechanical pose exactly once " + facing);
            double[] cameraLocal = { 42.25, 28.75, 66.125 };
            double[] worldCamera = expected(state, selector, facing, cameraLocal);
            double[] inverse = { state.localPointX(worldCamera[0], worldCamera[2]), worldCamera[1] - state.getOriginY(),
                state.localPointZ(worldCamera[0], worldCamera[2]) };
            point(inverse, cameraLocal, "Independent camera inverse " + facing);
            double localDistance = distance(expectedPose, inverse);
            equal(localDistance, distance(expected(state, selector, facing, expectedPose), worldCamera),
                "Blended batch distance in local frame equals world distance " + facing);
            double[] lodCenter = { 56, state.getRegionHeight() * .55, 56 };
            equal(distance(inverse, lodCenter), distance(worldCamera, expected(state, selector, facing, lodCenter)),
                "Particle camera LOD world distance " + facing);
        }
        // A shared journal/scene seen from opposite controller fronts must produce one world transform.
        for (ForgeDirection facing : FACES) {
            CriticalMachineState loom = state(27, true, facing), main = state(27, false, facing);
            double[] mainHost = expected(loom, true, facing, new double[] { 56.5, 4.5, 35.5 });
            main.xCoord = (int) Math.floor(mainHost[0]);
            main.zCoord = (int) Math.floor(mainHost[2]);
            point(rendered(main, expectedPose), rendered(loom, expectedPose), "Main/loom scene same owner frame " + facing);
            check(main.getControllerFacing(false) == loom.getControllerFacing(true).getOpposite(),
                "Main and loom opposite physical front " + facing);
        }
    }

    private static double distance(double[] a, double[] b) {
        double result = 0;
        for (int axis = 0; axis < 3; axis++) result += (a[axis] - b[axis]) * (a[axis] - b[axis]);
        return result;
    }

    private static void nativeLight() throws Exception {
        SampleWorld world = world();
        CriticalLightCache cache = new CriticalLightCache();
        double[][] samples = { { 13.125, 6.75, 63.875 }, { 37.875, 2.125, 2.25 }, { 78.5, 17.5, 100.5 } };
        for (ForgeDirection facing : FACES) {
            CriticalMachineState state = state(27, true, facing);
            world.queried.clear();
            cache.sample(world, "same-cache-key", samples, IDENTITY, state.getMeshOriginX(), state.getOriginY(),
                state.getMeshOriginZ(), facing, 0);
            Set<String> independent = new HashSet<>();
            for (double[] p : samples) {
                double[] expected = expected(state, true, facing, p);
                independent.add((int) Math.floor(expected[0]) + ":" + (int) Math.floor(expected[1]) + ":"
                    + (int) Math.floor(expected[2]));
            }
            check(new HashSet<>(world.queried).equals(independent), "Actual World light queries/invalidation on facing switch " + facing);
            lightQueries += world.queried.size();
        }
        // Same-tick frame changes above must refresh each time while preserving the 16-query budget.
        check(CriticalRenderMetrics.lastWorldLightSamples == 12, "World light query budget counts all same-tick rotations");
        for (ForgeDirection facing : FACES) {
            CriticalMachineState state = state(27, false, facing);
            double[] matrix = CriticalMechanicalPose.matrix(JsonParser.parseString(
                "{\"pivot\":[3,4,5],\"axis\":\"y\",\"cycle\":{\"keyframes\":["
                + "{\"phase\":1,\"angle\":1.5707963267948966,\"offset\":[2,-1,3]}]}}")
                .getAsJsonObject(), 1, 0, "build");
            int[] actual = CriticalLightCache.sampleBlock(new double[] { 8.5, 7.5, 12.5 }, matrix,
                state.getMeshOriginX(), state.getOriginY(), state.getMeshOriginZ(), facing);
            double[] expected = expected(state, false, facing, new double[] { 12.5, 6.5, 3.5 });
            check(Arrays.equals(actual, new int[] { (int) Math.floor(expected[0]), (int) Math.floor(expected[1]),
                (int) Math.floor(expected[2]) }), "Mechanical pose then frame light sample " + facing);
        }
    }

    private static void visibility() throws Exception {
        var voxelConstructor = CriticalGeometry.Voxel.class.getDeclaredConstructor(int.class, int.class, int.class,
            Block.class, int.class, String.class, String.class, String.class);
        voxelConstructor.setAccessible(true);
        List<CriticalGeometry.Voxel> voxels = List.of(
            voxelConstructor.newInstance(13, 7, 82, null, 0, "fixture", "fixture", "v2_solar"),
            voxelConstructor.newInstance(110, 2, 0, null, 0, "fixture", "fixture", "v2_solar"));
        Class<?> groupClass = Class.forName(CriticalGroupVisibility.class.getName() + "$Group");
        var groupConstructor = groupClass.getDeclaredConstructor(String.class, List.class);
        groupConstructor.setAccessible(true);
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        String previousIdentity = null;
        for (ForgeDirection facing : FACES) {
            SampleWorld world = world();
            CriticalMachineState state = state(27, true, facing);
            state.worldObj = world;
            CriticalGroupVisibility visibility = (CriticalGroupVisibility) unsafe.allocateInstance(CriticalGroupVisibility.class);
            Field groupsField = CriticalGroupVisibility.class.getDeclaredField("groups");
            groupsField.setAccessible(true);
            groupsField.set(visibility, List.of(groupConstructor.newInstance("v2_solar:fixture", voxels)));
            Field visibleField = CriticalGroupVisibility.class.getDeclaredField("visible");
            visibleField.setAccessible(true);
            visibleField.set(visibility, new HashSet<String>());
            check(visibility.update(state).contains("v2_solar:fixture"), "Actual group complete physical cells " + facing);
            Set<String> independent = new HashSet<>();
            for (CriticalGeometry.Voxel voxel : voxels) {
                double[] p = expected(state, true, facing, new double[] { voxel.x + .5, voxel.y + .5, voxel.z + .5 });
                independent.add((int) Math.floor(p[0]) + ":" + (int) Math.floor(p[1]) + ":" + (int) Math.floor(p[2]));
            }
            check(new HashSet<>(world.blockQueries).equals(independent), "Actual visibility World queries use rotated physical cells " + facing);
            String identity = CriticalGroupVisibility.identity(state);
            check(!identity.equals(previousIdentity), "Visibility cached identity changes with owner frame " + facing);
            previousIdentity = identity;
        }
    }

    private static void solarFX(Path models) throws Exception {
        JsonObject[] data = { read(models, "v2_base_t3"), read(models, "v2_loom"), read(models, "v2_solar") };
        List<double[]> points = new ArrayList<>();
        CriticalSolarFX.sample(Arrays.stream(data).map(j -> j.getAsJsonObject("meta")).toArray(JsonObject[]::new),
            Arrays.stream(data).map(j -> j.getAsJsonObject("animationGroups")).toArray(JsonObject[]::new),
            new String[] { "v2_base_t3", "v2_loom", "v2_solar" }, .42, 3.125, 1, null,
            (family, index, p, color, alpha, glow, glowAlpha, size) -> points.add(p.clone()));
        check(!points.isEmpty(), "Actual solar source sample emits points");
        for (ForgeDirection facing : FACES) {
            CriticalMachineState state = state(27, true, facing);
            for (double[] p : points) {
                // Production FX vertices add .5 after the JSON center-space pose, just like the native mesh matrix.
                double[] center = { p[0] + .5, p[1] + .5, p[2] + .5 };
                point(rendered(state, center), expected(state, true, facing, center), "Actual FX world point " + facing);
                fxPoints++;
            }
        }
    }

    private static void sunFX() throws Exception {
        JsonObject data = JsonParser.parseString("{\"animationGroups\":{\"gravity_demo\":{\"pivot\":[3,4,5],"
            + "\"axis\":\"y\",\"cycle\":{\"keyframes\":[{\"phase\":1,\"angle\":1.5707963267948966,"
            + "\"offset\":[2,-1,3]}]}}},\"meta\":{\"gravityClusters\":[{\"group\":\"gravity_demo\","
            + "\"cells\":[[8,7,12]]}]}}").getAsJsonObject();
        var constructor = CriticalGeometry.Model.class.getDeclaredConstructor(List.class, JsonObject.class, JsonObject.class);
        constructor.setAccessible(true);
        CriticalGeometry.Model model = constructor.newInstance(List.of(), data.getAsJsonObject("animationGroups"),
            data.getAsJsonObject("meta"));
        Method gravity = CriticalSunFX.class.getDeclaredMethod("gravity", CriticalGeometry.Model.class, String.class,
            double.class, double.class, int.class, Set.class, Tessellator.class);
        gravity.setAccessible(true);
        Field remaining = CriticalFXBudget.class.getDeclaredField("remaining");
        remaining.setAccessible(true);
        remaining.setInt(null, 1536);
        Capture capture = new Capture();
        gravity.invoke(null, model, "v2_sun", 1D, 0D, 1, null, capture);
        check(capture.vertices.size() == 24, "Actual sun gravity draws exactly one cube");
        double[] center = new double[3];
        for (double[] vertex : capture.vertices) for (int axis = 0; axis < 3; axis++) center[axis] += vertex[axis] / 24;
        double[] independent = { 12.5, 6.5 - .65 - 2.8 * .49, 3.5 };
        point(center, independent, "Independent Sun gravity mechanical pose and falling displacement");
        for (ForgeDirection facing : FACES) {
            CriticalMachineState state = state(27, true, facing);
            point(rendered(state, center), expected(state, true, facing, independent), "Sun gravity owner world point " + facing);
            fxPoints++;
        }
    }

    private static void legacy() {
        for (int revision : new int[] { 24, 25, 26 }) for (ForgeDirection facing : FACES) {
            CriticalMachineState state = state(revision, true, facing);
            double[] p = { 13.25, 7.75, 82.5 };
            point(rendered(state, p), new double[] { 24 + p[0], 66 + p[1], -143 + p[2] },
                "Frozen legacy N transform exact revision " + revision);
            equal(state.getStructureYawDegrees(), 0, "Legacy yaw remains exactly zero");
        }
    }

    private static JsonObject read(Path models, String slug) throws Exception {
        return JsonParser.parseString(Files.readString(models.resolve(slug + ".geometry.json"))).getAsJsonObject();
    }

    public static void main(String[] args) throws Exception {
        Path models = Path.of(args[0]);
        cubes(models);
        mechanicalAndCamera();
        nativeLight();
        visibility();
        solarFX(models);
        sunFX();
        legacy();
        String report = "{\"status\":\"PASS\",\"assertions\":" + assertions + ",\"nativeChunkCubes\":" + cubes
            + ",\"worldLightQueries\":" + lightQueries + ",\"fxWorldPoints\":" + fxPoints
            + ",\"maximumAbsoluteError\":" + maximumError
            + ",\"scope\":\"Source-compiled math, native cube vertex capture, actual fixture World light queries, SolarFX sample and Sun gravity vertex capture. GL outer transform verified from source wiring and Java trig only; no GL/game startup.\"}";
        Files.writeString(Path.of(args[1]), report + System.lineSeparator());
        System.out.println(report);
    }

    private static final class Capture extends Tessellator {
        final List<double[]> vertices = new ArrayList<>();
        Capture() { super(); }
        @Override public void addVertexWithUV(double x, double y, double z, double u, double v) {
            vertices.add(new double[] { x, y, z });
        }
        @Override public void addVertex(double x, double y, double z) { vertices.add(new double[] { x, y, z }); }
        @Override public void setColorRGBA_F(float r, float g, float b, float a) {}
    }

    private static SampleWorld world() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        SampleWorld result = (SampleWorld) ((sun.misc.Unsafe) field.get(null)).allocateInstance(SampleWorld.class);
        result.queried = new ArrayList<>();
        result.blockQueries = new ArrayList<>();
        return result;
    }

    private static final class SampleWorld extends World {
        List<String> queried;
        List<String> blockQueries;
        private SampleWorld() { super(null, "unused", (WorldProvider) null, null, null); }
        @Override public long getTotalWorldTime() { return 1; }
        @Override public int getHeight() { return 256; }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
        @Override public Block getBlock(int x, int y, int z) { blockQueries.add(x + ":" + y + ":" + z); return null; }
        @Override public int getBlockMetadata(int x, int y, int z) { return 0; }
        @Override public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minimum) {
            queried.add(x + ":" + y + ":" + z);
            return (Math.floorMod(x + z, 16) * 16 << 16) | Math.floorMod(y, 16) * 16;
        }
        @Override protected IChunkProvider createChunkProvider() { throw new UnsupportedOperationException(); }
        @Override protected int func_152379_p() { throw new UnsupportedOperationException(); }
        @Override public Entity getEntityByID(int id) { throw new UnsupportedOperationException(); }
    }
}
