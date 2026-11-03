package com.miaokatze.gtsr.client.machinevisual;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;

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
        if (draw == 0) return;
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
            RenderBlocks blocks = new RenderBlocks(entry.world);
            blocks.renderAllFaces = true;
            for (int index = 0; index < entry.definition.groups.size(); index++) {
                int bit = 1 << index;
                if ((draw & bit) == 0) continue;
                MachineVisualDefinition.Group group = entry.definition.groups.get(index);
                MachineVisualClearance.GroupGeometry geometry = entry.geometry.groups[index];
                GL11.glPushMatrix();
                Tessellator tessellator = Tessellator.instance;
                boolean batch = false;
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
                    for (int i = 0; i < group.members.size(); i++) {
                        MachineVisualDefinition.Cell member = group.members.get(i);
                        int px = entry.originX + geometry.members[i * 3],
                            py = entry.originY + geometry.members[i * 3 + 1],
                            pz = entry.originZ + geometry.members[i * 3 + 2];
                        if (!MachineVisualVisibility.uploadedHidden(entry, px, py, pz)) continue;
                        Block block = entry.world.getBlock(px, py, pz);
                        if (!member.matches(block, entry.world.getBlockMetadata(px, py, pz))) continue;
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
                    GL11.glPopMatrix();
                }
            }
        } finally {
            MachineVisualVisibility.endProxy();
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
