package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Iterator;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.util.UndergroundOilHelper;

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
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.common.UndergroundOil;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTEVeinSteamPyrolyzer extends MTESteamMultiBase<MTEVeinSteamPyrolyzer> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 4;
    private static final int DEPTH_OFF_SET = 0;

    private static IStructureDefinition<MTEVeinSteamPyrolyzer> STRUCTURE_DEFINITION = null;

    private Fluid mLockedFluid = null;
    private final ArrayList<ChunkCoordIntPair> mVeinChunks = new ArrayList<>();
    private int mChunkRange = -1;

    protected int mCountCasing = 0;
    protected int mSetTier = -1;

    private boolean mApplyFluidIncrease = false;

    public MTEVeinSteamPyrolyzer(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEVeinSteamPyrolyzer(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEVeinSteamPyrolyzer(mName);
    }

    @Override
    public String getMachineType() {
        return "Vein Steam Pyrolyzer";
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames) {
            if (meta == Materials.Bronze.mMetaItemSubID) return 1;
            if (meta == Materials.Steel.mMetaItemSubID) return 2;
        }
        return null;
    }

    @Nullable
    public static Integer getFireboxTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings3) {
            if (meta == 13) return 1;
            if (meta == 14) return 2;
        }
        return null;
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
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
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
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
    public IStructureDefinition<MTEVeinSteamPyrolyzer> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTEVeinSteamPyrolyzer>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "   ", " f ", "   " }, { " f ", "fcf", " f " }, { " c ", "ccc", " c " },
                            { " c ", "ccc", " c " }, { "b~b", "bbb", "bbb" } }))
                .addElement(
                    'f',
                    ofBlocksTiered(
                        MTEVeinSteamPyrolyzer::getFrameTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                        -1,
                        (MTEVeinSteamPyrolyzer t, Integer tier) -> t.mSetTier = tier,
                        (MTEVeinSteamPyrolyzer t) -> t.mSetTier))
                .addElement(
                    'c',
                    ofBlocksTiered(
                        MTEVeinSteamPyrolyzer::getFireboxTier,
                        ImmutableList
                            .of(Pair.of(GregTechAPI.sBlockCasings3, 13), Pair.of(GregTechAPI.sBlockCasings3, 14)),
                        -1,
                        (MTEVeinSteamPyrolyzer t, Integer tier) -> t.mSetTier = tier,
                        (MTEVeinSteamPyrolyzer t) -> t.mSetTier))
                .addElement(
                    'b',
                    ofChain(
                        buildHatchAdder(MTEVeinSteamPyrolyzer.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchIds(31040, MetaTileEntityID.PRESSURE_STEAM_HATCH.ID)
                            .casingIndex(bronzeCasingIndex)
                            .dot(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTEVeinSteamPyrolyzer.class).atLeast(gregtech.api.enums.HatchElement.OutputBus)
                            .casingIndex(bronzeCasingIndex)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTEVeinSteamPyrolyzer::onCasingAdded,
                                    ofBlocksTiered(
                                        MTEVeinSteamPyrolyzer::getCasingTier,
                                        ImmutableList.of(
                                            Pair.of(GregTechAPI.sBlockCasings1, 10),
                                            Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                        -1,
                                        (MTEVeinSteamPyrolyzer t, Integer tier) -> t.mSetTier = tier,
                                        (MTEVeinSteamPyrolyzer t) -> t.mSetTier)))))
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

        if (mSetTier <= 0) return false;
        if (mSteamInputFluids.size() < 1) return false;

        updateHatchTexture();
        return true;
    }

    private int getChipRangeBonus() {
        ItemStack controllerStack = getControllerSlot();
        if (controllerStack == null) return 0;
        if (GTSRItemList.VeinPyrolyzerChipT3.isStackEqual(controllerStack, true, true)) return 7;
        if (GTSRItemList.VeinPyrolyzerChipT2.isStackEqual(controllerStack, true, true)) return 3;
        if (GTSRItemList.VeinPyrolyzerChipT1.isStackEqual(controllerStack, true, true)) return 1;
        return 0;
    }

    @Override
    public void stopMachine(ShutDownReason aReason) {
        mApplyFluidIncrease = false;
        super.stopMachine(aReason);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        IGregTechTileEntity baseTE = getBaseMetaTileEntity();
        if (baseTE.getWorld().isRemote) return CheckRecipeResultRegistry.NO_RECIPE;

        int controllerChunkX = baseTE.getXCoord() >> 4;
        int controllerChunkZ = baseTE.getZCoord() >> 4;

        if (mLockedFluid == null) {
            FluidStack undergroundFluid = UndergroundOil.undergroundOilReadInformation(baseTE);
            if (undergroundFluid == null || undergroundFluid.getFluid() == null) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
            mLockedFluid = undergroundFluid.getFluid();
        }

        int baseRange = 1;
        int chipBonus = getChipRangeBonus();
        int currentRange = baseRange + chipBonus;

        if (mChunkRange != currentRange) {
            mChunkRange = currentRange;
            mVeinChunks.clear();
        }

        if (mVeinChunks.isEmpty()) {
            FluidStack tOil = new FluidStack(mLockedFluid, 0);
            int xStart = Math.floorDiv(controllerChunkX, currentRange) * currentRange;
            int zStart = Math.floorDiv(controllerChunkZ, currentRange) * currentRange;
            for (int i = 0; i < currentRange; i++) {
                for (int j = 0; j < currentRange; j++) {
                    FluidStack tFluid = UndergroundOil.undergroundOil(baseTE.getWorld(), xStart + i, zStart + j, -1);
                    if (tFluid != null && tOil.isFluidEqual(tFluid) && tFluid.amount > 0) {
                        mVeinChunks.add(new ChunkCoordIntPair(xStart + i, zStart + j));
                    }
                }
            }
            if (mVeinChunks.isEmpty()) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
        }

        if (mApplyFluidIncrease) {
            mApplyFluidIncrease = false;
            int increasePerOp = mSetTier == 2 ? 25000 : 10000;
            int totalIncreased = 0;
            for (Iterator<ChunkCoordIntPair> it = mVeinChunks.iterator(); it.hasNext();) {
                ChunkCoordIntPair chunk = it.next();
                int actual = UndergroundOilHelper.increaseFluidAmount(
                    baseTE.getWorld(),
                    chunk.chunkXPos,
                    chunk.chunkZPos,
                    increasePerOp,
                    Integer.MAX_VALUE);
                totalIncreased += actual;
            }
            if (totalIncreased <= 0) {
                mVeinChunks.clear();
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
        }

        int steamPerTick = mSetTier == 2 ? 200 : 50;
        int workTime = mSetTier == 2 ? 3600 : 7200;
        lEUt = -steamPerTick;
        mMaxProgresstime = workTime;
        mEfficiencyIncrease = 10000;
        mOutputItems = emptyItemStackArray;
        mApplyFluidIncrease = true;
        updateSlots();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getTierRecipes() {
        return 1;
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
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.vein_steam_pyrolyzer.0"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.vein_steam_pyrolyzer.1"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.vein_steam_pyrolyzer.2"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.vein_steam_pyrolyzer.5"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.vein_steam_pyrolyzer.6"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.vein_steam_pyrolyzer.7"))
            .beginStructureBlock(3, 5, 3, false)
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.vein_steam_pyrolyzer.12"))
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
        if (mLockedFluid != null) {
            aNBT.setString("mLockedFluid", mLockedFluid.getName());
        }
        aNBT.setInteger("mChunkRange", mChunkRange);
        aNBT.setInteger("mSetTier", mSetTier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mLockedFluid")) {
            String fluidName = aNBT.getString("mLockedFluid");
            mLockedFluid = net.minecraftforge.fluids.FluidRegistry.getFluid(fluidName);
        }
        mChunkRange = aNBT.getInteger("mChunkRange");
        mSetTier = aNBT.getInteger("mSetTier");
    }

    @Override
    public String[] getInfoData() {
        String fluidInfo = mLockedFluid != null
            ? EnumChatFormatting.GREEN + mLockedFluid.getLocalizedName(null) + EnumChatFormatting.RESET
            : EnumChatFormatting.GRAY + "None" + EnumChatFormatting.RESET;

        String rangeInfo = EnumChatFormatting.GOLD + (mChunkRange > 0 ? mChunkRange + "x" + mChunkRange : "N/A")
            + EnumChatFormatting.RESET;

        String chipInfo = EnumChatFormatting.AQUA + "+" + getChipRangeBonus() + EnumChatFormatting.RESET;

        int steamPerTick = mSetTier == 2 ? 200 : 50;
        int workTime = mSetTier == 2 ? 3600 : 7200;

        return new String[] { EnumChatFormatting.BLUE + "Vein Steam Pyrolyzer",
            EnumChatFormatting.GRAY + "Tier: " + EnumChatFormatting.GOLD + (mSetTier == 2 ? "Steel" : "Bronze"),
            EnumChatFormatting.GRAY + "Status: "
                + (mMachine ? EnumChatFormatting.GREEN + "Running" : EnumChatFormatting.RED + "Incomplete"),
            EnumChatFormatting.GRAY + "Fluid: " + fluidInfo,
            EnumChatFormatting.GRAY + "Range: " + rangeInfo + " (chip: " + chipInfo + ")",
            EnumChatFormatting.GRAY + "Steam: "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(steamPerTick * 20)
                + " L/s",
            EnumChatFormatting.GRAY + "Work Time: " + EnumChatFormatting.YELLOW + (workTime / 20) + "s" };
    }
}
