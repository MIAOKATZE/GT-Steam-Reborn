// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalBlockProtection;
import com.miaokatze.gtsr.common.critical.CriticalStructureAlignment;
import com.miaokatze.gtsr.common.critical.CriticalStructureMonitor;
import com.miaokatze.gtsr.common.critical.CriticalStructureMonitor.Sample;
import com.miaokatze.gtsr.common.critical.CriticalStructureMonitor.Status;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Sparse, controller-relative block lists exported by tools/ore-center/compile.cjs. */
public final class CenterStructure {

    public static final String SECTOR_CHANNEL = "ore_center_sectors";
    static final int[][] SLOTS = { { -19, 18, 33, 0 }, { -46, 9, 49, 0 }, { -19, 9, 64, 0 }, { -76, 1, 66, 3 },
        { -50, 1, 86, 0 }, { -19, 1, 99, 0 }, { 19, 18, 33, 0 }, { 19, 9, 64, 0 }, { 46, 9, 49, 0 }, { 19, 1, 99, 0 },
        { 50, 1, 86, 0 }, { 76, 1, 66, 1 }, { 38, 18, 0, 1 }, { 65, 9, 15, 1 }, { 65, 9, -15, 1 }, { 95, 1, 33, 1 },
        { 99, 1, 0, 1 }, { 95, 1, -33, 1 }, { 19, 18, -33, 2 }, { 46, 9, -49, 2 }, { 19, 9, -64, 2 }, { 76, 1, -66, 1 },
        { 50, 1, -86, 2 }, { 19, 1, -99, 2 }, { -19, 18, -33, 2 }, { -19, 9, -64, 2 }, { -46, 9, -49, 2 },
        { -19, 1, -99, 2 }, { -50, 1, -86, 2 }, { -76, 1, -66, 3 }, { -38, 18, 0, 3 }, { -65, 9, -15, 3 },
        { -65, 9, 15, 3 }, { -95, 1, -33, 3 }, { -99, 1, 0, 3 }, { -95, 1, 33, 3 }, };
    private static final int[] HATCH_X = { -5, -3, -1, 1, 3, 5 };
    private static final int[] SERVICE_PORT_X = { 3, -3, 5, -5, 7, -7, -6 };
    private static final String SERVICE_TOWER = "tower-service-";
    private static final Map<String, Shape> SHAPES = new HashMap<>();
    private final CriticalStructureMonitor tower = new CriticalStructureMonitor();
    private final CriticalStructureMonitor[] sectors = new CriticalStructureMonitor[6];
    private final ModuleCheck[] modules = new ModuleCheck[36];
    private ForgeDirection facing = ForgeDirection.UNKNOWN;
    private int towerTier = -1;
    private int formedSectors;
    private int scanCursor;
    private final long[] lastScan = new long[43];
    private int anchorIndex = -1;
    private boolean attachClaims = true;
    private int survivalCursor, survivalTier = -1, survivalMask;
    private boolean serviceFront;
    private static int[] legacyPlinthPorts, servicePlinthPorts;

    public CenterStructure() {
        for (int i = 0; i < sectors.length; i++) sectors[i] = new CriticalStructureMonitor();
    }

    public int tier(IGregTechTileEntity te) {
        return facing == te.getFrontFacing() && tower.getStatus() == Status.VALID ? towerTier : -1;
    }

    /** Raw formed wings still require the tower to become valid before operation. */
    public int sectorMask(IGregTechTileEntity te) {
        return tier(te) < 0 ? 0 : formedSectors;
    }

    public void collectHatches(IGregTechTileEntity te, Consumer<IGregTechTileEntity> accept) {
        if (towerTier >= 0 && facing == te.getFrontFacing())
            collectHatches(new Frame(te), towerShape(towerTier, serviceFront), accept);
    }

    private static void collectHatches(Frame frame, Shape tower, Consumer<IGregTechTileEntity> accept) {
        // Visit the existing tile entities in loaded tower chunks instead of polling every casing.
        int x0 = frame.x(tower.minX, tower.minZ + 19), x1 = frame.x(tower.maxX, tower.maxZ + 19);
        int z0 = frame.z(tower.minX, tower.minZ + 19), z1 = frame.z(tower.maxX, tower.maxZ + 19);
        tower.prepareLookup();
        for (int cx = Math.min(x0, x1) >> 4; cx <= Math.max(x0, x1) >> 4; cx++) {
            for (int cz = Math.min(z0, z1) >> 4; cz <= Math.max(z0, z1) >> 4; cz++) {
                if (!frame.loaded(cx << 4, cz << 4)) continue;
                for (Object tile : frame.world.getChunkFromChunkCoords(cx, cz).chunkTileEntityMap.values()) {
                    if (!(tile instanceof TileEntity entity) || entity.isInvalid() || !(entity instanceof IGregTechTileEntity gt)
                        || !MTEMineralIntegrationCenter.acceptsPort(gt.getMetaTileEntity())) continue;
                    int dx = gt.getXCoord() - frame.x, dz = gt.getZCoord() - frame.z;
                    int x = (int) CriticalStructureAlignment.unrotateX(dx, dz, frame.facing);
                    int z = (int) CriticalStructureAlignment.unrotateZ(dx, dz, frame.facing);
                    int y = gt.getYCoord() - frame.y;
                    int i = tower.lookup(x, y + 2, z - 19);
                    if (i >= 0 && portCasing(tower, i) || !tower.service && y == 0 && z == 1
                        && java.util.Arrays.binarySearch(HATCH_X, x) >= 0) accept.accept(gt);
                }
            }
        }
    }

