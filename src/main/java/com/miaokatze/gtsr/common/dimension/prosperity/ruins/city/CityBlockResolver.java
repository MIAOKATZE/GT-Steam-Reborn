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
 * <b>GT 未加载/字段 null 时不 put</b>——resolve 返回 null 由调用方跳过，既有防御链）。
 * 未知键丢弃返回 false（协议误用防御，与 ChunkClampedSink 同口径）。
 * 延迟初始化：离线驱动不触碰本类（含 net.minecraft 引用）。
 */
public final class CityBlockResolver implements BlockSink {

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
            blockResolver = resolver;
        }
        return blockResolver.get(key);
    }
}
