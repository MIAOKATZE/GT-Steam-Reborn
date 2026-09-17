package com.miaokatze.gtsr.common.network;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.items.SingularityTuningWand;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 奇点调谐棒网络通道（channel {@code gtsr_wand}，控制面；独立于 gtsr_fx/gtsr_terminal，
 * discriminator 空间不混用）。
 *
 * <p>
 * 仅一条 C2S（disc 0）：选择界面每次变更即时提交（词条游标 + 增量档位）。netty 线程入队，
 * ServerTick END 排水到主线程处理（GTSRFXNet BindServerDrain 先例口径）：服务端校验持械
 * （主手为奇点调谐棒）→ 钳位写手持 NBT → {@code detectAndSendChanges()} 同步客户端手持。
 */
public class WandNet {

    private static final ConcurrentLinkedQueue<PendingSelect> PENDING_SELECT = new ConcurrentLinkedQueue<>();

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_wand");

    public static void init() {
        NETWORK.registerMessage(SelectHandler.class, PacketWandSelect.class, 0, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new SelectServerDrain());
    }

    /** 客户端：提交当前选择（主线程；每次点击即发，服务端权威落 NBT） */
    public static void sendSelectFromClient(int entry, int deltaIndex) {
        NETWORK.sendToServer(new PacketWandSelect(entry, deltaIndex));
    }

    public static class SelectHandler implements IMessageHandler<PacketWandSelect, IMessage> {

        @Override
        public IMessage onMessage(PacketWandSelect msg, MessageContext ctx) {
            if (ctx.getServerHandler() != null) {
                PENDING_SELECT.add(new PendingSelect(ctx.getServerHandler().playerEntity, msg));
            }
            return null;
        }
    }

    private static final class PendingSelect {

        final EntityPlayerMP player;
        final PacketWandSelect msg;

        PendingSelect(EntityPlayerMP p, PacketWandSelect m) {
            player = p;
            msg = m;
        }
    }

    public static final class SelectServerDrain {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            PendingSelect task;
            while ((task = PENDING_SELECT.poll()) != null) {
                EntityPlayerMP player = task.player;
                if (player == null || player.isDead) continue;
                ItemStack held = player.getHeldItem();
                if (!(held != null && held.getItem() instanceof SingularityTuningWand)) continue;
                SingularityTuningWand.setSelectedEntry(held, task.msg.entry);
                SingularityTuningWand.setSelectedDeltaIndex(held, task.msg.deltaIndex);
                player.inventoryContainer.detectAndSendChanges();
            }
        }
    }
}
