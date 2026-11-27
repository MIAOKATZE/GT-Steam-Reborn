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
        modules();
        failedTransactions();
        worldMutations();
        neighboringCallback();
        knownUnknownReadiness();
        sparseFootprints();
        optionalPortRecovery();
        reloading();
        controller();
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
                        return (short) 80;
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

    static CriticalStructureMonitor moduleMonitor(CenterStructure s, int slot) throws Exception {
        return (CriticalStructureMonitor) get(((Object[]) get(s, "modules"))[slot], "monitor");
    }

    static int[] pos(Frame f, Shape shape, int i, int slot) {
        int[] a = CenterStructure.SLOTS[slot];
        int x = a[0] + CenterStructure.rotateX(shape.x[i], shape.z[i], a[3]),
            z = a[2] + CenterStructure.rotateZ(shape.x[i], shape.z[i], a[3]) + 19;
        return new int[] { f.x(x, z), f.y + a[1] + shape.y[i] - 66, f.z(x, z) };
    }

    static boolean scan(CenterStructure s, IGregTechTileEntity te, int slot, Shape shape) throws Exception {
        Method m = CenterStructure.class.getDeclaredMethod(
            "scan",
            CriticalStructureMonitor.class,
            Shape.class,
            Frame.class,
            int.class,
            int.class,
            int.class,
            int.class,
            boolean.class,
            int.class);
        m.setAccessible(true);
        int[] a = CenterStructure.SLOTS[slot];
        return (Boolean) m.invoke(null, moduleMonitor(s, slot), shape, new Frame(te), a[0], a[1], a[2], a[3], true, 1);
    }

    static Shape shape(int slot, CenterModule mod, int grade) {
        return CenterStructure
            .optionalShape("module-" + slot % 6 + "-" + mod.key.toLowerCase(Locale.ROOT) + "-" + grade);
    }

    static void modules() throws Exception {
        for (ForgeDirection dir : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
            ForgeDirection.SOUTH, ForgeDirection.WEST })
            for (CenterModule mod : CenterModule.values()) for (int grade = 1; grade <= 3; grade++) {
                int slot = mod.kind == 0 ? 0 : mod.kind == 1 ? 1 : 3;
                SparseWorld w = world();
                IGregTechTileEntity te = tile(w, dir);
                CenterStructure s = ready(te);
                Shape p = shape(slot, mod, grade);
                check(p != null, "package resource exists");
                check(s.install(te, slot, mod, grade), "install package");
                check(!s.install(te, slot, mod, grade), "reject duplicate install");
                for (int t = 0; t < 100 && !s.moduleIntact(te, slot, mod, grade); t++) scan(s, te, slot, p);
                check(s.moduleIntact(te, slot, mod, grade), "complete module validation");
                check(s.remove(te, slot, mod, grade), "intact removal");
                check(!s.remove(te, slot, mod, grade), "no double refund/removal");
                check(w.nonAir() == 0, "remove all generated voxels");
                NBTTagCompound claims = new NBTTagCompound();
                CenterClaims.get(w)
                    .writeToNBT(claims);
                check(
                    claims.getTagList("modules", 10)
                        .tagCount() == 0,
                    "claims released");
                cycles++;
            }
        for (ForgeDirection dir : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
            ForgeDirection.SOUTH, ForgeDirection.WEST }) for (int slot = 0; slot < 36; slot++) {
                CenterModule mod = slot % 6 == 0 ? CenterModule.SPEED
                    : slot % 6 <= 2 ? CenterModule.CRUSHER : CenterModule.LOGISTICS;
                SparseWorld pw = world();
                IGregTechTileEntity pt = tile(pw, dir);
                CenterStructure ps = ready(pt);
                Shape piece = shape(slot, mod, 3);
                check(ps.install(pt, slot, mod, 3), "install every pad and facing");
                for (int t = 0; t < 100 && !ps.moduleIntact(pt, slot, mod, 3); t++) scan(ps, pt, slot, piece);
                check(ps.remove(pt, slot, mod, 3) && pw.nonAir() == 0, "remove every pad and facing");
                cycles++;
            }
        SparseWorld w = world();
        IGregTechTileEntity te = tile(w, ForgeDirection.NORTH);
        CenterStructure s = ready(te);
        Shape p = shape(0, CenterModule.SPEED, 1);
        Frame f = new Frame(te);
        int[] first = pos(f, p, 0, 0);
        w.cells.put(key(first), new Cell(Blocks.diamond_block, 0));
        check(!s.install(te, 0, CenterModule.SPEED, 1), "ordinary solid obstacle rejects before overwrite");
        check(
            w.getBlock(first[0], first[1], first[2]) == Blocks.diamond_block && w.writes == 0,
            "ordinary solid obstacle remains untouched");
        w.cells.put(key(first), new Cell(p.block(0), p.metadata(0)));
        check(
            !s.install(te, 0, CenterModule.SPEED, 1) && w.writes == 0,
            "matching player material is never adopted for a free refund");
        w.cells.remove(key(first));
        check(s.install(te, 0, CenterModule.SPEED, 1), "empty pad accepts a paid installation");
        for (int t = 0; t < 30 && !s.moduleIntact(te, 0, CenterModule.SPEED, 1); t++) scan(s, te, 0, p);
        long missing = chunk(first[0] >> 4, first[2] >> 4);
        w.missing.add(missing);
        int before = w.writes;
        check(!s.remove(te, 0, CenterModule.SPEED, 1) && w.writes == before, "unloaded removal refuses all writes");
        for (int t = 0; t < 30; t++) scan(s, te, 0, p);
        check(!s.moduleBroken(0, CenterModule.SPEED, 1), "unloaded is not damaged");
        check(w.unloadedReads == 0, "no unloaded block reads");
        w.missing.clear();
        for (int t = 0; t < 50 && !s.moduleIntact(te, 0, CenterModule.SPEED, 1); t++) scan(s, te, 0, p);
        check(s.moduleIntact(te, 0, CenterModule.SPEED, 1), "reloaded module recovers");
        w.cells.put(key(first), new Cell(Blocks.air, 0));
        before = w.writes;
        check(!s.remove(te, 0, CenterModule.SPEED, 1) && before == w.writes, "damaged module cannot refund");
        for (int t = 0; t < 7000 && !s.moduleBroken(0, CenterModule.SPEED, 1); t++) scan(s, te, 0, p);
        check(s.moduleBroken(0, CenterModule.SPEED, 1), "true mismatch breaks module");
        SparseWorld rollback = world();
        IGregTechTileEntity rt = tile(rollback, ForgeDirection.NORTH);
        CenterStructure rs = ready(rt);
        rollback.failAt = 20;
        check(
            !rs.install(rt, 0, CenterModule.SPEED, 1) && rollback.nonAir() == 0,
            "failed installation rolls back changed cells");
        check(!rs.moduleLoaded(rt, 0, CenterModule.SPEED, 1), "failed install never claims module");
        SparseWorld occupied = world();
        IGregTechTileEntity ot = tile(occupied, ForgeDirection.NORTH);
        CenterStructure os = ready(ot);
        int[] op = pos(new Frame(ot), p, 0, 0);
        occupied.tiles.put(key(op), new gregtech.api.metatileentity.BaseMetaTileEntity());
        check(
            !os.install(ot, 0, CenterModule.SPEED, 1) && occupied.writes == 0,
            "tile entity obstacle rejects before write");
        result.put("moduleUnloadedReads", w.unloadedReads);
    }

    static void failedTransactions() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            SparseWorld w = world();
            IGregTechTileEntity te = tile(w, ForgeDirection.NORTH);
            CenterStructure center = ready(te);
            if (mode == 0) w.mutateThenFailAt = 20;
            else if (mode == 1) w.throwAt = 20;
            else w.corruptAt = 20;
            check(
                !center.install(te, 0, CenterModule.SPEED, 1),
                "false/throw/wrong-result installation never succeeds");
            check(w.nonAir() == 0, "failed and partially applied writes roll back every attempted voxel");
            check(
                (Integer) get(CenterClaims.get(w), "internalWrites") == 0,
                "transaction notification guard always releases");
        }
        SparseWorld removal = world();
        IGregTechTileEntity removalTile = tile(removal, ForgeDirection.NORTH);
        CenterStructure removable = ready(removalTile);
        check(removable.install(removalTile, 0, CenterModule.SPEED, 1), "install removal rollback fixture");
        Shape removalShape = shape(0, CenterModule.SPEED, 1);
        for (int i = 0; i < 100 && !removable.moduleIntact(removalTile, 0, CenterModule.SPEED, 1); i++)
            scan(removable, removalTile, 0, removalShape);
        long original = removal.nonAir();
        removal.mutateThenFailAt = removal.writes + 20;
        check(
            !removable.remove(removalTile, 0, CenterModule.SPEED, 1) && removal.nonAir() == original,
            "partially applied removal rolls back and gives no refund");
        check(
            removable.remove(removalTile, 0, CenterModule.SPEED, 1) && removal.nonAir() == 0,
            "a fully restored removal can be retried exactly once");
        SparseWorld refused = world();
        IGregTechTileEntity te = tile(refused, ForgeDirection.NORTH);
        CenterStructure center = ready(te);
        refused.failAt = 20;
        refused.refuseRollback = true;
        check(!center.install(te, 0, CenterModule.SPEED, 1), "refused rollback cannot acknowledge installation");
        check(
            refused.nonAir() > 0 && center.moduleBroken(0, CenterModule.SPEED, 1),
            "unreverted generated remnants are quarantined as damaged");
        Shape shape = shape(0, CenterModule.SPEED, 1);
        int[] first = pos(new Frame(te), shape, 0, 0);
        check(
            CenterClaims.suppressDrops(refused, first[0], first[1], first[2], shape.block(0), shape.metadata(0)),
            "unpaid remnants remain no-drop");
        check(!center.remove(te, 0, CenterModule.SPEED, 1), "partial rollback never grants a package refund");
        check(
            (Integer) get(CenterClaims.get(refused), "internalWrites") == 0,
            "refused rollback releases notification guard");
    }

    static void worldMutations() throws Exception {
        SparseWorld world = world();
        world.notifyClaims = true;
        IGregTechTileEntity te = tile(world, ForgeDirection.NORTH);
        CenterStructure center = ready(te);
        Shape module = shape(0, CenterModule.SPEED, 1);
        Frame frame = new Frame(te);
        check(
            center.install(te, 0, CenterModule.SPEED, 1),
            "self-notifying world accepts installation without invalidating ownership");
        for (int i = 0; i < 100 && !center.moduleIntact(te, 0, CenterModule.SPEED, 1); i++) scan(center, te, 0, module);
        check(
            center.moduleIntact(te, 0, CenterModule.SPEED, 1),
            "internal generated writes leave installed module intact");
        NBTTagCompound saved = new NBTTagCompound();
        center.save(saved, te);
        int[] at = pos(frame, module, module.count() - 1, 0);
        world.setBlock(
            at[0],
            at[1],
            at[2],
            module.block(module.count() - 1),
            module.metadata(module.count() - 1) ^ 1,
            2);
        check(
            center.moduleBroken(0, CenterModule.SPEED, 1),
            "non-player metadata mutation immediately revokes module authorization");
        saved = new NBTTagCompound();
        center.save(saved, te);
        check(
            (saved.getCompoundTag("oreCenterStructure")
                .getLong("VerifiedModules") & 1) == 0,
            "damaged module cannot survive through a stale saved authorization");
        NBTTagCompound ownership = new NBTTagCompound();
        CenterClaims.get(world)
            .writeToNBT(ownership);
        check(
            ownership.getTagList("modules", 10)
                .getCompoundTagAt(0)
                .getByteArray("removed").length > 0,
            "non-player mutation persists exact removed ownership");
        Shape tower = CenterStructure.optionalShape("tower-3");
        fill(world, frame, tower);
        ((CriticalStructureMonitor) get(center, "tower")).restoreValidation(tower.count());
        int index = tower.count() - 1;
        center.changed(frame, tower.x[index], tower.y[index], tower.z[index]);
        check(center.tier(te) == 3, "matching-state world notification preserves cached formation");
        world.cells.put(
            key(
                frame.x(tower.x[index], tower.z[index] + 19),
                frame.y + tower.y[index] - 2,
                frame.z(tower.x[index], tower.z[index] + 19)),
            new Cell(Blocks.air, 0));
        center.changed(frame, tower.x[index], tower.y[index], tower.z[index]);
        check(
            center.tier(te) < 0 && !center.isCheckingFormation(te),
            "real loaded tower damage skips the native recovery grace immediately");
    }

    static void neighboringCallback() throws Exception {
        SparseWorld world = world();
        world.notifyClaims = true;
        IGregTechTileEntity te = tile(world, ForgeDirection.NORTH);
        CenterStructure center = ready(te);
        Shape original = shape(0, CenterModule.SPEED, 1);
        Frame frame = new Frame(te);
        check(center.install(te, 0, CenterModule.SPEED, 1), "install callback neighbor fixture");
        for (int i = 0; i < 100 && !center.moduleIntact(te, 0, CenterModule.SPEED, 1); i++)
            scan(center, te, 0, original);
        int[] neighbor = pos(frame, original, 0, 0);
        world.callbackAt = world.writes + 1;
        world.callback = () -> {
            check(
                !CenterClaims.suppressDrops(world, 300, 80, 300, Blocks.diamond_block, 0),
                "module transaction never suppresses unrelated drops from a synchronous callback");
            world.setBlock(neighbor[0], neighbor[1], neighbor[2], Blocks.air, 0, 2);
        };
        check(
            center.install(te, 1, CenterModule.CRUSHER, 1),
            "nested neighbor mutation does not reject the current module write");
        check(
            world.callback == null && center.moduleBroken(0, CenterModule.SPEED, 1),
            "synchronous callback mutation still revokes the neighboring module immediately");
        Shape installed = shape(1, CenterModule.CRUSHER, 1);
        for (int i = 0; i < 100 && !center.moduleIntact(te, 1, CenterModule.CRUSHER, 1); i++)
            scan(center, te, 1, installed);
        check(
            center.moduleIntact(te, 1, CenterModule.CRUSHER, 1),
            "coordinate guard resumes its own outer write after a callback");
        check(
            (Integer) get(CenterClaims.get(world), "internalWrites") == 0,
            "nested world callback leaves no transaction guard");
    }

    static void knownUnknownReadiness() throws Exception {
        SparseWorld world = world();
        IGregTechTileEntity te = tile(world, ForgeDirection.NORTH);
        CenterStructure center = ready(te);
        readyForExecution(center, te);
        check(
            ((CriticalStructureMonitor) get(center, "tower")).isFastSweep() && center.isExecutionReady(te),
            "cached valid background fast sweeps remain executable");
        check(center.install(te, 0, CenterModule.SPEED, 1), "install known-unknown module fixture");
        check(
            !center.isExecutionReady(te),
            "a cold pending module is a known unknown before a native successful check");
        Shape piece = shape(0, CenterModule.SPEED, 1);
        for (int i = 0; i < 100 && !center.moduleIntact(te, 0, CenterModule.SPEED, 1); i++) scan(center, te, 0, piece);
        check(center.isExecutionReady(te), "fully loaded verified module releases the readiness gate");
        int[] at = pos(new Frame(te), piece, 0, 0);
        world.missing.add(chunk(at[0] >> 4, at[2] >> 4));
        check(!center.isExecutionReady(te), "unloaded footprint is a known unknown despite a cached valid monitor");
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
        for (int t = 0; t < 50 && !s.moduleIntact(te, 0, CenterModule.SPEED, 1); t++) scan(s, te, 0, pack);
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
            moduleMonitor(s, slot).restoreValidation(shape(slot, mod, 3).count());
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
            for (int slot = 0; slot < 36; slot++) pending |= moduleMonitor(s, slot).isFastSweep();
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
        check(m.installModuleFromBus(0) && pack.stackSize == 1, "controller consumes exactly one package after build");
        CenterStructure s = (CenterStructure) get(m, "structure");
        Shape p = shape(0, CenterModule.SPEED, 1);
        for (int i = 0; i < 50 && !s.moduleIntact(te, 0, CenterModule.SPEED, 1); i++) scan(s, te, 0, p);
        check(
            edit.invoke(m, te, 0)
                .toString()
                .startsWith("Module package"),
            "controller dismantles intact package");
        List<net.minecraft.item.ItemStack> pending = (List) get(m, "pendingItems");
        check(pending.size() == 1 && pending.get(0).stackSize == 1, "one refund queued");
        check(((int[]) get(m, "grades"))[0] == 0, "refunded slot is cleared");
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
