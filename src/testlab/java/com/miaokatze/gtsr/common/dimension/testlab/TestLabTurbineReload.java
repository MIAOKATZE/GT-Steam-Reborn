// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.MTEHeavySteamTurbine;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Real controller chunk unload, a late output hatch, then a late fuel supply. */
final class TestLabTurbineReload {

    private final Position site, input, output, dynamo;
    private final int outputId;
    private final boolean heavy;
    private final ChunkCoordIntPair controllerChunk;
    private IGregTechTileEntity controller;
    private ForgeChunkManager.Ticket ticket;
    private FluidStack steam;
    private NBTTagCompound warmCheckpoint;
    private int phase, since, reloadTick, formedTick, retainedEfficiency;
    private long energy;

    TestLabTurbineReload(IGregTechTileEntity controller, List<IGregTechTileEntity> ports) {
        this.controller = controller;
        site = new Position(controller);
        input = new Position(hatch(ports, MTEHatchInput.class));
        IGregTechTileEntity outputPort = hatch(ports, MTEHatchOutput.class);
        output = new Position(outputPort);
        outputId = outputPort.getMetaTileID();
        dynamo = new Position(hatch(ports, MTEHatchDynamo.class));
        heavy = controller.getMetaTileEntity() instanceof MTEHeavySteamTurbine;
        controllerChunk = new ChunkCoordIntPair(site.x >> 4, site.z >> 4);
    }

    TestLabReport.Result tick(TestLabTask owner, WorldServer world, int tick) {
        switch (phase) {
            case 0 -> {
                NBTTagCompound checkpoint = snapshot();
                if (efficiency(checkpoint) <= 0 || dynamo.get(world)
                    .getStoredEU() <= 0) return null;
                MTEHatchInput supply = (MTEHatchInput) input.get(world)
                    .getMetaTileEntity();
                if (supply.getFluid() == null) return null;
                steam = supply.getFluid()
                    .copy();
                warmCheckpoint = checkpoint;
                cutFuel(world);
                ticket = TestLabRuntime.INSTANCE.suspendTaskChunk(owner, world, controllerChunk);
                world.theChunkProviderServer
                    .unloadChunksIfNotNearSpawn(controllerChunk.chunkXPos, controllerChunk.chunkZPos);
                since = tick;
                phase = 1;
            }
            case 1 -> {
                if (world.theChunkProviderServer.chunkExists(controllerChunk.chunkXPos, controllerChunk.chunkZPos)) {
                    require(tick - since < 20, "Controller chunk never unloaded");
                    return null;
                }
                restoreChunk(world);
                IGregTechTileEntity old = controller;
                controller = site.get(world);
                require(controller != old && controller.isAllowedToWork(), "Reload lost the GT tile or work switch");
                require(efficiency(snapshot()) == efficiency(warmCheckpoint), "Reload lost the saved efficiency");
                require(world.setBlockToAir(output.x, output.y, output.z), "Could not delay the output port");
                energy = dynamo.get(world)
                    .getStoredEU();
                reloadTick = tick;
                phase = 2;
            }
            case 2 -> {
                NBTTagCompound current = snapshot();
                require(
                    controller.isAllowedToWork() && !machine().mMachine,
                    "A late required port incorrectly formed the machine or switched it off");
                require(efficiency(current) == efficiency(warmCheckpoint), "Lost efficiency while forming");
                if (heavy) for (String key : new String[] { "workingTicks", "idleTicks", "fullFlowTicks" }) {
                    require(current.getInteger(key) == warmCheckpoint.getInteger(key), "Lost rotor timing " + key);
                }
                require(
                    dynamo.get(world)
                        .getStoredEU() == energy,
                    "Generated before required port arrived");
                if (tick - reloadTick < 115) return null;
                TestLabFixtures.machine(world, output.x, output.y, output.z, outputId);
                since = tick;
                phase = 3;
            }
            case 3 -> {
                require(
                    controller.isAllowedToWork() && efficiency(snapshot()) >= efficiency(warmCheckpoint),
                    "Rebinding the output lost the work switch or efficiency");
                require(tick - since <= 24, "Output port returned but machine did not form");
                if (machine().mMachine && tick - since >= 22) {
                    formedTick = tick;
                    retainedEfficiency = efficiency(snapshot());
                    // Mega may settle its one previously paid tick after formation. All subsequent ticks need fuel.
                    energy = dynamo.get(world)
                        .getStoredEU();
                    phase = 4;
                }
            }
            case 4 -> {
                require(
                    controller.isAllowedToWork() && efficiency(snapshot()) == retainedEfficiency,
                    "Lost efficiency waiting for steam after formation");
                require(
                    dynamo.get(world)
                        .getStoredEU() == energy,
                    "Generated new energy with an empty steam tank");
                if (tick - formedTick < 25) return null;
                TestLabSupply.add(world, input.get(world), steam);
                since = tick;
                phase = 5;
            }
            case 5 -> {
                require(controller.isAllowedToWork() && tick - since <= 38, "No automatic restart with real steam");
                if (dynamo.get(world)
                    .getStoredEU() <= energy) return null;
                require(efficiency(snapshot()) >= retainedEfficiency, "The resumed turbine lost heat");
                cutFuel(world);
                since = tick;
                phase = 6;
            }
            case 6 -> {
                require(tick - since < 65, "Ordinary fuel starvation did not restore normal efficiency decay");
                if (efficiency(snapshot()) >= retainedEfficiency) return null;
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("nativeControllerChunkUnloadedAndReloaded", true);
                details.put("requiredPortWithheldTicks", 115);
                details.put("formedButFuelWithheldTicks", 25);
                details.put("savedEfficiency", efficiency(warmCheckpoint));
                details.put("efficiencyRetainedUntilSteam", retainedEfficiency);
                details.put("efficiencyAfterOrdinaryFuelLoss", efficiency(snapshot()));
                return new TestLabReport.Result(
                    TestLabReport.Status.PASS,
                    "Native warm reload preserved work and efficiency then resumed on real steam",
                    details);
            }
            default -> throw new IllegalStateException("Unknown turbine reload phase " + phase);
        }
        return null;
    }

