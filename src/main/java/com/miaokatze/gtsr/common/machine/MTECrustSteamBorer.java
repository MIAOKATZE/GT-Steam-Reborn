package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
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
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.VoidMinerUtilityShim;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings1;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTECrustSteamBorer extends MTESteamMultiBase<MTECrustSteamBorer> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 9;
    private static final int DEPTH_OFF_SET = 2;

    protected static final int STEAM_L_EUT = 100;
    protected static final int WORK_TIME_TICKS = 500;
    protected static final int STEAM_PER_SECOND = STEAM_L_EUT * 20;

    private static IStructureDefinition<MTECrustSteamBorer> STRUCTURE_DEFINITION = null;

    protected int mCountCasing = 0;
    protected int mSetTier = -1;
    protected VoidMinerUtility.DropMap dropMap = null;
    protected VoidMinerUtility.DropMap extraDropMap = null;

    protected int mCurrentDimId = 0;
    protected boolean canMineInCurrentDim = false;
    protected String mLastOreName = "";

    public MTECrustSteamBorer(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTECrustSteamBorer(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECrustSteamBorer(mName);
    }

    @Override
    public String getMachineType() {
        return "Crust Steam Borer";
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
    public static Integer getFireboxTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings3 && meta == 13) return 1;
        if (block == GregTechAPI.sBlockCasings3 && meta == 14) return 2;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        return null;
    }

    protected int getCasingTextureID() {
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    protected void updateHatchTexture() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mSteamInputFluids) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
    }

    @Override
    public void onValueUpdate(byte aValue) {}

    @Override
    public byte getUpdateData() {
        return 0;
    }

    protected boolean hasPressureSteamHatch() {
        for (MTEHatchCustomFluidBase hatch : mSteamInputFluids) {
            if (hatch instanceof MTEHatchPressureSteamInput) return true;
        }
        return false;
    }

    protected boolean hasSuperheatedSteamInHatch() {
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
    public IStructureDefinition<MTECrustSteamBorer> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTECrustSteamBorer>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "    F    ", "   FCF   ", "    F    ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "    F    ", "   FCF   ", "    F    ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "    F    ", "   FCF   ", "    F    ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "   EFE   ", "   FCF   ", "   EFE   ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "  FD DF  ", "  DCBCD  ", "   BCB   ", "  DCBCD  ", "  FD DF  ",
                                "         ", "         " },
                            { "         ", "   B B   ", "  FB~BF  ", " BB   BB ", "  F C F  ", " BB   BB ", "  FBFBF  ",
                                "   B B   ", "         " },
                            { "   B B   ", "  BBBBB  ", " BFFFFFB ", "BBF   FBB", " BF C FB ", "BBF   FBB", " BFFFFFB ",
                                "  BBBBB  ", "   B B   " } }))
                .addElement(
                    'B',
                    ofChain(
                        buildHatchAdder(MTECrustSteamBorer.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchIds(31040, MetaTileEntityID.PRESSURE_STEAM_HATCH.ID)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTECrustSteamBorer.class).atLeast(OutputBus)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTECrustSteamBorer::onCasingAdded,
                                    ofBlocksTiered(
                                        MTECrustSteamBorer::getCasingTier,
                                        ImmutableList.of(
                                            Pair.of(GregTechAPI.sBlockCasings1, 10),
                                            Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                        -1,
                                        (MTECrustSteamBorer t, Integer tier) -> t.mSetTier = tier,
                                        (MTECrustSteamBorer t) -> t.mSetTier)))))
                .addElement(
                    'C',
                    onElementPass(
                        MTECrustSteamBorer::onCasingAdded,
                        ofBlocksTiered(
                            MTECrustSteamBorer::getPipeTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                            -1,
                            (MTECrustSteamBorer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTECrustSteamBorer t) -> t.mSetTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTECrustSteamBorer::onCasingAdded,
                        ofBlocksTiered(
                            MTECrustSteamBorer::getGearTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                            -1,
                            (MTECrustSteamBorer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTECrustSteamBorer t) -> t.mSetTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTECrustSteamBorer::onCasingAdded,
                        ofBlocksTiered(
                            MTECrustSteamBorer::getFireboxTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings3, 13), Pair.of(GregTechAPI.sBlockCasings3, 14)),
                            -1,
                            (MTECrustSteamBorer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTECrustSteamBorer t) -> t.mSetTier)))
                .addElement(
                    'F',
                    onElementPass(
                        MTECrustSteamBorer::onCasingAdded,
                        ofBlocksTiered(
                            MTECrustSteamBorer::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTECrustSteamBorer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTECrustSteamBorer t) -> t.mSetTier)))
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
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCountCasing = 0;
        mSetTier = -1;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) {
            return false;
        }

        if (this.mSteamInputFluids.size() != 1 || this.mOutputBusses.size() != 1) return false;

        updateHatchTexture();
        return true;
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        mCurrentDimId = getBaseMetaTileEntity().getWorld().provider.dimensionId;
        canMineInCurrentDim = isValidDimension(mCurrentDimId);
        if (canMineInCurrentDim) {
            calculateDropMap();
        }
    }

    protected boolean isValidDimension(int dimId) {
        return dimId == 0 || dimId == -1;
    }

    protected void calculateDropMap() {
        dropMap = VoidMinerUtilityShim.getDropMapById(mCurrentDimId);
        extraDropMap = VoidMinerUtilityShim.getExtraDropMapById(mCurrentDimId);
        dropMap.isDistributionCached(extraDropMap);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (!canMineInCurrentDim) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        if (getTotalSteamStored() > 0) {
            lEUt = -STEAM_L_EUT;
            mMaxProgresstime = WORK_TIME_TICKS;
            mEfficiencyIncrease = 10000;
            mOutputItems = emptyItemStackArray;
            updateSlots();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    protected void outputAfterRecipe() {
        if (dropMap != null && dropMap.getTotalWeight() > 0) {
            GTUtility.ItemId oreId = dropMap.nextOre();
            if (oreId != null) {
                ItemStack oreStack = oreId.getItemStack();
                if (oreStack != null) {
                    addOutputPartial(oreStack, false);
                    mLastOreName = oreStack.getDisplayName();
                }
            }
        }
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getTierRecipes() {
        return 0;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                aActive ? getFrontOverlayActive() : getFrontOverlay() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.crust_borer.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.crust_borer.desc"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + " 2000 L/s")
            .addInfo(
                EnumChatFormatting.GREEN + "Superheated Steam"
                    + EnumChatFormatting.GRAY
                    + " quadruples "
                    + EnumChatFormatting.GREEN
                    + "Speed"
                    + EnumChatFormatting.GRAY
                    + " and "
                    + EnumChatFormatting.AQUA
                    + "Steam Usage")
            .beginStructureBlock(9, 11, 9, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.crust_borer.ctrl"))
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.crust_borer.output_bus"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_or_pressure"),
                1)
            .addStructureInfo("")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 20, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 42, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 8, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 11, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebox"), 4, false)
            .addStructureInfo(EnumChatFormatting.YELLOW + "Parallel: " + EnumChatFormatting.GOLD + "1")
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.shared.optional_cooling")
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
        aNBT.setInteger("mCurrentDimId", mCurrentDimId);
        aNBT.setBoolean("canMineInCurrentDim", canMineInCurrentDim);
        aNBT.setString("mLastOreName", mLastOreName);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mCurrentDimId = aNBT.getInteger("mCurrentDimId");
        canMineInCurrentDim = aNBT.getBoolean("canMineInCurrentDim");
        mLastOreName = aNBT.getString("mLastOreName");
        if (canMineInCurrentDim) {
            calculateDropMap();
        }
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                        + EnumChatFormatting.GOLD
                        + (mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                            : mSetTier == 1 ? StatCollector.translateToLocal("gtsr.gui.tier.bronze") : "None")))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val))
            .widget(new TextWidget().setStringSupplier(() -> {
                String statusText;
                if (!canMineInCurrentDim) {
                    statusText = EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gtsr.gui.crust_borer.invalid_dim");
                } else if (mMaxProgresstime <= 0) {
                    statusText = EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.crust_borer.no_steam");
                } else {
                    statusText = EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.status.running");
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status") + statusText;
            }))
            .widget(new FakeSyncWidget.BooleanSyncer(() -> canMineInCurrentDim, val -> canMineInCurrentDim = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.dimension")
                        + (canMineInCurrentDim ? EnumChatFormatting.GREEN + String.valueOf(mCurrentDimId)
                            : EnumChatFormatting.RED
                                + StatCollector.translateToLocal("gtsr.gui.crust_borer.invalid_dim"))))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentDimId, val -> mCurrentDimId = val))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.mining")
                        + (mLastOreName != null && !mLastOreName.isEmpty() ? EnumChatFormatting.GREEN + mLastOreName
                            : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.none"))))
            .widget(new FakeSyncWidget.StringSyncer(() -> mLastOreName, val -> mLastOreName = val))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.steam_cost")
                        + EnumChatFormatting.RED
                        + GTUtility.formatNumbers(STEAM_PER_SECOND)
                        + " L/s"))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.work_cycle")
                        + EnumChatFormatting.YELLOW
                        + (WORK_TIME_TICKS / 20)
                        + "s"));
    }

    @Override
    public String[] getInfoData() {
        if (!mMachine) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.crust_borer.type"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building") };
        }
        String dimInfo = canMineInCurrentDim ? EnumChatFormatting.GREEN + String.valueOf(mCurrentDimId)
            : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.crust_borer.invalid_dim");
        String oreInfo = mLastOreName != null && !mLastOreName.isEmpty() ? EnumChatFormatting.GREEN + mLastOreName
            : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.none");
        boolean boosted = hasSuperheatedSteamInHatch();
        int workTime = boosted ? WORK_TIME_TICKS / 4 : WORK_TIME_TICKS;
        String statusText;
        if (!canMineInCurrentDim) {
            statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.crust_borer.invalid_dim");
        } else if (getTotalSteamStored() <= 0) {
            statusText = EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.no_steam");
        } else {
            statusText = EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.status.running");
        }
        return new String[] { EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.crust_borer.type"),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("gtsr.gui.tier.bronze"),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status") + statusText,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.dimension") + dimInfo,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.mining") + oreInfo,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.steam_cost")
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(STEAM_PER_SECOND)
                + " L/s",
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.work_cycle")
                + EnumChatFormatting.YELLOW
                + (workTime / 20)
                + "s" };
    }
}
