package com.miaokatze.gtsr.common.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 奇点 FX 粒子管道入口（facade）。
 * v1.10.31 起不再自管列表/更新/渲染：全部 FX（电弧/光片/辉光）直接加入 vanilla EffectRenderer，
 * 渲染层级与吸积盘粒子一致——在方块 pass 0 之后、pass 1（染色玻璃/水等半透明面）之前渲染，
 * 深度测试开启：普通方块遮挡、染色玻璃不遮挡。粒子生命周期由 EntityFX（onUpdate/isDead）管理。
 */
@SideOnly(Side.CLIENT)
public class GTSRFXEngine {

    private static final GTSRFXEngine INSTANCE = new GTSRFXEngine();

    private GTSRFXEngine() {}

    public static GTSRFXEngine instance() {
        return INSTANCE;
    }

    /**
     * 粒子管道入口：加入 vanilla EffectRenderer（含粒子上限与更新/移除管理）。
     */
    public void addEffect(GTSRFXParticle particle) {
        Minecraft.getMinecraft().effectRenderer.addEffect(particle);
    }

    public static void spawnArc(World world, double sx, double sy, double sz, double ex, double ey, double ez,
        float width, int type, int duration, float multi, int speed, float darkScale) {
        GTSRArcFX arc = GTSRArcFX
            .add(world, sx, sy, sz, ex, ey, ez, System.nanoTime(), width, type, duration, multi, speed);
        arc.setDarkScale(darkScale);
    }

    public static void spawnParticle(GTSRFXParticle particle) {
        instance().addEffect(particle);
    }
}
