package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlockAdder;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
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
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;

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

/**
 * 蒸汽奇点压缩机 v2。
 * <p>
 * 脱离 GT++ 蒸汽多机基类：不再使用 lEUt 蒸汽消耗机制，改为每次周期检查吞噬输入仓中最高等级的蒸汽
 * （仿 GT5U 通用化学燃料引擎：checkProcessing 每次调用即一个完整周期，成功时置 20 tick 进度，
 * 完成后基类立即重查，形成连续循环），按对数方程提升热量。
 * <ul>
 * <li>等级 1：钢外壳 + 钢齿轮箱 + 钢管道 + 防爆玻璃 + 钢框架，只识别 蒸汽/过热/超临界；热量 y=0.005x/(x+200000)</li>
 * <li>等级 2：强化镀铑钯外壳/齿轮箱/管道 + LuV+ 玻璃 + 镀铑钯框架，可识别致密变体；热量 y=0.002x/(x+1000)</li>
 * <li>热量 ≥100% 清零并产出 1 个蒸汽纠缠奇点（等级 1）/ 临界蒸汽纠缠奇点（等级 2）</li>
 * <li>等级 2 螺丝刀切换致密蒸汽压缩模式：每输入 1 个蒸汽纠缠奇点运行 600s，蒸汽按 1000:1 压缩为致密蒸汽</li>
 * </ul>
 */
