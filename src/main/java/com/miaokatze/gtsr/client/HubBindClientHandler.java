package com.miaokatze.gtsr.client;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import org.lwjgl.input.Keyboard;

import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.base.MTEHubArrayBase;
import com.miaokatze.gtsr.common.network.GTSRFXNet;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** 客户端 Alt 绑定预检：阻断原版放置预测并发送服务端绑定请求。 */
@SideOnly(Side.CLIENT)
public class HubBindClientHandler {

    @SubscribeEvent
    public void onPlayerRightClickBlock(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || !event.world.isRemote) return;
        if (!Keyboard.isKeyDown(Keyboard.KEY_LMENU) && !Keyboard.isKeyDown(Keyboard.KEY_RMENU)) return;
        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof IGregTechTileEntity gte)) return;
        IMetaTileEntity meta = gte.getMetaTileEntity();
        boolean canBind = meta instanceof MTEHubArrayBase hub && hub.canBindHeld(event.entityPlayer.getHeldItem())
            || meta instanceof MTESingularityDrillingHub
                && MTESingularityDrillingHub.canBindHeld(event.entityPlayer.getHeldItem());
        if (!canBind || event.entityPlayer.getHeldItem() == null) return;
        event.setCanceled(true);
        GTSRFXNet.sendHubBind(event.x, event.y, event.z, event.world.provider.dimensionId);
    }
}
