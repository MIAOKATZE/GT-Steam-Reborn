package com.miaokatze.gtsr.compat.gtnl;

import java.util.ArrayList;

import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtsr.api.compat.ICoolingHatch;
import com.miaokatze.gtsr.api.compat.ICoolingHatchHolder;
import com.miaokatze.gtsr.api.compat.IPressureSteamCoolingHatch;
import com.miaokatze.gtsr.api.compat.SteamCoolingSupport;

/** Executes the production cooling ledger. Only output tank capacity is controlled by this fixture. */
public final class GtnlCoolingContract {

    private GtnlCoolingContract() {}

    public static void main(String[] args) throws Exception {
        if (!GtnlNativeBootstrap.enter(GtnlCoolingContract.class.getName(), args)) return;
        Holder holder = new Holder();
        Tank first = new Tank(100), second = new Tank(200);
        holder.pressure.add(first);
        holder.pressure.add(second);
        SteamCoolingSupport.pushCoolingProducts(holder, 400, true);
        equal(100, first.steam, "first pressure tank filled once");
        equal(200, second.steam, "second pressure tank gets only remainder");
        equal(100, holder.pending, "400 physical liters return exactly 400 liters including pending");

        Tank water = new Tank(2);
        holder.cooling.add(water);
        SteamCoolingSupport.pushCoolingProducts(holder, 159, false);
        equal(0, water.water, "159 liters cannot produce one liter of water");
        SteamCoolingSupport.pushCoolingProducts(holder, 162, false);
        equal(2, water.water, "321 liters produce two liters of water");
        equal(1, holder.accumulated, "ordinary fractional remainder retained");
        SteamCoolingSupport.pushCoolingProducts(holder, 320, false);
        equal(321, holder.accumulated, "full water tank retains unpaid output and fractional remainder");

        NBTTagCompound tag = new NBTTagCompound();
        SteamCoolingSupport.saveNBT(holder, tag);
        Holder loaded = new Holder();
        SteamCoolingSupport.loadNBT(loaded, tag);
        equal(100, loaded.pending, "pressure remainder survives NBT reload");
        equal(321, loaded.accumulated, "ordinary remainder survives NBT reload");
        Tank freshPressure = new Tank(1000), freshWater = new Tank(1000);
        loaded.pressure.add(freshPressure);
        loaded.cooling.add(freshWater);
        SteamCoolingSupport.pushCoolingProducts(loaded, 1, true);
        SteamCoolingSupport.pushCoolingProducts(loaded, 159, false);
        equal(101, freshPressure.steam, "reloaded pending is flushed once with next physical payment");
        equal(3, freshWater.water, "reloaded ordinary ledger keeps exact 160:1 ratio");
        equal(0, loaded.pending, "flushed pressure ledger empty");
        equal(0, loaded.accumulated, "flushed ordinary ledger empty");

        loaded.pending = 7;
        loaded.accumulated = 13;
        SteamCoolingSupport.clearHatches(loaded);
        equal(0, loaded.pressure.size(), "structure rebuild drops pressure hatch references");
        equal(0, loaded.cooling.size(), "structure rebuild drops ordinary hatch references");
        equal(7, loaded.pending, "structure rebuild keeps pressure ledger");
        equal(13, loaded.accumulated, "structure rebuild keeps ordinary ledger");

        NBTTagCompound old = new NBTTagCompound();
        old.setInteger("gtsr.accumulatedSteam", 159);
        SteamCoolingSupport.loadNBT(loaded, old);
        equal(159, loaded.accumulated, "legacy int ordinary ledger remains readable");
        equal(0, loaded.pending, "legacy missing pressure ledger defaults to zero");
        System.out.println("PASS production cooling: physical ratios, sequential filling, full tanks, NBT and rebuild");
    }

    private static void equal(long expected, long actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
    }

    static final class Tank implements IPressureSteamCoolingHatch {

        private final int capacity;
        int steam;
        int water;

        Tank(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void updateTexture(int casing) {}

        @Override
        public int pushCoolingSteam(int amount) {
            int accepted = Math.min(amount, capacity - steam);
            steam += accepted;
            return accepted;
        }

        @Override
        public int pushCoolingWater(int amount) {
            int accepted = Math.min(amount, capacity - water);
            water += accepted;
            return accepted;
        }
    }

    static final class Holder implements ICoolingHatchHolder {

        final ArrayList<ICoolingHatch> cooling = new ArrayList<>();
        final ArrayList<IPressureSteamCoolingHatch> pressure = new ArrayList<>();
        long accumulated;
        long pending;

        @Override
        public ArrayList<ICoolingHatch> gtsr$getCoolingHatches() {
            return cooling;
        }

        @Override
        public ArrayList<IPressureSteamCoolingHatch> gtsr$getPressureHatches() {
            return pressure;
        }

        @Override
        public long gtsr$getAccumulatedSteam() {
            return accumulated;
        }

        @Override
        public void gtsr$setAccumulatedSteam(long value) {
            accumulated = value;
        }

        @Override
        public long gtsr$getPendingSuperheatedSteam() {
            return pending;
        }

        @Override
        public void gtsr$setPendingSuperheatedSteam(long value) {
            pending = value;
        }
    }
}
