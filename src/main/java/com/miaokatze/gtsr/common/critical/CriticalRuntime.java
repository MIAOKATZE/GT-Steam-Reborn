package com.miaokatze.gtsr.common.critical;

import net.minecraft.item.ItemStack;

public final class CriticalRuntime {

    private static boolean registered;

    private CriticalRuntime() {}

    /** GT controller factory used by recipes. */
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
        if (registered) return;
        CriticalRenderAnchors.register();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new StructureChangeListener());
        CriticalNightClock.register();
        registered = true;
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
