// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.machinevisual;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;

import gregtech.api.render.RenderOverlay;
import gregtech.common.blocks.BlockCasings2;
import gregtech.common.blocks.BlockCasings4;
import gregtech.common.render.GTRendererCasing;

/** GT's own atlas/ISBRH draws the original full blocks; only the display matrix moves. */
public final class MachineVisualRenderer {

    private MachineVisualRenderer() {}

    public static void render(MachineVisualHost host, double x, double y, double z, float partialTick) {
        MachineVisualClient.observe(host, 0);
        MachineVisualClient.Entry entry = MachineVisualClient.entry(host);
        if (entry == null) return;
        if (entry.facing != host.getExtendedFacing()) {
            MachineVisualVisibility.remove(entry);
            return;
        }
        if (entry.retired) return;
        entry.lastRenderFrame = MachineVisualClient.renderFrame();
        boolean working = MachineVisualClient.isWorking(host);
        MachineVisualClient.renderCalls++;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            com.miaokatze.gtsr.client.workeffects.WorkEffectRenderer.render(host, working);
        } finally {
            GL11.glPopMatrix();
        }
        int ready = MachineVisualVisibility.ready(entry);
        int draw = entry.mask | entry.restoringMask;
        if (draw == 0) {
            clearMesh(entry);
            return;
        }
        boolean animating = MachineVisualClient.isAnimating(host);
        long decorativeTime = animating ? MachineVisualClient.decorativeTime(entry, partialTick) : 0;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();
        MachineVisualVisibility.beginProxy();
        try {
            GL11.glTranslated(x + .5, y + .5, z + .5);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1, 1, 1, 1);
            boolean turbine = host.getClass() == MTEMegaSteamTurbineArray.class;
            // The same lifecycle-owned native lists are used by Critical's stellar meshes.
            // The first native light/material sample belongs to this decorative animation until restoration.
            boolean cached = turbine;
            if (cached) {
                if (entry.meshCache == null) entry.meshCache = new MachineVisualMeshCache();
                entry.meshCache.begin(
                    MinecraftForgeClient.getRenderPass(),
                    Minecraft.getMinecraft().gameSettings.ambientOcclusion);
            }
            for (int index = 0; index < entry.definition.groups.size(); index++) {
                int bit = 1 << index;
                if ((draw & bit) == 0) continue;
                MachineVisualDefinition.Group group = entry.definition.groups.get(index);
                MachineVisualClearance.GroupGeometry geometry = entry.geometry.groups[index];
                MachineVisualVisibility.CellEntry[] binding = entry.boundCells[index];
                GL11.glPushMatrix();
                try {
                    if (animating && (ready & bit) != 0) {
                        double value = group.sample(decorativeTime);
                        if (group.kind.startsWith("rotate_")) {
                            double[] pivot = geometry.pivot, axis = geometry.axis;
                            GL11.glTranslated(pivot[0], pivot[1], pivot[2]);
                            GL11.glRotated(
                                Math.toDegrees(value) * entry.geometry.handedness,
                                axis[0],
                                axis[1],
                                axis[2]);
                            GL11.glTranslated(-pivot[0], -pivot[1], -pivot[2]);
                        } else {
                            GL11.glTranslated(
                                geometry.axis[0] * value,
                                geometry.axis[1] * value,
                                geometry.axis[2] * value);
                        }
                    }
                    if (cached) {
                        if (entry.meshCache
                            .draw(index, entry.meshRevisions[index], () -> drawGroup(entry, binding, turbine)))
                            MachineVisualClient.motionMeshReplays++;
                        else MachineVisualClient.motionMeshCompiles++;
                    } else {
                        if (entry.meshCache != null) entry.meshCache.invalidate(index);
                        drawGroup(entry, binding, turbine);
                    }
                } finally {
                    GL11.glPopMatrix();
                }
            }
        } finally {
            MachineVisualVisibility.endProxy();
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    static void clearMesh(MachineVisualClient.Entry entry) {
        if (entry.meshCache != null) {
            MachineVisualMeshCache owned = entry.meshCache;
            entry.meshCache = null;
            owned.clear();
        }
    }

    private static void drawGroup(MachineVisualClient.Entry entry, MachineVisualVisibility.CellEntry[] binding,
        boolean turbine) {
        RenderBlocks blocks = new RenderBlocks(entry.world);
        blocks.renderAllFaces = true;
        if (turbine && entry.renderAccess == null) entry.renderAccess = new MachineVisualRenderAccess();
        boolean scopedLighting = turbine && entry.renderAccess.begin(entry.world);
        Tessellator tessellator = Tessellator.instance;
        boolean batch = false;
        try {
            for (MachineVisualVisibility.CellEntry cell : binding) {
                int px = cell.x, py = cell.y, pz = cell.z;
                if (!cell.uploaded || !cell.uploadedHidden) continue;
                // Large groups bake only at lifecycle changes. Small groups use the uploaded binding directly;
                // their native renderer owns material/pass handling without another identity scan every frame.
                Block block = cell.block;
                if (turbine && (entry.world.getBlock(px, py, pz) != block
                    || entry.world.getBlockMetadata(px, py, pz) != cell.metadata)) continue;
                if (!batch) {
                    tessellator.startDrawingQuads();
                    tessellator.setTranslation(
                        -entry.tile.getXCoord() - .5,
                        -entry.tile.getYCoord() - .5,
                        -entry.tile.getZCoord() - .5);
                    batch = true;
                }
                // Only the sampled native GT casing renderer uses the per-invocation light memo.
                // Other machines, custom blocks, overlays and the tungsten gearbox retain the real World.
                boolean nativeCasing = block.getClass() == BlockCasings2.class
                    || block.getClass() == BlockCasings4.class;
                blocks.blockAccess = scopedLighting && nativeCasing
                    && block.getRenderType() == GTRendererCasing.mRenderID
                    && RenderOverlay.get(entry.world, px, py, pz) == null ? entry.renderAccess : entry.world;
                blocks.renderBlockByRenderType(block, px, py, pz);
                MachineVisualClient.renderedCubes++;
            }
            if (batch) MachineVisualClient.recordSubmission(tessellator.draw(), false);
        } finally {
            tessellator.setTranslation(0, 0, 0);
            if (scopedLighting) entry.renderAccess.end();
        }
    }

}
