package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
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
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
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
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
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
import com.miaokatze.gtsr.common.util.HubTeleportUtil;
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
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.GTStructureChannels;

public class MTEWaterHubArray extends MTEGTSRMultiBlockBase<MTEWaterHubArray>
    implements IConstructable, ISurvivalConstructable, com.miaokatze.gtsr.common.machine.base.IHubArray {

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
    private static final int BOUND_TRANSFER_RATE = 1_000_000;
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

    public static class BoundCacheNode {

        public int x;
        public int y;
        public int z;
        public int dimensionId;
        public boolean isOutputMode;
        public transient IGregTechTileEntity cachedTile;
        public transient long lastLookupTick;
        public transient long nextLookupTick;
        public transient boolean lastLookupLoaded;

        public BoundCacheNode(int x, int y, int z, int dimensionId, boolean isOutputMode) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimensionId = dimensionId;
            this.isOutputMode = isOutputMode;
        }

        public void writeToNBT(NBTTagCompound tag) {
            tag.setInteger("x", x);
            tag.setInteger("y", y);
            tag.setInteger("z", z);
            tag.setInteger("dim", dimensionId);
            tag.setBoolean("out", isOutputMode);
        }

        public static BoundCacheNode readFromNBT(NBTTagCompound tag) {
            return new BoundCacheNode(
                tag.getInteger("x"),
                tag.getInteger("y"),
                tag.getInteger("z"),
                tag.getInteger("dim"),
                tag.getBoolean("out"));
        }
    }

    private final ArrayList<MTEWaterHubInputHatch> mWaterInputHatches = new ArrayList<>();
    private final ArrayList<MTEWaterHubOutputHatch> mWaterOutputHatches = new ArrayList<>();
    private final ArrayList<BoundCacheNode> mBoundNodes = new ArrayList<>();

    // S1：奇点仓结构接纳计数（仅结构组成提示用，不注入 mController、不参与传输——传输走终端绑定链）
    private int mSingularityFluidInputCompartmentCount = 0;
    private int mSingularityFluidOutputCompartmentCount = 0;

    public int mHubUnitCount = 0;
    public int mReinforcedHubUnitCount = 0;
    public int mOverpressureHubUnitCount = 0;
    private int mCasingAmount = 0;
    public int mSetTier = -1;
    private int mCasingTier = -1;
    private int mPipeTier = -1;
    private int mFrameTier = -1;
    public int mStackCount = 0;
    public long mWaterStored = 0;
    private String mStoredFluidType = null;
    // 存储流体名的客户端副本（description packet 同步）：正面流体窗取流体用，空串=无（回退默认水）
    private String mClientFluidName = "";
    /** 渲染状态同步去重 key（存储流体名），服务端 onPostTick 维护，变化才 issueTileUpdate。 */
    private String mLastSyncKey = null;
    public boolean mOverflowInput = false;

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

        if (mStackCount == 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        // Validate all tier fields are consistent
        if (mCasingTier <= 0 || mPipeTier <= 0 || mFrameTier <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (mCasingTier != mPipeTier || mCasingTier != mFrameTier) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        mSetTier = mCasingTier;

        // 三段互斥（镜像蒸汽枢纽）：tier1 禁耐压/超压单元且需普通单元>0；
        // tier2 禁普通/超压单元且需耐压单元>0；tier≥3 禁普通/耐压单元且需超压单元>0
        if (mSetTier == 1 && (mReinforcedHubUnitCount > 0 || mOverpressureHubUnitCount > 0)) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (mSetTier == 2 && (mHubUnitCount > 0 || mOverpressureHubUnitCount > 0)) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (mSetTier >= 3 && (mHubUnitCount > 0 || mReinforcedHubUnitCount > 0)) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        if (mSetTier == 1 && mHubUnitCount <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (mSetTier == 2 && mReinforcedHubUnitCount <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (mSetTier >= 3 && mOverpressureHubUnitCount <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
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

    public boolean isFormed() {
        return mMachine;
    }

    /**
     * 通用流体判定（S5 放宽）：蓄水枢纽阵列接受任意注册流体；单一类型锁由 mStoredFluidType 保证
     * （首流锁定类型、异种拒收、抽干解锁），方法名保留避免破坏既有调用点。
     */
    public static boolean isWaterFluid(FluidStack aFluid) {
        return aFluid != null && aFluid.getFluid() != null;
    }

    public static boolean isWaterFluidName(String fluidName) {
        return fluidName != null && !fluidName.isEmpty();
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

    public long getTotalCapacity() {
        long base = (long) mHubUnitCount * HUB_UNIT_CAPACITY
            + (long) mReinforcedHubUnitCount * REINFORCED_HUB_UNIT_CAPACITY
            + (long) mOverpressureHubUnitCount * OVERPRESSURE_HUB_UNIT_CAPACITY;
        // 强化芯片 ×20 优先，普通奇点芯片 ×5 兜底（镜像蒸汽枢纽 getTotalCapacity 结构与优先级）
        if (hasReinforcedChipInstalled()) return base * 20;
        return hasHubSingularityChip() ? base * 5 : base;
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

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || !mMachine) return;

        long totalCapacity = getTotalCapacity();
        if (mWaterStored > totalCapacity) {
            mWaterStored = totalCapacity;
        }

        autoOutputWater();

        // 存储流体名变化才发 description packet（首流锁定与抽干清空自然覆盖，量变化不发包，稳态零流量）
        String syncKey = mStoredFluidType != null ? mStoredFluidType : "";
        if (!syncKey.equals(mLastSyncKey)) {
            mLastSyncKey = syncKey;
            aBaseMetaTileEntity.issueTileUpdate();
        }

        if (aTick % BOUND_TRANSFER_INTERVAL == 0) {
            transferWithBoundNodes(aBaseMetaTileEntity);
        }
    }

    private void autoOutputWater() {
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

    private void transferWithBoundNodes(IGregTechTileEntity aBaseMetaTileEntity) {
        if (!hasChipInstalled()) {
            return;
        }

        ArrayList<BoundCacheNode> invalidNodes = new ArrayList<>();

        for (BoundCacheNode node : mBoundNodes) {
            // 节点类型过滤对齐蒸汽枢纽模式：resolveCacheNodeType+acceptsNodeType，而非硬 instanceof
            IHubCacheNode cacheNode = resolveCacheNode(node, true);
            if (cacheNode == null) {
                if (node.lastLookupLoaded) invalidNodes.add(node);
                continue;
            }
            if (!acceptsNodeType(resolveCacheNodeType(cacheNode)) || node.cachedTile == null) {
                invalidNodes.add(node);
                continue;
            }
            IGregTechTileEntity gtTile = node.cachedTile;

            if (node.isOutputMode) {
                if (mWaterStored <= 0 || mStoredFluidType == null) continue;
                // v1.10.61：改用节点实际速率（交互速率百分比），镜像蒸汽枢纽结构
                int nodeRate = getNodeTransferRate(gtTile);
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
                // v1.10.61：改用节点实际速率（交互速率百分比），镜像蒸汽枢纽结构；
                // 异种拒收由 receiveWater 的 mStoredFluidType 单一类型锁负责
                int nodeRate = getNodeTransferRate(gtTile);
                FluidStack drained = gtTile.drain(ForgeDirection.UNKNOWN, nodeRate, false);
                if (drained != null && drained.amount > 0) {
                    int accepted = receiveWater(drained, true);
                    if (accepted > 0) {
                        gtTile.drain(ForgeDirection.UNKNOWN, accepted, true);
                    }
                }
            }
        }

        mBoundNodes.removeAll(invalidNodes);
    }

    /**
     * v1.10.61：按节点交互速率百分比计算实际传输速率（镜像蒸汽枢纽 getNodeTransferRate；
     * 水节点基础交互速率 = OUTPUT_PER_TICK × 20，getEffectiveHubTransferRate 应用 mTransferRatePercent）。
     * S1 类型拓宽：缓存节点=速率百分比实算；奇点仓=固定常量（getEffectiveHubTransferRate 默认实现）。
     */
    private int getNodeTransferRate(IGregTechTileEntity gte) {
        IMetaTileEntity mte = gte.getMetaTileEntity();
        if (mte instanceof IHubCacheNode cacheNode) {
            return (int) Math.min(cacheNode.getEffectiveHubTransferRate(), Integer.MAX_VALUE);
        }
        return BOUND_TRANSFER_RATE;
    }

    private boolean hasHubSingularityChip() {
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.HubSingularityChip.isStackEqual(stack, true, true);
    }

    /** 普通或强化奇点芯片任一在位（绑定/传输门控，镜像蒸汽枢纽 hasChipInstalled）。 */
    private boolean hasChipInstalled() {
        ItemStack stack = getControllerSlot();
        return stack != null && (GTSRItemList.HubSingularityChip.isStackEqual(stack, true, true)
            || GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(stack, true, true));
    }

    /** 强化奇点芯片（等级3 前置，镜像蒸汽枢纽）：容量×20 与超压节点绑定门控共用。 */
    private boolean hasReinforcedChipInstalled() {
        if (mSetTier < 3) return false;
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(stack, true, true);
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        mOverflowInput = !mOverflowInput;
        if (aPlayer.worldObj.isRemote) return;
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.tooltip.shared.overflow_input") + ": "
                + (mOverflowInput ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on")
                    : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")));
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        ItemStack held = aPlayer.getHeldItem();

        // 手持枢纽终端右击：打开缓存节点状态管理界面（Modern UI 2，独立 factory），
        // 不占用空手右键（空手仍打开主 GUI），与钻井枢纽的打开方式保持一致
        if (held != null && GTSRItemList.HubTerminal.isStackEqual(held, false, true)) {
            if (aBaseMetaTileEntity.isServerSide()) {
                openHubStatusGui(aPlayer);
            }
            return true;
        }

        if (held != null && (GTSRItemList.HubSingularityChip.isStackEqual(held, true, true)
            || GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(held, true, true))) {
            if (aBaseMetaTileEntity.isServerSide()) {
                sendBindingDebug(aPlayer);
            }
            return true;
        }

        if (held == null) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        // 手持节点类型识别（三种水系节点 + 两类奇点流体仓，镜像蒸汽枢纽模式）
        String type = null;
        if (GTSRItemList.WaterCacheNode.isStackEqual(held, false, true)) {
            type = "water";
        } else if (GTSRItemList.ReinforcedWaterCacheNode.isStackEqual(held, false, true)) {
            type = "reinforced_water";
        } else if (GTSRItemList.OverpressureWaterCacheNode.isStackEqual(held, false, true)) {
            type = "overpressure_water";
        } else if (GTSRItemList.SingularityFluidInputCompartment.isStackEqual(held, false, true)) {
            type = "singularity_fluid_in";
        } else if (GTSRItemList.SingularityFluidOutputCompartment.isStackEqual(held, false, true)) {
            type = "singularity_fluid_out";
        }

        if (type == null) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        if (!aBaseMetaTileEntity.isServerSide()) return true;

        // 超压节点绑定门控（镜像蒸汽枢纽对 overpressure_steam 的门控）：无强化芯片（等级3）拒绝
        if ("overpressure_water".equals(type) && !hasReinforcedChipInstalled()) {
            GTUtility.sendChatToPlayer(
                aPlayer,
                StatCollector.translateToLocal("gtsr.binding.overpressure_no_reinforced_chip"));
            return true;
        }

        if (!hasChipInstalled()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.no_chip"));
            return true;
        }

        int myX = aBaseMetaTileEntity.getXCoord();
        int myY = aBaseMetaTileEntity.getYCoord();
        int myZ = aBaseMetaTileEntity.getZCoord();
        int myDim = aBaseMetaTileEntity.getWorld().provider.dimensionId;

        // 已绑定本枢纽的堆叠：无论普通/shift，优先走现有 output 翻转/解绑交互（完全保留现状逻辑）
        if (held.hasTagCompound() && held.getTagCompound()
            .hasKey("gtsr.hubPos")) {
            NBTTagCompound existing = held.getTagCompound()
                .getCompoundTag("gtsr.hubPos");
            int boundX = existing.getInteger("x");
            int boundY = existing.getInteger("y");
            int boundZ = existing.getInteger("z");
            int boundDim = existing.getInteger("dim");

            if (boundX == myX && boundY == myY && boundZ == myZ && boundDim == myDim) {
                // 奇点仓模式锁定：不提供 output 翻转，右击只解绑（沿用现解绑文案，镜像蒸汽枢纽）
                if (isModeLockedType(type)) {
                    held.getTagCompound()
                        .removeTag("gtsr.hubPos");
                    GTUtility.sendChatToPlayer(
                        aPlayer,
                        StatCollector.translateToLocal("gtsr.binding.cleared") + held.getDisplayName()
                            + StatCollector.translateToLocal("gtsr.binding.binding"));
                    return true;
                }
                boolean isOutput = existing.hasKey("output") && existing.getBoolean("output");

                if (!isOutput) {
                    existing.setBoolean("output", true);
                    GTUtility.sendChatToPlayer(
                        aPlayer,
                        StatCollector.translateToLocal("gtsr.binding.bound_input") + held.getDisplayName()
                            + StatCollector.translateToLocal("gtsr.binding.mode_input"));
                } else {
                    held.getTagCompound()
                        .removeTag("gtsr.hubPos");
                    GTUtility.sendChatToPlayer(
                        aPlayer,
                        StatCollector.translateToLocal("gtsr.binding.cleared") + held.getDisplayName()
                            + StatCollector.translateToLocal("gtsr.binding.binding"));
                }
                return true;
            }
        }

        // shift 右击：整个手持堆叠全部绑定（0 奇点成本，仅打标记）；
        // 普通右击：拆出 1 个绑定（0 奇点成本，仅打标记），绑定物回背包，手持剩余保持未绑定
        if (aPlayer.isSneaking()) {
            bindWholeHeld(aPlayer, held, type, myX, myY, myZ, myDim);
        } else {
            bindOneFromHeld(aPlayer, held, type, myX, myY, myZ, myDim);
        }
        return true;
    }

    /**
     * 普通右击：从手持堆叠拆出 1 个通用流体缓存节点绑定到本枢纽（0 奇点成本，仅打标记），
     * 写 hubPos NBT 后放回玩家背包（背包无空位则落地），手持剩余 N-1 个保持未绑定。
     * 绑定他处的堆叠仅覆盖拆出的这 1 个。
     */
    private void bindOneFromHeld(EntityPlayer aPlayer, ItemStack held, String type, int myX, int myY, int myZ,
        int myDim) {
        // Water cache node requires 0 singularity to bind
        // (singularity_consumed flag still set for compatibility, but no actual consumption)
        // 拆 1 个（copy + 减量，≤0 则清手持槽）
        ItemStack bound = held.copy();
        bound.stackSize = 1;
        held.stackSize--;
        if (held.stackSize <= 0) {
            aPlayer.inventory.mainInventory[aPlayer.inventory.currentItem] = null;
        }

        // 打标记（拆出物继承原 NBT，无标记则补；标记只作用于拆出物）
        if (!bound.hasTagCompound()) {
            bound.setTagCompound(new NBTTagCompound());
        }
        bound.getTagCompound()
            .setBoolean("gtsr.singularity_consumed", true);

        // 写 hubPos（覆盖绑定他处的旧 hubPos）
        NBTTagCompound hubTag = new NBTTagCompound();
        hubTag.setInteger("x", myX);
        hubTag.setInteger("y", myY);
        hubTag.setInteger("z", myZ);
        hubTag.setInteger("dim", myDim);
        hubTag.setString("type", type);
        hubTag.setBoolean("output", getLockedItemOutput(type));
        bound.getTagCompound()
            .setTag("gtsr.hubPos", hubTag);

        GTUtility.addItemToPlayerInventory(aPlayer, bound);
        aPlayer.inventoryContainer.detectAndSendChanges();
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.binding.bound_output") + bound.getDisplayName()
                + StatCollector.translateToLocal("gtsr.binding.mode_output"));
    }

    /**
     * shift 右击：整个手持堆叠全部绑定到本枢纽（0 奇点成本，仅打标记）。绑定他处的堆叠覆盖整堆。
     */
    private void bindWholeHeld(EntityPlayer aPlayer, ItemStack held, String type, int myX, int myY, int myZ,
        int myDim) {
        // Water cache node requires 0 singularity to bind
        // (singularity_consumed flag still set for compatibility, but no actual consumption)
        if (!held.hasTagCompound()) {
            held.setTagCompound(new NBTTagCompound());
        }
        held.getTagCompound()
            .setBoolean("gtsr.singularity_consumed", true);

        // 整堆写 hubPos（覆盖绑定他处的旧 hubPos）
        NBTTagCompound hubTag = new NBTTagCompound();
        hubTag.setInteger("x", myX);
        hubTag.setInteger("y", myY);
        hubTag.setInteger("z", myZ);
        hubTag.setInteger("dim", myDim);
        hubTag.setString("type", type);
        hubTag.setBoolean("output", getLockedItemOutput(type));
        held.getTagCompound()
            .setTag("gtsr.hubPos", hubTag);

        aPlayer.inventoryContainer.detectAndSendChanges();
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.binding.bound_output") + held.getDisplayName()
                + StatCollector.translateToLocal("gtsr.binding.mode_output"));
    }

    private BoundCacheNode findBoundNode(int x, int y, int z, int dimId) {
        for (BoundCacheNode node : mBoundNodes) {
            if (node.x == x && node.y == y && node.z == z && node.dimensionId == dimId) {
                return node;
            }
        }
        return null;
    }

    /** 奇点仓类型（模式锁定，右键已绑定分支只解绑不翻转；绑定消耗恒 0，无需消耗表条目）。 */
    private static boolean isModeLockedType(String type) {
        return "singularity_fluid_in".equals(type) || "singularity_fluid_out".equals(type);
    }

    /**
     * 锁定类型绑定时的 item output 恒定值（反转语义：false=枢纽→节点/接收仓，true=节点→枢纽/发送仓；
     * 与节点 loadNBTData 强制归位值互补）。非锁定类型保持 false（现状）。
     */
    private static boolean getLockedItemOutput(String type) {
        return "singularity_fluid_out".equals(type);
    }

    @Override
    public void registerCacheNode(int x, int y, int z, int dim, boolean isOutputMode) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            existing.isOutputMode = isOutputMode;
        } else {
            mBoundNodes.add(new BoundCacheNode(x, y, z, dim, isOutputMode));
        }
    }

    @Override
    public void unregisterCacheNode(int x, int y, int z, int dim) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            existing.cachedTile = null;
            existing.lastLookupTick = 0;
            existing.nextLookupTick = 0;
            existing.lastLookupLoaded = false;
            mBoundNodes.remove(existing);
        }
    }

    @Override
    public void updateCacheNodeMode(int x, int y, int z, int dim, boolean isOutputMode) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            existing.isOutputMode = isOutputMode;
        }
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
    public void openHubStatusGui(EntityPlayer player) {
        com.miaokatze.gtsr.common.gui.WaterHubStatusGuiFactory.open(player, this);
    }

    /**
     * 按坐标解析绑定缓存节点对应的 IHubCacheNode 实例（S1 拓宽：缓存节点与四个奇点仓）；
     * 世界未加载、方块不存在或目标不是缓存节点/奇点仓时返回 null。
     */
    private IHubCacheNode resolveCacheNode(int x, int y, int z, int dim) {
        BoundCacheNode bound = findBoundNode(x, y, z, dim);
        return bound == null ? null : resolveCacheNode(bound, false);
    }

    private IHubCacheNode resolveCacheNodeForAction(int x, int y, int z, int dim) {
        BoundCacheNode bound = findBoundNode(x, y, z, dim);
        if (bound == null) return null;
        bound.cachedTile = null;
        bound.lastLookupTick = 0;
        bound.nextLookupTick = 0;
        bound.lastLookupLoaded = false;
        return resolveCacheNode(bound, true);
    }

    /** Resolves one target per hub tick and keeps unavailable remote bindings for a later retry. */
    private IHubCacheNode resolveCacheNode(BoundCacheNode bound, boolean loadChunk) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World hubWorld = base == null ? null : base.getWorld();
        long now = hubWorld == null ? 0L : hubWorld.getTotalWorldTime();
        if (bound.cachedTile != null && (bound.lastLookupTick == now || now < bound.nextLookupTick)) {
            IMetaTileEntity mte = bound.cachedTile.getMetaTileEntity();
            return mte instanceof IHubCacheNode node ? node : null;
        }
        if (!loadChunk && now < bound.nextLookupTick) return null;

        bound.lastLookupTick = now;
        bound.lastLookupLoaded = false;
        World world = DimensionManager.getWorld(bound.dimensionId);
        if (world == null) {
            bound.cachedTile = null;
            bound.nextLookupTick = now + BOUND_TRANSFER_INTERVAL;
            return null;
        }
        if (!world.blockExists(bound.x, 0, bound.z)) {
            if (!loadChunk || !HubTeleportUtil.ensureChunkLoaded(world, bound.x, bound.z)) {
                bound.cachedTile = null;
                bound.nextLookupTick = now + BOUND_TRANSFER_INTERVAL;
                return null;
            }
        }
        if (!world.blockExists(bound.x, bound.y, bound.z)) {
            bound.cachedTile = null;
            bound.nextLookupTick = now + BOUND_TRANSFER_INTERVAL;
            return null;
        }

        bound.lastLookupLoaded = true;
        TileEntity te = world.getTileEntity(bound.x, bound.y, bound.z);
        if (te instanceof IGregTechTileEntity gte && gte.getMetaTileEntity() instanceof IHubCacheNode node) {
            bound.cachedTile = gte;
            bound.nextLookupTick = now + BOUND_TRANSFER_INTERVAL;
            return node;
        }
        bound.cachedTile = null;
        bound.nextLookupTick = now + BOUND_TRANSFER_INTERVAL;
        return null;
    }

    /**
     * 按实际节点类判定类型字符串（不用缓存字段，水枢纽 BoundCacheNode 无 reinforced 字段）。
     * 各节点类与四仓互不继承、共同实现 IHubCacheNode（S1 起），instanceof 顺序无关
     * （镜像蒸汽枢纽 resolveCacheNodeType）。
     */
    private static String resolveCacheNodeType(IHubCacheNode node) {
        if (node instanceof MTEOverpressureWaterCacheNode) return "overpressure_water";
        if (node instanceof MTEReinforcedWaterCacheNode) return "reinforced_water";
        if (node instanceof MTEWaterCacheNode) return "water";
        if (node instanceof MTESingularityFluidOutputCompartment) return "singularity_fluid_out";
        if (node instanceof MTESingularityFluidInputCompartment) return "singularity_fluid_in";
        return "";
    }

    /**
     * 序列化当前绑定缓存节点列表（供状态 UI 同步显示）。
     * 每项含：坐标/维度/类型(type)/自定义名(name)/流体注册名(fluid)/储量(stored,long)/容量(cap,long)/
     * 速率百分比(rate)/输出模式(out)。节点无法解析时数据回退为空/0，行仍显示（标记离线）。
     */
    public NBTTagList getCacheNodeListTag() {
        NBTTagList list = new NBTTagList();
        for (BoundCacheNode node : mBoundNodes) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", node.x);
            tag.setInteger("y", node.y);
            tag.setInteger("z", node.z);
            tag.setInteger("dim", node.dimensionId);
            IHubCacheNode cacheNode = resolveCacheNode(node, false);
            tag.setString("type", cacheNode != null ? resolveCacheNodeType(cacheNode) : "");
            // 节点自定义名（无则为空串，客户端回退显示默认类型名；奇点仓恒空串）
            tag.setString("name", cacheNode != null ? cacheNode.getCustomName() : "");
            tag.setString("fluid", cacheNode != null ? cacheNode.getStoredFluidName() : "");
            // stored/cap 必须 long：缓存节点容量可能超出 int 范围
            tag.setLong("stored", cacheNode != null ? cacheNode.getStoredFluidAmount() : 0L);
            tag.setLong("cap", cacheNode != null ? cacheNode.getFluidCapacityLong() : 0L);
            // 速率百分比（奇点仓无速率档恒 100；GUI 侧 S4 再对仓隐藏/改容量按钮）
            tag.setInteger("rate", cacheNode != null ? cacheNode.getTransferRatePercent() : 0);
            // 容量档百分比（S4：缓存节点与接收仓生效；发送仓恒 100，GUI 容量按钮对其禁用）
            tag.setInteger("capPct", cacheNode != null ? cacheNode.getCapacityLimitPercent() : 100);
            tag.setBoolean("out", cacheNode != null ? cacheNode.isOutputMode() : node.isOutputMode);
            // 自动输出开关（与方向模式解耦）：节点离线时回退 false（奇点仓恒 false）
            tag.setBoolean("auto", cacheNode != null && cacheNode.isAutoOutput());
            tag.setBoolean("modeLocked", cacheNode != null && cacheNode.isOutputModeLocked());
            list.appendTag(tag);
        }
        return list;
    }

    /** 状态 UI 循环节点交互速率百分比（与手持芯片右击同一循环逻辑；奇点仓为 no-op）。 */
    public void cycleCacheNodeRateFromGui(int x, int y, int z, int dim) {
        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null) return;
        node.cycleTransferRatePercent();
    }

    /** 状态 UI 循环节点容量上限百分比（S4：缓存节点与接收仓；发送仓为 no-op，与空手 Shift 右击同逻辑）。 */
    public void cycleCacheNodeCapFromGui(int x, int y, int z, int dim) {
        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null) return;
        node.cycleCapacityLimitPercent();
    }

    /** 状态 UI 切换节点输出模式：写节点本体 + 同步枢纽侧绑定记录（IHubArray.updateCacheNodeMode）。 */
    public void setCacheNodeModeFromGui(int x, int y, int z, int dim, boolean output) {
        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null) return;
        // 模式锁定节点（奇点仓）：服务端整体拒改（节点与枢纽侧记录都不动，避免传输方向错位）
        if (node.isOutputModeLocked()) return;
        node.setOutputMode(output);
        updateCacheNodeMode(x, y, z, dim, output);
    }

    /** 状态 UI 切换节点自动输出开关：只写节点本体（与方向模式解耦，枢纽绑定记录无需同步）。 */
    public void setCacheNodeAutoFromGui(int x, int y, int z, int dim, boolean auto) {
        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null) return;
        node.setAutoOutput(auto);
    }

    /**
     * 状态 UI 重命名节点：名字在服务端做安全裁剪（剔 §/去首尾空白/≤24 字符），
     * 裁剪后为空表示清除自定义名（UI 回退默认类型名）。
     * 名字变化由列表每 tick 变化检测自动同步到枢纽状态 UI 客户端；
     * 节点方块自身（GUI 标题/Waila）另经 issueTileUpdate 触发 description packet 同步。
     */
    public void renameCacheNodeFromGui(int x, int y, int z, int dim, String name) {
        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null) return;
        node.setCustomName(com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode.sanitizeCustomName(name));
        // 触发节点 TE 重同步（S35 description packet），客户端 MTE 拿到新自定义名以更新 GUI 标题
        if (node instanceof MetaTileEntity mte && mte.getBaseMetaTileEntity() != null) {
            mte.getBaseMetaTileEntity()
                .issueTileUpdate();
        }
    }

    /** Performs the same validated, one-singularity teleport used by the drilling hub status UI. */
    public void teleportPlayerToNodeFromGui(EntityPlayer player, int x, int y, int z, int dim) {
        if (player == null) return;
        if (!canUseStatusAction(player) || findBoundNode(x, y, z, dim) == null) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_node"));
            return;
        }

        World targetWorld = HubTeleportUtil.resolveTargetWorld(player, dim);
        if (targetWorld == null) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_dim"));
            return;
        }
        if (!HubTeleportUtil.ensureChunkLoaded(targetWorld, x, z)) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_node"));
            return;
        }

        IHubCacheNode node = resolveCacheNodeForAction(x, y, z, dim);
        if (node == null || !acceptsNodeType(resolveCacheNodeType(node))) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_node"));
            return;
        }

        int safeY = HubTeleportUtil.findSafeTeleportHeight(targetWorld, x, y, z);
        if (safeY < 0) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_unsafe"));
            return;
        }
        if (!HubTeleportUtil.teleportPlayer(player, targetWorld, dim, x, safeY, z)) {
            GTUtility
                .sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_no_singularity"));
        }
    }

    private boolean canUseStatusAction(EntityPlayer player) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        if (player == null || base == null || world == null || player.dimension != world.provider.dimensionId)
            return false;
        return base.canAccessData()
            && player.getDistanceSq(base.getXCoord() + 0.5D, base.getYCoord() + 0.5D, base.getZCoord() + 0.5D) <= 64.0D;
    }

    private void sendBindingDebug(EntityPlayer aPlayer) {
        GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_title"));
        if (mBoundNodes.isEmpty()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_no_bindings"));
            return;
        }
        if (!hasChipInstalled()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_no_chip"));
        }
        for (BoundCacheNode node : mBoundNodes) {
            String mode = node.isOutputMode ? StatCollector.translateToLocal("gtsr.binding.debug_output")
                : StatCollector.translateToLocal("gtsr.binding.debug_input");
            String posInfo = StatCollector.translateToLocal("gtsr.binding.debug_node") + node.x
                + ", "
                + node.y
                + ", "
                + node.z
                + " "
                + StatCollector.translateToLocal("gtsr.binding.debug_mode")
                + mode;
            GTUtility.sendChatToPlayer(aPlayer, posInfo);
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("mWaterStored", mWaterStored);
        aNBT.setInteger("mHubUnitCount", mHubUnitCount);
        aNBT.setInteger("mReinforcedHubUnitCount", mReinforcedHubUnitCount);
        aNBT.setInteger("mOverpressureHubUnitCount", mOverpressureHubUnitCount);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setInteger("mCasingTier", mCasingTier);
        aNBT.setInteger("mPipeTier", mPipeTier);
        aNBT.setInteger("mFrameTier", mFrameTier);
        aNBT.setBoolean("mOverflowInput", mOverflowInput);
        if (mStoredFluidType != null) {
            aNBT.setString("mStoredFluidType", mStoredFluidType);
        }
        if (!mBoundNodes.isEmpty()) {
            NBTTagCompound boundListTag = new NBTTagCompound();
            boundListTag.setInteger("count", mBoundNodes.size());
            for (int i = 0; i < mBoundNodes.size(); i++) {
                NBTTagCompound nodeTag = new NBTTagCompound();
                mBoundNodes.get(i)
                    .writeToNBT(nodeTag);
                boundListTag.setTag("node" + i, nodeTag);
            }
            aNBT.setTag("mBoundNodes", boundListTag);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mWaterStored = aNBT.getLong("mWaterStored");
        mHubUnitCount = aNBT.getInteger("mHubUnitCount");
        mReinforcedHubUnitCount = aNBT.getInteger("mReinforcedHubUnitCount");
        mOverpressureHubUnitCount = aNBT.getInteger("mOverpressureHubUnitCount");
        mSetTier = aNBT.getInteger("mSetTier");
        mCasingTier = aNBT.getInteger("mCasingTier");
        mPipeTier = aNBT.getInteger("mPipeTier");
        mFrameTier = aNBT.getInteger("mFrameTier");
        mOverflowInput = aNBT.getBoolean("mOverflowInput");
        if (aNBT.hasKey("mStoredFluidType")) {
            mStoredFluidType = aNBT.getString("mStoredFluidType");
        }
        mBoundNodes.clear();
        if (aNBT.hasKey("mBoundNodes")) {
            NBTTagCompound boundListTag = aNBT.getCompoundTag("mBoundNodes");
            int count = boundListTag.getInteger("count");
            for (int i = 0; i < count; i++) {
                NBTTagCompound nodeTag = boundListTag.getCompoundTag("node" + i);
                mBoundNodes.add(BoundCacheNode.readFromNBT(nodeTag));
            }
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
            // 正面三层：tier 基材 + 流体窗（存储流体，空回退水；整面平铺窗直接透出框架开孔，无需旋转）+ 枢纽框架层
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
