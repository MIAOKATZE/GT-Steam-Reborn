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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
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
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.objects.overclockdescriber.OverclockDescriber;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTEAirCompressor extends MTESteamMultiBlockBase<MTEAirCompressor> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 2;
    private static final int DEPTH_OFF_SET = 0;

    private static IStructureDefinition<MTEAirCompressor> STRUCTURE_DEFINITION = null;

    public int mSetTier = -1;
    protected int mCasingCount = 0;

    public MTEAirCompressor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEAirCompressor(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEAirCompressor(mName);
    }

    @Override
    public String getMachineType() {
        return "空气压缩机";
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

    protected int getCasingTextureID() {
        if (mSetTier == 2) {
            return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    protected void updateHatchTexture() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mSteamInputFluids) h.updateTexture(textureID);
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
    public IStructureDefinition<MTEAirCompressor> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTEAirCompressor>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "EBE", "CDC", "BBB", "CDC", "EBE" }, { "EBE", "CDC", "B B", "CDC", "EBE" },
                            { "E~E", "CDC", "B B", "CDC", "EBE" }, { "EBE", "BBB", "BBB", "BBB", "EBE" } }))
                .addElement(
                    'B',
                    ofChain(
                        // Use atLeast(PressureSteamInputHatch) instead of hatchIds(...). Its mteBlacklist()
                        // excludes MTEHatchPressureSteamInput.class so NEI does not render it on casing positions.
                        buildHatchAdder(MTEAirCompressor.class).atLeast(PressureSteamInputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTEAirCompressor.class).atLeast(OutputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build(),
                        onElementPass(
                            MTEAirCompressor::onCasingAdded,
                            ofBlocksTiered(
                                MTEAirCompressor::getCasingTier,
                                ImmutableList.of(
                                    Pair.of(GregTechAPI.sBlockCasings1, 10),
                                    Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                -1,
                                (MTEAirCompressor t, Integer tier) -> t.mSetTier = tier,
                                (MTEAirCompressor t) -> t.mSetTier))))
                .addElement(
                    'C',
                    onElementPass(
                        MTEAirCompressor::onCasingAdded,
                        ofBlocksTiered(
                            MTEAirCompressor::getPipeTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                            -1,
                            (MTEAirCompressor t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEAirCompressor t) -> t.mSetTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTEAirCompressor::onCasingAdded,
                        ofBlocksTiered(
                            MTEAirCompressor::getGearTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                            -1,
                            (MTEAirCompressor t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEAirCompressor t) -> t.mSetTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTEAirCompressor::onCasingAdded,
                        ofBlocksTiered(
                            MTEAirCompressor::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTEAirCompressor t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEAirCompressor t) -> t.mSetTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {
        mCasingCount++;
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
        mSetTier = -1;
        mCasingCount = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }
        if (mSetTier <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mSteamInputFluids.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mOutputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        updateHatchTexture();
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.airCompressorRecipes;
    }

    @Override
    public OverclockDescriber getOverclockDescriber() {
        return null;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (getTotalSteamStored() <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        lEUt = mSetTier == 2 ? -60 : -20;
        mMaxProgresstime = 20;
        mEfficiencyIncrease = 10000;
        boolean isNether = getBaseMetaTileEntity().getWorld().provider.dimensionId == -1;
        int amount = 800 * getMaxParallelRecipes();
        mOutputFluids = new FluidStack[] {
            isNether ? Materials.NetherAir.getFluid(amount) : Materials.Air.getGas(amount) };
        updateSlots();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public int getMaxParallelRecipes() {
        return mSetTier == 2 ? 4 : 1;
    }

    @Override
    public int getTierRecipes() {
        return 0;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    protected IIconContainer getInactiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_STEAM_COMPRESSOR;
    }

    @Override
    protected IIconContainer getActiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_STEAM_COMPRESSOR_ACTIVE;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.air_compressor.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.air_compressor.desc"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + " 400/1200 L/s"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.gui.tier.bronze")
                    + "/"
                    + StatCollector.translateToLocal("gtsr.gui.tier.steel")
                    + ")")
            .addInfo(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.superheated_quadruples"))
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("gtsr.tooltip.air_compressor.nether"))
            .beginStructureBlock(5, 4, 3, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.air_compressor.ctrl"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.air_compressor.input_hatch"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.air_compressor.output_hatch"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addStructureInfo("")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 23, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 12, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 6, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 16, false)
            .addStructureInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.parallel")
                    + ": "
                    + EnumChatFormatting.GOLD
                    + "1"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.gui.tier.bronze")
                    + ")"
                    + EnumChatFormatting.GOLD
                    + "/4"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.gui.tier.steel")
                    + ")")
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
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
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSetTier = aNBT.getInteger("mSetTier");
    }

    protected boolean hasPressureSteamHatch() {
        for (MTEHatchCustomFluidBase hatch : mSteamInputFluids) {
            if (hatch instanceof MTEHatchPressureSteamInput) return true;
        }
        return false;
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
        screenElements.widget(new TextWidget().setStringSupplier(() -> {
            String tierText = mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText
                + EnumChatFormatting.RESET;
        }))
            .widget(new TextWidget().setStringSupplier(() -> {
                String steamType = hasSuperheatedSteamInHatch()
                    ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                    : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                    + EnumChatFormatting.YELLOW
                    + steamType
                    + EnumChatFormatting.RESET;
            }))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                        + EnumChatFormatting.YELLOW
                        + getMaxParallelRecipes()
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val));
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new com.miaokatze.gtsr.common.gui.MTEAirCompressorGui(this);
    }

    @Override
    public String[] getInfoData() {
        if (!mMachine) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.air_compressor.type"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building") };
        }
        String tierText = mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
            : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
        String steamType = hasSuperheatedSteamInHatch()
            ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
            : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
        return new String[] {
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.air_compressor.type"),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                + EnumChatFormatting.YELLOW
                + steamType,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                + EnumChatFormatting.YELLOW
                + getMaxParallelRecipes() };
    }
}
