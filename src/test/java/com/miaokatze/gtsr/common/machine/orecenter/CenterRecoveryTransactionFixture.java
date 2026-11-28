// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import static com.miaokatze.gtsr.common.machine.orecenter.CenterRecoveryContracts.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

/** Uses real GT energy storage/debit and native output transactions; only world IO remains detached. */
final class CenterRecoveryTransactionFixture {

    static void run() throws Exception {
        energyAcrossTwoHatchesAndReload();
        jointItemFluidOutput();
        result.put(
            "nativeBatchTransactions",
            "two real energy hatches, 10EU/3tick exact debit, shortage/NBT recovery, joint item+fluid atomic output and exactly-once retry");
    }

    private static BaseMetaTileEntity bind(MetaTileEntity hatch, SparseWorld world) {
        BaseMetaTileEntity tile = new BaseMetaTileEntity();
        tile.setWorldObj(world);
        tile.yCoord = 80;
        hatch.setBaseMetaTileEntity(tile);
        check(hatch.isValid(), "transaction fixture uses a live native hatch/base-tile pair");
        return tile;
    }

    private static Method method(String name, Class<?>... parameters) throws Exception {
        Method method = MTEMineralIntegrationCenter.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static void energyAcrossTwoHatchesAndReload() throws Exception {
        SparseWorld world = world();
        MTEMineralIntegrationCenter machine = center(world);
        MTEHatchEnergy first = new MTEHatchEnergy("transaction_energy_a", 1, new String[0], null);
        MTEHatchEnergy second = new MTEHatchEnergy("transaction_energy_b", 1, new String[0], null);
        BaseMetaTileEntity a = bind(first, world), b = bind(second, world);
        machine.mEnergyHatches.add(first);
        machine.mEnergyHatches.add(second);
        set(machine, "totalTicks", 3);
        set(machine, "elapsed", 0);
        set(machine, "energyTotal", 10L);
        set(machine, "steamTotal", 0L);
        Method pay = method("payTick", IGregTechTileEntity.class);
        check(a.setStoredEU(1) && b.setStoredEU(1), "native energy storage accepts fixture supply");
        pay.invoke(machine, machine.getBaseMetaTileEntity());
        check(
            a.getStoredEU() == 1 && b.getStoredEU() == 1 && (Integer) get(machine, "elapsed") == 0,
            "aggregate shortage cannot partially drain either energy hatch or advance progress");
        b.setStoredEU(2);
        pay.invoke(machine, machine.getBaseMetaTileEntity());
        check(
            a.getStoredEU() == 0 && b.getStoredEU() == 0 && (Integer) get(machine, "elapsed") == 1,
            "first 3EU tick drains exactly 1+2 across native hatches and advances once");
        a.setStoredEU(2);
        pay.invoke(machine, machine.getBaseMetaTileEntity());
        check(
            a.getStoredEU() == 2 && b.getStoredEU() == 0 && (Integer) get(machine, "elapsed") == 1,
            "a later brownout retains its partially available EU and paid progress");
        NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        MTEMineralIntegrationCenter loaded = center(world);
        loaded.loadNBTData(saved);
        loaded.mEnergyHatches.add(first);
        loaded.mEnergyHatches.add(second);
        check(
            (Integer) get(loaded, "elapsed") == 1 && (Integer) get(loaded, "totalTicks") == 3
                && (Long) get(loaded, "energyTotal") == 10L,
            "brownout NBT retains the nonzero full bill and exact previously paid progress");
        pay.invoke(loaded, loaded.getBaseMetaTileEntity());
        check(
            a.getStoredEU() == 2 && b.getStoredEU() == 0 && (Integer) get(loaded, "elapsed") == 1,
            "reload does not repay an old tick or partially drain the still insufficient supply");
        b.setStoredEU(1);
        pay.invoke(loaded, loaded.getBaseMetaTileEntity());
        check(
            a.getStoredEU() == 0 && b.getStoredEU() == 0 && (Integer) get(loaded, "elapsed") == 2,
            "restored second tick pays exactly its remaining 3EU");
        a.setStoredEU(4);
        pay.invoke(loaded, loaded.getBaseMetaTileEntity());
        check(
            a.getStoredEU() == 0 && b.getStoredEU() == 0 && (Integer) get(loaded, "elapsed") == 3,
            "last tick pays the 4EU remainder, completing exactly the original 10EU bill");
        CenterClaims.unload(world);
    }

    @SuppressWarnings("unchecked")
    private static void jointItemFluidOutput() throws Exception {
        SparseWorld world = world();
        MTEMineralIntegrationCenter machine = center(world);
        MTEHatchOutputBus bus = new MTEHatchOutputBus("transaction_output_bus", 1, new String[0], null);
        MTEHatchOutput tank = new MTEHatchOutput("transaction_output_tank", 1, new String[0], null);
        bind(bus, world);
        bind(tank, world);
        ((List<MTEHatchOutputBus>) get(machine, "mOutputBusses")).add(bus);
        ((List<MTEHatchOutput>) get(machine, "mOutputHatches")).add(tank);
        check(
            machine.getOutputBusses()
                .size() == 1
                && machine.getOutputHatches()
                    .size() == 1,
            "production output discovery recognizes both native fixture ports");
        List<ItemStack> items = (List<ItemStack>) get(machine, "pendingItems");
        List<FluidStack> fluids = (List<FluidStack>) get(machine, "pendingFluids");
        items.add(new ItemStack(Items.iron_ingot, 3));
        fluids.add(new FluidStack(FluidRegistry.WATER, 1000));
        Method eject = method("ejectBatch", IGregTechTileEntity.class);
        tank.setFillableStack(new FluidStack(FluidRegistry.WATER, tank.getCapacity()));
        eject.invoke(machine, machine.getBaseMetaTileEntity());
        check(
            Arrays.stream(bus.getRealInventory())
                .allMatch(stack -> stack == null) && tank.getFluidAmount() == tank.getCapacity()
                && items.size() == 1
                && items.get(0).stackSize == 3
                && fluids.size() == 1
                && fluids.get(0).amount == 1000,
            "fluid blockage leaves staged item output uncommitted and both pending outputs unchanged");
        tank.setFillableStack(null);
        for (int slot = 0; slot < bus.getRealInventory().length; slot++)
            bus.getRealInventory()[slot] = new ItemStack(Blocks.cobblestone, 64);
        eject.invoke(machine, machine.getBaseMetaTileEntity());
        check(
            tank.getFluidAmount() == 0 && Arrays.stream(bus.getRealInventory())
                .allMatch(
                    stack -> stack != null
                        && stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.cobblestone)
                        && stack.stackSize == 64)
                && items.size() == 1
                && items.get(0).stackSize == 3
                && fluids.size() == 1
                && fluids.get(0).amount == 1000,
            "item blockage leaves native fluid transaction uncommitted and both pending outputs unchanged");
        Arrays.fill(bus.getRealInventory(), null);
        eject.invoke(machine, machine.getBaseMetaTileEntity());
        int iron = Arrays.stream(bus.getRealInventory())
            .filter(stack -> stack != null && stack.getItem() == Items.iron_ingot)
            .mapToInt(stack -> stack.stackSize)
            .sum();
        check(
            iron == 3 && tank.getFluidAmount() == 1000 && items.isEmpty() && fluids.isEmpty(),
            "freeing both outputs commits exactly three items and 1000mB together");
        eject.invoke(machine, machine.getBaseMetaTileEntity());
        int ironAfterRetry = Arrays.stream(bus.getRealInventory())
            .filter(stack -> stack != null && stack.getItem() == Items.iron_ingot)
            .mapToInt(stack -> stack.stackSize)
            .sum();
        check(
            ironAfterRetry == 3 && tank.getFluidAmount() == 1000,
            "a second output retry cannot duplicate either committed product");
        CenterClaims.unload(world);
    }
}