    private static synchronized int[] plinthPorts(Shape tower) {
        int[] ports = tower.service ? servicePlinthPorts : legacyPlinthPorts;
        if (ports == null) {
            ports = java.util.stream.IntStream.range(0, tower.count())
                .filter(i -> plinthCasing(tower, i))
                .filter(i -> outside(tower, tower.x[i] - 1, tower.z[i])
                    || outside(tower, tower.x[i] + 1, tower.z[i])
                    || outside(tower, tower.x[i], tower.z[i] - 1)
                    || outside(tower, tower.x[i], tower.z[i] + 1))
                .toArray();
            if (tower.service) servicePlinthPorts = ports;
            else legacyPlinthPorts = ports;
        }
        return ports;
    }

    private static Shape towerShape(int tier, boolean service) {
        return shape((service ? SERVICE_TOWER : "tower-") + tier);
    }

    private static boolean hasServiceFront(Frame frame) {
        if (!frame.loaded(frame.x(0, 0), frame.z(0, 0))) return false;
        // The decorative sill is required by this layout and cannot become a hatch.
        Shape tower = towerShape(0, true);
        tower.prepareLookup();
        int marker = tower.lookup(0, 1, -19);
        int x = frame.x(0, 0), z = frame.z(0, 0), y = frame.y - 1;
        return frame.world.getBlock(x, y, z) == tower.block(marker)
            && frame.world.getBlockMetadata(x, y, z) == tower.metadata(marker);
    }

    private static boolean outside(Shape tower, int x, int z) {
        tower.prepareLookup();
        return tower.lookup(x, 2, z) < 0 && tower.lookup(x, 3, z) < 0;
    }

    private static boolean plinthCasing(Shape shape, int index) {
        if (!shape.tower || shape.y[index] != 2) return false;
        Block block = shape.block(index);
        return block == GregTechAPI.sBlockCasings3 || block == GregTechAPI.sBlockCasings4;
    }

    private static boolean portCasing(Shape shape, int i) {
        if (!shape.tower) return false;
        Block block = shape.block(i);
        int meta = shape.metadata(i);
        if (Byte.toUnsignedInt(shape.ids[i]) == shape.shell || block == GregTechAPI.sBlockCasings3 && meta == 10) return true;
        if (!plinthCasing(shape, i)) return false;
        Shape base = towerShape(0, shape.service);
        int[] ports = plinthPorts(base);
        return java.util.Arrays.binarySearch(ports, base.lookup(shape.x[i], 2, shape.z[i])) >= 0;
    }

    private static boolean substitutedPort(Shape shape, int i, Frame frame, int wx, int wy, int wz) {
        return shape.tower && frame.world.getBlock(wx, wy, wz) == GregTechAPI.sBlockMachines && portCasing(shape, i)
            && frame.world.getTileEntity(wx, wy, wz) instanceof IGregTechTileEntity gt
            && MTEMineralIntegrationCenter.acceptsPort(gt.getMetaTileEntity());
    }

    void showPreviewPorts(IGregTechTileEntity te) {
        Frame frame = new Frame(te);
        ItemStack[] examples = { ItemList.Hatch_Input_LV.get(1), ItemList.Hatch_Output_LV.get(1),
            ItemList.Hatch_Input_Bus_LV.get(1), ItemList.Hatch_Output_Bus_LV.get(1),
            GTSRItemList.SteamInputHatchGeneric.get(1), GTSRItemList.SteamCoolingHatch.get(1),
            ItemList.Hatch_Energy_LV.get(1) };
        for (int i = 0; i < examples.length; i++) placeExamplePort(frame, SERVICE_PORT_X[i], 0, examples[i]);
    }

    private static void placeExamplePort(Frame frame, int x, int z, ItemStack stack) {
        short id = (short) stack.getItemDamage();
        int wx = frame.x(x, z), wz = frame.z(x, z);
        frame.world.setBlock(wx, frame.y, wz, GregTechAPI.sBlockMachines,
            GregTechAPI.METATILEENTITIES[id].getTileEntityBaseType(), 2);
        if (frame.world.getTileEntity(wx, frame.y, wz) instanceof IGregTechTileEntity hatch) {
            hatch.setInitialValuesAsNBT(null, id);
            hatch.setFrontFacing(frame.facing);
        }
    }

    public void tick(IGregTechTileEntity te) {
        if (te == null || te.getWorld().isRemote) return;
        World world = te.getWorld();
        CenterClaims claims = CenterClaims.get(world);
        if (!claims.allowsScan(this, world.getTotalWorldTime())) return;
        long started = System.nanoTime();
        try {
            scanStructure(te, new Frame(te), claims);
        } finally {
            claims.countScan(System.nanoTime() - started);
        }
    }

