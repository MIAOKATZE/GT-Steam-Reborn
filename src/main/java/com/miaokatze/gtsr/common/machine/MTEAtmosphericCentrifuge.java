package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import javax.annotation.Nonnull;
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
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTEAtmosphericCentrifuge extends MTESteamMultiBase<MTEAtmosphericCentrifuge>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 2;
    private static final int DEPTH_OFF_SET = 0;

    private static IStructureDefinition<MTEAtmosphericCentrifuge> STRUCTURE_DEFINITION = null;

    protected int mSetTier = -1;
    protected int mCasingCount = 0;

    public MTEAtmosphericCentrifuge(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEAtmosphericCentrifuge(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEAtmosphericCentrifuge(mName);
    }

    @Override
    public String getMachineType() {
        return "Atmospheric Centrifuge";
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
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
        if (block == GregTechAPI.sBlockFrames && meta == gregtech.api.enums.Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == gregtech.api.enums.Materials.Steel.mMetaItemSubID) return 2;
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
        for (MTEHatch h : mInputHatches) h.updateTexture(textureID);
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
    public IStructureDefinition<MTEAtmosphericCentrifuge> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTEAtmosphericCentrifuge>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { " EBBBE ", "EBBBBBE", "BBBBBBB", "BBBBBBB", "BBBBBBB", "EBBBBBE", " EBBBE " },
                            { " EDDDE ", "ED   DE", "D     D", "D  C  D", "D     D", "ED   DE", " EDDDE " },
                            { " EB~BE ", "EB C BE", "B  C  B", "BCCCCCB", "B  C  B", "EB C BE", " EBBBE " },
                            { " EBBBE ", "EBBBBBE", "BBBBBBB", "BBBBBBB", "BBBBBBB", "EBBBBBE", " EBBBE " } }))
                .addElement(
                    'B',
                    ofChain(
                        buildHatchAdder(MTEAtmosphericCentrifuge.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchIds(31040, MetaTileEntityID.PRESSURE_STEAM_HATCH.ID)
                            .casingIndex(bronzeCasingIndex)
                            .dot(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTEAtmosphericCentrifuge.class).atLeast(InputHatch, OutputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTEAtmosphericCentrifuge::onCasingAdded,
                                    ofBlocksTiered(
                                        MTEAtmosphericCentrifuge::getCasingTier,
                                        ImmutableList.of(
                                            Pair.of(GregTechAPI.sBlockCasings1, 10),
                                            Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                        -1,
                                        (MTEAtmosphericCentrifuge t, Integer tier) -> t.mSetTier = tier,
                                        (MTEAtmosphericCentrifuge t) -> t.mSetTier)))))
                .addElement(
                    'C',
                    onElementPass(
                        MTEAtmosphericCentrifuge::onCasingAdded,
                        ofBlocksTiered(
                            MTEAtmosphericCentrifuge::getGearTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                            -1,
                            (MTEAtmosphericCentrifuge t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEAtmosphericCentrifuge t) -> t.mSetTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTEAtmosphericCentrifuge::onCasingAdded,
                        ofBlock(cpw.mods.fml.common.registry.GameRegistry.findBlock("IC2", "blockAlloyGlass"), 0)))
                .addElement(
                    'E',
                    onElementPass(
                        MTEAtmosphericCentrifuge::onCasingAdded,
                        ofBlocksTiered(
                            MTEAtmosphericCentrifuge::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, gregtech.api.enums.Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, gregtech.api.enums.Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTEAtmosphericCentrifuge t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEAtmosphericCentrifuge t) -> t.mSetTier)))
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
        mSetTier = -1;
        mCasingCount = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) return false;
        if (mSetTier <= 0) return false;
        if (mSteamInputFluids.isEmpty()) return false;
        if (mInputHatches.size() > 10) return false;
        if (mOutputHatches.isEmpty()) return false;
        if (mOutputHatches.size() > 10) return false;
        updateHatchTexture();
        return true;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.atmosphericCentrifugeRecipes;
    }

    @Override
    protected boolean canUseControllerSlotForRecipe() {
        return false;
    }

    protected boolean hasRareGasChip() {
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.RareGasSeparationChip.isStackEqual(stack, true, true);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        return super.checkProcessing();
    }

    @Override
    public int getMaxParallelRecipes() {
        return mSetTier == 2 ? 16 : 4;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @Nonnull
            @Override
            protected CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                if (!hasRareGasChip() && recipe.mFluidOutputs.length > 2) {
                    return CheckRecipeResultRegistry.NO_RECIPE;
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }
        }.setMaxParallelSupplier(this::getTrueParallel);
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
    protected int getCasingTextureId() {
        return getCasingTextureID();
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
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_CENTRIFUGE);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_CENTRIFUGE_ACTIVE);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(getMachineType())
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.atmospheric_centrifuge.0"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.atmospheric_centrifuge.1"))
            .beginStructureBlock(7, 4, 7, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.atmospheric_centrifuge.ctrl"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.atmospheric_centrifuge.input_hatch"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.atmospheric_centrifuge.output_hatch"), 1)
            .addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.BLUE + "青铜" + EnumChatFormatting.DARK_PURPLE + " 等级")
            .addStructureInfo(EnumChatFormatting.GOLD + "26x" + EnumChatFormatting.GRAY + " 青铜镀层砖")
            .addStructureInfo(EnumChatFormatting.GOLD + "1x" + EnumChatFormatting.GRAY + " 并行: 4")
            .addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.BLUE + "钢" + EnumChatFormatting.DARK_PURPLE + " 等级")
            .addStructureInfo(EnumChatFormatting.GOLD + "26x" + EnumChatFormatting.GRAY + " 实心钢机壳")
            .addStructureInfo(EnumChatFormatting.GOLD + "1x" + EnumChatFormatting.GRAY + " 并行: 16")
            .addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.YELLOW + "稀有气体分离芯片" + EnumChatFormatting.GRAY + ": 放入控制器槽位，解锁稀有气体配方")
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
    public String[] getInfoData() {
        String tierText = mSetTier == 2 ? "钢" : mSetTier == 1 ? "青铜" : "N/A";
        String steamType = hasPressureSteamHatch() ? "耐压蒸汽" : hasSuperheatedSteamInHatch() ? "过热蒸汽" : "普通蒸汽";
        String chipText = hasRareGasChip() ? "已安装" : "未安装";
        return new String[] {
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.recipe.atmospheric_centrifuge"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.info.atmospheric_centrifuge.tier")
                + EnumChatFormatting.GOLD
                + tierText,
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.info.geothermal_boiler.status")
                + (mMachine
                    ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.info.geothermal_boiler.running")
                    : EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gtsr.info.geothermal_boiler.incomplete")),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.info.atmospheric_centrifuge.steam_type")
                + EnumChatFormatting.YELLOW
                + steamType,
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.info.atmospheric_centrifuge.parallel")
                + EnumChatFormatting.YELLOW
                + getMaxParallelRecipes(),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.info.atmospheric_centrifuge.chip")
                + (hasRareGasChip() ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                + chipText };
    }
}
