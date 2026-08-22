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
import net.minecraft.nbt.NBTTagList;
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

public class MTESteamHubArray extends MTEHubArrayBase<MTESteamHubArray>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_BASE = "base";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final String STRUCTURE_PIECE_CAP = "cap";
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 0;
    private static final int DEPTH_OFF_SET = 1;
    /** 自动输出速率：每 tick 1,000,000 L = 20,000,000 L/s */
    private static final int AUTO_OUTPUT_RATE = 1_000_000;

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    // 正面枢纽框架层（customAlpha pass1 图标，extFacing 对齐多方块旋转）：registerIcons（仅客户端）时
    // 构造并静态缓存，getTexture 复用勿每调用 new，服务端类加载不触碰渲染类
    private static ITexture FRAME_UNBOUND_FACING;

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
    private int mCasingTier = -1;
    private int mPipeTier = -1;
    private int mGearTier = -1;
    private int mFrameTier = -1;
    public int mStackCount = 0;
    public long mSteamStored = 0;
    private FluidStack mStoredFluidType = null;
    // 存储流体名的客户端副本（description packet 同步）：正面流体窗取流体用，空串=无（回退默认蒸汽）
    private String mClientFluidName = "";
    /** 渲染状态同步去重 key（存储流体名），服务端 onPostTick 维护，变化才 issueTileUpdate。 */
    private String mLastSyncKey = null;
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
        return new MTESteamHubArray(mName);
    }

    @Override
    public IStructureDefinition<MTESteamHubArray> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        for (MTESteamHubInputHatch hatch : mSteamInputHatches) {
            hatch.mController = null;
        }
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            hatch.mController = null;
        }

        mPressureUnitCount = 0;
        mReinforcedUnitCount = 0;
        mOverpressureUnitCount = 0;
        mCasingAmount = 0;
        mSetTier = -1;
        mCasingTier = -1;
        mPipeTier = -1;
        mGearTier = -1;
        mFrameTier = -1;
        mStackCount = 0;
        mSteamInputHatches.clear();
        mSteamOutputHatches.clear();
        mSingularitySteamCompartmentCount = 0;
        mSingularitySteamOutputCompartmentCount = 0;

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

        if (!checkPiece(STRUCTURE_PIECE_CAP, HORIZONTAL_OFF_SET, -1, DEPTH_OFF_SET, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        // Validate all tier fields are consistent
        if (!require(mCasingTier > 0 && mPipeTier > 0 && mGearTier > 0 && mFrameTier > 0, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (!require(mCasingTier == mPipeTier && mCasingTier == mGearTier && mCasingTier == mFrameTier, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        mSetTier = mCasingTier;

        if (!require(mSetTier != 1 || (mReinforcedUnitCount <= 0 && mOverpressureUnitCount <= 0), errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (!require(mSetTier != 2 || (mPressureUnitCount <= 0 && mOverpressureUnitCount <= 0), errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (!require(mSetTier < 3 || (mPressureUnitCount <= 0 && mReinforcedUnitCount <= 0), errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        if (!require(mSetTier < 3 || mOverpressureUnitCount > 0, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        if (!require((mPressureUnitCount + mReinforcedUnitCount + mOverpressureUnitCount) > 0, errors)) {
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
        for (MTESteamHubInputHatch hatch : mSteamInputHatches) {
            hatch.updateTexture(tierCasingIndex);
        }
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            hatch.updateTexture(tierCasingIndex);
        }

        getBaseMetaTileEntity().issueTileUpdate();
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

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || !mMachine) return;

        long totalCapacity = getTotalCapacity();
        if (mSteamStored > totalCapacity) {
            mSteamStored = totalCapacity;
        }

        autoOutputSteam();

        // 存储流体名变化才发 description packet（首流锁定与抽干清空自然覆盖，量变化不发包，稳态零流量）
        String syncKey = mStoredFluidType != null ? mStoredFluidType.getFluid()
            .getName() : "";
        if (!syncKey.equals(mLastSyncKey)) {
            mLastSyncKey = syncKey;
            aBaseMetaTileEntity.issueTileUpdate();
        }

        mTickCounter++;
        if (mTickCounter % 20 == 0) {
            transferWithBoundNodes();
        }
    }

    private void autoOutputSteam() {
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
     * 打开缓存节点状态管理界面（Modern UI 2）。必须在服务端调用，
     * 实际打开逻辑委托给 SteamHubStatusGuiFactory（独立 MUI2 factory，不影响主 GUI）。
     */
    @Override
    public void openHubStatusGui(EntityPlayer player) {
        com.miaokatze.gtsr.common.gui.SteamHubStatusGuiFactory.open(player, this);
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

    private void transferWithBoundNodes() {
        boolean chipInstalled = hasChipInstalled();
        ArrayList<BoundCacheNode> invalidNodes = new ArrayList<>();

        for (BoundCacheNode node : mBoundNodes) {
            IHubCacheNode cacheNode = resolveCacheNode(node, true);
            if (cacheNode == null) {
                if (node.lastLookupLoaded) invalidNodes.add(node);
                continue;
            }
            if (!acceptsNodeType(resolveNodeType(cacheNode)) || node.cachedTile == null) {
                invalidNodes.add(node);
                continue;
            }
            if (!chipInstalled) continue;
            IGregTechTileEntity gte = node.cachedTile;

            if (node.isOutputMode) {
                int nodeRate = getNodeTransferRate(gte);
                FluidStack toSend = extractSteam(nodeRate, false);
                if (toSend != null && toSend.amount > 0) {
                    int filled = gte.fill(ForgeDirection.UNKNOWN, toSend, true);
                    if (filled > 0) extractSteam(filled, true);
                }
            } else {
                int nodeRate = getNodeTransferRate(gte);
                FluidStack drained = gte.drain(ForgeDirection.UNKNOWN, nodeRate, false);
                if (drained != null && drained.amount > 0) {
                    int received = receiveSteam(drained, true);
                    if (received > 0) gte.drain(ForgeDirection.UNKNOWN, received, true);
                }
            }
        }

        mBoundNodes.removeAll(invalidNodes);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("mSteamStored", mSteamStored);
        aNBT.setInteger("mPressureUnitCount", mPressureUnitCount);
        aNBT.setInteger("mReinforcedUnitCount", mReinforcedUnitCount);
        aNBT.setInteger("mOverpressureUnitCount", mOverpressureUnitCount);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setInteger("mCasingTier", mCasingTier);
        aNBT.setInteger("mPipeTier", mPipeTier);
        aNBT.setInteger("mGearTier", mGearTier);
        aNBT.setInteger("mFrameTier", mFrameTier);
        aNBT.setLong("mTickCounter", mTickCounter);
        aNBT.setBoolean("mOverflowInput", mOverflowInput);
        if (mStoredFluidType != null) {
            NBTTagCompound fluidTag = new NBTTagCompound();
            mStoredFluidType.writeToNBT(fluidTag);
            aNBT.setTag("mStoredFluidType", fluidTag);
        }
        if (!mBoundNodes.isEmpty()) {
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
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSteamStored = aNBT.getLong("mSteamStored");
        mPressureUnitCount = aNBT.getInteger("mPressureUnitCount");
        mReinforcedUnitCount = aNBT.getInteger("mReinforcedUnitCount");
        mOverpressureUnitCount = aNBT.getInteger("mOverpressureUnitCount");
        mSetTier = aNBT.getInteger("mSetTier");
        mCasingTier = aNBT.getInteger("mCasingTier");
        mPipeTier = aNBT.getInteger("mPipeTier");
        mGearTier = aNBT.getInteger("mGearTier");
        mFrameTier = aNBT.getInteger("mFrameTier");
        mTickCounter = aNBT.getLong("mTickCounter");
        mOverflowInput = aNBT.getBoolean("mOverflowInput");
        if (aNBT.hasKey("mStoredFluidType")) {
            mStoredFluidType = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mStoredFluidType"));
        }
        mBoundNodes.clear();
        if (aNBT.hasKey("mBoundNodes")) {
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
    }

    @Override
    public NBTTagCompound getDescriptionData() {
        NBTTagCompound data = super.getDescriptionData();
        if (data == null) data = new NBTTagCompound();
        data.setInteger("mSetTier", mSetTier);
        // 正面流体窗渲染状态：存储流体名（空串=无，客户端回退默认蒸汽）
        data.setString(
            "gtsr.hubFluid",
            mStoredFluidType != null ? mStoredFluidType.getFluid()
                .getName() : "");
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
            // 正面三层：tier 基材 + 内缩流体窗（存储流体，空回退蒸汽；窗收在框架环内，与节点/仓同变体）+ 枢纽框架层
            Fluid fluid = FluidRegistry.getFluid(mClientFluidName);
            if (fluid == null) fluid = FluidRegistry.getFluid("steam");
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingTextureId),
                GTSRFluidWindowTexture.getOrCreate(fluid), FRAME_UNBOUND_FACING };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingTextureId) };
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new MTESteamHubArrayGui(this);
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
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.storage_units")
                        + " "
                        + EnumChatFormatting.GOLD
                        + (mPressureUnitCount + mReinforcedUnitCount + mOverpressureUnitCount)
                        + "/"
                        + (25 * mStackCount)
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.steam_buffer")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + UnitFormatUtil.format(mSteamStored)
                        + " L"
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.steam_hub.total_capacity")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + UnitFormatUtil.format(getTotalCapacity())
                        + " L"
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mStackCount, val -> mStackCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mPressureUnitCount, val -> mPressureUnitCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mReinforcedUnitCount, val -> mReinforcedUnitCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mOverpressureUnitCount, val -> mOverpressureUnitCount = val))
            .widget(new FakeSyncWidget.LongSyncer(() -> mSteamStored, val -> mSteamStored = val));
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.steam_hub.type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        String tierText = mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
            : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
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
        int totalUnits = mPressureUnitCount + mReinforcedUnitCount + mOverpressureUnitCount;
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.storage_units")
                + " "
                + EnumChatFormatting.GOLD
                + totalUnits
                + "/"
                + (25 * mStackCount)
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.steam_buffer")
                + " "
                + EnumChatFormatting.LIGHT_PURPLE
                + UnitFormatUtil.format(mSteamStored)
                + " L"
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.steam_hub.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_hub.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_hub.desc2"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.steam_hub.desc2_2"))
            .addInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.steam_hub.chip_1"))
            .addInfo(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.steam_hub.chip_2"))
            .addInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.screwdriver_overflow"))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.shared.overflow_input_desc"))
            .beginStructureBlock(9, 32, 9, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.steam_hub.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub.hub_input"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub.hub_output"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub.storage"),
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub.storage"),
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
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 70, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 7, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 24, false)
            .addStructureHint("gtsr.tooltip.steam_hub.height")
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.steam_hub.hint_tier1")
            .addStructureHint("gtsr.tooltip.steam_hub.hint_tier2")
            .addStructureHint("gtsr.tooltip.steam_hub.hint_tier3")
            .addStructureHint("gtsr.tooltip.shared.hub_singularity_cost")
            .addStructureHint("gtsr.tooltip.shared.overflow_input_screwdriver")
            .addStructureHint("gtsr.tooltip.steam_hub.hint_status")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }
}
