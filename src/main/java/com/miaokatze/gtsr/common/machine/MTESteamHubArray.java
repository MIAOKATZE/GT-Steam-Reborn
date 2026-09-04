package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.structure.GTSRStructureChecks.require;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTESteamHubArrayGui;
import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEHubArrayBase;
import com.miaokatze.gtsr.common.machine.base.MTEHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureSteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedSteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTESteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamStorageUnit;
import com.miaokatze.gtsr.common.terminal.TerminalNet;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;
import com.miaokatze.gtsr.common.util.UnitFormatUtil;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.GTStructureChannels;

public class MTESteamHubArray extends MTEHubArrayBase<MTESteamHubArray>
    implements IConstructable, ISurvivalConstructable {

    // 结构探偏移（construct/survivalConstruct 与基类 checkMachine 探高模板共用）
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 0;
    private static final int DEPTH_OFF_SET = 1;
    /** 自动输出速率：每 tick 1,000,000 L = 20,000,000 L/s */
    private static final int AUTO_OUTPUT_RATE = 1_000_000;

    private static final IStructureDefinition<MTESteamHubArray> STRUCTURE_DEFINITION;

    static {
        STRUCTURE_DEFINITION = StructureDefinition.<MTESteamHubArray>builder()
            .addShape(
                STRUCTURE_PIECE_BASE,
                transpose(
                    new String[][] { { "         ", "   F~F   ", "  ECCCE  ", " FCCCCCF ", " DCCCCCD ", " FCCCCCF ",
                        "  ECCCE  ", "   FDF   ", "         " } }))
            .addShape(
                STRUCTURE_PIECE_STACK,
                transpose(
                    new String[][] { { "         ", "   FDF   ", "  AAAAA  ", " FAAAAAF ", " DAAAAAD ", " FAAAAAF ",
                        "  AAAAA  ", "   FDF   ", "         " } }))
            .addShape(
                STRUCTURE_PIECE_CAP,
                transpose(
                    new String[][] { { "   CCC   ", "  CFCFC  ", " CCCCCCC ", "CFCCCCCFC", "CCCCCCCCC", "CFCCCCCFC",
                        " CCCCCCC ", "  CFCFC  ", "   CCC   " } }))
            .addElement(
                'A',
                ofChain(
                    // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                    onElementPass(
                        MTESteamHubArray::onCasingAdded,
                        ofBlocksTiered(
                            MTESteamHubArray::getCasingTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockCasings1, 10),
                                Pair.of(GregTechAPI.sBlockCasings2, 0),
                                Pair.of(GregTechAPI.sBlockCasings4, 0)),
                            -1,
                            (MTESteamHubArray t, Integer tier) -> t.mCasingTier = tier,
                            (MTESteamHubArray t) -> t.mCasingTier)),
                    buildHatchAdder(MTESteamHubArray.class)
                        .atLeast(
                            SteamHubStorageElement.PressureUnit,
                            SteamHubStorageElement.ReinforcedUnit,
                            SteamHubStorageElement.OverpressureUnit)
                        .casingIndex(CASING_INDEX)
                        .hint(2)
                        .build()))
            .addElement(
                'C',
                ofChain(
                    // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                    onElementPass(
                        MTESteamHubArray::onCasingAdded,
                        ofBlocksTiered(
                            MTESteamHubArray::getCasingTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockCasings1, 10),
                                Pair.of(GregTechAPI.sBlockCasings2, 0),
                                Pair.of(GregTechAPI.sBlockCasings4, 0)),
                            -1,
                            (MTESteamHubArray t, Integer tier) -> t.mCasingTier = tier,
                            (MTESteamHubArray t) -> t.mCasingTier)),
                    buildHatchAdder(MTESteamHubArray.class).atLeast(
                        // S1：奇点仓两枚举在前——更具体的 adder 先认领，防止后续泛化 adder 的
                        // instanceof 误收（枚举间 adder 按声明顺序短路）
                        SteamHubHatchElement.SingularitySteamCompartment,
                        SteamHubHatchElement.SingularitySteamOutputCompartment,
                        SteamHubHatchElement.SteamInput,
                        SteamHubHatchElement.SteamOutput)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .build()))
            .addElement(
                'D',
                onElementPass(
                    MTESteamHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTESteamHubArray::getPipeTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockCasings2, 12),
                            Pair.of(GregTechAPI.sBlockCasings2, 13),
                            Pair.of(GregTechAPI.sBlockCasings2, 15)),
                        -1,
                        (MTESteamHubArray t, Integer tier) -> t.mPipeTier = tier,
                        (MTESteamHubArray t) -> t.mPipeTier)))
            .addElement(
                'E',
                onElementPass(
                    MTESteamHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTESteamHubArray::getGearTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockCasings2, 2),
                            Pair.of(GregTechAPI.sBlockCasings2, 3),
                            Pair.of(GregTechAPI.sBlockCasings2, 15)),
                        -1,
                        (MTESteamHubArray t, Integer tier) -> t.mGearTier = tier,
                        (MTESteamHubArray t) -> t.mGearTier)))
            .addElement(
                'F',
                onElementPass(
                    MTESteamHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTESteamHubArray::getFrameTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID),
                            Pair.of(GregTechAPI.sBlockFrames, Materials.TungstenSteel.mMetaItemSubID)),
                        -1,
                        (MTESteamHubArray t, Integer tier) -> t.mFrameTier = tier,
                        (MTESteamHubArray t) -> t.mFrameTier)))
            .build();
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
        if (block == GregTechAPI.sBlockCasings4 && meta == 0) return 3;
        return null;
    }

    @Nullable
    public static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 12) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 2;
        if (block == GregTechAPI.sBlockCasings2 && meta == 15) return 3;
        return null;
    }

    @Nullable
    public static Integer getGearTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 2) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 3) return 2;
        if (block == GregTechAPI.sBlockCasings2 && meta == 15) return 3;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.TungstenSteel.mMetaItemSubID) return 3;
        return null;
    }

    private enum SteamHubHatchElement implements IHatchElement<MTESteamHubArray> {

        // S1：奇点仓两枚举（结构接纳，问题 2）；声明顺序 = atLeast adder 认领顺序（具体在前）
        SingularitySteamCompartment(MTESteamHubArray::addSingularitySteamCompartmentToMachineList,
            MTESingularitySteamCompartment.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTESingularitySteamCompartment.class);
            }
        },
        SingularitySteamOutputCompartment(MTESteamHubArray::addSingularitySteamOutputCompartmentToMachineList,
            MTESingularitySteamOutputCompartment.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTESingularitySteamOutputCompartment.class);
            }
        },
        SteamInput(MTESteamHubArray::addSteamInputToMachineList, MTESteamHubInputHatch.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTESteamHubInputHatch.class);
            }
        },
        SteamOutput(MTESteamHubArray::addSteamOutputToMachineList, MTESteamHubOutputHatch.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTESteamHubOutputHatch.class);
            }
        };

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTESteamHubArray> adder;

        @SafeVarargs
        SteamHubHatchElement(IGTHatchAdder<MTESteamHubArray> adder, Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTESteamHubArray> adder() {
            return adder;
        }

        @Override
        public long count(MTESteamHubArray t) {
            if (this == SteamInput) return t.mSteamInputHatches.size();
            if (this == SteamOutput) return t.mSteamOutputHatches.size();
            if (this == SingularitySteamCompartment) return t.mSingularitySteamCompartmentCount;
            return t.mSingularitySteamOutputCompartmentCount;
        }
    }

    private enum SteamHubStorageElement implements IHatchElement<MTESteamHubArray> {

        PressureUnit(MTESteamHubArray::addPressureUnitToMachineList, MTEHubStorageUnit.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHubStorageUnit.class);
            }
        },
        ReinforcedUnit(MTESteamHubArray::addReinforcedUnitToMachineList, MTEReinforcedHubStorageUnit.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEReinforcedHubStorageUnit.class);
            }
        },
        OverpressureUnit(MTESteamHubArray::addOverpressureUnitToMachineList, MTEOverpressureHubStorageUnit.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEOverpressureHubStorageUnit.class);
            }
        };

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTESteamHubArray> adder;

        @SafeVarargs
        SteamHubStorageElement(IGTHatchAdder<MTESteamHubArray> adder, Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTESteamHubArray> adder() {
            return adder;
        }

        @Override
        public long count(MTESteamHubArray t) {
            if (this == PressureUnit) return t.mPressureUnitCount;
            if (this == ReinforcedUnit) return t.mReinforcedUnitCount;
            return t.mOverpressureUnitCount;
        }
    }

    private final ArrayList<MTESteamHubInputHatch> mSteamInputHatches = new ArrayList<>();
    private final ArrayList<MTESteamHubOutputHatch> mSteamOutputHatches = new ArrayList<>();

    // S1：奇点仓结构接纳计数（仅结构组成提示用，不注入 mController、不参与传输——传输走终端绑定链）
    private int mSingularitySteamCompartmentCount = 0;
    private int mSingularitySteamOutputCompartmentCount = 0;

    public int mPressureUnitCount = 0;
    public int mReinforcedUnitCount = 0;
    public int mOverpressureUnitCount = 0;
    private int mCasingAmount = 0;
    private int mGearTier = -1;
    public long mSteamStored = 0;
    private FluidStack mStoredFluidType = null;
    private long mTickCounter = 0;

    public MTESteamHubArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTESteamHubArray(String aName) {
        super(aName);
        registerProgressEntries();
    }

    // GTSR 进度词条：注册顺序 = GUI 终端显示顺序（存储单元/蒸汽缓冲/总容量；等级/芯片/状态为文本行保留在 GUI）
    private void registerProgressEntries() {
        // 存储单元：显示 "已装/上限(25×堆叠)"，formatter 内读机器字段拼上限
        registerEntryCustom(
            "storage_units",
            "gtsr.gui.steam_hub.storage_units",
            EnumChatFormatting.GOLD,
            () -> mPressureUnitCount + mReinforcedUnitCount + mOverpressureUnitCount,
            v -> (long) v + "/" + (25 * mStackCount));
        registerEntryCustom(
            "steam_buffer",
            "gtsr.gui.steam_hub.steam_buffer",
            EnumChatFormatting.LIGHT_PURPLE,
            () -> mSteamStored,
            v -> UnitFormatUtil.format((long) v) + " L");
        registerEntryCustom(
            "total_capacity",
            "gtsr.gui.steam_hub.total_capacity",
            EnumChatFormatting.LIGHT_PURPLE,
            () -> getTotalCapacity(),
            v -> UnitFormatUtil.format((long) v) + " L");
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamHubArray(mName);
    }

    @Override
    public IStructureDefinition<MTESteamHubArray> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    // A04-H4 结构校验钩子（checkMachine 骨架见 MTEHubArrayBase：reset→BASE→探高→CAP→tier 一致→
    // 三段互斥→贴图→issueTileUpdate；失败分支复位动作时序由模板逐字保留）

    @Override
    protected int horizontalOffset() {
        return HORIZONTAL_OFF_SET;
    }

    @Override
    protected int verticalOffset() {
        return VERTICAL_OFF_SET;
    }

    @Override
    protected int depthOffset() {
        return DEPTH_OFF_SET;
    }

    @Override
    protected void detachHatchControllers() {
        for (MTESteamHubInputHatch hatch : mSteamInputHatches) {
            hatch.mController = null;
        }
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            hatch.mController = null;
        }
    }

    @Override
    protected void resetFamilyStructureState() {
        mPressureUnitCount = 0;
        mReinforcedUnitCount = 0;
        mOverpressureUnitCount = 0;
        mCasingAmount = 0;
        mGearTier = -1;
        mSteamInputHatches.clear();
        mSteamOutputHatches.clear();
        mSingularitySteamCompartmentCount = 0;
        mSingularitySteamOutputCompartmentCount = 0;
    }

    @Override
    protected boolean checkCapPiece(List<StructureError> errors) {
        return checkPiece(STRUCTURE_PIECE_CAP, HORIZONTAL_OFF_SET, -1, DEPTH_OFF_SET, errors);
    }

    @Override
    protected boolean areTiersComplete() {
        return mCasingTier > 0 && mPipeTier > 0 && mGearTier > 0 && mFrameTier > 0;
    }

    @Override
    protected boolean areTiersConsistent() {
        return mCasingTier == mPipeTier && mCasingTier == mGearTier && mCasingTier == mFrameTier;
    }

    @Override
    protected boolean checkUnitRules(List<StructureError> errors) {
        if (!require(mSetTier != 1 || (mReinforcedUnitCount <= 0 && mOverpressureUnitCount <= 0), errors)) {
            return false;
        }
        if (!require(mSetTier != 2 || (mPressureUnitCount <= 0 && mOverpressureUnitCount <= 0), errors)) {
            return false;
        }
        if (!require(mSetTier < 3 || (mPressureUnitCount <= 0 && mReinforcedUnitCount <= 0), errors)) {
            return false;
        }
        if (!require(mSetTier < 3 || mOverpressureUnitCount > 0, errors)) return false;
        return require((mPressureUnitCount + mReinforcedUnitCount + mOverpressureUnitCount) > 0, errors);
    }

    @Override
    protected void updateHatchTextures(int tierCasingIndex) {
        for (MTESteamHubInputHatch hatch : mSteamInputHatches) {
            hatch.updateTexture(tierCasingIndex);
        }
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            hatch.updateTexture(tierCasingIndex);
        }
    }

    private void onCasingAdded() {
        mCasingAmount++;
    }

    public boolean addSteamInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTESteamHubInputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.mController = this;
            return mSteamInputHatches.add(hatch);
        }
        return false;
    }

    public boolean addSteamOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTESteamHubOutputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.mController = this;
            return mSteamOutputHatches.add(hatch);
        }
        return false;
    }

    // S1：奇点仓结构接纳（问题 2）——只计数与换装外壳材质，不注入 mController、不加入输入/输出运转列表
    // （仓与枢纽的流体交互走终端绑定链 transferWithBoundNodes，与结构成员资格无关）
    public boolean addSingularitySteamCompartmentToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTESingularitySteamCompartment hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            mSingularitySteamCompartmentCount++;
            return true;
        }
        return false;
    }

    public boolean addSingularitySteamOutputCompartmentToMachineList(IGregTechTileEntity aTileEntity,
        int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTESingularitySteamOutputCompartment hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            mSingularitySteamOutputCompartmentCount++;
            return true;
        }
        return false;
    }

    public boolean addPressureUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEHubStorageUnit) {
            mPressureUnitCount++;
            return true;
        }
        return false;
    }

    public boolean addReinforcedUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEReinforcedHubStorageUnit) {
            mReinforcedUnitCount++;
            return true;
        }
        return false;
    }

    public boolean addOverpressureUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEOverpressureHubStorageUnit) {
            mOverpressureUnitCount++;
            return true;
        }
        return false;
    }

    @Override
    public int receiveFluid(FluidStack fluid, boolean doFill) {
        return receiveSteam(fluid, doFill);
    }

    @Override
    public FluidStack extractFluid(int amount, boolean doDrain) {
        return extractSteam(amount, doDrain);
    }

    public int receiveSteam(FluidStack aFluid, boolean doFill) {
        if (aFluid == null) return 0;
        boolean isAllowed = mSetTier >= 3 && hasReinforcedChipInstalled()
            ? MTESteamHubOutputHatch.isAnySteamFluid(aFluid)
            : MTESteamHubOutputHatch.isSteamFluid(aFluid);
        if (!isAllowed) return 0;
        if (mStoredFluidType != null && !mStoredFluidType.isFluidEqual(aFluid)) return 0;

        long capacity = getTotalCapacity();
        long canAccept = capacity - mSteamStored;

        if (mOverflowInput) {
            if (doFill) {
                if (mStoredFluidType == null) {
                    mStoredFluidType = new FluidStack(aFluid.getFluid(), 0);
                }
                long actualStore = Math.min(aFluid.amount, canAccept);
                mSteamStored += actualStore;
            }
            return aFluid.amount;
        }

        int toAccept = (int) Math.min(aFluid.amount, canAccept);

        if (doFill && toAccept > 0) {
            if (mStoredFluidType == null) {
                mStoredFluidType = new FluidStack(aFluid.getFluid(), 0);
            }
            mSteamStored += toAccept;
        }

        return toAccept;
    }

    public FluidStack extractSteam(int maxDrain, boolean doDrain) {
        if (mSteamStored <= 0 || mStoredFluidType == null) return null;

        int toDrain = (int) Math.min(maxDrain, mSteamStored);
        FluidStack result = new FluidStack(mStoredFluidType.getFluid(), toDrain);

        if (doDrain) {
            mSteamStored -= toDrain;
            if (mSteamStored <= 0) {
                mStoredFluidType = null;
            }
        }

        return result;
    }

    public FluidStack getStoredFluidStack() {
        if (mStoredFluidType == null || mSteamStored <= 0) return null;
        int amount = (int) Math.min(mSteamStored, Integer.MAX_VALUE);
        return new FluidStack(mStoredFluidType.getFluid(), amount);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_CAP, stackSize, hintsOnly, HORIZONTAL_OFF_SET, -1, DEPTH_OFF_SET);
        buildPiece(STRUCTURE_PIECE_BASE, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
        int tTotalHeight = Math.max(3, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 3, 32));
        int stackCount = tTotalHeight - 2;
        for (int i = 0; i < stackCount; i++) {
            int bOffset = 1 + i;
            buildPiece(STRUCTURE_PIECE_STACK, stackSize, hintsOnly, HORIZONTAL_OFF_SET, bOffset, DEPTH_OFF_SET);
        }
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(
            STRUCTURE_PIECE_CAP,
            stackSize,
            HORIZONTAL_OFF_SET,
            -1,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        built = survivalBuildPiece(
            STRUCTURE_PIECE_BASE,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        int tTotalHeight = Math.max(3, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 3, 32));
        int stackCount = tTotalHeight - 2;
        for (int i = 0; i < stackCount; i++) {
            int bOffset = 1 + i;
            built = survivalBuildPiece(
                STRUCTURE_PIECE_STACK,
                stackSize,
                HORIZONTAL_OFF_SET,
                bOffset,
                DEPTH_OFF_SET,
                elementBudget,
                env,
                false,
                true);
            if (built >= 0) return built;
        }
        return -1;
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] { EnumChatFormatting.AQUA + "Structure:", "1. CAP (1 layer): Base foundation (bottom)",
            "2. BASE (1 layer): Controller layer", "3. STACK (1 layer): Repeatable storage unit layer (1~30)",
            "4. Total height: 3~32 layers (9x9x3 to 9x9x32)", "5. At least 1 Input Hatch and 1 Output Hatch required" };
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return false;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return false;
    }

    @Override
    public long getTotalCapacity() {
        long base = (long) mPressureUnitCount * MTESteamStorageUnit.PRESSURE_CAPACITY
            + (long) mReinforcedUnitCount * MTESteamStorageUnit.REINFORCED_CAPACITY
            + (long) mOverpressureUnitCount * MTEOverpressureHubStorageUnit.OVERPRESSURE_CAPACITY;
        if (hasReinforcedChipInstalled()) return base * 20;
        return hasHubChipInstalled() ? base * 5 : base;
    }

    public long getSteamStored() {
        return mSteamStored;
    }

    public int getPressureUnitCount() {
        return mPressureUnitCount;
    }

    public int getReinforcedUnitCount() {
        return mReinforcedUnitCount;
    }

    // A04-H3 族差异钩子（服务 tick 骨架见 MTEHubArrayBase.onPostTick：容量钳制→自动输出→同步去重→周期分派）
    @Override
    public long getStoredFluidAmount() {
        return mSteamStored;
    }

    @Override
    protected void setStoredFluidAmount(long amount) {
        mSteamStored = amount;
    }

    @Override
    protected String storedFluidNameForSync() {
        return mStoredFluidType != null ? mStoredFluidType.getFluid()
            .getName() : "";
    }

    @Override
    protected void onBoundTransferTick(long aTick) {
        mTickCounter++;
        if (mTickCounter % 20 == 0) {
            transferWithBoundNodes();
        }
    }

    @Override
    protected void autoOutputStored() {
        if (mSteamStored <= 0 || mStoredFluidType == null) return;
        long capacity = getTotalCapacity();
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            if (mSteamStored <= 0) break;
            if (hatch.mOverflowOutput && mSteamStored < (long) (capacity * 0.9)) continue;
            IGregTechTileEntity hatchBase = hatch.getBaseMetaTileEntity();
            if (hatchBase == null) continue;
            ForgeDirection hatchFront = hatchBase.getFrontFacing();
            IFluidHandler adjacent = hatchBase.getITankContainerAtSide(hatchFront);
            if (adjacent == null) continue;

            int toPush = (int) Math.min(AUTO_OUTPUT_RATE, mSteamStored);
            FluidStack toExport = new FluidStack(mStoredFluidType.getFluid(), toPush);
            int pushed = adjacent.fill(hatchFront.getOpposite(), toExport, true);
            if (pushed > 0) {
                mSteamStored -= pushed;
                if (mSteamStored <= 0) {
                    mStoredFluidType = null;
                    return;
                }
            }
        }
    }

    // 蒸汽枢纽族绑定差异钩子（绑定流主体见 MTEHubArrayBase.onRightclick / bindOne / bindWhole 模板）
    @Override
    protected String resolveHeldType(ItemStack held) {
        if (GTSRItemList.SteamCacheNode.isStackEqual(held, false, true)) return "steam";
        if (GTSRItemList.ReinforcedSteamCacheNode.isStackEqual(held, false, true)) return "reinforced_steam";
        if (GTSRItemList.OverpressureSteamCacheNode.isStackEqual(held, false, true)) return "overpressure_steam";
        if (GTSRItemList.SingularitySteamCompartment.isStackEqual(held, false, true)) return "singularity_steam";
        if (GTSRItemList.SingularitySteamOutputCompartment.isStackEqual(held, false, true)) {
            return "singularity_steam_out";
        }
        return null;
    }

    @Override
    protected boolean isReinforcedType(String type) {
        return "reinforced_steam".equals(type);
    }

    @Override
    protected boolean requiresReinforcedChipToBind(String type) {
        return "overpressure_steam".equals(type);
    }

    /** 绑定奇点成本表：steam=0、reinforced_steam=1、overpressure_steam=8、奇点蒸汽仓/输出仓=1。 */
    @Override
    protected int getBindSingularityCost(String type) {
        if ("reinforced_steam".equals(type)) return 1;
        if ("overpressure_steam".equals(type)) return 8;
        if ("singularity_steam".equals(type) || "singularity_steam_out".equals(type)) return 1;
        return 0;
    }

    @Override
    protected Pair<String, String> singularityCompartmentTypes() {
        return Pair.of("singularity_steam", "singularity_steam_out");
    }

    @Override
    protected void writeBindExtras(NBTTagCompound hubTag, boolean isReinforced) {
        hubTag.setBoolean("reinforced", isReinforced);
    }

    @Override
    public boolean acceptsNodeType(String type) {
        return "steam".equals(type) || "reinforced_steam".equals(type)
            || "overpressure_steam".equals(type)
            || "singularity_steam".equals(type)
            || "singularity_steam_out".equals(type);
    }

    /**
     * 打开缓存节点状态管理界面（terminal-native-ui 轨 A：S2C open 包 + 客户端本地 displayGuiScreen，
     * 零 windowId）。必须在服务端调用；守卫语义照旧（EntityPlayerMP/FakePlayer/基 TE 存活，
     * 与原服务端 open 守卫一致），实际打开由 TerminalClientPacketSink 双校验后承载。
     */
    @Override
    public void openHubStatusGui(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP playerMP) || player instanceof FakePlayer) return;
        IGregTechTileEntity base = this.getBaseMetaTileEntity();
        if (base == null) return;
        TerminalNet
            .sendOpen(TerminalUiType.STEAM_HUB, playerMP, base.getXCoord(), base.getYCoord(), base.getZCoord(), 0);
    }

    /**
     * 按实际节点类判定类型字符串（不用缓存字段，避免 BoundCacheNode 无 reinforced 字段时误判）。
     * 各节点类与四仓互不继承、共同实现 IHubCacheNode（S1 起），instanceof 顺序无关。
     */
    @Override
    protected String resolveNodeType(IHubCacheNode node) {
        if (node instanceof MTEOverpressureSteamCacheNode) return "overpressure_steam";
        if (node instanceof MTEReinforcedSteamCacheNode) return "reinforced_steam";
        if (node instanceof MTESteamCacheNode) return "steam";
        if (node instanceof MTESingularitySteamOutputCompartment) return "singularity_steam_out";
        if (node instanceof MTESingularitySteamCompartment) return "singularity_steam";
        return "";
    }

    /** 单节点传输（Steam FluidStack 锁）：output 分支 extractSteam→fill 实扣，input 分支 drain→receiveSteam。 */
    @Override
    protected void transferOneNode(BoundCacheNode node, IGregTechTileEntity gte, int nodeRate) {
        if (node.isOutputMode) {
            FluidStack toSend = extractSteam(nodeRate, false);
            if (toSend != null && toSend.amount > 0) {
                int filled = gte.fill(ForgeDirection.UNKNOWN, toSend, true);
                if (filled > 0) extractSteam(filled, true);
            }
        } else {
            FluidStack drained = gte.drain(ForgeDirection.UNKNOWN, nodeRate, false);
            if (drained != null && drained.amount > 0) {
                int received = receiveSteam(drained, true);
                if (received > 0) gte.drain(ForgeDirection.UNKNOWN, received, true);
            }
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("mSteamStored", mSteamStored);
        aNBT.setInteger("mPressureUnitCount", mPressureUnitCount);
        aNBT.setInteger("mReinforcedUnitCount", mReinforcedUnitCount);
        aNBT.setInteger("mOverpressureUnitCount", mOverpressureUnitCount);
        aNBT.setInteger("mGearTier", mGearTier);
        aNBT.setLong("mTickCounter", mTickCounter);
        if (mStoredFluidType != null) {
            NBTTagCompound fluidTag = new NBTTagCompound();
            mStoredFluidType.writeToNBT(fluidTag);
            aNBT.setTag("mStoredFluidType", fluidTag);
        }
    }

    /** 绑定列表 Steam 格式：NBTTagList 逐项（x/y/z/dim/reinforced/outputMode，键名存档契约不动）。 */
    @Override
    protected void saveBoundNodes(NBTTagCompound aNBT) {
        NBTTagList boundList = new NBTTagList();
        for (BoundCacheNode node : mBoundNodes) {
            NBTTagCompound nodeTag = new NBTTagCompound();
            nodeTag.setInteger("x", node.x);
            nodeTag.setInteger("y", node.y);
            nodeTag.setInteger("z", node.z);
            nodeTag.setInteger("dim", node.dimensionId);
            nodeTag.setBoolean("reinforced", node.isReinforced);
            nodeTag.setBoolean("outputMode", node.isOutputMode);
            boundList.appendTag(nodeTag);
        }
        aNBT.setTag("mBoundNodes", boundList);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSteamStored = aNBT.getLong("mSteamStored");
        mPressureUnitCount = aNBT.getInteger("mPressureUnitCount");
        mReinforcedUnitCount = aNBT.getInteger("mReinforcedUnitCount");
        mOverpressureUnitCount = aNBT.getInteger("mOverpressureUnitCount");
        mGearTier = aNBT.getInteger("mGearTier");
        mTickCounter = aNBT.getLong("mTickCounter");
        if (aNBT.hasKey("mStoredFluidType")) {
            mStoredFluidType = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mStoredFluidType"));
        }
    }

    @Override
    protected void loadBoundNodes(NBTTagCompound aNBT) {
        NBTTagList boundList = aNBT.getTagList("mBoundNodes", 10);
        for (int i = 0; i < boundList.tagCount(); i++) {
            NBTTagCompound nodeTag = boundList.getCompoundTagAt(i);
            int x = nodeTag.getInteger("x");
            int y = nodeTag.getInteger("y");
            int z = nodeTag.getInteger("z");
            int dim = nodeTag.getInteger("dim");
            boolean reinforced = nodeTag.getBoolean("reinforced");
            boolean outputMode = nodeTag.getBoolean("outputMode");
            mBoundNodes.add(new BoundCacheNode(x, y, z, dim, reinforced, outputMode));
        }
    }

    // A04-H4 展示层钩子（描述同步/正面贴图/GUI 文本/Waila/tooltip 模板见 MTEHubArrayBase 展示层 region）

    @Override
    protected String guiLangPrefix() {
        return "steam_hub";
    }

    @Override
    protected int unitsPerStack() {
        return 25;
    }

    @Override
    protected int getTotalUnitCount() {
        return mPressureUnitCount + mReinforcedUnitCount + mOverpressureUnitCount;
    }

    @Override
    protected String bufferLangKey() {
        return "gtsr.gui.steam_hub.steam_buffer";
    }

    @Override
    protected Fluid familyFallbackFluid() {
        return FluidRegistry.getFluid("steam");
    }

    /** 历史行为保留：Waila 等级行仅两档（tier≥3 显示 bronze 文案，与 GUI/drawTexts 三档不一致为现状）。 */
    @Override
    protected String tierDisplayText() {
        return StatCollector.translateToLocal(mSetTier == 2 ? "gtsr.gui.tier.steel" : "gtsr.gui.tier.bronze");
    }

    @Override
    protected int[] tooltipStructureDims() {
        return new int[] { 9, 32, 9 };
    }

    @Override
    protected void addFamilyStructureInfo(MultiblockTooltipBuilder tt) {
        tt.addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 70, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 7, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 24, false);
    }

    @Override
    protected void addFamilySyncers(DynamicPositionedColumn screenElements) {
        screenElements
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mPressureUnitCount, val -> mPressureUnitCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mReinforcedUnitCount, val -> mReinforcedUnitCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mOverpressureUnitCount, val -> mOverpressureUnitCount = val))
            .widget(new FakeSyncWidget.LongSyncer(() -> mSteamStored, val -> mSteamStored = val));
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new MTESteamHubArrayGui(this);
    }
}
