package com.miaokatze.gtsr.common.dimension.prosperity.ruins.city;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;

import gregtech.api.GregTechAPI;

/**
 * 城市方块键解析 Sink（dim1 S4b，游戏侧；RuinedMachinePlacer.resolveBlock 同范式泛化为
 * Sink 形态）。{@code city/} 纯几何层以 String 键参与（零 Minecraft 依赖，离线驱动可复用），
 * 世界写入前经本类解析为 Block 实例——ChunkClampedSink/世界只接受 Block 实例。
 * <p>
 * 键表 = plan §3.3 材料红线全集：BlockRuinedCasing(0/1/2) / BlockRuinDebris(0-3) /
 * BlockProsperitySurface(0-5) + 原版 stone/cobblestone/gravel/iron_bars + air（内腔清空）
 * + GT5U casing 白名单两键（S-A4，plan §4 S-A4 / §12 修订 7：{@code K_GT_BRONZE}=
 * GregTechAPI.sBlockCasings1（meta10 镀铜砖块由 char 层传参）、{@code K_GT_STEEL}=
 * GregTechAPI.sBlockCasings2（meta0 同前）；直引先例 CasingTierTextureHelper.java:15，
 * <b>GT 未加载/字段 null 时不 put</b>——resolve 返回 null 由调用方跳过，既有防御链）
 * + <b>P16-B1 为城外跨 chunk 巨构追加的两键</b>（{@link #K_GT_FIREBOX} = sBlockCasings3、
 * {@link #K_GT_GLASS} = sBlockGlass1；两键各自允许的 meta 与禁用 meta 见其字段注释，
 * 红线由 {@code tools/dim1/RuinFamilyCheck} 的 COLOSSUS 组按 (键,meta) 逐格钉死）。
 * <b>本表结构上不含</b> {@code gt.blockmachines}（GTSR 全部 MTE 的寄居块，
 * {@code BlockMachines.hasTileEntity} 恒 true）、casings4/casings5、frames、metal 块 ⇒
 * 任何模板写到那些键都解析为 null 而被跳过，这是"落块集合无非 TE 类方块"的第一道闸
 * （第二道是上面那条 (键,meta) 断言，两道都不依赖结构表里少个字符）。
 * 未知键丢弃返回 false（协议误用防御，与 ChunkClampedSink 同口径）。
 * 延迟初始化：离线驱动不触碰本类（含 net.minecraft 引用）。
 * <p>
 * 离线断言侧的 {@code gregtech.api.GregTechAPI} 占位类（{@code tools/dim1/gregtech/api/}）
 * 必须与本表同步字段：本类一旦读新字段而占位类没有，离线链会在 {@code NoSuchFieldError} 上红，
 * 不会静默假绿（P16-B1 已把 {@code sBlockCasings3}/{@code sBlockGlass1} 一并补进占位类）。
 */
public final class CityBlockResolver implements BlockSink {

    /**
     * GT5U 燃烧室外壳（逻辑键，非注册名；游戏侧 = {@code GregTechAPI.sBlockCasings3}）。
     * <p>
     * <b>P16-B1（plan §1 G6 / 任务包 item 3）</b>：本键<b>只</b>服务城外跨 chunk 巨构的
     * {@code 'f'}(meta13 青铜) / {@code 'F'}(meta14 钢) 两档；
     * <b>meta15（钨钢 Firebox）在禁用清单上</b>（取证件 B6 高阶壳红线），由
     * {@code tools/dim1/RuinFamilyCheck} 的 COLOSSUS 组按 (键, meta) 逐格钉。
     * 城内 28 变体（26 基础 + P16-B3 巨构 2，其记号表零新键）/ 6 outpost / 8 废墟一条都没引这个键，
     * 故它们的落块集合不变。
     */
    public static final String K_GT_FIREBOX = "gt5u:CasingFirebox";

    /**
     * GT5U 防爆玻璃（逻辑键，非注册名；游戏侧 = {@code GregTechAPI.sBlockGlass1} meta10，
     * 注册证据 {@code BlockGlass1.java:43}）。
     * <p>
     * <b>为什么直引字段而不是走 {@code GTVersionCompat.getReinforcedGlassBlock()}</b>：那个访问器的
     * beta-1 分支要 {@code GameRegistry.findBlock("IC2", ...)} 且其静态初始化读 FML
     * {@code Loader.instance()}——本仓的离线断言链（{@code RuinFamilyCheck} 的 C 组就是这个形状）
     * 要在无 Forge 装配的 JVM 里 {@code new CityBlockResolver(...)}，拖进那条静态链会直接
     * {@code ExceptionInInitializerError}。本项目按 {@code AGENTS.md} 绑定 beta-3 映射 ⇒
     * {@code sBlockGlass1} 就是当前环境的正解；万一跑在 beta-1 包上，本字段为 null，
     * 走下面既有的"不 put ⇒ resolve 落空 ⇒ 调用方跳过该部件"防御链（巨构少几格玻璃，不炸生成链）。
     * 这一条登记在本片回执的"未闭合边界"里。
     */
    public static final String K_GT_GLASS = "gt5u:ReinforcedGlass";

    /** 方块键 → Block 解析表（游戏内放置路径专用；延迟初始化避免离线驱动触碰）。 */
    private static Map<String, Block> blockResolver;

    private final BlockSink parent;

    public CityBlockResolver(BlockSink parent) {
        this.parent = parent;
    }

    @Override
    public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
        if (block instanceof Block) {
            return this.parent.setBlock(x, y, z, block, meta, flags);
        }
        final Block resolved = resolve(String.valueOf(block));
        if (resolved == null) {
            return false;
        }
        return this.parent.setBlock(x, y, z, resolved, meta, flags);
    }

    /** 键解析（未注册/未加载防御：返回 null 由调用方跳过，不炸生成链）。 */
    static Block resolve(String key) {
        if (blockResolver == null) {
            final Map<String, Block> resolver = new HashMap<>();
            resolver.put(CityVariants.K_CASING, BlocksGTSR.ruinedCasing);
            resolver.put(CityVariants.K_DEBRIS, BlocksGTSR.ruinDebris);
            resolver.put(CityVariants.K_SURFACE, BlocksGTSR.prosperitySurface);
            resolver.put(CityVariants.K_STONE, Blocks.stone);
            resolver.put(CityVariants.K_COBBLE, Blocks.cobblestone);
            resolver.put(CityVariants.K_GRAVEL, Blocks.gravel);
            resolver.put(CityVariants.K_BARS, Blocks.iron_bars);
            resolver.put(CityVariants.K_AIR, Blocks.air);
            // GT5U casing 白名单两键（S-A4）：GT 缺席时字段为 null，不 put（resolve 落空 → 调用方跳过）
            if (GregTechAPI.sBlockCasings1 != null) {
                resolver.put(CityVariants.K_GT_BRONZE, GregTechAPI.sBlockCasings1);
            }
            if (GregTechAPI.sBlockCasings2 != null) {
                resolver.put(CityVariants.K_GT_STEEL, GregTechAPI.sBlockCasings2);
            }
            // P16-B1：跨 chunk 巨构残骸的 GT 壳两键（同一"GT 缺席 ⇒ 不 put ⇒ 调用方跳过"防御链）
            if (GregTechAPI.sBlockCasings3 != null) {
                resolver.put(K_GT_FIREBOX, GregTechAPI.sBlockCasings3);
            }
            if (GregTechAPI.sBlockGlass1 != null) {
                resolver.put(K_GT_GLASS, GregTechAPI.sBlockGlass1);
            }
            blockResolver = resolver;
        }
        return blockResolver.get(key);
    }
}
