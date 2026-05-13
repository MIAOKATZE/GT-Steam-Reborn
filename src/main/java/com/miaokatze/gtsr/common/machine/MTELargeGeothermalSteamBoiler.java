package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

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
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamOutputHatch;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTELargeGeothermalSteamBoiler extends MTESteamMultiBase<MTELargeGeothermalSteamBoiler>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 2;
    private static final int DEPTH_OFF_SET = 1;

    private static IStructureDefinition<MTELargeGeothermalSteamBoiler> STRUCTURE_DEFINITION = null;
    private static final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    static {
        numberFormat.setMinimumFractionDigits(1);
        numberFormat.setMaximumFractionDigits(1);
    }

    protected int mSetTier = -1;
    protected int mCasingCount = 0;
    protected double mHeat = 0.0d;
    protected int mCurrentSteamOutput = 0;

    private static final double HEAT_UP_BRONZE = 0.00006d;
    private static final double HEAT_UP_STEEL = 0.00003d;
    private static final double HEAT_DOWN = 0.00006d;
    private static final double HEAT_UP_CHIP = 0.00001d;

    private static final int MAX_OUTPUT_BRONZE = 60_000;
    private static final int MAX_OUTPUT_STEEL = 150_000;

    private static final int BASE_RECIPE_TIME = 100;
    private static final int HEATED_RECIPE_TIME_BRONZE = 400;
    private static final int HEATED_RECIPE_TIME_STEEL = 200;
    private static final int OVERHEAT_CHIP_RECIPE_TIME = 140;

    private static final int LAVA_PER_RECIPE = 1000;

    private final ArrayList<MTESteamOutputHatch> mSteamOutputHatches = new ArrayList<>();
    private final ArrayList<MTEPressureSteamOutputHatch> mPressureSteamOutputHatches = new ArrayList<>();

    public MTELargeGeothermalSteamBoiler(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTELargeGeothermalSteamBoiler(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeGeothermalSteamBoiler(mName);
    }

    @Override
    public String getMachineType() {
        return "Geothermal Boiler";
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
        for (MTEHatch h : mSteamInputs) h.updateTexture(textureID);
        for (MTEHatch h : mSteamOutputs) h.updateTexture(textureID);
        for (MTEHatch h : mSteamOutputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mPressureSteamOutputHatches) h.updateTexture(textureID);
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
    public IStructureDefinition<MTELargeGeothermalSteamBoiler> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTELargeGeothermalSteamBoiler>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC" }, { "CCC", "C C", "CCC" }, { "C~C", "CCC", "CCC" } }))
                .addElement(
                    'C',
                    ofChain(
                        buildHatchAdder(MTELargeGeothermalSteamBoiler.class).atLeast(InputHatch, OutputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTELargeGeothermalSteamBoiler.class)
                            .atLeast(GeothermalHatchElement.SteamOutput, GeothermalHatchElement.PressureSteamOutput)
                            .casingIndex(bronzeCasingIndex)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTELargeGeothermalSteamBoiler.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchIds(31040)
                            .casingIndex(bronzeCasingIndex)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTELargeGeothermalSteamBoiler::onCasingAdded,
                                    ofBlocksTiered(
                                        MTELargeGeothermalSteamBoiler::getCasingTier,
                                        ImmutableList.of(
                                            Pair.of(GregTechAPI.sBlockCasings1, 10),
                                            Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                        -1,
                                        (MTELargeGeothermalSteamBoiler t, Integer tier) -> t.mSetTier = tier,
                                        (MTELargeGeothermalSteamBoiler t) -> t.mSetTier)))))
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
        mSteamOutputHatches.clear();
        mPressureSteamOutputHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) return false;
        if (mSetTier <= 0) return false;
        if (!hasValidOutputHatchesForTier()) return false;

        updateHatchTexture();
        return true;
    }

    private boolean hasValidOutputHatchesForTier() {
        boolean hasChip = hasOverheatChip();
        if (mSetTier == 1 && !hasChip) {
            return !mSteamOutputHatches.isEmpty();
        }
        if (mSetTier == 2 || hasChip) {
            return !mPressureSteamOutputHatches.isEmpty();
        }
        return !mSteamOutputHatches.isEmpty() || !mPressureSteamOutputHatches.isEmpty();
    }

    private boolean hasOverheatChip() {
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.GeothermalOverheatChip.isStackEqual(stack, true, true);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        FluidStack lava = drainLavaInput(LAVA_PER_RECIPE, false);
        if (lava == null || lava.amount < LAVA_PER_RECIPE) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        drainLavaInput(LAVA_PER_RECIPE, true);

        mOutputItems = new ItemStack[] { new ItemStack(Blocks.obsidian, 1),
            GTOreDictUnificator.get(OrePrefixes.dust, Materials.Sulfur, 2) };

        int duration = calculateActualDuration();
        mMaxProgresstime = duration;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private FluidStack drainLavaInput(int amount, boolean doDrain) {
        for (MTEHatch h : mInputHatches) {
            if (h instanceof MTEHatchOutput) continue;
            FluidStack fluid = h.getFluid();
            if (fluid != null && fluid.getFluid() == FluidRegistry.getFluid("lava") && fluid.amount >= amount) {
                if (doDrain) {
                    return h.drain(amount, true);
                }
                return fluid;
            }
        }
        return null;
    }

    private int calculateActualDuration() {
        boolean hasChip = hasOverheatChip();
        if (hasChip) {
            double multiplier = 1.0
                + mHeat * ((double) (OVERHEAT_CHIP_RECIPE_TIME - BASE_RECIPE_TIME) / BASE_RECIPE_TIME);
            return (int) (BASE_RECIPE_TIME * multiplier);
        }
        int heatedTime = mSetTier == 1 ? HEATED_RECIPE_TIME_BRONZE : HEATED_RECIPE_TIME_STEEL;
        double multiplier = 1.0 + mHeat * ((double) (heatedTime - BASE_RECIPE_TIME) / BASE_RECIPE_TIME);
        return (int) (BASE_RECIPE_TIME * multiplier);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (aTick % 20 == 0) {
            boolean isRunning = mMaxProgresstime > 0 && mProgresstime > 0;
            if (isRunning) {
                double rate = hasOverheatChip() ? HEAT_UP_CHIP : (mSetTier == 1 ? HEAT_UP_BRONZE : HEAT_UP_STEEL);
                mHeat = Math.min(1.0d, mHeat + rate);
            } else {
                mHeat = Math.max(0.0d, mHeat - HEAT_DOWN);
            }
        }

        if (aTick % 20 == 0 && mHeat > 0.0d && mMaxProgresstime > 0) {
            int maxOutput = mSetTier == 1 ? MAX_OUTPUT_BRONZE : MAX_OUTPUT_STEEL;
            int steamOutput = (int) (maxOutput * mHeat);
            mCurrentSteamOutput = steamOutput;

            if (steamOutput > 0) {
                boolean isSuperheated = hasOverheatChip();
                FluidStack steam = isSuperheated ? FluidRegistry.getFluidStack("ic2superheatedsteam", steamOutput)
                    : Materials.Steam.getGas(steamOutput);
                distributeSteam(steam);
            }
        } else if (mMaxProgresstime <= 0) {
            mCurrentSteamOutput = 0;
        }
    }

    private void distributeSteam(FluidStack steam) {
        if (steam == null) return;

        boolean isSuperheated = "ic2superheatedsteam".equals(FluidRegistry.getFluidName(steam.getFluid()));

        if (isSuperheated) {
            for (MTEPressureSteamOutputHatch hatch : mPressureSteamOutputHatches) {
                if (steam.amount <= 0) break;
                int filled = hatch.fill(ForgeDirection.UNKNOWN, steam.copy(), true);
                steam.amount -= filled;
            }
        } else {
            for (MTESteamOutputHatch hatch : mSteamOutputHatches) {
                if (steam.amount <= 0) break;
                int filled = hatch.fill(ForgeDirection.UNKNOWN, steam.copy(), true);
                steam.amount -= filled;
            }
            for (MTEPressureSteamOutputHatch hatch : mPressureSteamOutputHatches) {
                if (steam.amount <= 0) break;
                int filled = hatch.fill(ForgeDirection.UNKNOWN, steam.copy(), true);
                steam.amount -= filled;
            }
        }
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + "Heat: "
                        + EnumChatFormatting.GOLD
                        + numberFormat.format(mHeat * 100)
                        + "% "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + "Steam Output: "
                        + EnumChatFormatting.AQUA
                        + numberFormat.format(mCurrentSteamOutput)
                        + " L/s "
                        + EnumChatFormatting.WHITE
                        + (hasOverheatChip() ? "Superheated" : "Steam")
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mHeat, val -> mHeat = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentSteamOutput, val -> mCurrentSteamOutput = val));
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(getMachineType())
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.0"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.1"))
            .beginStructureBlock(3, 3, 3, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.ctrl"))
            .addInputHatch(EnumChatFormatting.GOLD + "1+" + EnumChatFormatting.GRAY + " Any casing", 1)
            .addOutputHatch(EnumChatFormatting.GOLD + "1+" + EnumChatFormatting.GRAY + " Any casing", 1)
            .addStructureInfo(
                EnumChatFormatting.AQUA + "Steam Output Hatch "
                    + EnumChatFormatting.GOLD
                    + "1+"
                    + EnumChatFormatting.GRAY
                    + " Any casing (Bronze tier)")
            .addStructureInfo(
                EnumChatFormatting.LIGHT_PURPLE + "Pressure Steam Output Hatch "
                    + EnumChatFormatting.GOLD
                    + "1+"
                    + EnumChatFormatting.GRAY
                    + " Any casing (Steel tier)")
            .addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.BLUE + "Bronze " + EnumChatFormatting.DARK_PURPLE + "Tier")
            .addStructureInfo(EnumChatFormatting.GOLD + "26x" + EnumChatFormatting.GRAY + " Bronze Plated Bricks")
            .addStructureInfo(EnumChatFormatting.GOLD + "60,000" + EnumChatFormatting.GRAY + " L/s max steam output")
            .addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.BLUE + "Steel " + EnumChatFormatting.DARK_PURPLE + "Tier")
            .addStructureInfo(EnumChatFormatting.GOLD + "26x" + EnumChatFormatting.GRAY + " Solid Steel Machine Casing")
            .addStructureInfo(EnumChatFormatting.GOLD + "150,000" + EnumChatFormatting.GRAY + " L/s max steam output")
            .toolTipFinisher();
        return tt;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
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
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getTierRecipes() {
        return 1;
    }

    @Override
    protected int getCasingTextureId() {
        return getCasingTextureID();
    }

    @Override
    public String[] getInfoData() {
        String tierText = mSetTier == 2 ? "Steel" : mSetTier == 1 ? "Bronze" : "N/A";
        return new String[] { EnumChatFormatting.BLUE + "Large Geothermal Steam Boiler",
            EnumChatFormatting.GRAY + "Tier: " + EnumChatFormatting.GOLD + tierText,
            EnumChatFormatting.GRAY + "Status: "
                + (mMachine ? EnumChatFormatting.GREEN + "Running" : EnumChatFormatting.RED + "Incomplete"),
            EnumChatFormatting.GRAY + "Heat: " + EnumChatFormatting.YELLOW + numberFormat.format(mHeat * 100) + "%",
            EnumChatFormatting.GRAY + "Steam Output: "
                + EnumChatFormatting.AQUA
                + numberFormat.format(mCurrentSteamOutput)
                + " L/s" };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setDouble("mHeat", mHeat);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSetTier = aNBT.getInteger("mSetTier");
        mHeat = aNBT.getDouble("mHeat");
    }

    public boolean addSteamOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTESteamOutputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mSteamOutputHatches.add(hatch);
        }
        return false;
    }

    public boolean addPressureSteamOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEPressureSteamOutputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mPressureSteamOutputHatches.add(hatch);
        }
        return false;
    }

    private enum GeothermalHatchElement implements IHatchElement<MTELargeGeothermalSteamBoiler> {

        SteamOutput(MTELargeGeothermalSteamBoiler::addSteamOutputToMachineList, MTESteamOutputHatch.class),
        PressureSteamOutput(MTELargeGeothermalSteamBoiler::addPressureSteamOutputToMachineList,
            MTEPressureSteamOutputHatch.class);

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTELargeGeothermalSteamBoiler> adder;

        @SafeVarargs
        GeothermalHatchElement(IGTHatchAdder<MTELargeGeothermalSteamBoiler> adder,
            Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTELargeGeothermalSteamBoiler> adder() {
            return adder;
        }

        @Override
        public long count(MTELargeGeothermalSteamBoiler t) {
            return switch (this) {
                case SteamOutput -> t.mSteamOutputHatches.size();
                case PressureSteamOutput -> t.mPressureSteamOutputHatches.size();
            };
        }
    }
}
