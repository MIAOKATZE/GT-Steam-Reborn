package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
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
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;

public class MTELargeCokeOven extends MTEEnhancedMultiBlockBase<MTELargeCokeOven>
    implements IConstructable, ISurvivalConstructable {

    private static final double HEAT_PER_RECIPE = 0.1d;
    private static final double HEAT_UP_PER_SECOND = 0.0001d;
    private static final double HEAT_DOWN_PER_SECOND = 0.00006d;
    private static final int BASE_RECIPE_TIME_SECONDS = 1800;
    private static final int HEAT_SPEEDUP_PER_PERCENT = 10;
    private static final int MIN_RECIPE_TIME_SECONDS = 800;
    private static final int MAX_PARALLEL_T1 = 8;
    private static final int MAX_PARALLEL_T2 = 16;

    private double mHeat = 0.0d;
    private int mTier = 1;
    private int mParallel = 0;

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 5;
    private static final int DEPTH_OFF_SET = 0;
    private static IStructureDefinition<MTELargeCokeOven> STRUCTURE_DEFINITION = null;

    public MTELargeCokeOven(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTELargeCokeOven(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeCokeOven(mName);
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return true;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return true;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.largeCokeOvenRecipes;
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
        if (block == GregTechAPI.sBlockFrames && meta == gregtech.api.enums.Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == gregtech.api.enums.Materials.Steel.mMetaItemSubID) return 2;
        return null;
    }

    @Override
    public IStructureDefinition<MTELargeCokeOven> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTELargeCokeOven>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "   GFFF", "    F F", "   GFFF" }, { "   GFFF", "    F F", "   GFFF" },
                            { "BBBGFFF", "BBBBF F", "BBBGFFF" }, { "BBBGFFF", "BCCCC F", "BBBGFFF" },
                            { "BBBGFFF", "BCCCC F", "BBBGFFF" }, { "B~BGFFF", "BCCCC F", "BBBGFFF" },
                            { "BBBGEEE", "BBBDEEE", "BBBGEEE" } }))
                .addElement(
                    'B',
                    ofChain(
                        buildHatchAdder(MTELargeCokeOven.class).atLeast(InputBus)
                            .casingIndex(10)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTELargeCokeOven.class).atLeast(OutputBus)
                            .casingIndex(10)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTELargeCokeOven.class).atLeast(OutputHatch)
                            .casingIndex(10)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTELargeCokeOven::onCasingAdded,
                                    ofBlocksTiered(
                                        MTELargeCokeOven::getCasingTier,
                                        ImmutableList.of(
                                            Pair.of(GregTechAPI.sBlockCasings1, 10),
                                            Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                        -1,
                                        (MTELargeCokeOven t, Integer tier) -> t.mTier = tier,
                                        (MTELargeCokeOven t) -> t.mTier)))))
                .addElement(
                    'C',
                    onElementPass(
                        MTELargeCokeOven::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeCokeOven::getPipeTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                            -1,
                            (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                            (MTELargeCokeOven t) -> t.mTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTELargeCokeOven::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeCokeOven::getGearTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                            -1,
                            (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                            (MTELargeCokeOven t) -> t.mTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTELargeCokeOven::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeCokeOven::getFireboxTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings3, 13), Pair.of(GregTechAPI.sBlockCasings3, 14)),
                            -1,
                            (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                            (MTELargeCokeOven t) -> t.mTier)))
                .addElement(
                    'F',
                    onElementPass(MTELargeCokeOven::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings4, 15)))
                .addElement(
                    'G',
                    onElementPass(
                        MTELargeCokeOven::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeCokeOven::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, gregtech.api.enums.Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, gregtech.api.enums.Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                            (MTELargeCokeOven t) -> t.mTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {}

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.desc2"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.desc3"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.formula"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.accel"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.recipe"))
            .beginStructureBlock(3, 7, 7, true)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.ctrl"))
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.output_bus"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.output_hatch"), 1)
            .addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.BLUE + "Bronze/Steel " + EnumChatFormatting.DARK_PURPLE + "Tier")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 39, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebox"), 9, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 12, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 1, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 14, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebrick"), 45, false)
            .addStructureInfo(
                EnumChatFormatting.YELLOW + "Parallel: "
                    + EnumChatFormatting.GOLD
                    + "4"
                    + EnumChatFormatting.GRAY
                    + " (Bronze)"
                    + EnumChatFormatting.GOLD
                    + "/16"
                    + EnumChatFormatting.GRAY
                    + " (Steel)")
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
        clearHatches();
        mTier = 1;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) {
            return false;
        }

        if (mTier < 1) {
            return false;
        }

        return mInputBusses.size() >= 1 && mOutputBusses.size() >= 1;
    }

    @Override
    public int getMaxParallelRecipes() {
        if (mTier >= 2) return MAX_PARALLEL_T2;
        return MAX_PARALLEL_T1;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic().setMaxParallelSupplier(this::getMaxParallelRecipes);
    }

    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        CheckRecipeResult result = super.checkProcessing();
        if (!result.wasSuccessful()) return result;

        mParallel = processingLogic.getCurrentParallels();

        if (mHeat > 0.0d) {
            int reducedSeconds = (int) (mHeat * 100.0d * HEAT_SPEEDUP_PER_PERCENT);
            int actualSeconds = Math.max(MIN_RECIPE_TIME_SECONDS, BASE_RECIPE_TIME_SECONDS - reducedSeconds);
            mMaxProgresstime = actualSeconds * 20;
        }

        return result;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (mMachine && aTick % 20 == 0) {
            if (mMaxProgresstime > 0) {
                mHeat = Math.min(1.0d, mHeat + HEAT_UP_PER_SECOND);
            } else {
                mHeat = Math.max(0.0d, mHeat - HEAT_DOWN_PER_SECOND);
            }
        }

        aBaseMetaTileEntity.setActive(mMaxProgresstime > 0);
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.coke_oven.temperature")
                        + String.format("%.0f%%", mHeat * 100.0d)))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mHeat, val -> mHeat = val));
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.coke_oven.type"));

        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.temperature")
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mHeat * 100.0d));

        String statusKey;
        EnumChatFormatting statusColor;
        if (mMaxProgresstime > 0) {
            statusKey = "gtsr.gui.status.running";
            statusColor = EnumChatFormatting.AQUA;
        } else if (mHeat > 0) {
            statusKey = "gtsr.gui.coke_oven.status.heating";
            statusColor = EnumChatFormatting.GREEN;
        } else {
            statusKey = "gtsr.gui.status.idle";
            statusColor = EnumChatFormatting.GRAY;
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey));

        if (mMaxProgresstime > 0) {
            int secondsRemaining = (mMaxProgresstime - mProgresstime) / 20;
            info.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time")
                    + EnumChatFormatting.GOLD
                    + secondsRemaining
                    + "s");
        }

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                + " "
                + EnumChatFormatting.GOLD
                + (mTier >= 2 ? "4/16" : "4"));

        return info.toArray(new String[0]);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mHeat", mHeat);
        aNBT.setInteger("mTier", mTier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mHeat = aNBT.getDouble("mHeat");
        mTier = aNBT.getInteger("mTier");
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return IAlignmentLimits.UPRIGHT;
    }

    protected int getCasingTextureID() {
        if (mTier >= 2) {
            return ((gregtech.common.blocks.BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((gregtech.common.blocks.BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE);
    }

    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE);
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
}
