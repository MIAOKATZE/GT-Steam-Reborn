package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTESiemensMartinFurnace extends MTESteamMultiBase<MTESiemensMartinFurnace>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 1;
    private static final int DEPTH_OFF_SET = 1;

    private static final int SUPERHEATED_STEAM_COST = 24_000;
    private static final double TEMPERATURE_INCREMENT = 0.005d;
    private static final int MAX_PARALLEL = 32;

    private static IStructureDefinition<MTESiemensMartinFurnace> STRUCTURE_DEFINITION = null;

    private double mFurnaceTemperature = 0.0d;

    public MTESiemensMartinFurnace(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESiemensMartinFurnace(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESiemensMartinFurnace(mName);
    }

    @Override
    public String getMachineType() {
        return "Siemens-Martin Furnace";
    }

    protected int getCasingTextureID() {
        return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
    }

    protected void updateHatchTexture() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mSteamInputFluids) h.updateTexture(textureID);
        for (MTEHatch h : mSteamInputs) h.updateTexture(textureID);
        for (MTEHatch h : mSteamOutputs) h.updateTexture(textureID);
    }

    @Override
    public IStructureDefinition<MTESiemensMartinFurnace> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);

            STRUCTURE_DEFINITION = StructureDefinition.<MTESiemensMartinFurnace>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC" }, { "CCC", "C C", "CCC" }, { "C~C", "CCC", "CCC" } }))
                .addElement(
                    'C',
                    ofChain(
                        buildHatchAdder(MTESiemensMartinFurnace.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchIds(31040, MetaTileEntityID.PRESSURE_STEAM_HATCH.ID)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTESiemensMartinFurnace.class)
                            .atLeast(SteamHatchElement.InputBus_Steam, SteamHatchElement.OutputBus_Steam)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .buildAndChain(ofBlock(GregTechAPI.sBlockCasings2, 0))))
                .build();
        }
        return STRUCTURE_DEFINITION;
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
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) {
            return false;
        }

        if (this.mSteamInputFluids.isEmpty()) return false;
        if (this.mSteamInputs.isEmpty()) return false;
        if (this.mSteamOutputs.isEmpty()) return false;

        updateHatchTexture();
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (mMachine) {
            if (consumeSuperheatedSteam()) {
                mFurnaceTemperature = Math.min(1.0d, mFurnaceTemperature + TEMPERATURE_INCREMENT);
            }
        }
    }

    private boolean consumeSuperheatedSteam() {
        Fluid superheated = FluidRegistry.getFluid("ic2superheatedsteam");
        if (superheated == null) return false;

        FluidStack request = new FluidStack(superheated, SUPERHEATED_STEAM_COST);
        for (MTEHatchCustomFluidBase hatch : mSteamInputFluids) {
            FluidStack drained = hatch.drain(SUPERHEATED_STEAM_COST, false);
            if (drained != null && drained.amount >= SUPERHEATED_STEAM_COST && drained.isFluidEqual(request)) {
                hatch.drain(SUPERHEATED_STEAM_COST, true);
                return true;
            }
        }
        return false;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (mFurnaceTemperature < 1.0d) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        return super.checkProcessing();
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.siemensMartinRecipes;
    }

    @Override
    public int getMaxParallelRecipes() {
        if (mFurnaceTemperature < 1.0d) return 0;
        return MAX_PARALLEL;
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
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE);
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mFurnaceTemperature", mFurnaceTemperature);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mFurnaceTemperature = aNBT.getDouble("mFurnaceTemperature");
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + "Siemens-Martin Furnace" + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.GRAY + "Temperature: "
                + EnumChatFormatting.RED
                + String.format("%.0f%%", mFurnaceTemperature * 100)
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.GRAY + "Status: "
                + (mMachine ? EnumChatFormatting.GREEN + "Running" : EnumChatFormatting.RED + "Incomplete"));
        info.add(
            EnumChatFormatting.GRAY + "Steam: "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(SUPERHEATED_STEAM_COST)
                + " L/t"
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.GRAY + "Parallel: " + EnumChatFormatting.GOLD + MAX_PARALLEL + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(getMachineType())
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.info"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.superheated"))
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.steam_cost")
                    + EnumChatFormatting.WHITE
                    + SUPERHEATED_STEAM_COST
                    + " L/t"
                    + EnumChatFormatting.RESET)
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.temperature"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.parallel"))
            .beginStructureBlock(3, 3, 3, false)
            .addInputHatch(
                EnumChatFormatting.GOLD + "1"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.any_casing"),
                1)
            .addSteamInputBus(
                EnumChatFormatting.GOLD + "1"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.any_casing"),
                1)
            .addSteamOutputBus(
                EnumChatFormatting.GOLD + "1"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.any_casing"),
                1)
            .addStructureInfo(
                EnumChatFormatting.GOLD + "26"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.casing"))
            .toolTipFinisher();
        return tt;
    }
}
