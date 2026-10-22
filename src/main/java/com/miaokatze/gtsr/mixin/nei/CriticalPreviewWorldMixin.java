package com.miaokatze.gtsr.mixin.nei;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.miaokatze.gtsr.client.nei.CriticalPreviewWorld;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

/** An independent revision journal. Never reads or consumes the construction hasChanged flag. */
@Pseudo
@Mixin(targets = "blockrenderer6343.client.world.TrackedDummyWorld", remap = false)
public abstract class CriticalPreviewWorldMixin implements CriticalPreviewWorld {

    @Unique
    private boolean gtsr$criticalPreview;
    @Unique
    private long gtsr$previewRevision;
    @Shadow
    public Long2ObjectMap<Block> blockMap;
    @Shadow
    public Long2IntMap blockMetaMap;
    @Shadow
    public Long2ObjectMap<TileEntity> tileMap;
    @Shadow
    private int visibleYLevel;

    @Override
    public void gtsr$markCriticalPreview() {
        gtsr$criticalPreview = true;
        gtsr$previewRevision++;
    }

    @Override
    public boolean gtsr$isCriticalPreview() {
        return gtsr$criticalPreview;
    }

    @Override
    public long gtsr$previewRevision() {
        return gtsr$previewRevision;
    }

    // The optional jar uses SRG names in production and MCP names in the development classpath.
    @Inject(
        method = { "setBlock(IIILnet/minecraft/block/Block;II)Z", "func_147465_d(IIILnet/minecraft/block/Block;II)Z" },
        at = @At("HEAD"),
        require = 1)
    private void gtsr$blockWrite(int x, int y, int z, Block block, int metadata, int flags,
        CallbackInfoReturnable<Boolean> callback) {
        if (!gtsr$criticalPreview) return;
        long position = CoordinatePacker.pack(x, y, z);
        Block next = block == Blocks.air ? null : block;
        if (blockMap.get(position) != next || blockMetaMap.get(position) != (next == null ? 0 : metadata))
            gtsr$previewRevision++;
    }

    @Inject(method = { "setBlockMetadataWithNotify(IIIII)Z", "func_72921_c(IIIII)Z" }, at = @At("HEAD"), require = 1)
    private void gtsr$metadataWrite(int x, int y, int z, int metadata, int flags,
        CallbackInfoReturnable<Boolean> callback) {
        if (!gtsr$criticalPreview) return;
        long position = CoordinatePacker.pack(x, y, z);
        if (blockMap.containsKey(position) && blockMetaMap.get(position) != metadata) gtsr$previewRevision++;
    }

    @Inject(
        method = { "setTileEntity(IIILnet/minecraft/tileentity/TileEntity;)V",
            "func_147455_a(IIILnet/minecraft/tileentity/TileEntity;)V" },
        at = @At("HEAD"),
        require = 1)
    private void gtsr$tileWrite(int x, int y, int z, TileEntity tile, CallbackInfo callback) {
        if (gtsr$criticalPreview && tileMap.get(CoordinatePacker.pack(x, y, z)) != tile) gtsr$previewRevision++;
    }

    @Inject(method = { "removeTileEntity(III)V", "func_147475_p(III)V" }, at = @At("HEAD"), require = 1)
    private void gtsr$tileRemoved(int x, int y, int z, CallbackInfo callback) {
        if (gtsr$criticalPreview && tileMap.containsKey(CoordinatePacker.pack(x, y, z))) gtsr$previewRevision++;
    }

    @Inject(method = "setVisibleYLevel(I)V", at = @At("HEAD"), require = 1)
    private void gtsr$layerChanged(int level, CallbackInfo callback) {
        if (gtsr$criticalPreview && visibleYLevel != level) gtsr$previewRevision++;
    }
}
