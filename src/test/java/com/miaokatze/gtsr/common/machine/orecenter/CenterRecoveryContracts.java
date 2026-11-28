// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.critical.CriticalStructureMonitor;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Frame;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Shape;

import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Detached fixture: actual serialized shapes, native production lifecycle, sparse world and material identities. */
public final class CenterRecoveryContracts {

    static sun.misc.Unsafe unsafe;
    static int checks, cycles;
    static final Map<String, Object> result = new LinkedHashMap<>();

    static Field field(Class<?> c, String name) throws Exception {
        for (; c != null; c = c.getSuperclass()) try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {}
        throw new NoSuchFieldException(name);
    }

    static Object get(Object o, String n) throws Exception {
        return field(o.getClass(), n).get(o);
    }

    static void set(Object o, String n, Object v) throws Exception {
        field(o.getClass(), n).set(o, v);
    }

    static void check(boolean b, String name) {
        if (!b) throw new AssertionError(name);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        Class<?> boot = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = boot.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, CenterRecoveryContracts.class.getName(), args)) return;
        Method setup = Class.forName("com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts")
            .getDeclaredMethod("bootstrapGt");
        setup.setAccessible(true);
        setup.invoke(null);
        Field u = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        u.setAccessible(true);
        unsafe = (sun.misc.Unsafe) u.get(null);
        palette();
        result.put(
            "productionSource",
            CenterStructure.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString());
        result.put(
            "gtSource",
            gregtech.api.metatileentity.implementations.MTEMultiBlockBase.class.getResource("MTEMultiBlockBase.class")
                .toString());
        CenterVirtualModuleFixture.run();
        sparseFootprints();
        optionalPortRecovery();
        reloading();
        CenterRecoveryReadinessFixture.run();
        controller();
        CenterRecoveryTransactionFixture.run();
        monitor();
        fullCadence();
        result.put("checks", checks);
        result.put("cycles", cycles);
        result.put("status", "PASS");
        result.put(
            "boundary",
            "No Minecraft process, disk chunk IO, Forge event dispatch, claim protection mixin, TCP or GUI. SparseWorld controls chunk readiness/failures. Foreign palette blocks are registered inert identities; production geometry and matching are unchanged.");
        System.out.println(
            new com.google.gson.GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(result));
        System.out.println("PASS center lifecycle checks=" + checks + " install_remove_cycles=" + cycles);
    }

    static void palette() throws Exception {
        Object ctrl = get(cpw.mods.fml.common.Loader.instance(), "modController");
        Object prev = get(ctrl, "activeContainer");
        int id = 2300;
        for (String key : paletteNames()) {
            String[] p = key.split(":", 2);
            if (GameRegistry.findBlock(p[0], p[1]) != null) continue;
            cpw.mods.fml.common.ModMetadata md = new cpw.mods.fml.common.ModMetadata();
            md.modId = p[0];
            md.name = "Detached palette identity";
            set(ctrl, "activeContainer", new cpw.mods.fml.common.DummyModContainer(md));
            Block b = new Block(Material.rock) {};
            GameData.getBlockRegistry()
                .addObject(id++, key, b);
            check(GameRegistry.findBlock(p[0], p[1]) == b, "registered palette identity " + key);
        }
        set(ctrl, "activeContainer", prev);
        GregTechAPI.sBlockCasings1 = GameRegistry.findBlock("gregtech", "gt.blockcasings");
        GregTechAPI.sBlockCasings2 = GameRegistry.findBlock("gregtech", "gt.blockcasings2");
        GregTechAPI.sBlockCasings3 = GameRegistry.findBlock("gregtech", "gt.blockcasings3");
        GregTechAPI.sBlockCasings4 = GameRegistry.findBlock("gregtech", "gt.blockcasings4");
        GregTechAPI.sBlockMachines = new Block(Material.iron) {};
    }

    static Set<String> paletteNames() throws Exception {
        Set<String> names = new TreeSet<>();
        Set<String> keys = new LinkedHashSet<>();
        for (int tier = 0; tier < 4; tier++) {
            keys.add("tower-" + tier);
            keys.add("tower-service-" + tier);
        }
        for (int sector = 0; sector < 6; sector++) keys.add("sector-" + sector);
        for (int pad = 0; pad < 6; pad++)
            for (CenterModule module : CenterModule.values()) for (int grade = 1; grade <= 3; grade++)
                keys.add("module-" + pad + "-" + module.key.toLowerCase(Locale.ROOT) + "-" + grade);
        for (String key : keys) {
            java.io.InputStream raw = CenterStructure.class
                .getResourceAsStream("/assets/gtsr/orecenter/" + key + ".gz");
            if (raw == null) continue;
            try (java.io.DataInputStream in = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(raw))) {
                int count = in.readUnsignedShort();
                in.readInt();
                for (int i = 0; i < count; i++) {
                    byte[] name = new byte[in.readUnsignedByte()];
                    in.readFully(name);
                    names.add(new String(name, java.nio.charset.StandardCharsets.UTF_8));
                    in.readByte();
                }
            }
        }
        return names;
    }

    static void readyForExecution(CenterStructure structure, IGregTechTileEntity te) throws Exception {
        Frame frame = new Frame(te);
        fill((SparseWorld) frame.world, frame, CenterStructure.optionalShape("tower-3"));
        CriticalStructureMonitor[] wings = (CriticalStructureMonitor[]) get(structure, "sectors");
        set(structure, "formedSectors", 63);
        for (int i = 0; i < 6; i++) {
            Shape wing = CenterStructure.optionalShape("sector-" + i);
            fill((SparseWorld) frame.world, frame, wing);
            wings[i].restoreValidation(wing.count());
        }
    }

    public static final class RecoveryMachine extends com.miaokatze.gtsr.common.machine.cluster.MTEUnitCrusher
        implements com.miaokatze.gtsr.common.machine.recovery.IGtsrStructureRecovery {

        private final com.miaokatze.gtsr.common.machine.recovery.GtsrStructureRecovery.State recovery = new com.miaokatze.gtsr.common.machine.recovery.GtsrStructureRecovery.State();

        RecoveryMachine() {
            super("center_recovery_contract");
            mStartUpCheck = 2400;
            recovery.active = true;
        }

        @Override
        public com.miaokatze.gtsr.common.machine.recovery.GtsrStructureRecovery.State gtsr$getStructureRecovery() {
            return recovery;
        }
    }

    static SparseWorld world() throws Exception {
        SparseWorld w = (SparseWorld) unsafe.allocateInstance(SparseWorld.class);
        w.cells = new HashMap<>();
        w.tiles = new HashMap<>();
        w.missing = new HashSet<>();
        w.queriedChunks = new HashSet<>();
        set(w, "provider", new WorldProviderSurface());
        set(w, "perWorldStorage", new MapStorage(null));
        w.chunks = (IChunkProvider) Proxy.newProxyInstance(
            IChunkProvider.class.getClassLoader(),
            new Class[] { IChunkProvider.class },
            (p, m, a) -> {
                if (!m.getName()
                    .equals("chunkExists")) return null;
                long key = chunk((Integer) a[0], (Integer) a[1]);
                w.chunkChecks++;
                if (w.captureChunkQueries) w.queriedChunks.add(key);
                return !w.missing.contains(key);
            });
        return w;
    }

    static IGregTechTileEntity tile(SparseWorld w, ForgeDirection facing) {
        return tile(w, facing, 80);
    }

    static IGregTechTileEntity tile(SparseWorld w, ForgeDirection facing, int controllerY) {
        RecoveryMachine recovery = new RecoveryMachine();
        return (IGregTechTileEntity) Proxy.newProxyInstance(
            IGregTechTileEntity.class.getClassLoader(),
            new Class[] { IGregTechTileEntity.class },
            (p, m, a) -> {
                switch (m.getName()) {
                    case "getMetaTileEntity":
                        return recovery;
                    case "getWorld":
                        return w;
                    case "getFrontFacing":
                        return facing;
                    case "getYCoord":
                        return (short) controllerY;
                    case "getXCoord":
                    case "getZCoord":
                        return 0;
                    case "isServerSide":
                        return true;
                    case "isClientSide":
                        return false;
                }
                Class<?> t = m.getReturnType();
                if (t == boolean.class) return false;
                if (t == int.class) return 0;
                if (t == short.class) return (short) 0;
                if (t == long.class) return 0L;
                if (t == byte.class) return (byte) 0;
                return null;
            });
    }

    static CenterStructure ready(IGregTechTileEntity te) throws Exception {
        CenterStructure s = new CenterStructure();
        set(s, "facing", te.getFrontFacing());
        set(s, "towerTier", 3);
        set(s, "formedSectors", 63);
        ((CriticalStructureMonitor) get(s, "tower")).restoreValidation(
            CenterStructure.optionalShape("tower-3")
                .count());
        Method bind = CenterStructure.class.getDeclaredMethod("bindPortIdentity", Frame.class);
        bind.setAccessible(true);
        bind.invoke(s, new Frame(te));
        set(s, "portIndexReady", true);
        return s;
    }

    static int[] pos(Frame f, Shape shape, int i, int slot) {
        int[] a = CenterStructure.SLOTS[slot];
        int x = a[0] + CenterStructure.rotateX(shape.x[i], shape.z[i], a[3]),
            z = a[2] + CenterStructure.rotateZ(shape.x[i], shape.z[i], a[3]) + 19;
        return new int[] { f.x(x, z), f.y + a[1] + shape.y[i] - 66, f.z(x, z) };
    }

    static Shape shape(int slot, CenterModule mod, int grade) {
        return CenterStructure
            .optionalShape("module-" + slot % 6 + "-" + mod.key.toLowerCase(Locale.ROOT) + "-" + grade);
    }

    /** Reproduce the real Lab's quartz floor and the actual one-sector crusher/logistics assembly. */

    static boolean sameNonAirCells(SparseWorld world, Map<String, Cell> expected) {
        long count = expected.values()
            .stream()
            .filter(cell -> cell.block() != Blocks.air)
            .count();
        if (world.nonAir() != count) return false;
        for (Map.Entry<String, Cell> entry : expected.entrySet()) {
            Cell want = entry.getValue();
            if (want.block() == Blocks.air) continue;
            Cell actual = world.cells.get(entry.getKey());
            if (actual == null || actual.block() != want.block() || actual.meta() != want.meta()) return false;
        }
        return true;
    }

    static Set<Long> expectedFootprint(Shape shape, Frame frame, int x, int z, int rotation) {
        Set<Long> expected = new HashSet<>();
        for (int i = 0; i < shape.count(); i++) {
            int localX = x + CenterStructure.rotateX(shape.x[i], shape.z[i], rotation);
            int localZ = z + CenterStructure.rotateZ(shape.x[i], shape.z[i], rotation) + 19;
            expected.add(chunk(frame.x(localX, localZ) >> 4, frame.z(localX, localZ) >> 4));
        }
        return expected;
    }

    static void sparseFootprints() throws Exception {
        SparseWorld world = world();
        world.captureChunkQueries = true;
        Frame frame = new Frame(tile(world, ForgeDirection.NORTH));
        CenterStructure center = new CenterStructure();
        Shape wing = CenterStructure.optionalShape("sector-0");
        Method loaded = CenterStructure.class
            .getDeclaredMethod("footprintLoaded", Shape.class, Frame.class, int.class, int.class, int.class);
        loaded.setAccessible(true);
        Set<Long> expected = expectedFootprint(wing, frame, 0, 0, 0);
        for (int x = -16; x <= 16; x++)
            for (int z = -16; z <= 16; z++) if (!expected.contains(chunk(x, z))) world.missing.add(chunk(x, z));
        check(
            (Boolean) loaded.invoke(center, wing, frame, 0, 0, 0),
            "sparse wing needs no loaded chunks in its empty bounding-box gaps");
        check(
            world.queriedChunks.equals(expected) && world.chunkChecks == expected.size(),
            "footprint checks only each real occupied chunk once");
        Map<?, ?> cached = (Map<?, ?>) get(center, "footprints");
        Object original = cached.values()
            .iterator()
            .next();
        Object columns = wing.columns();
        for (int i = 0; i < 100; i++) check(
            (Boolean) loaded.invoke(center, wing, frame, 0, 0, 0),
            "sparse footprint remains ready without revisiting columns");
        check(
            cached.size() == 1 && cached.values()
                .iterator()
                .next() == original && wing.columns() == columns,
            "readiness reuses the same chunk-offset and column arrays");
        check(
            world.chunkChecks == 101 * expected.size(),
            "repeated readiness performs chunkExists calls without column-sized query work");
        long missing = expected.iterator()
            .next();
        world.missing.add(missing);
        world.now++;
        check(
            !(Boolean) loaded.invoke(center, wing, frame, 0, 0, 0),
            "cached offsets never cache chunk readiness across ticks");
        world.missing.clear();
        world.now++;
        check(
            (Boolean) loaded.invoke(center, wing, frame, 0, 0, 0),
            "same cached offsets observe an immediately reloaded chunk");
        for (ForgeDirection facing : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
            ForgeDirection.SOUTH, ForgeDirection.WEST }) {
            for (int offset : new int[] { 0, 7 }) {
                Frame moved = new Frame(world, offset, 80, offset + 16, facing);
                world.queriedChunks.clear();
                world.chunkChecks = 0;
                check((Boolean) loaded.invoke(center, wing, moved, 0, 0, 0), "moved and rotated footprint is ready");
                check(
                    world.queriedChunks.equals(expectedFootprint(wing, moved, 0, 0, 0)),
                    "frame origin, subchunk offset and facing invalidate stale offsets");
                check(
                    cached.size() == 1 && cached.values()
                        .iterator()
                        .next() != original,
                    "changed controller frame retains only its own footprint cache");
            }
        }
        Shape module = shape(0, CenterModule.SPEED, 1);
        Frame same = new Frame(world, 7, 80, 23, ForgeDirection.WEST);
        for (int slot : new int[] { 0, 6, 12 }) {
            int[] at = CenterStructure.SLOTS[slot];
            world.queriedChunks.clear();
            world.chunkChecks = 0;
            check(
                (Boolean) loaded.invoke(center, module, same, at[0], at[2], at[3]),
                "transformed module footprint is ready");
            check(
                world.queriedChunks.equals(expectedFootprint(module, same, at[0], at[2], at[3])),
                "slot translation and rotation select the exact occupied chunks");
        }
        for (int i = 0; i < 140; i++) loaded.invoke(center, module, same, i, 0, 0);
        check(cached.size() <= 128, "per-controller footprint cache is bounded while transforms change");
    }

    static void optionalPortRecovery() throws Exception {
        SparseWorld world = world();
        IGregTechTileEntity te = tile(world, ForgeDirection.NORTH);
        Frame frame = new Frame(te);
        CenterStructure original = ready(te);
        readyForExecution(original, te);
        Shape tower = CenterStructure.optionalShape("tower-3");
        int at = -1;
        for (int i = tower.count() - 1; i >= 0; i--) if (CenterStructure.portCasing(tower, i)) {
            at = i;
            break;
        }
        check(at >= 0, "optional GT port fixture has a legal casing position");
        int x = frame.x(tower.x[at], tower.z[at] + 19), y = frame.y + tower.y[at] - 2,
            z = frame.z(tower.x[at], tower.z[at] + 19);
        world.cells.put(key(x, y, z), new Cell(GregTechAPI.sBlockMachines, 0));
        set(original, "portIndexReady", false);
        for (int i = 0; i < 1500 && !(Boolean) get(original, "portIndexReady"); i++) {
            world.now++;
            original.tick(te);
        }
        check(
            (Boolean) get(original, "portIndexReady"),
            "old saves build their native port index in bounded startup slices");
        NBTTagCompound nbt = new NBTTagCompound();
        original.save(nbt, te);
        check(
            nbt.getCompoundTag("oreCenterStructure")
                .getIntArray("Ports").length == 1,
            "native optional port coordinates persist in the formation snapshot");
        CenterStructure loaded = new CenterStructure();
        loaded.load(nbt);
        world.now++;
        loaded.tick(te);
        check(
            loaded.tier(te) == 3 && !loaded.isExecutionReady(te),
            "cached valid tower cannot end native recovery while its remembered optional GT tile is absent");
        check(
            !(Boolean) get(loaded, "portIndexUnknown") && (Boolean) get(loaded, "portIndexReady"),
            "persisted ports avoid rebuilding the full tower index");
        gregtech.api.metatileentity.BaseMetaTileEntity port = new gregtech.api.metatileentity.BaseMetaTileEntity();
        port.setWorldObj(world);
        port.xCoord = x;
        port.yCoord = y;
        port.zCoord = z;
        world.tiles.put(key(x, y, z), port);
        check(!loaded.isExecutionReady(te), "existing optional GT tile with delayed meta remains unknown");
        gregtech.api.metatileentity.implementations.MTEHatchInputBus meta = new gregtech.api.metatileentity.implementations.MTEHatchInputBus(
            "optional_port_contract",
            1,
            new String[0],
            null);
        meta.setBaseMetaTileEntity(port);
        check(
            loaded.isExecutionReady(te),
            "materialized optional native port releases the readiness gate without a whole tower sweep");
        check(
            (Integer) get(loaded, "portIndexCursor") == 0,
            "persisted optional-port readiness never performs a steady full-tower index scan");
    }

    static void fill(SparseWorld w, Frame f, Shape p) {
        for (int i = 0; i < p.count(); i++) w.cells.put(
            key(f.x(p.x[i], p.z[i] + 19), f.y + p.y[i] - 2, f.z(p.x[i], p.z[i] + 19)),
            new Cell(p.block(i), p.metadata(i)));
    }

    static void reloading() throws Exception {
        SparseWorld w = world();
        IGregTechTileEntity te = tile(w, ForgeDirection.NORTH);
        CenterStructure s = ready(te);
        set(s, "formedSectors", 1);
        set(s, "serviceFront", true);
        Shape body = CenterStructure.optionalShape("tower-service-3"), wing = CenterStructure.optionalShape("sector-0");
        Frame f = new Frame(te);
        fill(w, f, body);
        fill(w, f, wing);
        check(s.install(te, 0, CenterModule.SPEED, 1), "install before save");
        Shape pack = shape(0, CenterModule.SPEED, 1);
        NBTTagCompound tag = new NBTTagCompound();
        s.save(tag, te);
        check(
            tag.getCompoundTag("oreCenterStructure")
                .getLong("VerifiedModules") == 1,
            "verified module snapshot persisted");
        CenterStructure loaded = new CenterStructure();
        loaded.load(tag);
        w.now = 100;
        loaded.tick(te);
        check(loaded.tier(te) == 3 && loaded.sectorMask(te) == 1, "reload restores cached formation on ready chunks");
        check(loaded.moduleIntact(te, 0, CenterModule.SPEED, 1), "reload restores verified module");
        check(
            com.miaokatze.gtsr.common.machine.recovery.GtsrStructureRecovery
                .isRecovering((gregtech.api.metatileentity.implementations.MTEMultiBlockBase) te.getMetaTileEntity()),
            "port readiness uses native startup recovery");
        // The grace is about GT port materialization, not blanket shape authorization.
        int at = -1;
        for (int i = 0; i < body.count(); i++) if (CenterStructure.portCasing(body, i)) {
            at = i;
            break;
        }
        check(at >= 0, "real tower contains port positions");
        int x = f.x(body.x[at], body.z[at] + 19), y = f.y + body.y[at] - 2, z = f.z(body.x[at], body.z[at] + 19);
        w.cells.put(key(x, y, z), new Cell(GregTechAPI.sBlockMachines, 0));
        Method sample = CenterStructure.class.getDeclaredMethod(
            "sample",
            Shape.class,
            int.class,
            Frame.class,
            int.class,
            int.class,
            int.class,
            int.class,
            boolean.class);
        sample.setAccessible(true);
        check(
            sample.invoke(null, body, at, new Frame(te), 0, 0, 0, 0, false) == CriticalStructureMonitor.Sample.UNLOADED,
            "pending native GT port is unknown during native startup");
        ((gregtech.api.metatileentity.implementations.MTEMultiBlockBase) te.getMetaTileEntity()).mStartUpCheck = -1;
        check(
            sample.invoke(null, body, at, new Frame(te), 0, 0, 0, 0, false) == CriticalStructureMonitor.Sample.BROKEN,
            "pending GT port has no independent timer after native startup expires");
        ((gregtech.api.metatileentity.implementations.MTEMultiBlockBase) te.getMetaTileEntity()).mStartUpCheck = 2400;
        set(loaded, "scanCursor", 0);
        ((CriticalStructureMonitor) get(loaded, "tower")).requestFastSweep();
        for (int t = 0; t < 500 && loaded.tier(te) >= 0; t++) {
            w.now++;
            loaded.tick(te);
        }
        check(loaded.tier(te) < 0 && loaded.isCheckingFormation(te), "pending GT port inside grace pauses formation");
        result.put("pendingPortKeepsFormation", false);
        NBTTagCompound duringRecovery = new NBTTagCompound();
        loaded.save(duringRecovery, te);
        check(
            duringRecovery.getCompoundTag("oreCenterStructure")
                .getInteger("FormationVersion") == 1,
            "formation snapshot survives save while a GT port is pending");
        CenterStructure resaved = new CenterStructure();
        resaved.load(duringRecovery);
        check(get(resaved, "lastFormation") != null, "pending-port snapshot survives a second reload");
        w.cells.put(key(x, y, z), new Cell(body.block(at), body.metadata(at)));
        for (int t = 0; t < 1000 && loaded.tier(te) < 0; t++) {
            w.now++;
            loaded.tick(te);
        }
        check(loaded.tier(te) == 3, "ready port permits full recovery");
        CenterStructure wrong = new CenterStructure();
        wrong.load(tag);
        w.provider.dimensionId = 9;
        w.now++;
        wrong.tick(te);
        check(get(wrong, "savedFormation") == null, "moved snapshot rejected");
        w.provider.dimensionId = 0;
        // Cached sectors can wait independently; no timeout changes unknown chunks into missing blocks.
        CenterStructure partial = new CenterStructure();
        partial.load(tag);
        int[] first = pos(f, pack, 0, 0);
        w.missing.add(chunk(first[0] >> 4, first[2] >> 4));
        w.now += 2000;
        partial.tick(te);
        check(!partial.moduleBroken(0, CenterModule.SPEED, 1), "long unloaded snapshot not damaged");
        check(w.unloadedReads == 0, "recovery never reads unloaded blocks");
        // Forge material update without a player Place/Break event is caught only by the fallback sweep.
        CenterStructure delayed = ready(te);
        set(delayed, "formedSectors", 1);
        set(delayed, "serviceFront", true);
        fill(w, f, body);
        fill(w, f, wing);
        w.missing.clear();
        ((CriticalStructureMonitor) get(delayed, "tower")).restoreValidation(body.count());
        for (int i = 0; i < 2000 && ((CriticalStructureMonitor) get(delayed, "tower")).getCompletedRounds() == 0; i++) {
            w.now++;
            delayed.tick(te);
        }
        check(
            ((CriticalStructureMonitor) get(delayed, "tower")).getCompletedRounds() > 0,
            "steady scan fixture completed its initial round");
        int damageIndex = body.count() - 1, dx = f.x(body.x[damageIndex], body.z[damageIndex] + 19),
            dy = f.y + body.y[damageIndex] - 2, dz = f.z(body.x[damageIndex], body.z[damageIndex] + 19);
        w.cells.put(key(dx, dy, dz), new Cell(Blocks.air, 0));
        int delay = 0;
        while (delay < 7000 && delayed.tier(te) >= 0) {
            w.now++;
            delayed.tick(te);
            delay++;
        }
        check(delayed.tier(te) < 0, "background sweep eventually catches an unevented change");
        result.put("lastVoxelUneventedDamageDetectionTicks", delay);
        CenterStructure evented = ready(te);
        set(evented, "serviceFront", true);
        evented.changed(body.x[damageIndex], body.y[damageIndex], body.z[damageIndex]);
        check(evented.tier(te) < 0, "exact tower change event invalidates cached formation immediately");
    }

    static Object getUnchecked(Object object, String name) {
        try {
            return get(object, name);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void monitor() {
        CriticalStructureMonitor m = new CriticalStructureMonitor();
        m.restoreValidation(12000);
        check(m.getStatus() == CriticalStructureMonitor.Status.VALID, "cached monitor initially valid");
        m.tickSamples(12000, i -> CriticalStructureMonitor.Sample.UNLOADED);
        check(
            m.getStatus() == CriticalStructureMonitor.Status.UNLOADED && !m.hasKnownDamage(),
            "unknown chunk revokes execution without damage");
        for (int t = 0; t < 100 && !m.tick(12000, i -> true); t++)
            check(m.getLastChecks() <= 1024, "initial slice bounded");
        check(m.getStatus() == CriticalStructureMonitor.Status.VALID, "full sweep recovers");
        CriticalStructureMonitor reload = new CriticalStructureMonitor();
        reload.restoreValidation(12000);
        while (reload.isFastSweep()) reload.tickSamples(12000, i -> CriticalStructureMonitor.Sample.MATCH);
        reload.tickSamples(12000, i -> CriticalStructureMonitor.Sample.UNLOADED);
        check(
            reload.isFastSweep() && !reload.hasKnownDamage(),
            "steady unknown immediately switches to fast recovery without inventing damage");
        int recoveryCalls = 0;
        while (reload.getStatus() != CriticalStructureMonitor.Status.VALID && recoveryCalls++ < 50) {
            reload.tickSamples(12000, i -> CriticalStructureMonitor.Sample.MATCH);
            check(reload.getLastChecks() <= 1024, "unloaded recovery remains within a fast slice");
        }
        check(
            reload.getStatus() == CriticalStructureMonitor.Status.VALID && recoveryCalls <= 30,
            "unloaded steady monitor requires a fresh clean round and recovers promptly");
        CriticalStructureMonitor mixed = new CriticalStructureMonitor();
        mixed.tickSamples(
            2,
            i -> i == 0 ? CriticalStructureMonitor.Sample.UNLOADED : CriticalStructureMonitor.Sample.BROKEN);
        check(
            mixed.hasKnownDamage() && mixed.getStatus() == CriticalStructureMonitor.Status.BROKEN,
            "an early unknown cell cannot hide a later loaded mismatch");
        CriticalStructureMonitor debt = new CriticalStructureMonitor();
        debt.restoreValidation(24000);
        while (debt.isFastSweep()) debt.tickSamples(24000, i -> CriticalStructureMonitor.Sample.MATCH);
        debt.tickSamples(24000, 64, i -> CriticalStructureMonitor.Sample.MATCH);
        long earned = ((Number) getUnchecked(debt, "credit")).longValue();
        check(earned >= 6000, "checks above the slice cap retain their earned credit");
        debt.tickSamples(Integer.MAX_VALUE, 64, i -> CriticalStructureMonitor.Sample.MATCH);
        check(debt.getLastChecks() <= 1024, "huge shapes remain bounded without credit overflow");
        long rounds = m.getCompletedRounds();
        for (int t = 0; t < 6000; t++) {
            check(m.tick(12000, i -> true), "steady verdict preserved");
            check(m.getLastChecks() <= 128, "steady checks bounded");
        }
        check(m.getCompletedRounds() == rounds + 1, "6000 tick steady full round");
        m.invalidate();
        m.pauseUnloaded();
        check(m.hasKnownDamage(), "known damage survives unloaded transition");
    }

    static MTEMineralIntegrationCenter center(SparseWorld w) throws Exception {
        Constructor<MTEMineralIntegrationCenter> c = MTEMineralIntegrationCenter.class
            .getDeclaredConstructor(String.class);
        c.setAccessible(true);
        MTEMineralIntegrationCenter m = c.newInstance("center_lifecycle_audit");
        gregtech.api.metatileentity.BaseMetaTileEntity te = new gregtech.api.metatileentity.BaseMetaTileEntity();
        te.setWorldObj(w);
        te.yCoord = 80;
        set(te, "mFacing", ForgeDirection.NORTH);
        m.setBaseMetaTileEntity(te);
        set(m, "structure", ready(te));
        return m;
    }

    static void fullCadence() throws Exception {
        SparseWorld w = world();
        IGregTechTileEntity te = tile(w, ForgeDirection.NORTH);
        Frame f = new Frame(te);
        CenterStructure s = ready(te);
        set(s, "serviceFront", true);
        Shape body = CenterStructure.optionalShape("tower-service-3");
        fill(w, f, body);
        CriticalStructureMonitor[] sectors = (CriticalStructureMonitor[]) get(s, "sectors");
        for (int i = 0; i < 6; i++) {
            Shape wing = CenterStructure.optionalShape("sector-" + i);
            fill(w, f, wing);
            sectors[i].restoreValidation(wing.count());
        }
        for (int slot = 0; slot < 36; slot++) {
            CenterModule mod = slot % 6 == 0 ? CenterModule.SPEED
                : slot % 6 <= 2 ? CenterModule.CRUSHER : CenterModule.LOGISTICS;
            check(s.install(te, slot, mod, 3), "full footprint accepts 36 representative modules");
        }
        CriticalStructureMonitor tower = (CriticalStructureMonitor) get(s, "tower");
        tower.restoreValidation(body.count());
        long rounds = tower.getCompletedRounds();
        int fast = 0;
        while (fast < 4000 && tower.getCompletedRounds() == rounds) {
            w.now++;
            s.tick(te);
            fast++;
        }
        check(tower.getCompletedRounds() > rounds, "full center fast tower sweep completes");
        // Finish startup work in all 43 monitors before measuring an entire steady tower round.
        for (int t = 0; t < 4000; t++) {
            boolean pending = tower.isFastSweep();
            for (CriticalStructureMonitor wing : sectors) pending |= wing.isFastSweep();
            if (!pending) break;
            w.now++;
            s.tick(te);
        }
        rounds = tower.getCompletedRounds();
        for (int t = 0; t < 7000 && tower.getCompletedRounds() == rounds; t++) {
            w.now++;
            s.tick(te);
        }
        rounds = tower.getCompletedRounds();
        int steady = 0;
        while (steady < 12000 && tower.getCompletedRounds() == rounds) {
            w.now++;
            s.tick(te);
            steady++;
        }
        check(tower.getCompletedRounds() > rounds, "full center steady tower sweep completes");
        check(
            steady <= 6002,
            "36-module center retains the 6000-tick native monitor cadence within multiplex boundary");
        result.put("full36ModuleFastTowerSweepTicks", fast);
        result.put("full36ModuleSteadyTowerSweepTicks", steady);
        result.put("full36ModuleWorldPositions", w.nonAir());
        Set<Long> occupiedChunks = new HashSet<>();
        for (String k : w.cells.keySet()) {
            String[] xyz = k.split(",");
            occupiedChunks.add(chunk(Integer.parseInt(xyz[0]) >> 4, Integer.parseInt(xyz[2]) >> 4));
        }
        result.put("full36ModuleOccupiedChunksAtOrigin", occupiedChunks.size());
    }

    static void controller() throws Exception {
        Class<?> items = Class.forName("com.miaokatze.gtsr.common.items.CenterModuleItems");
        Constructor<?> ic = items.getDeclaredConstructor();
        ic.setAccessible(true);
        Object item = ic.newInstance();
        Field instance = items.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, item);
        GameData.getItemRegistry()
            .addObject(29000, "gtsr:ore_integration_module", (net.minecraft.item.Item) item);
        SparseWorld w = world();
        MTEMineralIntegrationCenter m = center(w);
        IGregTechTileEntity te = m.getBaseMetaTileEntity();
        gregtech.api.metatileentity.implementations.MTEHatchInputBus bus = new gregtech.api.metatileentity.implementations.MTEHatchInputBus(
            "audit_bus",
            1,
            new String[0],
            null);
        gregtech.api.metatileentity.BaseMetaTileEntity bt = new gregtech.api.metatileentity.BaseMetaTileEntity();
        bt.setWorldObj(w);
        bus.setBaseMetaTileEntity(bt);
        ((List) get(m, "mInputBusses")).add(bus);
        net.minecraft.item.ItemStack pack = com.miaokatze.gtsr.common.items.CenterModuleItems
            .stack(CenterModule.SPEED, 1);
        pack.stackSize = 2;
        bus.mInventory[0] = pack;
        Method edit = MTEMineralIntegrationCenter.class
            .getDeclaredMethod("changeModule", IGregTechTileEntity.class, int.class);
        edit.setAccessible(true);
        set(m, "totalTicks", 100);
        set(m, "elapsed", 37);
        check(
            !m.installModuleFromBus(0) && pack.stackSize == 2 && w.writes == 0,
            "busy batch rejects module edits without debit");
        set(m, "totalTicks", 0);
        set(m, "elapsed", 0);
        check(
            m.installModuleFromBus(0) && pack.stackSize == 1,
            "controller consumes exactly one package for virtual installation");
        check(w.writes == 0, "real package debit creates no module world blocks");
        NBTTagCompound carried = new NBTTagCompound();
        m.setItemNBT(carried);
        MTEMineralIntegrationCenter moved = center(w);
        moved.loadNBTData(carried);
        check(moved.installedModuleGrades()[0] == 1, "native GT item NBT carries the paid virtual package");
        check(
            edit.invoke(m, te, 0)
                .toString()
                .startsWith("Module package"),
            "controller dismantles intact package");
        List<net.minecraft.item.ItemStack> pending = (List) get(m, "pendingItems");
        check(pending.size() == 1 && pending.get(0).stackSize == 1, "one refund queued");
        check(((int[]) get(m, "grades"))[0] == 0, "refunded slot is cleared");
        check(w.writes == 0, "real package refund creates no module world writes");
        NBTTagCompound refundItem = new NBTTagCompound();
        m.setItemNBT(refundItem);
        MTEMineralIntegrationCenter movedRefund = center(w);
        movedRefund.loadNBTData(refundItem);
        check(
            ((List<?>) get(movedRefund, "pendingItems")).size() == 1 && movedRefund.installedModuleGrades()[0] == 0,
            "controller item carries exactly one pending refund without recreating installation");
        Method eject = MTEMineralIntegrationCenter.class.getDeclaredMethod("ejectBatch", IGregTechTileEntity.class);
        eject.setAccessible(true);
        eject.invoke(m, te);
        check(pending.size() == 1, "no output space retains refund");
        NBTTagCompound saved = new NBTTagCompound();
        m.saveNBTData(saved);
        MTEMineralIntegrationCenter restored = center(w);
        restored.loadNBTData(saved);
        List<net.minecraft.item.ItemStack> restoredPending = (List) get(restored, "pendingItems");
        check(
            restoredPending.size() == 1 && restoredPending.get(0).stackSize == 1,
            "refund persists exactly once across NBT");
        check(
            com.miaokatze.gtsr.common.items.CenterModuleItems.moduleOf(restoredPending.get(0)) == CenterModule.SPEED,
            "refund type persists");
        set(restored, "totalTicks", 100);
        set(restored, "elapsed", 37);
        set(restored, "steamTotal", 0L);
        set(restored, "energyTotal", 0L);
        set(restored, "nextProbe", Long.MAX_VALUE);
        Method tick = MTEMineralIntegrationCenter.class
            .getDeclaredMethod("tickBatch", IGregTechTileEntity.class, long.class);
        tick.setAccessible(true);
        restored.mMachine = false;
        tick.invoke(restored, restored.getBaseMetaTileEntity(), 1L);
        check((Integer) get(restored, "elapsed") == 37, "unformed recovery preserves but does not advance progress");
        readyForExecution((CenterStructure) get(restored, "structure"), restored.getBaseMetaTileEntity());
        restored.mMachine = true;
        tick.invoke(restored, restored.getBaseMetaTileEntity(), 2L);
        check(
            (Integer) get(restored, "elapsed") == 37,
            "missing native ports cannot advance the paid batch despite a cached shape");
        result.put(
            "controllerDebitRefund",
            "busy rejection, one-pack debit, one-pack pending refund, output blocked retention, NBT identity and progress pause/resume verified");
    }

    static String key(int[] p) {
        return key(p[0], p[1], p[2]);
    }

    static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    static long chunk(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    static final class Cell {

        private final Block block;
        private final int meta;

        Cell(Block block, int meta) {
            this.block = block;
            this.meta = meta;
        }

        Block block() {
            return block;
        }

        int meta() {
            return meta;
        }
    }

    public static final class SparseWorld extends World {

        Map<String, Cell> cells;
        Map<String, TileEntity> tiles;
        Set<Long> missing;
        IChunkProvider chunks;
        long now;
        int writes, failAt, unloadedReads, mutateThenFailAt, throwAt, corruptAt;
        boolean refuseRollback, notifyClaims;
        Runnable callback;
        int callbackAt, chunkChecks;
        boolean captureChunkQueries;
        Set<Long> queriedChunks;

        SparseWorld() {
            super(null, "unused", (WorldProvider) null, null, null);
        }

        void read(int x, int z) {
            if (missing.contains(chunk(x >> 4, z >> 4))) unloadedReads++;
        }

        long nonAir() {
            return cells.values()
                .stream()
                .filter(c -> c.block() != Blocks.air)
                .count();
        }

        @Override
        public boolean setBlock(int x, int y, int z, Block b, int meta, int flags) {
            writes++;
            if (throwAt > 0 && writes == throwAt) throw new IllegalStateException("injected write exception");
            if (failAt > 0 && (writes == failAt || refuseRollback && writes > failAt)) return false;
            if (notifyClaims) {
                Cell previous = cells.get(key(x, y, z));
                if (previous != null && previous.block() != Blocks.air)
                    CenterClaims.suppressDrops(this, x, y, z, previous.block(), previous.meta());
            }
            cells.put(key(x, y, z), new Cell(b, corruptAt == writes ? meta ^ 1 : meta));
            if (callback != null && writes == callbackAt) {
                Runnable action = callback;
                callback = null;
                action.run();
            }
            if (notifyClaims) CenterClaims.changed(this, x, y, z);
            return writes != mutateThenFailAt;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            read(x, z);
            Cell c = cells.get(key(x, y, z));
            return c == null ? Blocks.air : c.block();
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            read(x, z);
            Cell c = cells.get(key(x, y, z));
            return c == null ? 0 : c.meta();
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            read(x, z);
            return tiles.get(key(x, y, z));
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            return getBlock(x, y, z) == Blocks.air;
        }

        @Override
        public IChunkProvider getChunkProvider() {
            return chunks;
        }

        @Override
        public net.minecraft.world.chunk.Chunk getChunkFromChunkCoords(int x, int z) {
            return null;
        }

        @Override
        public int getHeight() {
            return 256;
        }

        @Override
        public long getTotalWorldTime() {
            return now;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return chunks;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public net.minecraft.entity.Entity getEntityByID(int id) {
            return null;
        }
    }
}
