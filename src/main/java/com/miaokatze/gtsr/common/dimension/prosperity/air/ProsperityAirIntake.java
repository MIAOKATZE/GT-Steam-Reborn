package com.miaokatze.gtsr.common.dimension.prosperity.air;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;

import com.miaokatze.gtsr.common.dimension.prosperity.lore.FictionTimeline;
import com.miaokatze.gtsr.config.Config;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchAirIntake;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchAirIntakeAtmosphere;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchAirIntakeExtreme;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchFluidGenerator;

/** Restricts only the six prosperity feeds while the existing world project is unfinished. */
public final class ProsperityAirIntake {

    public static boolean unstable(IGregTechTileEntity base) {
        if (base == null) return false;
        World world = base.getWorld();
        if (world == null || world.provider == null
            || Config.prosperityDimId < 0
            || world.provider.dimensionId != Config.prosperityDimId) return false;
        Materials air = ProsperityAirLookup.of(world, base.getXCoord(), base.getZCoord());
        return air != null && air.mGas != null
            && !FictionTimeline.get(world)
                .worldCompleted();
    }

    public static int generationAmount(MTEHatchFluidGenerator hatch, int nativeAmount) {
        if (!(hatch instanceof MTEHatchAirIntake) || !unstable(hatch.getBaseMetaTileEntity())) return nativeAmount;
        // All three GT205 intakes generate once every 20 ticks; these amounts are litres per second.
        if (hatch instanceof MTEHatchAirIntakeAtmosphere) return 2000;
        if (hatch instanceof MTEHatchAirIntakeExtreme) return 500;
        return 100;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void placed(BlockEvent.PlaceEvent event) {
        if (event.world.isRemote || event.player == null) return;
        TileEntity tile = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(tile instanceof IGregTechTileEntity)) return;
        IGregTechTileEntity base = (IGregTechTileEntity) tile;
        if (!(base.getMetaTileEntity() instanceof MTEHatchAirIntake) || !unstable(base)) return;
        int rate = generationAmount((MTEHatchFluidGenerator) base.getMetaTileEntity(), 0);
        event.player.addChatMessage(new ChatComponentTranslation("gtsr.air_intake.unstable", rate));
    }
}
