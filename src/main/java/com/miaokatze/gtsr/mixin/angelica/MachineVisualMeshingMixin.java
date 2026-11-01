package com.miaokatze.gtsr.mixin.angelica;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaBuiltRenderSectionData;
import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility;

/** Retains the exact per-output mask through both worker and deferred-main-thread block draws. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuilderMeshingTask", remap = false)
public abstract class MachineVisualMeshingMixin {

    @Shadow
    @Final
    protected RenderSection render;

    @Shadow
    protected abstract IBlockAccess getBlockAccess();

    @Inject(
        method = "execute(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuilderMeshingTask;onEnterExecute()V",
            shift = At.Shift.AFTER),
        require = 1)
    private void gtsr$captureSection(ChunkBuildContext context, CancellationToken cancellation,
        CallbackInfoReturnable<ChunkBuildOutput> callback) {
        IBlockAccess access = getBlockAccess();
        net.minecraft.world.World world = access instanceof MachineVisualWorldSliceAccess slice ? slice.gtsr$world()
            : access instanceof net.minecraft.world.World real ? real : null;
        MachineVisualVisibility
            .beginSection(this, world, render.getOriginX(), render.getOriginY(), render.getOriginZ());
    }

    @Inject(method = "drawBlock", at = @At("HEAD"), require = 1)
    private void gtsr$beginMachineMasks(Block block, int x, int y, int z, int pass, Tessellator tess,
        RenderBlocks renderer, CallbackInfo callback) {
        if (!MachineVisualVisibility.mayOwn(x, y, z)) return;
        net.minecraft.world.World world = renderer.blockAccess instanceof MachineVisualWorldSliceAccess slice
            ? slice.gtsr$world()
            : renderer.blockAccess instanceof net.minecraft.world.World real ? real : null;
        MachineVisualVisibility.beginBuild(this, world);
    }

    @Inject(method = "drawBlock", at = @At("RETURN"), require = 1)
    private void gtsr$endMachineMasks(Block block, int x, int y, int z, int pass, Tessellator tess,
        RenderBlocks renderer, CallbackInfo callback) {
        MachineVisualVisibility.endBuild();
    }

    @Inject(method = "finalizeSection", at = @At("RETURN"), require = 1)
    private void gtsr$bindMachineOutput(ChunkBuildBuffers buffers, AngelicaBuiltRenderSectionData renderData, int x,
        int y, int z, CallbackInfoReturnable<ChunkBuildOutput> callback) {
        MachineVisualVisibility.finishBuild(this, callback.getReturnValue());
    }
}
