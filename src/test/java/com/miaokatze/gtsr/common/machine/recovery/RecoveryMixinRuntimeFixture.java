// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.recovery;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.miaokatze.gtsr.common.machine.cluster.MTEUnitCrusher;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/** Runs actual transformed GT methods with an isolated tile boundary, not merely state predicates. */
public final class RecoveryMixinRuntimeFixture {

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, RecoveryMixinRuntimeFixture.class.getName(), args)) return;
        Method setup = Class.forName("com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts")
            .getDeclaredMethod("bootstrapGt");
        setup.setAccessible(true);
        setup.invoke(null);
        MTEUnitCrusher machine = new MTEUnitCrusher("transformed_recovery_contract");
        if (!(machine instanceof IGtsrStructureRecovery)) throw new AssertionError("Actual GT mixin bridge absent");
        boolean[] flags = { true, true };
        IGregTechTileEntity tile = (IGregTechTileEntity) Proxy.newProxyInstance(
            RecoveryMixinRuntimeFixture.class.getClassLoader(),
            new Class<?>[] { IGregTechTileEntity.class },
            (proxy, method, values) -> {
                switch (method.getName()) {
                    case "getMetaTileEntity":
                        return machine;
                    case "isServerSide":
                        return true;
                    case "isAllowedToWork":
                        return flags[0];
                    case "disableWorking":
                        flags[0] = false;
                        return null;
                    case "isActive":
                        return flags[1];
                    case "setActive":
                        flags[1] = (Boolean) values[0];
                        return null;
                }
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == short.class) return (short) 0;
                if (type == byte.class) return (byte) 0;
                if (type == long.class) return 0L;
                if (type == float.class) return 0F;
                if (type == double.class) return 0D;
                return null;
            });
        machine.setBaseMetaTileEntity(tile);
        GtsrStructureRecovery.State state = GtsrStructureRecovery.state(machine);
        state.active = false;
        state.phase = GtsrStructureRecovery.State.EXPIRED;
        state.unknown = true;
        machine.mMachine = false;
        machine.mStartUpCheck = -1;
        machine.mUpdate = 20;
        machine.mProgresstime = 37;
        machine.mMaxProgresstime = 100;
        machine.mEfficiency = 9000;
        ItemStack[] paid = { new ItemStack(Items.iron_ingot, 3) };
        machine.mOutputItems = paid;
        machine.stopMachine(ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
        require(
            machine.mProgresstime == 37 && machine.mMaxProgresstime == 100 && machine.mOutputItems == paid,
            "Expired unknown stop destroyed paid work");
        require(flags[0] && !flags[1], "Unknown stop disabled working or remained active");
        require(machine.mEfficiency == 0, "Expired recovery incorrectly kept native efficiency warm");
        machine.onPostTick(tile, 11);
        require(
            machine.mProgresstime == 37 && machine.mMaxProgresstime == 100 && machine.mOutputItems == paid,
            "Native postTick destroyed paused paid work");
        require(flags[0] && !flags[1], "Native activity mirror was not suppressed for unknown pause");
        state.phase = GtsrStructureRecovery.State.DAMAGED;
        state.unknown = false;
        if (!ShutDownReasonRegistry.isRegistered(ShutDownReasonRegistry.STRUCTURE_INCOMPLETE.getID()))
            ShutDownReasonRegistry.register(ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
        machine.stopMachine(ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
        require(
            machine.mProgresstime == 0 && machine.mMaxProgresstime == 0 && machine.mOutputItems == null,
            "Confirmed damage bypassed the real GT stop");
        require(!flags[0], "Confirmed damage bypassed native working-switch shutdown");
        thermalReloads();
        System.out.println(
            "PASS actual transformed GT unknown/damaged stop and postTick assertions=7; isolated tile, no world IO");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void thermalReloads() throws Exception {
        Field allocator = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        allocator.setAccessible(true);
        World world = (World) ((sun.misc.Unsafe) allocator.get(null)).allocateInstance(ThermalWorld.class);
        ((ThermalWorld) world).chunks = (IChunkProvider) Proxy.newProxyInstance(
            RecoveryMixinRuntimeFixture.class.getClassLoader(),
            new Class<?>[] { IChunkProvider.class },
            (proxy, method, values) -> {
                if (method.getName()
                    .equals("chunkExists")) return false;
                throw new AssertionError("Unexpected thermal chunk IO: " + method.getName());
            });
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(world, new net.minecraft.world.WorldProviderSurface());
        String root = "com.miaokatze.gtsr.common.machine.";
        for (String name : new String[] { root + "MTESteamSingularityEntangler",
            root + "MTECriticalSingularityCompressor", root + "tcds.MTEThermoChemicalDenseSteamGenerator",
            root + "MTELargeGeothermalSteamBoiler" }) {
            for (int max : new int[] { 0, 20 }) {
                Constructor<?> ctor = Class.forName(name)
                    .getDeclaredConstructor(String.class);
                ctor.setAccessible(true);
                MTEMultiBlockBase machine = (MTEMultiBlockBase) ctor.newInstance("thermal_reload_contract");
                IGregTechTileEntity tile = (IGregTechTileEntity) Proxy.newProxyInstance(
                    RecoveryMixinRuntimeFixture.class.getClassLoader(),
                    new Class<?>[] { IGregTechTileEntity.class },
                    (proxy, method, values) -> {
                        if (method.getName()
                            .equals("getWorld")) return world;
                        if (method.getName()
                            .equals("getFrontFacing")) return net.minecraftforge.common.util.ForgeDirection.NORTH;
                        if (method.getName()
                            .equals("getMetaTileEntity")) return machine;
                        if (method.getName()
                            .equals("isServerSide")
                            || method.getName()
                                .equals("isAllowedToWork"))
                            return true;
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) return false;
                        if (type == int.class) return 0;
                        if (type == short.class) return (short) 0;
                        if (type == byte.class) return (byte) 0;
                        if (type == long.class) return 0L;
                        if (type == float.class) return 0F;
                        if (type == double.class) return 0D;
                        return null;
                    });
                machine.setBaseMetaTileEntity(tile);
                NBTTagCompound nbt = new NBTTagCompound();
                double heat = name.contains("tcds.") ? 50D : 1D;
                nbt.setDouble("mHeat", heat);
                nbt.setInteger("mMaxProgresstime", max);
                machine.loadNBTData(nbt);
                // Structure matching is deliberately deferred in this detached thermal boundary.
                // Actual world matching and two-JVM persistence are covered by the explicit restart cases.
                machine.mUpdate = 1000;
                for (int tick = 1; tick <= 99; tick++) machine.onPostTick(tile, tick);
                NBTTagCompound after = new NBTTagCompound();
                machine.saveNBTData(after);
                require(after.getDouble("mHeat") == heat, "Reload changed protected heat: " + name + " max=" + max);
                require(
                    machine.mProgresstime == 0 && machine.mMaxProgresstime == max,
                    "Recovery progressed or discarded a paid cycle: " + name);
                require(GtsrStructureRecovery.isRecovering(machine), "Thermal test left its native recovery window");
                System.out.println("PASS transformed thermal reload " + name + " max=" + max + " heat=" + heat);
                GtsrStructureRecovery.State recovery = GtsrStructureRecovery.state(machine);
                recovery.active = false;
                recovery.phase = GtsrStructureRecovery.State.EXPIRED;
                recovery.unknown = true;
                machine.mStartUpCheck = -1;
                machine.mMachine = false;
                machine.onPostTick(tile, 100);
                NBTTagCompound expired = new NBTTagCompound();
                machine.saveNBTData(expired);
                require(expired.getDouble("mHeat") <= heat, "Unknown expired batch heated without work: " + name);
                require(machine.mMaxProgresstime == max, "Expired unknown lost paid cycle: " + name);
                require(((ThermalWorld) world).reads == 0, "Unknown expired visual touched unloaded blocks: " + name);
                if (machine instanceof com.miaokatze.gtsr.common.machine.base.MTESingularityModeMachineBase) {
                    Method consume = com.miaokatze.gtsr.common.machine.base.MTESingularityModeMachineBase.class
                        .getDeclaredMethod("canConsumeSingularity");
                    consume.setAccessible(true);
                    require(
                        !(Boolean) consume.invoke(machine),
                        "Paused paid cycle permits singularity payment: " + name);
                }
                System.out.println("PASS transformed expired unknown cooling/payment gate " + name + " max=" + max);
            }
        }
    }

    /** No chunk storage or IO: only the detached singularity visual cleanup reads this world. */
    public static final class ThermalWorld extends World {

        IChunkProvider chunks;
        int reads;

        private ThermalWorld() {
            super(null, "unused", (net.minecraft.world.WorldProvider) null, null, null);
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            reads++;
            return Blocks.air;
        }

        @Override
        public IChunkProvider getChunkProvider() {
            return chunks;
        }

        @Override
        public net.minecraft.tileentity.TileEntity getTileEntity(int x, int y, int z) {
            return null;
        }

        @Override
        public long getTotalWorldTime() {
            return 0;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int id) {
            return null;
        }
    }
}
