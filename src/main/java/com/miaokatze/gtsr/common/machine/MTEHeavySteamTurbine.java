package com.miaokatze.gtsr.common.machine;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.common.gui.MTEHeavySteamTurbineGui;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.machine.turbine.HeavySteamTurbineCycle;
import com.miaokatze.gtsr.common.machine.turbine.HeavySteamTurbineStructureDef;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes.SteamType;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualState;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import tectech.thing.metaTileEntity.hatch.MTEHatchDynamoMulti;

/** Independent, non-stacking ordinary-steam generator. All conversion parameters live in HeavySteamTurbineCycle. */
@IMetaTileEntity.SkipGenerateDescription
public class MTEHeavySteamTurbine extends MTEGTSRMultiBlockBase<MTEHeavySteamTurbine>
    implements IConstructable, ISurvivalConstructable, MachineVisualHost {

    public static final int HORIZONTAL_OFF_SET = 2;
    public static final int VERTICAL_OFF_SET = 7;
    public static final int DEPTH_OFF_SET = 1;
    public int mMaterialTier = -1;
    private final List<MTEHatchDynamoMulti> multiDynamos = new ArrayList<>();
    private final MachineVisualState machineVisualState = new MachineVisualState();
    private int energyCredit, paidSteam, excessWater, pendingWater;

    public enum DynamoElement implements IHatchElement<MTEHeavySteamTurbine> {

        OUTPUT;

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return ImmutableList.of(MTEHatchDynamo.class, MTEHatchDynamoMulti.class);
        }

        @Override
        public IGTHatchAdder<? super MTEHeavySteamTurbine> adder() {
            return MTEHeavySteamTurbine::addDynamoToMachineList;
        }

        @Override
        public String getDisplayName() {
            return StatCollector.translateToLocal("GT5U.MBTT.DynamoHatch");
        }

        @Override
        public String getDescriptionLangKey() {
            return "GT5U.MBTT.DynamoHatch";
        }

        @Override
        public long count(MTEHeavySteamTurbine machine) {
            return machine.mDynamoHatches.size() + machine.multiDynamos.size();
        }
    }

    public MTEHeavySteamTurbine(int id, String name, String regionalName) {
        super(id, name, regionalName);
        registerProgressEntries();
    }

    public MTEHeavySteamTurbine(String name) {
        super(name);
        registerProgressEntries();
    }

    private void registerProgressEntries() {
        registerEntry(
            "rated_output",
            "gtsr.gui.heavy_turbine.rated_output",
            "%.0f EU/t",
            EnumChatFormatting.AQUA,
            () -> HeavySteamTurbineCycle.output(mMaterialTier));
        registerEntry(
            "steam_efficiency",
            "gtsr.gui.heavy_turbine.efficiency",
            "%.0f%%",
            EnumChatFormatting.GOLD,
            () -> HeavySteamTurbineCycle.efficiency(mMaterialTier));
        registerEntry(
            "steam_rate",
            "gtsr.gui.heavy_turbine.steam_rate",
            "%.3f L/t",
            EnumChatFormatting.WHITE,
            () -> mMaterialTier < 1 ? 0
                : HeavySteamTurbineCycle.output(mMaterialTier) * (double) HeavySteamTurbineCycle.ENERGY_UNITS_PER_EU
                    / HeavySteamTurbineCycle.efficiency(mMaterialTier));
    }

    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEHeavySteamTurbineGui(this);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEHeavySteamTurbine(mName);
    }

    @Override
    public IStructureDefinition<MTEHeavySteamTurbine> getStructureDefinition() {
        return HeavySteamTurbineStructureDef.definition();
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        buildPiece("main", stack, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int budget, ISurvivalBuildEnvironment environment) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            "main",
            stack,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            budget,
            environment,
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        mMaterialTier = -1;
        multiDynamos.clear();
        if (!checkPiece("main", HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;
        if (mMaterialTier < 1 || mInputHatches.isEmpty()
            || mOutputHatches.isEmpty()
            || (mDynamoHatches.isEmpty() && multiDynamos.isEmpty())) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        for (MTEHatch hatch : dynamos()) hatch.updateTexture(casingIndex());
        for (MTEHatch hatch : mInputHatches) hatch.updateTexture(casingIndex());
        for (MTEHatch hatch : mOutputHatches) hatch.updateTexture(casingIndex());
        // Dynamo capacity changes do not emit recipe events; reuse GT's idle recheck schedule.
        doPeriodicChecks = true;
    }

    @Override
    public boolean addDynamoToMachineList(IGregTechTileEntity tile, int texture) {
        if (tile == null) return false;
        if (tile.getMetaTileEntity() instanceof MTEHatchDynamoMulti hatch) {
            hatch.updateTexture(texture);
            return multiDynamos.add(hatch);
        }
        return super.addDynamoToMachineList(tile, texture);
    }

    private List<MTEHatch> dynamos() {
        List<MTEHatch> result = new ArrayList<>();
        for (MTEHatch hatch : GTUtility.validMTEList(mDynamoHatches)) result.add(hatch);
        for (MTEHatch hatch : GTUtility.validMTEList(multiDynamos)) result.add(hatch);
        return result;
    }

    private long availableOutput(MTEHatch hatch) {
        return HeavySteamTurbineCycle.usableDynamoCapacity(
            hatch.maxEUOutput(),
            hatch.maxAmperesOut(),
            hatch.maxEUStore(),
            hatch.getBaseMetaTileEntity()
                .getStoredEU());
    }

    private boolean hasOutputRoom() {
        long room = 0;
        for (MTEHatch hatch : dynamos()) room += availableOutput(hatch);
        return room >= HeavySteamTurbineCycle.output(mMaterialTier);
    }

    private boolean hasSteam(int required) {
        long available = paidSteam;
        for (FluidStack fluid : getStoredFluids()) {
            if (SteamTurbineSteamTypes.classifyFluid(fluid) == SteamType.STEAM) available += fluid.amount;
        }
        return available >= required;
    }

    private boolean paySteam(int required) {
        int before = paidSteam;
        for (FluidStack fluid : getStoredFluids()) {
            if (paidSteam >= required) break;
            if (SteamTurbineSteamTypes.classifyFluid(fluid) == SteamType.STEAM) {
                paidSteam += GTSRHatchFluidAccess
                    .depleteFluidAcross(mInputHatches, new FluidStack(fluid, required - paidSteam));
            }
        }
        if (paidSteam != before) markDirty();
        // Partial network withdrawals remain owned by the controller until a later successful tick.
        return paidSteam >= required;
    }

    private boolean flushWater() {
        int before = pendingWater;
        for (var hatch : GTUtility.validMTEList(mOutputHatches)) {
            if (pendingWater == 0) break;
            pendingWater -= hatch.fill(ForgeDirection.UNKNOWN, GTModHandler.getDistilledWater(pendingWater), true);
        }
        if (pendingWater != before) markDirty();
        return pendingWater == 0;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (mMaterialTier < 1) return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        if (!hasOutputRoom() || !flushWater()) return CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
        if (!hasSteam(HeavySteamTurbineCycle.steamRequired(mMaterialTier, energyCredit)))
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        mEUt = HeavySteamTurbineCycle.output(mMaterialTier);
        mEfficiency = 10000;
        mMaxProgresstime = 20;
        return CheckRecipeResultRegistry.GENERATING;
    }

    @Override
    public boolean onRunningTick(ItemStack stack) {
        // Reserve both power throughput and buffer space before withdrawing any steam.
        if (!hasOutputRoom() || !flushWater()) {
            mMaxProgresstime = 0;
            mProgresstime = 0;
            mEUt = 0;
            return false;
        }
        int steam = HeavySteamTurbineCycle.steamRequired(mMaterialTier, energyCredit);
        if (!hasSteam(steam) || !paySteam(steam)) {
            mMaxProgresstime = 0;
            mProgresstime = 0;
            mEUt = 0;
            return false;
        }
        paidSteam -= steam;
        energyCredit = HeavySteamTurbineCycle.nextCredit(mMaterialTier, energyCredit, steam);
        long remaining = HeavySteamTurbineCycle.output(mMaterialTier);
        for (MTEHatch hatch : dynamos()) {
            long amount = Math.min(remaining, availableOutput(hatch));
            if (amount > 0) hatch.getBaseMetaTileEntity()
                .increaseStoredEnergyUnits(amount, false);
            remaining -= amount;
            if (remaining == 0) break;
        }
        excessWater += steam;
        pendingWater += excessWater / HeavySteamTurbineCycle.STEAM_PER_WATER;
        excessWater %= HeavySteamTurbineCycle.STEAM_PER_WATER;
        flushWater();
        return true;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public int getMaxEfficiency(ItemStack stack) {
        return 10000;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public String[] getInfoData() {
        return new String[] { StatCollector.translateToLocal("gt.blockmachines.gtsr.heavy.steam.turbine.name"),
            HeavySteamTurbineCycle.output(mMaterialTier) + " EU/t",
            StatCollector.translateToLocal("gtsr.gui.heavy_turbine.efficiency")
                + HeavySteamTurbineCycle.efficiency(mMaterialTier)
                + "%" };
    }

    private int casingIndex() {
        return mMaterialTier == 2 ? HeavySteamTurbineStructureDef.STEEL_CASING_INDEX
            : HeavySteamTurbineStructureDef.BRONZE_CASING_INDEX;
    }

    @Override
    public byte getUpdateData() {
        return (byte) mMaterialTier;
    }

    @Override
    public void onValueUpdate(byte value) {
        mMaterialTier = value;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(casingIndex());
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing, TextureFactory.of(
            active ? Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE : Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL),
            TextureFactory.builder()
                .addIcon(
                    active ? Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE_GLOW
                        : Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_GLOW)
                .extFacing()
                .glow()
                .build() };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder()
            .addMachineType(StatCollector.translateToLocal("gt.blockmachines.gtsr.heavy.steam.turbine.name"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.heavy_turbine.description"))
            .addInfo(
                StatCollector.translateToLocalFormatted(
                    "gtsr.tooltip.heavy_turbine.bronze",
                    HeavySteamTurbineCycle.BRONZE_OUTPUT_EUT,
                    HeavySteamTurbineCycle.BRONZE_EFFICIENCY_PERCENT))
            .addInfo(
                StatCollector.translateToLocalFormatted(
                    "gtsr.tooltip.heavy_turbine.steel",
                    HeavySteamTurbineCycle.STEEL_OUTPUT_EUT,
                    HeavySteamTurbineCycle.STEEL_EFFICIENCY_PERCENT))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.heavy_turbine.output"))
            .beginStructureBlock(12, 10, 8, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.heavy_turbine.controller"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.heavy_turbine.ports"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.heavy_turbine.ports"), 1)
            .addDynamoHatch(StatCollector.translateToLocal("gtsr.tooltip.heavy_turbine.ports"), 1)
            .toolTipFinisher();
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        nbt.setInteger("energyCredit", energyCredit);
        nbt.setInteger("paidSteam", paidSteam);
        nbt.setInteger("excessWater", excessWater);
        nbt.setInteger("pendingWater", pendingWater);
        nbt.setInteger("materialTier", mMaterialTier);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        energyCredit = nbt.getInteger("energyCredit");
        paidSteam = nbt.getInteger("paidSteam");
        excessWater = nbt.getInteger("excessWater");
        pendingWater = nbt.getInteger("pendingWater");
        mMaterialTier = nbt.getInteger("materialTier");
    }

    @Override
    public MachineVisualState visualState() {
        return machineVisualState;
    }

    @Override
    public MachineVisualDefinition visualDefinition() {
        return MachineVisualDefinition.get("heavy_steam_turbine");
    }

    @Override
    public boolean needsClientTick() {
        return true;
    }

    @Override
    protected void incrementProgressTime() {
        super.incrementProgressTime();
        machineVisualState.advanced(getBaseMetaTileEntity().getTimer());
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        machineVisualState.observe(this, tick, mMachine, mProgresstime, mMaxProgresstime, true);
    }
}
