// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import java.util.zip.GZIPInputStream;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
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
import com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase;
import com.miaokatze.gtsr.common.machine.recovery.GtsrStructureRecovery;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

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
    private static final int MAX_FOOTPRINT_CACHE = 128;
    final WeakReference<CenterStructure> scanQueueReference = new WeakReference<>(this);
    private final Map<FootprintKey, long[]> footprints = new LinkedHashMap<>(MAX_FOOTPRINT_CACHE, 0.75F, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<FootprintKey, long[]> eldest) {
            return size() > MAX_FOOTPRINT_CACHE;
        }
    };
    private World footprintWorld;
    private int footprintX, footprintY, footprintZ;
    private ForgeDirection footprintFacing = ForgeDirection.UNKNOWN;
    private static final String[][][] MODULE_KEYS = moduleKeys();
    private final CriticalStructureMonitor tower = new CriticalStructureMonitor();
    private final CriticalStructureMonitor[] sectors = new CriticalStructureMonitor[6];
    private final ModuleCheck[] modules = new ModuleCheck[36];
    private ForgeDirection facing = ForgeDirection.UNKNOWN;
    private int towerTier = -1;
    private int formedSectors;
    private int scanCursor;
    private final long[] lastScan = new long[7];
    private long moduleRevision;
    private boolean attachClaims = true;
    private int survivalCursor, survivalTier = -1, survivalMask;
    private boolean serviceFront;
    private SavedFormation savedFormation, lastFormation, portIdentity;
    // Unlike lastFormation, these obligations survive revocation of a damaged optional piece.
    private int reviewSectors;
    private long reviewModules;
    private final java.util.Set<Integer> knownPorts = new HashSet<>();
    private boolean portIndexReady, portIndexUnknown;
    private int portIndexCursor;
    private boolean restoredTower;
    private long nextRestoreCheck;
    private static int[] legacyPlinthPorts, servicePlinthPorts;

    private static final class SavedFormation {

        final ForgeDirection facing;
        final int dimension, x, y, z, tier, sectors;
        final boolean service;
        final long modules;

        SavedFormation(ForgeDirection facing, int dimension, int x, int y, int z, int tier, boolean service,
            int sectors, long modules) {
            this.facing = facing;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tier = tier;
            this.service = service;
            this.sectors = sectors;
            this.modules = modules;
        }

        boolean matches(Frame frame) {
            return facing == frame.facing && dimension == frame.world.provider.dimensionId
                && x == frame.x
                && y == frame.y
                && z == frame.z;
        }
    }

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

    boolean isCheckingFormation(IGregTechTileEntity te) {
        if (facing != te.getFrontFacing()) return true;
        if (tower.hasKnownDamage()) return false;
        if (savedFormation != null && formedSectors == 0 || recovering(te)) return true;
        if (towerTier < 0) return false;
        Status status = tower.getStatus();
        if (status == Status.PENDING || status == Status.UNLOADED) return true;
        if (status != Status.VALID || formedSectors != 0) return false;
        for (CriticalStructureMonitor sector : sectors) {
            if (sector.getStatus() == Status.PENDING || sector.getStatus() == Status.UNLOADED) return true;
        }
        return false;
    }

    private static boolean recovering(IGregTechTileEntity te) {
        return te.getMetaTileEntity() instanceof MTEMultiBlockBase machine
            && GtsrStructureRecovery.isRecovering(machine);
    }

    /** Chunk IO cannot advance a paid batch, even while its last verified shape remains cached. */
    boolean isExecutionReady(IGregTechTileEntity te) {
        if (tier(te) < 0 || formedSectors == 0) return false;
        Frame frame = new Frame(te);
        if (!portIndexReady || !knownPortsReady(frame)) return false;
        if (!footprintLoaded(towerShape(towerTier, serviceFront), frame, 0, 0, 0)) return false;
        int expectedSectors = formedSectors | (lastFormation == null ? 0 : lastFormation.sectors);
        for (int i = 0; i < sectors.length; i++)
            if ((reviewSectors & 1 << i) != 0 && sectors[i].hasKnownDamage()) return false;
        for (int i = 0; i < sectors.length; i++) if ((expectedSectors & 1 << i) != 0) {
            if (sectors[i].getStatus() == Status.PENDING || sectors[i].getStatus() == Status.UNLOADED
                || !footprintLoaded(shape("sector-" + i), frame, 0, 0, 0)) return false;
        }
        return true;
    }

    /** A cached verdict may protect paid work, but cannot authorize a fresh debit before its full recheck. */
    boolean isNewBatchReady(IGregTechTileEntity te) {
        if (!isExecutionReady(te) || reviewSectors != 0 || !verified(tower)) return false;
        for (int i = 0; i < sectors.length; i++)
            if ((formedSectors & 1 << i) != 0 && !verified(sectors[i])) return false;
        return true;
    }

    private static boolean verified(CriticalStructureMonitor monitor) {
        // Paid construction is already checked voxel by voxel and legitimately has zero monitor rounds.
        return !monitor.isFastSweep() && monitor.getStatus() == Status.VALID;
    }

    private static boolean reviewed(CriticalStructureMonitor monitor) {
        return !monitor.isFastSweep() && monitor.getCompletedRounds() > 0
            && (monitor.getStatus() == Status.VALID || monitor.getStatus() == Status.BROKEN);
    }

    private void finishReviews() {
        for (int i = 0; i < sectors.length; i++)
            if ((reviewSectors & 1 << i) != 0 && reviewed(sectors[i])) reviewSectors &= ~(1 << i);
    }

    /** Release only runtime references. Generated voxel ownership and paid batch state are unchanged. */
    public void detach(IGregTechTileEntity te) {
        if (te != null && te.getWorld() != null) CenterClaims.detach(te.getWorld(), this);
        clearRuntimeReferences();
    }

    boolean hasUnconvertedModules() {
        for (ModuleCheck module : modules) if (module != null && !module.virtual) return true;
        return false;
    }

    public void controllerRemoved(IGregTechTileEntity te) {
        if (te != null && te.getWorld() != null && !te.getWorld().isRemote) CenterClaims.get(te.getWorld())
            .retireCompleted(new Frame(te));
        detach(te);
    }

    void clearRuntimeReferences() {
        footprints.clear();
        footprintWorld = null;
        footprintFacing = ForgeDirection.UNKNOWN;
        attachClaims = true;
    }

    void collectHatches(IGregTechTileEntity te, ObjIntConsumer<IGregTechTileEntity> accept) {
        if (towerTier < 0 || facing != te.getFrontFacing()) return;
        int casingTexture = MTEClusterUnitBase.tierCasingTextureId(towerTier);
        Frame frame = new Frame(te);
        collectHatches(frame, towerShape(towerTier, serviceFront), hatch -> {
            int dx = hatch.getXCoord() - frame.x, dz = hatch.getZCoord() - frame.z;
            int x = (int) CriticalStructureAlignment.unrotateX(dx, dz, frame.facing);
            int z = (int) CriticalStructureAlignment.unrotateZ(dx, dz, frame.facing) - 19;
            knownPorts.add(Shape.cell(x, hatch.getYCoord() - frame.y + 2, z));
            accept.accept(hatch, casingTexture);
        });
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
                    if (!(tile instanceof TileEntity entity) || entity.isInvalid()
                        || !(entity instanceof IGregTechTileEntity gt)
                        || !MTEMineralIntegrationCenter.acceptsPort(gt.getMetaTileEntity())) continue;
                    int dx = gt.getXCoord() - frame.x, dz = gt.getZCoord() - frame.z;
                    int x = (int) CriticalStructureAlignment.unrotateX(dx, dz, frame.facing);
                    int z = (int) CriticalStructureAlignment.unrotateZ(dx, dz, frame.facing);
                    int y = gt.getYCoord() - frame.y;
                    int i = tower.lookup(x, y + 2, z - 19);
                    if (i >= 0 && portCasing(tower, i)
                        || !tower.service && y == 0 && z == 1 && java.util.Arrays.binarySearch(HATCH_X, x) >= 0)
                        accept.accept(gt);
                }
            }
        }
    }

    private static synchronized int[] plinthPorts(Shape tower) {
        int[] ports = tower.service ? servicePlinthPorts : legacyPlinthPorts;
        if (ports == null) {
            ports = java.util.stream.IntStream.range(0, tower.count())
                .filter(i -> plinthCasing(tower, i))
                .filter(
                    i -> outside(tower, tower.x[i] - 1, tower.z[i]) || outside(tower, tower.x[i] + 1, tower.z[i])
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

    static boolean portCasing(Shape shape, int i) {
        if (!shape.tower) return false;
        Block block = shape.block(i);
        int meta = shape.metadata(i);
        if (Byte.toUnsignedInt(shape.ids[i]) == shape.shell || block == GregTechAPI.sBlockCasings3 && meta == 10)
            return true;
        if (!plinthCasing(shape, i)) return false;
        Shape base = towerShape(0, shape.service);
        int[] ports = plinthPorts(base);
        return java.util.Arrays.binarySearch(ports, base.lookup(shape.x[i], 2, shape.z[i])) >= 0;
    }

    private static boolean substitutedPort(Shape shape, int i, Frame frame, int wx, int wy, int wz) {
        return shape.tower && frame.world.getBlock(wx, wy, wz) == GregTechAPI.sBlockMachines
            && portCasing(shape, i)
            && frame.world.getTileEntity(wx, wy, wz) instanceof IGregTechTileEntity gt
            && MTEMineralIntegrationCenter.acceptsPort(gt.getMetaTileEntity());
    }

    void showPreviewPorts(IGregTechTileEntity te, int tier) {
        Frame frame = new Frame(te);
        ItemStack[] examples = { ItemList.Hatch_Input_LV.get(1), ItemList.Hatch_Output_LV.get(1),
            ItemList.Hatch_Input_Bus_LV.get(1), ItemList.Hatch_Output_Bus_LV.get(1),
            GTSRItemList.SteamInputHatchGeneric.get(1), GTSRItemList.SteamCoolingHatch.get(1),
            ItemList.Hatch_Energy_LV.get(1) };
        for (int i = 0; i < examples.length; i++) placeExamplePort(frame, SERVICE_PORT_X[i], 0, examples[i], tier);
    }

    private static void placeExamplePort(Frame frame, int x, int z, ItemStack stack, int tier) {
        short id = (short) stack.getItemDamage();
        int wx = frame.x(x, z), wz = frame.z(x, z);
        frame.world.setBlock(
            wx,
            frame.y,
            wz,
            GregTechAPI.sBlockMachines,
            GregTechAPI.METATILEENTITIES[id].getTileEntityBaseType(),
            2);
        if (frame.world.getTileEntity(wx, frame.y, wz) instanceof IGregTechTileEntity hatch) {
            hatch.setInitialValuesAsNBT(null, id);
            hatch.setFrontFacing(frame.facing);
            if (hatch.getMetaTileEntity() instanceof MTEHatch port)
                port.updateTexture(MTEClusterUnitBase.tierCasingTextureId(tier));
        }
    }

    public void tick(IGregTechTileEntity te) {
        if (te == null || te.getWorld().isRemote) return;
        World world = te.getWorld();
        CenterClaims claims = CenterClaims.get(world);
        if (!claims.allowsScan(this, world.getTotalWorldTime())) return;
        long started = System.nanoTime();
        try {
            migrateLegacy(te, claims);
            scanStructure(te, new Frame(te), claims);
            if (tower.hasKnownDamage() && te.getMetaTileEntity() instanceof MTEMultiBlockBase machine)
                GtsrStructureRecovery.confirmedDamage(machine);
        } finally {
            finishReviews();
            claims.countScan(System.nanoTime() - started);
        }
    }

    private void scanStructure(IGregTechTileEntity te, Frame frame, CenterClaims claims) {
        if (attachClaims || te.getFrontFacing() != facing) claims.track(frame, this);
        if (attachClaims) {
            attachClaims = false;
        }
        if (te.getFrontFacing() != facing) {
            facing = te.getFrontFacing();
            towerTier = -1;
            tower.reset();
            for (CriticalStructureMonitor sector : sectors) sector.reset();
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
        if (found < 0 && !TierAnchor.INSTANCE.unloaded(frame)) {
            tower.invalidate();
            savedFormation = lastFormation = null;
            return;
        }
        restoreFormation(frame, found, service);
        if (towerTier < 0) return;
        frame.awaitingPorts = recovering(te);
        bindPortIdentity(frame);
        if (!portIndexReady) {
            indexPorts(frame);
            return;
        }

        // Fast recovery owns one bounded slice; stable monitors share a fair 128-sample tick budget.
        if (tower.getStatus() == Status.PENDING) {
            scan(tower, towerShape(towerTier, serviceFront), frame, 0, 0, 0, 0, false, elapsed(frame, 0));
            if (tower.hasKnownDamage()) savedFormation = lastFormation = null;
            return;
        }
        int remaining = CriticalStructureMonitor.MAX_CHECKS_PER_TICK;
        for (int attempts = 0; attempts < lastScan.length && remaining > 0; attempts++) {
            int monitor = scanCursor;
            scanCursor = (scanCursor + 1) % lastScan.length;
            if (monitor == 0) {
                boolean fast = tower.isFastSweep();
                scan(
                    tower,
                    towerShape(towerTier, serviceFront),
                    frame,
                    0,
                    0,
                    0,
                    0,
                    false,
                    elapsed(frame, 0),
                    fast ? CriticalStructureMonitor.MAX_INITIAL_CHECKS_PER_TICK
                        - (CriticalStructureMonitor.MAX_CHECKS_PER_TICK - remaining) : remaining);
                if (tower.hasKnownDamage()) savedFormation = lastFormation = null;
                if (fast) return;
                remaining -= tower.getLastChecks();
                continue;
            }
            int i = monitor - 1;
            if (i < sectors.length) {
                Shape piece = shape("sector-" + i);
                Sample marker = sample(piece, 0, frame, 0, 0, 0, 0, false);
                remaining--;
                if (marker == Sample.BROKEN) {
                    sectors[i].invalidate();
                    revokeSector(i);
                    // An absent, never-formed optional wing needs no ongoing full sweep.
                    if ((reviewSectors & 1 << i) == 0
                        && (lastFormation == null || (lastFormation.sectors & 1 << i) == 0)) continue;
                }
                boolean fast = sectors[i].isFastSweep();
                if (scan(
                    sectors[i],
                    piece,
                    frame,
                    0,
                    0,
                    0,
                    0,
                    false,
                    elapsed(frame, monitor),
                    fast ? CriticalStructureMonitor.MAX_INITIAL_CHECKS_PER_TICK
                        - (CriticalStructureMonitor.MAX_CHECKS_PER_TICK - remaining) : remaining))
                    formedSectors |= 1 << i;
                else if (!sectors[i].hasKnownDamage()) formedSectors &= ~(1 << i);
                if (sectors[i].hasKnownDamage()) revokeSector(i);
                if (fast) return;
                remaining -= sectors[i].getLastChecks();
                continue;
            }

        }
    }

    private void restoreFormation(Frame frame, int found, boolean service) {
        SavedFormation saved = savedFormation;
        if (saved == null) return;
        if (tower.hasKnownDamage()) {
            savedFormation = lastFormation = null;
            return;
        }
        if (!saved.matches(frame) || found >= 0 && saved.tier != found) {
            savedFormation = lastFormation = null;
            return;
        }
        if (found < 0) {
            if (!TierAnchor.INSTANCE.unloaded(frame)) {
                savedFormation = lastFormation = null;
            }
            return;
        }
        long now = frame.world.getTotalWorldTime();
        if (now < nextRestoreCheck) return;
        nextRestoreCheck = now + 20;
        Shape body = towerShape(found, saved.service);
        if (!restoredTower) {
            if (!footprintLoaded(body, frame, 0, 0, 0)) return;
            if (saved.service != service) {
                savedFormation = lastFormation = null;
                return;
            }
            tower.restoreValidation(body.count());
            restoredTower = true;
        }
        int waitingSectors = 0;
        long waitingModules = 0;
        for (int i = 0; i < sectors.length; i++) if ((saved.sectors & 1 << i) != 0) {
            if (sectors[i].hasKnownDamage()) {
                revokeSector(i);
                continue;
            }
            Shape wing = shape("sector-" + i);
            Sample marker = sample(wing, 0, frame, 0, 0, 0, 0, false);
            if (marker == Sample.BROKEN) {
                sectors[i].invalidate();
                revokeSector(i);
            } else if (marker == Sample.UNLOADED || !footprintLoaded(wing, frame, 0, 0, 0)) waitingSectors |= 1 << i;
            else {
                sectors[i].restoreValidation(wing.count());
                formedSectors |= 1 << i;
            }
        }
        savedFormation = waitingSectors == 0 && waitingModules == 0 ? null
            : new SavedFormation(
                saved.facing,
                saved.dimension,
                saved.x,
                saved.y,
                saved.z,
                saved.tier,
                saved.service,
                waitingSectors,
                waitingModules);
    }

    private void bindPortIdentity(Frame frame) {
        if (portIdentity != null && portIdentity.matches(frame)
            && portIdentity.tier == towerTier
            && portIdentity.service == serviceFront) return;
        knownPorts.clear();
        portIndexReady = portIndexUnknown = false;
        portIndexCursor = 0;
        portIdentity = new SavedFormation(
            frame.facing,
            frame.world.provider.dimensionId,
            frame.x,
            frame.y,
            frame.z,
            towerTier,
            serviceFront,
            0,
            0);
    }

    /** Old saves discover port blocks once, in bounded slices, without adding another monitor slice to that tick. */
    private void indexPorts(Frame frame) {
        Shape body = towerShape(towerTier, serviceFront);
        long deadline = System.nanoTime() + CriticalStructureMonitor.INITIAL_SCAN_NANOS;
        int checks = 0;
        int total = body.count() + (body.service ? 0 : HATCH_X.length);
        while (portIndexCursor < total && checks < CriticalStructureMonitor.MAX_INITIAL_CHECKS_PER_TICK) {
            int i = portIndexCursor++;
            if (i >= body.count()) indexPort(frame, HATCH_X[i - body.count()], 2, -18);
            else {
                if (portCasing(body, i)) indexPort(frame, body.x[i], body.y[i], body.z[i]);
                if (sample(body, i, frame, 0, 0, 0, 0, false) == Sample.BROKEN) {
                    tower.invalidate();
                    savedFormation = lastFormation = null;
                    confirmDamage(frame);
                }
            }
            checks++;
            if ((checks & 31) == 0 && System.nanoTime() >= deadline) return;
        }
        if (portIndexCursor < total) return;
        portIndexReady = !portIndexUnknown;
        if (portIndexUnknown) {
            portIndexCursor = 0;
            portIndexUnknown = false;
        }
    }

    private void indexPort(Frame frame, int x, int y, int z) {
        int wx = frame.x(x, z + 19), wz = frame.z(x, z + 19);
        if (!frame.loaded(wx, wz)) {
            portIndexUnknown = true;
            return;
        }
        if (frame.world.getBlock(wx, frame.y + y - 2, wz) == GregTechAPI.sBlockMachines)
            knownPorts.add(Shape.cell(x, y, z));
    }

    /** Cached formation may run a background sweep, but every remembered native port must already exist. */
    private boolean knownPortsReady(Frame frame) {
        Shape body = towerShape(towerTier, serviceFront);
        body.prepareLookup();
        java.util.Iterator<Integer> ports = knownPorts.iterator();
        while (ports.hasNext()) {
            int cell = ports.next();
            int x = ((cell >>> 17) & 511) - 128, y = cell & 255, z = ((cell >>> 8) & 511) - 128;
            int index = body.lookup(x, y, z);
            boolean extra = !body.service && y == 2 && z == -18 && java.util.Arrays.binarySearch(HATCH_X, x) >= 0;
            if (!extra && (index < 0 || !portCasing(body, index))) {
                ports.remove();
                portIndexReady = false;
                portIndexCursor = 0;
                return false;
            }
            int wx = frame.x(x, z + 19), wy = frame.y + y - 2, wz = frame.z(x, z + 19);
            if (!frame.loaded(wx, wz)) return false;
            Block actual = frame.world.getBlock(wx, wy, wz);
            if (actual != GregTechAPI.sBlockMachines) {
                if (extra || actual == body.block(index)
                    && frame.world.getBlockMetadata(wx, wy, wz) == body.metadata(index)) {
                    ports.remove();
                    continue;
                }
                tower.invalidate();
                confirmDamage(frame);
                return false;
            }
            TileEntity tile = frame.world.getTileEntity(wx, wy, wz);
            var meta = tile instanceof IGregTechTileEntity gt ? gt.getMetaTileEntity() : null;
            if (meta != null && !MTEMineralIntegrationCenter.acceptsPort(meta)) {
                tower.invalidate();
                confirmDamage(frame);
                return false;
            }
            if (tile == null || tile.isInvalid() || meta == null) {
                if (!frame.awaitingPorts) {
                    tower.invalidate();
                    confirmDamage(frame);
                }
                return false;
            }
        }
        return true;
    }

    private boolean footprintLoaded(Shape shape, Frame frame, int x, int z, int rotation) {
        // Bind offsets to one controller frame. Absolute positions never become persistent cache keys.
        if (footprintWorld != frame.world || footprintX != frame.x
            || footprintY != frame.y
            || footprintZ != frame.z
            || footprintFacing != frame.facing) {
            footprints.clear();
            footprintWorld = frame.world;
            footprintX = frame.x;
            footprintY = frame.y;
            footprintZ = frame.z;
            footprintFacing = frame.facing;
        }
        FootprintKey key = new FootprintKey(shape, x, z, rotation);
        long[] offsets = footprints.get(key);
        if (offsets == null) {
            java.util.Set<Long> chunks = new HashSet<>();
            for (int column : shape.columns()) {
                int sx = (short) (column >> 16), sz = (short) column;
                int localX = x + rotateX(sx, sz, rotation), localZ = z + rotateZ(sx, sz, rotation) + 19;
                int cx = (frame.x(localX, localZ) >> 4) - (frame.x >> 4);
                int cz = (frame.z(localX, localZ) >> 4) - (frame.z >> 4);
                chunks.add(((long) cx << 32) | (cz & 0xFFFFFFFFL));
            }
            offsets = chunks.stream()
                .mapToLong(Long::longValue)
                .sorted()
                .toArray();
            footprints.put(key, offsets);
        }
        // Read readiness every time: unloading a previously ready chunk must pause the very next batch tick.
        for (long offset : offsets) {
            int cx = (frame.x >> 4) + (int) (offset >> 32), cz = (frame.z >> 4) + (int) offset;
            if (!frame.world.getChunkProvider()
                .chunkExists(cx, cz)) return false;
        }
        return true;
    }

    private static final class FootprintKey {

        final Shape shape;
        final int x, z, rotation;

        FootprintKey(Shape shape, int x, int z, int rotation) {
            this.shape = shape;
            this.x = x;
            this.z = z;
            this.rotation = rotation;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FootprintKey key && shape == key.shape
                && x == key.x
                && z == key.z
                && rotation == key.rotation;
        }

        @Override
        public int hashCode() {
            return ((System.identityHashCode(shape) * 31 + x) * 31 + z) * 31 + rotation;
        }
    }

    private int elapsed(Frame frame, int monitor) {
        long now = frame.world.getTotalWorldTime(), prev = lastScan[monitor];
        lastScan[monitor] = now;
        return (int) Math.max(1, Math.min(64, now - prev));
    }

    private static int detectTier(Frame frame) {
        TierAnchor anchor = TierAnchor.INSTANCE;
        int x = frame.x(anchor.x, anchor.z), y = frame.y + anchor.y, z = frame.z(anchor.x, anchor.z);
        if (y < 0 || y >= frame.world.getHeight() || !frame.loaded(x, z)) return -1;
        Block block = frame.world.getBlock(x, y, z);
        int metadata = frame.world.getBlockMetadata(x, y, z);
        for (int i = 0; i < anchor.blocks.length; i++) {
            if (block == anchor.blocks[i] && metadata == anchor.metadata[i]) return i;
        }
        return -1;
    }

    private static final class TierAnchor {

        static final TierAnchor INSTANCE = find();
        final int x, y, z;
        final Block[] blocks = new Block[4];
        final int[] metadata = new int[4];

        TierAnchor(Shape[] towers, int index) {
            x = towers[0].x[index];
            y = towers[0].y[index] - 2;
            z = towers[0].z[index] + 19;
            for (int tier = 0; tier < towers.length; tier++) {
                blocks[tier] = towers[tier].block(index);
                metadata[tier] = towers[tier].metadata(index);
            }
        }

        boolean unloaded(Frame frame) {
            int worldY = frame.y + y;
            return worldY >= 0 && worldY < frame.world.getHeight() && !frame.loaded(frame.x(x, z), frame.z(x, z));
        }

        private static TierAnchor find() {
            Shape[] towers = { shape("tower-0"), shape("tower-1"), shape("tower-2"), shape("tower-3") };
            Shape first = towers[0];
            for (int index = 0; index < first.count(); index++) {
                if (first.y[index] < 5 || portCasing(first, index)) continue;
                boolean valid = true;
                for (int tier = 1; tier < towers.length && valid; tier++) {
                    Shape other = towers[tier];
                    if (index >= other.count() || other.x[index] != first.x[index]
                        || other.y[index] != first.y[index]
                        || other.z[index] != first.z[index]
                        || portCasing(other, index)) {
                        valid = false;
                        break;
                    }
                    for (int lower = 0; lower < tier; lower++) {
                        if (other.block(index) == towers[lower].block(index)
                            && other.metadata(index) == towers[lower].metadata(index)) valid = false;
                    }
                }
                if (valid) return new TierAnchor(towers, index);
            }
            throw new IllegalStateException("No ore center tier key");
        }
    }

    private static boolean scan(CriticalStructureMonitor monitor, Shape shape, Frame frame, int x, int y, int z,
        int rotation, boolean module, int elapsed) {
        return monitor.tickSamples(shape.count(), elapsed, i -> sample(shape, i, frame, x, y, z, rotation, module));
    }

    private static boolean scan(CriticalStructureMonitor monitor, Shape shape, Frame frame, int x, int y, int z,
        int rotation, boolean module, int elapsed, int maxChecks) {
        return monitor
            .tickSamples(shape.count(), elapsed, maxChecks, i -> sample(shape, i, frame, x, y, z, rotation, module));
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
        Block actual = frame.world.getBlock(xx, yy, zz);
        if (actual == shape.block(i) && frame.world.getBlockMetadata(xx, yy, zz) == shape.metadata(i))
            return Sample.MATCH;
        if (!module && shape.tower && actual == GregTechAPI.sBlockMachines && portCasing(shape, i)) {
            TileEntity tile = frame.world.getTileEntity(xx, yy, zz);
            if (tile instanceof IGregTechTileEntity gt) {
                var meta = gt.getMetaTileEntity();
                if (MTEMineralIntegrationCenter.acceptsPort(meta)) return Sample.MATCH;
                if (meta == null && frame.awaitingPorts) return Sample.UNLOADED;
            } else if (tile == null && frame.awaitingPorts) return Sample.UNLOADED;
        }
        return Sample.BROKEN;
    }

    private static String[][][] moduleKeys() {
        CenterModule[] types = CenterModule.values();
        String[][][] keys = new String[6][types.length][3];
        for (CenterModule module : types) {
            String id = module.key.toLowerCase(Locale.ROOT);
            for (int pad = 0; pad < keys.length; pad++) {
                for (int grade = 1; grade <= 3; grade++)
                    keys[pad][module.ordinal()][grade - 1] = "module-" + pad + "-" + id + "-" + grade;
            }
        }
        return keys;
    }

    private static String moduleKey(int slot, CenterModule module, int grade) {
        return MODULE_KEYS[slot % 6][module.ordinal()][grade - 1];
    }

    boolean moduleBroken(int slot, CenterModule module, int grade) {
        ModuleCheck check = checkModule(slot, module, grade);
        return check != null && check.damaged;
    }

    public boolean moduleLoaded(IGregTechTileEntity te, int slot, CenterModule module, int grade) {
        ModuleCheck check = checkModule(slot, module, grade);
        return check != null && (check.virtual || check.damaged);
    }

    /** Installed ownership is independent of decorative world blocks and their loaded chunks. */
    public boolean moduleIntact(IGregTechTileEntity te, int slot, CenterModule module, int grade) {
        ModuleCheck check = checkModule(slot, module, grade);
        return check != null && check.virtual && !check.damaged;
    }

    private ModuleCheck checkModule(int slot, CenterModule module, int grade) {
        if (slot < 0 || slot >= 36 || grade < 1 || grade > 3 || module == null) return null;
        String key = moduleKey(slot, module, grade);
        return modules[slot] != null && modules[slot].resource.equals(key) ? modules[slot] : null;
    }

    public boolean canInstall(IGregTechTileEntity te, int slot, CenterModule module, int grade) {
        return te != null && !te.getWorld().isRemote
            && slot >= 0
            && slot < 36
            && grade >= 1
            && grade <= 3
            && module != null
            && module.acceptsGrade(grade, tier(te))
            && (sectorMask(te) & 1 << (slot / 6)) != 0
            && (modules[slot] == null || modules[slot].damaged)
            && !CenterClaims.get(te.getWorld())
                .hasLegacy(new Frame(te), slot);
    }

    /** Records a paid package without reading a template, clearing terrain, or writing any world voxel. */
    public boolean install(IGregTechTileEntity te, int slot, CenterModule module, int grade) {
        if (!canInstall(te, slot, module, grade)) return false;
        modules[slot] = new ModuleCheck(moduleKey(slot, module, grade));
        modules[slot].virtual = true;
        moduleRevision++;
        return true;
    }

    /** The caller owns the single pending-output refund transaction. */
    public boolean remove(IGregTechTileEntity te, int slot, CenterModule module, int grade) {
        if (te == null || te.getWorld().isRemote || !moduleIntact(te, slot, module, grade)) return false;
        modules[slot] = null;
        moduleRevision++;
        reviewModules &= ~(1L << slot);
        return true;
    }

    long moduleRevision() {
        return moduleRevision;
    }

    private void migrateLegacy(IGregTechTileEntity te, CenterClaims claims) {
        Frame frame = new Frame(te);
        for (int slot = 0; slot < modules.length; slot++) {
            ModuleCheck module = modules[slot];
            if (module != null && module.receiptLoaded) claims.retireLegacy(frame, slot, module.resource);
        }
        long revision = moduleRevision;
        claims.migrateLegacy(frame, this, CriticalStructureMonitor.MAX_INITIAL_CHECKS_PER_TICK);
        if (revision != moduleRevision) te.markDirty();
        for (int slot = 0; slot < modules.length; slot++) {
            ModuleCheck module = modules[slot];
            if (module == null || module.virtual || claims.hasLegacy(frame, slot)) continue;
            // Completion is recorded in the controller before the claim can be retired. Missing proof is loss.
            if (!module.migrationComplete) module.damaged = true;
            module.virtual = true;
            moduleRevision++;
            te.markDirty();
        }
    }

    boolean legacyRecord(int slot, String resource) {
        return slot >= 0 && slot < modules.length && modules[slot] != null && modules[slot].resource.equals(resource);
    }

    void legacyDamage(int slot) {
        if (slot >= 0 && slot < modules.length
            && modules[slot] != null
            && !modules[slot].virtual
            && !modules[slot].damaged) {
            modules[slot].damaged = true;
            moduleRevision++;
        }
    }

    void legacyComplete(int slot, String resource, boolean damaged) {
        ModuleCheck module = slot >= 0 && slot < modules.length ? modules[slot] : null;
        if (module == null || module.virtual || !module.resource.equals(resource)) return;
        module.damaged |= damaged;
        module.migrationComplete = true;
        module.virtual = true;
        moduleRevision++;
    }

    boolean permitsExternalRemoval(int slot) {
        return towerTier < 0 || tower.getStatus() == Status.BROKEN
            || (formedSectors & 1 << (slot / 6)) == 0 && sectors[slot / 6].getStatus() == Status.BROKEN;
    }

    void invalidateModule(int slot) {
        if (modules[slot] == null || modules[slot].virtual) return;
        modules[slot].damaged = true;
        moduleRevision++;
        lastFormation = without(lastFormation, 0, 1L << slot);
        savedFormation = without(savedFormation, 0, 1L << slot);
    }

    void changed(int x, int y, int z) {
        changed(null, x, y, z);
    }

    void changed(Frame frame, int x, int y, int z) {
        if (frame != null && towerTier >= 0) {
            Shape body = towerShape(towerTier, serviceFront);
            body.prepareLookup();
            int index = body.lookup(x, y, z);
            if (index >= 0 && portCasing(body, index)
                || !body.service && y == 2 && z == -18 && java.util.Arrays.binarySearch(HATCH_X, x) >= 0) {
                int cell = Shape.cell(x, y, z);
                if (frame.world.getBlock(frame.x(x, z + 19), frame.y + y - 2, frame.z(x, z + 19))
                    == GregTechAPI.sBlockMachines) knownPorts.add(cell);
                else knownPorts.remove(cell);
            }
        }
        if (towerTier >= 0 && y >= 0 && y <= 64 && x >= -22 && x <= 22 && z >= -22 && z <= 22) {
            Shape body = towerShape(towerTier, serviceFront);
            body.prepareLookup();
            int index = body.lookup(x, y, z);
            if (index >= 0 && (frame == null || sample(body, index, frame, 0, 0, 0, 0, false) == Sample.BROKEN)) {
                confirmDamage(frame);
                tower.invalidate();
                tower.requestFastSweep();
                savedFormation = lastFormation = null;
                return;
            }
        }
        int authorized = formedSectors | (lastFormation == null ? 0 : lastFormation.sectors);
        for (int i = 0; i < sectors.length; i++) if ((authorized & 1 << i) != 0) {
            Shape wing = shape("sector-" + i);
            wing.prepareLookup();
            int index = wing.lookup(x, y, z);
            if (index < 0 || frame != null && sample(wing, index, frame, 0, 0, 0, 0, false) != Sample.BROKEN) continue;
            sectors[i].invalidate();
            sectors[i].requestFastSweep();
            confirmDamage(frame);
            revokeSector(i);
            return;
        }
    }

    private static void confirmDamage(Frame frame) {
        if (frame == null || !frame.loaded(frame.x, frame.z)) return;
        if (frame.world.getTileEntity(frame.x, frame.y, frame.z) instanceof IGregTechTileEntity te
            && te.getMetaTileEntity() instanceof MTEMultiBlockBase machine)
            GtsrStructureRecovery.confirmedDamage(machine);
    }

    private void revokeSector(int sector) {
        if ((reviewSectors & 1 << sector) == 0 && ((formedSectors & 1 << sector) != 0
            || lastFormation != null && (lastFormation.sectors & 1 << sector) != 0)) {
            reviewSectors |= 1 << sector;
            sectors[sector].requestFastSweep();
        }
        formedSectors &= ~(1 << sector);
        long moduleMask = 63L << (sector * 6);
        lastFormation = without(lastFormation, 1 << sector, moduleMask);
        savedFormation = without(savedFormation, 1 << sector, moduleMask);
    }

    private static SavedFormation without(SavedFormation saved, int sectors, long modules) {
        if (saved == null) return null;
        return new SavedFormation(
            saved.facing,
            saved.dimension,
            saved.x,
            saved.y,
            saved.z,
            saved.tier,
            saved.service,
            saved.sectors & ~sectors,
            saved.modules & ~modules);
    }

    public void save(NBTTagCompound tag, IGregTechTileEntity te) {
        NBTTagCompound app = new NBTTagCompound();
        app.setInteger("ModuleFormat", 1);
        app.setLong("ModuleRevision", moduleRevision);
        app.setInteger("ReviewSectors", reviewSectors);
        app.setLong("ReviewModules", reviewModules);
        for (int i = 0; i < modules.length; i++) if (modules[i] != null) {
            app.setString(Integer.toString(i), modules[i].resource);
            if (modules[i].damaged) app.setBoolean("d" + i, true);
            app.setBoolean("v" + i, modules[i].virtual);
            app.setBoolean("m" + i, modules[i].migrationComplete);
        }
        SavedFormation saved = lastFormation;
        if (saved != null && (te == null || !saved.matches(new Frame(te))
            || tower.hasKnownDamage()
            || towerTier >= 0 && towerTier != saved.tier)) saved = null;
        for (int i = 0; i < sectors.length; i++)
            if (sectors[i].hasKnownDamage()) saved = without(saved, 1 << i, 63L << (i * 6));
        for (int i = 0; i < modules.length; i++)
            if (modules[i] == null || modules[i].damaged) saved = without(saved, 0, 1L << i);
        if (te != null && facing == te.getFrontFacing() && tower.getStatus() == Status.VALID && formedSectors != 0) {
            long verifiedModules = 0;
            for (int i = 0; i < modules.length; i++)
                if (modules[i] != null && !modules[i].damaged && modules[i].virtual) verifiedModules |= 1L << i;
            saved = new SavedFormation(
                facing,
                te.getWorld().provider.dimensionId,
                te.getXCoord(),
                te.getYCoord(),
                te.getZCoord(),
                towerTier,
                serviceFront,
                formedSectors | (saved == null ? 0 : saved.sectors),
                verifiedModules | (saved == null ? 0 : saved.modules));
        }
        lastFormation = saved;
        if (saved != null) {
            if (portIndexReady && portIdentity != null
                && portIdentity.facing == saved.facing
                && portIdentity.tier == saved.tier
                && portIdentity.service == saved.service) {
                app.setInteger("PortIndexVersion", 1);
                app.setIntArray(
                    "Ports",
                    knownPorts.stream()
                        .mapToInt(Integer::intValue)
                        .sorted()
                        .toArray());
            }
            app.setInteger("FormationVersion", 1);
            app.setInteger("Facing", saved.facing.ordinal());
            app.setInteger("Dimension", saved.dimension);
            app.setInteger("X", saved.x);
            app.setInteger("Y", saved.y);
            app.setInteger("Z", saved.z);
            app.setInteger("Tier", saved.tier);
            app.setBoolean("Service", saved.service);
            app.setInteger("Sectors", saved.sectors);
            app.setLong("VerifiedModules", saved.modules);
        }
        tag.setTag("oreCenterStructure", app);
    }

    public void load(NBTTagCompound tag) {
        NBTTagCompound app = tag.getCompoundTag("oreCenterStructure");
        ForgeDirection savedFacing = ForgeDirection.getOrientation(app.getInteger("Facing"));
        int savedTier = app.getInteger("Tier"), savedSectors = app.getInteger("Sectors");
        savedFormation = app.getInteger("FormationVersion") == 1 && savedFacing.offsetY == 0
            && savedFacing != ForgeDirection.UNKNOWN
            && savedTier >= 0
            && savedTier < 4
            && savedSectors > 0
            && savedSectors <= 63
                ? new SavedFormation(
                    savedFacing,
                    app.getInteger("Dimension"),
                    app.getInteger("X"),
                    app.getInteger("Y"),
                    app.getInteger("Z"),
                    savedTier,
                    app.getBoolean("Service"),
                    savedSectors,
                    app.getLong("VerifiedModules"))
                : null;
        facing = ForgeDirection.UNKNOWN;
        towerTier = -1;
        lastFormation = savedFormation;
        reviewSectors = (app.getInteger("ReviewSectors") | (savedFormation == null ? 0 : savedFormation.sectors)) & 63;
        reviewModules = (app.getLong("ReviewModules") | (savedFormation == null ? 0 : savedFormation.modules))
            & ((1L << 36) - 1);
        portIdentity = savedFormation;
        knownPorts.clear();
        int[] ports = app.getIntArray("Ports");
        portIndexReady = savedFormation != null && app.getInteger("PortIndexVersion") == 1 && ports.length <= 200006;
        if (portIndexReady) for (int port : ports) knownPorts.add(port);
        portIndexCursor = 0;
        portIndexUnknown = false;
        restoredTower = false;
        nextRestoreCheck = 0;
        formedSectors = scanCursor = 0;
        tower.reset();
        for (CriticalStructureMonitor sector : sectors) sector.reset();
        for (int i = 0; i < modules.length; i++) {
            String key = app.getString(Integer.toString(i));
            modules[i] = key.matches("module-" + i % 6 + "-[a-z]+-[123]") ? new ModuleCheck(key) : null;
            if (modules[i] != null) {
                modules[i].damaged = app.getBoolean("d" + i);
                modules[i].virtual = app.getInteger("ModuleFormat") == 1 && app.getBoolean("v" + i);
                modules[i].migrationComplete = app.getBoolean("m" + i);
                modules[i].receiptLoaded = modules[i].virtual && modules[i].migrationComplete;
            }
        }
        moduleRevision = Math.max(0, app.getLong("ModuleRevision"));
        reviewModules = 0;
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
        { CenterModule.SPEED, CenterModule.SEPARATOR, CenterModule.SMELTING } };

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
            if (player.worldObj != frame.world || player.isDead
                || te.getFrontFacing() != frame.facing
                || !player.capabilities.isCreativeMode && !player.canCommandSenderUseCommand(2, "gtsrdebug"))
                return finish("gtsr.wand.debug.denied");

            int slot = piece - 2;
            CenterModule module = slot < 0 ? null
                : grades[slot] > 0 ? CenterModule.values()[types[slot]]
                    : slot % 6 < 3 ? DEBUG_MODULES[slot / 6][slot % 6] : CenterModule.LOGISTICS;
            String target = piece == 0 ? (service ? SERVICE_TOWER : "tower-") + "3"
                : piece == 1 ? "outer-full" : moduleKey(slot, module, 3);
            if (slot >= 0) {
                if (claims.hasLegacy(frame, slot)) return finish("gtsr.wand.debug.build_blocked");
                modules[slot] = new ModuleCheck(target);
                modules[slot].virtual = true;
                types[slot] = module.ordinal();
                grades[slot] = 3;
                moduleRevision++;
                te.markDirty();
            } else {
                if (resource == null) resource = target;
                Shape layout = shape(resource);
                for (int i = 0; cursor < layout.count() && i < 2048 && System.nanoTime() < deadline; i++, cursor++) {
                    int x = frame.x(layout.x[cursor], layout.z[cursor] + 19);
                    int z = frame.z(layout.x[cursor], layout.z[cursor] + 19);
                    int y = frame.y + layout.y[cursor] - 2;
                    if (!frame.loaded(x, z)) return finish("gtsr.wand.debug.build_unloaded");
                    Block block = layout.block(cursor);
                    int meta = layout.metadata(cursor);
                    if (block == null || y < 0 || y >= frame.world.getHeight())
                        return finish("gtsr.wand.debug.build_blocked");
                    if (frame.world.getBlock(x, y, z) == block && frame.world.getBlockMetadata(x, y, z) == meta
                        || substitutedPort(layout, cursor, frame, x, y, z)) continue;
                    if (!frame.world.canMineBlock(player, x, y, z) || frame.world.getTileEntity(x, y, z) != null
                        || CriticalBlockProtection.protects(frame.world, x, y, z)
                        || !frame.world.setBlock(x, y, z, block, meta, 2))
                        return finish("gtsr.wand.debug.build_blocked");
                }
                if (cursor < layout.count()) return null;
                cursor = 0;
                resource = null;
            }
            return ++piece < grades.length + 2 ? null
                : finish(placeMissingPorts() ? "gtsr.wand.debug.build_done" : "gtsr.wand.debug.build_ports");
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
                for (int i = 0; i < types.length; i++) if (types[i] == hatch.getMetaTileEntity()
                    .getClass()) present[i] = true;
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
                    placeExamplePort(frame, x, depth, examples[i], 3);
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

    /** Immutable placement; y/z include the historical controller offsets exactly once. */
    public static final class ModulePlacement {

        private final int x, y, z, turns;

        private ModulePlacement(int x, int y, int z, int turns) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.turns = turns;
        }

        public int offsetX() {
            return x;
        }

        public int offsetY() {
            return y;
        }

        public int offsetZ() {
            return z;
        }

        public int quarterTurns() {
            return turns;
        }
    }

    public static ModulePlacement modulePlacement(int slot) {
        if (slot < 0 || slot >= SLOTS.length) throw new IllegalArgumentException("slot");
        int[] at = SLOTS[slot];
        return new ModulePlacement(at[0], at[1] - 66, at[2] + 19, at[3]);
    }

    /** Read-only shared template view. Arrays stay private to the structure loader. */
    public static final class ModuleTemplate {

        private final Shape shape;

        private ModuleTemplate(Shape shape) {
            this.shape = shape;
        }

        public int count() {
            return shape.count();
        }

        public int x(int i) {
            return shape.x[i];
        }

        public int y(int i) {
            return shape.y[i];
        }

        public int z(int i) {
            return shape.z[i];
        }

        public Block block(int i) {
            return shape.block(i);
        }

        public int meta(int i) {
            return shape.metadata(i);
        }
    }

    public static ModuleTemplate moduleTemplate(int slot, int type, int grade) {
        if (slot < 0 || slot >= 36 || type < 0 || type >= CenterModule.values().length || grade < 1 || grade > 3)
            return null;
        Shape shape = optionalShape(moduleKey(slot, CenterModule.values()[type], grade));
        return shape == null ? null : new ModuleTemplate(shape);
    }

    /** Only real tower/wing chunks participate in block-change notifications. */
    long[] realStructureChunks(Frame frame) {
        java.util.Set<Long> chunks = new HashSet<>();
        List<Shape> pieces = new ArrayList<>();
        for (int tier = 0; tier <= 3; tier++) {
            pieces.add(towerShape(tier, true));
            pieces.add(towerShape(tier, false));
        }
        for (int i = 0; i < 6; i++) pieces.add(shape("sector-" + i));
        for (Shape piece : pieces) for (int column : piece.columns()) {
            int x = (short) (column >> 16), z = (short) column;
            chunks.add(((long) (frame.x(x, z + 19) >> 4) << 32) | ((frame.z(x, z + 19) >> 4) & 0xFFFFFFFFL));
        }
        return chunks.stream()
            .mapToLong(Long::longValue)
            .toArray();
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
            if (shape.block(i) == null || wy < 0 || wy >= frame.world.getHeight() || !frame.loaded(wx, wz))
                return false;
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
        // Buffer decoded bytes as well as compressed input. The primitive reads below are only one or two bytes wide.
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(resource, 8192)))) {
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
        boolean awaitingPorts;

        Frame(IGregTechTileEntity te) {
            this(te.getWorld(), te.getXCoord(), te.getYCoord(), te.getZCoord(), te.getFrontFacing());
            awaitingPorts = recovering(te);
        }

        Frame(World world, int x, int y, int z, ForgeDirection facing) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = CriticalStructureAlignment.horizontal(facing);
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
        private int[] columns;

        Shape(boolean tower, boolean service, int shell, Block[] blocks, byte[] metas, short[] x, byte[] y, short[] z,
            byte[] ids) {
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

        int[] columns() {
            if (columns != null) return columns;
            int depth = maxZ - minZ + 1;
            BitSet used = new BitSet((maxX - minX + 1) * depth);
            for (int i = 0; i < x.length; i++) used.set((x[i] - minX) * depth + z[i] - minZ);
            columns = used.stream()
                .map(offset -> (minX + offset / depth) << 16 | (minZ + offset % depth) & 0xFFFF)
                .toArray();
            return columns;
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
        boolean damaged, virtual, migrationComplete, receiptLoaded;

        ModuleCheck(String resource) {
            this.resource = resource;
        }
    }
}
