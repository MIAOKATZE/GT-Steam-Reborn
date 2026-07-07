package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
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
import com.miaokatze.gtsr.common.gui.MTESiemensMartinFurnaceGui;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.blocks.BlockCasings2;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusInput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;

public class MTESiemensMartinFurnace extends MTEEnhancedMultiBlockBase<MTESiemensMartinFurnace>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 7;
    private static final int VERTICAL_OFF_SET = 16;
    private static final int DEPTH_OFF_SET = 3;

    private static final int SUPERHEATED_STEAM_COST = 300;
    private static final int SUPERHEATED_STEAM_COST_OVERHEAT = 3_000;
    private static final int SUPERHEATED_STEAM_COST_MAX = 1_500;
    private static final double TEMPERATURE_INCREMENT = 0.00025d;
    private static final double TEMPERATURE_DECREMENT = 0.001d;
    private static final double OVERHEAT_INCREMENT = 0.0001d; // +0.01%/s above 100%
    private static final double OVERHEAT_DECREMENT = 0.01d; // -1%/s when not running
    private static final double MAX_OVERHEAT = 2.0d; // 200%
    private static final double RECIPE_TIME_REDUCTION_PER_PERCENT = 0.005d; // 0.5% per 1% above 100%
    private static final double MAX_RECIPE_TIME_REDUCTION = 0.5d; // 50% max reduction
    private static final int MAX_PARALLEL = 64;

    private static IStructureDefinition<MTESiemensMartinFurnace> STRUCTURE_DEFINITION = null;

    public double mFurnaceTemperature = 0.0d;
    private final List<MTEHatchPressureSteamInput> mPressureSteamInputs = new ArrayList<>();
    private int mStartUpCheck = 100;

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

    public boolean addInputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchInputBus hatch) {
            hatch.mRecipeMap = getRecipeMap();
            hatch.updateTexture(aBaseCasingIndex);
            return mInputBusses.add(hatch);
        }
        return false;
    }

    public boolean addOutputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchOutputBus hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mOutputBusses.add(hatch);
        }
        return false;
    }

    /**
     * Custom hatch elements for the Siemens-Martin Furnace.
     * <p>
     * Each element exposes the machine-specific adder while overriding {@code mteBlacklist()}
     * so that NEI does not render the corresponding hatch over every casing position.
     */
    private enum SiemensMartinHatchElement implements IHatchElement<MTESiemensMartinFurnace> {

        InputBus(MTESiemensMartinFurnace::addInputBusToMachineList, MTEHatchInputBus.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchSteamBusInput.class);
            }
        },
        OutputBus(MTESiemensMartinFurnace::addOutputBusToMachineList, MTEHatchOutputBus.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchSteamBusOutput.class);
            }
        },
        PressureSteamInput(MTESiemensMartinFurnace::addPressureSteamToMachineList, MTEHatchPressureSteamInput.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchPressureSteamInput.class);
            }
        };

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTESiemensMartinFurnace> adder;

        @SafeVarargs
        SiemensMartinHatchElement(IGTHatchAdder<MTESiemensMartinFurnace> adder,
            Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTESiemensMartinFurnace> adder() {
            return adder;
        }

        @Override
        public long count(MTESiemensMartinFurnace t) {
            return 0;
        }
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
                        new String[][] {
                            { "              ", "              ", "              ", "      BBBG    ", "     B   BG   ",
                                "    B     B   ", "    B     B   ", "    B     B   ", "     B   BG   ",
                                "      BBBG    ", "              ", "              " },
                            { "              ", "              ", "              ", "      BBBG    ", "     BGGGBG   ",
                                "    BGGGGGB   ", "    BGGGGGB   ", "    BGGGGGB   ", "     BGGGBG   ",
                                "      BBBG    ", "              ", "              " },
                            { "              ", "              ", "              ", "      BBBG    ", "     B   BG   ",
                                "    B     B   ", "    B     B   ", "    B     B   ", "     B   BG   ",
                                "      BBBG    ", "              ", "              " },
                            { "              ", "              ", "              ", "      BBBG    ", "     B   BG   ",
                                "    B     B   ", "    B     B   ", "    B     B   ", "     B   BG   ",
                                "      BBBG    ", "              ", "              " },
                            { "              ", "              ", "              ", "      BBBG    ", "     B   BG   ",
                                "    B     B   ", "    B     B   ", "    B     B   ", "     B   BG   ",
                                "      BBBG    ", "              ", "              " },
                            { "              ", "              ", "              ", "      BBBG    ", "     B   BG   ",
                                "    B     B   ", "    B     B   ", "    B     B   ", "     B   BG   ",
                                "      BBBG    ", "              ", "              " },
                            { "              ", "              ", "              ", "      BBBG    ", "     B   BG   ",
                                "    B     B   ", "    B     B   ", "    B     B   ", "     B   BG   ",
                                "      BBBG    ", "              ", "              " },
                            { " GFG          ", " F F          ", "F   F         ", "F   F BBBG    ", "F   FB   BG   ",
                                "F   B     B   ", "F   B     B   ", "F   B     B   ", "F   FB   BG   ",
                                "F   F BBBG    ", " F F          ", " GFG          " },
                            { " GFG          ", " FGF          ", "FGGGF         ", "FGGGF BBBG    ", "FGGGFB   BG   ",
                                "FGGGB     B   ", "FGGGB     B   ", "FGGGB     B   ", "FGGGFB   BG   ",
                                "FGGGF BBBG    ", " FGF          ", " GFG          " },
                            { " GFG          ", " F F          ", "F   F         ", "F   F BBBG    ", "F   FB   BG   ",
                                "F   B     B   ", "F   B     B   ", "F   B     B   ", "F   FB   BG   ",
                                "F   F BBBG    ", " F F          ", " GFG          " },
                            { " GFG          ", " F F          ", "F   F         ", "F   F BBBG    ", "F   FB   BG   ",
                                "F   B     B   ", "F   B     B   ", "F   B     B   ", "F   FB   BG   ",
                                "F   F BBBG    ", " F F          ", " GFG          " },
                            { " GFG          ", " F F          ", "F   F         ", "F   F BBBG    ", "F   FB   BG   ",
                                "F   B     B   ", "F   C     B   ", "F   B     B   ", "F   FB   BG   ",
                                "F   F BBBG    ", " F F          ", " GFG          " },
                            { " GFG          ", " F F          ", "F   F         ", "F   F BBBG    ", "F   FB   BG   ",
                                "F   B     B   ", "F   C     B   ", "F   B     B   ", "F   FB   BG   ",
                                "F   F BBBG    ", " F F          ", " GFG          " },
                            { " GFG          ", " F F          ", "F   F         ", "F   F BBBG    ", "F   FB   BG   ",
                                "F   B     B   ", "F   C     B   ", "F   B     B   ", "F   FB   BG   ",
                                "F   F BBBG    ", " F F          ", " GFG          " },
                            { " GFG          ", " F F          ", "F   F         ", "F   F BBBG    ", "F   FB   BGF  ",
                                "F   B     BFF ", "F   C     BFFF", "F   B     BFF ", "F   FB   BGF  ",
                                "F   F BBBG    ", " F F          ", " GFG          " },
                            { " GFG          ", " F F          ", "F   F     BBB ", "F   F BBBGBBB ", "F   FB   BBFB ",
                                "F   B     BFF ", "F   C     CCFF", "F   B     BFF ", "F   FB   BGF  ",
                                "F   F BBBG    ", " F F          ", " GFG          " },
                            { " GFG          ", " F F      BBB ", "F   F     BCB ", "F   F B~BGBCB ", "F   FB   BDCD ",
                                "F   B     BCF ", "F   C     CCFF", "F   B     BFF ", "F   FB   BGF  ",
                                "F   F BBBG    ", " F F          ", " GFG          " },
                            { " GFG          ", " F F      BBB ", "F   F BBB BCB ", "F   F BBBGBCB ", "F   FB   BDCD ",
                                "F   B     BCF ", "F   B     CCFF", "F   B     BFF ", "F   FB   BGF  ",
                                "F   F BBBG    ", " F F          ", " GFG          " },
                            { " GEG          ", " EEE  BBB BBB ", "EEEEE BBB BBB ", "EEEEE EEEGBBB ", "EEEEEEEEEEDED ",
                                "EEEEEEEEEEEEE ", "EEEEEEEEEEEEEE", "EEEEEEEEEEEEE ", "EEEEEEEEEEGE  ",
                                "EEEEE EEEG    ", " EEE          ", " GEG          " } }))
                .addElement('~', onElementPass(x -> {}, ofBlock(GregTechAPI.sBlockCasings2, 0)))
                .addElement(
                    'B',
                    ofChain(
                        // 1) 输入总线（自定义 IHatchElement，带 mteBlacklist 避免 NEI 覆盖外壳）
                        buildHatchAdder(MTESiemensMartinFurnace.class).atLeast(SiemensMartinHatchElement.InputBus)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build(),
                        // 2) 输出总线
                        buildHatchAdder(MTESiemensMartinFurnace.class).atLeast(SiemensMartinHatchElement.OutputBus)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build(),
                        // 3) 耐压蒸汽输入仓
                        buildHatchAdder(MTESiemensMartinFurnace.class)
                            .atLeast(SiemensMartinHatchElement.PressureSteamInput)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build(),
                        // 4) casing 兜底
                        ofBlock(GregTechAPI.sBlockCasings2, 0)))
                .addElement('C', ofBlock(GregTechAPI.sBlockCasings2, 13))
                .addElement('D', ofBlock(GregTechAPI.sBlockCasings2, 3))
                .addElement('E', ofBlock(GregTechAPI.sBlockCasings3, 14))
                .addElement('F', ofBlock(GregTechAPI.sBlockCasings4, 15))
                .addElement('G', ofBlock(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID))
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
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mPressureSteamInputs.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }

        if (this.mPressureSteamInputs.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (this.mInputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (this.mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        updateHatchTexture();
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

        boolean inGracePeriod = mStartUpCheck > 0;

        if (mMachine && aBaseMetaTileEntity.isAllowedToWork()) {
            if (mFurnaceTemperature < 1.0d && mMaxProgresstime > 0) {
                stopMachine(ShutDownReasonRegistry.POWER_LOSS);
            }

            // Determine steam cost based on temperature and running state
            int steamCost = SUPERHEATED_STEAM_COST; // default 1200 L/tick = 24000 L/s
            if (mMaxProgresstime > 0) {
                if (mFurnaceTemperature >= MAX_OVERHEAT) {
                    steamCost = SUPERHEATED_STEAM_COST_MAX / 20; // 300 L/tick = 6000 L/s
                } else if (mFurnaceTemperature >= 1.0d) {
                    steamCost = SUPERHEATED_STEAM_COST_OVERHEAT / 20; // 600 L/tick = 12000 L/s
                }
            }

            if (consumeSuperheatedSteam(steamCost)) {
                if (mFurnaceTemperature < 1.0d) {
                    mFurnaceTemperature = Math.min(1.0d, mFurnaceTemperature + TEMPERATURE_INCREMENT);
                }
                // Overheat: only when running recipe, applied per second
                else if (mMaxProgresstime > 0 && aTick % 20 == 0) {
                    mFurnaceTemperature = Math.min(MAX_OVERHEAT, mFurnaceTemperature + OVERHEAT_INCREMENT);
                }
            } else {
                mFurnaceTemperature = Math.max(0.0d, mFurnaceTemperature - TEMPERATURE_DECREMENT);
                if (mMaxProgresstime > 0) {
                    stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                }
            }

            // Overheat decay when not running recipe, applied per second
            if (mMaxProgresstime <= 0 && mFurnaceTemperature > 1.0d && aTick % 20 == 0) {
                mFurnaceTemperature = Math.max(1.0d, mFurnaceTemperature - OVERHEAT_DECREMENT);
            }
        } else if (mMachine && !aBaseMetaTileEntity.isAllowedToWork()) {
            if (mMaxProgresstime > 0) {
                stopMachine(ShutDownReasonRegistry.POWER_LOSS);
            }
            mFurnaceTemperature = Math.max(0.0d, mFurnaceTemperature - TEMPERATURE_DECREMENT);
            // Overheat decay when not allowed to work
            if (mFurnaceTemperature > 1.0d && aTick % 20 == 0) {
                mFurnaceTemperature = Math.max(1.0d, mFurnaceTemperature - OVERHEAT_DECREMENT);
            }
        } else if (!mMachine && !inGracePeriod) {
            mFurnaceTemperature = Math.max(0.0d, mFurnaceTemperature - TEMPERATURE_DECREMENT);
        }
    }

    private boolean consumeSuperheatedSteam(int amount) {
        Fluid superheated = FluidRegistry.getFluid("ic2superheatedsteam");
        if (superheated == null) return false;

        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack drained = hatch.drain(amount, false);
            if (drained != null && drained.amount >= amount) {
                Fluid fluid = drained.getFluid();
                if (fluid != null && "ic2superheatedsteam".equals(fluid.getName())) {
                    hatch.drain(amount, true);
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

        CheckRecipeResult result = super.checkProcessing();
        if (!result.wasSuccessful()) return result;

        // Apply overheat recipe time reduction
        if (mFurnaceTemperature > 1.0d) {
            double overheatPercent = (mFurnaceTemperature - 1.0d) * 100.0d; // 0-100%
            double reduction = Math.min(MAX_RECIPE_TIME_REDUCTION, overheatPercent * RECIPE_TIME_REDUCTION_PER_PERCENT);
            mMaxProgresstime = (int) (mMaxProgresstime * (1.0d - reduction));
        }

        return result;
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
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new MTESiemensMartinFurnaceGui(this);
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.siemens_martin.temperature")
                        + (mFurnaceTemperature > 1.0d ? EnumChatFormatting.LIGHT_PURPLE : EnumChatFormatting.RED)
                        + String.format("%.1f%%", mFurnaceTemperature * 100.0d)
                        + EnumChatFormatting.RESET))
            .widget(new TextWidget().setStringSupplier(() -> {
                String statusKey;
                EnumChatFormatting statusColor;
                if (mMaxProgresstime > 0) {
                    statusKey = "gtsr.gui.status.running";
                    statusColor = EnumChatFormatting.AQUA;
                } else if (mFurnaceTemperature > 0 && mFurnaceTemperature < 1.0d) {
                    statusKey = "gtsr.gui.siemens_martin.status.heating";
                    statusColor = EnumChatFormatting.YELLOW;
                } else {
                    statusKey = "gtsr.gui.status.idle";
                    statusColor = EnumChatFormatting.GRAY;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                    + " "
                    + statusColor
                    + StatCollector.translateToLocal(statusKey)
                    + EnumChatFormatting.RESET;
            }))
            .widget(new TextWidget().setStringSupplier(() -> {
                int steamCostLps;
                if (mMaxProgresstime > 0) {
                    if (mFurnaceTemperature >= MAX_OVERHEAT) {
                        steamCostLps = SUPERHEATED_STEAM_COST_MAX;
                    } else if (mFurnaceTemperature >= 1.0d) {
                        steamCostLps = SUPERHEATED_STEAM_COST_OVERHEAT;
                    } else {
                        steamCostLps = SUPERHEATED_STEAM_COST * 20;
                    }
                } else {
                    steamCostLps = SUPERHEATED_STEAM_COST * 20;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.siemens_martin.steam_cost")
                    + EnumChatFormatting.RED
                    + NumberFormatUtil.formatNumber(steamCostLps)
                    + " L/s"
                    + EnumChatFormatting.RESET;
            }))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                        "gtsr.gui.parallel") + " " + EnumChatFormatting.GOLD + MAX_PARALLEL + EnumChatFormatting.RESET))
            .widget(new TextWidget().setStringSupplier(() -> {
                if (mFurnaceTemperature > 1.0d) {
                    double overheatPercent = (mFurnaceTemperature - 1.0d) * 100.0d;
                    double reduction = Math
                        .min(MAX_RECIPE_TIME_REDUCTION, overheatPercent * RECIPE_TIME_REDUCTION_PER_PERCENT);
                    return EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.siemens_martin.overheat_reduction")
                        + EnumChatFormatting.GOLD
                        + String.format("%.1f%%", reduction * 100.0d)
                        + EnumChatFormatting.RESET;
                }
                return EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.siemens_martin.overheat_reduction")
                    + EnumChatFormatting.GRAY
                    + "0%"
                    + EnumChatFormatting.RESET;
            }))
            .widget(new TextWidget().setStringSupplier(() -> {
                String recipeInfo = mMaxProgresstime > 0
                    ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.siemens_martin.recipe.active")
                    : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.siemens_martin.recipe.none");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                    "gtsr.gui.siemens_martin.current_recipe") + " " + recipeInfo + EnumChatFormatting.RESET;
            }))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mFurnaceTemperature, val -> mFurnaceTemperature = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val));
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
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.siemens_martin.temperature")
                + " "
                + (mFurnaceTemperature > 1.0d ? EnumChatFormatting.LIGHT_PURPLE : EnumChatFormatting.RED)
                + String.format("%.1f%%", mFurnaceTemperature * 100.0d)
                + EnumChatFormatting.RESET);
        String statusKey;
        EnumChatFormatting statusColor;
        if (mMaxProgresstime > 0) {
            statusKey = "gtsr.gui.status.running";
            statusColor = EnumChatFormatting.AQUA;
        } else if (mFurnaceTemperature > 0 && mFurnaceTemperature < 1.0d) {
            statusKey = "gtsr.gui.siemens_martin.status.heating";
            statusColor = EnumChatFormatting.YELLOW;
        } else {
            statusKey = "gtsr.gui.status.idle";
            statusColor = EnumChatFormatting.GRAY;
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey)
                + EnumChatFormatting.RESET);
        int steamCostLps;
        if (mMaxProgresstime > 0) {
            if (mFurnaceTemperature >= MAX_OVERHEAT) {
                steamCostLps = SUPERHEATED_STEAM_COST_MAX;
            } else if (mFurnaceTemperature >= 1.0d) {
                steamCostLps = SUPERHEATED_STEAM_COST_OVERHEAT;
            } else {
                steamCostLps = SUPERHEATED_STEAM_COST * 20;
            }
        } else {
            steamCostLps = SUPERHEATED_STEAM_COST * 20;
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.siemens_martin.steam_cost")
                + " "
                + EnumChatFormatting.RED
                + NumberFormatUtil.formatNumber(steamCostLps)
                + " L/s"
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                "gtsr.gui.parallel") + " " + EnumChatFormatting.GOLD + MAX_PARALLEL + EnumChatFormatting.RESET);
        if (mFurnaceTemperature > 1.0d) {
            double overheatPercent = (mFurnaceTemperature - 1.0d) * 100.0d;
            double reduction = Math.min(MAX_RECIPE_TIME_REDUCTION, overheatPercent * RECIPE_TIME_REDUCTION_PER_PERCENT);
            info.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.siemens_martin.overheat_reduction")
                    + " "
                    + EnumChatFormatting.GOLD
                    + String.format("%.1f%%", reduction * 100.0d)
                    + EnumChatFormatting.RESET);
        }
        String recipeInfo = mMaxProgresstime > 0
            ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.siemens_martin.recipe.active")
            : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.siemens_martin.recipe.none");
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.siemens_martin.current_recipe")
                + " "
                + recipeInfo
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.type"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.desc2"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + NumberFormatUtil.formatNumber(SUPERHEATED_STEAM_COST * 20)
                    + " L/s"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.superheated_only")
                    + ")"
                    + EnumChatFormatting.RESET)
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.steam_only"))
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.overheat"))
            .addInfo(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.overheat_steam"))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.overheat_recipe"))
            .beginStructureBlock(12, 19, 14, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.steam_input"),
                1)
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.siemens_martin.output_bus"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("gtsr.tooltip.shared.steel_only"))
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_casing"), 318, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_pipe_casing"), 20, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_gear_box_casing"), 6, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_firebox_casing"), 91, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebricks"), 238, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_frame_box"), 167, false)
            .addStructureInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.parallel")
                    + ": "
                    + EnumChatFormatting.GOLD
                    + MAX_PARALLEL)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.siemens_martin.hint_temp")
            .addStructureHint("gtsr.tooltip.siemens_martin.hint_interrupt")
            .addStructureHint("gtsr.tooltip.siemens_martin.steam_note")
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
}
