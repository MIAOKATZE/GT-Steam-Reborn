package com.miaokatze.gtsr.common.debug;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.input.Keyboard;

import com.miaokatze.gtsr.client.gui.wand.GuiMachineDebugSelector;
import com.miaokatze.gtsr.common.items.SingularityTuningWand;
import com.miaokatze.gtsr.common.network.WandNet;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** All keyboard and GUI references stay behind the physical client boundary. */
@SideOnly(Side.CLIENT)
public final class WandClient {

    private static final ConcurrentLinkedQueue<WandNet.PacketOpen> OPEN = new ConcurrentLinkedQueue<>();

    public static void init() {
        WandClient listener = new WandClient();
        FMLCommonHandler.instance()
            .bus()
            .register(listener);
    }

    public static void enqueue(WandNet.PacketOpen message) {
        OPEN.add(message);
    }

    public static void useTarget(EntityPlayer player, IGregTechTileEntity base, int x, int y, int z) {
        boolean alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        WandNet.sendTarget(
            x,
            y,
            z,
            player.dimension,
            base.getMetaTileID(),
            alt ? 0 : player.isSneaking() ? 2 : 1,
            player.inventory.currentItem);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        WandNet.PacketOpen message;
        while ((message = OPEN.poll()) != null) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.dimension != message.dimension) continue;
            var held = mc.thePlayer.getHeldItem();
            if (held == null || !(held.getItem() instanceof SingularityTuningWand)
                || mc.thePlayer.inventory.currentItem != message.slot) continue;
            if (!(mc.theWorld.getTileEntity(message.x, message.y, message.z) instanceof IGregTechTileEntity base)
                || base.getMetaTileID() != message.metaId) continue;
            var adapter = MachineDebugRegistry.find(base.getMetaTileEntity());
            if (adapter == null || !adapter.family()
                .equals(message.family)) continue;
            var schema = adapter.options(base.getMetaTileEntity());
            if (schema.length != message.values.length) continue;
            mc.displayGuiScreen(new GuiMachineDebugSelector(message.token, adapter.family(), schema, message.values));
        }
    }
}
