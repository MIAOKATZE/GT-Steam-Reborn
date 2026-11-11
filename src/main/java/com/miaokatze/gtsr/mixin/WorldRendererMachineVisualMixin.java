// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

    @WrapMethod(method = "updateRenderer")
    private void gtsr$compileMachineMasks(EntityLivingBase viewer, Operation<Void> original) {
        if (!needsUpdate) {
            original.call(viewer);
            return;
        }
        boolean complete = false;
        try {
            MachineVisualVisibility.beginSection(this, worldObj, posX, posY, posZ);
            MachineVisualVisibility.beginBuild(this, worldObj);
            original.call(viewer);
            MachineVisualVisibility.finishBuild(this, this);
            MachineVisualVisibility.uploaded(this);
            complete = true;
        } finally {
            MachineVisualVisibility.endBuild();
            if (!complete) MachineVisualVisibility.finishBuild(this, null);
        }
    }
}
