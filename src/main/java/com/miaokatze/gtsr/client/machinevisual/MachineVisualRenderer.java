// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.machinevisual;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;

/** GT's own atlas/ISBRH draws the original full blocks; only the display matrix moves. */
public final class MachineVisualRenderer {

    private MachineVisualRenderer() {}

    public static void render(MachineVisualHost host, double x, double y, double z, float partialTick) {
        com.miaokatze.gtsr.client.render.ClientRenderFrame.register();
        MachineVisualClient.observe(host, 0);
        MachineVisualClient.Entry entry = MachineVisualClient.entry(host);
        if (entry == null) return;
        if (entry.facing != host.visualFacing()) {
            MachineVisualVisibility.remove(entry);
            return;
        }
        if (entry.retired) return;
        entry.lastRenderFrame = MachineVisualClient.renderFrame();
        MachineVisualClient.renderCalls++;
        if (MachineVisualClient.isWorking(host))
            com.miaokatze.gtsr.client.workeffects.WorkEffectRenderer.render(host, x, y, z);
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
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        MachineVisualVisibility.beginProxy();
        try {
            GL11.glTranslated(x + .5, y + .5, z + .5);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            boolean transparentPass = MinecraftForgeClient.getRenderPass() == 1;
            GL11.glDepthMask(!transparentPass);
            if (transparentPass) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            } else GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1, 1, 1, 1);
            if (entry.meshCache == null) entry.meshCache = new MachineVisualMeshCache();
            entry.meshCache
                .begin(MinecraftForgeClient.getRenderPass(), Minecraft.getMinecraft().gameSettings.ambientOcclusion);
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
                    if (entry.meshCache.draw(index, entry.meshRevisions[index], () -> drawGroup(entry, binding)))
                        MachineVisualClient.motionMeshReplays++;
                    else MachineVisualClient.motionMeshCompiles++;
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

    private static void drawGroup(MachineVisualClient.Entry entry, MachineVisualVisibility.CellEntry[] binding) {
        RenderBlocks blocks = new NativeGroupBlocks(entry, binding);
        blocks.renderAllFaces = true;
        Tessellator tessellator = Tessellator.instance;
        boolean batch = false;
        try {
            for (MachineVisualVisibility.CellEntry cell : binding) {
                int px = cell.x, py = cell.y, pz = cell.z;
                if (!cell.uploaded || !cell.uploadedHidden) continue;
                Block block = cell.block;
                if (!block.canRenderInPass(Math.max(0, MinecraftForgeClient.getRenderPass()))) continue;
                if (!batch) {
                    tessellator.startDrawingQuads();
                    tessellator.setTranslation(
                        -entry.tile.getXCoord() - .5,
                        -entry.tile.getYCoord() - .5,
                        -entry.tile.getZCoord() - .5);
                    batch = true;
                }
                blocks.renderBlockByRenderType(block, px, py, pz);
                MachineVisualClient.renderedCubes++;
            }
            if (batch) MachineVisualClient.recordSubmission(tessellator.draw(), false);
        } finally {
            tessellator.setTranslation(0, 0, 0);
        }
    }

    /** Same-group face omission from StructureVBO, retaining native world positions and ISBRH materials. */
    private static final class NativeGroupBlocks extends RenderBlocks {

        private final java.util.Set<String> opaque = new java.util.HashSet<>();

        NativeGroupBlocks(MachineVisualClient.Entry entry, MachineVisualVisibility.CellEntry[] binding) {
            super(entry.world);
            for (MachineVisualVisibility.CellEntry cell : binding)
                if (cell.uploaded && cell.uploadedHidden && cell.block.isOpaqueCube()) opaque.add(cell.key);
        }

        private boolean covered(double x, double y, double z) {
            return opaque.contains((int) x + ":" + (int) y + ":" + (int) z);
        }

        @Override
        public void renderFaceYNeg(Block block, double x, double y, double z, IIcon icon) {
            if (renderMinY > 0 || !covered(x, y - 1, z)) super.renderFaceYNeg(block, x, y, z, icon);
        }

        @Override
        public void renderFaceYPos(Block block, double x, double y, double z, IIcon icon) {
            if (renderMaxY < 1 || !covered(x, y + 1, z)) super.renderFaceYPos(block, x, y, z, icon);
        }

        @Override
        public void renderFaceZNeg(Block block, double x, double y, double z, IIcon icon) {
            if (renderMinZ > 0 || !covered(x, y, z - 1)) super.renderFaceZNeg(block, x, y, z, icon);
        }

        @Override
        public void renderFaceZPos(Block block, double x, double y, double z, IIcon icon) {
            if (renderMaxZ < 1 || !covered(x, y, z + 1)) super.renderFaceZPos(block, x, y, z, icon);
        }

        @Override
        public void renderFaceXNeg(Block block, double x, double y, double z, IIcon icon) {
            if (renderMinX > 0 || !covered(x - 1, y, z)) super.renderFaceXNeg(block, x, y, z, icon);
        }

        @Override
        public void renderFaceXPos(Block block, double x, double y, double z, IIcon icon) {
            if (renderMaxX < 1 || !covered(x + 1, y, z)) super.renderFaceXPos(block, x, y, z, icon);
        }
    }

}