    void stop(WorldServer world) {
        if (ticket != null) restoreChunk(world);
        if (phase >= 1) {
            site.get(world)
                .disableWorking();
            TestLabSupply.remove(input.get(world));
        }
    }

    private void restoreChunk(WorldServer world) {
        ForgeChunkManager.forceChunk(ticket, controllerChunk);
        ticket = null;
        TestLabFixtures.requireChunk(world, controllerChunk.chunkXPos, controllerChunk.chunkZPos);
    }

    private void cutFuel(WorldServer world) {
        IGregTechTileEntity port = input.get(world);
        TestLabSupply.remove(port);
        ((MTEHatchInput) port.getMetaTileEntity()).setFillableStack(null);
        port.markDirty();
    }

    private MTEMultiBlockBase machine() {
        return (MTEMultiBlockBase) controller.getMetaTileEntity();
    }

    private NBTTagCompound snapshot() {
        NBTTagCompound nbt = new NBTTagCompound();
        machine().saveNBTData(nbt);
        return nbt;
    }

    private int efficiency(NBTTagCompound nbt) {
        return nbt.getInteger(heavy ? "turbineEfficiency" : "mEfficiency");
    }

    private static IGregTechTileEntity hatch(List<IGregTechTileEntity> ports, Class<?> type) {
        for (IGregTechTileEntity port : ports) if (type.isInstance(port.getMetaTileEntity())) return port;
        throw new IllegalStateException("Missing turbine fixture port " + type.getSimpleName());
    }

    private static final class Position {

        private final int x, y, z;

        Position(IGregTechTileEntity tile) {
            x = tile.getXCoord();
            y = tile.getYCoord();
            z = tile.getZCoord();
        }

        IGregTechTileEntity get(WorldServer world) {
            return (IGregTechTileEntity) world.getTileEntity(x, y, z);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