    private void scanStructure(IGregTechTileEntity te, Frame frame, CenterClaims claims) {
        if (attachClaims) {
            for (int slot = 0; slot < modules.length; slot++)
                if (modules[slot] != null) claims.attach(frame, slot, this);
            attachClaims = false;
        }
        if (te.getFrontFacing() != facing) {
            claims.track(frame, this);
            facing = te.getFrontFacing();
            towerTier = -1;
            tower.reset();
            for (CriticalStructureMonitor sector : sectors) sector.reset();
            for (ModuleCheck module : modules) if (module != null) module.monitor.reset();
            formedSectors = 0;
            survivalCursor = 0;
        }
        int found = detectTier(frame);
        boolean service = found >= 0 && hasServiceFront(frame);
        if (found != towerTier || service != serviceFront) {
            towerTier = found;
            serviceFront = service;
            tower.reset();
        }
        if (towerTier < 0) return;

        // A single monitor gets a slice per tick. Missing wings and unused pads don't consume a slice.
        if (tower.getStatus() == Status.PENDING) {
            scan(tower, towerShape(towerTier, serviceFront), frame, 0, 0, 0, 0, false, elapsed(frame, 0));
            return;
        }
        for (int attempts = 0; attempts < lastScan.length; attempts++) {
            int monitor = scanCursor;
            scanCursor = (scanCursor + 1) % lastScan.length;
            if (monitor == 0) {
                scan(tower, towerShape(towerTier, serviceFront), frame, 0, 0, 0, 0, false, elapsed(frame, 0));
                return;
            }
            int i = monitor - 1;
            if (i < sectors.length) {
                Shape piece = shape("sector-" + i);
                Sample marker = sample(piece, 0, frame, 0, 0, 0, 0, false);
                if (marker != Sample.MATCH) {
                    sectors[i].reset();
                    if (marker == Sample.BROKEN) sectors[i].invalidate();
                    else sectors[i].pauseUnloaded();
                    formedSectors &= ~(1 << i);
                    continue;
                }
                if (scan(sectors[i], piece, frame, 0, 0, 0, 0, false, elapsed(frame, monitor))) formedSectors |= 1 << i;
                else formedSectors &= ~(1 << i);
                return;
            }
            int slot = i - sectors.length;
            ModuleCheck module = modules[slot];
            if (module == null) continue;
            int[] at = SLOTS[slot];
            claims.attach(frame, slot, this);
            scan(
                module.monitor,
                shape(module.resource),
                frame,
                at[0],
                at[1],
                at[2],
                at[3],
                true,
                elapsed(frame, monitor));
            if (module.monitor.hasKnownDamage()) module.damaged = true;
            return;
        }
    }

    private int elapsed(Frame frame, int monitor) {
        long now = frame.world.getTotalWorldTime(), prev = lastScan[monitor];
        lastScan[monitor] = now;
        return (int) Math.max(1, Math.min(64, now - prev));
    }

    private int detectTier(Frame frame) {
        Shape first = shape("tower-0");
        if (anchorIndex < 0) {
            for (int i = 0; i < first.count(); i++) {
                if (portCasing(first, i) || first.y[i] < 5) continue;
                int index = i;
                boolean allDifferent = true;
                for (int j = 0; j < 4; j++) for (int k = j + 1; k < 4; k++) {
                    Shape a = shape("tower-" + j), b = shape("tower-" + k);
                    if (a.block(index) == b.block(index) && a.metadata(index) == b.metadata(index))
                        allDifferent = false;
                }
                if (allDifferent) {
                    anchorIndex = i;
                    break;
                }
            }
            if (anchorIndex < 0) throw new IllegalStateException("No ore center tier key");
        }
        for (int i = 0; i < 4; i++)
            if (sample(shape("tower-" + i), anchorIndex, frame, 0, 0, 0, 0, false) == Sample.MATCH) return i;
        return -1;
    }

    private static boolean scan(CriticalStructureMonitor monitor, Shape shape, Frame frame, int x, int y, int z,
        int rotation, boolean module, int elapsed) {
        return monitor.tickSamples(shape.count(), elapsed, i -> sample(shape, i, frame, x, y, z, rotation, module));
    }

    static int rotateX(int x, int z, int dir) {
        return dir == 1 ? -z : dir == 2 ? -x : dir == 3 ? z : x;
    }

    static int rotateZ(int x, int z, int dir) {
        return dir == 1 ? x : dir == 2 ? -z : dir == 3 ? -x : z;
    }

    private static Sample sample(Shape shape, int i, Frame frame, int x, int y, int z, int rotation, boolean module) {
        int rx = x + rotateX(shape.x[i], shape.z[i], rotation);
        int rz = z + rotateZ(shape.x[i], shape.z[i], rotation) + 19;
        int xx = frame.x(rx, rz), yy = frame.y + y + shape.y[i] - 2 - (module ? 64 : 0), zz = frame.z(rx, rz);
        if (yy < 0 || yy >= frame.world.getHeight()) return Sample.BROKEN;
        if (!frame.loaded(xx, zz)) return Sample.UNLOADED;
        return (frame.world.getBlock(xx, yy, zz) == shape.block(i)
            && frame.world.getBlockMetadata(xx, yy, zz) == shape.metadata(i))
            || !module && substitutedPort(shape, i, frame, xx, yy, zz) ? Sample.MATCH : Sample.BROKEN;
    }

