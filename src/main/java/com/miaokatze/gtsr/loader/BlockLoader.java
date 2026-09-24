package com.miaokatze.gtsr.loader;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.blocks.BlockAbyssalFluid;
import com.miaokatze.gtsr.common.blocks.BlockProsperityFalling;
import com.miaokatze.gtsr.common.blocks.BlockProsperityStone;
import com.miaokatze.gtsr.common.blocks.BlockRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.blocks.ItemBlockRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityCanopyLeaves;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityNaturalBase;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityNaturalTop;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRoostGlow;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLeaves;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityRustLog;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperitySurface;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperityTuft;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing;
import com.miaokatze.gtsr.common.dimension.prosperity.block.ItemBlockProsperityMeta;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredAshLog;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredCorestone;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredCrossDecor;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredMonolith;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredSurfaceBase;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredSurfaceTop;
import com.miaokatze.gtsr.main.GTSteamReborn;
import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;

public class BlockLoader {

    public static void initBlocks() {
        // 方块实例统一写入 blocks 包自持持有者（SR-O2-B05），machine/blocks 侧只读，不再反向引用 loader
        BlocksGTSR.runawaySingularity = new BlockRunawaySingularity();
        GameRegistry
            .registerBlock(BlocksGTSR.runawaySingularity, ItemBlockRunawaySingularity.class, "RunawaySingularity");
        GameRegistry.registerTileEntity(TileRunawaySingularity.class, "gtsr.runawaySingularity");
        CreativeTabManager.addItemToTabFirst(new ItemStack(BlocksGTSR.runawaySingularity));
        // 节点三新变体（1=自然/2=稳定/3=失控；0=OLD 保持首位，见 ItemBlockRunawaySingularity）
        CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.runawaySingularity, 1, 1));
        CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.runawaySingularity, 1, 2));
        CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.runawaySingularity, 1, 3));

        // dim1 S2：繁荣维度 meta 族方块（runawaySingularity 自持持有者范式）。
        // 必须先于 ProsperityBiomes.init（群系 top/filler 引用 BlocksGTSR.prosperitySurface，
        // CommonProxy.preInit 顺序：BlockLoader → ProsperityBiomes → DimensionRegistrar）。
        BlocksGTSR.prosperitySurface = new BlockProsperitySurface();
        GameRegistry.registerBlock(BlocksGTSR.prosperitySurface, ItemBlockProsperityMeta.class, "ProsperitySurface");
        BlocksGTSR.ruinDebris = new BlockRuinDebris();
        GameRegistry.registerBlock(BlocksGTSR.ruinDebris, ItemBlockProsperityMeta.class, "RuinDebris");
        BlocksGTSR.ruinedCasing = new BlockRuinedCasing();
        GameRegistry.registerBlock(BlocksGTSR.ruinedCasing, ItemBlockProsperityMeta.class, "RuinedCasing");
        for (int meta = 0; meta < BlockProsperitySurface.META_COUNT; meta++) {
            CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.prosperitySurface, 1, meta));
        }
        for (int meta = 0; meta < BlockRuinDebris.META_COUNT; meta++) {
            CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.ruinDebris, 1, meta));
        }
        for (int meta = 0; meta < BlockRuinedCasing.META_COUNT; meta++) {
            CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.ruinedCasing, 1, meta));
        }
        GTSteamReborn.LOG
            .info("[GTSR] prosperity blocks registered: ProsperitySurface(6)/RuinDebris(4)/RuinedCasing(3)");

        // dim78 S-A1：四群系自然区方块族（全部独立 Block ID，plan §12 修订 2；meta 恒 0）。
        // 必须先于 ProsperityBiomes.init（群系 top/filler 引用 BlocksGTSR.*自然族，
        // CommonProxy.preInit 顺序：BlockLoader → ProsperityBiomes → DimensionRegistrar）。
        BlocksGTSR.prosperitySteppeTop = new BlockProsperityNaturalTop(
            "ProsperitySteppeTop",
            "gtsr:prosperity_steppe_top",
            "gtsr:prosperity_steppe_top_side",
            "gtsr:prosperity_steppe_base");
        GameRegistry.registerBlock(BlocksGTSR.prosperitySteppeTop, "ProsperitySteppeTop");
        BlocksGTSR.prosperitySteppeBase = new BlockProsperityNaturalBase(
            "ProsperitySteppeBase",
            "gtsr:prosperity_steppe_base");
        GameRegistry.registerBlock(BlocksGTSR.prosperitySteppeBase, "ProsperitySteppeBase");
        BlocksGTSR.prosperityForestTop = new BlockProsperityNaturalTop(
            "ProsperityForestTop",
            "gtsr:prosperity_forest_top",
            "gtsr:prosperity_forest_top_side",
            "gtsr:prosperity_forest_base");
        GameRegistry.registerBlock(BlocksGTSR.prosperityForestTop, "ProsperityForestTop");
        BlocksGTSR.prosperityForestBase = new BlockProsperityNaturalBase(
            "ProsperityForestBase",
            "gtsr:prosperity_forest_base");
        GameRegistry.registerBlock(BlocksGTSR.prosperityForestBase, "ProsperityForestBase");
        BlocksGTSR.prosperityWastesTop = new BlockProsperityNaturalTop(
            "ProsperityWastesTop",
            "gtsr:prosperity_wastes_top",
            "gtsr:prosperity_wastes_top_side",
            "gtsr:prosperity_wastes_base");
        GameRegistry.registerBlock(BlocksGTSR.prosperityWastesTop, "ProsperityWastesTop");
        BlocksGTSR.prosperityWastesBase = new BlockProsperityNaturalBase(
            "ProsperityWastesBase",
            "gtsr:prosperity_wastes_base");
        GameRegistry.registerBlock(BlocksGTSR.prosperityWastesBase, "ProsperityWastesBase");
        BlocksGTSR.prosperitySwampTop = new BlockProsperityNaturalTop(
            "ProsperitySwampTop",
            "gtsr:prosperity_swamp_top",
            "gtsr:prosperity_swamp_top_side",
            "gtsr:prosperity_swamp_base");
        GameRegistry.registerBlock(BlocksGTSR.prosperitySwampTop, "ProsperitySwampTop");
        BlocksGTSR.prosperitySwampBase = new BlockProsperityNaturalBase(
            "ProsperitySwampBase",
            "gtsr:prosperity_swamp_base");
        GameRegistry.registerBlock(BlocksGTSR.prosperitySwampBase, "ProsperitySwampBase");
        BlocksGTSR.prosperityTuftRust = new BlockProsperityTuft("ProsperityTuftRust", "gtsr:prosperity_tuft_rust");
        GameRegistry.registerBlock(BlocksGTSR.prosperityTuftRust, "ProsperityTuftRust");
        BlocksGTSR.prosperityTuftCopper = new BlockProsperityTuft(
            "ProsperityTuftCopper",
            "gtsr:prosperity_tuft_copper");
        GameRegistry.registerBlock(BlocksGTSR.prosperityTuftCopper, "ProsperityTuftCopper");
        BlocksGTSR.prosperityRustLog = new BlockProsperityRustLog(
            "ProsperityRustLog",
            "gtsr:prosperity_rust_log_side",
            "gtsr:prosperity_rust_log_top");
        GameRegistry.registerBlock(BlocksGTSR.prosperityRustLog, "ProsperityRustLog");
        BlocksGTSR.prosperityRustLeaves = new BlockProsperityRustLeaves(
            "ProsperityRustLeaves",
            "gtsr:prosperity_rust_leaves");
        GameRegistry.registerBlock(BlocksGTSR.prosperityRustLeaves, "ProsperityRustLeaves");
        // P17-SB1：木 3 档 + 花 4 + 草 2（plan §0-Q3/§2-3；全部非表层 top，不进 SurfaceGate 名册；
        // 世界生成消费方＝S-B2，本片零生成接线。新类仅 BlockProsperityCanopyLeaves 一支）。
        BlocksGTSR.prosperityCopperLog = new BlockProsperityRustLog(
            "ProsperityCopperLog",
            "gtsr:prosperity_copper_log_side",
            "gtsr:prosperity_copper_log_top");
        GameRegistry.registerBlock(BlocksGTSR.prosperityCopperLog, "ProsperityCopperLog");
        BlocksGTSR.prosperityCopperLeaves = new BlockProsperityCanopyLeaves(
            "ProsperityCopperLeaves",
            "gtsr:prosperity_copper_leaves_side",
            "gtsr:prosperity_copper_leaves_top");
        GameRegistry.registerBlock(BlocksGTSR.prosperityCopperLeaves, "ProsperityCopperLeaves");
        BlocksGTSR.prosperityBrassLog = new BlockProsperityRustLog(
            "ProsperityBrassLog",
            "gtsr:prosperity_brass_log_side",
            "gtsr:prosperity_brass_log_top");
        GameRegistry.registerBlock(BlocksGTSR.prosperityBrassLog, "ProsperityBrassLog");
        BlocksGTSR.prosperityBrassLeaves = new BlockProsperityCanopyLeaves(
            "ProsperityBrassLeaves",
            "gtsr:prosperity_brass_leaves_side",
            "gtsr:prosperity_brass_leaves_top");
        GameRegistry.registerBlock(BlocksGTSR.prosperityBrassLeaves, "ProsperityBrassLeaves");
        BlocksGTSR.prosperityMarshLog = new BlockProsperityRustLog(
            "ProsperityMarshLog",
            "gtsr:prosperity_marsh_log_side",
            "gtsr:prosperity_marsh_log_top");
        GameRegistry.registerBlock(BlocksGTSR.prosperityMarshLog, "ProsperityMarshLog");
        BlocksGTSR.prosperityMarshLeaves = new BlockProsperityCanopyLeaves(
            "ProsperityMarshLeaves",
            "gtsr:prosperity_marsh_leaves_side",
            "gtsr:prosperity_marsh_leaves_top");
        GameRegistry.registerBlock(BlocksGTSR.prosperityMarshLeaves, "ProsperityMarshLeaves");
        BlocksGTSR.prosperityFlowerRust = new BlockProsperityTuft(
            "ProsperityFlowerRust",
            "gtsr:prosperity_flower_rust");
        GameRegistry.registerBlock(BlocksGTSR.prosperityFlowerRust, "ProsperityFlowerRust");
        BlocksGTSR.prosperityFlowerPatina = new BlockProsperityTuft(
            "ProsperityFlowerPatina",
            "gtsr:prosperity_flower_patina");
        GameRegistry.registerBlock(BlocksGTSR.prosperityFlowerPatina, "ProsperityFlowerPatina");
        BlocksGTSR.prosperityFlowerBrass = new BlockProsperityTuft(
            "ProsperityFlowerBrass",
            "gtsr:prosperity_flower_brass");
        GameRegistry.registerBlock(BlocksGTSR.prosperityFlowerBrass, "ProsperityFlowerBrass");
        BlocksGTSR.prosperityFlowerMarsh = new BlockProsperityTuft(
            "ProsperityFlowerMarsh",
            "gtsr:prosperity_flower_marsh");
        GameRegistry.registerBlock(BlocksGTSR.prosperityFlowerMarsh, "ProsperityFlowerMarsh");
        BlocksGTSR.prosperityTuftSedge = new BlockProsperityTuft("ProsperityTuftSedge", "gtsr:prosperity_tuft_sedge");
        GameRegistry.registerBlock(BlocksGTSR.prosperityTuftSedge, "ProsperityTuftSedge");
        BlocksGTSR.prosperityTuftBristle = new BlockProsperityTuft(
            "ProsperityTuftBristle",
            "gtsr:prosperity_tuft_bristle");
        GameRegistry.registerBlock(BlocksGTSR.prosperityTuftBristle, "ProsperityTuftBristle");
        // P17-S-B2：沙/砂砾 3 件（B 档 BlockProsperityNaturalBase 吃贴图名 ⇒ 零新 Java 类；
        // 全部非表层 top，不进 SurfaceGate 名册。消费方 = ProsperityDecorPlacer 荒漠沙砾趟，
        // prosperityRiverGravel 同时是 S-C 河床料）。
        // v1.20.39 T2（G8 重力沙砾，plan §3.9）：coarseSand/riverGravel 两件切 BlockProsperityFalling
        // （注册名/unlocalizedName 不变，世界数据兼容）；silicaSand 保持非重力（已拍板）。
        BlocksGTSR.prosperitySilicaSand = new BlockProsperityNaturalBase(
            "ProsperitySilicaSand",
            "gtsr:prosperity_silica_sand");
        GameRegistry.registerBlock(BlocksGTSR.prosperitySilicaSand, "ProsperitySilicaSand");
        BlocksGTSR.prosperityCoarseSand = new BlockProsperityFalling(
            "ProsperityCoarseSand",
            "gtsr:prosperity_coarse_sand");
        GameRegistry.registerBlock(BlocksGTSR.prosperityCoarseSand, "ProsperityCoarseSand");
        BlocksGTSR.prosperityRiverGravel = new BlockProsperityFalling(
            "ProsperityRiverGravel",
            "gtsr:prosperity_river_gravel");
        GameRegistry.registerBlock(BlocksGTSR.prosperityRiverGravel, "ProsperityRiverGravel");
        // v1.20.39 T2（G4 地底石化，plan §3.7）：地底 wholeBody 主体石（Material.rock，单一变体），
        // 消费方 = ChunkProviderProsperityRuins.baseBlockOf；非表层 top，不进 SurfaceGate 名册。
        BlocksGTSR.prosperityStone = new BlockProsperityStone("ProsperityStone", "gtsr:prosperity_stone");
        GameRegistry.registerBlock(BlocksGTSR.prosperityStone, "ProsperityStone");
        // v1.20.43 P22-B S2（p21 §4）：岛心巨树三件套——垂天玄柯（rust 骨架换谱复用 BlockProsperityRustLog）、
        // 漱玉清羽（冠叶复用 BlockProsperityCanopyLeaves，tint 随锈叶父类）、旧栖晴晕（新类
        // BlockProsperityRoostGlow：circuits 料 + setLightLevel(0.9375F)=光 14 + 硬度 0.0F + 无碰撞 +
        // 十字渲染，零 tint——金色画进像素）。meta 恒 0、ItemBlock 三全零；全部非表层 top，
        // 不进 SurfaceGate 名册（DIM78_SIZE 恒 5）；世界生成消费方 = S3 树趟 / S4 光趟，本片零接线。
        BlocksGTSR.prosperityZenithLog = new BlockProsperityRustLog(
            "ProsperityZenithLog",
            "gtsr:prosperity_zenith_log_side",
            "gtsr:prosperity_zenith_log_top");
        GameRegistry.registerBlock(BlocksGTSR.prosperityZenithLog, "ProsperityZenithLog");
        BlocksGTSR.prosperityJadeLeaves = new BlockProsperityCanopyLeaves(
            "ProsperityJadeLeaves",
            "gtsr:prosperity_jade_leaves_side",
            "gtsr:prosperity_jade_leaves_top");
        GameRegistry.registerBlock(BlocksGTSR.prosperityJadeLeaves, "ProsperityJadeLeaves");
        BlocksGTSR.prosperityRoostGlow = new BlockProsperityRoostGlow(
            "ProsperityRoostGlow",
            "gtsr:prosperity_roost_glow");
        GameRegistry.registerBlock(BlocksGTSR.prosperityRoostGlow, "ProsperityRoostGlow");
        final Block[] prosperityNaturalBlocks = { BlocksGTSR.prosperitySteppeTop, BlocksGTSR.prosperitySteppeBase,
            BlocksGTSR.prosperityForestTop, BlocksGTSR.prosperityForestBase, BlocksGTSR.prosperityWastesTop,
            BlocksGTSR.prosperityWastesBase, BlocksGTSR.prosperitySwampTop, BlocksGTSR.prosperitySwampBase,
            BlocksGTSR.prosperityTuftRust, BlocksGTSR.prosperityTuftCopper, BlocksGTSR.prosperityRustLog,
            BlocksGTSR.prosperityRustLeaves, BlocksGTSR.prosperityCopperLog, BlocksGTSR.prosperityCopperLeaves,
            BlocksGTSR.prosperityBrassLog, BlocksGTSR.prosperityBrassLeaves, BlocksGTSR.prosperityMarshLog,
            BlocksGTSR.prosperityMarshLeaves, BlocksGTSR.prosperityFlowerRust, BlocksGTSR.prosperityFlowerPatina,
            BlocksGTSR.prosperityFlowerBrass, BlocksGTSR.prosperityFlowerMarsh, BlocksGTSR.prosperityTuftSedge,
            BlocksGTSR.prosperityTuftBristle, BlocksGTSR.prosperitySilicaSand, BlocksGTSR.prosperityCoarseSand,
            BlocksGTSR.prosperityRiverGravel, BlocksGTSR.prosperityStone, BlocksGTSR.prosperityZenithLog,
            BlocksGTSR.prosperityJadeLeaves, BlocksGTSR.prosperityRoostGlow };
        for (final Block natural : prosperityNaturalBlocks) {
            CreativeTabManager.addItemToTab(new ItemStack(natural));
        }
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity natural blocks registered: terrain=8 (4x top+base) decor=4 (2x tuft+log+leaves)"
                + " + p17-sb1 wood=6 (3x log+canopy) flora=6 (4x flower+2x tuft)"
                + " + p17-sb2 sand=3 (2x sand + river gravel, all non-top; coarse+gravel now falling)"
                + " + v1.20.39 stone=1 (prosperityStone underground wholeBody)"
                + " + v1.20.43 p22-island-tree trio=3 (zenith log + jade canopy + roost glow light14, all non-top)");

        // dim79 重做（S-B）：破碎之地高硬度方块族（全部独立 Block ID，plan §12 修订 2/3：
        // hardness ≥1000F、blast 1200、harvest pickaxe 3；分层值登记 dim79-redo-slice-B-report.md，
        // 全部可调常量。旧 shatteredGrass/ShatteredDirt/RiftStone/ShatteredBlackstone 删除，
        // 注册名不保留——无 retrogen，存量 chunk≈0 已裁决）。
        // 必须先于 ShatteredBiomes.init（群系 top/filler 引用 BlocksGTSR.shattered*，
        // CommonProxy.preInit 顺序：BlockLoader → ShatteredBiomes.init → DimensionRegistrar）。
        BlocksGTSR.shatteredCorestone = new BlockShatteredCorestone();
        GameRegistry.registerBlock(BlocksGTSR.shatteredCorestone, "ShatteredCorestone");
        BlocksGTSR.shatteredMonolith = new BlockShatteredMonolith();
        GameRegistry.registerBlock(BlocksGTSR.shatteredMonolith, "ShatteredMonolith");
        BlocksGTSR.shatteredAshTop = new BlockShatteredSurfaceTop(
            "ShatteredAshTop",
            "gtsr:shattered_ash_top",
            "gtsr:shattered_ash_top_side",
            "gtsr:shattered_ash_base");
        GameRegistry.registerBlock(BlocksGTSR.shatteredAshTop, "ShatteredAshTop");
        BlocksGTSR.shatteredAshBase = new BlockShatteredSurfaceBase("ShatteredAshBase", "gtsr:shattered_ash_base");
        GameRegistry.registerBlock(BlocksGTSR.shatteredAshBase, "ShatteredAshBase");
        BlocksGTSR.shatteredSlagTop = new BlockShatteredSurfaceTop(
            "ShatteredSlagTop",
            "gtsr:shattered_slag_top",
            "gtsr:shattered_slag_top_side",
            "gtsr:shattered_slag_base");
        GameRegistry.registerBlock(BlocksGTSR.shatteredSlagTop, "ShatteredSlagTop");
        BlocksGTSR.shatteredSlagBase = new BlockShatteredSurfaceBase("ShatteredSlagBase", "gtsr:shattered_slag_base");
        GameRegistry.registerBlock(BlocksGTSR.shatteredSlagBase, "ShatteredSlagBase");
        BlocksGTSR.shatteredGlassTop = new BlockShatteredSurfaceTop(
            "ShatteredGlassTop",
            "gtsr:shattered_glass_top",
            "gtsr:shattered_glass_top_side",
            "gtsr:shattered_glass_base");
        GameRegistry.registerBlock(BlocksGTSR.shatteredGlassTop, "ShatteredGlassTop");
        BlocksGTSR.shatteredGlassBase = new BlockShatteredSurfaceBase(
            "ShatteredGlassBase",
            "gtsr:shattered_glass_base");
        GameRegistry.registerBlock(BlocksGTSR.shatteredGlassBase, "ShatteredGlassBase");
        BlocksGTSR.shatteredTarTop = new BlockShatteredSurfaceTop(
            "ShatteredTarTop",
            "gtsr:shattered_tar_top",
            "gtsr:shattered_tar_top_side",
            "gtsr:shattered_tar_base");
        GameRegistry.registerBlock(BlocksGTSR.shatteredTarTop, "ShatteredTarTop");
        BlocksGTSR.shatteredTarBase = new BlockShatteredSurfaceBase("ShatteredTarBase", "gtsr:shattered_tar_base");
        GameRegistry.registerBlock(BlocksGTSR.shatteredTarBase, "ShatteredTarBase");
        BlocksGTSR.shatteredSpikeCluster = new BlockShatteredCrossDecor(
            "ShatteredSpikeCluster",
            "gtsr:shattered_spike_cluster");
        GameRegistry.registerBlock(BlocksGTSR.shatteredSpikeCluster, "ShatteredSpikeCluster");
        BlocksGTSR.shatteredAshLog = new BlockShatteredAshLog(
            "ShatteredAshLog",
            "gtsr:shattered_ash_log_side",
            "gtsr:shattered_ash_log_top");
        GameRegistry.registerBlock(BlocksGTSR.shatteredAshLog, "ShatteredAshLog");
        BlocksGTSR.shatteredAshThorn = new BlockShatteredCrossDecor("ShatteredAshThorn", "gtsr:shattered_ash_thorn");
        GameRegistry.registerBlock(BlocksGTSR.shatteredAshThorn, "ShatteredAshThorn");
        final Block[] shatteredBlocks = { BlocksGTSR.shatteredCorestone, BlocksGTSR.shatteredMonolith,
            BlocksGTSR.shatteredAshTop, BlocksGTSR.shatteredAshBase, BlocksGTSR.shatteredSlagTop,
            BlocksGTSR.shatteredSlagBase, BlocksGTSR.shatteredGlassTop, BlocksGTSR.shatteredGlassBase,
            BlocksGTSR.shatteredTarTop, BlocksGTSR.shatteredTarBase, BlocksGTSR.shatteredSpikeCluster,
            BlocksGTSR.shatteredAshLog, BlocksGTSR.shatteredAshThorn };
        for (final Block shattered : shatteredBlocks) {
            CreativeTabManager.addItemToTab(new ItemStack(shattered));
        }
        GTSteamReborn.LOG
            .info("[GTSR] shattered blocks registered: Corestone/Monolith terrain=8 (4x top+base) decor=3");
    }

    /**
     * 深渊执念流体方块注册（v1.20.40 P19-U1，plan §I）——<b>init 段调用</b>
     * （CommonProxy.init，晚于 GT preInit），不在 {@link #initBlocks()}：
     * <ul>
     * <li><b>时序依据</b>：材料流体 abyssal_obsession 由 GT5U 于自身 preInit 末尾的
     * Materials.init() 回调链（onMaterialsInit → initMaterialProperties → mFluid →
     * FluidRegistry，见 GTSRProsperityAirMaterials 类注释）注册；gtsr 为 required-before:gregtech，
     * 自身 preInit 早于 GT preInit，initBlocks 时 {@code FluidRegistry.getFluid} 必为 null。
     * CommonProxy.init 已有同款时序先例：空气材料 dev 探针即因"init 晚于 Materials.init()/流体
     * 注册管线"才放在 init（CommonProxy.java:227-229 注释）。</li>
     * <li><b>fluid.setBlock 无需手动挂接</b>：BlockFluidBase 构造函数自即调
     * {@code fluid.setBlock(this)}（BlockFluidBase.java:60-76）；GT 材料管线只造 Fluid 不造世界
     * 方块，本构造是该流体唯一 block 绑定点（Fluid.setBlock 二次绑定告警，Fluid.java:117-127）。</li>
     * <li><b>失败开放</b>：流体未注册（材料链异常被 GTSRProsperityAirMaterials 降级吞掉）时跳过
     * 并告警，零崩溃面；worldgen 换块消费方（批2 U34）读 null 持有者自行降级。</li>
     * </ul>
     * 流体方块不进创造页签（GT 流体方块惯例；取用面=桶装+NEI）。
     */
    public static void initAbyssalFluid() {
        final net.minecraftforge.fluids.Fluid fluid = net.minecraftforge.fluids.FluidRegistry
            .getFluid("abyssal_obsession");
        if (fluid == null) {
            GTSteamReborn.LOG
                .warn("[GTSR] abyssal fluid material missing from FluidRegistry, fluid block registration skipped");
            return;
        }
        BlocksGTSR.abyssalFluid = new BlockAbyssalFluid(fluid);
        GameRegistry.registerBlock(BlocksGTSR.abyssalFluid, "AbyssalFluid");
        GTSteamReborn.LOG.info("[GTSR] abyssal fluid block registered: AbyssalFluid fluid=abyssal_obsession");
    }
}
