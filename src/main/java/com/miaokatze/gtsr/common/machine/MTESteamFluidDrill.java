package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.PressureSteamInputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.VoidProtectionHelper;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTESteamFluidDrill extends MTESteamMultiBlockBase<MTESteamFluidDrill> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 2;
    private static final int VERTICAL_OFF_SET = 5;
    private static final int DEPTH_OFF_SET = 0;

    private static final int BRONZE_MIN_OUTPUT = 200;
    private static final int BRONZE_MAX_OUTPUT = 2_000;
    private static final int STEEL_MIN_OUTPUT = 200;
    private static final int STEEL_MAX_OUTPUT = 8_000;
    private static final int BASE_STEAM_PER_SECOND = 1_000;
    private static final int PROGRESSION_TIME_TICKS = 20;

    private static IStructureDefinition<MTESteamFluidDrill> STRUCTURE_DEFINITION = null;

    private int mCountCasing = 0;
    public int mSetTier = -1;
    public int mOutputMode = 0;

    public MTESteamFluidDrill(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESteamFluidDrill(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamFluidDrill(mName);
    }

    @Override
    public String getMachineType() {
        return "蒸汽流体钻";
    }

    @Override
    public boolean isHighPressure() {
        return mSetTier >= 2;
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
        return null;
    }

    @Nullable
    public static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 12) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 2;
        return null;
    }

    @Nullable
    public static Integer getGearTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 2) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 3) return 2;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        return null;
    }

    private int getCasingTextureID() {
        if (mSetTier == 2) {
            return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    @Override
    protected void updateHatchTexture() {
        super.updateHatchTexture();
        int textureID = getCasingTextureID();
        for (MTEHatch h : mOutputHatches) h.updateTexture(textureID);
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mSetTier = aValue;
    }

    @Override
    public byte getUpdateData() {
        return (byte) mSetTier;
    }

    @Override
    public IStructureDefinition<MTESteamFluidDrill> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTESteamFluidDrill>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "     ", "     ", "  E  ", "     ", "     " },
                            { "     ", "     ", "  E  ", "     ", "     " },
                            { "     ", "  E  ", " ECE ", "  E  ", "     " },
                            { "     ", "  E  ", " ECE ", "  E  ", "     " },
                            { "     ", "  D  ", " DCD ", "  D  ", "     " },
                            { "  ~  ", " BBB ", "BBCBB", " BBB ", "  B  " } }))
                .addElement(
                    'B',
                    ofChain(
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        onElementPass(
                            MTESteamFluidDrill::onCasingAdded,
                            ofBlocksTiered(
                                MTESteamFluidDrill::getCasingTier,
                                ImmutableList.of(
                                    Pair.of(GregTechAPI.sBlockCasings1, 10),
                                    Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                -1,
                                (MTESteamFluidDrill t, Integer tier) -> t.mSetTier = tier,
                                (MTESteamFluidDrill t) -> t.mSetTier)),
                        // Use atLeast(PressureSteamInputHatch) instead of hatchIds(...). Its mteBlacklist()
                        // excludes MTEHatchPressureSteamInput.class so NEI does not render it on casing positions.
                        buildHatchAdder(MTESteamFluidDrill.class).atLeast(PressureSteamInputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTESteamFluidDrill.class).atLeast(OutputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build()))
                .addElement(
                    'C',
                    onElementPass(
                        MTESteamFluidDrill::onCasingAdded,
                        ofBlocksTiered(
                            MTESteamFluidDrill::getPipeTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                            -1,
                            (MTESteamFluidDrill t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTESteamFluidDrill t) -> t.mSetTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTESteamFluidDrill::onCasingAdded,
                        ofBlocksTiered(
                            MTESteamFluidDrill::getGearTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                            -1,
                            (MTESteamFluidDrill t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTESteamFluidDrill t) -> t.mSetTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTESteamFluidDrill::onCasingAdded,
                        ofBlocksTiered(
                            MTESteamFluidDrill::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTESteamFluidDrill t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTESteamFluidDrill t) -> t.mSetTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {
        mCountCasing++;
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
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mCountCasing = 0;
        mSetTier = -1;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }

        if (this.mOutputHatches.size() != 1 || this.mSteamInputFluids.size() != 1) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        if (mSetTier <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        updateHatchTexture();
    }

    private static final int HIGH_STEAM_PER_SECOND = 4_000;

    private int getSteamPerSecond() {
        return (mOutputMode == 2 || mOutputMode == 3) ? HIGH_STEAM_PER_SECOND : BASE_STEAM_PER_SECOND;
    }

    private int getEfficiencyIncrease() {
        return mSetTier == 1 ? 33 : 3;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    private float getEfficiencyRatio() {
        return mEfficiency / 10000F;
    }

    public int calculateFinalWaterOutput() {
        float ratio = getEfficiencyRatio();
        int minOutput, maxOutput;
        if (mSetTier == 1) {
            minOutput = BRONZE_MIN_OUTPUT;
            maxOutput = BRONZE_MAX_OUTPUT;
        } else {
            minOutput = STEEL_MIN_OUTPUT;
            maxOutput = STEEL_MAX_OUTPUT;
        }
        int output = (int) (minOutput + ratio * (maxOutput - minOutput));
        if (mOutputMode == 1 || mOutputMode == 2) {
            output = (int) (output * 0.1);
        } else if (mOutputMode == 3) {
            output = (int) (output * 0.005);
        }
        return output;
    }

    private FluidStack[] getOutputFluid(int amount) {
        switch (mOutputMode) {
            case 1:
                return new FluidStack[] { GTModHandler.getDistilledWater(amount) };
            case 2:
                return new FluidStack[] { Materials.SaltWater.getFluid(amount) };
            case 3:
                return new FluidStack[] {
                    new FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("lava"), amount) };
            default:
                return new FluidStack[] { Materials.Water.getFluid(amount) };
        }
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (mSetTier < 2) {
            GTUtility.sendChatToPlayer(
                aPlayer,
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_bronze_locked"));
            return;
        }
        mOutputMode = (mOutputMode + 1) % 4;
        mEfficiency = 0;
        String modeText;
        switch (mOutputMode) {
            case 1:
                modeText = StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_distilled");
                break;
            case 2:
                modeText = StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_brine");
                break;
            case 3:
                modeText = StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_lava");
                break;
            default:
                modeText = StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_water");
                break;
        }
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.output_mode") + ": " + modeText);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        int waterOutput = calculateFinalWaterOutput();

        VoidProtectionHelper voidProtection = new VoidProtectionHelper().setMachine(this)
            .setFluidOutputs(getOutputFluid(waterOutput))
            .build();

        if (voidProtection.isFluidFull()) {
            mOutputFluids = null;
            mMaxProgresstime = 0;
            return CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
        }

        if (getTotalSteamStored() >= getSteamPerSecond()) {
            mMaxProgresstime = PROGRESSION_TIME_TICKS;
            mEfficiencyIncrease = getEfficiencyIncrease();
            tryConsumeSteam(getSteamPerSecond());
            mOutputFluids = getOutputFluid(waterOutput);
            updateSlots();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        mEfficiency = 0;
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    public int getTierRecipes() {
        return 0;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.steamFluidDrillRecipes;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    protected IIconContainer getInactiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_WATER_PUMP;
    }

    @Override
    protected IIconContainer getActiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_WATER_PUMP_ACTIVE;
    }

    // beta-2 兼容：MTESteamMultiBlockBase 将 getActiveGlowOverlay/getInactiveGlowOverlay 改为 abstract
    // 当前返回 null（与 beta-1 默认行为等价，无发光层），后续可补充发光纹理资源
    @Override
    protected IIconContainer getActiveGlowOverlay() {
        return null;
    }

    @Override
    protected IIconContainer getInactiveGlowOverlay() {
        return null;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.desc2"))
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_switch"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_penalty"))
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_bronze_only"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.output_rates"))
            .addInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.rate_water"))
            .addInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.rate_distilled_brine"))
            .addInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.rate_lava"))
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.high_steam_cost"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + " "
                    + NumberFormatUtil.formatNumber(BASE_STEAM_PER_SECOND)
                    + " L/s ("
                    + NumberFormatUtil.formatNumber(HIGH_STEAM_PER_SECOND)
                    + " L/s Brine/Lava)")
            .addInfo(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.superheated_quadruples"))
            .beginStructureBlock(5, 6, 5, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.output_hatch"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + "Bronze"
                    + EnumChatFormatting.DARK_PURPLE
                    + "/"
                    + EnumChatFormatting.BLUE
                    + "Steel "
                    + EnumChatFormatting.DARK_PURPLE
                    + "Tier")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 11, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 10, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.fluid_drill.hint_bronze")
            .toolTipFinisher(
                EnumChatFormatting.AQUA + "GT"
                    + EnumChatFormatting.GREEN
                    + "-"
                    + EnumChatFormatting.GOLD
                    + "Steam"
                    + EnumChatFormatting.RED
                    + "-"
                    + EnumChatFormatting.BLUE
                    + "Reborn");
        return tt;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setInteger("mOutputMode", mOutputMode);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSetTier = aNBT.getInteger("mSetTier");
        mOutputMode = aNBT.getInteger("mOutputMode");
    }

    public boolean hasSuperheatedSteamInHatch() {
        for (MTEHatchCustomFluidBase hatch : mSteamInputFluids) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && fs.getFluid() != null
                && "ic2superheatedsteam".equals(
                    fs.getFluid()
                        .getName())
                && fs.amount > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mEfficiency, val -> mEfficiency = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mOutputMode, val -> mOutputMode = val))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                        + EnumChatFormatting.GOLD
                        + (mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                            : StatCollector.translateToLocal("gtsr.gui.tier.bronze"))))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                        + EnumChatFormatting.YELLOW
                        + (hasSuperheatedSteamInHatch()
                            ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                            : StatCollector.translateToLocal("gtsr.gui.steam_type.normal"))))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.fluid_drill.output_mode")
                        + EnumChatFormatting.LIGHT_PURPLE
                        + getOutputModeDisplayName()))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.fluid_drill.efficiency")
                        + EnumChatFormatting.GREEN
                        + String.format("%.1f%%", mEfficiency / 100F)))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.fluid_drill.output")
                        + EnumChatFormatting.LIGHT_PURPLE
                        + NumberFormatUtil.formatNumber(calculateFinalWaterOutput())
                        + " L/s"));
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new com.miaokatze.gtsr.common.gui.MTESteamFluidDrillGui(this);
    }

    public String getOutputModeDisplayName() {
        switch (mOutputMode) {
            case 1:
                return StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_distilled");
            case 2:
                return StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_brine");
            case 3:
                return StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_lava");
            default:
                return StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.mode_water");
        }
    }

    @Override
    public String[] getInfoData() {
        if (!mMachine) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.type"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building") };
        }
        float efficiencyPercent = mEfficiency / 100F;
        int currentOutput = calculateFinalWaterOutput();
        String tierText = mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
            : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
        String steamType = hasSuperheatedSteamInHatch()
            ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
            : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
        return new String[] { EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.fluid_drill.type"),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                + EnumChatFormatting.YELLOW
                + steamType,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.fluid_drill.output_mode")
                + EnumChatFormatting.LIGHT_PURPLE
                + getOutputModeDisplayName(),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.fluid_drill.efficiency")
                + EnumChatFormatting.GREEN
                + String.format("%.1f%%", efficiencyPercent),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.fluid_drill.output")
                + EnumChatFormatting.LIGHT_PURPLE
                + NumberFormatUtil.formatNumber(currentOutput)
                + " L/s" };
    }
}
