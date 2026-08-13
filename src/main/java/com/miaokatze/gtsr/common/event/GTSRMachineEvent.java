package com.miaokatze.gtsr.common.event;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * 机器事件聊天通知父类：玩家 / 坐标维度 / 机器 / 事件 + 全服通告开关。
 * <p>
 * 子类以 machineKey / eventKey / resultKey 组合 gtsr.event.broadcast 通告消息，
 * 经 sendChat() 全服或定向发送；静态管道（sendToPlayer / sendToOwner）供简单 lang 键站点直接使用。
 */
public abstract class GTSRMachineEvent {

    /** 机器显示名 lang 键（gt.blockmachines.gtsr.xxx.name） */
    protected final String machineKey;
    /** 事件名 lang 键 */
    protected final String eventKey;
    /** 结果 lang 键 */
    protected final String resultKey;
    /** 坐标 + 维度 */
    protected final int x, y, z, dim;
    /** 定向玩家（可 null） */
    protected EntityPlayer targetPlayer;
    /** 全服通告开关 */
    protected boolean broadcast;

    protected GTSRMachineEvent(String machineKey, String eventKey, String resultKey, int x, int y, int z, int dim) {
        this.machineKey = machineKey;
        this.eventKey = eventKey;
        this.resultKey = resultKey;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
    }

    /** 设置定向玩家（链式） */
    public GTSRMachineEvent setTarget(EntityPlayer p) {
        this.targetPlayer = p;
        return this;
    }

    /** 设置全服通告开关（链式） */
    public GTSRMachineEvent setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
        return this;
    }

    /**
     * 构建通告消息：%1$s=维度 %2$s=x %3$s=y %4$s=z %5$s=机器 %6$s=事件 %7$s=结果。
     * 机器/事件/结果以本地化文本展开后作为翻译参数注入。
     */
    public IChatComponent buildMessage() {
        return new ChatComponentTranslation(
            "gtsr.event.broadcast",
            dim,
            x,
            y,
            z,
            StatCollector.translateToLocal(machineKey),
            StatCollector.translateToLocal(eventKey),
            StatCollector.translateToLocal(resultKey));
    }

    /** 发送聊天：broadcast → 全服通告；否则定向发送 targetPlayer（非空时） */
    public void sendChat() {
        if (broadcast) {
            MinecraftServer.getServer()
                .getConfigurationManager()
                .sendChatMsg(buildMessage());
        } else if (targetPlayer != null) {
            targetPlayer.addChatMessage(buildMessage());
        }
    }

    /** 静态简单键管道：向指定玩家发送一条翻译键聊天消息 */
    public static void sendToPlayer(EntityPlayer p, String langKey, Object... args) {
        if (p != null) p.addChatMessage(new ChatComponentTranslation(langKey, args));
    }

    /** 静态简单键管道：向在线所有者发送一条翻译键聊天消息（遍历玩家列表按 UUID 匹配，离线不发送） */
    public static void sendToOwner(UUID ownerUuid, String langKey, Object... args) {
        if (ownerUuid == null) return;
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP player && player.getUniqueID()
                .equals(ownerUuid)) {
                player.addChatMessage(new ChatComponentTranslation(langKey, args));
                return;
            }
        }
    }
}
