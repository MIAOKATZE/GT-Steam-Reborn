package com.miaokatze.gtsr.common.dimension.testlab;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.machine.MTESpacetimeCalibration;
import com.miaokatze.gtsr.common.machine.base.IHubController;
import com.miaokatze.gtsr.common.machine.base.MTEHubArrayBase;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicTank;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Cross-process layout probe. The original task ID selects the saved station, but its start/build path is never run.
 * Loaded stock evidence is captured before canonical scans, clearing outputs, supplying fluids or reseeding inputs.
 */
final class TestLabExistingLayoutTask implements TestLabTask {

    private final TestLabTask delegate;
    private final List<IGregTechTileEntity> tiles = new ArrayList<>();
    private final List<IGregTechTileEntity> controllers = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private List<int[]> positions;
    private String beforeFingerprint;
    private String beforeMachineBodyFingerprint;
    private final Map<String, String> beforeBlocks = new LinkedHashMap<>();
    private final Map<String, MTESpacetimeCalibration> operationalCoreCells = new LinkedHashMap<>();
    private final Map<String, Boolean> operationalCellsInitiallyValid = new LinkedHashMap<>();
    private TestLabReport.Result outcome;
    private boolean resumed;
    private boolean resumeAttempted;
    private boolean sawNaturalFormed;
    private boolean sawNaturalActive;

    TestLabExistingLayoutTask(TestLabTask delegate) {
        this.delegate = delegate;
    }

