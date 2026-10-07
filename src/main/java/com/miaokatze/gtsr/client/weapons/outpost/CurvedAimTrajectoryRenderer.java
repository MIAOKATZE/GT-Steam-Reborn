package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.weapons.WeaponKind;
import com.miaokatze.gtsr.common.weapons.WeaponPose;

public final class CurvedAimTrajectoryRenderer {

    private static final double DASH_SEG = .8, DASH_GAP = .4;

    public static Vec3 launchDirection(EntityPlayer player, WeaponKind kind, float partial) {
        return WeaponPose.physical(player, kind, partial).forward;
    }

    public static void draw(EntityPlayer player, WeaponKind kind, float partial) {
        Vec3 muzzle = WeaponPose.muzzle(player, kind, partial), aim = launchDirection(player, kind, partial);
        List<Vec3> points = new ArrayList<>();
        points.add(muzzle);
        double x = muzzle.xCoord, y = muzzle.yCoord, z = muzzle.zCoord, vx = aim.xCoord * kind.projectileSpeed,
            vy = aim.yCoord * kind.projectileSpeed, vz = aim.zCoord * kind.projectileSpeed;
        boolean blocked = false;
        Vec3 eye = WeaponPose.eye(player, partial);
        MovingObjectPosition muzzleBlock = player.worldObj.func_147447_a(
            Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord),
            Vec3.createVectorHelper(muzzle.xCoord, muzzle.yCoord, muzzle.zCoord),
            false,
            true,
            false);
        if (muzzleBlock != null) {
            points.clear();
            points.add(eye);
            points.add(muzzleBlock.hitVec);
            blocked = true;
        }
        for (int i = 0; i < 48 && muzzleBlock == null; i++) {
            Vec3 start = Vec3.createVectorHelper(x, y, z), end = start.addVector(vx, vy, vz);
            MovingObjectPosition hit = player.worldObj.func_147447_a(
                Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord),
                Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord),
                false,
                true,
                false);
            if (hit != null) {
                points.add(hit.hitVec);
                blocked = true;
                break;
            }
            points.add(end);
            x = end.xCoord;
            y = end.yCoord;
            z = end.zCoord;
            vy -= kind.gravity;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(3.5F);
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA_F(blocked ? 1F : .6F, blocked ? .125F : .6F, blocked ? .125F : .6F, .8F);
        double budget = DASH_SEG; // 当前 段/间 剩余世界长度（段满闭合发一对顶点，间满推进下一段起点）
        boolean dash = true;
        double dashStartX = points.get(0).xCoord, dashStartY = points.get(0).yCoord, dashStartZ = points.get(0).zCoord;
        double px = dashStartX, py = dashStartY, pz = dashStartZ;
        for (int i = 1; i < points.size(); i++) {
            double qx = points.get(i).xCoord, qy = points.get(i).yCoord, qz = points.get(i).zCoord;
            double dx = qx - px, dy = qy - py, dz = qz - pz;
            double segLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
            while (segLen > 1.0e-9D) {
                double take = Math.min(segLen, budget);
                double f = take / segLen;
                double nx = px + dx * f, ny = py + dy * f, nz = pz + dz * f;
                budget -= take;
                segLen -= take;
                dx = qx - nx;
                dy = qy - ny;
                dz = qz - nz;
                px = nx;
                py = ny;
                pz = nz;
                if (budget <= 1.0e-9D) {
                    if (dash) {
                        tess.addVertex(dashStartX, dashStartY, dashStartZ);
                        tess.addVertex(nx, ny, nz);
                    } else {
                        dashStartX = nx;
                        dashStartY = ny;
                        dashStartZ = nz;
                    }
                    dash = !dash;
                    budget = dash ? DASH_SEG : DASH_GAP;
                }
            }
        }
        if (dash && budget < DASH_SEG) { // 尾段未闭合：不足整段长的残段也补收最后一对
            tess.addVertex(dashStartX, dashStartY, dashStartZ);
            tess.addVertex(px, py, pz);
        }
        tess.draw();
    }
}
