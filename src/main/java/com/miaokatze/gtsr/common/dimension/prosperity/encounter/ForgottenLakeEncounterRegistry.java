package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.EntityGearPigeon;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.EntitySlagRidgeHunter;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.EntitySteamFirefly;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public final class ForgottenLakeEncounterRegistry {

    public static BlockSealedChest sealedChest;
    private static boolean registered;

    public static void registerBlocks() {
        sealedChest = new BlockSealedChest();
        GameRegistry.registerBlock(sealedChest, "SealedChest");
        GameRegistry.registerTileEntity(TileEntitySealedChest.class, "gtsr.sealedChest");
    }

    public static void preInit() {
        if (registered) return;
        Object mod = Loader.instance()
            .getIndexedModList()
            .get(GTSteamReborn.MODID)
            .getMod();
        EntityRegistry.registerModEntity(EntityResidualOathguard.class, "ResidualOathguard", 40, mod, 96, 3, true);
        EntityRegistry.registerModEntity(EntitySilentKing.class, "SilentKing", 41, mod, 160, 1, true);
        MinecraftForge.EVENT_BUS.register(new ForgottenLakeEncounterRegistry());
        registered = true;
    }

    @SubscribeEvent
    public void removeRetiredCreatures(EntityJoinWorldEvent e) {
        if (!e.world.isRemote && e.world.provider instanceof WorldProviderProsperityRuins
            && (e.entity instanceof EntityGearPigeon || e.entity instanceof EntitySteamFirefly
                || e.entity instanceof EntitySlagRidgeHunter)) {
            e.entity.setDead();
            e.setCanceled(true);
        }
    }
}
