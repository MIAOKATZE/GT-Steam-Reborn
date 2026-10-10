package com.miaokatze.gtsr.client.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;

import com.miaokatze.gtsr.common.dimension.prosperity.travel.BeaconNavigationNetwork;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class BeaconNavigationClient {

    private static WorldClient world;
    private static EntityPlayer player;
    private static long nonce = System.nanoTime();
    private static int x, z;
    private static boolean found;
    private static boolean handshakeComplete;
    private static boolean searchingReported;
    private static int helloRetryTicks;

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new BeaconNavigationClient());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld || player != mc.thePlayer) {
            world = mc.theWorld;
            player = mc.thePlayer;
            found = false;
            searchingReported = false;
            handshakeComplete = false;
            helloRetryTicks = 0;
            if (++nonce == 0) nonce++;
        }
        if (world == null || player == null || handshakeComplete) return;
        if (helloRetryTicks-- <= 0) {
            BeaconNavigationNetwork.hello(nonce, player.dimension);
            helloRetryTicks = 99;
        }
    }

    public static void receive(final BeaconNavigationNetwork.Target target) {
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient receivingWorld = mc.theWorld;
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (receivingWorld == null || mc.theWorld != receivingWorld
                    || world != receivingWorld
                    || player == null
                    || player != mc.thePlayer
                    || target.nonce != nonce
                    || target.dimension != player.dimension) return;
                handshakeComplete = true;
                // Duplicate handshake acknowledgements never erase a previously displayed target.
                if (target.acknowledgement) return;
                if (target.reason == BeaconNavigationNetwork.Reason.UNAVAILABLE) {
                    player.addChatMessage(new ChatComponentTranslation("gtsr.beacon.navigation.unavailable"));
                    searchingReported = true;
                } else if (target.dimension == 0 && !target.found && !searchingReported) {
                    player.addChatMessage(new ChatComponentTranslation("gtsr.beacon.navigation.searching"));
                    searchingReported = true;
                }
                found = target.found;
                x = target.x;
                z = target.z;
            }
        });
    }

    /** Degrees clockwise from the dial's north, relative to the local player's view. */
    public static float angle(boolean charging) {
        double seconds = Minecraft.getSystemTime() / 1000D;
        if (charging) return (float) (seconds * 720 % 360);
        Minecraft mc = Minecraft.getMinecraft();
        if (found && world == mc.theWorld && player != null && player == mc.thePlayer) {
            double dx = x + .5 - player.posX, dz = z + .5 - player.posZ;
            return (float) (Math.toDegrees(Math.atan2(-dx, dz)) - player.rotationYaw);
        }
        if (mc.thePlayer != null && mc.thePlayer.dimension == 0) return 0;
        return (float) (seconds * 12 % 360);
    }
}
