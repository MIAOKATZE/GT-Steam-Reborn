package com.miaokatze.gtsr.common.dimension.framework;

import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * R1 维度干涉收口的事件守卫（防线 2 + 防线 3，plan R1 四层防线第 2/3 层）。
 * <p>
 * 注册口径：照本仓现有事件注册模式（CommonProxy.init 显式调用 register() 静态入口 + 证据日志
 * 先例；1.7.10 Forge 无 {@code @Mod.EventBusSubscriber}）。
 * 两个订阅分挂两条 Forge bus（见各方法注释）。
 * <p>
 * <b>DENY 语义核实结论（build/rfg/minecraft-src 实测）</b>：
 * <ul>
 * <li>{@code ChunkProviderServer.populate}（:303-318）<b>从不</b>投递 PopulateChunkEvent.Pre/Post
 * ——它们只在原版 ChunkProviderEnd/Generate/Hell 的 populate 内投递，GTSR 维度不走这些路径，
 * 故「Pre 上 DENY」在本维没有语义；且 {@code PopulateChunkEvent.Pre} 无 {@code @HasResult}，
 * {@code setResult} 对它是无效写。</li>
 * <li>{@code DENY} 真正的语义载体是 {@link PopulateChunkEvent.Populate}（{@code @HasResult}，
 * 经 {@code TerrainGen.populate} 投递在 {@code MinecraftForge.TERRAIN_GEN_BUS}，
 * TerrainGen.java:33-38：{@code return event.getResult() != Result.DENY}）。BC 油井这类
 * 「TerrainGen 礼仪生成器」先订 Post/CUSTOM 再回调 {@code TerrainGen.populate}——对 Populate
 * 置 DENY 即拦截之。因此本守卫订阅的是 <b>Populate</b>（任务包 Pre+DENY 的等价实义化）。</li>
 * <li>DENY <b>不会</b>短路 {@code GameRegistry.generateWorld}（它在 ChunkProviderServer.populate
 * :314 与 provider.populate 平行调用，与事件无因果链）——generateWorld 的过滤由
 * {@code GameRegistryMixin}（防线 1）独立负责，两层互补。</li>
 * </ul>
 * fail-open：本守卫未注册（init 链异常跳过）时回落现状，不产生新崩溃面。
 */
public final class DimensionInterferenceGuard {

    private DimensionInterferenceGuard() {}

    /**
     * 注册入口（{@code CommonProxy.init} 一行调用）：TERRAIN_GEN_BUS + EVENT_BUS 双挂
     * （Populate 在 TERRAIN_GEN_BUS，PotentialSpawns 在 EVENT_BUS）+ 注册证据日志。
     */
    public static void register() {
        final DimensionInterferenceGuard guard = new DimensionInterferenceGuard();
        MinecraftForge.TERRAIN_GEN_BUS.register(guard);
        MinecraftForge.EVENT_BUS.register(guard);
        GTSteamReborn.LOG
            .info("[GTSR] dimension interference guard registered: populate=DENY potentialSpawns=whitelist dims=GTSR");
    }

    /**
     * 防线 2：GTSR 维度上对一切 {@link PopulateChunkEvent.Populate} 置 {@code DENY}——
     * 任何经 TerrainGen 礼仪投递的地形特性（BC 油井等第三方 CUSTOM 生成）不再进入 dim78/79。
     * 非本维事件在判型后原样放行（零漂移）。优先级 NORMAL：越早 DENY，后续订阅者读到的结果越一致。
     */
    @SubscribeEvent
    public void onPopulate(PopulateChunkEvent.Populate event) {
        if (event.world == null || event.world.provider == null) {
            return;
        }
        if (event.world.provider instanceof GTSRWorldProviderBase) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * 防线 3：GTSR 维度上的 {@link WorldEvent.PotentialSpawns}（1.7.10 该事件在
     * {@code net.minecraftforge.event.world.WorldEvent}，非 1.8+ 的 LivingSpawnEvent）按
     * <b>声明真值白名单</b>过滤——白名单 = 各 GTSR 群系经 {@code GTSRBiomeBase.addCreatureSpawn}
     * （L7 唯一合法写入口）声明过的「类型 × 实体类」，即各 {@code GTSRBiomeBase} 子类有效
     * spawn 声明表中出现的实体类；{@code EntityRegistry.addSpawn} 注入的条目（EnderZoo 等）
     * 不在白名单内，就地移除。
     * <p>
     * 取法：不读 Chunk byte 群系平面（P9 L1/L7 红线），群系身份走
     * {@link GTSRBiomeAuthority#ordinalAt(int, int)}（与 {@code getPossibleCreatures} 同一
     * 确定性采样通道）；裁决入口 {@link GTSRBiomeBase#retainDeclaredSpawns}。
     * 优先级 LOWEST：让先到的订阅者（含追加条目的）先跑，本守卫收口。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPotentialSpawns(WorldEvent.PotentialSpawns event) {
        final World world = event.world;
        if (world == null || world.provider == null) {
            return;
        }
        if (!(world.provider instanceof GTSRWorldProviderBase)) {
            return; // 非 GTSR 维度：零触碰
        }
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimension(world.provider.dimensionId);
        final GTSRBiomeAuthority.Resolution resolved = authority.ordinalAt(event.x, event.z);
        if (resolved == null || !resolved.resolved() || !(resolved.biome instanceof GTSRBiomeBase)) {
            // 降级态与 getPossibleCreatures 同口径：无表 = 不刷（不回退他方表）。
            // P15：改走 cancel 而非原地清空——list 是 public final 字段且实例归上游 provider 所有，
            // 清空不可变实例直接崩服、清空他方群系在册表则永久改写全局注册表。cancel 经
            // ForgeEventFactory 交回 null，WorldServer 对 null 即"该类型此处不刷"，观测语义不变。
            event.setCanceled(true);
            return;
        }
        // P15：本行原地写的合法性前提是 provider 出口每次交本次调用私有的可变副本（C1 源级钉）
        ((GTSRBiomeBase) resolved.biome).retainDeclaredSpawns(event.type, event.list);
    }
}
