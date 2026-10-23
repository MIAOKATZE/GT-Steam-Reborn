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
import com.miaokatze.gtsr.api.compat.GTVersionCompat;
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
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
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
    private int dynamoPacketsSpentThisTick;
    private final java.util.Map<MTEHatch, Long> hatchEnergySpent = new java.util.IdentityHashMap<>();
    private final java.util.Map<MTEHatchDynamo, Integer> dynamoPacketsSpent = new java.util.IdentityHashMap<>();
    private final java.util.Map<Long, TileEntity> portSnapshot = new java.util.LinkedHashMap<>();
    private int previousJournalState = -1;
    private boolean previousCanonicalValidation;
    private boolean loomStructureValid, loomGeometryValid;
    private long loomValidationTick = Long.MIN_VALUE, meSnapshotTick = Long.MIN_VALUE;
    private long hatchDiscoveryTick = Long.MIN_VALUE;
    private int hatchDiscoveryJournalState = -1;
    private boolean hatchesDirty = true;
    private ItemStack[] meItemsSnapshot = new ItemStack[0];
    private FluidStack[] meFluidsSnapshot = new FluidStack[0];
    private final java.util.List<gregtech.api.metatileentity.implementations.MTEHatchDataAccess> criticalDataHatches = new java.util.ArrayList<>();
    private final java.util.List<gregtech.api.metatileentity.implementations.MTEHatchBulkCatalystHousing> criticalCatalystHatches = new java.util.ArrayList<>();
    private World previewWorld;
    private NBTTagCompound previewSelection;

    /** Auxiliary chambers already consumed by the critical recipe paths use the same native declaration. */
    public enum CriticalHatchElement implements gregtech.api.interfaces.IHatchElement<MTECriticalMachine> {

        DataAccess("gt.blockmachines.hatch.dataaccess.name",
            gregtech.api.metatileentity.implementations.MTEHatchDataAccess.class) {

            @Override
            public long count(MTECriticalMachine machine) {
                return machine.criticalDataHatches.size();
            }
        },
        BulkCatalyst("gt.blockmachines.hatch.catalystbulk.name",
            gregtech.api.metatileentity.implementations.MTEHatchBulkCatalystHousing.class) {

            @Override
            public long count(MTECriticalMachine machine) {
                return machine.criticalCatalystHatches.size();
            }
        };

        private final String translationKey;
        private final Class<? extends IMetaTileEntity> type;

        CriticalHatchElement(String translationKey, Class<? extends IMetaTileEntity> type) {
            this.translationKey = translationKey;
            this.type = type;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return java.util.Collections.singletonList(type);
        }

        @Override
        public gregtech.api.util.IGTHatchAdder<? super MTECriticalMachine> adder() {
            return (machine, tile, casing) -> tile != null && type.isInstance(tile.getMetaTileEntity())
                && machine.addCriticalAuxiliaryHatch(tile, casing);
        }

        @Override
        public String getDisplayName() {
            return StatCollector.translateToLocal(translationKey);
        }

        @Override
        public String getDescriptionLangKey() {
            return translationKey;
        }
    }

    private boolean addCriticalAuxiliaryHatch(IGregTechTileEntity tile, int casing) {
        if (tile.getMetaTileEntity() instanceof gregtech.api.metatileentity.implementations.MTEHatchDataAccess hatch) {
            hatch.updateTexture(casing);
            if (!criticalDataHatches.contains(hatch)) criticalDataHatches.add(hatch);
            return true;
        }
        if (tile
            .getMetaTileEntity() instanceof gregtech.api.metatileentity.implementations.MTEHatchBulkCatalystHousing hatch) {
            hatch.updateTexture(casing);
            if (!criticalCatalystHatches.contains(hatch)) criticalCatalystHatches.add(hatch);
            return true;
        }
        return false;
    }

    public MTECriticalMachine(int id, String name, String localizedName, CriticalMachineKind kind) {
        super(id, name, localizedName);
        presetKind = kind;
        state.setLoomController(kind == null);
        state.presetKind(kind == null ? CriticalMachineKind.SUN : kind);
        mStartUpCheck = 200;
        registerCriticalProgress();
    }

    public MTECriticalMachine(String name, CriticalMachineKind kind) {
        super(name);
        presetKind = kind;
        state.setLoomController(kind == null);
        state.presetKind(kind == null ? CriticalMachineKind.SUN : kind);
        mStartUpCheck = 200;
        registerCriticalProgress();
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new com.miaokatze.gtsr.common.gui.MTECriticalMachineGui(this);
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
        if (current.getGeometryVersion() == 2) info.add(StatCollector.translateToLocal("gtsr.critical.v2.shared"));
        info.add(current.getStatus());
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
        getLocalCriticalState();
        if (state.getGeometryVersion() == 2 && getBaseMetaTileEntity() != null) {
            IGregTechTileEntity tile = getBaseMetaTileEntity();
            int partnerX = state.blockWorldX(56, isKindSelector() ? 35 : 47);
            int partnerZ = state.blockWorldZ(56, isKindSelector() ? 35 : 47);
            if (tile.getFrontFacing() == state.getControllerFacing(isKindSelector()) && tile.getWorld()
                .getChunkProvider()
                .chunkExists(partnerX >> 4, partnerZ >> 4)
                && tile.getWorld()
                    .getTileEntity(partnerX, tile.getYCoord(), partnerZ) instanceof IGregTechTileEntity gt
                && gt.getFrontFacing() == state.getControllerFacing(!isKindSelector())
                && gt.getMetaTileEntity() instanceof MTECriticalMachine partner
                && partner.isKindSelector() != isKindSelector()
                && partner.getLocalCriticalState()
                    .getGeometryVersion() == 2) {
                CriticalMachineState other = partner.getLocalCriticalState();
                if (isKindSelector() && !state.isLoomJournalOwner()
                    && CriticalMachineState.supportsV2Kind(partner.getPresetKind())) return other;
                if (!isKindSelector() && other.isLoomJournalOwner()
                    && other.getKind() == presetKind
                    && state.isLoomOwnedMainOf(other)) return other;
            }
        }
        return state;
    }

    /** Local state owns persistence only on a journal host; linked main controllers are passive proxies. */
    public CriticalMachineState getLocalCriticalState() {
        if (getBaseMetaTileEntity() instanceof TileEntity tile) state.bindHost(tile);
        return state;
    }

    /** Never treat an unloaded or merely turned journal owner as an orphan. No copied task is reset. */
    public boolean canRemoveOrphanedMain(net.minecraft.entity.player.EntityPlayer player) {
        CriticalMachineState local = getLocalCriticalState();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || !local.canRemoveOrphanedMain(player) || getCriticalState() != local) return false;
        int x = local.blockWorldX(56, 47), z = local.blockWorldZ(56, 47);
        if (!tile.getWorld()
            .getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return false;
        if (tile.getWorld()
            .getTileEntity(x, tile.getYCoord(), z) instanceof IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof MTECriticalMachine partner
            && partner.isKindSelector()
            && local.isLoomOwnedMainOf(partner.getLocalCriticalState())) return false;
        return true;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTECriticalMachine(mName, presetKind);
    }

    @Override
    protected com.gtnewhorizon.structurelib.alignment.IAlignmentLimits getInitialAlignmentLimits() {
        return (direction, rotation, flip) -> direction.offsetY == 0 && direction != ForgeDirection.UNKNOWN
            && rotation == com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL
            && flip == com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE;
    }

    /** Both native declarations describe the same rotated region from their respective controller fronts. */
    @Override
    public com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing getExtendedFacing() {
        if (state == null) return com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.DEFAULT;
        return com.miaokatze.gtsr.common.critical.CriticalStructureAlignment
            .facing(getLocalCriticalState().getStructureFacing(), isKindSelector() || state.getGeometryVersion() != 2);
    }

    @Override
    public IStructureDefinition<MTECriticalMachine> getStructureDefinition() {
        return CriticalStructures.definition(this);
    }

    /** Use GT's declaration entry point so StructureLib-aware tools can capture the piece and offsets. */
    public boolean buildCriticalPiece(String piece, ItemStack trigger, boolean hintsOnly, int a, int b, int c) {
        return buildPiece(piece, trigger, hintsOnly, a, b, c);
    }

    public int survivalBuildCriticalPiece(String piece, ItemStack trigger, int a, int b, int c, int budget,
        com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment environment) {
        return survivalBuildPiece(piece, trigger, a, b, c, budget, environment, false);
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
        if (world instanceof com.miaokatze.gtsr.client.nei.CriticalPreviewWorld preview)
            preview.gtsr$markCriticalPreview();
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
        isLoomStructureValid();
        CriticalMachineState current = getCriticalState();
        if (!current.isFabricationHostValid() || current.getState() == 2 && !current.isStructureValidated())
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        criticalDataHatches.clear();
        criticalCatalystHatches.clear();
        // GT structure checks can clear the lists without changing any port's TileEntity identity.
        hatchesDirty = true;
        meSnapshotTick = Long.MIN_VALUE;
    }

    private void discoverHatches(boolean force) {
        CriticalMachineState current = getCriticalState();
        if (current.getWorldObj() == null) return;
        long now = current.getWorldObj()
            .getTotalWorldTime();
        if (!force && !hatchesDirty
            && hatchDiscoveryJournalState == current.getState()
            && hatchDiscoveryTick != Long.MIN_VALUE
            && now - hatchDiscoveryTick < 20) return;
        hatchDiscoveryTick = now;
        hatchDiscoveryJournalState = current.getState();
        java.util.Map<Long, TileEntity> next = new java.util.LinkedHashMap<>();
        for (CriticalGeometry.Voxel port : current.getResourceHatchPorts()) {
            int x = current.blockWorldX(port), y = current.blockWorldY(port.y), z = current.blockWorldZ(port);
            TileEntity tile = current.worldObj.getChunkProvider()
                .chunkExists(x >> 4, z >> 4) ? current.worldObj.getTileEntity(x, y, z) : null;
            next.put(port.positionKey(), current.isStandardHatch(port) ? tile : null);
        }
        boolean changed = next.size() != portSnapshot.size();
        for (java.util.Map.Entry<Long, TileEntity> entry : next.entrySet())
            if (!portSnapshot.containsKey(entry.getKey()) || portSnapshot.get(entry.getKey()) != entry.getValue())
                changed = true;
        if (!force && !hatchesDirty && !changed) return;
        clearHatches();
        meSnapshotTick = Long.MIN_VALUE;
        portSnapshot.clear();
        portSnapshot.putAll(next);
        for (TileEntity tile : next.values()) if (tile instanceof IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof gregtech.api.metatileentity.implementations.MTEHatchDataAccess data
            && !criticalDataHatches.contains(data)) criticalDataHatches.add(data);
        for (TileEntity tile : next.values()) if (tile instanceof IGregTechTileEntity gt && gt
            .getMetaTileEntity() instanceof gregtech.api.metatileentity.implementations.MTEHatchBulkCatalystHousing catalyst
            && !criticalCatalystHatches.contains(catalyst)) criticalCatalystHatches.add(catalyst);
        for (TileEntity tile : next.values())
            if (CriticalMachineState.isStandardHatchHost(tile) && tile instanceof IGregTechTileEntity gt)
                if (!addExoticEnergyInputToMachineList(
                    gt,
                    CriticalControllerTextures.casingIndex(current.getGeometryVersion() == 2))
                    && !addEnergyInputToMachineList(
                        gt,
                        CriticalControllerTextures.casingIndex(current.getGeometryVersion() == 2)))
                    addToMachineList(gt, CriticalControllerTextures.casingIndex(current.getGeometryVersion() == 2));
        hatchesDirty = false;
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        // GT205 schedules structure updates after 50 ticks and starts after 100, rather than polling
        // on a fixed recipe interval. Double those structure delays for the fabrication loom.
        if (tile.isServerSide() && mUpdated) {
            if (mUpdate <= 0) mUpdate = 100;
            mUpdated = false;
        }
        super.onPostTick(tile, tick);
        if (!tile.isServerSide()) return;
        CriticalMachineState current = getCriticalState();
        if (getLocalCriticalState().getGeometryVersion() == 2) {
            byte light = 4;
            if (tile instanceof gregtech.api.metatileentity.BaseMetaTileEntity base && base.getLightValue() != light)
                base.setLightValue(light);
        }
        discoverHatches(false);
        if (current != state || state.isLoomOwnedMain() || isKindSelector() && state.usesLegacyV2Geometry()) {
            refreshNativeStructure(current, tile);
            tile.setActive(current != state && current.isWorking());
            return;
        }
        energySpentThisTick = 0;
        energyEmittedThisTick = 0;
        dynamoPacketsSpentThisTick = 0;
        hatchEnergySpent.clear();
        dynamoPacketsSpent.clear();
        current.updateEntity();
        chargeBattery();
        if (tick % 20 == 0)
            com.miaokatze.gtsr.common.critical.CriticalRenderAnchors.ensure(current, isLoomGeometryValid());
        refreshNativeStructure(current, tile);
        exportHatchResources();
        syncNativeMachineDisplay(current);
        tile.setActive(current.isWorking());
    }

    /** Passive mains observe the journal's formation changes without waiting for a neighbour update. */
    private void refreshNativeStructure(CriticalMachineState current, IGregTechTileEntity tile) {
        if (previousJournalState != current.getState()
            || previousCanonicalValidation != current.isStructureValidated()) {
            previousJournalState = current.getState();
            previousCanonicalValidation = current.isStructureValidated();
            checkStructure(true, tile);
        }
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
        // Keep monitoring and player repair recovery alive after either loom or target damage.
        if (allowed && reason == gregtech.api.util.shutdown.ShutDownReasonRegistry.STRUCTURE_INCOMPLETE)
            getBaseMetaTileEntity().enableWorking();
    }

    public boolean isLoomStructureValid() {
        MTECriticalMachine owner = loomValidationOwner();
        if (owner != this) return owner.isLoomStructureValid();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null) return false;
        long now = tile.getWorld()
            .getTotalWorldTime();
        if (loomValidationTick == Long.MIN_VALUE || now - loomValidationTick >= 100) return forceLoomStructureCheck();
        return loomStructureValid;
    }

    public boolean forceLoomStructureCheck() {
        MTECriticalMachine owner = loomValidationOwner();
        if (owner != this) return owner.forceLoomStructureCheck();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null) return false;
        loomValidationTick = tile.getWorld()
            .getTotalWorldTime();
        clearHatches();
        loomGeometryValid = com.miaokatze.gtsr.common.critical.CriticalLoomStructure.validateGeometry(this);
        loomStructureValid = loomGeometryValid
            && com.miaokatze.gtsr.common.critical.CriticalLoomStructure.validateInterfaces(this);
        discoverHatches(true);
        return loomStructureValid;
    }

    private MTECriticalMachine loomValidationOwner() {
        CriticalMachineState current = getCriticalState();
        return current != state && current.getHost() instanceof IGregTechTileEntity tile
            && tile.getMetaTileEntity() instanceof MTECriticalMachine machine ? machine : this;
    }

    public boolean isLoomGeometryValid() {
        MTECriticalMachine owner = loomValidationOwner();
        if (owner != this) return owner.isLoomGeometryValid();
        isLoomStructureValid();
        return loomGeometryValid;
    }

    public boolean applyConstructionDebug(net.minecraft.entity.player.EntityPlayer player, int requirements, int speed,
        boolean ignoreEnergy) {
        return getCriticalState().applyConstructionDebug(player, requirements, speed, ignoreEnergy);
    }

    public boolean resetConstructionDebug(net.minecraft.entity.player.EntityPlayer player) {
        return getCriticalState().resetConstructionDebug(player);
    }

    public int getConstructionDebugRequirements() {
        return getCriticalState().getConstructionDebugRequirements();
    }

    public int getConstructionDebugSpeed() {
        return getCriticalState().getConstructionDebugSpeed();
    }

    public boolean getConstructionDebugIgnoreEnergy() {
        return getCriticalState().getConstructionDebugIgnoreEnergy();
    }

    @Override
    public boolean checkRecipe(ItemStack stack) {
        return false;
    }

    public long availableCriticalEnergy() {
        long total = getBaseMetaTileEntity() == null ? 0 : getBaseMetaTileEntity().getStoredEU();
        for (MTEHatch hatch : getExoticAndNormalEnergyHatchList()) if (hatch.isValid()) {
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

    public java.util.List<gregtech.api.metatileentity.implementations.MTEHatchInput> getCriticalInputHatches() {
        return java.util.Collections.unmodifiableList(mInputHatches);
    }

    public java.util.List<gregtech.common.tileentities.machines.IDualInputHatch> getCriticalDualInputHatches() {
        return new java.util.ArrayList<>(mDualInputHatches);
    }

    public java.util.List<gregtech.api.metatileentity.implementations.MTEHatchInputBus> getCriticalInputBusses() {
        return java.util.Collections.unmodifiableList(mInputBusses);
    }

    public java.util.List<gregtech.api.metatileentity.implementations.MTEHatchOutput> getCriticalOutputHatches() {
        return java.util.Collections.unmodifiableList(mOutputHatches);
    }

    public java.util.List<gregtech.api.metatileentity.implementations.MTEHatchOutputBus> getCriticalOutputBusses() {
        return java.util.Collections.unmodifiableList(mOutputBusses);
    }

    public java.util.List<gregtech.api.util.GTRecipe.RecipeAssemblyLine> criticalAssemblyRecipes() {
        java.util.Set<gregtech.api.util.GTRecipe.RecipeAssemblyLine> recipes = java.util.Collections
            .newSetFromMap(new java.util.IdentityHashMap<>());
        for (gregtech.api.metatileentity.implementations.MTEHatchDataAccess hatch : criticalDataHatches)
            if (hatch.isValid()) recipes.addAll(hatch.getAssemblyLineRecipes());
        return new java.util.ArrayList<>(recipes);
    }

    public long criticalRecipeVoltage() {
        long voltage = 0;
        for (MTEHatch hatch : getExoticAndNormalEnergyHatchList())
            if (hatch.isValid()) voltage = Math.max(voltage, hatch.maxEUInput());
        return voltage;
    }

    public long criticalRecipePower() {
        return Math.min(
            Math.max(0L, criticalEnergyTickCap() - energySpentThisTick),
            gregtech.api.util.GTUtility.addSafe(getBaseMetaTileEntity().getStoredEU(), availableHatchEnergyThisTick()));
    }

    public static long criticalEnergyTickCap() {
        return com.miaokatze.gtsr.common.critical.CriticalTier.T3.recipeVoltage() * 16L;
    }

    /** Canonical physical host identity; construction permission owner is intentionally unrelated. */
    public com.miaokatze.gtsr.common.critical.CriticalStellarRuntime.PowerPort criticalStellarPowerPort() {
        return new com.miaokatze.gtsr.common.critical.CriticalStellarRuntime.PowerPort() {

            @Override
            public long available() {
                return criticalRecipePower();
            }

            @Override
            public com.miaokatze.gtsr.common.critical.CriticalEnergyDebit debit(long requested) {
                return debitCriticalEnergy(requested);
            }

            @Override
            public long refund(long requested) {
                // This callback is used only while settling a prepaid local refund, never for generation.
                // Even with a full buffer (accepted=0), existing stored EU can drain through real dynamos.
                return emitCriticalEnergy(requested);
            }

            @Override
            public String identity() {
                IGregTechTileEntity base = getBaseMetaTileEntity();
                return base.getWorld().provider.dimensionId + ":"
                    + base.getXCoord()
                    + ":"
                    + base.getYCoord()
                    + ":"
                    + base.getZCoord();
            }

            @Override
            public void changed() {
                getBaseMetaTileEntity().markDirty();
            }
        };
    }

    public boolean hasCriticalStellarRefundOutput() {
        for (MTEHatchDynamo hatch : mDynamoHatches) if (hatch.isValid() && hatch.maxEUOutput() > 0
            && hatch.maxEUOutput() <= Integer.MAX_VALUE
            && hatch.maxAmperesOut() > 0) return true;
        return false;
    }

    public ItemStack[] criticalRecipeItemsSnapshot() {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        java.util.Collections.addAll(items, criticalPhysicalItemsSnapshot());
        java.util.Collections.addAll(items, criticalMEItemsSnapshot());
        if (getCriticalState().getKind() == CriticalMachineKind.PROCESSING
            && com.miaokatze.gtsr.common.critical.CriticalProcessingModes.selected(getCriticalState())
                .equals(GTVersionCompat.requireGppRecipeMap("quantumForceTransformerRecipes").unlocalizedName))
            for (gregtech.api.metatileentity.implementations.MTEHatchBulkCatalystHousing hatch : criticalCatalystHatches)
                if (hatch.isValid() && hatch.getItemStack() != null && hatch.getItemCount() > 0) {
                    ItemStack catalyst = hatch.getItemStack()
                        .copy();
                    catalyst.stackSize = hatch.getItemCount();
                    items.add(catalyst);
                }
        java.util.Set<gregtech.api.metatileentity.implementations.MTEHatchInputBus> seen = java.util.Collections
            .newSetFromMap(new java.util.IdentityHashMap<>());
        for (gregtech.api.metatileentity.implementations.MTEHatchInputBus bus : mInputBusses)
            if (bus.isValid() && seen.add(bus)) {
                int circuitSlot = bus instanceof gregtech.api.metatileentity.implementations.MTEHatchInputBusDebug
                    ? bus.getSizeInventory() - 1
                    : bus.getCircuitSlot();
                if (circuitSlot < 0) continue;
                ItemStack circuit = bus.getStackInSlot(circuitSlot);
                if (circuit != null) items.add(circuit.copy());
            }
        return items.toArray(new ItemStack[0]);
    }

    public ItemStack[] criticalPhysicalItemsSnapshot() {
        return com.miaokatze.gtsr.common.critical.CriticalHatchFunding.snapshotItems(mInputBusses);
    }

    public FluidStack[] criticalPhysicalFluidsSnapshot() {
        return com.miaokatze.gtsr.common.critical.CriticalHatchFunding.snapshotFluids(mInputHatches);
    }

    public String getCriticalInputOverviewForGui() {
        StringBuilder text = new StringBuilder();
        for (ItemStack stack : criticalPhysicalItemsSnapshot())
            appendInputOverview(text, stack.getDisplayName(), stack.stackSize);
        for (FluidStack fluid : criticalPhysicalFluidsSnapshot())
            appendInputOverview(text, fluid.getLocalizedName(), fluid.amount);
        for (ItemStack stack : criticalMEItemsSnapshot())
            appendInputOverview(text, "ME: " + stack.getDisplayName(), stack.stackSize);
        for (FluidStack fluid : criticalMEFluidsSnapshot())
            appendInputOverview(text, "ME: " + fluid.getLocalizedName(), fluid.amount);
        return text.length() == 0 ? "\u2014" : text.toString();
    }

    public String getCriticalOutputOverviewForGui() {
        StringBuilder text = new StringBuilder();
        for (gregtech.api.metatileentity.implementations.MTEHatchOutputBus bus : mOutputBusses) {
            if (!bus.isValid()) continue;
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                ItemStack stack = bus.getStackInSlot(slot);
                if (stack != null && stack.stackSize > 0)
                    appendInputOverview(text, stack.getDisplayName(), stack.stackSize);
            }
        }
        for (gregtech.api.metatileentity.implementations.MTEHatchOutput hatch : mOutputHatches) {
            if (!hatch.isValid()) continue;
            for (net.minecraftforge.fluids.FluidTankInfo tank : hatch.getTankInfo(ForgeDirection.UNKNOWN))
                if (tank != null && tank.fluid != null && tank.fluid.amount > 0)
                    appendInputOverview(text, tank.fluid.getLocalizedName(), tank.fluid.amount);
        }
        return text.length() == 0 ? "\u2014" : text.toString();
    }

    private static void appendInputOverview(StringBuilder text, String name, int amount) {
        if (text.length() >= 2048) return;
        if (text.length() > 0) text.append('\n');
        text.append(name)
            .append(" x ")
            .append(amount);
    }

    public com.miaokatze.gtsr.common.critical.CriticalNativeMEFunding.Result fundCriticalHatchInputs(ItemStack[] items,
        FluidStack[] fluids) {
        com.miaokatze.gtsr.common.critical.CriticalNativeMEFunding.Result result = com.miaokatze.gtsr.common.critical.CriticalHatchFunding
            .fund(this, mInputBusses, mInputHatches, items, fluids);
        meSnapshotTick = Long.MIN_VALUE;
        return result;
    }

    public boolean canConsumeCriticalEnergy(long amount) {
        long budget = com.miaokatze.gtsr.common.critical.CriticalTier.T3.recipeVoltage() * 16L;
        long buffered = getBaseMetaTileEntity() == null ? 0L : getBaseMetaTileEntity().getStoredEU();
        return amount >= 0 && amount <= budget - energySpentThisTick
            && amount <= gregtech.api.util.GTUtility.addSafe(buffered, availableHatchEnergyThisTick());
    }

    public com.miaokatze.gtsr.common.critical.CriticalNativeMEFunding.ItemConsumption consumeAllCriticalInputItem(
        ItemStack wanted) {
        com.miaokatze.gtsr.common.critical.CriticalNativeMEFunding.ItemConsumption result = com.miaokatze.gtsr.common.critical.CriticalHatchFunding
            .consumeAllItem(this, mInputBusses, wanted);
        meSnapshotTick = Long.MIN_VALUE;
        return result;
    }

    public boolean consumeCriticalEnergy(long amount) {
        long budget = com.miaokatze.gtsr.common.critical.CriticalTier.T3.recipeVoltage() * 16L;
        long buffered = getBaseMetaTileEntity().getStoredEU();
        if (amount < 0 || amount > budget - energySpentThisTick
            || amount > gregtech.api.util.GTUtility.addSafe(buffered, availableHatchEnergyThisTick())) return false;
        long fromBuffer = Math.min(amount, buffered);
        if (amount > fromBuffer && !drainNativeHatchEnergy(amount - fromBuffer)) return false;
        if (fromBuffer > 0) getBaseMetaTileEntity().decreaseStoredEnergyUnits(fromBuffer, false);
        energySpentThisTick += amount;
        return true;
    }

    /**
     * GT205's aggregate helper ignores individual decreaseStoredEnergyUnits return values. Debit each
     * explicit tile directly and acknowledge only a successful native decrease. No speculative rollback.
     */
    public com.miaokatze.gtsr.common.critical.CriticalEnergyDebit debitCriticalEnergy(long amount) {
        if (amount < 0) throw new IllegalArgumentException("negative debit");
        long remaining = Math.min(amount, criticalRecipePower());
        long confirmed = 0;
        if (remaining == 0) return new com.miaokatze.gtsr.common.critical.CriticalEnergyDebit(amount, 0, false);
        long fromBuffer = Math.min(remaining, getBaseMetaTileEntity().getStoredEU());
        try {
            if (fromBuffer > 0) {
                if (!getBaseMetaTileEntity().decreaseStoredEnergyUnits(fromBuffer, false))
                    return new com.miaokatze.gtsr.common.critical.CriticalEnergyDebit(amount, 0, true);
                confirmed += fromBuffer;
                remaining -= fromBuffer;
                energySpentThisTick += fromBuffer;
            }
            java.util.Set<MTEHatch> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (MTEHatch hatch : getExoticAndNormalEnergyHatchList()) {
                if (remaining == 0) break;
                if (!seen.add(hatch) || !hatch.isValid()) continue;
                long portion = Math.min(
                    remaining,
                    Math.min(
                        nativeHatchBudget(hatch),
                        hatch.getBaseMetaTileEntity()
                            .getStoredEU()));
                if (portion <= 0) continue;
                // Reserve this tick's source budget even if the external call becomes uncertain.
                hatchEnergySpent.put(hatch, hatchEnergySpent.getOrDefault(hatch, 0L) + portion);
                energySpentThisTick += portion;
                if (!hatch.getBaseMetaTileEntity()
                    .decreaseStoredEnergyUnits(portion, false))
                    return new com.miaokatze.gtsr.common.critical.CriticalEnergyDebit(amount, confirmed, true);
                confirmed += portion;
                remaining -= portion;
            }
            return new com.miaokatze.gtsr.common.critical.CriticalEnergyDebit(amount, confirmed, false);
        } catch (RuntimeException failure) {
            return new com.miaokatze.gtsr.common.critical.CriticalEnergyDebit(amount, confirmed, true);
        }
    }

    private long nativeHatchBudget(MTEHatch hatch) {
        long voltage = hatch.maxEUInput(), amperage = hatch.maxWorkingAmperesIn();
        long nativePower = voltage > 0 && amperage > Long.MAX_VALUE / voltage ? Long.MAX_VALUE : voltage * amperage;
        return Math.max(0L, nativePower - hatchEnergySpent.getOrDefault(hatch, 0L));
    }

    private long availableHatchEnergyThisTick() {
        long total = 0;
        java.util.Set<MTEHatch> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (MTEHatch hatch : getExoticAndNormalEnergyHatchList()) if (seen.add(hatch) && hatch.isValid()) {
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
        long remaining = amount;
        // Drain one explicit hatch at a time, so exotic hatches cannot bypass another hatch's native rate budget.
        for (MTEHatch hatch : getExoticAndNormalEnergyHatchList()) {
            if (!hatch.isValid()) continue;
            long portion = Math.min(
                remaining,
                Math.min(
                    nativeHatchBudget(hatch),
                    hatch.getBaseMetaTileEntity()
                        .getStoredEU()));
            if (portion <= 0) continue;
            if (!gregtech.api.util.ExoticEnergyInputHelper
                .drainEnergy(portion, java.util.Collections.singletonList(hatch))) return false;
            hatchEnergySpent.put(hatch, hatchEnergySpent.getOrDefault(hatch, 0L) + portion);
            remaining -= portion;
            if (remaining == 0) break;
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
        long left = Math
            .min(getBaseMetaTileEntity().getStoredEU(), Math.max(0L, voltage * 16L - energyEmittedThisTick)),
            emitted = 0;
        int ampBudget = Math.max(0, 16 - dynamoPacketsSpentThisTick);
        java.util.ArrayList<MTEHatchDynamo> original = new java.util.ArrayList<>(mDynamoHatches);
        try {
            // GT205's shared output helper has an int accumulator. Send at most one native
            // voltage packet per call, while preserving each hatch's own per-tick amp budget.
            for (MTEHatchDynamo hatch : original) {
                if (!hatch.isValid() || hatch.maxEUOutput() > Integer.MAX_VALUE) continue;
                long nativeAmps = Math
                    .min(ampBudget, Math.max(0L, hatch.maxAmperesOut() - dynamoPacketsSpent.getOrDefault(hatch, 0)));
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
                    dynamoPacketsSpent.put(hatch, dynamoPacketsSpent.getOrDefault(hatch, 0) + 1);
                    dynamoPacketsSpentThisTick++;
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
        return getCriticalState().energyCapacity();
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
        getLocalCriticalState().writeToNBT(journal);
        if (journal.hasKey("criticalStellarRuntime", 10)) {
            NBTTagCompound itemStellar = (NBTTagCompound) journal.getCompoundTag("criticalStellarRuntime")
                .copy();
            com.miaokatze.gtsr.common.critical.CriticalStellarRuntime.markItemCopy(itemStellar);
            journal.setTag("criticalStellarRuntime", itemStellar);
        }
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
    public int getLightOpacity() {
        return getLocalCriticalState().getGeometryVersion() == 2 ? 255 : super.getLightOpacity();
    }

    @Override
    public void loadNBTData(NBTTagCompound n) {
        super.loadNBTData(n);
        if (n.hasKey("criticalState", 10)) state.readFromNBT(n.getCompoundTag("criticalState"));
        else state.presetKind(presetKind);
    }

    @Override
    public void onRemoval() {
        if (!state.isLoomOwnedMain() && getCriticalState() == state
            && (!isKindSelector() || !state.usesLegacyV2Geometry())) state.invalidate();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        if (!state.isLoomOwnedMain() && getCriticalState() == state
            && (!isKindSelector() || !state.usesLegacyV2Geometry())) state.onChunkUnload();
        super.onUnload();
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return CriticalTooltips.create(presetKind);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.forKind(presetKind);
    }

    @Override
    public java.util.Collection<RecipeMap<?>> getAvailableRecipeMaps() {
        RecipeMap<?> map = getRecipeMap();
        if (map == null) return java.util.Collections.emptyList();
        if (presetKind == CriticalMachineKind.SOLAR) {
            return java.util.Arrays.asList(map, com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.stellarIrradiation);
        }
        return java.util.Collections.singletonList(map);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        return CriticalControllerTextures
            .get(presetKind, side, facing, active, getLocalCriticalState().getGeometryVersion() == 2);
    }
}