    private static String moduleKey(int slot, CenterModule module, int grade) {
        String id = module.key.toLowerCase(Locale.ROOT);
        if (id.equals("primary")) id = "yield";
        return "module-" + slot % 6 + "-" + id + "-" + grade;
    }

    public boolean moduleLoaded(IGregTechTileEntity te, int slot, CenterModule module, int grade) {
        ModuleCheck check = checkModule(slot, module, grade);
        return check != null && (check.damaged || check.monitor.getStatus() == Status.VALID
            || check.monitor.getStatus() == Status.BROKEN);
    }

    public boolean moduleIntact(IGregTechTileEntity te, int slot, CenterModule module, int grade) {
        ModuleCheck check = checkModule(slot, module, grade);
        return check != null && !check.damaged && check.monitor.getStatus() == Status.VALID;
    }

    private ModuleCheck checkModule(int slot, CenterModule module, int grade) {
        if (slot < 0 || slot >= 36 || grade < 1 || grade > 3 || module == null) return null;
        String key = moduleKey(slot, module, grade);
        return modules[slot] != null && modules[slot].resource.equals(key) ? modules[slot] : null;
    }

    public boolean install(IGregTechTileEntity te, int slot, CenterModule module, int grade) {
        if (slot < 0 || slot >= 36
            || grade < 1
            || grade > 3
            || module == null
            || (sectorMask(te) & 1 << (slot / 6)) == 0
            || modules[slot] != null && !modules[slot].damaged) return false;
        String key = moduleKey(slot, module, grade);
        Shape piece = optionalShape(key);
        if (piece == null) return false;
        Frame frame = new Frame(te);
        if (frame.world.isRemote) return false;
        CenterClaims claims = CenterClaims.get(frame.world);
        // A different package may not release the old package's surviving, free decorative blocks.
        if (claims.hasRemainingDifferent(frame, slot, key) || !writeModule(frame, slot, piece, false)) return false;
        modules[slot] = new ModuleCheck(key);
        claims.claim(frame, slot, key, piece);
        claims.attach(frame, slot, this);
        return true;
    }

    public boolean remove(IGregTechTileEntity te, int slot, CenterModule module, int grade) {
        ModuleCheck check = checkModule(slot, module, grade);
        if (check == null || te.getWorld().isRemote || !moduleIntact(te, slot, module, grade)) return false;
        Frame frame = new Frame(te);
        if (!writeModule(frame, slot, shape(check.resource), true)) return false;
        CenterClaims.get(frame.world)
            .release(frame, slot);
        modules[slot] = null;
        return true;
    }

    private static boolean writeModule(Frame frame, int slot, Shape shape, boolean remove) {
        int[] at = SLOTS[slot];
        int n = shape.count();
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        Block[] previous = new Block[n];
        byte[] metadata = new byte[n];
        boolean[] change = new boolean[n];
        CenterClaims claims = CenterClaims.get(frame.world);
        for (int i = 0; i < n; i++) {
            int rx = at[0] + rotateX(shape.x[i], shape.z[i], at[3]);
            int rz = at[2] + rotateZ(shape.x[i], shape.z[i], at[3]) + 19;
            int x = xs[i] = frame.x(rx, rz), y = ys[i] = frame.y + at[1] + shape.y[i] - 66, z = zs[i] = frame.z(rx, rz);
            if (shape.block(i) == null || y < 0
                || y >= frame.world.getHeight()
                || !frame.loaded(x, z)
                || frame.world.getTileEntity(x, y, z) != null) return false;
            if (!remove && CriticalBlockProtection.protects(frame.world, x, y, z) && !claims.owns(frame, slot, x, y, z))
                return false;
            previous[i] = frame.world.getBlock(x, y, z);
            metadata[i] = (byte) frame.world.getBlockMetadata(x, y, z);
            boolean expected = previous[i] == shape.block(i) && metadata[i] == shape.metadata(i);
            // Voluntary dismantling only refunds a whole intact module.
            if (remove && !expected) return false;
            change[i] = remove || !expected;
        }
        for (int i = 0; i < n; i++) if (change[i]) {
            Block block = remove ? net.minecraft.init.Blocks.air : shape.block(i);
            int meta = remove ? 0 : shape.metadata(i);
            if (!frame.world.setBlock(xs[i], ys[i], zs[i], block, meta, 2)) {
                for (int j = i - 1; j >= 0; j--)
                    if (change[j]) frame.world.setBlock(xs[j], ys[j], zs[j], previous[j], metadata[j], 2);
                return false;
            }
        }
        return true;
    }

    boolean permitsExternalRemoval(int slot) {
        return towerTier < 0 || tower.getStatus() == Status.BROKEN
            || (formedSectors & 1 << (slot / 6)) == 0 && sectors[slot / 6].getStatus() == Status.BROKEN;
    }

