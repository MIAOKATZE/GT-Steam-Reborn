package com.miaokatze.gtsr.mixin;

import java.util.List;
import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.common.dimension.framework.GTSROwnedGenerator;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldProviderBase;

import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * R1 维度干涉收口（防线 1）：GTSR 维度上过滤 {@code GameRegistry.generateWorld} 的第三方
 * {@code IWorldGenerator}（plan R1 四层防线第 1 层）。
 * <p>
 * 目标：{@code GameRegistry.generateWorld(int, int, World, IChunkProvider, IChunkProvider)}
 * （build/rfg/minecraft-src GameRegistry.java:97-114）。已核实事实：该方法由
 * {@code ChunkProviderServer.populate}（ChunkProviderServer.java:314）对<b>一切维度</b>外层
 * 调用，GTSR provider 无法从内部阻止，第三方生成器（Forestry 蜂巢、TC4 节点/矿等）由此进入
 * dim78/79。
 * <p>
 * 行为：{@code world.provider instanceof GTSRWorldProviderBase} 时取消原方法并以<b>同一套
 * FML 种子协议</b>（worldSeed/xSeed/zSeed/chunkSeed 派生 + 每 generator 前
 * {@code fmlRandom.setSeed(chunkSeed)}，逐行复刻 GameRegistry.java:103-113）只对
 * {@link GTSROwnedGenerator} 实例重放生成调用，其余 generator 跳过；非 GTSR 维度在 HEAD
 * 直接返回，原方法逐字节放行（主世界/下界/Everglades 零漂移）。
 * <p>
 * 写法依据：GameRegistry 是 FML 类（非混淆目标），故 {@code remap = false} +
 * 方法名字面量（与本仓既有 MTEHatchAirIntakeMixin 等 remap=false 混入同一口径）；方法体对
 * 原版成员（{@code world.getSeed()}）的引用走普通字节码引用，reobf 阶段随全 jar 重映射，
 * 不依赖 refmap。选择 HEAD+cancel 而非 @Redirect：@Redirect 占用调用点、与他 mod 冲突即
 * 崩溃，HEAD 注入可与其他 mod 的注入共存。
 * <p>
 * fail-open：本 mixin 未应用（mixin 加载失败或 mixins.gtsr.json 摘除本条目）时，
 * {@code generateWorld} 保持 FML 原行为——第三方生成器重新进入 GTSR 维度（回落现状），
 * 不产生新崩溃面。
 */
@Mixin(value = GameRegistry.class, remap = false)
public abstract class GameRegistryMixin {

    /** FML 的排序后生成器表（GameRegistry.java:68；{@code null} = 尚未计算）。 */
    @Shadow(remap = false)
    private static List<IWorldGenerator> sortedGeneratorList;

    @Shadow(remap = false)
    private static void computeSortedGeneratorList() {}

    @Inject(method = "generateWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtsr$skipForeignWorldGenerators(int chunkX, int chunkZ, World world,
        IChunkProvider chunkGenerator, IChunkProvider chunkProvider, CallbackInfo ci) {
        if (world == null || !(world.provider instanceof GTSRWorldProviderBase)) {
            return; // 非 GTSR 维度：放行原方法（行为零漂移）
        }
        ci.cancel();
        if (sortedGeneratorList == null) {
            computeSortedGeneratorList();
        }
        // FML 种子协议复刻（GameRegistry.java:103-107）——自有生成器拿到的随机流与
        // 未过滤时逐位一致（确定性世界生成不受本防线影响）
        long worldSeed = world.getSeed();
        Random fmlRandom = new Random(worldSeed);
        long xSeed = fmlRandom.nextLong() >> 2 + 1L;
        long zSeed = fmlRandom.nextLong() >> 2 + 1L;
        long chunkSeed = (xSeed * chunkX + zSeed * chunkZ) ^ worldSeed;

        for (IWorldGenerator generator : sortedGeneratorList) {
            if (!(generator instanceof GTSROwnedGenerator)) {
                continue; // 第三方生成器：GTSR 维度上跳过（本防线的全部目的）
            }
            fmlRandom.setSeed(chunkSeed);
            generator.generate(fmlRandom, chunkX, chunkZ, world, chunkGenerator, chunkProvider);
        }
    }
}