    static boolean supports(TestLabTask task) {
        if (task instanceof TestLabClusterTask) return true;
        if (task instanceof TestLabDrillingTask) return "singularity_drilling_node".equals(task.id());
        if (task instanceof TestLabHubTask) return ((TestLabHubTask) task).supportsExisting();
        if (!(task instanceof TestLabMachineTask)) return false;
        String name = task.id()
            .replaceFirst("_tier[0-9]+$", "")
            .toUpperCase(java.util.Locale.ROOT);
        MetaTileEntityID id = MetaTileEntityID.valueOf(name);
        return GregTechAPI.METATILEENTITIES[id.ID] instanceof MTEMultiBlockBase;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public String displayName() {
        return "Existing saved layout: " + delegate.displayName();
    }

    @Override
    public int timeoutTicks() {
        return delegate.timeoutTicks() + 200;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) throws Exception {
        positions = TestLabFixtures.existingPositions(world, x, z);
        Map<String, Object> baseline = TestLabState.machineBaseline(DimensionManager.getWorld(0), id());
        evidence.put("legacyFunctionBaseline", baseline);
        evidence.put("noRebuild", true);
        evidence.put("directWorldBlockWrites", 0);
        evidence.put("directMachineFormationWrites", 0);
        evidence.put("manualMachineTickCalls", 0);
        evidence.put("recordedPositions", positions.size());
        evidence.put(
            "chunkLoadVerification",
            "Runtime temporary Forge tickets; requireChunk validates real provider chunk identity before task start");
        for (int[] p : positions) {
            TileEntity tile = world.getTileEntity(p[0], p[1], p[2]);
            if (!(tile instanceof IGregTechTileEntity)) continue;
            IGregTechTileEntity gt = (IGregTechTileEntity) tile;
            tiles.add(gt);
            if (gt.getMetaTileEntity() instanceof MTEMultiBlockBase) controllers.add(gt);
        }
        evidence.put("loadedBeforeAnyFixtureMutation", snapshots(tiles));
        for (IGregTechTileEntity tile : controllers) if (tile.getMetaTileEntity() instanceof MTESpacetimeCalibration) {
            MTESpacetimeCalibration calibration = (MTESpacetimeCalibration) tile.getMetaTileEntity();
            Vec3 centre = calibration.localToWorld(23.5, 24.5, 4.5);
            int cx = (int) Math.floor(centre.xCoord), cy = (int) Math.floor(centre.yCoord),
                cz = (int) Math.floor(centre.zCoord);
            String key = cx + "," + cy + "," + cz;
            operationalCoreCells.put(key, calibration);
            operationalCellsInitiallyValid.put(key, validOperationalCore(world, calibration, cx, cy, cz));
        }
        for (int[] p : positions) beforeBlocks.put(position(p), blockIdentity(world, p));
        beforeFingerprint = fingerprint(world, false);
        beforeMachineBodyFingerprint = fingerprint(world, true);
        evidence.put("blockIdentitySha256Before", beforeFingerprint);
        evidence.put("machineBodyAndPortsSha256Before", beforeMachineBodyFingerprint);
        evidence.put("operationalCorePositions", new ArrayList<>(operationalCoreCells.keySet()));
        evidence.put(
            "operationalBlockPolicy",
            "Only the exact calibration centre may transition air <-> its controller-owned spacetime singularity; all other journal positions remain immutable. Saved drilling node has bedrock immediately below and uses GT deposit data without placing/retracting pipes; mining/pyrolysis resource fixtures modify GT data, not world blocks.");
        evidence.put(
            "loadedStockInterpretation",
            "Compare this pre-mutation snapshot with the stopped old-JVM offline fingerprint; later seeded stock does not prove conservation");
        if (!Boolean.TRUE.equals(baseline.get("found")) || controllers.isEmpty()) {
            outcome = new TestLabReport.Result(
                TestLabReport.Status.BLOCKED,
                "No completed old-layout function baseline/controller at original station; compatibility not established");
            return;
        }
        if (((Number) baseline.get("originX")).intValue() != x || ((Number) baseline.get("originZ")).intValue() != z) {
            outcome = new TestLabReport.Result(
                TestLabReport.Status.BLOCKED,
                "Old report origin does not match current catalog station; refusing implicit relocation");
            return;
        }
        List<Map<String, Object>> checks = canonicalChecks();
        evidence.put("canonicalChecksAfterLoad", checks);
        for (Map<String, Object> check : checks) if (!Boolean.TRUE.equals(check.get("accepted"))) {
            outcome = new TestLabReport.Result(
                TestLabReport.Status.FAIL,
                "Existing native checkStructure rejected under current rules; review current minimum casing rule; no rebuild or migration failure inferred");
            return;
        }
        if (!"PASS".equals(baseline.get("status"))) {
            outcome = new TestLabReport.Result(
                TestLabReport.Status.BLOCKED,
                "Saved layout forms under current rules, but old function baseline was " + baseline.get("status")
                    + "; old production compatibility is not established");
            return;
        }
        IGregTechTileEntity host = mainController();
        resumeAttempted = true;
        try {
            if (delegate instanceof TestLabMachineTask)
                ((TestLabMachineTask) delegate).resumeExisting(world, host, tiles);
            else if (delegate instanceof TestLabHubTask) ((TestLabHubTask) delegate).resumeExisting(world, host, tiles);
            else if (delegate instanceof TestLabClusterTask) {
                for (IGregTechTileEntity tile : tiles) emptyOutput(tile);
                ((TestLabClusterTask) delegate).resumeExisting(world, host, tiles);
            } else if (delegate instanceof TestLabDrillingTask) {
                ((TestLabDrillingTask) delegate).resumeExisting(world, host, tiles);
            } else throw new IllegalStateException("No existing-layout resume implementation: " + id());
            resumed = true;
        } catch (Exception | LinkageError failure) {
            outcome = new TestLabReport.Result(
                TestLabReport.Status.FAIL,
                "Existing-layout resume failed: " + failure.getClass()
                    .getName() + ": " + failure.getMessage());
        }
        evidence.put("fixtureStockAfterResumeBeforeFirstNaturalTick", snapshots(tiles));
        evidence.put(
            "fixtureStockChanges",
            stockDelta(castSnapshots(evidence.get("loadedBeforeAnyFixtureMutation")), snapshots(tiles)));
        evidence.put("resumeSupply", TestLabSupply.describe(world));
        if (delegate instanceof TestLabDrillingTask)
            evidence.put("drillingFixtureActions", ((TestLabDrillingTask) delegate).existingFixtureEvidence());
        if (delegate instanceof TestLabMachineTask) {
            Map<String, Object> environment = ((TestLabMachineTask) delegate).solarEnvironmentEvidence();
            if (!environment.isEmpty()) evidence.put("solarEnvironmentFixtureBeforeNaturalTicks", environment);
        }
        recordThermalFixtureEvidence();
    }

    private IGregTechTileEntity mainController() {
        for (IGregTechTileEntity tile : controllers) {
            if (delegate instanceof TestLabClusterTask
                && tile.getMetaTileEntity() instanceof MTESteamMineralLogisticsCluster) return tile;
            if (delegate instanceof TestLabHubTask && tile.getMetaTileEntity() instanceof MTEHubArrayBase) return tile;
        }
        if (controllers.size() == 1) return controllers.get(0);
        throw new IllegalStateException("Ambiguous existing main controller at " + id());
    }

    private List<Map<String, Object>> canonicalChecks() {
        List<Map<String, Object>> checks = new ArrayList<>();
        // Saved cluster units must scan their ports before the controller scans/binds the real topology.
        for (int pass = 0; pass < 2; pass++) for (IGregTechTileEntity tile : controllers) {
            boolean cluster = tile.getMetaTileEntity() instanceof MTESteamMineralLogisticsCluster;
            if (cluster != (pass == 1)) continue;
            MTEMultiBlockBase machine = (MTEMultiBlockBase) tile.getMetaTileEntity();
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("position", position(tile));
            check.put("metaTileId", tile.getMetaTileID());
            check.put(
                "class",
                machine.getClass()
                    .getName());
            check.put("accepted", TestLabStructure.checkStructure(machine, tile));
            checks.add(check);
        }
        return checks;
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) throws Exception {
        boolean formed = !controllers.isEmpty();
        for (IGregTechTileEntity tile : controllers) {
            formed &= ((MTEMultiBlockBase) tile.getMetaTileEntity()).mMachine;
            sawNaturalActive |= tile.isActive();
        }
        if (elapsedTicks > 1) sawNaturalFormed |= formed;
        if (resumed && outcome == null) {
            try {
                outcome = delegate.tick(world, elapsedTicks);
            } catch (Exception | LinkageError failure) {
                outcome = new TestLabReport.Result(
                    TestLabReport.Status.FAIL,
                    "Existing-layout natural tick observation failed: " + failure);
            }
        }
        if (outcome == null && elapsedTicks > delegate.timeoutTicks()) outcome = new TestLabReport.Result(
            TestLabReport.Status.FAIL,
            "Existing-layout natural production timed out; " + debug(world));
        if (outcome == null || resumed && elapsedTicks < 40) return null;
        if (resumed && outcome.status == TestLabReport.Status.PASS && !formed) outcome = new TestLabReport.Result(
            TestLabReport.Status.FAIL,
            "Existing controller lost natural formation after resume; " + outcome.detail);
        evidence.put("naturalTicksObserved", elapsedTicks);
        evidence.put("naturalFormationObserved", sawNaturalFormed);
        evidence.put("naturalActiveObserved", sawNaturalActive);
        evidence.put("resumedFunctionVerified", resumed && outcome.status == TestLabReport.Status.PASS);
        evidence.put("stockAfterNaturalTicks", snapshots(tiles));
        recordThermalFixtureEvidence();
        Map<String, Object> supplied = new LinkedHashMap<>();
        for (IGregTechTileEntity tile : tiles) {
            long amount = TestLabSupply.replenished(tile);
            if (amount > 0) supplied.put(position(tile), amount);
        }
        evidence.put("actualInputFluidReplenishedAfterInitialFill", supplied);
        String after = fingerprint(world, false);
        String machineBodyAfter = fingerprint(world, true);
        boolean bodyUnchanged = beforeMachineBodyFingerprint.equals(machineBodyAfter);
        boolean operationalChangesValid = true;
        List<Map<String, Object>> changes = new ArrayList<>();
        for (int[] p : positions) {
            String key = position(p);
            String current = blockIdentity(world, p);
            if (beforeBlocks.get(key)
                .equals(current)) continue;
            MTESpacetimeCalibration calibration = operationalCoreCells.get(key);
            boolean allowed = calibration != null && Boolean.TRUE.equals(operationalCellsInitiallyValid.get(key))
                && validOperationalCore(world, calibration, p[0], p[1], p[2])
                && resumed
                && outcome.status == TestLabReport.Status.PASS
                && calibration.isPortalActive();
            operationalChangesValid &= allowed;
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("position", key);
            change.put("before", beforeBlocks.get(key));
            change.put("after", current);
            change.put(
                "classification",
                calibration == null ? "MACHINE_BODY_OR_PORTS_MUST_NOT_CHANGE" : "CALIBRATION_OWNED_OPERATIONAL_CORE");
            change.put("allowedNaturalTransition", allowed);
            change.put("productionEvidence", calibration == null ? "none" : outcome.detail);
            changes.add(change);
        }
        evidence.put("blockIdentitySha256After", after);
        evidence.put("originalBlockIdentityUnchanged", beforeFingerprint.equals(after));
        evidence.put("machineBodyAndPortsSha256After", machineBodyAfter);
        evidence.put("originalMachineBodyAndPortsUnchanged", bodyUnchanged);
        evidence.put("allOperationalTransitionsAllowed", operationalChangesValid);
        evidence.put("journalBlockChanges", changes);
        evidence.put("resumedTaskDetails", outcome.details);
        TestLabReport.Status status = bodyUnchanged && operationalChangesValid ? outcome.status
            : TestLabReport.Status.FAIL;
        return new TestLabReport.Result(
            status,
            "existing_layout=true no_rebuild=true originalBlocksUnchanged=" + beforeFingerprint.equals(after)
                + " originalMachineBodyAndPortsUnchanged="
                + bodyUnchanged
                + " operationalTransitionsAllowed="
                + operationalChangesValid
                + " naturalTicks="
                + elapsedTicks
                + " resumed="
                + resumed
                + "; "
                + outcome.detail,
            evidence);
    }

    @Override
    public void stop(WorldServer world) {
        if (resumeAttempted) delegate.stop(world);
    }

    private void recordThermalFixtureEvidence() {
        Map<String, Object> thermal = delegate instanceof TestLabMachineTask
            ? ((TestLabMachineTask) delegate).thermalFixtureEvidence()
            : delegate instanceof TestLabClusterTask ? ((TestLabClusterTask) delegate).thermalFixtureEvidence()
                : java.util.Collections.emptyMap();
        evidence.put("thermalFixture", thermal);
        evidence.put("thermalFixtureApplied", thermal.getOrDefault("thermalFixtureApplied", false));
        evidence.put("thermalNbtFixture", thermal.getOrDefault("thermalNbtFixture", false));
        evidence.put("directThermalWrites", thermal.getOrDefault("directThermalWrites", 0));
        if (Boolean.TRUE.equals(thermal.get("thermalFixtureApplied"))) evidence.put(
            "preheatValidationScope",
            "FUNCTIONAL_PRODUCTION_ONLY; natural preheat and thermal save compatibility are not established");
    }

    @Override
    public String debug(WorldServer world) {
        return "existing_layout=true no_rebuild=true resumed=" + resumed
            + " controllers="
            + controllers.size()
            + " naturalFormed="
            + sawNaturalFormed
            + " naturalActive="
            + sawNaturalActive
            + (resumed ? " " + delegate.debug(world) : "");
    }

    /** Actual inventory extraction/tank drain, used only after the original loaded stock snapshot. */
    static void emptyOutput(IGregTechTileEntity tile) {
        if (tile.getMetaTileEntity() instanceof MTEHatchOutputBus) {
            IInventory inventory = (IInventory) tile;
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack != null) inventory.decrStackSize(slot, stack.stackSize);
            }
        }
        if (tile.getMetaTileEntity() instanceof MTEHatchOutput)
            ((MTEBasicTank) tile.getMetaTileEntity()).drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE, true);
    }

    private String fingerprint(WorldServer world, boolean machineBodyOnly) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int[] p : positions) {
            if (machineBodyOnly && operationalCoreCells.containsKey(position(p))) continue;
            String line = position(p) + "=" + blockIdentity(world, p);
            digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private static String position(int[] p) {
        return p[0] + "," + p[1] + "," + p[2];
    }

    private static String blockIdentity(WorldServer world, int[] p) {
        String result = Block.blockRegistry.getNameForObject(world.getBlock(p[0], p[1], p[2])) + ":"
            + world.getBlockMetadata(p[0], p[1], p[2]);
        TileEntity tile = world.getTileEntity(p[0], p[1], p[2]);
        if (tile instanceof IGregTechTileEntity) result += ":" + ((IGregTechTileEntity) tile).getMetaTileID()
            + ":"
            + ((IGregTechTileEntity) tile).getFrontFacing()
                .ordinal();
        return result;
    }

    private static boolean validOperationalCore(WorldServer world, MTESpacetimeCalibration calibration, int x, int y,
        int z) {
        return world.isAirBlock(x, y, z)
            || world.getBlock(x, y, z) == BlocksGTSR.spacetimeSingularity && calibration.ownsSingularity(x, y, z);
    }

    private static String position(IGregTechTileEntity tile) {
        return tile.getXCoord() + "," + tile.getYCoord() + "," + tile.getZCoord();
    }

    private static List<Map<String, Object>> snapshots(List<IGregTechTileEntity> tiles) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (IGregTechTileEntity tile : tiles) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("position", position(tile));
            entry.put("metaTileId", tile.getMetaTileID());
            entry.put(
                "facing",
                tile.getFrontFacing()
                    .ordinal());
            entry.put("ownerName", tile.getOwnerName());
            entry.put("ownerUuid", String.valueOf(tile.getOwnerUuid()));
            entry.put("allowed", tile.isAllowedToWork());
            entry.put("storedEU", tile.getStoredEU());
            NBTTagCompound nbt = new NBTTagCompound();
            ((TileEntity) tile).writeToNBT(nbt);
            entry.put("completeTileNbtSnbt", nbt.toString());
            entry.put("completeTileNbt", nbtValue(nbt));
            Map<String, Long> stock = new LinkedHashMap<>();
            if (tile instanceof IInventory) {
                IInventory inventory = (IInventory) tile;
                for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                    ItemStack item = inventory.getStackInSlot(slot);
                    if (item == null) continue;
                    String key = "item:slot=" + slot
                        + ":id="
                        + Item.getIdFromItem(item.getItem())
                        + ":damage="
                        + item.getItemDamage()
                        + ":tag="
                        + item.getTagCompound();
                    stock.put(key, (long) item.stackSize);
                }
            }
            if (tile.getMetaTileEntity() instanceof MTEBasicTank) {
                FluidStack fluid = ((MTEBasicTank) tile.getMetaTileEntity()).getFluid();
                if (fluid != null) stock.put(
                    "fluid:" + fluid.getFluid()
                        .getName(),
                    (long) fluid.amount);
            }
            if (tile.getMetaTileEntity() instanceof IHubController)
                stock.put("hubStoredFluid", ((IHubController) tile.getMetaTileEntity()).getStoredFluidAmount());
            entry.put("stock", stock);
            result.add(entry);
        }
        return result;
    }

    /** Stable JSON representation permits offline old-Anvil vs newly-loaded pre-mutation stock comparison. */
    private static Object nbtValue(NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            Map<String, Object> result = new LinkedHashMap<>();
            NBTTagCompound compound = (NBTTagCompound) nbt;
            for (String key : compound.func_150296_c()) result.put(key, nbtValue(compound.getTag(key)));
            return result;
        }
        if (nbt instanceof NBTTagList) {
            List<Object> result = new ArrayList<>();
            // 1.7.10 exposes removal but no generic list getter. Read a copy, preserving original NBT.
            NBTTagList list = (NBTTagList) nbt.copy();
            for (int index = list.tagCount() - 1; index >= 0; index--) result.add(nbtValue(list.removeTag(index)));
            java.util.Collections.reverse(result);
            return result;
        }
        if (nbt instanceof NBTTagString) return ((NBTTagString) nbt).func_150285_a_();
        if (nbt instanceof NBTTagByteArray) {
            List<Integer> result = new ArrayList<>();
            for (byte value : ((NBTTagByteArray) nbt).func_150292_c()) result.add(value & 255);
            return result;
        }
        if (nbt instanceof NBTTagIntArray) return ((NBTTagIntArray) nbt).func_150302_c();
        String number = nbt.toString()
            .replaceFirst("[bBsSlLfFdD]$", "");
        if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0)
            return Double.valueOf(number);
        return Long.valueOf(number);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castSnapshots(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stockDelta(List<Map<String, Object>> before, List<Map<String, Object>> after) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < before.size(); index++) {
            Map<String, Long> oldStock = (Map<String, Long>) before.get(index)
                .get("stock");
            Map<String, Long> newStock = (Map<String, Long>) after.get(index)
                .get("stock");
            Map<String, Long> changes = new LinkedHashMap<>();
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(oldStock.keySet());
            keys.addAll(newStock.keySet());
            for (String key : keys) {
                long delta = newStock.getOrDefault(key, 0L) - oldStock.getOrDefault(key, 0L);
                if (delta != 0) changes.put(key, delta);
            }
            if (!changes.isEmpty()) result.put(
                String.valueOf(
                    before.get(index)
                        .get("position")),
                changes);
        }
        return result;
    }
}
