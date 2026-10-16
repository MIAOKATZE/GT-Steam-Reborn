package com.miaokatze.gtsr.common.critical;

import net.minecraft.item.ItemStack;

public final class CriticalRuntime {

    private static boolean registered;
    private static final java.util.Set<CriticalMachineState> CLEARANCE = java.util.Collections
        .newSetFromMap(new java.util.WeakHashMap<>());

    static void registerClearance(CriticalMachineState state) {
        if (state.worldObj != null && !state.worldObj.isRemote) CLEARANCE.add(state);
    }

    static void unregisterClearance(CriticalMachineState state) {
        if (state.worldObj != null && !state.worldObj.isRemote) CLEARANCE.remove(state);
    }

    public static void changedDuringClearance(net.minecraft.world.World world, int x, int y, int z) {
        if (world.isRemote || CLEARANCE.isEmpty()) return;
        for (CriticalMachineState state : CLEARANCE) if (state.worldObj == world) state.recordClearanceChange(x, y, z);
    }

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
        public void onWorldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
            CriticalBlockProtection.unloadClientWorld(event.world);
        }

        @cpw.mods.fml.common.eventhandler.SubscribeEvent(
            priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
        public void onExplosion(net.minecraftforge.event.world.ExplosionEvent.Detonate event) {
            CriticalBlockProtection.filterExplosion(event);
        }

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onControllerBreak(net.minecraftforge.event.world.BlockEvent.BreakEvent event) {
            if (event.world.isRemote) return;
            if (CriticalBlockProtection.protects(event.world, event.x, event.y, event.z)) {
                event.setCanceled(true);
                return;
            }
            net.minecraft.tileentity.TileEntity tile = event.world.getTileEntity(event.x, event.y, event.z);
            if (!(tile instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt)
                || !(gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine))
                return;
            CriticalMachineState state = machine.getCriticalState();
            if (machine.getLocalCriticalState()
                .isLoomOwnedMain() && state == machine.getLocalCriticalState()) {
                event.setCanceled(!machine.canRemoveOrphanedMain(event.getPlayer()));
                return;
            }
            if (!state.canUse(event.getPlayer()) || !state.canRemove()) event.setCanceled(true);
        }

        /** Cancel destructive GT tool interactions before they bypass the normal harvest event. */
        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onPlayerInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
            if (event.world.isRemote
                || event.action == net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_AIR)
                return;
            if (!CriticalBlockProtection.protects(event.world, event.x, event.y, event.z)) return;
            // Rotating a generated GT controller would invalidate its exact paid-voxel identity.
            // Ordinary right-click still reaches its GUI, including the internal dismantle/repair controls.
            if (event.world.getBlock(event.x, event.y, event.z) instanceof CriticalMaterials.MaterialBlock
                || event.action == net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.LEFT_CLICK_BLOCK
                || event.entityPlayer != null && gregtech.api.util.GTUtility
                    .isStackInList(event.entityPlayer.getHeldItem(), gregtech.api.GregTechAPI.sWrenchList))
                event.setCanceled(true);
        }

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onChange(net.minecraftforge.event.world.BlockEvent event) {
            if (!event.world.isRemote) {
                changedDuringClearance(event.world, event.x, event.y, event.z);
                CriticalWorldData.get(event.world)
                    .invalidateAt(event.world, event.x, event.y, event.z);
            }
        }
    }
}
