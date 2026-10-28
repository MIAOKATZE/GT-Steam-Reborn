package com.miaokatze.gtsr.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.miaokatze.gtsr.client.critical.CriticalNightClient;
import com.miaokatze.miaokatzecore.client.render.MatrixAttributeScope;

/** Fade the original provider sky into a moonless night, reusing Minecraft's native star display list. */
@Mixin(value = RenderGlobal.class, priority = 1100)
public abstract class RenderGlobalCriticalNightMixin {

    @Shadow
    public WorldClient theWorld;
    @Shadow
    private Minecraft mc;
    @Shadow
    private int starGLCallList;

    @WrapMethod(method = "renderSky")
    private void gtsr$starryNight(float partialTicks, Operation<Void> original) {
        float strength = CriticalNightClient.strength(theWorld);
        if (strength < 1F) {
            CriticalNightClient.beginSample(theWorld, false);
            try {
                original.call(partialTicks);
            } finally {
                CriticalNightClient.endSample();
            }
        }
        if (strength <= 0F) return;
        Vec3 night = CriticalNightClient.skyColor(theWorld, mc.renderViewEntity, partialTicks, 1F);
        try (MatrixAttributeScope ignored = new MatrixAttributeScope(GL11.GL_ALL_ATTRIB_BITS)) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f((float) night.xCoord, (float) night.yCoord, (float) night.zCoord, strength);
            // Cover the original sun/moon/custom sky continuously; never replace a provider's handler.
            Tessellator tessellator = Tessellator.instance;
            for (int side = 0; side < 6; side++) {
                GL11.glPushMatrix();
                if (side == 1) GL11.glRotatef(90F, 1F, 0F, 0F);
                if (side == 2) GL11.glRotatef(-90F, 1F, 0F, 0F);
                if (side == 3) GL11.glRotatef(180F, 1F, 0F, 0F);
                if (side == 4) GL11.glRotatef(90F, 0F, 0F, 1F);
                if (side == 5) GL11.glRotatef(-90F, 0F, 0F, 1F);
                tessellator.startDrawingQuads();
                tessellator.addVertex(-100D, -100D, -100D);
                tessellator.addVertex(-100D, -100D, 100D);
                tessellator.addVertex(100D, -100D, 100D);
                tessellator.addVertex(100D, -100D, -100D);
                tessellator.draw();
                GL11.glPopMatrix();
            }
            GL11.glRotatef(-90F, 0F, 1F, 0F);
            GL11.glRotatef(180F, 1F, 0F, 0F);
            float stars = .5F * strength * (1F - theWorld.getRainStrength(partialTicks));
            GL11.glColor4f(stars, stars, stars, 1F);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glCallList(starGLCallList);
        }
    }

    @Inject(method = { "renderClouds", "renderCloudsFancy" }, at = @At("HEAD"), cancellable = true, require = 2)
    private void gtsr$moonless(float partialTicks, CallbackInfo ci) {
        if (CriticalNightClient.strength(theWorld) > 0F) ci.cancel();
    }
}
