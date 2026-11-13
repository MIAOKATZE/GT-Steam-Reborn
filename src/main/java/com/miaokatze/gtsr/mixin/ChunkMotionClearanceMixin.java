// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.client.machinevisual.MotionSectionChanges;

@Mixin(Chunk.class)
public abstract class ChunkMotionClearanceMixin implements MotionSectionChanges {

    // Unobserved chunks (including integrated server chunks) never allocate the array.
    @Unique
    private long[] gtsr$motionRevisions;

    @Override
    public long gtsr$motionRevision(int section) {
        if (gtsr$motionRevisions == null) gtsr$motionRevisions = new long[16];
        return gtsr$motionRevisions[section];
    }

    @Unique
    private void gtsr$changed(int y) {
        if (gtsr$motionRevisions != null && y >= 0 && y < 256) gtsr$motionRevisions[y >> 4]++;
    }

    @Inject(method = "func_150807_a", at = @At("RETURN"), require = 1)
    private void gtsr$block(int x, int y, int z, Block b, int meta, CallbackInfoReturnable<Boolean> ci) {
        if (gtsr$motionRevisions != null && ci.getReturnValueZ()) gtsr$changed(y);
    }

    @Inject(method = "setBlockMetadata", at = @At("RETURN"), require = 1)
    private void gtsr$metadata(int x, int y, int z, int meta, CallbackInfoReturnable<Boolean> ci) {
        if (gtsr$motionRevisions != null && ci.getReturnValueZ()) gtsr$changed(y);
    }

    @Inject(method = "func_150812_a", at = @At("RETURN"), require = 1)
    private void gtsr$tileAdded(int x, int y, int z, TileEntity te, CallbackInfo ci) {
        gtsr$changed(y);
    }

    @Inject(method = "removeTileEntity", at = @At("RETURN"), require = 1)
    private void gtsr$tileRemoved(int x, int y, int z, CallbackInfo ci) {
        gtsr$changed(y);
    }

    @Inject(method = "fillChunk", at = @At("RETURN"), require = 1)
    private void gtsr$chunkPacket(byte[] data, int sections, int extras, boolean full, CallbackInfo ci) {
        if (gtsr$motionRevisions != null) for (int i = 0; i < 16; i++) {
            if (full || (sections & 1 << i) != 0 || (extras & 1 << i) != 0) gtsr$motionRevisions[i]++;
        }
    }

    @Inject(method = "setStorageArrays", at = @At("RETURN"), require = 1)
    private void gtsr$storage(ExtendedBlockStorage[] storage, CallbackInfo ci) {
        if (gtsr$motionRevisions != null) for (int i = 0; i < 16; i++) gtsr$motionRevisions[i]++;
    }
}
