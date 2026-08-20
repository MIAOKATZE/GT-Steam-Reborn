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
import com.miaokatze.gtsr.common.gui.MTESteamHubArrayGui;
import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
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

public class MTESteamHubArray extends MTEGTSRMultiBlockBase<MTESteamHubArray>
    implements IConstructable, ISurvivalConstructable, com.miaokatze.gtsr.common.machine.base.IHubArray {

    private static final String STRUCTURE_PIECE_BASE = "base";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final String STRUCTURE_PIECE_CAP = "cap";
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 0;
    private static final int DEPTH_OFF_SET = 1;
    /** 自动输出速率：每 tick 1,000,000 L = 20,000,000 L/s */
    private static final int AUTO_OUTPUT_RATE = 1_000_000;
    private static final int TRANSFER_RATE = 1_000_000;

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

    private static class BoundCacheNode {

        final int x, y, z;
        final int dimensionId;
        final boolean isReinforced;
        boolean isOutputMode;
        transient IGregTechTileEntity cachedTile;
        transient long lastLookupTick;
        transient long nextLookupTick;
        transient boolean lastLookupLoaded;

        BoundCacheNode(int x, int y, int z, int dim, boolean reinforced, boolean outputMode) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimensionId = dim;
            this.isReinforced = reinforced;
            this.isOutputMode = outputMode;
        }

        void invalidateCache() {
            cachedTile = null;
            lastLookupTick = 0;
            nextLookupTick = 0;
            lastLookupLoaded = false;
        }
    }

    private final ArrayList<MTESteamHubInputHatch> mSteamInputHatches = new ArrayList<>();
    private final ArrayList<MTESteamHubOutputHatch> mSteamOutputHatches = new ArrayList<>();
    private final ArrayList<BoundCacheNode> mBoundNodes = new ArrayList<>();

    // S1：奇点仓结构接纳计数（仅结构组成提示用，不注入 mController、不参与传输——传输走终端绑定链）
    private int mSingularitySteamCompartmentCount = 0;
    private int mSingularitySteamOutputCompartmentCount = 0;

    public int mPressureUnitCount = 0;
    public int mReinforcedUnitCount = 0;
    public int mOverpressureUnitCount = 0;
    private int mCasingAmount = 0;
    public int mSetTier = -1;
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
    public boolean mOverflowInput = false;

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

        if (mStackCount == 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        if (!checkPiece(STRUCTURE_PIECE_CAP, HORIZONTAL_OFF_SET, -1, DEPTH_OFF_SET, errors)) {
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        // Validate all tier fields are consistent
        if (mCasingTier <= 0 || mPipeTier <= 0 || mGearTier <= 0 || mFrameTier <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (mCasingTier != mPipeTier || mCasingTier != mGearTier || mCasingTier != mFrameTier) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        mSetTier = mCasingTier;

        if (mSetTier == 1 && (mReinforcedUnitCount > 0 || mOverpressureUnitCount > 0)) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (mSetTier == 2 && (mPressureUnitCount > 0 || mOverpressureUnitCount > 0)) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }
        if (mSetTier >= 3 && (mPressureUnitCount > 0 || mReinforcedUnitCount > 0)) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        if (mSetTier >= 3 && mOverpressureUnitCount <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            getBaseMetaTileEntity().issueTileUpdate();
            return;
        }

        if ((mPressureUnitCount + mReinforcedUnitCount + mOverpressureUnitCount) <= 0) {
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

    public boolean isFormed() {
        return mMachine;
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

    private int getNodeTransferRate(IGregTechTileEntity gte) {
        IMetaTileEntity mte = gte.getMetaTileEntity();
        // S1 类型拓宽：缓存节点=速率百分比实算；奇点仓=固定常量（getEffectiveHubTransferRate 默认实现）
        if (mte instanceof IHubCacheNode cacheNode) {
            return (int) Math.min(cacheNode.getEffectiveHubTransferRate(), Integer.MAX_VALUE);
        }
        return TRANSFER_RATE;
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

        String type = null;
        boolean isReinforced = false;
        if (GTSRItemList.SteamCacheNode.isStackEqual(held, false, true)) {
            type = "steam";
        } else if (GTSRItemList.ReinforcedSteamCacheNode.isStackEqual(held, false, true)) {
            type = "reinforced_steam";
            isReinforced = true;
        } else if (GTSRItemList.OverpressureSteamCacheNode.isStackEqual(held, false, true)) {
            type = "overpressure_steam";
        } else if (GTSRItemList.SingularitySteamCompartment.isStackEqual(held, false, true)) {
            type = "singularity_steam";
        } else if (GTSRItemList.SingularitySteamOutputCompartment.isStackEqual(held, false, true)) {
            type = "singularity_steam_out";
        }

        if (type == null) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        if (!aBaseMetaTileEntity.isServerSide()) return true;

        if ("overpressure_steam".equals(type) && !hasReinforcedChipInstalled()) {
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
                // 奇点仓模式锁定：不提供 output 翻转，右击只解绑（沿用现解绑文案）
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

        // shift 右击：整个手持堆叠全部绑定（奇点消耗 = 单次成本 × 堆叠数量）；
        // 普通右击：拆出 1 个绑定（奇点按类型成本消耗一次），绑定物回背包，手持剩余保持未绑定
        if (aPlayer.isSneaking()) {
            bindWholeHeld(aPlayer, held, type, isReinforced, myX, myY, myZ, myDim);
        } else {
            bindOneFromHeld(aPlayer, held, type, isReinforced, myX, myY, myZ, myDim);
        }
        return true;
    }

    /**
     * 普通右击：从手持堆叠拆出 1 个缓存节点绑定到本枢纽（无 singularity_consumed 标记则按类型成本
     * 消耗一次奇点：steam=0 仅打标记；reinforced_steam=1；overpressure_steam=8），
     * 写 hubPos NBT 后放回玩家背包（背包无空位则落地），手持剩余 N-1 个保持未绑定。
     * 绑定他处的堆叠仅覆盖拆出的这 1 个。
     */
    private void bindOneFromHeld(EntityPlayer aPlayer, ItemStack held, String type, boolean isReinforced, int myX,
        int myY, int myZ, int myDim) {
        // 先按手持标记状态决定是否消耗：无标记则按类型成本消耗一次（不足则报错不执行，保持手持原状）
        if (!held.hasTagCompound() || !held.getTagCompound()
            .hasKey("gtsr.singularity_consumed")) {
            int singularityCost = getSingularityCost(type);
            if (singularityCost > 0 && !consumeSteamEntangledSingularities(aPlayer, singularityCost)) {
                GTUtility.sendChatToPlayer(
                    aPlayer,
                    StatCollector.translateToLocal("gtsr.binding.no_singularity") + " (" + singularityCost + ")");
                return;
            }
        }

        // 拆 1 个（copy + 减量，≤0 则清手持槽）
        ItemStack bound = held.copy();
        bound.stackSize = 1;
        held.stackSize--;
        if (held.stackSize <= 0) {
            aPlayer.inventory.mainInventory[aPlayer.inventory.currentItem] = null;
        }

        // 打标记（拆出物继承原 NBT，无标记则补；标记/消耗只作用于拆出物）
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
        hubTag.setBoolean("reinforced", isReinforced);
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
     * shift 右击：整个手持堆叠全部绑定到本枢纽，奇点消耗 = 单次成本 × 堆叠数量
     * （背包总量不足则报错不执行；steam=0 仅打标记）。绑定他处的堆叠覆盖整堆。
     */
    private void bindWholeHeld(EntityPlayer aPlayer, ItemStack held, String type, boolean isReinforced, int myX,
        int myY, int myZ, int myDim) {
        // 无标记则按"单次成本 × 堆叠数量"消耗奇点并给整堆打标记
        if (!held.hasTagCompound() || !held.getTagCompound()
            .hasKey("gtsr.singularity_consumed")) {
            int singularityCost = getSingularityCost(type) * held.stackSize;
            if (singularityCost > 0 && !consumeSteamEntangledSingularities(aPlayer, singularityCost)) {
                GTUtility.sendChatToPlayer(
                    aPlayer,
                    StatCollector.translateToLocal("gtsr.binding.no_singularity") + " (" + singularityCost + ")");
                return;
            }
            if (!held.hasTagCompound()) {
                held.setTagCompound(new NBTTagCompound());
            }
            held.getTagCompound()
                .setBoolean("gtsr.singularity_consumed", true);
        }

        // 整堆写 hubPos（覆盖绑定他处的旧 hubPos）
        if (!held.hasTagCompound()) {
            held.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound hubTag = new NBTTagCompound();
        hubTag.setInteger("x", myX);
        hubTag.setInteger("y", myY);
        hubTag.setInteger("z", myZ);
        hubTag.setInteger("dim", myDim);
        hubTag.setString("type", type);
        hubTag.setBoolean("output", getLockedItemOutput(type));
        hubTag.setBoolean("reinforced", isReinforced);
        held.getTagCompound()
            .setTag("gtsr.hubPos", hubTag);

        aPlayer.inventoryContainer.detectAndSendChanges();
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.binding.bound_output") + held.getDisplayName()
                + StatCollector.translateToLocal("gtsr.binding.mode_output"));
    }

    /**
     * 绑定奇点成本表：steam=0、reinforced_steam=1、overpressure_steam=8、
     * 奇点蒸汽仓/奇点蒸汽输出仓=1。
     */
    private static int getSingularityCost(String type) {
        if ("reinforced_steam".equals(type)) return 1;
        if ("overpressure_steam".equals(type)) return 8;
        if ("singularity_steam".equals(type) || "singularity_steam_out".equals(type)) return 1;
        return 0;
    }

    /** 奇点仓类型（模式锁定，右键已绑定分支只解绑不翻转）。 */
    private static boolean isModeLockedType(String type) {
        return "singularity_steam".equals(type) || "singularity_steam_out".equals(type);
    }

    /**
     * 锁定类型绑定时的 item output 恒定值（反转语义：false=枢纽→节点/接收仓，true=节点→枢纽/发送仓；
     * 与 loadNBTData 强制归位值互补）。非锁定类型保持 false（现状）。
     */
    private static boolean getLockedItemOutput(String type) {
        return "singularity_steam_out".equals(type);
    }

    /**
     * 从玩家主物品栏消耗指定数量个蒸汽纠缠奇点（背包总量不足时不消耗并返回 false）。
     * 
     * @return 是否成功消耗
     */
    private static boolean consumeSteamEntangledSingularities(EntityPlayer player, int amount) {
        int found = 0;
        for (ItemStack invStack : player.inventory.mainInventory) {
            if (invStack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(invStack, true, true)) {
                found += invStack.stackSize;
            }
        }
        if (found < amount) return false;
        int remaining = amount;
        for (int i = 0; i < player.inventory.mainInventory.length && remaining > 0; i++) {
            ItemStack invStack = player.inventory.mainInventory[i];
            if (invStack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(invStack, true, true)) {
                int toConsume = Math.min(remaining, invStack.stackSize);
                invStack.stackSize -= toConsume;
                remaining -= toConsume;
                if (invStack.stackSize <= 0) {
                    player.inventory.mainInventory[i] = null;
                }
            }
        }
        player.inventoryContainer.detectAndSendChanges();
        return true;
    }

    private boolean hasChipInstalled() {
        ItemStack stack = getControllerSlot();
        return stack != null && (GTSRItemList.HubSingularityChip.isStackEqual(stack, true, true)
            || GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(stack, true, true));
    }

    private boolean hasHubChipInstalled() {
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.HubSingularityChip.isStackEqual(stack, true, true);
    }

    private boolean hasReinforcedChipInstalled() {
        if (mSetTier < 3) return false;
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(stack, true, true);
    }

    private BoundCacheNode findBoundNode(int x, int y, int z, int dim) {
        for (BoundCacheNode node : mBoundNodes) {
            if (node.x == x && node.y == y && node.z == z && node.dimensionId == dim) {
                return node;
            }
        }
        return null;
    }

    @Override
    public void registerCacheNode(int x, int y, int z, int dim, boolean isOutputMode) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            existing.isOutputMode = isOutputMode;
        } else {
            mBoundNodes.add(new BoundCacheNode(x, y, z, dim, false, isOutputMode));
        }
    }

    @Override
    public void unregisterCacheNode(int x, int y, int z, int dim) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            existing.invalidateCache();
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
        return "steam".equals(type) || "reinforced_steam".equals(type)
            || "overpressure_steam".equals(type)
            || "singularity_steam".equals(type)
            || "singularity_steam_out".equals(type);
    }

    /**
     * 打开缓存节点状态管理界面（Modern UI 2）。必须在服务端调用，
     * 实际打开逻辑委托给 SteamHubStatusGuiFactory（独立 MUI2 factory，不影响主 GUI）。
     */
    public void openHubStatusGui(EntityPlayer player) {
        com.miaokatze.gtsr.common.gui.SteamHubStatusGuiFactory.open(player, this);
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
        bound.invalidateCache();
        return resolveCacheNode(bound, true);
    }

    /**
     * Resolves a bound node at most once per world tick. UI polling never loads chunks; explicit transfer/action
     * operations may load the target chunk. A temporarily unavailable world/chunk is not treated as an invalid node.
     */
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
            bound.nextLookupTick = now + 20;
            return null;
        }
        if (!world.blockExists(bound.x, 0, bound.z)) {
            if (!loadChunk || !HubTeleportUtil.ensureChunkLoaded(world, bound.x, bound.z)) {
                bound.cachedTile = null;
                bound.nextLookupTick = now + 20;
                return null;
            }
        }
        if (!world.blockExists(bound.x, bound.y, bound.z)) {
            bound.cachedTile = null;
            bound.nextLookupTick = now + 20;
            return null;
        }

        bound.lastLookupLoaded = true;
        TileEntity te = world.getTileEntity(bound.x, bound.y, bound.z);
        if (te instanceof IGregTechTileEntity gte && gte.getMetaTileEntity() instanceof IHubCacheNode node) {
            bound.cachedTile = gte;
            bound.nextLookupTick = now + 20;
            return node;
        }
        bound.cachedTile = null;
        bound.nextLookupTick = now + 20;
        return null;
    }

    /**
     * 按实际节点类判定类型字符串（不用缓存字段，避免 BoundCacheNode 无 reinforced 字段时误判）。
     * 各节点类与四仓互不继承、共同实现 IHubCacheNode（S1 起），instanceof 顺序无关。
     */
    private static String resolveCacheNodeType(IHubCacheNode node) {
        if (node instanceof MTEOverpressureSteamCacheNode) return "overpressure_steam";
        if (node instanceof MTEReinforcedSteamCacheNode) return "reinforced_steam";
        if (node instanceof MTESteamCacheNode) return "steam";
        if (node instanceof MTESingularitySteamOutputCompartment) return "singularity_steam_out";
        if (node instanceof MTESingularitySteamCompartment) return "singularity_steam";
        return "";
    }

    /**
     * 序列化当前绑定缓存节点列表（供状态 UI 同步显示）。
     * 每项含：坐标/维度/类型(type)/自定义名(name)/流体名(fluid)/储量(stored,long)/容量(cap,long)/
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
            // stored/cap 必须 long：强化/超压节点容量超出 int 范围
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
            String posInfo = StatCollector.translateToLocal("gtsr.binding.debug_node") + "("
                + node.x
                + ", "
                + node.y
                + ", "
                + node.z
                + ") DIM:"
                + node.dimensionId
                + " "
                + StatCollector.translateToLocal("gtsr.binding.debug_mode")
                + mode;
            GTUtility.sendChatToPlayer(aPlayer, posInfo);
        }
    }

    private void transferWithBoundNodes() {
        if (!hasChipInstalled()) {
            return;
        }

        ArrayList<BoundCacheNode> invalidNodes = new ArrayList<>();

        for (BoundCacheNode node : mBoundNodes) {
            IHubCacheNode cacheNode = resolveCacheNode(node, true);
            if (cacheNode == null) {
                if (node.lastLookupLoaded) invalidNodes.add(node);
                continue;
            }
            if (!acceptsNodeType(resolveCacheNodeType(cacheNode)) || node.cachedTile == null) {
                invalidNodes.add(node);
                continue;
            }
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
            // 正面三层：tier 基材 + 流体窗（存储流体，空回退蒸汽；整面平铺窗直接透出框架开孔，无需旋转）+ 枢纽框架层
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