public class MTESteamSingularityCompressor extends MTEEnhancedMultiBlockBase<MTESteamSingularityCompressor>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 5;
    private static final int VERTICAL_OFF_SET = 8;
    private static final int DEPTH_OFF_SET = 2;

    /** 单周期进度长度（20 tick，成功后基类立即重查形成连续循环） */
    private static final int CYCLE_LENGTH = 20;
    /** 无蒸汽或关机时每秒热量衰减 */
    private static final double HEAT_DECAY_PER_SECOND = 0.01d;
    /** 蒸汽纠缠奇点续航时长：600s（无冗余，结束时无缝续杯） */
    private static final int SINGULARITY_DURATION_TICKS = 12000;
    /** 等级 1 基础热量上限（0.5%/s） */
    private static final double TIER1_HEAT_MAX = 0.005d;
    /** 等级 1 对数方程半值点 */
    private static final long TIER1_HEAT_HALF_L = 200000L;
    /** 等级 2 基础热量上限（0.2%/s） */
    private static final double TIER2_HEAT_MAX = 0.002d;
    /** 等级 2 对数方程半值点 */
    private static final long TIER2_HEAT_HALF_L = 1000L;
    /** 蒸汽系数：蒸汽/致密蒸汽 0.5；过热/致密过热 1；超临界/致密超临界 2 */
    private static final double[] GRADE_COEF = { 0.5d, 1.0d, 2.0d };
    private static final String[] DENSE_FLUID_NAMES = { "densesteam", "densesuperheatedsteam",
        "densesupercriticalsteam" };
    /** 致密压缩倍率：1000:1 */
    private static final long DENSE_COMPRESSION_RATIO = 1000L;

    private static IStructureDefinition<MTESteamSingularityCompressor> STRUCTURE_DEFINITION = null;
    private static IIconContainer OVERLAY_OFF;
    private static IIconContainer OVERLAY_ON;
    private static Block TIER2_FRAME_BLOCK = null;
    private static Integer TIER2_FRAME_META = null;

    /** 当前结构等级（1/2，由 checkMachine 判定） */
    public int mTier = 0;
    public double mHeat = 0.0d;
    /** 致密蒸汽压缩模式开关（仅等级 2，关机且热量清零时可切换） */
    public boolean mDenseMode = false;
    /** 致密模式剩余续航 tick（600s 计时） */
    public int mDenseTicks = 0;

    private int mCasingTierB = -1;
    private int mCasingTierC = -1;
    private int mCasingTierD = -1;
    private int mCasingTierE = -1;
    private int mCasingTierF = -1;

    /** 耐压蒸汽输入仓（不继承 MTEHatchInput，独立列表） */
    private final List<MTEHatchPressureSteamInput> mPressureSteamInputs = new ArrayList<>();

    public MTESteamSingularityCompressor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESteamSingularityCompressor(String aName) {
        super(aName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = Textures.BlockIcons.custom("gtsr:MTESteamSingularityCompressor_OFF");
        OVERLAY_ON = Textures.BlockIcons.custom("gtsr:MTESteamSingularityCompressor_ON");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamSingularityCompressor(mName);
    }

    /**
     * 等级 2 框架方块：GT5U 的 BW 装饰框架（bw.frames，BlockDecorativeFrame），按 Werkstoff ID 索引 meta。
     * 镀铑钯（RhodiumPlatedPalladium）的 Werkstoff ID 为 88。
     */
    private static Block getTier2FrameBlock() {
        if (TIER2_FRAME_BLOCK == null) {
            TIER2_FRAME_BLOCK = GregTechAPI.sBlockFramesBW;
            if (TIER2_FRAME_BLOCK == null) {
                TIER2_FRAME_BLOCK = GameRegistry.findBlock("gregtech", "bw.frames");
            }
        }
        return TIER2_FRAME_BLOCK;
    }

    private static int getTier2FrameMeta() {
        if (TIER2_FRAME_META == null) {
            final Werkstoff werkstoff = WerkstoffLoader.RhodiumPlatedPalladium;
            TIER2_FRAME_META = werkstoff != null ? (int) werkstoff.getmID() : 88;
        }
        return TIER2_FRAME_META;
    }

    /**
     * 注意：非匹配方块必须返回 {@code null}（不得返回 notSet=-1），否则 StructureLib 的
     * ofBlocksTiered 会把未定级状态下的任意方块（含 hatch、空气）当作 -1 级外壳接受，
     * 导致 hatch 被吞（不注册）或错误方块被误收，结构无法成型。
     */
    @Nullable
    private static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings8 && meta == 6) return 2;
        return null;
    }

    /** 'C'（管道位）等级：等级 1 钢管（sBlockCasings2:13），等级 2 强化镀铑钯外壳（sBlockCasings8:6） */
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

    protected int getCasingTextureIndex() {
        return mTier >= 2 ? GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6)
            : GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    }

    protected void updateHatchTextures() {
        int textureID = getCasingTextureIndex();
        for (MTEHatch h : mInputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mOutputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mInputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mPressureSteamInputs) h.updateTexture(textureID);
        // v1.9.41 同款修复：ME 样板仓/输入总线注册在 mDualInputHatches，漏更会停滞钢材质
        for (IDualInputHatch h : mDualInputHatches) h.updateTexture(textureID);
    }

    /**
     * mTier 客户端同步：checkMachine 只在服务端执行，客户端 MTE 的 mTier 恒为 0，
     * 等级 2 控制器底材会一直渲染钢外壳；通过 getUpdateData/onValueUpdate 同步
     * （结构成型时 MTEEnhancedMultiBlockBase.onStructureCheckFinished 触发 issueTileUpdate）。
     */
    @Override
    public void onValueUpdate(byte aValue) {
        mTier = aValue;
    }

    @Override
    public byte getUpdateData() {
        return (byte) mTier;
    }

    @Override
    public IStructureDefinition<MTESteamSingularityCompressor> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
            final List<Pair<Block, Integer>> casingTiers = new ArrayList<>();
            casingTiers.add(Pair.of(GregTechAPI.sBlockCasings2, 0)); // 1
            casingTiers.add(Pair.of(GregTechAPI.sBlockCasings8, 6)); // 2
            final List<Pair<Block, Integer>> frameTiers = new ArrayList<>();
            frameTiers.add(Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)); // 1
            frameTiers.add(Pair.of(getTier2FrameBlock(), getTier2FrameMeta())); // 2

            STRUCTURE_DEFINITION = StructureDefinition.<MTESteamSingularityCompressor>builder()
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
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        ofBlocksTiered(
                            MTESteamSingularityCompressor::getCasingTier,
                            casingTiers,
                            -1,
                            (t, tier) -> t.mCasingTierB = tier,
                            t -> t.mCasingTierB),
                        buildHatchAdder(MTESteamSingularityCompressor.class).atLeast(CompressorHatchElement.SteamInput)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTESteamSingularityCompressor.class)
                            .atLeast(CompressorHatchElement.SteamInputBus)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTESteamSingularityCompressor.class)
                            .atLeast(CompressorHatchElement.SteamOutputBus)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTESteamSingularityCompressor.class)
                            .atLeast(CompressorHatchElement.SteamOutputHatch)
                            .casingIndex(casingIndex)
                            .hint(2)
                            .build()))
                .addElement(
                    'C',
                    ofBlocksTiered(
                        MTESteamSingularityCompressor::getPipeTier,
                        casingTiers,
                        -1,
                        (t, tier) -> t.mCasingTierC = tier,
                        t -> t.mCasingTierC))
                .addElement(
                    'D',
                    ofBlocksTiered(
                        MTESteamSingularityCompressor::getCasingTier,
                        casingTiers,
                        -1,
                        (t, tier) -> t.mCasingTierD = tier,
                        t -> t.mCasingTierD))
                .addElement(
                    'E',
                    ofChain(
                        // 等级 2 优先：LuV+ 玻璃（GlassTier 系统，等级 ≥ LuV），NEI 投影据此显示 LuV 玻璃
                        ofBlockAdder((t, block, meta) -> {
                            Integer glassTier = GlassTier.getGlassBlockTier(block, meta);
                            if (glassTier == null || glassTier < VoltageIndex.LuV) return false;
                            t.mCasingTierE = 2;
                            return true;
                        }, ItemRegistry.bw_realglas, 3),
                        // 等级 1：防爆玻璃（兼容 beta-1 IC2 blockAlloyGlass / beta-2 GT5U sBlockGlass1 meta 10）
                        onElementPass(
                            t -> t.mCasingTierE = 1,
                            ofBlock(
                                GTVersionCompat.getReinforcedGlassBlock(),
                                GTVersionCompat.getReinforcedGlassMeta()))))
                .addElement(
                    'F',
                    ofBlocksTiered(
                        MTESteamSingularityCompressor::getFrameTier,
                        frameTiers,
                        -1,
                        (t, tier) -> t.mCasingTierF = tier,
                        t -> t.mCasingTierF))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    /**
     * 压缩机本地仓室元素。
     * <p>
     * 输入侧接受：输入仓/蒸汽仓（MTEHatchInput 及其子类，含 ME 输入仓、巨型超压蒸汽输入仓）
     * 与耐压蒸汽仓（MTEHatchPressureSteamInput）；输出侧接受：输出总线（含 ME 输出总线）、
     * 输出总线（蒸汽）（MTEHatchSteamBusOutput）与输出仓（含 ME 输出仓）。
     * {@code mteBlacklist()} 排除自定义仓类在 NEI 投影中覆盖外壳占位。
     */
    private enum CompressorHatchElement implements IHatchElement<MTESteamSingularityCompressor> {

        SteamInput("GTSR.HatchElement.SteamInput", MTESteamSingularityCompressor::addSteamInputToMachineList,
            MTEHatchInput.class, MTEHatchPressureSteamInput.class) {

            @Override
            public long count(MTESteamSingularityCompressor t) {
                return t.mInputHatches.size() + t.mPressureSteamInputs.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchPressureSteamInput.class);
            }
        },

        SteamInputBus("GTSR.HatchElement.SteamInputBus", MTESteamSingularityCompressor::addInputBusToMachineList,
            MTEHatchInputBus.class) {

            @Override
            public long count(MTESteamSingularityCompressor t) {
                return t.mInputBusses.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchSteamBusInput.class);
            }
        },

        SteamOutputBus("GTSR.HatchElement.SteamOutputBus", MTESteamSingularityCompressor::addOutputBusToMachineList,
            MTEHatchOutputBus.class) {

            @Override
            public long count(MTESteamSingularityCompressor t) {
                return t.mOutputBusses.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchSteamBusOutput.class);
            }
        },

        SteamOutputHatch("GTSR.HatchElement.SteamOutputHatch",
            MTESteamSingularityCompressor::addOutputHatchToMachineList, MTEHatchOutput.class) {

            @Override
            public long count(MTESteamSingularityCompressor t) {
                return t.mOutputHatches.size();
            }
        };

        private final String translationKey;
        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTESteamSingularityCompressor> adder;

        @SafeVarargs
        CompressorHatchElement(String translationKey, IGTHatchAdder<MTESteamSingularityCompressor> adder,
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
        public IGTHatchAdder<? super MTESteamSingularityCompressor> adder() {
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

    /**
     * 蒸汽输入仓 adder：接受标准输入仓（MTEHatchInput 及其子类，含 ME 仓与巨型超压蒸汽输入仓）
     * 与耐压蒸汽仓（MTEHatchPressureSteamInput）。
     */
    public boolean addSteamInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte == null) return false;
        if (mte instanceof MTEHatchInput) {
            return addInputHatchToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (mte instanceof MTEHatchPressureSteamInput hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mPressureSteamInputs.add(hatch);
        }
        return false;
    }

    /**
     * 输出总线 adder：GT5U 的 {@code addOutputBusToMachineList} 显式拒绝
     * {@code MTEHatchSteamBusOutput}，这里放开蒸汽输出总线（它继承 MTEHatchOutputBus），
     * 标准输出总线与 ME 输出总线照常接受。
     */
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
        mCasingTierB = -1;
        mCasingTierC = -1;
        mCasingTierD = -1;
        mCasingTierE = -1;
        mCasingTierF = -1;
        mPressureSteamInputs.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }

        int tier = mCasingTierB;
        if (mCasingTierC != tier || mCasingTierD != tier
            || mCasingTierE != tier
            || mCasingTierF != tier
            || (tier != 1 && tier != 2)) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        mTier = tier;

        if ((mInputHatches.isEmpty() && mPressureSteamInputs.isEmpty()) || mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        // 等级 2 的致密模式需要输出仓
        if (mTier == 2 && mOutputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mTier < 2) {
            mDenseMode = false;
        }

        updateHatchTextures();
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        // 每次被基类调用即执行一个完整周期（脱体 lEUt，无 EU 需求）。
        // 注意：不能在内部按世界时间（getTimer % 20）门控——基类用 mTotalRunTime 相位
        // 每 100 tick 检查一次配方，与世界时间相位固定错位时机器会永远点不着火。
        // 成功时 startCycle 置 20 tick 进度，进度完成后基类会立即重查，形成连续循环。
        return mDenseMode ? processDenseCycle() : processHeatCycle();
    }

    /** 蓄热周期：吞噬最高等级蒸汽并提升热量 */
    private CheckRecipeResult processHeatCycle() {
        boolean includeDense = mTier >= 2;
        int grade = findHighestGrade(includeDense);
        if (grade < 0) {
            // 无蒸汽：不在这里衰减（由 onPostTick 每秒衰减 1%），直接待机
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        long x = sumGrade(grade, includeDense);
        drainGrade(grade, includeDense);
        double base = mTier >= 2 ? TIER2_HEAT_MAX * x / (x + TIER2_HEAT_HALF_L)
            : TIER1_HEAT_MAX * x / (x + TIER1_HEAT_HALF_L);
        mHeat += GRADE_COEF[grade] * base;

        if (mHeat >= 1.0d) {
            addOutputPartial(
                mTier >= 2 ? GTSRItemList.CriticalSteamEntangledSingularity.get(1)
                    : GTSRItemList.SteamEntangledSingularity.get(1));
            mHeat = 0.0d;
        }
        startCycle();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** 致密压缩周期：蒸汽按 1000:1 压缩为致密蒸汽（续航倒计时由 onPostTick 实时维护） */
    private CheckRecipeResult processDenseCycle() {
        // 保险：若倒计时已在 onPostTick 耗尽且来不及续杯，这里兜底消化 1 颗
        if (mDenseTicks <= 0) {
            if (!consumeSingularityFromInputBuses(1)) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
            mDenseTicks = SINGULARITY_DURATION_TICKS;
        }

        int grade = findHighestGrade(false);
        if (grade < 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        long x = sumGrade(grade, false);
        if (x < DENSE_COMPRESSION_RATIO) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        FluidStack dense = FluidRegistry.getFluidStack(DENSE_FLUID_NAMES[grade], (int) (x / DENSE_COMPRESSION_RATIO));
        if (dense == null || dense.amount <= 0 || !canFitOutput(dense)) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        drainGrade(grade, false);
        fillOutput(dense);
        startCycle();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private void startCycle() {
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mOutputItems = emptyItemStackArray;
        mMaxProgresstime = CYCLE_LENGTH;
    }

    /** 合并普通输入仓与耐压蒸汽仓的当前蒸汽来源列表 */
    private List<MTEHatch> getSteamInputHatches() {
        List<MTEHatch> all = new ArrayList<>(mInputHatches.size() + mPressureSteamInputs.size());
        all.addAll(mInputHatches);
        all.addAll(mPressureSteamInputs);
        return all;
    }

    /** 查找输入仓中当前最高等级的蒸汽类别；无则返回 -1 */
    private int findHighestGrade(boolean includeDense) {
        int grade = -1;
        for (MTEHatch hatch : getSteamInputHatches()) {
            FluidStack fs = hatch.getFluid();
            if (fs == null || fs.amount <= 0 || fs.getFluid() == null) continue;
            int g = getFluidGrade(
                fs.getFluid()
                    .getName(),
                includeDense);
            if (g > grade) grade = g;
        }
        return grade;
    }

    private long sumGrade(int grade, boolean includeDense) {
        long x = 0;
        for (MTEHatch hatch : getSteamInputHatches()) {
            FluidStack fs = hatch.getFluid();
            if (fs == null || fs.getFluid() == null) continue;
            if (getFluidGrade(
                fs.getFluid()
                    .getName(),
                includeDense) == grade) {
                x += fs.amount;
            }
        }
        return x;
    }

    private void drainGrade(int grade, boolean includeDense) {
        for (MTEHatch hatch : getSteamInputHatches()) {
            FluidStack fs = hatch.getFluid();
            if (fs == null || fs.getFluid() == null) continue;
            if (getFluidGrade(
                fs.getFluid()
                    .getName(),
                includeDense) == grade) {
                hatch.drain(Integer.MAX_VALUE, true);
            }
        }
    }

    private int getFluidGrade(String name, boolean includeDense) {
        switch (name) {
            case "steam":
                return 0;
            case "densesteam":
                return includeDense ? 0 : -1;
            case "ic2superheatedsteam":
                return 1;
            case "densesuperheatedsteam":
                return includeDense ? 1 : -1;
            case "supercriticalsteam":
                return 2;
            case "densesupercriticalsteam":
                return includeDense ? 2 : -1;
            default:
                return -1;
        }
    }

    private boolean canFitOutput(FluidStack stack) {
        int capacity = 0;
        for (MTEHatchOutput hatch : mOutputHatches) {
            FluidStack existing = hatch.getFluid();
            int used = existing != null ? existing.amount : 0;
            capacity += hatch.getCapacity() - used;
            if (capacity >= stack.amount) return true;
        }
        return false;
    }

    private void fillOutput(FluidStack stack) {
        int remaining = stack.amount;
        for (MTEHatchOutput hatch : mOutputHatches) {
            if (remaining <= 0) break;
            FluidStack toFill = stack.copy();
            toFill.amount = remaining;
            int filled = hatch.fill(toFill, true);
            remaining -= filled;
        }
    }

    private boolean consumeSingularityFromInputBuses(int amount) {
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
        return false;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (aPlayer.worldObj.isRemote) return;
        if (mMachine) {
            GTUtility.sendChatTrans(aPlayer, "gtsr.chat.dense_mode.require_off");
            return;
        }
        if (mHeat > 0.0001d) {
            GTUtility.sendChatTrans(aPlayer, "gtsr.chat.dense_mode.require_heat_clear");
            return;
        }
        if (mTier < 2) {
            GTUtility.sendChatTrans(aPlayer, "gtsr.chat.dense_mode.require_tier2");
            return;
        }
        mDenseMode = !mDenseMode;
        GTUtility.sendChatTrans(aPlayer, mDenseMode ? "gtsr.chat.dense_mode.on" : "gtsr.chat.dense_mode.off");
        getBaseMetaTileEntity().markDirty();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (aTick % CYCLE_LENGTH != 0L) return;

        if (!mMachine || !aBaseMetaTileEntity.isAllowedToWork()) {
            // 机器关机（结构失效或软锤/红石关闭）：每秒降低 1% 热量
            mHeat = Math.max(0.0d, mHeat - HEAT_DECAY_PER_SECOND);
            return;
        }
        if (mDenseMode) {
            // 致密模式：续航倒计时实时推进（600s），归零瞬间自动消化 1 颗续杯
            mDenseTicks -= CYCLE_LENGTH;
            if (mDenseTicks <= 0) {
                if (consumeSingularityFromInputBuses(1)) {
                    mDenseTicks = SINGULARITY_DURATION_TICKS;
                } else {
                    mDenseTicks = 0;
                }
            }
            return;
        }
        // 蓄热模式：开机但空闲（无进行中周期）且当前无蒸汽：每秒降低 1% 热量
        if (mMaxProgresstime <= 0 && findHighestGrade(mTier >= 2) < 0) {
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
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new com.miaokatze.gtsr.common.gui.MTESteamSingularityCompressorGui(this);
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
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.desc"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.desc2"))
            .addInfo(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.desc3"))
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.desc4"))
            .addInfo(
                EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.desc5"))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.desc6"))
            .addSeparator()
            .beginStructureBlock(11, 11, 11, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.steam_input"),
                1)
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.output_bus"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.output_hatch"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.tier1_blocks"))
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.tier2_blocks"))
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
        aNBT.setDouble("mHeat", mHeat);
        aNBT.setBoolean("mDenseMode", mDenseMode);
        aNBT.setInteger("mDenseTicks", mDenseTicks);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mHeat = aNBT.getDouble("mHeat");
        mDenseMode = aNBT.getBoolean("mDenseMode");
        mDenseTicks = aNBT.getInteger("mDenseTicks");
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.singularity_compressor.type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.heat")
                + " "
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mHeat * 100.0d)
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.tier")
                + " "
                + EnumChatFormatting.GOLD
                + mTier
                + EnumChatFormatting.RESET);
        String modeKey = mDenseMode ? "gtsr.gui.singularity_compressor.mode.dense"
            : "gtsr.gui.singularity_compressor.mode.accumulate";
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.mode")
                + " "
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal(modeKey)
                + EnumChatFormatting.RESET);
        if (mDenseMode) {
            info.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.singularity_compressor.dense_time")
                    + " "
                    + EnumChatFormatting.RED
                    + String.format("%ds", mDenseTicks / CYCLE_LENGTH)
                    + EnumChatFormatting.RESET);
        }
        return info.toArray(new String[0]);
    }
}