    void invalidateModule(int slot) {
        if (modules[slot] == null) return;
        modules[slot].monitor.invalidate();
        modules[slot].damaged = true;
    }

    void changed(int x, int y, int z) {
        if (towerTier >= 0 && y >= 0 && y <= 64 && x >= -22 && x <= 22 && z >= -22 && z <= 22) {
            Shape body = towerShape(towerTier, serviceFront);
            body.prepareLookup();
            if (body.lookup(x, y, z) >= 0) {
                tower.invalidate();
                tower.requestFastSweep();
                return;
            }
        }
        for (int i = 0; i < sectors.length; i++) if ((formedSectors & 1 << i) != 0) {
            Shape wing = shape("sector-" + i);
            wing.prepareLookup();
            if (wing.lookup(x, y, z) < 0) continue;
            sectors[i].invalidate();
            sectors[i].requestFastSweep();
            formedSectors &= ~(1 << i);
            return;
        }
    }

    public void save(NBTTagCompound tag) {
        NBTTagCompound app = new NBTTagCompound();
        for (int i = 0; i < modules.length; i++) if (modules[i] != null) {
            app.setString(Integer.toString(i), modules[i].resource);
            if (modules[i].damaged) app.setBoolean("d" + i, true);
        }
        tag.setTag("oreCenterStructure", app);
    }

    public void load(NBTTagCompound tag) {
        NBTTagCompound app = tag.getCompoundTag("oreCenterStructure");
        for (int i = 0; i < modules.length; i++) {
            String key = app.getString(Integer.toString(i));
            modules[i] = key.matches("module-" + i % 6 + "-[a-z]+-[123]") ? new ModuleCheck(key) : null;
            if (modules[i] != null) modules[i].damaged = app.getBoolean("d" + i);
        }
        attachClaims = true;
    }

    public boolean construct(IGregTechTileEntity te, int tier, int mask, boolean hintsOnly) {
        if (tier < 0 || tier > 3 || mask < 1 || mask > 63) return false;
        Frame frame = new Frame(te);
        List<Shape> blueprints = pieces(tier, mask);
        if (hintsOnly) {
            if (!frame.world.isRemote) return false;
            for (Shape piece : blueprints) projectPiece(frame, piece);
            return true;
        }
        if (frame.world.isRemote) return false;
        for (Shape piece : blueprints) if (!canConstruct(frame, piece)) return false;
        for (Shape piece : blueprints) buildPiece(frame, piece);
        tower.requestFastSweep();
        for (CriticalStructureMonitor sector : sectors) sector.requestFastSweep();
        return true;
    }

    private static final CenterModule[][] DEBUG_MODULES = {
        { CenterModule.SPEED, CenterModule.CRUSHER, CenterModule.EXTRACTOR },
        { CenterModule.PARALLEL, CenterModule.WASHER, CenterModule.CENTRIFUGE },
        { CenterModule.YIELD, CenterModule.SIFTER, CenterModule.ELECTROLYZER },
        { CenterModule.SECONDARY, CenterModule.DRYER, CenterModule.THERMAL_CENTRIFUGE },
        { CenterModule.SECONDARY, CenterModule.FURNACE, CenterModule.REACTOR },
        { CenterModule.SPEED, CenterModule.SEPARATOR, CenterModule.SMELTING }
    };

    DebugBuild debugBuild(IGregTechTileEntity te, EntityPlayer player, int[] types, int[] grades) {
        return new DebugBuild(new Frame(te), player, types, grades);
    }

    final class DebugBuild {

        private final Frame frame;
        private final EntityPlayer player;
        private final int[] types, grades;
        private final CenterClaims claims;
        private final boolean service;
        private int piece, cursor;
        private boolean clearing;
        private String resource;

        private DebugBuild(Frame frame, EntityPlayer player, int[] types, int[] grades) {
            this.frame = frame;
            this.player = player;
            this.types = types;
            this.grades = grades;
            claims = CenterClaims.get(frame.world);
            service = hasServiceFront(frame) || detectTier(frame) < 0;
        }

