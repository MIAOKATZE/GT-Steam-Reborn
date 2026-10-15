package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicTank;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;

/** Opt-in, coordinate-bound refill service. Only actual input hatches in the test world are eligible. */
public final class TestLabSupply {

    private static final List<Feed> FEEDS = new ArrayList<>();

    private TestLabSupply() {}

    public static void add(WorldServer world, IGregTechTileEntity tile, FluidStack fluid) {
        validateFluid(world, tile, fluid);
        remove(tile);
        FEEDS.add(new Feed(world, tile, fluid.copy()));
    }

    /** Shared by command setup and task fixtures; generic tanks and output hatches are excluded. */
    public static MTEBasicTank inputTank(WorldServer world, IGregTechTileEntity tile) {
        TestLabFixtures.attach(world);
        if (tile == null || tile.getWorld() != world
            || !(tile.getMetaTileEntity() instanceof MTEHatchInput
                || tile.getMetaTileEntity() instanceof MTEHatchCustomFluidBase))
            throw new IllegalArgumentException("Supply requires an actual input hatch in this lab");
        return (MTEBasicTank) tile.getMetaTileEntity();
    }

    public static void validateFluid(WorldServer world, IGregTechTileEntity tile, FluidStack fluid) {
        MTEBasicTank hatch = inputTank(world, tile);
        if (fluid == null || fluid.getFluid() == null || !hatch.isFluidInputAllowed(fluid))
            throw new IllegalArgumentException("该真实输入仓的流体过滤不接纳此流体");
    }

    public static void remove(IGregTechTileEntity tile) {
        FEEDS.removeIf(feed -> feed.tile == tile);
    }

    public static void clear(WorldServer world) {
        FEEDS.removeIf(feed -> feed.world == world);
    }

    public static void tick(WorldServer world) {
        if (!(world.provider instanceof TestLabWorldProvider)) return;
        Iterator<Feed> iterator = FEEDS.iterator();
        while (iterator.hasNext()) {
            Feed feed = iterator.next();
            if (feed.world != world) continue;
            if (((TileEntity) feed.tile).isInvalid()
                || world.getTileEntity(feed.tile.getXCoord(), feed.tile.getYCoord(), feed.tile.getZCoord())
                    != feed.tile) {
                iterator.remove();
                continue;
            }
            if (!(feed.tile.getMetaTileEntity() instanceof MTEHatchInput
                || feed.tile.getMetaTileEntity() instanceof MTEHatchCustomFluidBase)) {
                iterator.remove();
                continue;
            }
            MTEBasicTank hatch = inputTank(world, feed.tile);
            // Call the real hatch's filter/capacity-aware fill; no arbitrary output tank or global hook is touched.
            FluidStack request = feed.fluid.copy();
            request.amount = Integer.MAX_VALUE;
            int filled = hatch.fill(ForgeDirection.UNKNOWN, request, true);
            if (feed.initialized) feed.replenished += Math.max(0, filled);
            if (filled > 0 || hatch.getFluid() != null) feed.initialized = true;
        }
    }

    public static String describe(WorldServer world) {
        StringBuilder result = new StringBuilder();
        for (Feed feed : FEEDS) if (feed.world == world) result.append(feed.tile.getXCoord())
            .append(',')
            .append(feed.tile.getYCoord())
            .append(',')
            .append(feed.tile.getZCoord())
            .append('=')
            .append(
                feed.fluid.getFluid()
                    .getName())
            .append(';');
        return result.toString();
    }

    static long replenished(IGregTechTileEntity tile) {
        for (Feed feed : FEEDS) if (feed.tile == tile) return feed.replenished;
        return 0;
    }

    public static boolean hasFeedAtChunk(WorldServer world, int chunkX, int chunkZ) {
        for (Feed feed : FEEDS) {
            if (feed.world == world && (feed.tile.getXCoord() >> 4) == chunkX && (feed.tile.getZCoord() >> 4) == chunkZ)
                return true;
        }
        return false;
    }

    private static final class Feed {

        final WorldServer world;
        final IGregTechTileEntity tile;
        final FluidStack fluid;
        boolean initialized;
        long replenished;

        Feed(WorldServer world, IGregTechTileEntity tile, FluidStack fluid) {
            this.world = world;
            this.tile = tile;
            this.fluid = fluid;
        }
    }
}
