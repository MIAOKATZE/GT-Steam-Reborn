package com.miaokatze.gtsr.mixin;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility;

/** Vanilla display lists become visible synchronously when updateRenderer returns. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMachineVisualMixin {

    @Shadow
    public World worldObj;
    @Shadow
    public int posX, posY, posZ;
    @Shadow
    public boolean needsUpdate;
    private boolean gtsr$buildingMachineMasks;

    @Inject(method = "updateRenderer", at = @At("HEAD"))
    private void gtsr$beginMachineMasks(EntityLivingBase viewer, CallbackInfo callback) {
        gtsr$buildingMachineMasks = needsUpdate;
        if (gtsr$buildingMachineMasks) {
            MachineVisualVisibility.beginSection(this, worldObj, posX, posY, posZ);
            MachineVisualVisibility.beginBuild(this, worldObj);
        }
    }

    @Inject(method = "updateRenderer", at = @At("RETURN"))
    private void gtsr$commitMachineMasks(EntityLivingBase viewer, CallbackInfo callback) {
        if (!gtsr$buildingMachineMasks) return;
        MachineVisualVisibility.finishBuild(this, this);
        MachineVisualVisibility.endBuild();
        MachineVisualVisibility.uploaded(this);
    }
}
