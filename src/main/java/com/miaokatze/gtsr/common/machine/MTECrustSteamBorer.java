package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

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
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 7;
    private static final int DEPTH_OFF_SET = 1;

    protected static final int STEAM_L_EUT = 100;
    protected static final int WORK_TIME_TICKS = 500;
    protected static final int STEAM_PER_SECOND = STEAM_L_EUT * 20;

    private static IStructureDefinition<MTECrustSteamBorer> STRUCTURE_DEFINITION = null;

    protected int mCountCasing = 0;
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
                            { "       ", "       ", "       ", "   B   ", "       ", "       ", "       " },
                            { "       ", "       ", "       ", "   B   ", "       ", "       ", "       " },
                            { "       ", "       ", "       ", "   B   ", "       ", "       ", "       " },
                            { "       ", "       ", "   B   ", "  BCB  ", "   B   ", "       ", "       " },
                            { "       ", "       ", "   B   ", "  BCB  ", "   B   ", "       ", "       " },
                            { "       ", "       ", "   B   ", "  BCB  ", "   B   ", "       ", "       " },
                            { "       ", " B   B ", "  DAD  ", "  ACA  ", "  DAD  ", " B   B ", "       " },
                            { "  D D  ", " BA~AB ", " A   A ", " B C B ", " A   A ", " BABAB ", "       " },
                            { "  E E  ", " BBBBB ", "EB   BE", " B C B ", "EB   BE", " BBBBB ", "  E E  " } }))
                .addElement('A', ofBlock(GregTechAPI.sBlockCasings1, 10))
                .addElement('B', ofBlock(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID))
                .addElement('C', ofBlock(GregTechAPI.sBlockCasings1, 10))
                .addElement('D', ofBlock(GregTechAPI.sBlockCasings1, 10))
                .addElement(
                    'E',
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
                                    ofBlock(GregTechAPI.sBlockCasings1, 10)))))
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
        tt.addMachineType(getMachineType())
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.info"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.bronze"))
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.steam_cost")
                    + EnumChatFormatting.WHITE
                    + STEAM_PER_SECOND
                    + " L/s"
                    + EnumChatFormatting.RESET)
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.pressure_hatch"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.dim_limit"))
            .beginStructureBlock(7, 9, 7, false)
            .addOutputBus(
                EnumChatFormatting.GOLD + "1"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.any_casing"),
                1)
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.steam_or_pressure_hatch"))
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.cooling_hatch"))
            .addStructureInfo(
                EnumChatFormatting.GOLD + "42"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.bronze_frame"))
            .addStructureInfo(
                EnumChatFormatting.GOLD + "20"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.bronze_casing"))
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
    public String[] getInfoData() {
        String dimInfo = canMineInCurrentDim
            ? EnumChatFormatting.GREEN + String.valueOf(mCurrentDimId) + EnumChatFormatting.RESET
            : EnumChatFormatting.RED + "Unsupported" + EnumChatFormatting.RESET;

        String oreInfo = mLastOreName != null && !mLastOreName.isEmpty()
            ? EnumChatFormatting.GREEN + mLastOreName + EnumChatFormatting.RESET
            : EnumChatFormatting.GRAY + "None" + EnumChatFormatting.RESET;

        boolean boosted = hasSuperheatedSteamInHatch();
        int workTime = boosted ? WORK_TIME_TICKS / 4 : WORK_TIME_TICKS;

        String hatchInfo = hasPressureSteamHatch()
            ? EnumChatFormatting.GOLD + "Pressure Steam Hatch"
                + EnumChatFormatting.RESET
                + (boosted ? " " + EnumChatFormatting.GREEN + "(4x)" : "")
            : EnumChatFormatting.WHITE + "Steam Hatch" + EnumChatFormatting.RESET;

        return new String[] { EnumChatFormatting.BLUE + "Crust Steam Borer",
            EnumChatFormatting.GRAY + "Tier: " + EnumChatFormatting.GOLD + "Bronze",
            EnumChatFormatting.GRAY + "Status: "
                + (mMachine ? EnumChatFormatting.GREEN + "Running" : EnumChatFormatting.RED + "Incomplete"),
            EnumChatFormatting.GRAY + "Dimension: " + dimInfo, EnumChatFormatting.GRAY + "Mining: " + oreInfo,
            EnumChatFormatting.GRAY + "Hatch: " + hatchInfo,
            EnumChatFormatting.GRAY + "Steam Consumption: "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(STEAM_PER_SECOND)
                + " L/s",
            EnumChatFormatting.GRAY + "Work Time: " + EnumChatFormatting.YELLOW + (workTime / 20) + "s" };
    }
}