        /** Each call writes at most one layout slice and never loads a chunk. */
        String advance(IGregTechTileEntity te, long deadline) {
            if (player.worldObj != frame.world || player.isDead || te.getFrontFacing() != frame.facing
                || !player.capabilities.isCreativeMode && !player.canCommandSenderUseCommand(2, "gtsrdebug"))
                return finish("gtsr.wand.debug.denied");

            int slot = piece - 2;
            CenterModule module = slot < 0 ? null : grades[slot] > 0 ? CenterModule.values()[types[slot]]
                : slot % 6 < 3 ? DEBUG_MODULES[slot / 6][slot % 6] : CenterModule.LOGISTICS;
            String target = piece == 0 ? (service ? SERVICE_TOWER : "tower-") + "3"
                : piece == 1 ? "outer-full" : moduleKey(slot, module, 3);
            if (resource == null) {
                String previous = slot < 0 ? null : claims.moduleResource(frame, slot);
                clearing = previous != null && !previous.equals(target);
                resource = clearing ? previous : target;
            }
            Shape layout = shape(resource);
            Shape replacement = clearing ? shape(target) : null;
            if (replacement != null) replacement.prepareLookup();
            int[] at = slot < 0 ? null : SLOTS[slot];
            for (int i = 0; cursor < layout.count() && i < 2048 && System.nanoTime() < deadline; i++, cursor++) {
                int dx = at == null ? layout.x[cursor] : at[0] + rotateX(layout.x[cursor], layout.z[cursor], at[3]);
                int dz = at == null ? layout.z[cursor] : at[2] + rotateZ(layout.x[cursor], layout.z[cursor], at[3]);
                int x = frame.x(dx, dz + 19), z = frame.z(dx, dz + 19);
                int y = frame.y + layout.y[cursor] - 2 + (at == null ? 0 : at[1] - 64);
                if (!frame.loaded(x, z)) return finish("gtsr.wand.debug.build_unloaded");
                Block block = layout.block(cursor);
                int meta = layout.metadata(cursor);
                if (block == null || y < 0 || y >= frame.world.getHeight())
                    return finish("gtsr.wand.debug.build_blocked");
                boolean matches = frame.world.getBlock(x, y, z) == block
                    && frame.world.getBlockMetadata(x, y, z) == meta;
                if (clearing) {
                    if (!matches || !claims.owns(frame, slot, x, y, z)) continue;
                    int next = replacement.lookup(layout.x[cursor], layout.y[cursor], layout.z[cursor]);
                    if (next >= 0 && replacement.block(next) == block && replacement.metadata(next) == meta) continue;
                } else if (matches || substitutedPort(layout, cursor, frame, x, y, z)) continue;
                if (!frame.world.canMineBlock(player, x, y, z) || frame.world.getTileEntity(x, y, z) != null
                    || CriticalBlockProtection.protects(frame.world, x, y, z) && !claims.owns(frame, slot, x, y, z)
                    || !frame.world.setBlock(x, y, z, clearing ? Blocks.air : block, clearing ? 0 : meta, 2))
                    return finish("gtsr.wand.debug.build_blocked");
            }
            if (cursor < layout.count()) return null;
            cursor = 0;
            if (clearing) {
                claims.claim(frame, slot, target, shape(target));
                modules[slot] = new ModuleCheck(target);
                claims.attach(frame, slot, CenterStructure.this);
                types[slot] = module.ordinal();
                grades[slot] = 3;
                te.markDirty();
                resource = target;
                clearing = false;
                return null;
            }
            if (slot >= 0) {
                if (!target.equals(claims.moduleResource(frame, slot))) claims.claim(frame, slot, target, layout);
                modules[slot] = new ModuleCheck(target);
                claims.attach(frame, slot, CenterStructure.this);
                types[slot] = module.ordinal();
                grades[slot] = 3;
                te.markDirty();
            }
            resource = null;
            return ++piece < grades.length + 2 ? null : finish(
                placeMissingPorts() ? "gtsr.wand.debug.build_done" : "gtsr.wand.debug.build_ports");
        }

        private boolean placeMissingPorts() {
            ItemStack[] examples = { ItemList.Hatch_Input_LV.get(1), ItemList.Hatch_Output_LV.get(1),
                ItemList.Hatch_Input_Bus_LV.get(1), ItemList.Hatch_Output_Bus_LV.get(1) };
            Class<?>[] types = new Class<?>[examples.length];
            for (int i = 0; i < examples.length; i++)
                types[i] = GregTechAPI.METATILEENTITIES[examples[i].getItemDamage()].getClass();
            boolean[] present = new boolean[examples.length];
            collectHatches(frame, towerShape(3, service), hatch -> {
                if (hatch.getMetaTileEntity() == null) return;
                for (int i = 0; i < types.length; i++) if (types[i] == hatch.getMetaTileEntity().getClass())
                    present[i] = true;
            });
            for (int i = 0; i < examples.length; i++) {
                if (present[i]) continue;
                int[] locations = service ? SERVICE_PORT_X : HATCH_X;
                int depth = service ? 0 : 1;
                for (int j = 0; j < locations.length; j++) {
                    int x = locations[(i + j) % locations.length];
                    int wx = frame.x(x, depth), wz = frame.z(x, depth);
                    if (!frame.loaded(wx, wz) || !frame.world.canMineBlock(player, wx, frame.y, wz)) continue;
                    Block block = frame.world.getBlock(wx, frame.y, wz);
                    boolean mountingCasing = service && block == GregTechAPI.sBlockCasings3
                        && frame.world.getBlockMetadata(wx, frame.y, wz) == 10;
                    if (!mountingCasing && !frame.world.isAirBlock(wx, frame.y, wz)) continue;
                    placeExamplePort(frame, x, depth, examples[i]);
                    present[i] = frame.world.getTileEntity(wx, frame.y, wz) instanceof IGregTechTileEntity hatch
                        && hatch.getMetaTileID() == examples[i].getItemDamage();
                    if (present[i]) break;
                }
                if (!present[i]) return false;
            }
            return true;
        }

        private String finish(String result) {
            tower.reset();
            towerTier = -1;
            for (CriticalStructureMonitor sector : sectors) sector.reset();
            formedSectors = 0;
            return result;
        }

