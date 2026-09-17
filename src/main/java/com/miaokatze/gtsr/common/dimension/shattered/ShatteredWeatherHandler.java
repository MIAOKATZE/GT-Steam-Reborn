package com.miaokatze.gtsr.common.dimension.shattered;

import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 破碎之地强制雷暴 + 附加雷控制器（dim1 S6b，plan §2 S6b / 04 §2.2 裁剪版——失稳值热点追踪不做）：
 * <ul>
 * <li>层1 强制雷暴：服务端 dim79 world tick（END 相）内 WorldInfo.setThundering(true) +
 * setThunderTime(MAX/2)——雷暴窗口永不自然耗尽。dimId 守卫保证：离开 dim79 后该维度世界卸载停 tick，
 * handler 不再对任何 WorldInfo 写入（其余维度天气自然运转）。</li>
 * <li>层2 附加雷：每 30-80t 一道，落点 = 随机在线玩家 24-48 格水平环上随机方位的地表
 * （getPrecipitationHeight），addWeatherEffect 原版雷——不做额外伤害增益。</li>
 * </ul>
 * 注册口径：1.7.10 TickEvent.WorldTickEvent 在 FML bus 派发（{@code FMLCommonHandler.instance().bus()}），
 * 任务包要求的 {@code MinecraftForge.EVENT_BUS.register} 同对象一并注册（双轨保险，Forge 事件本类不消费）。
 */
public class ShatteredWeatherHandler {

    /** 附加雷间隔下限（tick，04 §2.2 口径 30-80t）。 */
    private static final int STRIKE_DELAY_MIN = 30;

    /** 附加雷间隔窗口（30 + rand(50) → 30-79t）。 */
    private static final int STRIKE_DELAY_WINDOW = 50;

    /** 附加雷落点距玩家最小水平距离（格）。 */
    private static final int STRIKE_DISTANCE_MIN = 24;

    /** 附加雷落点距离窗口（24 + rand(25) → 24-48 格）。 */
    private static final int STRIKE_DISTANCE_WINDOW = 25;

    /** 下一道附加雷倒计时（服务端主线程单处消费，仅 dim79 END 相到达）。 */
    private int nextStrikeDelay = STRIKE_DELAY_MIN;

    /**
     * 注册入口（CommonProxy.init 一行调用）：双轨 bus 注册同实例 + 注册证据日志
     * （plan S6b 验收 runServer 日志 grep 锚点）。
     */
    public static void register() {
        final ShatteredWeatherHandler handler = new ShatteredWeatherHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
        GTSteamReborn.LOG.info(
            "[GTSR] shattered weather handler registered: dimId={} forcedThunderstorm=true extraStrike=1/30-80t",
            Config.shatteredDimId);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return; // 仅 END 相处理一次（START/END 双触发会把附加雷频率翻倍）
        }
        final World world = event.world;
        // 守卫链①：服务端（WorldTickEvent 双端都会构造，客户端世界直接跳过）
        if (event.side.isClient() || world.isRemote) {
            return;
        }
        // 守卫链②：破碎维度（shatteredDimId<0 视为禁用）——离开 dim79 的世界 tick 不到这里，
        // 其余维度天气完全不受本 handler 写入
        if (Config.shatteredDimId < 0 || world.provider.dimensionId != Config.shatteredDimId) {
            return;
        }
        // 守卫链③：维度总开关（关闭时维度不注册，此为配置热改双保险）
        if (!Config.planDimension.shatteredDimension) {
            return;
        }
        // 层1：强制雷暴（写 WorldInfo，1.7.10 服务端安全口径，04 §2.2；雷暴窗口不自然衰减）
        final WorldInfo info = world.getWorldInfo();
        if (!info.isThundering()) {
            info.setThundering(true);
            info.setThunderTime(Integer.MAX_VALUE / 2);
        }
        // 层2：附加雷（world 内无在线玩家时不掷不递减）
        if (world.playerEntities.isEmpty()) {
            return;
        }
        if (--this.nextStrikeDelay > 0) {
            return;
        }
        this.nextStrikeDelay = STRIKE_DELAY_MIN + world.rand.nextInt(STRIKE_DELAY_WINDOW);
        strikeNearRandomPlayer(world);
    }

    /** 附加雷：随机在线玩家 24-48 格水平环上随机方位 → 地表（getPrecipitationHeight）原版雷。 */
    private static void strikeNearRandomPlayer(World world) {
        final Object obj = world.playerEntities.get(world.rand.nextInt(world.playerEntities.size()));
        if (!(obj instanceof EntityPlayer)) {
            return;
        }
        final EntityPlayer player = (EntityPlayer) obj;
        final int distance = STRIKE_DISTANCE_MIN + world.rand.nextInt(STRIKE_DISTANCE_WINDOW); // 24-48
        final double angle = world.rand.nextDouble() * Math.PI * 2.0D;
        final int x = (int) Math.round(player.posX + Math.cos(angle) * distance);
        final int z = (int) Math.round(player.posZ + Math.sin(angle) * distance);
        final int y = world.getPrecipitationHeight(x, z);
        world.addWeatherEffect(new EntityLightningBolt(world, x, y, z));
    }
}
