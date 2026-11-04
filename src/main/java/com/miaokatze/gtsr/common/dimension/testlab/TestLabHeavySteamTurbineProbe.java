package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.MTEHeavySteamTurbine;
import com.miaokatze.gtsr.common.machine.turbine.HeavySteamTurbineCycle;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes.SteamType;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicTank;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTModHandler;

/** Natural world ticks verify conversion, then explicitly seeded real dynamo buffers verify backpressure. */
final class TestLabHeavySteamTurbineProbe {

    private final MTEHeavySteamTurbine machine;
    private final List<IGregTechTileEntity> ports;
    private final int power, efficiency;
    private int phase, started;
    private long energyBefore, steamBefore, waterBefore;
    private NBTTagCompound before;
    private String productionEvidence;

    TestLabHeavySteamTurbineProbe(MTEHeavySteamTurbine machine, List<IGregTechTileEntity> ports, int tick) {
        this.machine = machine;
        this.ports = ports;
        power = HeavySteamTurbineCycle.output(machine.mMaterialTier);
        efficiency = HeavySteamTurbineCycle.efficiency(machine.mMaterialTier);
        snapshot(tick);
    }

    String tick(int tick) {
        if (phase == 0) {
            if (tick - started < 20) return null;
            NBTTagCompound after = state();
            long energy = energy() - energyBefore;
            long steam = steamBalance() - steamBefore
                - (after.getInteger("paidSteam") - before.getInteger("paidSteam"));
            require(energy == (long) power * (tick - started), "native per-tick output mismatch: " + energy);
            require(
                steam * efficiency
                    == energy * HeavySteamTurbineCycle.ENERGY_UNITS_PER_EU + after.getInteger("energyCredit")
                        - before.getInteger("energyCredit"),
                "native steam energy mismatch");
            long water = water() - waterBefore + after.getInteger("pendingWater") - before.getInteger("pendingWater");
            require(
                water * HeavySteamTurbineCycle.STEAM_PER_WATER + after.getInteger("excessWater")
                    - before.getInteger("excessWater") == steam,
                "native distilled-water conservation mismatch");
            productionEvidence = "nativeOutput=" + power
                + "EU/t efficiency="
                + efficiency
                + "% measuredEnergy="
                + energy
                + "EU steam="
                + steam
                + "L sample="
                + (tick - started)
                + "ticks";
            fillDynamo(0);
            snapshot(tick);
            phase = 1;
            return null;
        }
        if (phase == 1 || phase == 2) {
            if (tick - started < 5) return null;
            require(energy() == energyBefore, "blocked dynamo changed or overflowed");
            require(steamBalance() == steamBefore, "steam consumed with blocked dynamo");
            require(
                machine.getBaseMetaTileEntity()
                    .isAllowedToWork(),
                "backpressure disabled automatic restart");
            if (phase == 1) {
                fillDynamo(power - 1);
                snapshot(tick);
                phase = 2;
            } else {
                for (IGregTechTileEntity port : ports)
                    if (port.getMetaTileEntity() instanceof MTEHatchDynamo) ((BaseMetaTileEntity) port).setStoredEU(0);
                snapshot(tick);
                phase = 3;
            }
            return null;
        }
        if (energy() > 0 && steamBalance() > steamBefore) {
            return productionEvidence + " fullDynamo=PASS insufficientRemainingCapacity=PASS"
                + " blockedSteamWithdrawal=0 automaticRestart=PASS manualMachineTicks=0"
                + " bufferFixture=real_dynamo_EU_seeded_only_after_production_measurement";
        }
        require(tick - started <= 150, "native turbine failed to restart after dynamo emptied");
        return null;
    }

    private void snapshot(int tick) {
        started = tick;
        energyBefore = energy();
        steamBefore = steamBalance();
        waterBefore = water();
        before = state();
    }

    private NBTTagCompound state() {
        NBTTagCompound nbt = new NBTTagCompound();
        machine.saveNBTData(nbt);
        return nbt;
    }

    private long energy() {
        long total = 0;
        for (IGregTechTileEntity port : ports)
            if (port.getMetaTileEntity() instanceof MTEHatchDynamo) total += port.getStoredEU();
        return total;
    }

    private long steamBalance() {
        long total = 0;
        for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchInput) {
            FluidStack fluid = ((MTEBasicTank) port.getMetaTileEntity()).getFluid();
            total += TestLabSupply.replenished(port);
            if (SteamTurbineSteamTypes.classifyFluid(fluid) == SteamType.STEAM) total -= fluid.amount;
        }
        return total;
    }

    private long water() {
        long total = 0;
        for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchOutput hatch) {
            FluidStack fluid = hatch.getFluid();
            if (fluid != null && fluid.isFluidEqual(GTModHandler.getDistilledWater(1))) total += fluid.amount;
        }
        return total;
    }

    private void fillDynamo(int remainingRoom) {
        boolean first = true;
        for (IGregTechTileEntity port : ports) if (port.getMetaTileEntity() instanceof MTEHatchDynamo) {
            ((BaseMetaTileEntity) port).setStoredEU(port.getEUCapacity() - (first ? remainingRoom : 0));
            first = false;
        }
        require(!first, "native dynamo fixture missing");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
