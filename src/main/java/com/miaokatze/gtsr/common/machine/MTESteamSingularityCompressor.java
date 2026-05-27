package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.text.NumberFormat;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
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
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTESteamSingularityCompressor extends MTESteamMultiBase<MTESteamSingularityCompressor>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 5;
    private static final int VERTICAL_OFF_SET = 8;
    private static final int DEPTH_OFF_SET = 2;

    private static final int STEAM_L_EUT = 6000;
    private static final double HEAT_UP_PER_RECIPE = 0.0002d;
    private static final double HEAT_DOWN_RATE = 0.001d;
    private static final long STOP_THRESHOLD = 1200;
    private static final int HEAT_RECIPE_TIME = 20;

    private static final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    static {
        numberFormat.setMinimumFractionDigits(3);
        numberFormat.setMaximumFractionDigits(3);
    }

    private static IStructureDefinition<MTESteamSingularityCompressor> STRUCTURE_DEFINITION = null;

    protected int mCasingCount = 0;
    protected double mHeat = 0.0d;
    protected long mStoppedTicks = 0;
    protected int mStartUpCheck = 100;

    private static Textures.BlockIcons.CustomIcon OVERLAY_OFF;
    private static Textures.BlockIcons.CustomIcon OVERLAY_ON;

    public MTESteamSingularityCompressor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESteamSingularityCompressor(String aName) {
        super(aName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = new Textures.BlockIcons.CustomIcon("gtsr:MTESteamSingularityCompressor_OFF");
        OVERLAY_ON = new Textures.BlockIcons.CustomIcon("gtsr:MTESteamSingularityCompressor_ON");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamSingularityCompressor(mName);
    }

    @Override
    public String getMachineType() {
        return "Steam Singularity Compressor";
    }

    protected int getCasingTextureID() {
        return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
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

    @Override
    public IStructureDefinition<MTESteamSingularityCompressor> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);

            STRUCTURE_DEFINITION = StructureDefinition.<MTESteamSingularityCompressor>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { " DBBBBBBBD ", "DB  EBE  BD", "B DEBDBED B", "B EEEEEEE B", "BEBEEEEEBEB", "BBDEEEEEDBB",
                                "BEBEEEEEBEB", "B EEEEEEE B", "B DEBDBED B", "DB   B   BD", " DBBBBBBBD " },
                            { " F       F ", "FD  EBE  DF", "  BEFBFEB  ", "  E     E  ", " EF     FE ", " BB     BB ",
                                " EF     FE ", "  E     E  ", "  BEFBFEB  ", "FD  EBE  DF", " F       F " },
                            { " F       F ", "FD  E E  DF", "  BEFBFEB  ", "  EB B BE  ", " EF     FE ", "  BB   BB  ",
                                " EF     FE ", "  EB B BE  ", "  BEFBFEB  ", "FD  E E  DF", " F       F " },
                            { " F       F ", "F   E E   F", "  CEFEFEC  ", "  ED D DE  ", " EF     FE ", "  ED   DE  ",
                                " EF     FE ", "  ED D DE  ", "  CEFEFEC  ", "F   E E  DF", " F       F " },
                            { " F       F ", "F   E E   F", "  CEFEFEC  ", "  EB B BE  ", " EF BBB FE ", "  EBB BBE  ",
                                " EF BBB FE ", "  EB B BE  ", "  CEFEFEC  ", "F   E E   F", " F       F " },
                            { " F       F ", "F   E E   F", "   EFEFE   ", "  E     E  ", " EF EEE FE ", "  E E E E  ",
                                " EF EEE FE ", "  E     E  ", "   EFEFE   ", "F   E E   F", " F       F " },
                            { " F       F ", "F   E E   F", "  CEFEFEC  ", "  EB B BE  ", " EF BBB FE ", "  EBB BBE  ",
                                " EF BBB FE ", "  EB B BE  ", "  CEFEFEC  ", "F   E E   F", " F       F " },
                            { " F       F ", "F   E E   F", "  CEFEFEC  ", "  ED D DE  ", " EF     FE ", "  ED   DE  ",
                                " EF     FE ", "  ED D DE  ", "  CEFEFEC  ", "F   E E   F", " F       F " },
                            { " F       F ", "FD  E E  DF", "  BEF~FEB  ", "  EB B BE  ", " EF     FE ", "  BB   BB  ",
                                " EF     FE ", "  EB B BE  ", "  BEFBFEB  ", "FD  E E  DF", " F       F " },
                            { " F       F ", "FD  EBE  DF", "  BEFBFEB  ", "  ED D DE  ", " EF     FE ", " BBD   DBB ",
                                " EF     FE ", "  ED D DE  ", "  BEFBFEB  ", "FD  EBE  DF", " F       F " },
                            { " DBBBBBBBD ", "DB  EBE  BD", "B DEBDBED B", "B EEEEEEE B", "BEBEEEEEBEB", "BBDEEEEEDBB",
                                "BEBEEEEEBEB", "B EEEEEEE B", "B DEBDBED B", "DB  EBE  BD", " DBBBBBBBD " } }))
                .addElement(
                    'B',
                    ofChain(
                        buildHatchAdder(MTESteamSingularityCompressor.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchClass(MTESteamCoolingHatch.class)
                            .casingIndex(casingIndex)
                            .dot(2)
                            .build(),
                        buildHatchAdder(MTESteamSingularityCompressor.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchClass(MTEPressureSteamCoolingHatch.class)
                            .casingIndex(casingIndex)
                            .dot(2)
                            .build(),
                        buildHatchAdder(MTESteamSingularityCompressor.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchIds(31040, MetaTileEntityID.PRESSURE_STEAM_HATCH.ID)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTESteamSingularityCompressor.class).atLeast(OutputBus)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTESteamSingularityCompressor::onCasingAdded,
                                    ofBlock(GregTechAPI.sBlockCasings2, 0)))))
                .addElement(
                    'C',
                    onElementPass(
                        MTESteamSingularityCompressor::onCasingAdded,
                        ofBlock(GregTechAPI.sBlockCasings2, 13)))
                .addElement(
                    'D',
                    onElementPass(MTESteamSingularityCompressor::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 3)))
                .addElement(
                    'E',
                    onElementPass(
                        MTESteamSingularityCompressor::onCasingAdded,
                        ofBlock(GameRegistry.findBlock("IC2", "blockAlloyGlass"), 0)))
                .addElement(
                    'F',
                    onElementPass(
                        MTESteamSingularityCompressor::onCasingAdded,
                        ofBlock(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)))
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
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasingCount = 0;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) {
            return false;
        }

        if (this.mSteamInputFluids.size() != 1 || this.mOutputBusses.size() != 1) return false;

        updateHatchTexture();
        return true;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        lEUt = -STEAM_L_EUT;
        mMaxProgresstime = HEAT_RECIPE_TIME;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mOutputItems = emptyItemStackArray;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    protected void outputAfterRecipe() {
        mHeat += HEAT_UP_PER_RECIPE;

        if (mHeat >= 1.0d) {
            addOutput(GTSRItemList.SteamEntangledSingularity.get(1));
            mHeat = 0.0d;
        }
        updateSlots();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (mMachine) {
            mStartUpCheck = 100;
        } else if (mStartUpCheck > 0) {
            mStartUpCheck--;
        }

        if (mMaxProgresstime <= 0) {
            mStoppedTicks++;
            if (mStoppedTicks > STOP_THRESHOLD && mStartUpCheck <= 0) {
                mHeat = Math.max(0.0d, mHeat - HEAT_DOWN_RATE);
            }
        } else {
            mStoppedTicks = 0;
        }
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    public double getEuDiscountForParallelism() {
        return 1.0d;
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true;
    }

    public int getOutputSlot() {
        return 0;
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
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.heat")
                        + ": "
                        + String.format("%.3f%%", mHeat * 100.0d)))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mHeat, val -> mHeat = val));
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
        return TextureFactory.of(OVERLAY_OFF);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(OVERLAY_ON);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(getMachineType())
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.0"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.1"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.2"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.3"))
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.4")
                    + EnumChatFormatting.WHITE
                    + " "
                    + GTUtility.formatNumbers(STEAM_L_EUT * 20)
                    + " L/s"
                    + EnumChatFormatting.RESET)
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.5"))
            .beginStructureBlock(11, 11, 11, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.ctrl"))
            .addStructureInfo(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.casing"))
            .addStructureInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.input_hatch")
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.any_casing"))
            .addOutputBus(
                EnumChatFormatting.GOLD + "1"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.any_casing"),
                1)
            .addStructureInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.cooling"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mHeat", mHeat);
        aNBT.setLong("mStoppedTicks", mStoppedTicks);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mHeat = aNBT.getDouble("mHeat");
        mStoppedTicks = aNBT.getLong("mStoppedTicks");
    }

    @Override
    public String[] getInfoData() {
        return new String[] {
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.recipe.steam_singularity_compressor"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.info.geothermal_boiler.status")
                + (mMachine
                    ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.info.geothermal_boiler.running")
                    : EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gtsr.info.geothermal_boiler.incomplete")),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.heat")
                + EnumChatFormatting.YELLOW
                + numberFormat.format(mHeat * 100)
                + "%",
            EnumChatFormatting.GRAY + "Steam: "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(STEAM_L_EUT * 20)
                + " L/s" };
    }
}
