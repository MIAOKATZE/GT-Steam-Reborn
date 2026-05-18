package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.text.NumberFormat;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

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
import gregtech.common.blocks.BlockCasings1;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTESteamSingularityCompressor extends MTESteamMultiBase<MTESteamSingularityCompressor>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 1;
    private static final int DEPTH_OFF_SET = 0;

    private static final int STEAM_PER_CYCLE = 256_000;
    private static final double HEAT_UP_RATE = 0.00025d;
    private static final double HEAT_DOWN_RATE = 0.001d;
    private static final long STOP_THRESHOLD = 1200;

    private static final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    static {
        numberFormat.setMinimumFractionDigits(3);
        numberFormat.setMaximumFractionDigits(3);
    }

    private static IStructureDefinition<MTESteamSingularityCompressor> STRUCTURE_DEFINITION = null;

    protected int mCasingCount = 0;
    protected double mHeat = 0.0d;
    protected long mStoppedTicks = 0;

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

    @Override
    public IStructureDefinition<MTESteamSingularityCompressor> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTESteamSingularityCompressor>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC" }, { "C~C", "C C", "CCC" }, { "CCC", "CCC", "CCC" } }))
                .addElement(
                    'C',
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
                                    ofBlock(GregTechAPI.sBlockCasings1, 10)))))
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
        FluidStack steamStack = Materials.Steam.getGas(STEAM_PER_CYCLE);
        if (!depleteInput(steamStack)) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        mHeat += HEAT_UP_RATE;

        if (mHeat >= 1.0d) {
            addOutput(GTSRItemList.SteamEntangledSingularity.get(1));
            mHeat = 0.0d;
        }

        mMaxProgresstime = 20;
        mEfficiencyIncrease = 10000;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (mMaxProgresstime <= 0) {
            mStoppedTicks++;
            if (mStoppedTicks > STOP_THRESHOLD) {
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
                    + GTUtility.formatNumbers(STEAM_PER_CYCLE)
                    + " L/t"
                    + EnumChatFormatting.RESET)
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.5"))
            .beginStructureBlock(3, 3, 3, false)
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
                + GTUtility.formatNumbers(STEAM_PER_CYCLE)
                + " L/t" };
    }
}
