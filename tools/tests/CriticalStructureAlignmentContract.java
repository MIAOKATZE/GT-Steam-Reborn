package com.miaokatze.gtsr.common.critical;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureWalker;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

/** Detached coordinate contract. Uses the installed StructureLib implementation; starts no game runtime. */
public final class CriticalStructureAlignmentContract {

    private static long checked;

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
        checked++;
    }

    public static void main(String[] args) throws Exception {
        ExtendedFacing[] faces = { ExtendedFacing.NORTH_NORMAL_NONE, ExtendedFacing.EAST_NORMAL_NONE,
            ExtendedFacing.SOUTH_NORMAL_NONE, ExtendedFacing.WEST_NORMAL_NONE };
        int[][] expected = { { -3, 5, 7 }, { -7, 5, -3 }, { 3, 5, -7 }, { 7, 5, 3 } };
        for (int i = 0; i < faces.length; i++) {
            int[] actual = new int[3];
            faces[i].getWorldOffset(new int[] { 3, -5, 7 }, actual);
            check(Arrays.equals(actual, expected[i]), "Independent cardinal basis: " + faces[i]);
        }
        Path modelRoot = Path.of(args[0]);
        String[] slugs = { "v2_loom", "v2_v25_loom", "v2_v27_loom", "v2_legacy_loom", "v2_base_t3", "v2_v25_base_t3", "v2_v27_base_t3",
            "v2_legacy_base_t3", "v2_solar", "v2_sun" };
        for (String slug : slugs) {
            JsonArray blocks = JsonParser.parseString(Files.readString(modelRoot.resolve(slug + ".geometry.json")))
                .getAsJsonObject().getAsJsonArray("blocks");
            for (boolean selector : new boolean[] { true, false }) for (ForgeDirection frame : new ForgeDirection[] {
                ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST }) {
                int cx = 56, cy = 4, cz = selector ? 47 : 35;
                // Independently specified world origin; main and loom stand 12 world blocks apart.
                int ox = -160, oy = 64, oz = 192;
                CriticalMachineState state = fixture(27, selector, frame);
                state.xCoord = ox + cx;
                state.yCoord = oy + cy;
                state.zCoord = oz + cz;
                check(CriticalStructureAlignment.controllerA(selector)
                    == CriticalStructureAlignment.shapeA(cx, selector), "~ horizontal offset");
                check(CriticalStructureAlignment.controllerC(selector)
                    == CriticalStructureAlignment.shapeC(cz, selector), "~ depth offset");
                for (JsonElement entry : blocks) {
                    JsonArray row = entry.getAsJsonArray();
                    int vx = row.get(0).getAsInt(), vy = row.get(1).getAsInt(), vz = row.get(2).getAsInt();
                    int[] abc = { CriticalStructureAlignment.shapeA(vx, selector)
                        - CriticalStructureAlignment.controllerA(selector), cy - vy,
                        CriticalStructureAlignment.shapeC(vz, selector)
                            - CriticalStructureAlignment.controllerC(selector) };
                    int[] offset = new int[3];
                    CriticalStructureAlignment.facing(frame, selector).getWorldOffset(abc, offset);
                    int dx = vx - cx, dz = vz - cz;
                    int expectedX = state.xCoord + (frame == ForgeDirection.EAST ? -dz
                        : frame == ForgeDirection.SOUTH ? -dx : frame == ForgeDirection.WEST ? dz : dx);
                    int expectedZ = state.zCoord + (frame == ForgeDirection.EAST ? dx
                        : frame == ForgeDirection.SOUTH ? -dz : frame == ForgeDirection.WEST ? -dx : dz);
                    check(offset[0] + state.xCoord == expectedX && offset[1] + state.yCoord == oy + vy
                        && offset[2] + state.zCoord == expectedZ, "Independent world voxel: " + slug + " selector="
                            + selector + " voxel=" + row);
                    check(state.blockWorldX(vx, vz) == expectedX && state.blockWorldZ(vx, vz) == expectedZ,
                        "Actual state world block " + frame);
                    check(state.localBlockX(expectedX, expectedZ) == vx && state.localBlockZ(expectedX, expectedZ) == vz,
                        "Actual state inverse " + frame);
                    if (checked % 97 == 0) for (int cornerX : new int[] { 0, 1 }) for (int cornerZ : new int[] { 0, 1 }) {
                        double wx = state.getMeshOriginX() + CriticalStructureAlignment.rotateX(vx + cornerX, vz + cornerZ, frame);
                        double wz = state.getMeshOriginZ() + CriticalStructureAlignment.rotateZ(vx + cornerX, vz + cornerZ, frame);
                        check(wx >= expectedX && wx <= expectedX + 1 && wz >= expectedZ && wz <= expectedZ + 1,
                            "Cube center/corner correspondence " + frame);
                        check(state.localPointX(wx, wz) == vx + cornerX && state.localPointZ(wx, wz) == vz + cornerZ,
                            "Continuous actor/camera inverse " + frame);
                    }
                }
                check(state.getOriginX() <= state.blockWorldX(0, 0)
                    && state.getOriginX() <= state.blockWorldX(111, 111)
                    && state.getOriginZ() <= state.blockWorldZ(0, 0)
                    && state.getOriginZ() <= state.blockWorldZ(111, 111), "Region minimum corner " + frame);
            }
        }
        for (int revision : new int[] { 24, 25, 26 }) {
            CriticalMachineState legacy = fixture(revision, true, ForgeDirection.EAST);
            legacy.xCoord = 56; legacy.yCoord = 68; legacy.zCoord = 47;
            check(legacy.getStructureFacing() == ForgeDirection.NORTH && legacy.blockWorldX(13, 79) == 13
                && legacy.blockWorldZ(13, 79) == 79, "Frozen legacy world mapping " + revision);
            NBTTagCompound written = new NBTTagCompound(); legacy.writeToNBT(written);
            check(written.getInteger("v2LayoutRevision") == revision
                && written.getLong("jobFingerprint") == 0x1122334455667788L
                && Arrays.equals(written.getByteArray("placed"), new byte[] { 1, 4, 16, 64, (byte) 128 })
                && written.getString("jobId").equals("existing-job"), "Legacy journal fields and bytes " + revision);
        }
        realIteration();
        emptyControllerFacing();
        proxyIndex();
        freshNativeFront();
        nativeHatchClasses();
        String report = "{\"status\":\"PASS\",\"checks\":" + checked
            + ",\"runtime\":\"detached JVM; real StructureLib ExtendedFacing; no server or client startup\"}";
        Files.writeString(Path.of(args[1]), report + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println(report);
    }

    private static CriticalMachineState fixture(int revision, boolean selector, ForgeDirection facing) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("geometryVersion", 2); tag.setInteger("v2LayoutRevision", revision);
        tag.setBoolean("loomController", selector); tag.setBoolean("loomJournalOwner", selector);
        tag.setInteger("structureFacing", facing.ordinal()); tag.setBoolean("structureFacingLocked", true);
        tag.setInteger("state", 1); tag.setString("jobId", "existing-job");
        tag.setLong("jobFingerprint", 0x1122334455667788L);
        tag.setByteArray("placed", new byte[] { 1, 4, 16, 64, (byte) 128 });
        tag.setTag("config", new CriticalConfiguration(CriticalTier.T1, CriticalMachineKind.SOLAR, 0, 0, 0).write());
        CriticalMachineState state = new CriticalMachineState(); state.readFromNBT(tag); return state;
    }

    /** A tile supplies only front/world; production state binding and persistence remain unchanged. */
    private static void emptyControllerFacing() throws Exception {
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        for (boolean selector : new boolean[] { true, false }) for (ForgeDirection front : new ForgeDirection[] {
            ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST }) {
            FacingTile tile = (FacingTile) unsafe.allocateInstance(FacingTile.class);
            tile.front = front;
            CriticalMachineState state = new CriticalMachineState(); state.setLoomController(selector);
            state.bindHost(tile);
            ForgeDirection expected = selector ? front : front.getOpposite();
            check(state.getStructureFacing() == expected && state.getControllerFacing(selector) == front,
                "New empty controller follows actual front " + front + " selector=" + selector);
            NBTTagCompound tag = new NBTTagCompound(); state.writeToNBT(tag);
            CriticalMachineState restored = new CriticalMachineState(); restored.readFromNBT(tag); restored.bindHost(tile);
            check(restored.getStructureFacing() == expected, "Empty controller NBT follows actual front " + front);
            tile.front = front.getOpposite(); restored.bindHost(tile);
            check(restored.getControllerFacing(selector) == tile.front, "Unlocked empty controller can turn " + front);
            CriticalMachineState frozen = fixture(27, selector, expected); frozen.bindHost(tile);
            check(frozen.getStructureFacing() == expected, "Running journal ignores changed tile front " + front);
            NBTTagCompound persistent = new NBTTagCompound(); frozen.writeToNBT(persistent);
            CriticalMachineState reloaded = new CriticalMachineState(); reloaded.readFromNBT(persistent); reloaded.bindHost(tile);
            check(reloaded.getStructureFacing() == expected, "Running journal preserves frame after NBT " + front);
            tag.setInteger("v2LayoutRevision", 26); tag.removeTag("structureFacing"); tag.removeTag("structureFacingLocked");
            CriticalMachineState oldEmpty = new CriticalMachineState(); oldEmpty.readFromNBT(tag); oldEmpty.bindHost(tile);
            check(oldEmpty.getV2LayoutRevision() == 28 && oldEmpty.getControllerFacing(selector) == tile.front,
                "Uncommissioned old controller can follow current front " + front);
        }
    }

    private static final class FacingTile extends gregtech.api.metatileentity.BaseMetaTileEntity {
        ForgeDirection front;
        @Override public ForgeDirection getFrontFacing() { return front; }
        @Override public World getWorldObj() { return null; }
    }

    private static void proxyIndex() throws Exception {
        Class<?> registration = Class.forName(CriticalLoomProxyVisibility.class.getName() + "$Registration");
        java.lang.reflect.Constructor<?> constructor = registration.getDeclaredConstructor(World.class, int.class,
            int.class, int.class, int.class, int.class, ForgeDirection.class, String.class);
        constructor.setAccessible(true);
        java.lang.reflect.Method worldX = registration.getDeclaredMethod("worldX", CriticalGeometry.Voxel.class);
        java.lang.reflect.Method worldZ = registration.getDeclaredMethod("worldZ", CriticalGeometry.Voxel.class);
        worldX.setAccessible(true); worldZ.setAccessible(true);
        java.lang.reflect.Constructor<CriticalGeometry.Voxel> voxelConstructor = CriticalGeometry.Voxel.class
            .getDeclaredConstructor(int.class, int.class, int.class, net.minecraft.block.Block.class,
                int.class, String.class, String.class, String.class);
        voxelConstructor.setAccessible(true);
        for (ForgeDirection frame : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
            ForgeDirection.SOUTH, ForgeDirection.WEST }) {
            CriticalMachineState state = fixture(27, true, frame);
            state.xCoord = -96; state.yCoord = 64; state.zCoord = 160;
            Object snapshot = constructor.newInstance(null, state.getOriginX(), state.getOriginY(), state.getOriginZ(),
                state.blockWorldX(0, 0), state.blockWorldZ(0, 0), frame, "v2_loom");
            for (int[] point : new int[][] { { 0, 0 }, { 111, 111 }, { 56, 47 }, { 45, 82 } }) {
                CriticalGeometry.Voxel voxel = voxelConstructor.newInstance(point[0], 4, point[1], null, 0,
                    "panel", "moving", "v2_loom");
                int dx = point[0] - 56, dz = point[1] - 47;
                int expectedX = -96 + (frame == ForgeDirection.EAST ? -dz : frame == ForgeDirection.SOUTH ? -dx
                    : frame == ForgeDirection.WEST ? dz : dx);
                int expectedZ = 160 + (frame == ForgeDirection.EAST ? dx : frame == ForgeDirection.SOUTH ? -dz
                    : frame == ForgeDirection.WEST ? -dx : dz);
                check((int) worldX.invoke(snapshot, voxel) == expectedX && (int) worldZ.invoke(snapshot, voxel) == expectedZ,
                    "Actual proxy registration world index " + frame);
            }
        }
    }

    /** Only the loading predicate is supplied; the actual StructureLib builder and traversal run unchanged. */
    private static void realIteration() throws Exception {
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        World world = (World) unsafe.allocateInstance(LoadedCoordinates.class);
        int[][] samples = { { 54, 4, 49 }, { 62, 7, 45 }, { 0, 0, 111 }, { 111, 6, 0 } };
        for (boolean selector : new boolean[] { true, false }) for (ForgeDirection frame : new ForgeDirection[] {
            ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST }) {
            char[][][] grid = new char[112][8][112];
            for (char[][] slice : grid) for (char[] row : slice) Arrays.fill(row, ' ');
            for (int[] p : samples) grid[CriticalStructureAlignment.shapeC(p[2], selector)][7 - p[1]]
                [CriticalStructureAlignment.shapeA(p[0], selector)] = 'A';
            grid[CriticalStructureAlignment.controllerC(selector)][3][CriticalStructureAlignment.controllerA(selector)] = '~';
            String[][] shape = new String[112][8];
            for (int z = 0; z < 112; z++) for (int y = 0; y < 8; y++) shape[z][y] = new String(grid[z][y]);
            IStructureElement<Object> element = new IStructureElement<>() {
                public boolean check(Object context, World w, int x, int y, int z) { return true; }
                public boolean spawnHint(Object context, World w, int x, int y, int z, ItemStack stack) { return true; }
                public boolean placeBlock(Object context, World w, int x, int y, int z, ItemStack stack) { return false; }
            };
            IStructureDefinition<Object> definition = StructureDefinition.<Object>builder().addShape("loom", shape)
                .addElement('A', element).build();
            check(definition.isContainedInStructure("loom", CriticalStructureAlignment.controllerA(selector), 3,
                CriticalStructureAlignment.controllerC(selector)), "Real SL ~ occupied controller " + frame);
            Set<String> actual = new HashSet<>();
            definition.iterate("loom", world, CriticalStructureAlignment.facing(frame, selector), 80, 70, -96,
                CriticalStructureAlignment.controllerA(selector), 3, CriticalStructureAlignment.controllerC(selector),
                new IStructureWalker<>() {
                    public boolean visit(IStructureElement<Object> e, World w, int x, int y, int z, int a, int b, int c) {
                        actual.add(x + "," + y + "," + z); return true;
                    }
                    public boolean blockNotLoaded(IStructureElement<Object> e, World w, int x, int y, int z, int a, int b, int c) {
                        throw new AssertionError("Detached loading predicate unexpectedly false");
                    }
                });
            Set<String> expected = new HashSet<>();
            for (int[] p : samples) {
                int dx = p[0] - 56, dz = p[2] - (selector ? 47 : 35);
                int x = 80 + (frame == ForgeDirection.EAST ? -dz : frame == ForgeDirection.SOUTH ? -dx
                    : frame == ForgeDirection.WEST ? dz : dx);
                int z = -96 + (frame == ForgeDirection.EAST ? dx : frame == ForgeDirection.SOUTH ? -dz
                    : frame == ForgeDirection.WEST ? -dx : dz);
                expected.add(x + "," + (70 + p[1] - 4) + "," + z);
            }
            check(actual.equals(expected), "Real SL iterate independent coordinates " + frame + " selector=" + selector);
        }
    }

    private static final class LoadedCoordinates extends World {
        private LoadedCoordinates() { super(null, "unused", (WorldProvider) null, null, null); }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
        @Override protected IChunkProvider createChunkProvider() { throw new UnsupportedOperationException(); }
        @Override protected int func_152379_p() { throw new UnsupportedOperationException(); }
        @Override public Entity getEntityByID(int id) { throw new UnsupportedOperationException(); }
    }

    private static void freshNativeFront() throws Exception {
        java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
        java.lang.reflect.Field front = gregtech.api.metatileentity.BaseMetaTileEntity.class.getDeclaredField("mFacing");
        front.setAccessible(true);
        for (boolean selector : new boolean[] { true, false }) {
            gregtech.api.metatileentity.BaseMetaTileEntity host = (gregtech.api.metatileentity.BaseMetaTileEntity)
                unsafe.allocateInstance(gregtech.api.metatileentity.BaseMetaTileEntity.class);
            host.xCoord = 80; host.yCoord = 70; host.zCoord = -96;
            CriticalMachineState state = new CriticalMachineState(); state.setLoomController(selector);
            for (ForgeDirection direction : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
                ForgeDirection.SOUTH, ForgeDirection.WEST }) {
                front.set(host, direction); state.bindHost(host);
                check(state.getControllerFacing(selector) == direction, "Fresh native front " + direction + " selector=" + selector);
                check(state.getStructureFacing() == (selector ? direction : direction.getOpposite()),
                    "Canonical owner frame from real GT front " + direction);
            }
        }
    }

    private static void nativeHatchClasses() throws Exception {
        java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
        Class<?>[] supported = { gregtech.api.metatileentity.implementations.MTEHatchInputBus.class,
            gregtech.api.metatileentity.implementations.MTEHatchInput.class,
            gregtech.api.metatileentity.implementations.MTEHatchEnergy.class,
            gregtech.api.metatileentity.implementations.MTEHatchOutputBus.class,
            gregtech.api.metatileentity.implementations.MTEHatchOutput.class,
            gregtech.api.metatileentity.implementations.MTEHatchDynamo.class };
        for (Class<?> type : supported) {
            gregtech.api.interfaces.metatileentity.IMetaTileEntity meta = (gregtech.api.interfaces.metatileentity.IMetaTileEntity)
                unsafe.allocateInstance(type);
            check(CriticalMachineState.isSupportedHatchMeta(meta), "Actual ordinary GT hatch class supported " + type.getSimpleName());
            check(CriticalMachineState.isEnergyInputHatchMeta(meta)
                == (type == gregtech.api.metatileentity.implementations.MTEHatchEnergy.class), "Actual input energy hatch classification");
        }
    }
}
