package com.miaokatze.gtsr.common.dimension.testlab;

import net.minecraft.entity.Entity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Vec3;
import net.minecraft.world.chunk.Chunk;

import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldProviderBase;

/** Uses the existing world-generation and spawn isolation guards. */
public final class TestLabWorldProvider extends GTSRWorldProviderBase {

    @Override
    public Vec3 getFogColor(float ticks, float pass) {
        return Vec3.createVectorHelper(0.7, 0.8, 0.9);
    }

    @Override
    public Vec3 getSkyColor(Entity entity, float ticks) {
        return Vec3.createVectorHelper(0.55, 0.7, 0.9);
    }

    @Override
    protected Float fixedCelestialAngle() {
        return 0.0F;
    }

    @Override
    public ChunkCoordinates getEntrancePortalLocation() {
        return new ChunkCoordinates(0, 65, 0);
    }

    @Override
    public void updateWeather() {
        // WorldServerMulti shares the overworld's WorldInfo. Leave its weather untouched;
        // the strength setters are client-only in the 1.7.10 dedicated-server distribution.
    }

    @Override
    public boolean canDoRainSnowIce(Chunk chunk) {
        return false;
    }

    @Override
    public boolean canDoLightning(Chunk chunk) {
        return false;
    }
}
