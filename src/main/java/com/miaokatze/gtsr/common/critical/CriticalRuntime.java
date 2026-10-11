package com.miaokatze.gtsr.common.critical;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;

public final class CriticalRuntime {

    public static Block controller;

    private CriticalRuntime() {}

    /** GT controller factory used by recipes and legacy migration. */
    public static ItemStack getLoomControllerStack() {
        return com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalLoom.get(1);
    }

    public static int getLoomMetaId() {
        return com.miaokatze.gtsr.common.api.enums.MetaTileEntityID.CRITICAL_LOOM.ID;
    }

    public static void registerRecipes() {
        com.miaokatze.gtsr.loader.recipes.CriticalMachineRecipes.register();
    }

    public static void register() {
        if (controller != null) return;
        CriticalRenderAnchors.register();
        controller = new BlockCriticalController();
        GameRegistry.registerBlock(controller, "critical_controller");
        GameRegistry.registerTileEntity(TileEntityCriticalController.class, "gtsr.critical.controller");
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new StructureChangeListener());
        CriticalNightClock.register();
    }

    public static final class StructureChangeListener {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onControllerBreak(net.minecraftforge.event.world.BlockEvent.BreakEvent event) {
            if (event.world.isRemote) return;
            net.minecraft.tileentity.TileEntity tile = event.world.getTileEntity(event.x, event.y, event.z);
            if (!(tile instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt)
                || !(gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine))
                return;
            CriticalMachineState state = machine.getCriticalState();
            if (!state.canUse(event.getPlayer()) || !state.canRemove()) event.setCanceled(true);
        }

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onChange(net.minecraftforge.event.world.BlockEvent event) {
            if (!event.world.isRemote) CriticalWorldData.get(event.world)
                .invalidateAt(event.world, event.x, event.y, event.z);
        }
    }
}