        void notifyPlayer(String result) {
            com.miaokatze.gtsr.common.debug.MachineDebugRegistry.feedback(player, result);
        }
    }

    private static List<Shape> pieces(int tier, int mask) {
        List<Shape> result = new ArrayList<>(7);
        result.add(towerShape(tier, true));
        if (mask == 63) result.add(shape("outer-full"));
        else for (int i = 0; i < 6; i++) if ((mask & 1 << i) != 0) result.add(shape("outer-" + i));
        return result;
    }

    @FunctionalInterface
    public interface AssemblyPad {
        void pad(int slot, int x, int z);
    }

    public static void visitAssemblyPads(AssemblyPad sink) {
        for (int i = 0; i < SLOTS.length; i++) sink.pad(i, SLOTS[i][0], SLOTS[i][2]);
    }

    public int survivalConstruct(IGregTechTileEntity te, int tier, int mask, int budget,
        ISurvivalBuildEnvironment env) {
        if (te.getWorld().isRemote || tier < 0 || tier > 3 || mask < 1 || mask > 63 || budget <= 0) return 0;
        Frame frame = new Frame(te);
        int placed = 0, index = 0, scanned = 0;
        if (survivalTier != tier || survivalMask != mask) survivalCursor = 0;
        survivalTier = tier;
        survivalMask = mask;
        for (Shape piece : pieces(tier, mask)) for (int i = 0; i < piece.count(); i++, index++) {
            if (index < survivalCursor) continue;
            if (++scanned > 1024 || placed >= budget) {
                survivalCursor = index;
                return placed;
            }
            int xx = frame.x(piece.x[i], piece.z[i] + 19), yy = frame.y + piece.y[i] - 2,
                zz = frame.z(piece.x[i], piece.z[i] + 19);
            if (!frame.loaded(xx, zz)) {
                survivalCursor = index;
                return placed;
            }
            if (frame.world.getBlock(xx, yy, zz) == piece.block(i)
                && frame.world.getBlockMetadata(xx, yy, zz) == piece.metadata(i)) continue;
            if (substitutedPort(piece, i, frame, xx, yy, zz)) continue;
            if (piece.block(i) == null || yy < 0
                || yy >= frame.world.getHeight()
                || CriticalBlockProtection.protects(frame.world, xx, yy, zz)) {
                survivalCursor = index;
                return placed;
            }
            PlaceResult result = StructureUtility.survivalPlaceBlock(
                piece.block(i),
                piece.metadata(i),
                frame.world,
                xx,
                yy,
                zz,
                env.getSource(),
                env.getActor());
            if (result == PlaceResult.REJECT || result == PlaceResult.STOP) {
                survivalCursor = index;
                return placed;
            }
            if (result == PlaceResult.ACCEPT || result == PlaceResult.ACCEPT_STOP) placed++;
        }
        survivalCursor = 0;
        return placed == 0 ? -1 : placed;
    }

    private static boolean canConstruct(Frame frame, Shape shape) {
        for (int i = 0; i < shape.count(); i++) {
            int rx = shape.x[i], rz = shape.z[i] + 19;
            int wx = frame.x(rx, rz), wy = frame.y + shape.y[i] - 2, wz = frame.z(rx, rz);
            if (shape.block(i) == null || wy < 0
                || wy >= frame.world.getHeight()
                || !frame.loaded(wx, wz)) return false;
            if (substitutedPort(shape, i, frame, wx, wy, wz)) continue;
            if (frame.world.getTileEntity(wx, wy, wz) != null) return false;
            if (CriticalBlockProtection.protects(frame.world, wx, wy, wz)) return false;
        }
        return true;
    }

    private static void projectPiece(Frame frame, Shape shape) {
        for (int i = 0; i < shape.count(); i++) {
            Block block = shape.block(i);
            if (block == null) continue;
            int rx = shape.x[i], rz = shape.z[i] + 19;
            int x = frame.x(rx, rz), y = frame.y + shape.y[i] - 2, z = frame.z(rx, rz);
            if (y < 0 || y >= frame.world.getHeight() || !frame.loaded(x, z)) continue;
            int meta = shape.metadata(i);
            if (frame.world.getBlock(x, y, z) == block && frame.world.getBlockMetadata(x, y, z) == meta) continue;
            if (!substitutedPort(shape, i, frame, x, y, z))
                StructureLibAPI.hintParticle(frame.world, x, y, z, block, meta);
        }
    }

    private static void buildPiece(Frame frame, Shape shape) {
        for (int i = 0; i < shape.count(); i++) {
            int rx = shape.x[i], rz = shape.z[i] + 19;
            int wx = frame.x(rx, rz), wy = frame.y + shape.y[i] - 2, wz = frame.z(rx, rz);
            if (shape.block(i) == null || !frame.loaded(wx, wz)) continue;
            boolean matches = frame.world.getBlock(wx, wy, wz) == shape.block(i)
                && frame.world.getBlockMetadata(wx, wy, wz) == shape.metadata(i);
            if (matches || substitutedPort(shape, i, frame, wx, wy, wz)) continue;
            frame.world.setBlock(wx, wy, wz, shape.block(i), shape.metadata(i), 2);
        }
    }

