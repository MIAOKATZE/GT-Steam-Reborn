package com.miaokatze.gtsr.common.machine;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressEntry;
import com.miaokatze.gtsr.common.critical.CriticalControllerTextures;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalStructures;
import com.miaokatze.gtsr.common.critical.CriticalTooltips;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRExtendedPowerMultiBlockBase;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;

/** One GT host owns the construction journal and runs the critical engine. */
public final class MTECriticalMachine extends MTEGTSRExtendedPowerMultiBlockBase<MTECriticalMachine> implements
    gregtech.api.interfaces.IChunkLoader, com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable {

    private final CriticalMachineKind presetKind;
    private final CriticalMachineState state = new CriticalMachineState();
    private long energySpentThisTick;
    private long energyEmittedThisTick;
    private final java.util.Map<MTEHatchEnergy, Long> hatchEnergySpent = new java.util.IdentityHashMap<>();
    private final java.util.Map<Long, TileEntity> portSnapshot = new java.util.LinkedHashMap<>();
    private int previousJournalState = -1;
    private boolean previousCanonicalValidation;
    private boolean loomStructureValid;
    private long loomValidationTick = Long.MIN_VALUE, meSnapshotTick = Long.MIN_VALUE;
    private ItemStack[] meItemsSnapshot = new ItemStack[0];
    private FluidStack[] meFluidsSnapshot = new FluidStack[0];
    private World previewWorld;
    private NBTTagCompound previewSelection;

    public MTECriticalMachine(int id, String name, String localizedName, CriticalMachineKind kind) {
        super(id, name, localizedName);
        presetKind = kind;
        state.presetKind(kind);
        registerCriticalProgress();
    }

    public MTECriticalMachine(String name, CriticalMachineKind kind) {
        super(name);
        presetKind = kind;
        state.presetKind(kind);
        registerCriticalProgress();
    }

    private void registerCriticalProgress() {
        registerEntry(
            GTSRProgressEntry
                .of(
                    "criticalConstruction",
                    "gtsr.critical.construction",
                    "%.1f%%",
                    EnumChatFormatting.YELLOW,
                    () -> constructionPercent())
                .showZero());
        registerEntry(
            GTSRProgressEntry
                .of("criticalBatch", "gtsr.critical.batch", "%.1f%%", EnumChatFormatting.GREEN, () -> batchPercent())
                .showZero());
        registerEntry(
            GTSRProgressEntry
                .of(
                    "criticalStoredEnergy",
                    "gtsr.spacetime.gui.energy",
                    "%,.0f EU",
                    EnumChatFormatting.AQUA,
                    () -> getCriticalState().getBufferedEnergy())
                .showZero());
    }

    private double constructionPercent() {
        CriticalMachineState current = getCriticalState();
        if (current.getState() == 2) return 100D;
        int total = current.getJobTotal();
        return total <= 0 ? 0D : Math.min(100D, 100D * current.getConstructionPlaced() / total);
    }

    private double batchPercent() {
        CriticalMachineState current = getCriticalState();
        return current.getBatchDuration() <= 0 ? 0D
            : Math.min(100D, 100D * current.getBatchProgress() / current.getBatchDuration());
    }

    @Override
    public String[] getInfoData() {
        java.util.List<String> info = new java.util.ArrayList<>(java.util.Arrays.asList(super.getInfoData()));
        CriticalMachineState current = getCriticalState();
        info.add(
            StatCollector.translateToLocal("gtsr.critical.status." + current.getState()) + " / "
                + StatCollector.translateToLocal("gtsr.critical.reason." + current.getReasonCode()));
        if (current.getState() == 1 || current.getState() == 3 || current.getState() == 4) info.add(
            StatCollector.translateToLocal("gtsr.critical.construction") + " "
                + current.getStage()
                + ": "
                + current.getConstructionPlaced()
                + "/"
                + current.getJobTotal());
        if (current.getBatchDuration() > 0) {
            info.add(
                StatCollector.translateToLocal("gtsr.critical.batch") + ": "
                    + current.getBatchProgress()
                    + "/"
                    + current.getBatchDuration()
                    + " t");
            info.add(
                StatCollector.translateToLocalFormatted(
                    "GT5U.multiblock.scanner.usage",
                    com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil
                        .formatNumber(current.isWorking() ? current.getBatchEuPerTick() : 0L)));
        }
        info.add(
            StatCollector.translateToLocal("gtsr.spacetime.gui.energy") + " "
                + com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil
                    .formatNumber(current.getBufferedEnergy())
                + " / "
                + com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber(current.energyCapacity())
                + " EU");
        return info.toArray(new String[0]);
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        CriticalMachineState current = getCriticalState();
        tag.setInteger("progress", current.getBatchProgress());
        tag.setInteger("maxProgress", current.getBatchDuration());
        tag.setLong("energyUsage", -lEUt);
        tag.setFloat("efficiency", mEfficiency / 100F);
        tag.setInteger("criticalState", current.getState());
        tag.setInteger("criticalReason", current.getReasonCode());
        tag.setInteger("criticalStage", current.getStage());
        tag.setInteger("criticalJobProgress", current.getConstructionPlaced());
        tag.setInteger("criticalJobTotal", current.getJobTotal());
        tag.setLong("criticalBufferedEnergy", current.getBufferedEnergy());
        tag.setLong("criticalEnergyCapacity", current.energyCapacity());
    }

    @Override
    public void getWailaBody(ItemStack stack, java.util.List<String> tip,
        mcp.mobius.waila.api.IWailaDataAccessor accessor, mcp.mobius.waila.api.IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        int journal = tag.getInteger("criticalState");
        tip.add(
            StatCollector.translateToLocal("gtsr.critical.status." + journal) + " / "
                + StatCollector.translateToLocal("gtsr.critical.reason." + tag.getInteger("criticalReason")));
        if (journal == 1 || journal == 3 || journal == 4) tip.add(
            StatCollector.translateToLocal("gtsr.critical.construction") + " "
                + tag.getInteger("criticalStage")
                + ": "
                + tag.getInteger("criticalJobProgress")
                + "/"
                + tag.getInteger("criticalJobTotal"));
        tip.add(
            StatCollector.translateToLocal("gtsr.spacetime.gui.energy") + " "
                + com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil
                    .formatNumber(tag.getLong("criticalBufferedEnergy"))
                + " / "
                + com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil
                    .formatNumber(tag.getLong("criticalEnergyCapacity"))
                + " EU");
    }

    public boolean isKindSelector() {
        return presetKind == null;
    }

    public CriticalMachineKind getPresetKind() {
        return presetKind;
    }

    public CriticalMachineState getCriticalState() {
        if (getBaseMetaTileEntity() instanceof TileEntity tile) state.bindHost(tile);
        return state;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTECriticalMachine(mName, presetKind);
    }

    @Override
    protected com.gtnewhorizon.structurelib.alignment.IAlignmentLimits getInitialAlignmentLimits() {
        return (direction, rotation, flip) -> direction == ForgeDirection.NORTH
            && rotation == com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL
            && flip == com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE;
    }

    @Override
    public IStructureDefinition<MTECriticalMachine> getStructureDefinition() {
        return CriticalStructures.definition(this);
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        CriticalStructures.construct(this, stack, hintsOnly);
    }

    /** BlockRenderer repeats survival callbacks until its DummyWorld receives no writes. */
    public void constructPreview(ItemStack trigger) {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || !CriticalStructures.isPreviewWorld(tile.getWorld())) return;
        CriticalMachineState current = getCriticalState();
        NBTTagCompound selection = current.getConfiguration()
            .write();
        selection.setInteger("previewTriggerCount", trigger == null ? 0 : trigger.stackSize);
        selection.setInteger("previewControllerZ", current.getControllerZ());
        World world = tile.getWorld();
        if (world == previewWorld && selection.equals(previewSelection)) return;
        CriticalStructures.construct(this, trigger, false);
        previewWorld = world;
        previewSelection = selection;
    }

    @Override
    public int survivalConstruct(ItemStack stack, int budget,
        com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment environment) {
        return CriticalStructures.survivalConstruct(this, stack, budget, environment);
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        forceLoomStructureCheck();
        if (!getCriticalState().isFabricationHostValid() || state.getState() == 2 && !state.isStructureValidated())
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
    }

    private void discoverHatches(boolean force) {
        CriticalMachineState current = getCriticalState();
        if (current.getWorldObj() == null) return;
        java.util.Map<Long, TileEntity> next = new java.util.LinkedHashMap<>();
        for (CriticalGeometry.Voxel port : current.getHatchPorts()) {
            int x = current.getOriginX() + port.x, y = current.getOriginY() + port.y, z = current.getOriginZ() + port.z;
            TileEntity tile = current.worldObj.getChunkProvider()
                .chunkExists(x >> 4, z >> 4) ? current.worldObj.getTileEntity(x, y, z) : null;
            next.put(port.positionKey(), tile);
        }
        boolean changed = next.size() != portSnapshot.size();
        for (java.util.Map.Entry<Long, TileEntity> entry : next.entrySet())
            if (!portSnapshot.containsKey(entry.getKey()) || portSnapshot.get(entry.getKey()) != entry.getValue())
                changed = true;
        if (!force && !changed) return;
        clearHatches();
        portSnapshot.clear();
        portSnapshot.putAll(next);
        for (TileEntity tile : next.values())
            if (CriticalMachineState.isStandardHatchHost(tile) && tile instanceof IGregTechTileEntity gt)
                addToMachineList(gt, CriticalControllerTextures.casingIndex());
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        if (!tile.isServerSide()) return;
        CriticalMachineState current = getCriticalState();
        discoverHatches(false);
        energySpentThisTick = 0;
        energyEmittedThisTick = 0;
        hatchEnergySpent.clear();
        if (isLoomStructureValid()) importHatchResources();
        chargeBattery();
        current.updateEntity();
        if (tick % 20 == 0)
            com.miaokatze.gtsr.common.critical.CriticalRenderAnchors.ensure(current, isLoomStructureValid());
        if (previousJournalState != current.getState()
            || previousCanonicalValidation != current.isStructureValidated()) {
            previousJournalState = current.getState();
            previousCanonicalValidation = current.isStructureValidated();
            checkStructure(true, tile);
        }
        exportHatchResources();
        syncNativeMachineDisplay(current);
        tile.setActive(current.isWorking());
    }

    /** The critical journal is the sole processing engine; GT still owns structure and host lifecycle. */
    @Override
    protected void runMachine(IGregTechTileEntity tile, long tick) {
        // Native recipe processing must never consume the display-only progress/EU fields below.
    }

    private void syncNativeMachineDisplay(CriticalMachineState current) {
        boolean construction = current.getState() == 1 || current.getState() == 4;
        // GT uses max progress > 0 as its lifecycle activity flag, not merely as an informational duration.
        // Paused journal progress remains visible in the handheld terminal without activating native leases/sounds.
        mProgresstime = !current.isWorking() ? 0
            : construction ? current.getConstructionElapsedTicks() : current.getBatchProgress();
        mMaxProgresstime = !current.isWorking() ? 0
            : construction ? current.getConstructionDurationTicks() : current.getBatchDuration();
        lEUt = energyEmittedThisTick - energySpentThisTick;
        mEUt = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, lEUt));
        mEfficiency = current.isWorking() || lEUt != 0L ? 10000 : 0;
        if (current.isWorking()) mLastWorkingTick = mTotalRunTime;
        // Structure validity and maintenance bits remain the result of GT's real checks.
        setCheckRecipeResult(
            current.getReasonCode() == 1
                ? gregtech.api.recipe.check.CheckRecipeResultRegistry.insufficientPower(current.getBatchEuPerTick())
                : current.isWorking() ? gregtech.api.recipe.check.CheckRecipeResultRegistry.SUCCESSFUL
                    : gregtech.api.recipe.check.CheckRecipeResultRegistry.NONE);
    }

    @Override
    protected boolean supportsCraftingMEBuffer() {
        return false;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public void stopMachine(gregtech.api.util.shutdown.ShutDownReason reason) {
        boolean allowed = getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isAllowedToWork();
        super.stopMachine(reason);
        // An intact loom must keep the journal's full target scan running after canonical damage.
        if (allowed && loomStructureValid
            && reason == gregtech.api.util.shutdown.ShutDownReasonRegistry.STRUCTURE_INCOMPLETE)
            getBaseMetaTileEntity().enableWorking();
    }

    public boolean isLoomStructureValid() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null) return false;
        long now = tile.getWorld()
            .getTotalWorldTime();
        if (loomValidationTick == Long.MIN_VALUE || now - loomValidationTick >= 20) return forceLoomStructureCheck();
        return loomStructureValid;
    }

    public boolean forceLoomStructureCheck() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null) return false;
        loomValidationTick = tile.getWorld()
            .getTotalWorldTime();
        clearHatches();
        loomStructureValid = com.miaokatze.gtsr.common.critical.CriticalLoomStructure.validate(this);
        discoverHatches(true);
        return loomStructureValid;
    }

    public boolean applyConstructionDebug(net.minecraft.entity.player.EntityPlayer player, int requirements, int speed,
        boolean ignoreEnergy) {
        return getCriticalState().applyConstructionDebug(player, requirements, speed, ignoreEnergy);
    }

    public boolean resetConstructionDebug(net.minecraft.entity.player.EntityPlayer player) {
        return getCriticalState().resetConstructionDebug(player);
    }

    public int getConstructionDebugRequirements() {
        return state.getConstructionDebugRequirements();
    }

    public int getConstructionDebugSpeed() {
        return state.getConstructionDebugSpeed();
    }

    public boolean getConstructionDebugIgnoreEnergy() {
        return state.getConstructionDebugIgnoreEnergy();
    }

    @Override
    public boolean checkRecipe(ItemStack stack) {
        return false;
    }

    public long availableCriticalEnergy() {
        long total = getBaseMetaTileEntity() == null ? 0 : getBaseMetaTileEntity().getStoredEU();
        for (MTEHatchEnergy hatch : mEnergyHatches) if (hatch.isValid()) {
            long stored = hatch.getBaseMetaTileEntity()
                .getStoredEU();
            total = total > Long.MAX_VALUE - stored ? Long.MAX_VALUE : total + stored;
        }
        return total;
    }

    private void refreshCriticalMESnapshot() {
        long now = getBaseMetaTileEntity().getWorld()
            .getTotalWorldTime();
        if (meSnapshotTick != Long.MIN_VALUE && now - meSnapshotTick < 20) return;
        meSnapshotTick = now;
        meItemsSnapshot = com.miaokatze.gtsr.common.critical.CriticalNativeMEFunding.snapshotItems(mInputBusses);
        meFluidsSnapshot = com.miaokatze.gtsr.common.critical.CriticalNativeMEFunding.snapshotFluids(mInputHatches);
    }

    public ItemStack[] criticalMEItemsSnapshot() {
        refreshCriticalMESnapshot();
        return meItemsSnapshot;
    }

    public FluidStack[] criticalMEFluidsSnapshot() {
        refreshCriticalMESnapshot();
        return meFluidsSnapshot;
    }

    public com.miaokatze.gtsr.common.critical.CriticalNativeMEFunding.Result fundMissingCriticalInputs(
        ItemStack[] items, FluidStack[] fluids) {
        com.miaokatze.gtsr.common.critical.CriticalNativeMEFunding.Result result = com.miaokatze.gtsr.common.critical.CriticalNativeMEFunding
            .fund(this, mInputBusses, mInputHatches, items, fluids);
        meSnapshotTick = Long.MIN_VALUE;
        return result;
    }

    public boolean canConsumeCriticalEnergy(long amount) {
        long budget = state.getTier()
            .recipeVoltage() * 16L;
        long buffered = getBaseMetaTileEntity() == null ? 0L : getBaseMetaTileEntity().getStoredEU();
        return amount >= 0 && amount <= budget - energySpentThisTick
            && amount <= gregtech.api.util.GTUtility.addSafe(buffered, availableHatchEnergyThisTick());
    }

    public boolean consumeCriticalEnergy(long amount) {
        long budget = state.getTier()
            .recipeVoltage() * 16L;
        long buffered = getBaseMetaTileEntity().getStoredEU();
        if (amount < 0 || amount > budget - energySpentThisTick
            || amount > gregtech.api.util.GTUtility.addSafe(buffered, availableHatchEnergyThisTick())) return false;
        long fromBuffer = Math.min(amount, buffered);
        if (amount > fromBuffer && !drainNativeHatchEnergy(amount - fromBuffer)) return false;
        if (fromBuffer > 0) getBaseMetaTileEntity().decreaseStoredEnergyUnits(fromBuffer, false);
        energySpentThisTick += amount;
        return true;
    }

    private long nativeHatchBudget(MTEHatchEnergy hatch) {
        long voltage = hatch.maxEUInput(), amperage = hatch.maxWorkingAmperesIn();
        long nativePower = voltage > 0 && amperage > Long.MAX_VALUE / voltage ? Long.MAX_VALUE : voltage * amperage;
        return Math.max(0L, nativePower - hatchEnergySpent.getOrDefault(hatch, 0L));
    }

    private long availableHatchEnergyThisTick() {
        long total = 0;
        for (MTEHatchEnergy hatch : mEnergyHatches) if (hatch.isValid()) {
            long legal = Math.min(
                hatch.getBaseMetaTileEntity()
                    .getStoredEU(),
                nativeHatchBudget(hatch));
            total = gregtech.api.util.GTUtility.addSafe(total, legal);
        }
        return total;
    }

    private boolean drainNativeHatchEnergy(long amount) {
        if (amount < 0 || availableHatchEnergyThisTick() < amount) return false;
        java.util.ArrayList<MTEHatchEnergy> original = new java.util.ArrayList<>(mEnergyHatches);
        long remaining = amount;
        try {
            // Use GT's standard energy helper, scoped to one hatch's remaining native power budget.
            // The shared helper otherwise drains the first full buffer beyond its rated per-tick amperage.
            for (MTEHatchEnergy hatch : original) {
                if (!hatch.isValid()) continue;
                long portion = Math.min(
                    remaining,
                    Math.min(
                        nativeHatchBudget(hatch),
                        hatch.getBaseMetaTileEntity()
                            .getStoredEU()));
                if (portion <= 0) continue;
                mEnergyHatches.clear();
                mEnergyHatches.add(hatch);
                if (!drainEnergyInput(portion)) return false;
                hatchEnergySpent.put(hatch, hatchEnergySpent.getOrDefault(hatch, 0L) + portion);
                remaining -= portion;
                if (remaining == 0) break;
            }
        } finally {
            mEnergyHatches.clear();
            mEnergyHatches.addAll(original);
        }
        return remaining == 0;
    }

    private void chargeBattery() {
        if (state.getKind() != CriticalMachineKind.BATTERY || !state.isEnabled() || !state.isStructureValidated())
            return;
        long free = Math.max(0L, maxEUStore() - getBaseMetaTileEntity().getStoredEU());
        long budget = Math.max(
            0L,
            state.getTier()
                .recipeVoltage() * 16L - energySpentThisTick);
        long charge = Math.min(free, Math.min(availableHatchEnergyThisTick(), budget));
        if (charge > 0 && drainNativeHatchEnergy(charge)) {
            energySpentThisTick += charge;
            getBaseMetaTileEntity().increaseStoredEnergyUnits(charge, false);
        }
    }

    public long emitCriticalEnergy(long amount) {
        long free = Math.max(0L, maxEUStore() - getBaseMetaTileEntity().getStoredEU());
        long accepted = Math.min(Math.max(0L, amount), free);
        if (accepted > 0 && !getBaseMetaTileEntity().increaseStoredEnergyUnits(accepted, false)) accepted = 0;
        CriticalMachineKind kind = state.getKind();
        if (kind != CriticalMachineKind.BATTERY && kind != CriticalMachineKind.TURBINE
            && kind != CriticalMachineKind.SUN
            && kind != CriticalMachineKind.ACCELERATOR) return accepted;
        long voltage = state.getTier()
            .recipeVoltage();
        long left = Math.min(getBaseMetaTileEntity().getStoredEU(), voltage * 16L), emitted = 0;
        int ampBudget = 16;
        java.util.ArrayList<MTEHatchDynamo> original = new java.util.ArrayList<>(mDynamoHatches);
        try {
            // GT205's shared output helper has an int accumulator. Send at most one native
            // voltage packet per call, while preserving each hatch's own per-tick amp budget.
            for (MTEHatchDynamo hatch : original) {
                if (!hatch.isValid() || hatch.maxEUOutput() > Integer.MAX_VALUE) continue;
                long nativeAmps = Math.min(ampBudget, hatch.maxAmperesOut());
                mDynamoHatches.clear();
                mDynamoHatches.add(hatch);
                for (long amp = 0; amp < nativeAmps && left > 0; amp++) {
                    long hatchFree = Math.max(
                        0L,
                        hatch.maxEUStore() - hatch.getBaseMetaTileEntity()
                            .getStoredEU());
                    long packet = Math.min(left, Math.min(hatch.maxEUOutput(), hatchFree));
                    if (packet <= 0 || !addEnergyOutputMultipleDynamos(packet, true)) break;
                    emitted += packet;
                    left -= packet;
                    ampBudget--;
                }
                if (ampBudget == 0 || left == 0) break;
            }
        } finally {
            mDynamoHatches.clear();
            mDynamoHatches.addAll(original);
        }
        if (emitted > 0) {
            getBaseMetaTileEntity().decreaseStoredEnergyUnits(emitted, false);
            energyEmittedThisTick += emitted;
        }
        return accepted;
    }

    private void importHatchResources() {
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus instanceof gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch) continue;
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                if (slot == bus.getCircuitSlot()) continue;
                for (int target = 0; target < CriticalMachineState.INPUT_SLOTS; target++) {
                    // A whole-stack native removal clears the slot without mutating the old stack reference.
                    // Re-read the real slot and transfer only the stack actually returned by GT's removal.
                    ItemStack source = bus.getStackInSlot(slot);
                    if (source == null || source.stackSize <= 0) break;
                    ItemStack existing = state.getStackInSlot(target);
                    if (existing != null
                        && (!existing.isItemEqual(source) || !ItemStack.areItemStackTagsEqual(existing, source)))
                        continue;
                    int moved = Math.min(
                        source.stackSize,
                        existing == null ? Math.min(64, source.getMaxStackSize())
                            : Math.min(64, existing.getMaxStackSize()) - existing.stackSize);
                    if (moved <= 0) continue;
                    ItemStack removed = bus.decrStackSize(slot, moved);
                    if (removed == null || removed.stackSize <= 0) continue;
                    ItemStack result = removed.copy();
                    result.stackSize = removed.stackSize + (existing == null ? 0 : existing.stackSize);
                    state.setInventorySlotContents(target, result);
                }
            }
        }
        for (gregtech.api.metatileentity.implementations.MTEHatchInput hatch : mInputHatches) {
            if (hatch instanceof gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch) continue;
            for (net.minecraftforge.fluids.FluidTankInfo tank : hatch.getTankInfo(ForgeDirection.UNKNOWN)) {
                FluidStack fluid = tank.fluid;
                if (fluid == null || fluid.amount <= 0) continue;
                int accepted = state.fill(ForgeDirection.UNKNOWN, fluid, false);
                if (accepted > 0) {
                    FluidStack request = fluid.copy();
                    request.amount = accepted;
                    FluidStack drained = hatch.drain(ForgeDirection.UNKNOWN, request, true);
                    int removed = drained != null && drained.isFluidEqual(request) ? drained.amount : 0;
                    if (removed > 0) {
                        request.amount = removed;
                        state.fill(ForgeDirection.UNKNOWN, request, true);
                    }
                }
            }
        }
    }

    private void exportHatchResources() {
        for (int slot = CriticalMachineState.INPUT_SLOTS; slot < state.getSizeInventory(); slot++) {
            ItemStack stack = state.getStackInSlot(slot);
            if (stack != null && addOutputAtomic(stack.copy())) state.setInventorySlotContents(slot, null);
        }
        net.minecraftforge.fluids.FluidTankInfo[] tanks = state.getTankInfo(ForgeDirection.UNKNOWN);
        for (int slot = 8; slot < tanks.length; slot++) {
            FluidStack fluid = tanks[slot].fluid;
            if (fluid != null) {
                FluidStack remainder = fluid.copy();
                addOutputPartial(remainder, getOutputHatches(new FluidStack[] { fluid }), true);
                int delivered = fluid.amount - remainder.amount;
                if (delivered > 0) {
                    FluidStack transfer = fluid.copy();
                    transfer.amount = delivered;
                    state.drain(ForgeDirection.UNKNOWN, transfer, true);
                }
            }
        }
    }

    @Override
    public net.minecraft.world.ChunkCoordIntPair getActiveChunk() {
        return state.getActiveConstructionChunk();
    }

    @Override
    public long maxEUStore() {
        return state.energyCapacity();
    }

    @Override
    public void saveNBTData(NBTTagCompound n) {
        super.saveNBTData(n);
        NBTTagCompound journal = new NBTTagCompound();
        state.writeToNBT(journal);
        n.setTag("criticalState", journal);
    }

    @Override
    public void setItemNBT(NBTTagCompound n) {
        super.setItemNBT(n);
        NBTTagCompound journal = new NBTTagCompound();
        getCriticalState().writeToNBT(journal);
        n.setTag("criticalState", journal);
        if (getBaseMetaTileEntity() != null) n.setLong("mStoredEnergy", getBaseMetaTileEntity().getStoredEU());
    }

    @Override
    public boolean isAccessAllowed(net.minecraft.entity.player.EntityPlayer player) {
        return getCriticalState().canUse(player);
    }

    @Override
    public float getExplosionResistance(ForgeDirection side) {
        return 10000F;
    }

    @Override
    public void loadNBTData(NBTTagCompound n) {
        super.loadNBTData(n);
        if (n.hasKey("criticalState", 10)) state.readFromNBT(n.getCompoundTag("criticalState"));
        else state.presetKind(presetKind);
    }

    @Override
    public void onRemoval() {
        getCriticalState().invalidate();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        getCriticalState().onChunkUnload();
        super.onUnload();
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return CriticalTooltips.create(presetKind);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return presetKind == null ? com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.displayRecipes
            : com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.forKind(presetKind);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        return CriticalControllerTextures.get(presetKind, side, facing, active);
    }
}
