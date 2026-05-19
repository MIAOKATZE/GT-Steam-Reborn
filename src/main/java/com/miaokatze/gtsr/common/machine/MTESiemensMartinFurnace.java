package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

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
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings2;

public class MTESiemensMartinFurnace extends MTEEnhancedMultiBlockBase<MTESiemensMartinFurnace>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 1;
    private static final int DEPTH_OFF_SET = 0;

    private static final int SUPERHEATED_STEAM_COST = 1_200;
    private static final double TEMPERATURE_INCREMENT = 0.00025d;
    private static final double TEMPERATURE_DECREMENT = 0.001d;
    private static final int MAX_PARALLEL = 32;

    private static IStructureDefinition<MTESiemensMartinFurnace> STRUCTURE_DEFINITION = null;

    private double mFurnaceTemperature = 0.0d;
    private final List<MTEHatchPressureSteamInput> mPressureSteamInputs = new ArrayList<>();

    void addPressureSteamInput(MTEHatchPressureSteamInput hatch) {
        mPressureSteamInputs.add(hatch);
    }

    private boolean addPressureSteamToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEHatchPressureSteamInput hatch) {
            addPressureSteamInput(hatch);
            return true;
        }
        return false;
    }

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

    public String getMachineType() {
        return "Siemens-Martin Furnace";
    }

    protected int getCasingTextureID() {
        return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
    }

    protected void updateHatchTexture() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mPressureSteamInputs) h.updateTexture(textureID);
        for (MTEHatch h : mInputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
    }

    @Override
    public IStructureDefinition<MTESiemensMartinFurnace> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);

            STRUCTURE_DEFINITION = StructureDefinition.<MTESiemensMartinFurnace>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC" }, { "C~C", "C C", "CCC" }, { "CCC", "CCC", "CCC" } }))
                .addElement(
                    'C',
                    ofChain(
                        buildHatchAdder(MTESiemensMartinFurnace.class)
                            .adder(MTESiemensMartinFurnace::addPressureSteamToMachineList)
                            .hatchClass(MTEHatchPressureSteamInput.class)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTESiemensMartinFurnace.class).atLeast(InputBus)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTESiemensMartinFurnace.class).atLeast(OutputBus)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .buildAndChain(onElementPass(x -> {}, ofBlock(GregTechAPI.sBlockCasings2, 0)))))
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
        mPressureSteamInputs.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) {
            return false;
        }

        if (this.mPressureSteamInputs.isEmpty()) return false;
        if (this.mInputBusses.isEmpty()) return false;
        if (this.mOutputBusses.isEmpty()) return false;

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
            } else {
                mFurnaceTemperature = Math.max(0.0d, mFurnaceTemperature - TEMPERATURE_DECREMENT);
            }
        }
    }

    private boolean consumeSuperheatedSteam() {
        Fluid superheated = FluidRegistry.getFluid("ic2superheatedsteam");
        if (superheated == null) return false;

        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack drained = hatch.drain(SUPERHEATED_STEAM_COST, false);
            if (drained != null && drained.amount >= SUPERHEATED_STEAM_COST) {
                Fluid fluid = drained.getFluid();
                if (fluid != null && "ic2superheatedsteam".equals(fluid.getName())) {
                    hatch.drain(SUPERHEATED_STEAM_COST, true);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic().setMaxParallelSupplier(this::getMaxParallelRecipes);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (mFurnaceTemperature < 1.0d) {
            if (hasInputItems()) {
                return SimpleCheckRecipeResult.ofFailure("gtsr.gui.siemens_martin.temperature_low");
            }
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        return super.checkProcessing();
    }

    private boolean hasInputItems() {
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus.getBaseMetaTileEntity() != null) {
                for (int i = 0; i < bus.getSizeInventory(); i++) {
                    if (bus.getStackInSlot(i) != null) {
                        return true;
                    }
                }
            }
        }
        return false;
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

    public int getTierRecipes() {
        return 0;
    }

    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gtsr.gui.siemens_martin_furnace.temperature")
                        + ": "
                        + String.format("%.0f%%", mFurnaceTemperature * 100.0d)))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mFurnaceTemperature, val -> mFurnaceTemperature = val));
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

    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE);
    }

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
                + (mMaxProgresstime > 0 ? EnumChatFormatting.GREEN + "Running" : EnumChatFormatting.RED + "Idle"));
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
                    + GTUtility.formatNumbers(SUPERHEATED_STEAM_COST)
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
            .addInputBus(
                EnumChatFormatting.GOLD + "1"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.any_casing"),
                1)
            .addOutputBus(
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
