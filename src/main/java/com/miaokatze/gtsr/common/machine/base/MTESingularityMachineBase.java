package com.miaokatze.gtsr.common.machine.base;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
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
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTESingularityMachineGui;

import bartworks.common.loaders.ItemRegistry;
import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoltageIndex;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GlassTier;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusInput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;

/** Shared structure and steam plumbing for the singularity machines. */
public abstract class MTESingularityMachineBase extends MTEEnhancedMultiBlockBase<MTESingularityMachineBase>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 5;
    private static final int VERTICAL_OFF_SET = 8;
    private static final int DEPTH_OFF_SET = 2;
    protected static final int CYCLE_LENGTH = 20;
    protected static final double HEAT_DECAY_PER_SECOND = 0.01d;
    protected static final double[] GRADE_COEF = { 0.5d, 1.0d, 2.0d };
    protected static final String[] DENSE_FLUID_NAMES = { "densesteam", "densesuperheatedsteam",
        "densesupercriticalsteam" };
    protected static final String[] NORMAL_FLUID_NAMES = { "steam", "ic2superheatedsteam", "supercriticalsteam" };
    /** 单周期单仓最大取流/探测量。覆盖压力蒸汽输入仓 512K 容量，避免 ME 网络全量提取。 */
    protected static final int MAX_DRAIN_PER_CYCLE = 1_000_000;

    private static IStructureDefinition<MTESingularityMachineBase> STRUCTURE_DEFINITION;
    private static IIconContainer OVERLAY_OFF;
    private static IIconContainer OVERLAY_ON;
    private static Block TIER2_FRAME_BLOCK;
    private static Integer TIER2_FRAME_META;

    public int mTier = 0;
    public double mHeat = 0.0d;

    private int mCasingTierB = -1;
    private int mCasingTierC = -1;
    private int mCasingTierD = -1;
    private int mCasingTierE = -1;
    private int mCasingTierF = -1;

    private final List<MTEHatchPressureSteamInput> mPressureSteamInputs = new ArrayList<>();

    protected MTESingularityMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected MTESingularityMachineBase(String aName) {
        super(aName);
    }

    protected abstract int getRequiredTier();

    protected abstract double getHeatMax();

    protected abstract long getHeatHalfPoint();

    protected abstract boolean includeDenseSteam();

    protected abstract ItemStack getAggregationOutput();

    protected String getTooltipKeyPrefix() {
        return "gtsr.tooltip.singularity_compressor.";
    }

    public String getGuiKeyPrefix() {
        return "gtsr.gui.singularity_compressor.";
    }

    protected boolean requiresOutputHatch() {
        return false;
    }

    protected boolean requiresInputBus() {
        return false;
    }

    public boolean isDenseStateManipulator() {
        return false;
    }

    public int getModeForGui() {
        return -1;
    }

    public int getFuelTicksForGui() {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = Textures.BlockIcons.custom("gtsr:MTESteamSingularityCompressor_OFF");
        OVERLAY_ON = Textures.BlockIcons.custom("gtsr:MTESteamSingularityCompressor_ON");
        super.registerIcons(aBlockIconRegister);
    }

    private static Block getTier2FrameBlock() {
        if (TIER2_FRAME_BLOCK == null) {
            TIER2_FRAME_BLOCK = GregTechAPI.sBlockFramesBW;
            if (TIER2_FRAME_BLOCK == null) TIER2_FRAME_BLOCK = GameRegistry.findBlock("gregtech", "bw.frames");
        }
        return TIER2_FRAME_BLOCK;
    }

    private static int getTier2FrameMeta() {
        if (TIER2_FRAME_META == null) {
            Werkstoff werkstoff = WerkstoffLoader.RhodiumPlatedPalladium;
            TIER2_FRAME_META = werkstoff != null ? (int) werkstoff.getmID() : 88;
        }
        return TIER2_FRAME_META;
    }

    @Nullable
    private static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings8 && meta == 6) return 2;
        return null;
    }

    @Nullable
    private static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 1;
        if (block == GregTechAPI.sBlockCasings8 && meta == 6) return 2;
        return null;
    }

    @Nullable
    private static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 1;
        if (block == getTier2FrameBlock() && meta == getTier2FrameMeta()) return 2;
        return null;
    }

    @Nullable
    private static Integer getGlassTier(Block block, int meta) {
        if (block == GTVersionCompat.getReinforcedGlassBlock() && meta == GTVersionCompat.getReinforcedGlassMeta()) {
            return 1;
        }
        Integer glassTier = GlassTier.getGlassBlockTier(block, meta);
        if (glassTier == null || glassTier < VoltageIndex.LuV) return null;
        return 2;
    }

    protected int getCasingTextureIndex() {
        return mTier >= 2 ? GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6)
            : GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    }

    protected int getHatchCasingTextureIndex() {
        return mTier >= 2 ? GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6)
            : GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    }

    protected void updateHatchTextures() {
        int textureID = getHatchCasingTextureIndex();
        for (MTEHatch h : mInputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mOutputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mInputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mPressureSteamInputs) h.updateTexture(textureID);
        for (IDualInputHatch h : mDualInputHatches) h.updateTexture(textureID);
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mTier = aValue;
    }

    @Override
    public byte getUpdateData() {
        return (byte) mTier;
    }

    private static IStructureDefinition<MTESingularityMachineBase> createStructureDefinition() {
        int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        List<Pair<Block, Integer>> casingTiers = new ArrayList<>();
        casingTiers.add(Pair.of(GregTechAPI.sBlockCasings2, 0));
        casingTiers.add(Pair.of(GregTechAPI.sBlockCasings8, 6));
        List<Pair<Block, Integer>> frameTiers = new ArrayList<>();
        frameTiers.add(Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID));
        frameTiers.add(Pair.of(getTier2FrameBlock(), getTier2FrameMeta()));
        List<Pair<Block, Integer>> glassTiers = new ArrayList<>();
        glassTiers.add(Pair.of(GTVersionCompat.getReinforcedGlassBlock(), GTVersionCompat.getReinforcedGlassMeta()));
        glassTiers.add(Pair.of(ItemRegistry.bw_realglas, 3));

        return StructureDefinition.<MTESingularityMachineBase>builder()
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
                    ofBlocksTiered(
                        MTESingularityMachineBase::getCasingTier,
                        casingTiers,
                        -1,
                        (t, tier) -> t.mCasingTierB = tier,
                        t -> t.mCasingTierB),
                    buildHatchAdder(MTESingularityMachineBase.class).atLeast(SingularityHatchElement.SteamInput)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESingularityMachineBase.class).atLeast(SingularityHatchElement.SteamInputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESingularityMachineBase.class).atLeast(SingularityHatchElement.SteamOutputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESingularityMachineBase.class).atLeast(SingularityHatchElement.SteamOutputHatch)
                        .casingIndex(casingIndex)
                        .hint(2)
                        .build()))
            .addElement(
                'C',
                ofBlocksTiered(
                    MTESingularityMachineBase::getPipeTier,
                    casingTiers,
                    -1,
                    (t, tier) -> t.mCasingTierC = tier,
                    t -> t.mCasingTierC))
            .addElement(
                'D',
                ofBlocksTiered(
                    MTESingularityMachineBase::getCasingTier,
                    casingTiers,
                    -1,
                    (t, tier) -> t.mCasingTierD = tier,
                    t -> t.mCasingTierD))
            .addElement(
                'E',
                ofBlocksTiered(
                    MTESingularityMachineBase::getGlassTier,
                    glassTiers,
                    -1,
                    (t, tier) -> t.mCasingTierE = tier,
                    t -> t.mCasingTierE))
            .addElement(
                'F',
                ofBlocksTiered(
                    MTESingularityMachineBase::getFrameTier,
                    frameTiers,
                    -1,
                    (t, tier) -> t.mCasingTierF = tier,
                    t -> t.mCasingTierF))
            .build();
    }

    private enum SingularityHatchElement implements IHatchElement<MTESingularityMachineBase> {

        SteamInput("GTSR.HatchElement.SteamInput", MTESingularityMachineBase::addSteamInputToMachineList,
            MTEHatchInput.class, MTEHatchPressureSteamInput.class) {

            @Override
            public long count(MTESingularityMachineBase t) {
                return t.mInputHatches.size() + t.mPressureSteamInputs.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchPressureSteamInput.class);
            }
        },

        SteamInputBus("GTSR.HatchElement.SteamInputBus", MTESingularityMachineBase::addInputBusToMachineList,
            MTEHatchInputBus.class) {

            @Override
            public long count(MTESingularityMachineBase t) {
                return t.mInputBusses.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchSteamBusInput.class);
            }
        },

        SteamOutputBus("GTSR.HatchElement.SteamOutputBus", MTESingularityMachineBase::addOutputBusToMachineList,
            MTEHatchOutputBus.class) {

            @Override
            public long count(MTESingularityMachineBase t) {
                return t.mOutputBusses.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchSteamBusOutput.class);
            }
        },

        SteamOutputHatch("GTSR.HatchElement.SteamOutputHatch", MTESingularityMachineBase::addOutputHatchToMachineList,
            MTEHatchOutput.class) {

            @Override
            public long count(MTESingularityMachineBase t) {
                return t.mOutputHatches.size();
            }
        };

        private final String translationKey;
        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTESingularityMachineBase> adder;

        @SafeVarargs
        SingularityHatchElement(String translationKey, IGTHatchAdder<MTESingularityMachineBase> adder,
            Class<? extends IMetaTileEntity>... mteClasses) {
            this.translationKey = translationKey;
            this.mteClasses = ImmutableList.copyOf(mteClasses);
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTESingularityMachineBase> adder() {
            return adder;
        }

        @Override
        public String getDisplayName() {
            return GTUtility.translate(translationKey);
        }

        @Override
        public String getDescriptionLangKey() {
            return translationKey;
        }
    }

    public boolean addSteamInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte == null) return false;
        if (mte instanceof MTEHatchInput) return addInputHatchToMachineList(aTileEntity, aBaseCasingIndex);
        if (mte instanceof MTEHatchPressureSteamInput hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mPressureSteamInputs.add(hatch);
        }
        return false;
    }

    public boolean addOutputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte == null) return false;
        if (mte instanceof MTEHatchOutputBus hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.updateCraftingIcon(getMachineCraftingIcon());
            return mOutputBusses.add(hatch);
        }
        return false;
    }

    @Override
    public final IStructureDefinition<MTESingularityMachineBase> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) STRUCTURE_DEFINITION = createStructureDefinition();
        return STRUCTURE_DEFINITION;
    }

    @Override
    public final void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public final int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
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
        mCasingTierB = -1;
        mCasingTierC = -1;
        mCasingTierD = -1;
        mCasingTierE = -1;
        mCasingTierF = -1;
        mPressureSteamInputs.clear();
        mTier = getRequiredTier();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;

        int tier = mCasingTierB;
        if (mCasingTierC != tier || mCasingTierD != tier
            || mCasingTierE != tier
            || mCasingTierF != tier
            || tier != getRequiredTier()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        mTier = tier;

        if ((mInputHatches.isEmpty() && mPressureSteamInputs.isEmpty()) || mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (requiresInputBus() && mInputBusses.isEmpty() && mDualInputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (requiresOutputHatch() && mOutputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        updateHatchTextures();
    }

    protected final CheckRecipeResult processAggregationCycle() {
        int grade = findHighestGrade(includeDenseSteam());
        if (grade < 0) return CheckRecipeResultRegistry.NO_RECIPE;
        long amount = sumGrade(grade, includeDenseSteam());
        drainGrade(grade, includeDenseSteam());
        double base = getHeatMax() * amount / (amount + getHeatHalfPoint());
        mHeat += GRADE_COEF[grade] * base;
        if (mHeat >= 1.0d) {
            addOutputPartial(getAggregationOutput());
            mHeat = 0.0d;
        }
        startCycle();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    protected final void startCycle() {
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mOutputItems = emptyItemStackArray;
        mMaxProgresstime = CYCLE_LENGTH;
    }

    protected List<MTEHatch> getSteamInputHatches() {
        List<MTEHatch> all = new ArrayList<>(mInputHatches.size() + mPressureSteamInputs.size());
        all.addAll(mInputHatches);
        all.addAll(mPressureSteamInputs);
        return all;
    }

    private FluidStack[] gradeProbeStacks(int grade, boolean includeDense, boolean denseOnly) {
        FluidStack normal = FluidRegistry.getFluidStack(NORMAL_FLUID_NAMES[grade], 1);
        FluidStack dense = FluidRegistry.getFluidStack(DENSE_FLUID_NAMES[grade], 1);
        if (denseOnly) return new FluidStack[] { dense };
        if (includeDense) return new FluidStack[] { normal, dense };
        return new FluidStack[] { normal };
    }

    protected final boolean probeGrade(int grade, boolean includeDense, boolean denseOnly) {
        for (FluidStack request : gradeProbeStacks(grade, includeDense, denseOnly)) {
            if (request == null) continue;
            for (MTEHatch hatch : getSteamInputHatches()) {
                FluidStack result = hatch.drain(ForgeDirection.UNKNOWN, request, false);
                if (result != null && result.amount > 0) return true;
            }
        }
        return false;
    }

    protected final int findHighestGrade(boolean includeDense) {
        for (int grade = 2; grade >= 0; grade--) {
            if (probeGrade(grade, includeDense, false)) return grade;
        }
        return -1;
    }

    protected final long sumGrade(int grade, boolean includeDense) {
        long amount = 0;
        for (FluidStack request : gradeProbeStacks(grade, includeDense, false)) {
            if (request == null) continue;
            for (MTEHatch hatch : getSteamInputHatches()) {
                FluidStack full = request.copy();
                full.amount = MAX_DRAIN_PER_CYCLE;
                FluidStack result = hatch.drain(ForgeDirection.UNKNOWN, full, false);
                if (result != null && result.amount > 0) amount += result.amount;
            }
        }
        return amount;
    }

    protected final void drainGrade(int grade, boolean includeDense) {
        for (FluidStack request : gradeProbeStacks(grade, includeDense, false)) {
            if (request == null) continue;
            for (MTEHatch hatch : getSteamInputHatches()) {
                // 按需量实扣：先探测本仓实际可得量（cap 到 MAX_DRAIN_PER_CYCLE），再按探测结果实扣。
                // 修复：原实现以 MAX_VALUE 实扣，对 ME 输入仓（MTEHatchInputME/RestrictedInputHatchME）
                // 会一次拉取整个网络该流体库存（restrict 在 drain 路径完全绕过）。
                FluidStack full = request.copy();
                full.amount = MAX_DRAIN_PER_CYCLE;
                FluidStack available = hatch.drain(ForgeDirection.UNKNOWN, full, false);
                if (available != null && available.amount > 0) {
                    FluidStack toDrain = available.copy();
                    toDrain.amount = Math.min(available.amount, MAX_DRAIN_PER_CYCLE);
                    hatch.drain(ForgeDirection.UNKNOWN, toDrain, true);
                }
            }
        }
    }

    protected final int fillOutput(FluidStack stack) {
        int remaining = stack.amount;
        for (MTEHatchOutput hatch : mOutputHatches) {
            if (remaining <= 0) break;
            FluidStack toFill = stack.copy();
            toFill.amount = remaining;
            remaining -= hatch.fill(toFill, true);
        }
        return stack.amount - remaining;
    }

    protected final boolean consumeSingularityFromInputBuses(int amount) {
        ItemStack singularity = GTSRItemList.SteamEntangledSingularity.get(1);
        if (singularity == null) return false;
        for (MTEHatchInputBus bus : mInputBusses) {
            for (int i = 0; i < bus.getSizeInventory(); i++) {
                ItemStack stack = bus.getStackInSlot(i);
                if (stack != null && stack.getItem() == singularity.getItem() && stack.stackSize >= amount) {
                    bus.decrStackSize(i, amount);
                    return true;
                }
            }
        }
        for (IDualInputHatch dual : mDualInputHatches) {
            for (ItemStack stack : dual.getAllItems()) {
                if (stack != null && stack.getItem() == singularity.getItem() && stack.stackSize >= amount) {
                    stack.stackSize -= amount;
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean shouldDecayHeat() {
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || aTick % CYCLE_LENGTH != 0L || !shouldDecayHeat()) return;
        if (!mMachine || !aBaseMetaTileEntity.isAllowedToWork()) {
            mHeat = Math.max(0.0d, mHeat - HEAT_DECAY_PER_SECOND);
            return;
        }
        if (mMaxProgresstime <= 0 && findHighestGrade(includeDenseSteam()) < 0) {
            mHeat = Math.max(0.0d, mHeat - HEAT_DECAY_PER_SECOND);
        }
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
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
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTESingularityMachineGui<>(this);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        int casingIndex = getCasingTextureIndex();
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex),
                TextureFactory.of(aActive ? OVERLAY_ON : OVERLAY_OFF) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex) };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        String keyPrefix = getTooltipKeyPrefix();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal(keyPrefix + "type"))
            .addInfo(StatCollector.translateToLocal(keyPrefix + "desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(keyPrefix + "desc2"))
            .addInfo(EnumChatFormatting.GREEN + StatCollector.translateToLocal(keyPrefix + "desc3"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal(keyPrefix + "desc4"))
            .addInfo(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc5"))
            .addSeparator()
            .beginStructureBlock(11, 11, 11, false)
            .addController(StatCollector.translateToLocal(keyPrefix + "ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal(keyPrefix + "steam_input"),
                1);
        if (requiresInputBus()) {
            tt.addInputBus(StatCollector.translateToLocal(keyPrefix + "input_bus"), 1);
        }
        tt.addOutputBus(StatCollector.translateToLocal(keyPrefix + "output_bus"), 1);
        if (requiresOutputHatch()) {
            tt.addOutputHatch(StatCollector.translateToLocal(keyPrefix + "output_hatch"), 1);
        }
        tt.addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "tier1_blocks"))
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "tier2_blocks"))
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
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mTier", mTier);
        aNBT.setDouble("mHeat", mHeat);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mTier = aNBT.getInteger("mTier");
        mHeat = aNBT.getDouble("mHeat");
    }

    @Override
    public String[] getInfoData() {
        String tooltipKeyPrefix = getTooltipKeyPrefix();
        String guiKeyPrefix = getGuiKeyPrefix();
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal(tooltipKeyPrefix + "type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(guiKeyPrefix + "heat")
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mHeat * 100.0d)
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(guiKeyPrefix + "tier")
                + EnumChatFormatting.GOLD
                + mTier
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }
}
