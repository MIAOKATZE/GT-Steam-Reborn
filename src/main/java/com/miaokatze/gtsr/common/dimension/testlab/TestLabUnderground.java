package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.enums.Materials;
import gregtech.api.objects.GTUOFluid;
import gregtech.api.util.GTChunkAssociatedData;
import gregtech.common.UndergroundOil;

/** A real GT underground deposit scoped to one test-world chunk; the original object is restored on stop. */
final class TestLabUnderground {

    private final WorldServer world;
    private final int chunkX, chunkZ;
    private final Object store, original, fixture;
    private final Method setter, amount;
    private boolean restored;

    TestLabUnderground(WorldServer world, int chunkX, int chunkZ) throws Exception {
        TestLabFixtures.attach(world);
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        Field storage = UndergroundOil.class.getDeclaredField("STORAGE");
        storage.setAccessible(true);
        store = storage.get(null);
        Method getter = GTChunkAssociatedData.class
            .getMethod("get", net.minecraft.world.World.class, int.class, int.class);
        original = getter.invoke(store, world, chunkX, chunkZ);
        GTUOFluid vein = new GTUOFluid(new ConfigCategory("gtsr_machine_lab"));
        vein.Registry = Materials.Oil.getFluid(1)
            .getFluid()
            .getName();
        vein.MinAmount = 1;
        vein.MaxAmount = 100000;
        vein.Chance = 1;
        vein.DecreasePerOperationAmount = 5;
        Class<?> dataClass = Class.forName("gregtech.common.UndergroundOil$ChunkData");
        Constructor<?> ctor = dataClass.getDeclaredConstructor(int.class, GTUOFluid.class, String.class, boolean.class);
        ctor.setAccessible(true);
        fixture = ctor.newInstance(100000, vein, "gtsr_test_lab", true);
        setter = GTChunkAssociatedData.class.getDeclaredMethod(
            "set",
            net.minecraft.world.World.class,
            int.class,
            int.class,
            GTChunkAssociatedData.IData.class);
        setter.setAccessible(true);
        setter.invoke(store, world, chunkX, chunkZ, fixture);
        amount = dataClass.getDeclaredMethod("getAmount");
        amount.setAccessible(true);
    }

    long amount() throws Exception {
        return ((Number) amount.invoke(fixture)).longValue();
    }

    FluidStack fluid() {
        return UndergroundOil.undergroundOil(world, chunkX, chunkZ, -1);
    }

    void restore() {
        if (restored) return;
        try {
            setter.invoke(store, world, chunkX, chunkZ, original);
            restored = true;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to restore lab-only underground deposit", failure);
        }
    }
}
