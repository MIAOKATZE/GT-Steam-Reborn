package com.miaokatze.gtsr.common.critical;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;

public final class CriticalRuntime {

    public static Block controller;

    private CriticalRuntime() {}

    public static void registerRecipes() {
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(controller),
                "RFR",
                "SCE",
                "RMR",
                'R',
                ItemList.Robot_Arm_ZPM.get(1),
                'F',
                ItemList.Field_Generator_ZPM.get(1),
                'S',
                ItemList.Sensor_ZPM.get(1),
                'C',
                ItemList.Casing_ZPM.get(1),
                'E',
                ItemList.Emitter_ZPM.get(1),
                'M',
                "circuitZPM"));
    }

    public static void register() {
        if (controller != null) return;
        controller = new BlockCriticalController();
        GameRegistry.registerBlock(controller, "critical_controller");
        GameRegistry.registerTileEntity(TileEntityCriticalController.class, "gtsr.critical.controller");
        CreativeTabManager.addItemToTab(new ItemStack(controller));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new StructureChangeListener());
        CriticalNightClock.register();
    }

    public static final class StructureChangeListener {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onChange(net.minecraftforge.event.world.BlockEvent event) {
            if (!event.world.isRemote) CriticalWorldData.get(event.world)
                .invalidateAt(event.world, event.x, event.y, event.z);
        }
    }
}
