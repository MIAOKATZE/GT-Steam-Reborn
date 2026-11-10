package com.miaokatze.gtsr.common.dimension.framework;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 破碎之地只保留旧配置身份，不注册维度、Provider 或群系，也不打开其 DIM 文件夹。
 * Forge 1.7.10 的普通玩家文件与集成服 level.dat Player 均在 readFromNBT 后投递 LoadFromFile，
 * ServerConfigurationManager 随后才按 player.dimension 选择世界并发送 JoinGame。
 */
public final class RetiredShatteredDimension {

    private static final RetiredShatteredDimension INSTANCE = new RetiredShatteredDimension();
    private final Map<EntityPlayerMP, ChunkCoordinates> pending = new WeakHashMap<>();
    private static boolean registered;

    private RetiredShatteredDimension() {}

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    /** Never reinterpret vanilla or the retained Prosperity ID as a retired dimension. */
    public static boolean isRetiredDimension(int dimension) {
        return dimension > 1 && dimension == Config.shatteredDimId && dimension != Config.prosperityDimId;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        pending.remove(player);
        if (!isRetiredDimension(player.dimension)) return;
        WorldServer overworld = player.mcServer.worldServerForDimension(0);
        if (overworld == null) throw new IllegalStateException("Cannot evacuate retired dimension without overworld");
        ChunkCoordinates spawn = overworld.provider.getRandomizedSpawnPoint();
        if (spawn == null) spawn = overworld.getSpawnPoint();
        final int oldDimension = player.dimension;
        player.dimension = 0;
        player.setWorld(overworld);
        place(player, spawn);
        pending.put(player, new ChunkCoordinates(spawn));
        GTSteamReborn.LOG.info(
            "[GTSR] evacuated retired shattered dimension " + oldDimension
                + " player="
                + player.getUniqueID()
                + " before login world selection");
    }

    /** Only the player repaired by this load is touched; vanilla restores a saved Riding chain after LoggedIn. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        ChunkCoordinates spawn = pending.remove(player);
        if (spawn == null || player.dimension != 0) return;
        if (player.ridingEntity != null) player.mountEntity(null);
        place(player, spawn);
        if (player.playerNetServerHandler != null) player.playerNetServerHandler
            .setPlayerLocation(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);
        player.addChatMessage(new ChatComponentTranslation("gtsr.dimension.shattered.evacuated"));
    }

    private static void place(EntityPlayerMP player, ChunkCoordinates spawn) {
        player.setPositionAndRotation(
            spawn.posX + .5D,
            spawn.posY,
            spawn.posZ + .5D,
            player.rotationYaw,
            player.rotationPitch);
        player.motionX = player.motionY = player.motionZ = 0;
        player.fallDistance = 0;
    }
}
