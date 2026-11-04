package com.miaokatze.gtsr.common.machine;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressEntry;
import com.miaokatze.gtsr.common.gui.MTEHeavySteamTurbineGui;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.machine.turbine.HeavySteamTurbineCycle;
import com.miaokatze.gtsr.common.machine.turbine.HeavySteamTurbineCycle.Settlement;
import com.miaokatze.gtsr.common.machine.turbine.HeavySteamTurbineStructureDef;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes.SteamType;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualState;

import gregtech.api.enums.Materials;
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

/** Independent, non-stacking turbine. All conversion arithmetic lives in HeavySteamTurbineCycle. */
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
    private int pendingSteam, turbineEfficiency, workingTicks, idleTicks, fullFlowTicks;
    private int actualOutput, actualSteam, effectiveEfficiency;
    private boolean superheated, workedThisTick, fullFlowThisTick;

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
        efficiencyDecrease = 0;
        registerProgressEntries();
    }

    public MTEHeavySteamTurbine(String name) {
        super(name);
        efficiencyDecrease = 0;
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
            GTSRProgressEntry
                .of(
                    "steam_efficiency",
                    "gtsr.gui.heavy_turbine.efficiency",
                    "%.0f%%",
                    EnumChatFormatting.GOLD,
                    () -> effectiveEfficiency)
                .showZero());
        registerEntry(
            GTSRProgressEntry
                .of(
                    "rotor_efficiency",
                    "gtsr.gui.heavy_turbine.rotor_efficiency",
                    "%.0f%%",
                    EnumChatFormatting.GOLD,
                    () -> turbineEfficiency)
                .showZero());
        registerEntry(
            "max_efficiency",
            "gtsr.gui.heavy_turbine.max_efficiency",
            "%.0f%%",
            EnumChatFormatting.GOLD,
            () -> HeavySteamTurbineCycle.efficiencyLimit(mMaterialTier, superheated));
        registerEntry(
            GTSRProgressEntry
                .of(
                    "actual_output",
                    "gtsr.gui.heavy_turbine.actual_output",
                    "%.0f EU/t",
                    EnumChatFormatting.AQUA,
                    () -> actualOutput)
                .showZero());
        registerEntry(
            GTSRProgressEntry
                .of(
                    "steam_rate",
                    "gtsr.gui.heavy_turbine.steam_rate",
                    "%.0f L/t",
                    EnumChatFormatting.WHITE,
                    () -> actualSteam)
                .showZero());
        registerEntry(
            "rated_steam",
            "gtsr.gui.heavy_turbine.rated_steam",
            "%.0f L/t",
            EnumChatFormatting.WHITE,
            () -> HeavySteamTurbineCycle.ratedSteam(mMaterialTier, superheated));
        registerEntry(
            GTSRProgressEntry
                .ofCustom(
                    "steam_type",
                    "gtsr.gui.steam_type",
                    EnumChatFormatting.WHITE,
                    () -> superheated ? 1 : 0,
                    value -> StatCollector
                        .translateToLocal(value == 1 ? SteamType.SH_STEAM.nameKey : SteamType.STEAM.nameKey))
                .showZero());
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

    private long outputRoom() {
        long room = 0;
        // The largest possible tick is twice the steel rating plus prepaid rounding energy.
        long needed = 2L * HeavySteamTurbineCycle.STEEL_OUTPUT_EUT + energyCredit / 200 + 1;
        for (MTEHatch hatch : dynamos()) {
            room += Math.min(needed - room, availableOutput(hatch));
            if (room == needed) break;
        }
        return room;
    }

    private int availableSteam(SteamType type, int limit) {
        if (type == SteamType.NONE) return 0;
        long available = type == SteamType.STEAM ? paidSteam : 0;
        for (FluidStack fluid : getStoredFluids()) {
            if (SteamTurbineSteamTypes.classifyFluid(fluid) == type) available += fluid.amount;
        }
        return (int) Math.min(limit, available);
    }

    private SteamType selectSteam() {
        // Old saves can own already withdrawn ordinary steam; settle it before switching fuels.
        if (paidSteam > 0) return SteamType.STEAM;
        if (mMaterialTier == 2 && availableSteam(SteamType.SH_STEAM, 1) > 0) return SteamType.SH_STEAM;
        return availableSteam(SteamType.STEAM, 1) > 0 ? SteamType.STEAM : SteamType.NONE;
    }

    private int consumeSteam(SteamType type, int required) {
        if (type == SteamType.NONE) return 0;
        int consumed = type == SteamType.STEAM ? Math.min(required, paidSteam) : 0;
        paidSteam -= consumed;
        for (FluidStack fluid : getStoredFluids()) {
            if (consumed >= required) break;
            if (SteamTurbineSteamTypes.classifyFluid(fluid) == type) {
                consumed += GTSRHatchFluidAccess
                    .depleteFluidAcross(mInputHatches, new FluidStack(fluid, required - consumed));
            }
        }
        return consumed;
    }

    private boolean flushCoolant() {
        int waterBefore = pendingWater, steamBefore = pendingSteam;
        for (var hatch : GTUtility.validMTEList(mOutputHatches)) {
            if (pendingWater > 0)
                pendingWater -= hatch.fill(ForgeDirection.UNKNOWN, GTModHandler.getDistilledWater(pendingWater), true);
            if (pendingSteam > 0)
                pendingSteam -= hatch.fill(ForgeDirection.UNKNOWN, Materials.Steam.getGas(pendingSteam), true);
        }
        if (waterBefore != pendingWater || steamBefore != pendingSteam) markDirty();
        return pendingWater == 0 && pendingSteam == 0;
    }

    private int coolantSteamRoom(boolean heated) {
        int rated = HeavySteamTurbineCycle.ratedSteam(mMaterialTier, heated);
        int required = heated ? rated : (rated + excessWater) / HeavySteamTurbineCycle.STEAM_PER_WATER;
        int room = 0;
        for (var hatch : GTUtility.validMTEList(mOutputHatches)) {
            int remaining = required - room;
            if (remaining == 0) break;
            room += hatch.fill(
                ForgeDirection.UNKNOWN,
                heated ? Materials.Steam.getGas(remaining) : GTModHandler.getDistilledWater(remaining),
                false);
        }
        return heated ? room : Math.min(rated, (room + 1) * HeavySteamTurbineCycle.STEAM_PER_WATER - 1 - excessWater);
    }

    private Settlement settlement(SteamType type, int steam, long room) {
        boolean heated = type == SteamType.SH_STEAM || (type == SteamType.NONE && superheated);
        return HeavySteamTurbineCycle
            .settle(mMaterialTier, heated, turbineEfficiency, energyCredit, steam, room, coolantSteamRoom(heated));
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (mMaterialTier < 1) return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        if (outputRoom() == 0 || !flushCoolant()) return CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
        // GT checks the next cycle in the very tick that just emptied a bounded input hatch.
        // Keep a successfully fueled tick continuous; the next running tick still charges real fuel.
        if (workedThisTick) {
            mEfficiencyIncrease = 0;
            mMaxProgresstime = 20;
            return CheckRecipeResultRegistry.GENERATING;
        }
        SteamType type = selectSteam();
        if (type == SteamType.NONE && energyCredit < HeavySteamTurbineCycle.ENERGY_UNITS_PER_EU)
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        Settlement plan = settlement(type, availableSteam(type, 4096), outputRoom());
        if (plan.steam() == 0 && plan.output() == 0) return CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
        mEfficiencyIncrease = 0;
        mMaxProgresstime = 20;
        return CheckRecipeResultRegistry.GENERATING;
    }

    @Override
    public boolean onRunningTick(ItemStack stack) {
        if (!getBaseMetaTileEntity().isAllowedToWork() || outputRoom() == 0 || !flushCoolant()) return pauseCycle();
        SteamType type = selectSteam();
        long room = outputRoom();
        Settlement plan = settlement(type, availableSteam(type, 4096), room);
        int consumed = consumeSteam(type, plan.steam());
        // Network withdrawals may return less than simulation. Charge and cool exactly the actual quantity.
        plan = settlement(type, consumed, room);
        if (consumed == 0 && plan.output() == 0) return pauseCycle();
        energyCredit = plan.credit();
        actualOutput = plan.output();
        actualSteam = consumed;
        effectiveEfficiency = plan.efficiency();
        mEUt = actualOutput;
        if (consumed > 0) {
            superheated = type == SteamType.SH_STEAM;
            workedThisTick = true;
            fullFlowThisTick = plan.fullFlow();
            if (superheated) pendingSteam += consumed;
            else {
                excessWater += consumed;
                pendingWater += excessWater / HeavySteamTurbineCycle.STEAM_PER_WATER;
                excessWater %= HeavySteamTurbineCycle.STEAM_PER_WATER;
            }
        }
        long remaining = actualOutput;
        for (MTEHatch hatch : dynamos()) {
            long amount = Math.min(remaining, availableOutput(hatch));
            if (amount > 0 && hatch.getBaseMetaTileEntity()
                .increaseStoredEnergyUnits(amount, false)) remaining -= amount;
            if (remaining == 0) break;
        }
        // A refused insertion remains prepaid energy rather than disappearing.
        energyCredit += (int) remaining * HeavySteamTurbineCycle.ENERGY_UNITS_PER_EU;
        actualOutput -= (int) remaining;
        mEUt = actualOutput;
        flushCoolant();
        return true;
    }

    private boolean pauseCycle() {
        mMaxProgresstime = 0;
        mProgresstime = 0;
        mEUt = 0;
        return false;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public int getMaxEfficiency(ItemStack stack) {
        return 20000;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public String[] getInfoData() {
        return new String[] { StatCollector.translateToLocal("gt.blockmachines.gtsr.heavy.steam.turbine.name"),
            StatCollector.translateToLocal("gtsr.gui.heavy_turbine.actual_output") + actualOutput + " EU/t",
            StatCollector.translateToLocal("gtsr.gui.heavy_turbine.efficiency") + effectiveEfficiency + "%",
            StatCollector.translateToLocal("gtsr.gui.heavy_turbine.rotor_efficiency") + turbineEfficiency + "%" };
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setLong("energyUsage", -actualOutput);
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
                    HeavySteamTurbineCycle.BRONZE_OUTPUT_EUT))
            .addInfo(
                StatCollector.translateToLocalFormatted(
                    "gtsr.tooltip.heavy_turbine.steel",
                    HeavySteamTurbineCycle.STEEL_OUTPUT_EUT))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.heavy_turbine.startup"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.heavy_turbine.partial"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.heavy_turbine.cooling"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.heavy_turbine.pause"))
            .addSeparator()
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
        nbt.setInteger("pendingSteam", pendingSteam);
        nbt.setInteger("turbineEfficiency", turbineEfficiency);
        nbt.setInteger("workingTicks", workingTicks);
        nbt.setInteger("idleTicks", idleTicks);
        nbt.setInteger("fullFlowTicks", fullFlowTicks);
        nbt.setBoolean("superheated", superheated);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        energyCredit = nbt.getInteger("energyCredit");
        paidSteam = nbt.getInteger("paidSteam");
        excessWater = nbt.getInteger("excessWater");
        pendingWater = nbt.getInteger("pendingWater");
        mMaterialTier = nbt.getInteger("materialTier");
        pendingSteam = nbt.getInteger("pendingSteam");
        turbineEfficiency = nbt.hasKey("turbineEfficiency") ? nbt.getInteger("turbineEfficiency")
            : Math.min(100, nbt.getInteger("mEfficiency") / 100);
        workingTicks = nbt.getInteger("workingTicks");
        idleTicks = nbt.getInteger("idleTicks");
        fullFlowTicks = nbt.getInteger("fullFlowTicks");
        superheated = nbt.getBoolean("superheated");
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
        workedThisTick = false;
        fullFlowThisTick = false;
        actualOutput = 0;
        actualSteam = 0;
        effectiveEfficiency = 0;
        super.onPostTick(tile, tick);
        if (tile.isServerSide()) {
            int previousEfficiency = turbineEfficiency;
            if (workedThisTick) {
                idleTicks = 0;
                if (turbineEfficiency < 100) {
                    fullFlowTicks = 0;
                    if (++workingTicks == HeavySteamTurbineCycle.TICKS_PER_SECOND) {
                        turbineEfficiency = HeavySteamTurbineCycle.warmUp(
                            turbineEfficiency,
                            HeavySteamTurbineCycle.efficiencyLimit(mMaterialTier, superheated),
                            false);
                        workingTicks = 0;
                    }
                } else {
                    workingTicks = 0;
                    fullFlowTicks = fullFlowThisTick ? fullFlowTicks + 1 : 0;
                    if (fullFlowTicks == HeavySteamTurbineCycle.TICKS_PER_SECOND) {
                        turbineEfficiency = HeavySteamTurbineCycle.warmUp(
                            turbineEfficiency,
                            HeavySteamTurbineCycle.efficiencyLimit(mMaterialTier, superheated),
                            true);
                        fullFlowTicks = 0;
                    }
                }
            } else {
                workingTicks = 0;
                fullFlowTicks = 0;
                if (++idleTicks == HeavySteamTurbineCycle.TICKS_PER_SECOND) {
                    turbineEfficiency = HeavySteamTurbineCycle.coolDown(turbineEfficiency);
                    idleTicks = 0;
                }
            }
            mEfficiency = effectiveEfficiency * 100;
            mEfficiencyIncrease = 0;
            if (turbineEfficiency != previousEfficiency) markDirty();
        }
        machineVisualState.observe(this, tick, mMachine, mProgresstime, mMaxProgresstime, true);
    }
}
