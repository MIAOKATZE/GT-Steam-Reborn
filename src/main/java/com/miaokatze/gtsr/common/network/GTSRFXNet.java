package com.miaokatze.gtsr.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.common.blocks.GTSRSingularityFX;
import com.miaokatze.gtsr.common.machine.HubBindPlacementGuard;
import com.miaokatze.gtsr.common.terminal.TerminalNet;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * GTSR 网络通道（粒子 S2C + 终端轨）。
 * 服务端吸收方块时广播 AbsorbMessage，客户端在主线程生成向心粒子；
 * init 尾部追加终端轨 TerminalNet 注册（channel "gtsr_terminal"，terminal-native-ui M8/N28）。
 */
public class GTSRFXNet {

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_fx");

    public static void init() {
        NETWORK.registerMessage(AbsorbMessageHandler.class, AbsorbMessage.class, 0, Side.CLIENT);
        // 终端轨（channel "gtsr_terminal"）：注册点收束于本 init 尾部，满足「GTSRFXNet 基建可扩展」决策
        TerminalNet.register();
        // 枢纽绑定放置拦截（Forge 事件轨）：服务端取消潜行右击枢纽的放置并转调绑定入口
        MinecraftForge.EVENT_BUS.register(new HubBindPlacementGuard());
    }

    /** 服务端：吸收方块处生成向心粒子（S2C 广播 64 格内玩家） */
    public static void sendAbsorb(World world, int fx, int fy, int fz, int tx, int ty, int tz) {
        if (world.isRemote) {
            return;
        }
        AbsorbMessage msg = new AbsorbMessage(fx, fy, fz, tx, ty, tz);
        double maxDistSq = 64.0 * 64.0;
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) o;
                double dx = p.posX - fx;
                double dy = p.posY - fy;
                double dz = p.posZ - fz;
                if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                    NETWORK.sendTo(msg, p);
                }
            }
        }
    }

    public static class AbsorbMessage implements IMessage {

        private int fx;
        private int fy;
        private int fz;
        private int tx;
        private int ty;
        private int tz;

        public AbsorbMessage() {}

        public AbsorbMessage(int fx, int fy, int fz, int tx, int ty, int tz) {
            this.fx = fx;
            this.fy = fy;
            this.fz = fz;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            PacketBuffer pb = new PacketBuffer(buf);
            this.fx = pb.readInt();
            this.fy = pb.readInt();
            this.fz = pb.readInt();
            this.tx = pb.readInt();
            this.ty = pb.readInt();
            this.tz = pb.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            PacketBuffer pb = new PacketBuffer(buf);
            pb.writeInt(this.fx);
            pb.writeInt(this.fy);
            pb.writeInt(this.fz);
            pb.writeInt(this.tx);
            pb.writeInt(this.ty);
            pb.writeInt(this.tz);
        }
    }

    public static class AbsorbMessageHandler implements IMessageHandler<AbsorbMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(AbsorbMessage msg, MessageContext ctx) {
            // Netty 线程执行：调度回客户端主线程生成粒子（实现见 GTSRSingularityFX.spawnAbsorbScheduled，
            // 该类级 @SideOnly 辅助类服务端永不加载；本 handler 不得出现 lambda/客户端类引用，
            // 否则合成方法会在服务端类链接时解析 Minecraft/WorldClient 导致 SideTransformer 崩溃）
            GTSRSingularityFX.spawnAbsorbScheduled(msg.fx, msg.fy, msg.fz, msg.tx, msg.ty, msg.tz);
            return null;
        }
    }
}
