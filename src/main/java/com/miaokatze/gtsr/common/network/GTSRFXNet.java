package com.miaokatze.gtsr.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.GTSRSingularityFX;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * GTSR 网络通道：客户端粒子特效 S2C 同步（首个网络设施）。
 * 服务端吸收方块时广播 AbsorbMessage，客户端在主线程生成向心粒子。
 */
public class GTSRFXNet {

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_fx");

    public static void init() {
        NETWORK.registerMessage(AbsorbMessageHandler.class, AbsorbMessage.class, 0, Side.CLIENT);
    }

    /** 服务端：吸收方块处生成向心粒子（S2C 广播 64 格内玩家） */
    public static void sendAbsorb(World world, int fx, int fy, int fz, int tx, int ty, int tz, int color) {
        if (world.isRemote) {
            return;
        }
        AbsorbMessage msg = new AbsorbMessage(fx, fy, fz, tx, ty, tz, color);
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
        private int color;

        public AbsorbMessage() {}

        public AbsorbMessage(int fx, int fy, int fz, int tx, int ty, int tz, int color) {
            this.fx = fx;
            this.fy = fy;
            this.fz = fz;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
            this.color = color;
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
            this.color = pb.readInt();
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
            pb.writeInt(this.color);
        }
    }

    public static class AbsorbMessageHandler implements IMessageHandler<AbsorbMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(AbsorbMessage msg, MessageContext ctx) {
            // Netty 线程执行：粒子生成必须调度回客户端主线程（1.7.10 无 addScheduledTask，用 func_152344_a）
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    World w = Minecraft.getMinecraft().theWorld;
                    if (w != null) {
                        float r = (float) ((msg.color >> 16) & 0xFF) / 255.0F;
                        float g = (float) ((msg.color >> 8) & 0xFF) / 255.0F;
                        float b = (float) (msg.color & 0xFF) / 255.0F;
                        GTSRSingularityFX.spawnAbsorb(
                            w,
                            msg.fx + 0.5,
                            msg.fy + 0.5,
                            msg.fz + 0.5,
                            msg.tx + 0.5,
                            msg.ty + 0.5,
                            msg.tz + 0.5,
                            r,
                            g,
                            b);
                    }
                });
            return null;
        }
    }
}
