package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamOutputHatch;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;

public class MTELargeSolarOverpressureArray extends MTEEnhancedMultiBlockBase<MTELargeSolarOverpressureArray>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 5;
    private static final int VERTICAL_OFF_SET = 0;
    private static final int DEPTH_OFF_SET = 0;

    private static IStructureDefinition<MTELargeSolarOverpressureArray> STRUCTURE_DEFINITION = null;
    private static final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    static {
        numberFormat.setMinimumFractionDigits(3);
        numberFormat.setMaximumFractionDigits(3);
    }

    protected int mSetTier = -1;
    protected double mHeat = 0.0d;
    protected double mCalcification = 0.0d;
    protected long mRunningTicks = 0L;
    protected boolean mIsHeating = false;
    protected boolean mIsOperating = false;
    protected int tierCasing = -1;
    protected int tierGlass = -1;
    protected int tierConductor = -1;

    private static final int CALCIFICATION_FACTOR = 3;
    private static final int STEAM_PER_WATER = 160;

    private static Textures.BlockIcons.CustomIcon OVERLAY_OFF;
    private static Textures.BlockIcons.CustomIcon OVERLAY_ON;

    public MTELargeSolarOverpressureArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTELargeSolarOverpressureArray(String aName) {
        super(aName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = new Textures.BlockIcons.CustomIcon("gtsr:MTELargeSolarOverpressureArray_OFF");
        OVERLAY_ON = new Textures.BlockIcons.CustomIcon("gtsr:MTELargeSolarOverpressureArray_ON");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeSolarOverpressureArray(mName);
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return false;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return false;
    }

    public int getTier() {
        return mSetTier;
    }

    public boolean isBronze() {
        return mSetTier == 1;
    }

    public boolean isSteel() {
        return mSetTier == 2;
    }

    public boolean isNickel() {
        return mSetTier == 3;
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
        return null;
    }

    @Nullable
    public static Integer getConductorTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockMetal6 && meta == 10) return 1;
        if (block == Blocks.gold_block && meta == 0) return 2;
        if (block == GregTechAPI.sBlockMetal5 && meta == 4) return 3;
        return null;
    }

    @Nullable
    public static Integer getGlassTier(Block block, int meta) {
        if (block == Blocks.glass) return 1;
        if (block == GregTechAPI.sBlockGlass1) return 2;
        return null;
    }

    protected int getCasingTextureID() {
        if (mSetTier == 2 || mSetTier == 3) {
            return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    protected void updateHatchTextures() {
        if (mSetTier <= 0) return;
        int textureID = getCasingTextureID();

        for (IMetaTileEntity hatch : mOutputHatches) {
            if (hatch instanceof gregtech.api.metatileentity.implementations.MTEHatch h) {
                h.updateTexture(textureID);
            }
        }

        if (mSetTier >= 2) {
            for (IMetaTileEntity hatch : mInputHatches) {
                if (hatch instanceof gregtech.api.metatileentity.implementations.MTEHatch h) {
                    h.updateTexture(textureID);
                }
            }
        }
    }

    @Override
    public IStructureDefinition<MTELargeSolarOverpressureArray> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTELargeSolarOverpressureArray>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { "CCCCC~CCCCC", "CGGGGGGGGGC", "CGGGGGGGGGC", "CGGGGGGGGGC", "CGGGGGGGGGC", "CGGGGGGGGGC",
                                "CGGGGGGGGGC", "CGGGGGGGGGC", "CGGGGGGGGGC", "CGGGGGGGGGC", "CCCCCCCCCCC" },
                            { "CCCCCCCCCCC", "C         C", "C         C", "C         C", "C         C", "C         C",
                                "C         C", "C         C", "C         C", "C         C", "CCCCCCCCCCC" },
                            { "CCCCCCCCCCC", "CDDDDDDDDDC", "CDDDDDDDDDC", "CDDDDDDDDDC", "CDDDDDDDDDC", "CDDDDDDDDDC",
                                "CDDDDDDDDDC", "CDDDDDDDDDC", "CDDDDDDDDDC", "CDDDDDDDDDC", "CCCCCCCCCCC" },
                            { "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC",
                                "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC" } }))
                .addElement(
                    'C',
                    buildHatchAdder(MTELargeSolarOverpressureArray.class).atLeast(OutputHatch, InputHatch)
                        .casingIndex(bronzeCasingIndex)
                        .dot(1)
                        .buildAndChain(
                            onElementPass(
                                t -> {},
                                ofBlocksTiered(
                                    MTELargeSolarOverpressureArray::getCasingTier,
                                    ImmutableList.of(
                                        Pair.of(GregTechAPI.sBlockCasings1, 10),
                                        Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                    -1,
                                    (MTELargeSolarOverpressureArray t, Integer tier) -> t.tierCasing = tier,
                                    (MTELargeSolarOverpressureArray t) -> t.tierCasing))))
                .addElement(
                    'G',
                    ofBlocksTiered(
                        MTELargeSolarOverpressureArray::getGlassTier,
                        ImmutableList.of(
                            Pair.of(Blocks.glass, 0),
                            Pair.of(GregTechAPI.sBlockGlass1, 0),
                            Pair.of(GregTechAPI.sBlockGlass1, 1),
                            Pair.of(GregTechAPI.sBlockGlass1, 2),
                            Pair.of(GregTechAPI.sBlockGlass1, 3)),
                        -1,
                        (MTELargeSolarOverpressureArray t, Integer tier) -> t.tierGlass = tier,
                        (MTELargeSolarOverpressureArray t) -> t.tierGlass))
                .addElement(
                    'D',
                    ofBlocksTiered(
                        MTELargeSolarOverpressureArray::getConductorTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockMetal6, 10),
                            Pair.of(Blocks.gold_block, 0),
                            Pair.of(GregTechAPI.sBlockMetal5, 4)),
                        -1,
                        (MTELargeSolarOverpressureArray t, Integer tier) -> t.tierConductor = tier,
                        (MTELargeSolarOverpressureArray t) -> t.tierConductor))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mSetTier = -1;
        tierCasing = -1;
        tierGlass = -1;
        tierConductor = -1;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) return false;

        if (tierCasing == 1 && tierGlass >= 1 && tierConductor == 1) {
            mSetTier = 1;
        } else if (tierCasing == 2 && tierGlass >= 1 && tierConductor == 2) {
            mSetTier = 2;
        } else if (tierCasing == 2 && tierGlass >= 1 && tierConductor == 3) {
            mSetTier = 3;
        }

        if (mSetTier <= 0) return false;
        if (!hasValidOutputHatchesForTier()) return false;

        updateHatchTextures();
        return true;
    }

    private boolean hasValidOutputHatchesForTier() {
        return !mOutputHatches.isEmpty();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (!mMachine || mSetTier <= 0) return;

        if (!aBaseMetaTileEntity.isAllowedToWork()) {
            mIsOperating = false;
            mCurrentSteamOutput = 0;
        }

        World world = aBaseMetaTileEntity.getWorld();
        boolean isClearWeather = !world.isRaining() && !world.isThundering()
            || aBaseMetaTileEntity.getBiome().rainfall == 0.0F;
        boolean isSeeSky = aBaseMetaTileEntity.getSkyAtSide(ForgeDirection.UP);
        boolean isDay = world.isDaytime();

        if (aTick % 20 == 0) {
            boolean canHeat = isClearWeather && isSeeSky && isDay;
            boolean wasHeating = mIsHeating;

            if (canHeat && aBaseMetaTileEntity.isAllowedToWork()) {
                mHeat += getHeatIncreaseSpeed();
                if (mHeat > 1.0d) mHeat = 1.0d;
                mIsHeating = true;
            } else {
                mHeat -= getHeatDecreaseSpeed();
                if (mHeat < 0) mHeat = 0;
                mIsHeating = false;
            }

            if (wasHeating != mIsHeating) {
                aBaseMetaTileEntity.issueClientUpdate();
            }
        }

        if (mMaxProgresstime > 0) {
            if (++mProgresstime >= mMaxProgresstime) {
                mProgresstime = 0;
                outputSteam();
            }
        } else {
            checkProcessing();
        }
    }

    @Nonnull
    @Override
    public CheckRecipeResult checkProcessing() {
        if (!mMachine || mSetTier <= 0 || !getBaseMetaTileEntity().isAllowedToWork()) {
            mIsOperating = false;
            mCurrentSteamOutput = 0;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        ArrayList<FluidStack> storedFluids = super.getStoredFluids();

        boolean hasWaterInSystem = false;
        for (FluidStack hatchFluid : storedFluids) {
            FluidStack waterFluid = GTModHandler.getWater(1);
            FluidStack distilledWaterFluid = GTModHandler.getDistilledWater(1);

            boolean hasWater = hatchFluid.isFluidEqual(waterFluid);
            boolean hasDistilledWater = hatchFluid.isFluidEqual(distilledWaterFluid);

            if (hasWater || hasDistilledWater) {
                hasWaterInSystem = true;
                int amountOfFluidInHatch = hatchFluid.amount;

                long calcificationDelayTicks = getCalcificationDelayTicks();
                long calcificationInterval = getCalcificationFullTime() / 100;

                if (mRunningTicks > calcificationDelayTicks && (mRunningTicks / 20) % calcificationInterval == 0
                    && hasWater
                    && !hasDistilledWater) {
                    mCalcification += 0.01d;
                    if (mCalcification > 1.0d) mCalcification = 1.0d;
                }

                if (amountOfFluidInHatch > 0 && mHeat > 0.01d) {
                    float solarBooster = calculateSolarBooster();
                    int baseProduction = (int) (getBaseSteamProduction() * solarBooster);

                    int consumedWater = (int) (Math.min(amountOfFluidInHatch, baseProduction / STEAM_PER_WATER) * mHeat
                        / ((mCalcification * (CALCIFICATION_FACTOR - 1)) + 1));

                    if (consumedWater <= 0) continue;

                    FluidStack liquidToDeplete;
                    if (hasDistilledWater) {
                        liquidToDeplete = GTModHandler.getDistilledWater(consumedWater);
                    } else {
                        liquidToDeplete = GTModHandler.getWater(consumedWater);
                    }

                    if (super.depleteInput(liquidToDeplete)) {
                        int steamAmount = consumedWater * STEAM_PER_WATER;
                        mRunningTicks += 20;
                        mCurrentSteamOutput = steamAmount;

                        FluidStack outputSteam;
                        if (isNickel()) {
                            outputSteam = FluidRegistry.getFluidStack("ic2superheatedsteam", steamAmount);
                        } else {
                            outputSteam = Materials.Steam.getGas(steamAmount);
                        }

                        super.mOutputFluids = new FluidStack[] { outputSteam };
                        super.mMaxProgresstime = 20;
                        super.mEfficiency = getMaxEfficiency(null);
                        mIsOperating = true;

                        return CheckRecipeResultRegistry.SUCCESSFUL;
                    }
                }
            }
        }

        if (!hasWaterInSystem) {
            mIsOperating = false;
            mCurrentSteamOutput = 0;
        }
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    private void outputSteam() {
        if (super.mOutputFluids != null && super.mOutputFluids.length > 0) {
            FluidStack outputSteam = super.mOutputFluids[0];
            if (outputSteam != null && outputSteam.amount > 0) {
                if (isNickel()) {
                    distributeSuperheatedSteamToOutputHatches(outputSteam.amount);
                } else {
                    distributeSteamToOutputHatches(outputSteam.amount);
                }
            }
        }
        super.mOutputFluids = null;
        mMaxProgresstime = 0;
        checkProcessing();
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.large_solar_overpressure_array.name"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.0"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.1") + getBaseSteamProduction() + " L/s")
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.2"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.3"))
            .beginStructureBlock(11, 4, 3, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.solar_array.ctrl"))
            .addCasingInfoRange(StatCollector.translateToLocal("gtsr.tooltip.solar_array.casing"), 60, -1, false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.solar_array.glass"),
                StatCollector.translateToLocal("gtsr.tooltip.solar_array.glass_pos"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.solar_array.conductor"),
                StatCollector.translateToLocal("gtsr.tooltip.solar_array.conductor_pos"))
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.solar_array.output_hatch"), 1)
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.solar_array.input_hatch"), 1)
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side == facing) {
            ITexture frontOverlay = mIsHeating ? TextureFactory.of(OVERLAY_ON) : TextureFactory.of(OVERLAY_OFF);
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()), frontOverlay };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.solar_array.heat")
                        + EnumChatFormatting.GOLD
                        + numberFormat.format(mHeat * 100)
                        + "% "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal("gtsr.gui.solar_array.calcification")
                        + EnumChatFormatting.RED
                        + numberFormat.format(mCalcification * 100)
                        + "% "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.solar_array.steam_output")
                        + EnumChatFormatting.AQUA
                        + GTUtility.formatNumbers(mCurrentSteamOutput)
                        + " L/s "
                        + EnumChatFormatting.WHITE
                        + (isNickel() ? StatCollector.translateToLocal("gtsr.gui.solar_array.superheated")
                            : StatCollector.translateToLocal("gtsr.gui.solar_array.steam"))
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal("gtsr.gui.solar_array.solar_booster")
                        + EnumChatFormatting.GREEN
                        + numberFormat.format(calculateSolarBooster() * 100)
                        + "% "
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mHeat, val -> mHeat = val))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mCalcification, val -> mCalcification = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentSteamOutput, val -> mCurrentSteamOutput = val));
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        super.addUIWidgets(builder, buildContext);

        builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> {
            if (clickData.mouseButton == 0) {
                mCalcification = 0;
                mRunningTicks = 0;
            }
        })
            .setPlayClickSound(true)
            .setBackground(
                () -> new IDrawable[] { GTUITextures.BUTTON_STANDARD,
                    GTUITextures.OVERLAY_BUTTON_MACHINEMODE_WASHPLANT })
            .addTooltip(
                EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.solar_array.clear_calcification")
                    + EnumChatFormatting.RESET)
            .setTooltipShowUpDelay(TOOLTIP_DELAY)
            .setPos(new Pos2d(174, 91))
            .setSize(16, 16));
    }

    private float calculateSolarBooster() {
        float booster = 1.0f;

        ItemStack stack = getControllerSlot();
        if (stack != null) {
            if (ItemList.Machine_HP_Solar.isStackEqual(stack, false, false)) {
                booster += 2.0f * Math.min(stack.stackSize, 64) / 64.0f;
            } else if (ItemList.Machine_Bronze_Boiler_Solar.isStackEqual(stack, false, false)) {
                booster += 1.0f * Math.min(stack.stackSize, 64) / 64.0f;
            }
        }

        return Math.min(booster, 3.0f);
    }

    private void distributeSteamToOutputHatches(int totalSteam) {
        List<IMetaTileEntity> validHatches = new ArrayList<>();

        for (IMetaTileEntity hatch : mOutputHatches) {
            if (hatch instanceof MTESteamOutputHatch || hatch instanceof MTEPressureSteamOutputHatch) {
                validHatches.add(hatch);
            }
        }

        if (validHatches.isEmpty()) return;
        int perHatch = totalSteam / validHatches.size();
        int remainder = totalSteam % validHatches.size();

        for (int i = 0; i < validHatches.size(); i++) {
            IMetaTileEntity hatch = validHatches.get(i);
            int amount = perHatch + (i < remainder ? 1 : 0);

            if (amount > 0) {
                FluidStack steam = Materials.Steam.getGas(amount);
                if (hatch instanceof MTESteamOutputHatch) {
                    ((MTESteamOutputHatch) hatch).fill(ForgeDirection.UNKNOWN, steam, true);
                } else if (hatch instanceof MTEPressureSteamOutputHatch) {
                    ((MTEPressureSteamOutputHatch) hatch).fill(ForgeDirection.UNKNOWN, steam, true);
                }
            }
        }
    }

    private void distributeSuperheatedSteamToOutputHatches(int totalSuperheatedSteam) {
        List<MTEPressureSteamOutputHatch> pressureHatches = new ArrayList<>();

        for (IMetaTileEntity hatch : mOutputHatches) {
            if (hatch instanceof MTEPressureSteamOutputHatch) {
                pressureHatches.add((MTEPressureSteamOutputHatch) hatch);
            }
        }

        if (pressureHatches.isEmpty()) return;
        int perHatch = totalSuperheatedSteam / pressureHatches.size();
        int remainder = totalSuperheatedSteam % pressureHatches.size();

        for (int i = 0; i < pressureHatches.size(); i++) {
            MTEPressureSteamOutputHatch hatch = pressureHatches.get(i);
            int amount = perHatch + (i < remainder ? 1 : 0);

            if (amount > 0) {
                FluidStack superheatedSteam = FluidRegistry.getFluidStack("ic2superheatedsteam", amount);
                if (superheatedSteam != null) {
                    hatch.fill(ForgeDirection.UNKNOWN, superheatedSteam, true);
                }
            }
        }
    }

    private int getBaseSteamProduction() {
        switch (mSetTier) {
            case 3:
            case 2:
                return 30000;
            case 1:
            default:
                return 12000;
        }
    }

    private double getHeatIncreaseSpeed() {
        switch (mSetTier) {
            case 1:
                return 0.00015d;
            case 2:
                return 0.00012d;
            case 3:
                return 0.00005d;
            default:
                return 0.00015d;
        }
    }

    private double getHeatDecreaseSpeed() {
        switch (mSetTier) {
            case 1:
                return 0.0001d;
            case 2:
                return 0.00008d;
            case 3:
                return 0.00004d;
            default:
                return 0.0001d;
        }
    }

    private long getCalcificationDelayTicks() {
        switch (mSetTier) {
            case 1:
                return 12L * 3600 * 20;
            case 2:
                return 6L * 3600 * 20;
            case 3:
                return 4L * 3600 * 20;
            default:
                return 12L * 3600 * 20;
        }
    }

    private long getCalcificationFullTime() {
        switch (mSetTier) {
            case 1:
                return 24L * 3600 * 20;
            case 2:
                return 12L * 3600 * 20;
            case 3:
                return 8L * 3600 * 20;
            default:
                return 24L * 3600 * 20;
        }
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            true,
            true);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setDouble("mHeat", mHeat);
        aNBT.setDouble("mCalcification", mCalcification);
        aNBT.setLong("mRunningTicks", mRunningTicks);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSetTier = aNBT.getInteger("mSetTier");
        mHeat = aNBT.getDouble("mHeat");
        mCalcification = aNBT.getDouble("mCalcification");
        mRunningTicks = aNBT.getLong("mRunningTicks");
    }

    @Override
    public void onValueUpdate(byte aValue) {
        boolean oldHeating = mIsHeating;
        int oldTier = mSetTier;
        mIsHeating = (aValue & 0x01) != 0;
        mSetTier = (aValue >> 1) & 0x0F;
        if (oldHeating != mIsHeating || oldTier != mSetTier) {
            getBaseMetaTileEntity().issueTextureUpdate();
        }
    }

    @Override
    public byte getUpdateData() {
        return (byte) ((mSetTier << 1) | (mIsHeating ? 0x01 : 0x00));
    }

    @Override
    public String[] getDescription() {
        List<String> description = new ArrayList<>();

        description.add(
            EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("gtsr.tooltip.large_solar_overpressure_array.name"));
        description.add(
            EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.solar_array.1")
                + getBaseSteamProduction()
                + " L/s");
        description.add(
            isNickel() ? EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.solar_array.superheated")
                : EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.solar_array.steam"));

        return description.toArray(new String[0]);
    }

    protected int mCurrentSteamOutput = 0;

    @Override
    public String[] getInfoData() {
        String tierText = isNickel() ? StatCollector.translateToLocal("gtsr.info.solar_array.tier_nickel")
            : isSteel() ? StatCollector.translateToLocal("gtsr.info.solar_array.tier_steel")
                : isBronze() ? StatCollector.translateToLocal("gtsr.info.solar_array.tier_bronze") : "N/A";
        String steamType = isNickel() ? StatCollector.translateToLocal("gtsr.gui.solar_array.superheated")
            : StatCollector.translateToLocal("gtsr.gui.solar_array.steam");
        float booster = calculateSolarBooster();
        return new String[] { EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.info.solar_array.name"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.info.solar_array.tier")
                + EnumChatFormatting.GOLD
                + tierText,
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.info.solar_array.status")
                + (mIsOperating
                    ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.info.solar_array.running")
                    : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.info.solar_array.stopped")),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.solar_array.heat")
                + EnumChatFormatting.YELLOW
                + numberFormat.format(mHeat * 100)
                + "%",
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.solar_array.calcification")
                + EnumChatFormatting.RED
                + numberFormat.format(mCalcification * 100)
                + "%",
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.solar_array.steam_output")
                + EnumChatFormatting.AQUA
                + GTUtility.formatNumbers(mCurrentSteamOutput)
                + " L/s "
                + EnumChatFormatting.WHITE
                + steamType,
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.solar_array.solar_booster")
                + EnumChatFormatting.GREEN
                + numberFormat.format(booster * 100)
                + "%" };
    }
}
