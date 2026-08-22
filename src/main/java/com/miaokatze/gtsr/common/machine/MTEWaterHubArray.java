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
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
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
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTEWaterHubArrayGui;
import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEHubArrayBase;
import com.miaokatze.gtsr.common.machine.base.MTEHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureWaterCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedWaterCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEWaterCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEWaterHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEWaterHubOutputHatch;
import com.miaokatze.gtsr.common.util.GTSRFluidWindowTexture;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.common.util.UnitFormatUtil;
import com.miaokatze.gtsr.register.TextureManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.GTStructureChannels;

public class MTEWaterHubArray extends MTEHubArrayBase<MTEWaterHubArray>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_BASE = "base";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 0;
    private static final int DEPTH_OFF_SET = 0;
    /** 自动输出速率：每 tick 64,000 L = 1,280,000 L/s */
    private static final int AUTO_OUTPUT_RATE = 64_000;
    private static final int HUB_UNIT_CAPACITY = 1_280_000;
    private static final int REINFORCED_HUB_UNIT_CAPACITY = 5_120_000;
    private static final int OVERPRESSURE_HUB_UNIT_CAPACITY = 20_480_000;
    private static final int BOUND_TRANSFER_INTERVAL = 20;

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    // 正面枢纽框架层（customAlpha pass1 图标，extFacing 对齐多方块旋转）：registerIcons（仅客户端）时
    // 构造并静态缓存，getTexture 复用勿每调用 new，服务端类加载不触碰渲染类
    private static ITexture FRAME_UNBOUND_FACING;

    private static final IStructureDefinition<MTEWaterHubArray> STRUCTURE_DEFINITION;

    static {
        STRUCTURE_DEFINITION = StructureDefinition.<MTEWaterHubArray>builder()
            .addShape(
                STRUCTURE_PIECE_BASE,
                transpose(
                    new String[][] { { "  C~C  ", " CDCDC ", "CDCCCDC", "ECCCCCE", "CDCCCDC", " CDCDC ", "  CEC  " } }))
            .addShape(
                STRUCTURE_PIECE_STACK,
                transpose(
                    new String[][] { { "       ", "  DCD  ", " DAAAD ", "ECAAACE", " DAAAD ", "  DCD  ", "   E   " } }))
            .addElement(
                'A',
                ofChain(
                    // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                    onElementPass(
                        MTEWaterHubArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEWaterHubArray::getCasingTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockCasings1, 10),
                                Pair.of(GregTechAPI.sBlockCasings2, 0),
                                Pair.of(GregTechAPI.sBlockCasings4, 0)),
                            -1,
                            (MTEWaterHubArray t, Integer tier) -> t.mCasingTier = tier,
                            (MTEWaterHubArray t) -> t.mCasingTier)),
                    buildHatchAdder(MTEWaterHubArray.class)
                        .atLeast(
                            WaterHubStorageElement.HubUnit,
                            WaterHubStorageElement.ReinforcedHubUnit,
                            WaterHubStorageElement.OverpressureUnit)
                        .casingIndex(CASING_INDEX)
                        .hint(2)
                        .build()))
            .addElement(
                'C',
                ofChain(
                    // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                    onElementPass(
                        MTEWaterHubArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEWaterHubArray::getCasingTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockCasings1, 10),
                                Pair.of(GregTechAPI.sBlockCasings2, 0),
                                Pair.of(GregTechAPI.sBlockCasings4, 0)),
                            -1,
                            (MTEWaterHubArray t, Integer tier) -> t.mCasingTier = tier,
                            (MTEWaterHubArray t) -> t.mCasingTier)),
                    buildHatchAdder(MTEWaterHubArray.class).atLeast(
                        // S1：奇点仓两枚举必须在前——流体输入/输出仓分别继承 MTEWaterHubInputHatch/
                        // MTEWaterHubOutputHatch，泛化 adder 的 instanceof 会误收子类（R4），
                        // 枚举间 adder 按声明顺序短路，具体类先认领
                        WaterHubHatchElement.SingularityFluidInputCompartment,
                        WaterHubHatchElement.SingularityFluidOutputCompartment,
                        WaterHubHatchElement.WaterOutput,
                        WaterHubHatchElement.WaterInput)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .build()))
            .addElement(
                'D',
                onElementPass(
                    MTEWaterHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTEWaterHubArray::getPipeTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockCasings2, 12),
                            Pair.of(GregTechAPI.sBlockCasings2, 13),
                            Pair.of(GregTechAPI.sBlockCasings2, 15)),
                        -1,
                        (MTEWaterHubArray t, Integer tier) -> t.mPipeTier = tier,
                        (MTEWaterHubArray t) -> t.mPipeTier)))
            .addElement(
                'E',
                onElementPass(
                    MTEWaterHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTEWaterHubArray::getFrameTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID),
                            Pair.of(GregTechAPI.sBlockFrames, Materials.TungstenSteel.mMetaItemSubID)),
                        -1,
                        (MTEWaterHubArray t, Integer tier) -> t.mFrameTier = tier,
                        (MTEWaterHubArray t) -> t.mFrameTier)))
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
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.TungstenSteel.mMetaItemSubID) return 3;
        return null;
    }

    private enum WaterHubHatchElement implements IHatchElement<MTEWaterHubArray> {

        // S1：奇点仓两枚举（结构接纳，问题 2）；声明顺序 = atLeast adder 认领顺序（具体在前，
        // 两仓分别是近亲输入/输出仓的子类，必须在泛化枚举之前认领）
        SingularityFluidInputCompartment(MTEWaterHubArray::addSingularityFluidInputCompartmentToMachineList,
            MTESingularityFluidInputCompartment.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTESingularityFluidInputCompartment.class);
            }
        },
        SingularityFluidOutputCompartment(MTEWaterHubArray::addSingularityFluidOutputCompartmentToMachineList,
            MTESingularityFluidOutputCompartment.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTESingularityFluidOutputCompartment.class);
            }
        },
        WaterInput(MTEWaterHubArray::addWaterInputToMachineList, MTEWaterHubInputHatch.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEWaterHubInputHatch.class);
            }
        },
        WaterOutput(MTEWaterHubArray::addWaterOutputToMachineList, MTEWaterHubOutputHatch.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEWaterHubOutputHatch.class);
            }
        };

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTEWaterHubArray> adder;

        @SafeVarargs
        WaterHubHatchElement(IGTHatchAdder<MTEWaterHubArray> adder, Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTEWaterHubArray> adder() {
            return adder;
        }

        @Override
        public long count(MTEWaterHubArray t) {
            if (this == WaterInput) return t.mWaterInputHatches.size();
            if (this == WaterOutput) return t.mWaterOutputHatches.size();
            if (this == SingularityFluidInputCompartment) return t.mSingularityFluidInputCompartmentCount;
            return t.mSingularityFluidOutputCompartmentCount;
        }
    }

    private enum WaterHubStorageElement implements IHatchElement<MTEWaterHubArray> {

        HubUnit(MTEWaterHubArray::addHubUnitToMachineList, MTEHubStorageUnit.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHubStorageUnit.class);
            }
        },
        ReinforcedHubUnit(MTEWaterHubArray::addReinforcedHubUnitToMachineList, MTEReinforcedHubStorageUnit.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEReinforcedHubStorageUnit.class);
            }
        },
        OverpressureUnit(MTEWaterHubArray::addOverpressureHubUnitToMachineList, MTEOverpressureHubStorageUnit.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEOverpressureHubStorageUnit.class);
            }
        };

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTEWaterHubArray> adder;

        @SafeVarargs
        WaterHubStorageElement(IGTHatchAdder<MTEWaterHubArray> adder, Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTEWaterHubArray> adder() {
            return adder;
        }

        @Override
        public long count(MTEWaterHubArray t) {
            if (this == HubUnit) return t.mHubUnitCount;
            if (this == ReinforcedHubUnit) return t.mReinforcedHubUnitCount;
            return t.mOverpressureHubUnitCount;
        }
    }

    private final ArrayList<MTEWaterHubInputHatch> mWaterInputHatches = new ArrayList<>();
    private final ArrayList<MTEWaterHubOutputHatch> mWaterOutputHatches = new ArrayList<>();

    // S1：奇点仓结构接纳计数（仅结构组成提示用，不注入 mController、不参与传输——传输走终端绑定链）
    private int mSingularityFluidInputCompartmentCount = 0;
    private int mSingularityFluidOutputCompartmentCount = 0;

    public int mHubUnitCount = 0;
    public int mReinforcedHubUnitCount = 0;
    public int mOverpressureHubUnitCount = 0;
    private int mCasingAmount = 0;
    public int mStackCount = 0;
    public long mWaterStored = 0;
    private String mStoredFluidType = null;
    // 存储流体名的客户端副本（description packet 同步）：正面流体窗取流体用，空串=无（回退默认水）
    private String mClientFluidName = "";

    public MTEWaterHubArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTEWaterHubArray(String aName) {
        super(aName);
        registerProgressEntries();
    }

    // GTSR 进度词条：注册顺序 = GUI 终端显示顺序（存储单元/水缓冲/总容量；等级/芯片/状态为文本行保留在 GUI）
    private void registerProgressEntries() {
        // 存储单元：显示 "已装/上限(9×堆叠)"，formatter 内读机器字段拼上限
        registerEntryCustom(
            "storage_units",
            "gtsr.gui.water_hub.storage_units",
            EnumChatFormatting.GOLD,
            () -> mHubUnitCount + mReinforcedHubUnitCount + mOverpressureHubUnitCount,
            v -> (long) v + "/" + (9 * mStackCount));
        registerEntryCustom(
            "water_buffer",
            "gtsr.gui.water_hub.water_buffer",
            EnumChatFormatting.LIGHT_PURPLE,
            () -> mWaterStored,
            v -> UnitFormatUtil.format((long) v) + " L");
        registerEntryCustom(
            "total_capacity",
            "gtsr.gui.water_hub.total_capacity",
            EnumChatFormatting.LIGHT_PURPLE,
            () -> getTotalCapacity(),
            v -> UnitFormatUtil.format((long) v) + " L");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        if (FRAME_UNBOUND_FACING == null) {
            FRAME_UNBOUND_FACING = TextureFactory.builder()
                .addIcon(TextureManager.HUB_FRAME_UNBOUND)
                .extFacing()
                .build();
        }
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEWaterHubArray(mName);
    }

    @Override
    public IStructureDefinition<MTEWaterHubArray> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        for (MTEWaterHubInputHatch hatch : mWaterInputHatches) {
            hatch.mController = null;
        }
        for (MTEWaterHubOutputHatch hatch : mWaterOutputHatches) {
            hatch.mController = null;
        }

        mHubUnitCount = 0;
        mReinforcedHubUnitCount = 0;
        mOverpressureHubUnitCount = 0;
        mCasingAmount = 0;
        mSetTier = -1;
        mCasingTier = -1;
        mPipeTier = -1;
        mFrameTier = -1;
        mStackCount = 0;
        mWaterInputHatches.clear();
        mWaterOutputHatches.clear();
        mSingularityFluidInputCompartmentCount = 0;
        mSingularityFluidOutputCompartmentCount = 0;

        if (!checkPiece(STRUCTURE_PIECE_BASE, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        for (int i = 0; i < 30; i++) {
            int bOffset = 1 + i;
            if (!checkPiece(STRUCTURE_PIECE_STACK, HORIZONTAL_OFF_SET, bOffset, DEPTH_OFF_SET)) break;
            mStackCount++;
        }

        if (!require(mStackCount > 0, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        // Validate all tier fields are consistent
        if (!require(mCasingTier > 0 && mPipeTier > 0 && mFrameTier > 0, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (!require(mCasingTier == mPipeTier && mCasingTier == mFrameTier, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        mSetTier = mCasingTier;

        // 三段互斥（镜像蒸汽枢纽）：tier1 禁耐压/超压单元且需普通单元>0；
        // tier2 禁普通/超压单元且需耐压单元>0；tier≥3 禁普通/耐压单元且需超压单元>0
        if (!require(mSetTier != 1 || (mReinforcedHubUnitCount <= 0 && mOverpressureHubUnitCount <= 0), errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (!require(mSetTier != 2 || (mHubUnitCount <= 0 && mOverpressureHubUnitCount <= 0), errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (!require(mSetTier < 3 || (mHubUnitCount <= 0 && mReinforcedHubUnitCount <= 0), errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        if (!require(mSetTier != 1 || mHubUnitCount > 0, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (!require(mSetTier != 2 || mReinforcedHubUnitCount > 0, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (!require(mSetTier < 3 || mOverpressureHubUnitCount > 0, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        int tierCasingIndex;
        if (mSetTier >= 3) {
            tierCasingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0);
        } else if (mSetTier == 2) {
            tierCasingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        } else {
            tierCasingIndex = CASING_INDEX;
        }
        for (MTEWaterHubInputHatch hatch : mWaterInputHatches) {
            hatch.updateTexture(tierCasingIndex);
        }
        for (MTEWaterHubOutputHatch hatch : mWaterOutputHatches) {
            hatch.updateTexture(tierCasingIndex);
        }

        getBaseMetaTileEntity().issueTileUpdate();
    }

    private void onCasingAdded() {
        mCasingAmount++;
    }

    public boolean addWaterInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEWaterHubInputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.mController = this;
            return mWaterInputHatches.add(hatch);
        }
        return false;
    }

    public boolean addWaterOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEWaterHubOutputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.mController = this;
            return mWaterOutputHatches.add(hatch);
        }
        return false;
    }

    // S1：奇点仓结构接纳（问题 2）——只计数与换装外壳材质，不注入 mController、不加入输入/输出运转列表
    // （仓与枢纽的流体交互走终端绑定链 transferWithBoundNodes，与结构成员资格无关）
    public boolean addSingularityFluidInputCompartmentToMachineList(IGregTechTileEntity aTileEntity,
        int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTESingularityFluidInputCompartment hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            mSingularityFluidInputCompartmentCount++;
            return true;
        }
        return false;
    }

    public boolean addSingularityFluidOutputCompartmentToMachineList(IGregTechTileEntity aTileEntity,
        int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTESingularityFluidOutputCompartment hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            mSingularityFluidOutputCompartmentCount++;
            return true;
        }
        return false;
    }

    public boolean addHubUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEHubStorageUnit) {
            mHubUnitCount++;
            return true;
        }
        return false;
    }

    public boolean addReinforcedHubUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEReinforcedHubStorageUnit) {
            mReinforcedHubUnitCount++;
            return true;
        }
        return false;
    }

    public boolean addOverpressureHubUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEOverpressureHubStorageUnit) {
            mOverpressureHubUnitCount++;
            return true;
        }
        return false;
    }

    /**
     * 通用流体判定（S5 放宽）：蓄水枢纽阵列接受任意注册流体；单一类型锁由 mStoredFluidType 保证
     * （首流锁定类型、异种拒收、抽干解锁）。外部调用点走 {@link MTEWaterHubOutputHatch#isWaterFluid}。
     */
    private static boolean isWaterFluid(FluidStack aFluid) {
        return aFluid != null && aFluid.getFluid() != null;
    }

    @Override
    public int receiveFluid(FluidStack fluid, boolean doFill) {
        return receiveWater(fluid, doFill);
    }

    @Override
    public FluidStack extractFluid(int amount, boolean doDrain) {
        return extractWater(amount, doDrain);
    }

    public int receiveWater(FluidStack aFluid, boolean doFill) {
        if (aFluid == null) return 0;
        if (!isWaterFluid(aFluid)) return 0;
        if (mStoredFluidType != null && !mStoredFluidType.equals(
            aFluid.getFluid()
                .getName()))
            return 0;

        long capacity = getTotalCapacity();
        long canAccept = capacity - mWaterStored;

        if (mOverflowInput) {
            if (doFill) {
                if (mStoredFluidType == null) {
                    mStoredFluidType = aFluid.getFluid()
                        .getName();
                }
                long actualStore = Math.min(aFluid.amount, canAccept);
                mWaterStored += actualStore;
            }
            return aFluid.amount;
        }

        int toAccept = (int) Math.min(aFluid.amount, canAccept);

        if (doFill && toAccept > 0) {
            if (mStoredFluidType == null) {
                mStoredFluidType = aFluid.getFluid()
                    .getName();
            }
            mWaterStored += toAccept;
        }

        return toAccept;
    }

    public FluidStack extractWater(int maxDrain, boolean doDrain) {
        if (mWaterStored <= 0 || mStoredFluidType == null) return null;

        int toDrain = (int) Math.min(maxDrain, mWaterStored);
        FluidStack result = FluidStack.loadFluidStackFromNBT(createFluidTag(mStoredFluidType, toDrain));

        if (doDrain) {
            mWaterStored -= toDrain;
            if (mWaterStored <= 0) {
                mStoredFluidType = null;
            }
        }

        return result;
    }

    public FluidStack getStoredFluidStack() {
        if (mStoredFluidType == null || mWaterStored <= 0) return null;
        int amount = (int) Math.min(mWaterStored, Integer.MAX_VALUE);
        return FluidStack.loadFluidStackFromNBT(createFluidTag(mStoredFluidType, amount));
    }

    private static NBTTagCompound createFluidTag(String fluidName, int amount) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("FluidName", fluidName);
        tag.setInteger("Amount", amount);
        return tag;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_BASE, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
        int tTotalHeight = Math.max(2, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 2, 31));
        int stackCount = tTotalHeight - 1;
        for (int i = 0; i < stackCount; i++) {
            int bOffset = 1 + i;
            buildPiece(STRUCTURE_PIECE_STACK, stackSize, hintsOnly, HORIZONTAL_OFF_SET, bOffset, DEPTH_OFF_SET);
        }
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(
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
        int tTotalHeight = Math.max(2, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 2, 31));
        int stackCount = tTotalHeight - 1;
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
        return new String[] { EnumChatFormatting.AQUA + "Structure:", "1. BASE (1 layer): Controller layer (bottom)",
            "2. STACK (1 layer): Repeatable storage unit layer (1~30, on top of BASE)",
            "3. Total height: 2~31 layers (7x7x2 to 7x7x31)", "4. At least 1 Input Hatch and 1 Output Hatch required" };
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
        long base = (long) mHubUnitCount * HUB_UNIT_CAPACITY
            + (long) mReinforcedHubUnitCount * REINFORCED_HUB_UNIT_CAPACITY
            + (long) mOverpressureHubUnitCount * OVERPRESSURE_HUB_UNIT_CAPACITY;
        // 强化芯片 ×20 优先，普通奇点芯片 ×5 兜底（镜像蒸汽枢纽 getTotalCapacity 结构与优先级）
        if (hasReinforcedChipInstalled()) return base * 20;
        return hasHubChipInstalled() ? base * 5 : base;
    }

    public long getWaterStored() {
        return mWaterStored;
    }

    public int getHubUnitCount() {
        return mHubUnitCount;
    }

    public int getReinforcedHubUnitCount() {
        return mReinforcedHubUnitCount;
    }

    public int getOverpressureHubUnitCount() {
        return mOverpressureHubUnitCount;
    }

    // A04-H3 族差异钩子（服务 tick 骨架见 MTEHubArrayBase.onPostTick：容量钳制→自动输出→同步去重→周期分派）
    @Override
    protected long getStoredFluidAmount() {
        return mWaterStored;
    }

    @Override
    protected void setStoredFluidAmount(long amount) {
        mWaterStored = amount;
    }

    @Override
    protected String storedFluidNameForSync() {
        return mStoredFluidType != null ? mStoredFluidType : "";
    }

    @Override
    protected void onBoundTransferTick(long aTick) {
        if (aTick % BOUND_TRANSFER_INTERVAL == 0) {
            transferWithBoundNodes();
        }
    }

    @Override
    protected void autoOutputStored() {
        if (mWaterStored <= 0 || mStoredFluidType == null) return;
        long capacity = getTotalCapacity();
        for (MTEWaterHubOutputHatch hatch : mWaterOutputHatches) {
            if (mWaterStored <= 0) break;
            if (hatch.mOverflowOutput && mWaterStored < (long) (capacity * 0.9)) continue;
            IGregTechTileEntity hatchBase = hatch.getBaseMetaTileEntity();
            if (hatchBase == null) continue;
            ForgeDirection hatchFront = hatchBase.getFrontFacing();
            IFluidHandler adjacent = hatchBase.getITankContainerAtSide(hatchFront);
            if (adjacent == null) continue;

            int toPush = (int) Math.min(AUTO_OUTPUT_RATE, mWaterStored);
            FluidStack toExport = FluidStack.loadFluidStackFromNBT(createFluidTag(mStoredFluidType, toPush));
            int pushed = adjacent.fill(hatchFront.getOpposite(), toExport, true);
            if (pushed > 0) {
                mWaterStored -= pushed;
                if (mWaterStored <= 0) {
                    mStoredFluidType = null;
                    return;
                }
            }
        }
    }

    /**
     * 单节点传输（Water String 锁 + createFluidTag）：output 分支直扣 mWaterStored 后 fill 实放，
     * input 分支 drain→receiveWater（异种拒收由 receiveWater 的单一类型锁负责）。
     * v1.10.61：按节点实际速率（交互速率百分比）传输，镜像蒸汽枢纽结构。
     */
    @Override
    protected void transferOneNode(BoundCacheNode node, IGregTechTileEntity gtTile, int nodeRate) {
        if (node.isOutputMode) {
            if (mWaterStored <= 0 || mStoredFluidType == null) return;
            int toTransfer = (int) Math.min(nodeRate, mWaterStored);
            FluidStack toExport = FluidStack.loadFluidStackFromNBT(createFluidTag(mStoredFluidType, toTransfer));
            int filled = gtTile.fill(ForgeDirection.UNKNOWN, toExport, true);
            if (filled > 0) {
                mWaterStored -= filled;
                if (mWaterStored <= 0) {
                    mStoredFluidType = null;
                }
            }
        } else {
            FluidStack drained = gtTile.drain(ForgeDirection.UNKNOWN, nodeRate, false);
            if (drained != null && drained.amount > 0) {
                int accepted = receiveWater(drained, true);
                if (accepted > 0) {
                    gtTile.drain(ForgeDirection.UNKNOWN, accepted, true);
                }
            }
        }
    }

    // 蓄水枢纽族绑定差异钩子（绑定流主体见 MTEHubArrayBase.onRightclick / bindOne / bindWhole 模板；
    // 绑定奇点成本恒 0 走基类默认，hubPos 无 reinforced 维度走基类默认 writeBindExtras 空实现）
    @Override
    protected String resolveHeldType(ItemStack held) {
        if (GTSRItemList.WaterCacheNode.isStackEqual(held, false, true)) return "water";
        if (GTSRItemList.ReinforcedWaterCacheNode.isStackEqual(held, false, true)) return "reinforced_water";
        if (GTSRItemList.OverpressureWaterCacheNode.isStackEqual(held, false, true)) return "overpressure_water";
        if (GTSRItemList.SingularityFluidInputCompartment.isStackEqual(held, false, true)) {
            return "singularity_fluid_in";
        }
        if (GTSRItemList.SingularityFluidOutputCompartment.isStackEqual(held, false, true)) {
            return "singularity_fluid_out";
        }
        return null;
    }

    @Override
    protected boolean requiresReinforcedChipToBind(String type) {
        return "overpressure_water".equals(type);
    }

    @Override
    protected Pair<String, String> singularityCompartmentTypes() {
        return Pair.of("singularity_fluid_in", "singularity_fluid_out");
    }

    @Override
    public boolean acceptsNodeType(String type) {
        return "water".equals(type) || "reinforced_water".equals(type)
            || "overpressure_water".equals(type)
            || "singularity_fluid_in".equals(type)
            || "singularity_fluid_out".equals(type);
    }

    /**
     * 打开缓存节点状态管理界面（Modern UI 2）。必须在服务端调用，
     * 实际打开逻辑委托给 WaterHubStatusGuiFactory（独立 MUI2 factory，不影响主 GUI）。
     */
    @Override
    public void openHubStatusGui(EntityPlayer player) {
        com.miaokatze.gtsr.common.gui.WaterHubStatusGuiFactory.open(player, this);
    }

    /**
     * 按实际节点类判定类型字符串（不用缓存字段，水枢纽绑定记录无 reinforced 维度）。
     * 各节点类与四仓互不继承、共同实现 IHubCacheNode（S1 起），instanceof 顺序无关
     * （镜像蒸汽枢纽 resolveNodeType）。
     */
    @Override
    protected String resolveNodeType(IHubCacheNode node) {
        if (node instanceof MTEOverpressureWaterCacheNode) return "overpressure_water";
        if (node instanceof MTEReinforcedWaterCacheNode) return "reinforced_water";
        if (node instanceof MTEWaterCacheNode) return "water";
        if (node instanceof MTESingularityFluidOutputCompartment) return "singularity_fluid_out";
        if (node instanceof MTESingularityFluidInputCompartment) return "singularity_fluid_in";
        return "";
    }

    // 蓄水枢纽绑定列表的 NBT 序列化格式（count+nodeN，与基类 BoundCacheNode 解耦）：
    // 键名/结构为存档契约，逐字保留（Steam 侧走 NBTTagList 逐项格式，两格式并存）
    private static void writeBoundNodeToNBT(BoundCacheNode node, NBTTagCompound tag) {
        tag.setInteger("x", node.x);
        tag.setInteger("y", node.y);
        tag.setInteger("z", node.z);
        tag.setInteger("dim", node.dimensionId);
        tag.setBoolean("out", node.isOutputMode);
    }

    private static BoundCacheNode readBoundNodeFromNBT(NBTTagCompound tag) {
        return new BoundCacheNode(
            tag.getInteger("x"),
            tag.getInteger("y"),
            tag.getInteger("z"),
            tag.getInteger("dim"),
            tag.getBoolean("out"));
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("mWaterStored", mWaterStored);
        aNBT.setInteger("mHubUnitCount", mHubUnitCount);
        aNBT.setInteger("mReinforcedHubUnitCount", mReinforcedHubUnitCount);
        aNBT.setInteger("mOverpressureHubUnitCount", mOverpressureHubUnitCount);
        if (mStoredFluidType != null) {
            aNBT.setString("mStoredFluidType", mStoredFluidType);
        }
    }

    /** 绑定列表 Water 格式：count+nodeN（键名/结构为存档契约，与 Steam 侧 NBTTagList 格式并存）。 */
    @Override
    protected void saveBoundNodes(NBTTagCompound aNBT) {
        NBTTagCompound boundListTag = new NBTTagCompound();
        boundListTag.setInteger("count", mBoundNodes.size());
        for (int i = 0; i < mBoundNodes.size(); i++) {
            NBTTagCompound nodeTag = new NBTTagCompound();
            writeBoundNodeToNBT(mBoundNodes.get(i), nodeTag);
            boundListTag.setTag("node" + i, nodeTag);
        }
        aNBT.setTag("mBoundNodes", boundListTag);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mWaterStored = aNBT.getLong("mWaterStored");
        mHubUnitCount = aNBT.getInteger("mHubUnitCount");
        mReinforcedHubUnitCount = aNBT.getInteger("mReinforcedHubUnitCount");
        mOverpressureHubUnitCount = aNBT.getInteger("mOverpressureHubUnitCount");
        if (aNBT.hasKey("mStoredFluidType")) {
            mStoredFluidType = aNBT.getString("mStoredFluidType");
        }
    }

    @Override
    protected void loadBoundNodes(NBTTagCompound aNBT) {
        NBTTagCompound boundListTag = aNBT.getCompoundTag("mBoundNodes");
        int count = boundListTag.getInteger("count");
        for (int i = 0; i < count; i++) {
            NBTTagCompound nodeTag = boundListTag.getCompoundTag("node" + i);
            mBoundNodes.add(readBoundNodeFromNBT(nodeTag));
        }
    }

    @Override
    public NBTTagCompound getDescriptionData() {
        NBTTagCompound data = super.getDescriptionData();
        if (data == null) data = new NBTTagCompound();
        data.setInteger("mSetTier", mSetTier);
        // 正面流体窗渲染状态：存储流体名（空串=无，客户端回退默认水）
        data.setString("gtsr.hubFluid", mStoredFluidType != null ? mStoredFluidType : "");
        return data;
    }

    @Override
    public void onDescriptionPacket(NBTTagCompound data) {
        super.onDescriptionPacket(data);
        mSetTier = data.getInteger("mSetTier");
        mClientFluidName = data.getString("gtsr.hubFluid");
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            base.issueTextureUpdate();
        }
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        int casingTextureId;
        if (mSetTier >= 3) {
            casingTextureId = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0);
        } else if (mSetTier == 2) {
            casingTextureId = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        } else {
            casingTextureId = CASING_INDEX;
        }
        if (side == facing) {
            // 正面三层：tier 基材 + 内缩流体窗（存储流体，空回退水；窗收在框架环内，与节点/仓同变体）+ 枢纽框架层
            Fluid fluid = FluidRegistry.getFluid(mClientFluidName);
            if (fluid == null) fluid = FluidRegistry.WATER;
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingTextureId),
                GTSRFluidWindowTexture.getOrCreate(fluid), FRAME_UNBOUND_FACING };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingTextureId) };
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new MTEWaterHubArrayGui(this);
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new TextWidget().setStringSupplier(() -> {
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.hub.terminal_hint")
                + EnumChatFormatting.RESET;
        }));
        screenElements.widget(new TextWidget().setStringSupplier(() -> {
            String tierText;
            if (mSetTier >= 3) {
                tierText = StatCollector.translateToLocal("gtsr.gui.tier.tungstensteel");
            } else if (mSetTier == 2) {
                tierText = StatCollector.translateToLocal("gtsr.gui.tier.steel");
            } else {
                tierText = StatCollector.translateToLocal("gtsr.gui.tier.bronze");
            }
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText
                + EnumChatFormatting.RESET;
        }))
            .widget(new TextWidget().setStringSupplier(() -> {
                ItemStack chip = getControllerSlot();
                String chipText;
                if (chip != null && GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(chip, true, true)) {
                    if (mSetTier >= 3) {
                        chipText = EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("gtsr.gui.chip.reinforced_installed");
                    } else {
                        chipText = EnumChatFormatting.RED
                            + StatCollector.translateToLocal("gtsr.gui.chip.need_higher_tier");
                    }
                } else if (chip != null && GTSRItemList.HubSingularityChip.isStackEqual(chip, true, true)) {
                    chipText = EnumChatFormatting.GREEN
                        + StatCollector.translateToLocal("gtsr.gui.chip.singularity_installed");
                } else {
                    chipText = EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.chip.none");
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.chip")
                    + " "
                    + chipText
                    + EnumChatFormatting.RESET;
            }))
            .widget(new TextWidget().setStringSupplier(() -> {
                String status = mMaxProgresstime > 0
                    ? EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.gui.status.running")
                    : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.status.idle");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                    + " "
                    + status
                    + EnumChatFormatting.RESET;
            }))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.water_hub.storage_units")
                        + " "
                        + EnumChatFormatting.GOLD
                        + (mHubUnitCount + mReinforcedHubUnitCount + mOverpressureHubUnitCount)
                        + "/"
                        + (9 * mStackCount)
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.water_hub.water_buffer")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + UnitFormatUtil.format(mWaterStored)
                        + " L"
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.water_hub.total_capacity")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + UnitFormatUtil.format(getTotalCapacity())
                        + " L"
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mStackCount, val -> mStackCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mHubUnitCount, val -> mHubUnitCount = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> mReinforcedHubUnitCount, val -> mReinforcedHubUnitCount = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(
                    () -> mOverpressureHubUnitCount,
                    val -> mOverpressureHubUnitCount = val))
            .widget(new FakeSyncWidget.LongSyncer(() -> mWaterStored, val -> mWaterStored = val));
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.water_hub.type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        String tierText;
        if (mSetTier >= 3) {
            tierText = StatCollector.translateToLocal("gtsr.gui.tier.tungstensteel");
        } else if (mSetTier == 2) {
            tierText = StatCollector.translateToLocal("gtsr.gui.tier.steel");
        } else {
            tierText = StatCollector.translateToLocal("gtsr.gui.tier.bronze");
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText
                + EnumChatFormatting.RESET);
        String statusKey = mMaxProgresstime > 0 ? "gtsr.gui.status.running" : "gtsr.gui.status.idle";
        EnumChatFormatting statusColor = mMaxProgresstime > 0 ? EnumChatFormatting.AQUA : EnumChatFormatting.GRAY;
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey)
                + EnumChatFormatting.RESET);
        int totalUnits = mHubUnitCount + mReinforcedHubUnitCount + mOverpressureHubUnitCount;
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.water_hub.storage_units")
                + " "
                + EnumChatFormatting.GOLD
                + totalUnits
                + "/"
                + (9 * mStackCount)
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.water_hub.water_buffer")
                + " "
                + EnumChatFormatting.LIGHT_PURPLE
                + UnitFormatUtil.format(mWaterStored)
                + " L"
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.water_hub.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub.desc2"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.water_hub.desc2_2"))
            .addInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.water_hub.chip_1"))
            .addInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.water_hub.chip_2"))
            .addInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.screwdriver_overflow"))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.shared.overflow_input_desc"))
            .beginStructureBlock(7, 31, 7, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.water_hub.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.water_hub.hub_input"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.water_hub.hub_output"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.water_hub.storage"),
                StatCollector.translateToLocal("gtsr.tooltip.water_hub.storage"),
                2)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + "Bronze"
                    + EnumChatFormatting.DARK_PURPLE
                    + "/"
                    + EnumChatFormatting.BLUE
                    + "Steel"
                    + EnumChatFormatting.DARK_PURPLE
                    + "/"
                    + EnumChatFormatting.BLUE
                    + "TungstenSteel "
                    + EnumChatFormatting.DARK_PURPLE
                    + "Tier")
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub.counts"))
            .addStructureHint("gtsr.tooltip.water_hub.height")
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.water_hub.hint_tier1")
            .addStructureHint("gtsr.tooltip.water_hub.hint_tier2")
            .addStructureHint("gtsr.tooltip.water_hub.hint_tier3")
            .addStructureHint("gtsr.tooltip.shared.hub_singularity_cost")
            .addStructureHint("gtsr.tooltip.shared.overflow_input_screwdriver")
            .addStructureHint("gtsr.tooltip.water_hub.hint_status")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }
}
