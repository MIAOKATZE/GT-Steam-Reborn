package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.MTEHeavySteamTurbine;
import com.miaokatze.gtsr.common.machine.turbine.HeavySteamTurbineCycle;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes.SteamType;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTModHandler;
import tectech.thing.metaTileEntity.hatch.MTEHatchDynamoMulti;

/** Bounded native ticks with real hatch feeds and sinks; never invokes a machine tick or fabricates generated EU. */
final class TestLabHeavySteamTurbineProbe {

    private final MTEHeavySteamTurbine machine;
    private final List<IGregTechTileEntity> ports;
    private final boolean heated;
    private final int rated, limit;
    private int phase, started, samples, atLimitTicks, stoppedEfficiency;
    private int pulseTicks, pulseWorkTicks;
    private long energyBefore, steamBefore, coolantBefore, measuredEnergy, consumedSteam;
    private NBTTagCompound before;
    private boolean partialOutputSeen, lowFlowStarted, pulseComplete;

    TestLabHeavySteamTurbineProbe(MTEHeavySteamTurbine machine, List<IGregTechTileEntity> ports, int tick,
        boolean heated) {
        this.machine = machine;
        this.ports = ports;
        for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchInput) {
            TestLabSupply.remove(port);
        }
        this.heated = heated;
        pulseComplete = machine.mMaterialTier != 1;
        rated = HeavySteamTurbineCycle.ratedSteam(machine.mMaterialTier, heated);
        limit = HeavySteamTurbineCycle.efficiencyLimit(machine.mMaterialTier, heated);
        started = tick;
        prepareNextTick();
        snapshot();
    }

    String tick(int tick) {
        if (samples == 0 && tick == started) return null;
        NBTTagCompound after = state();
        long consumed = steamBefore - inputSteam() + before.getInteger("paidSteam") - after.getInteger("paidSteam");
        long generated = energy() - energyBefore;
        long coolant = coolant() - coolantBefore
            + after.getInteger(heated ? "pendingSteam" : "pendingWater")
            - before.getInteger(heated ? "pendingSteam" : "pendingWater");
        int effective = (int) machine.getEntryValue("steam_efficiency");
        require(consumed >= 0 && consumed <= rated, "actual fuel exceeds rated throughput");
        require(
            consumed * effective * (heated ? 2 : 1)
                == generated * 200 + after.getInteger("energyCredit") - before.getInteger("energyCredit"),
            "weighted native energy/credit conservation failed");
        require(
            heated ? coolant == consumed
                : coolant * 160 + after.getInteger("excessWater") - before.getInteger("excessWater") == consumed,
            "native coolant conservation failed");
        require(
            generated == (long) machine.getEntryValue("actual_output"),
            "displayed output differs from real dynamo");
        require(consumed == (long) machine.getEntryValue("steam_rate"), "displayed steam differs from actual debit");
        measuredEnergy += generated;
        consumedSteam += consumed;
        samples++;
        int rotor = after.getInteger("turbineEfficiency");
        int elapsed = tick - started;
        switch (phase) {
            case 0 -> {
                require(consumed <= 16 && effective <= 100 && rotor <= 100, "low steam exceeded its efficiency cap");
                if (!lowFlowStarted && consumed > 0) {
                    require(
                        before.getInteger("turbineEfficiency") == 0 && effective == 0,
                        "bounded low feed did not start from zero efficiency");
                    lowFlowStarted = true;
                }
                require(elapsed <= 150 || lowFlowStarted, "low feed never started after native initialization");
                if (lowFlowStarted && !pulseComplete) {
                    pulseTicks++;
                    if (consumed > 0) pulseWorkTicks++;
                    require(pulseTicks <= 200, "five-tick pulse feed did not accumulate 20 actual working ticks");
                    require(
                        rotor == (pulseWorkTicks < 20 ? 0 : 2),
                        "pulse feed lost cumulative warm-up: workTicks=" + pulseWorkTicks + " rotor=" + rotor);
                    if (pulseWorkTicks == 20) pulseComplete = true;
                }
                if (lowFlowStarted && pulseComplete) require(
                    machine.getBaseMetaTileEntity()
                        .isActive(),
                    "low feed interrupted native running");
                require(elapsed <= 1400, "16L/t failed to warm naturally from zero to 100 percent");
                if (rotor == 100 && elapsed >= 60) {
                    require(measuredEnergy > 0 && consumedSteam > 0, "lowSteamProportionalOutput=FAIL");
                    advance(1, tick);
                }
            }
            case 1 -> {
                require(rotor <= limit, "material/fuel efficiency limit exceeded");
                require(elapsed <= 2400, "rated flow did not reach the efficiency limit");
                if (rotor == limit && ++atLimitTicks >= 21) advance(2, tick);
            }
            case 2 -> {
                require(consumed <= rated / 2 && effective <= 100, "half feed generated above 100 percent efficiency");
                require(rotor == limit, "partial feed destroyed saved rotor efficiency");
                if (elapsed >= 40) {
                    stoppedEfficiency = rotor;
                    advance(3, tick);
                }
            }
            case 3 -> {
                require(consumed == 0 && generated == 0, "full dynamo consumed fuel or overflowed");
                require(
                    machine.getBaseMetaTileEntity()
                        .isAllowedToWork(),
                    "backpressure disabled automatic restart");
                if (elapsed == 20)
                    require(rotor == Math.max(0, stoppedEfficiency - 10), "idle decay is not 10 points/second");
                if (elapsed >= 40) advance(4, tick);
            }
            case 4 -> {
                require(generated <= 1 && effective <= 100, "fragmentary output room exceeded its real capacity");
                partialOutputSeen |= generated == 1 && consumed < rated;
                require(elapsed <= 150, "one-EU output room never resumed proportional generation");
                if (elapsed >= 40 && partialOutputSeen) advance(5, tick);
            }
            case 5 -> {
                // Ordinary steam can finish the already-owned sub-water fraction before becoming blocked.
                if (elapsed > 10) require(consumed == 0, "full liquid output kept consuming fuel");
                require(
                    machine.getBaseMetaTileEntity()
                        .isAllowedToWork(),
                    "coolant blockage disabled automatic restart");
                if (elapsed >= 40) advance(6, tick);
            }
            case 6 -> {
                require(elapsed <= 150, "drained liquid output did not recover automatically");
                if (consumed > 0) {
                    stoppedEfficiency = rotor;
                    advance(7, tick);
                }
            }
            case 7 -> {
                require(consumed == 0, "fuel disappeared after supply was disconnected");
                if (elapsed == 20)
                    require(rotor == Math.max(0, stoppedEfficiency - 10), "fuel starvation decay mismatch");
                if (elapsed >= 40) {
                    stoppedEfficiency = rotor;
                    machine.getBaseMetaTileEntity()
                        .disableWorking();
                    advance(8, tick);
                }
            }
            case 8 -> {
                require(consumed == 0 && generated == 0, "disabled controller kept generating");
                if (elapsed == 20) require(rotor == Math.max(0, stoppedEfficiency - 10), "disabled decay mismatch");
                if (elapsed >= 40) {
                    machine.getBaseMetaTileEntity()
                        .enableWorking();
                    advance(9, tick);
                }
            }
            case 9 -> {
                require(elapsed <= 150, "enabled controller did not recover");
                if (consumed > 0) {
                    verifyNbtRoundTrip(after);
                    return "nativeLowFeed=16L/t lowFlowWarmup=100% fullFlowLimit=" + limit
                        + "% halfFlowEffectiveCap=100% fractionalOutputRoom=1EU PASS fullDynamo=PASS"
                        + " coolantBlockRecovery=PASS starvationDecay=10pp/s disabledDecay=10pp/s"
                        + " automaticRestart=PASS controllerNbtRoundTrip=PASS measuredEnergy="
                        + measuredEnergy
                        + "EU actualSteam="
                        + consumedSteam
                        + "L samples="
                        + samples
                        + " steam="
                        + (heated ? "superheated" : "ordinary")
                        + " pulsedWarmup="
                        + (machine.mMaterialTier == 1 ? "5tick_period_20_actual_workticks_0to2_PASS" : "not_applicable")
                        + " fixture=real_input_hatch_fill_and_output_drain_real_dynamo_buffer_only manualMachineTicks=0";
                }
            }
            default -> throw new IllegalStateException("Unknown heavy probe phase " + phase);
        }
        prepareNextTick();
        snapshot();
        return null;
    }

    private void advance(int nextPhase, int tick) {
        phase = nextPhase;
        started = tick;
    }

    private void prepareNextTick() {
        boolean pulseWindow = phase == 0 && lowFlowStarted && !pulseComplete;
        if (!pulseWindow) {
            for (IGregTechTileEntity port : ports)
                if (port.getMetaTileEntity() instanceof MTEHatchInput hatch) hatch.drain(Integer.MAX_VALUE, true);
        }
        int remaining = phase == 0 ? 16 : phase == 2 ? rated / 2 : phase == 7 ? 0 : rated;
        // Keep a delivered pulse in the real hatch until GT consumes it; only empty hatches receive the next batch.
        if (pulseWindow) remaining = pulseTicks % 5 == 0 && inputSteam() == 0 ? 16 : 0;
        for (IGregTechTileEntity port : ports)
            if (remaining > 0 && port.getMetaTileEntity() instanceof MTEHatchInput hatch) {
                FluidStack fluid = heated ? GTModHandler.getSuperHeatedSteam(remaining)
                    : GTModHandler.getSteam(remaining);
                remaining -= hatch.fill(ForgeDirection.UNKNOWN, fluid, true);
            }
        require(remaining == 0, "real hatch rejected the bounded fuel fixture");
        boolean first = true;
        for (IGregTechTileEntity port : ports) if (isDynamo(port)) {
            if (phase == 3) ((BaseMetaTileEntity) port).setStoredEU(port.getEUCapacity());
            else if (phase == 4) ((BaseMetaTileEntity) port).setStoredEU(port.getEUCapacity() - (first ? 1 : 0));
            else require(port.decreaseStoredEnergyUnits(port.getStoredEU(), false), "native dynamo load refused EU");
            first = false;
        }
        require(!first, "real dynamo fixture missing");
        for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchOutput hatch) {
            if (phase == 5) hatch.fill(
                ForgeDirection.UNKNOWN,
                heated ? Materials.Steam.getGas(Integer.MAX_VALUE) : GTModHandler.getDistilledWater(Integer.MAX_VALUE),
                true);
            else hatch.drain(Integer.MAX_VALUE, true);
        }
    }

    private void snapshot() {
        before = state();
        energyBefore = energy();
        steamBefore = inputSteam();
        coolantBefore = coolant();
    }

    private NBTTagCompound state() {
        NBTTagCompound nbt = new NBTTagCompound();
        machine.saveNBTData(nbt);
        return nbt;
    }

    private long energy() {
        long total = 0;
        for (IGregTechTileEntity port : ports) if (isDynamo(port)) total += port.getStoredEU();
        return total;
    }

    private boolean isDynamo(IGregTechTileEntity port) {
        return port.getMetaTileEntity() instanceof MTEHatchDynamo
            || port.getMetaTileEntity() instanceof MTEHatchDynamoMulti;
    }

    private long inputSteam() {
        long total = 0;
        for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchInput hatch) {
            FluidStack fluid = hatch.getFluid();
            if (SteamTurbineSteamTypes.classifyFluid(fluid) == (heated ? SteamType.SH_STEAM : SteamType.STEAM))
                total += fluid.amount;
        }
        return total;
    }

    private long coolant() {
        long total = 0;
        for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchOutput hatch) {
            FluidStack fluid = hatch.getFluid();
            FluidStack want = heated ? Materials.Steam.getGas(1) : GTModHandler.getDistilledWater(1);
            if (fluid != null && fluid.isFluidEqual(want)) total += fluid.amount;
        }
        return total;
    }

    private void verifyNbtRoundTrip(NBTTagCompound saved) {
        IGregTechTileEntity original = machine.getBaseMetaTileEntity();
        WorldServer world = (WorldServer) original.getWorld();
        int x = original.getXCoord(), y = original.getYCoord() + 16, z = original.getZCoord();
        require(world.isAirBlock(x, y, z), "NBT fixture position is occupied");
        IGregTechTileEntity temporary = TestLabFixtures.machine(world, x, y, z, original.getMetaTileID());
        try {
            MTEHeavySteamTurbine restored = (MTEHeavySteamTurbine) temporary.getMetaTileEntity();
            restored.loadNBTData(saved);
            NBTTagCompound roundTrip = new NBTTagCompound();
            restored.saveNBTData(roundTrip);
            for (String key : new String[] { "energyCredit", "paidSteam", "excessWater", "pendingWater", "pendingSteam",
                "materialTier", "turbineEfficiency", "workingTicks", "idleTicks", "fullFlowTicks" })
                require(saved.getInteger(key) == roundTrip.getInteger(key), "NBT changed " + key);
            require(saved.getBoolean("superheated") == roundTrip.getBoolean("superheated"), "NBT changed fuel type");
        } finally {
            temporary.disableWorking();
            require(
                world.getTileEntity(x, y, z) == temporary && TestLabFixtures.OWNER.equals(temporary.getOwnerUuid()),
                "NBT fixture ownership changed before cleanup");
            world.removeTileEntity(x, y, z);
            world.setBlockToAir(x, y, z);
            require(world.isAirBlock(x, y, z) && world.getTileEntity(x, y, z) == null, "NBT fixture cleanup failed");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Heavy phase=" + phase + " sample=" + samples + " " + message);
    }
}