    private static Shape shape(String key) {
        Shape shape = optionalShape(key);
        if (shape == null) throw new IllegalStateException("Missing ore center layout " + key);
        return shape;
    }

    static synchronized Shape optionalShape(String key) {
        Shape cached = SHAPES.get(key);
        if (cached != null) return cached;
        InputStream resource = CenterStructure.class.getResourceAsStream("/assets/gtsr/orecenter/" + key + ".gz");
        if (resource == null) return null;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(resource))) {
            int colors = in.readUnsignedShort(), count = in.readInt();
            if (colors < 1 || colors > 255 || count < 1 || count > 200000) throw new IOException("Invalid block count");
            Block[] blocks = new Block[colors];
            byte[] metas = new byte[colors];
            for (int i = 0; i < colors; i++) {
                byte[] name = new byte[in.readUnsignedByte()];
                in.readFully(name);
                String[] id = new String(name, java.nio.charset.StandardCharsets.UTF_8).split(":", 2);
                blocks[i] = GameRegistry.findBlock(id[0], id[1]);
                metas[i] = in.readByte();
            }
            boolean tower = key.startsWith("tower-");
            int shell = -1;
            if (tower) {
                int tier = key.charAt(key.length() - 1) - '0';
                Block casing = switch (tier) {
                    case 0 -> GregTechAPI.sBlockCasings1;
                    case 1 -> GregTechAPI.sBlockCasings2;
                    default -> GregTechAPI.sBlockCasings4;
                };
                int meta = tier == 0 ? 10 : tier == 2 ? 2 : 0;
                for (int i = 0; i < colors; i++) if (blocks[i] == casing && metas[i] == meta) shell = i;
                if (shell < 0) throw new IOException("Missing main tower casing");
            }
            Shape result = new Shape(
                tower,
                key.startsWith(SERVICE_TOWER),
                shell,
                blocks,
                metas,
                new short[count],
                new byte[count],
                new short[count],
                new byte[count]);
            for (int i = 0; i < count; i++) {
                result.x[i] = in.readShort();
                result.y[i] = in.readByte();
                result.z[i] = in.readShort();
                result.ids[i] = in.readByte();
                result.minX = Math.min(result.minX, result.x[i]);
                result.maxX = Math.max(result.maxX, result.x[i]);
                result.minZ = Math.min(result.minZ, result.z[i]);
                result.maxZ = Math.max(result.maxZ, result.z[i]);
            }
            SHAPES.put(key, result);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Invalid ore center layout " + key, e);
        }
    }

    static final class Frame {

        final World world;
        final int x, y, z;
        final ForgeDirection facing;

        Frame(IGregTechTileEntity te) {
            world = te.getWorld();
            x = te.getXCoord();
            y = te.getYCoord();
            z = te.getZCoord();
            facing = CriticalStructureAlignment.horizontal(te.getFrontFacing());
        }

        int x(int x, int z) {
            return this.x + CriticalStructureAlignment.rotateX(x, z, facing);
        }

        int z(int x, int z) {
            return this.z + CriticalStructureAlignment.rotateZ(x, z, facing);
        }

        boolean loaded(int x, int z) {
            return world.getChunkProvider()
                .chunkExists(x >> 4, z >> 4);
        }
    }

    static final class Shape {

        final boolean tower, service;
        final int shell;
        int minX, maxX, minZ, maxZ;
        final Block[] blocks;
        final byte[] metas;
        final short[] x, z;
        final byte[] y, ids;
        private long[] lookup;

        Shape(boolean tower, boolean service, int shell, Block[] blocks, byte[] metas, short[] x, byte[] y, short[] z, byte[] ids) {
            this.tower = tower;
            this.service = service;
            this.shell = shell;
            this.blocks = blocks;
            this.metas = metas;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ids = ids;
        }

        int count() {
            return x.length;
        }

        Block block(int i) {
            return blocks[Byte.toUnsignedInt(ids[i])];
        }

        int metadata(int i) {
            return Byte.toUnsignedInt(metas[Byte.toUnsignedInt(ids[i])]);
        }

        void prepareLookup() {
            if (lookup != null) return;
            long[] values = new long[count()];
            for (int i = 0; i < values.length; i++) values[i] = ((long) cell(x[i], y[i], z[i]) << 16) | i;
            java.util.Arrays.sort(values);
            lookup = values;
        }

        int lookup(int x, int y, int z) {
            if (x < -128 || x > 128 || z < -128 || z > 128 || y < 0 || y > 127) return -1;
            int key = cell(x, y, z);
            int lo = 0, hi = lookup.length - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                long entry = lookup[mid];
                int found = (int) (entry >>> 16);
                if (found == key) return (int) (entry & 0xFFFF);
                if (found < key) lo = mid + 1;
                else hi = mid - 1;
            }
            return -1;
        }

        private static int cell(int x, int y, int z) {
            return ((x + 128) << 17) | ((z + 128) << 8) | y;
        }
    }

    private static final class ModuleCheck {

        final String resource;
        final CriticalStructureMonitor monitor = new CriticalStructureMonitor();
        boolean damaged;

        ModuleCheck(String resource) {
            this.resource = resource;
        }
    }
}
