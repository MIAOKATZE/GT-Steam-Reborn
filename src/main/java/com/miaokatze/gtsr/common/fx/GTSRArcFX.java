package com.miaokatze.gtsr.common.fx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 分形电弧粒子：分段树 + 双趟渲染（外发光 + 核心）。
 * 移植自 Thaumcraft 4.2.3.5 FXLightningBoltCommon/FXLightningBolt，自包含实现。
 */
@SideOnly(Side.CLIENT)
public class GTSRArcFX extends GTSRFXParticle {

    public static final float speed = 3.0F;
    public static final int fadetime = 20;

    private ArrayList<Segment> segments = new ArrayList<Segment>();
    private final GTSRFXVec start;
    private final GTSRFXVec end;
    private final HashMap<Integer, Integer> splitparents = new HashMap<Integer, Integer>();
    private final Random rand;
    private final World world;
    private float multiplier = 1.0F;
    private float length;
    private int numsegments0 = 1;
    private int increment = 1;
    private int type;
    private int numsplits;
    private boolean finalized;
    private float width = 0.03F;
    private float darkScale = 1.0F; // 暗化系数（供消散变黑），1.0 为不暗化

    private GTSRArcFX(World world, double x1, double y1, double z1, double x2, double y2, double z2, long seed,
        float width, int type, int duration, float multi, int speed) {
        super(world, x1, y1, z1);
        this.world = world;
        this.start = new GTSRFXVec(x1, y1, z1);
        this.end = new GTSRFXVec(x2, y2, z2);
        this.rand = new Random(seed);
        this.length = this.end.copy()
            .subtract(this.start)
            .length();
        this.particleMaxAge = duration + this.rand.nextInt(duration) - duration / 2;
        this.multiplier = multi;
        this.increment = speed > 0 ? speed : 1;
        this.width = width;
        this.type = type;
        this.particleAge = -((int) (this.length * 3.0F));
        this.setPosition(this.start.x, this.start.y, this.start.z);
        this.setVelocity(0.0D, 0.0D, 0.0D);
        this.setRBGColorF(1.0F, 1.0F, 1.0F);
        this.segments.add(new Segment(this.start, this.end));
    }

    public static GTSRArcFX create(World world, double sx, double sy, double sz, double ex, double ey, double ez,
        long seed, float width, int type, int duration, float multi, int speed) {
        GTSRArcFX fx = new GTSRArcFX(world, sx, sy, sz, ex, ey, ez, seed, width, type, duration, multi, speed);
        fx.defaultFractal();
        fx.finalizeBolt();
        return fx;
    }

    public static GTSRArcFX add(World world, double sx, double sy, double sz, double ex, double ey, double ez,
        long seed, float width, int type, int duration, float multi, int speed) {
        GTSRArcFX fx = create(world, sx, sy, sz, ex, ey, ez, seed, width, type, duration, multi, speed);
        GTSRFXEngine.instance()
            .addEffect(fx);
        return fx;
    }

    public void setDarkScale(float darkScale) {
        this.darkScale = darkScale;
    }

    private void fractal(int splits, float amount, float splitchance, float splitlength, float splitangle) {
        if (this.finalized) {
            return;
        }
        ArrayList<Segment> oldsegments = this.segments;
        this.segments = new ArrayList<Segment>();
        Segment prev = null;
        Iterator<Segment> iterator = oldsegments.iterator();
        while (iterator.hasNext()) {
            Segment segment = iterator.next();
            prev = segment.prev;
            GTSRFXVec subsegment = segment.diff.copy()
                .multiply(1.0F / (float) splits);
            BoltPoint[] newpoints = new BoltPoint[splits + 1];
            GTSRFXVec startpoint = segment.startpoint.point;
            newpoints[0] = segment.startpoint;
            newpoints[splits] = segment.endpoint;
            for (int i = 1; i < splits; i++) {
                GTSRFXVec next = GTSRFXVec.getPerpendicular(segment.diff)
                    .rotate(this.rand.nextFloat() * 360.0F, segment.diff);
                next.multiply((this.rand.nextFloat() - 0.5F) * amount);
                GTSRFXVec basepoint = startpoint.copy()
                    .add(
                        subsegment.copy()
                            .multiply((float) i));
                newpoints[i] = new BoltPoint(basepoint, next);
            }
            for (int i = 0; i < splits; i++) {
                Segment seg = new Segment(
                    newpoints[i],
                    newpoints[i + 1],
                    segment.light,
                    segment.segmentno * splits + i,
                    segment.splitno);
                seg.prev = prev;
                if (prev != null) {
                    prev.next = seg;
                }
                if (i != 0 && this.rand.nextFloat() < splitchance) {
                    GTSRFXVec splitrot = GTSRFXVec.xCrossProduct(seg.diff)
                        .rotate(this.rand.nextFloat() * 360.0F, seg.diff);
                    GTSRFXVec diff = seg.diff.copy()
                        .rotate((this.rand.nextFloat() * 0.66F + 0.33F) * splitangle, splitrot)
                        .multiply(splitlength);
                    this.numsplits++;
                    this.splitparents.put(this.numsplits, seg.splitno);
                    Segment split = new Segment(
                        newpoints[i],
                        new BoltPoint(
                            newpoints[i + 1].basepoint,
                            newpoints[i + 1].offsetvec.copy()
                                .add(diff)),
                        segment.light / 2.0F,
                        seg.segmentno,
                        this.numsplits);
                    split.prev = prev;
                    this.segments.add(split);
                }
                prev = seg;
                this.segments.add(seg);
            }
            if (segment.next != null) {
                segment.next.prev = prev;
            }
        }
        this.numsegments0 *= splits;
    }

    private void defaultFractal() {
        this.fractal(2, this.length * this.multiplier / 8.0F, 0.7F, 0.1F, 45.0F);
        this.fractal(2, this.length * this.multiplier / 12.0F, 0.5F, 0.1F, 50.0F);
        this.fractal(2, this.length * this.multiplier / 17.0F, 0.5F, 0.1F, 55.0F);
        this.fractal(2, this.length * this.multiplier / 23.0F, 0.5F, 0.1F, 60.0F);
        this.fractal(2, this.length * this.multiplier / 30.0F, 0.0F, 0.0F, 0.0F);
        this.fractal(2, this.length * this.multiplier / 34.0F, 0.0F, 0.0F, 0.0F);
        this.fractal(2, this.length * this.multiplier / 40.0F, 0.0F, 0.0F, 0.0F);
    }

    private void calculateCollisionAndDiffs() {
        HashMap<Integer, Integer> lastactivesegment = new HashMap<Integer, Integer>();
        Collections.sort(this.segments, new SegmentSorter());
        int lastsplitcalc = 0;
        int lastactiveseg = 0;
        Segment segment = null;
        for (Iterator<Segment> iterator = this.segments.iterator(); iterator
            .hasNext(); lastactiveseg = segment.segmentno) {
            segment = iterator.next();
            if (segment.splitno > lastsplitcalc) {
                lastactivesegment.put(lastsplitcalc, lastactiveseg);
                lastsplitcalc = segment.splitno;
                lastactiveseg = lastactivesegment.get(this.splitparents.get(segment.splitno));
            }
        }
        lastactivesegment.put(lastsplitcalc, lastactiveseg);
        lastsplitcalc = 0;
        lastactiveseg = lastactivesegment.get(0);
        for (Iterator<Segment> iterator = this.segments.iterator(); iterator.hasNext(); segment.calcEndDiffs()) {
            segment = iterator.next();
            if (lastsplitcalc != segment.splitno) {
                lastsplitcalc = segment.splitno;
                lastactiveseg = lastactivesegment.get(segment.splitno);
            }
            if (segment.segmentno > lastactiveseg) {
                iterator.remove();
            }
        }
    }

    private void finalizeBolt() {
        if (!this.finalized) {
            this.finalized = true;
            this.calculateCollisionAndDiffs();
            Collections.sort(this.segments, new SegmentLightSorter());
        }
    }

    @Override
    public void onUpdate() {
        this.particleAge += this.increment;
        if (this.particleAge > this.particleMaxAge) {
            this.particleAge = this.particleMaxAge;
        }
        if (this.particleAge >= this.particleMaxAge) {
            this.setDead();
        }
    }

    private static GTSRFXVec getRelativeViewVector(GTSRFXVec pos) {
        EntityClientPlayerMP renderentity = Minecraft.getMinecraft().thePlayer;
        return new GTSRFXVec(
            (double) ((float) renderentity.posX - pos.x),
            (double) ((float) renderentity.posY - pos.y),
            (double) ((float) renderentity.posZ - pos.z));
    }

    private void renderBolt(Tessellator tessellator, float partialframe, float cosyaw, float cospitch, float sinyaw,
        float cossinpitch, int pass, float mainalpha) {
        float playery = cosyaw == 0.0F ? -cossinpitch / 1.0E-4F : -cossinpitch / cosyaw;
        GTSRFXVec playervec = new GTSRFXVec(
            (double) (sinyaw * -cospitch),
            (double) playery,
            (double) (cosyaw * cospitch));
        float boltage = this.particleAge >= 0 ? (float) this.particleAge / (float) this.particleMaxAge : 0.0F;
        if (pass == 0) {
            mainalpha = (1.0F - boltage) * 0.4F;
        } else {
            mainalpha = 1.0F - boltage * 0.5F;
        }
        int renderlength;
        if ((int) (this.length * 3.0F) == 0) {
            renderlength = this.numsegments0;
        } else {
            renderlength = (int) (((float) this.particleAge + partialframe + (float) ((int) (this.length * 3.0F)))
                / (float) ((int) (this.length * 3.0F))
                * (float) this.numsegments0);
        }
        Iterator<Segment> iterator = this.segments.iterator();
        while (iterator.hasNext()) {
            Segment rendersegment = iterator.next();
            if (rendersegment.segmentno <= renderlength) {
                float width = this.width
                    * (getRelativeViewVector(rendersegment.startpoint.point).length() / 5.0F + 1.0F)
                    * (1.0F + rendersegment.light)
                    * 0.5F;
                float sinp = rendersegment.sinprev;
                if (sinp <= 1.0E-3F || Float.isNaN(sinp)) {
                    sinp = 1.0F;
                }
                float sinn = rendersegment.sinnext;
                if (sinn <= 1.0E-3F || Float.isNaN(sinn)) {
                    sinn = 1.0F;
                }
                // 宽度方向单位化：叉积归一后乘 width/sinp，任意视角下电弧宽度恒定（修复俯仰角接近 ±90° 时
                // playery 爆炸导致的全屏巨块）；叉积退化为零（视向量与段方向平行）时取任意垂直向量兜底
                GTSRFXVec cross1 = GTSRFXVec.crossProduct(playervec, rendersegment.prevdiff);
                if (cross1.lengthPow2() < 1.0E-6F) {
                    cross1 = GTSRFXVec.getPerpendicular(rendersegment.prevdiff);
                } else {
                    cross1.normalize();
                }
                GTSRFXVec diff1 = cross1.multiply(width / sinp);
                GTSRFXVec cross2 = GTSRFXVec.crossProduct(playervec, rendersegment.nextdiff);
                if (cross2.lengthPow2() < 1.0E-6F) {
                    cross2 = GTSRFXVec.getPerpendicular(rendersegment.nextdiff);
                } else {
                    cross2.normalize();
                }
                GTSRFXVec diff2 = cross2.multiply(width / sinn);
                GTSRFXVec startvec = rendersegment.startpoint.point;
                GTSRFXVec endvec = rendersegment.endpoint.point;
                float rx1 = (float) ((double) startvec.x - (double) interpPosX);
                float ry1 = (float) ((double) startvec.y - (double) interpPosY);
                float rz1 = (float) ((double) startvec.z - (double) interpPosZ);
                float rx2 = (float) ((double) endvec.x - (double) interpPosX);
                float ry2 = (float) ((double) endvec.y - (double) interpPosY);
                float rz2 = (float) ((double) endvec.z - (double) interpPosZ);
                tessellator.setColorRGBA_F(
                    this.particleRed,
                    this.particleGreen,
                    this.particleBlue,
                    mainalpha * rendersegment.light);
                tessellator.addVertexWithUV(
                    (double) (rx2 - diff2.x),
                    (double) (ry2 - diff2.y),
                    (double) (rz2 - diff2.z),
                    0.5D,
                    0.0D);
                tessellator.addVertexWithUV(
                    (double) (rx1 - diff1.x),
                    (double) (ry1 - diff1.y),
                    (double) (rz1 - diff1.z),
                    0.5D,
                    0.0D);
                tessellator.addVertexWithUV(
                    (double) (rx1 + diff1.x),
                    (double) (ry1 + diff1.y),
                    (double) (rz1 + diff1.z),
                    0.5D,
                    1.0D);
                tessellator.addVertexWithUV(
                    (double) (rx2 + diff2.x),
                    (double) (ry2 + diff2.y),
                    (double) (rz2 + diff2.z),
                    0.5D,
                    1.0D);
                if (rendersegment.next == null) {
                    GTSRFXVec roundend = rendersegment.endpoint.point.copy()
                        .add(
                            rendersegment.diff.copy()
                                .normalize()
                                .multiply(width));
                    float rx3 = (float) ((double) roundend.x - (double) interpPosX);
                    float ry3 = (float) ((double) roundend.y - (double) interpPosY);
                    float rz3 = (float) ((double) roundend.z - (double) interpPosZ);
                    tessellator.addVertexWithUV(
                        (double) (rx3 - diff2.x),
                        (double) (ry3 - diff2.y),
                        (double) (rz3 - diff2.z),
                        0.0D,
                        0.0D);
                    tessellator.addVertexWithUV(
                        (double) (rx2 - diff2.x),
                        (double) (ry2 - diff2.y),
                        (double) (rz2 - diff2.z),
                        0.5D,
                        0.0D);
                    tessellator.addVertexWithUV(
                        (double) (rx2 + diff2.x),
                        (double) (ry2 + diff2.y),
                        (double) (rz2 + diff2.z),
                        0.5D,
                        1.0D);
                    tessellator.addVertexWithUV(
                        (double) (rx3 + diff2.x),
                        (double) (ry3 + diff2.y),
                        (double) (rz3 + diff2.z),
                        0.0D,
                        1.0D);
                }
                if (rendersegment.prev == null) {
                    GTSRFXVec roundend = rendersegment.startpoint.point.copy()
                        .subtract(
                            rendersegment.diff.copy()
                                .normalize()
                                .multiply(width));
                    float rx3 = (float) ((double) roundend.x - (double) interpPosX);
                    float ry3 = (float) ((double) roundend.y - (double) interpPosY);
                    float rz3 = (float) ((double) roundend.z - (double) interpPosZ);
                    tessellator.addVertexWithUV(
                        (double) (rx1 - diff1.x),
                        (double) (ry1 - diff1.y),
                        (double) (rz1 - diff1.z),
                        0.5D,
                        0.0D);
                    tessellator.addVertexWithUV(
                        (double) (rx3 - diff1.x),
                        (double) (ry3 - diff1.y),
                        (double) (rz3 - diff1.z),
                        0.0D,
                        0.0D);
                    tessellator.addVertexWithUV(
                        (double) (rx3 + diff1.x),
                        (double) (ry3 + diff1.y),
                        (double) (rz3 + diff1.z),
                        0.0D,
                        1.0D);
                    tessellator.addVertexWithUV(
                        (double) (rx1 + diff1.x),
                        (double) (ry1 + diff1.y),
                        (double) (rz1 + diff1.z),
                        0.5D,
                        1.0D);
                }
            }
        }
    }

    @Override
    public void renderParticle(Tessellator tess, float partialframe, float cosyaw, float cospitch, float sinyaw,
        float sinsinpitch, float cossinpitch) {
        EntityClientPlayerMP renderentity = Minecraft.getMinecraft().thePlayer;
        int visibleDistance = 10000; // getDistanceSq 是距离平方：10000 = 100 格
        if (!Minecraft.getMinecraft().gameSettings.fancyGraphics) {
            visibleDistance = 2500; // 2500 = 50 格
        }
        if (renderentity != null
            && renderentity.getDistanceSq(this.posX, this.posY, this.posZ) <= (double) visibleDistance) {
            tess.draw(); // 刷新外层批次，本粒子自管批次（TC4 FXLightningBolt 兼容模式）
            GL11.glPushMatrix();
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            this.particleRed = 1.0F;
            this.particleGreen = 1.0F;
            this.particleBlue = 1.0F;
            float ma = 1.0F;
            switch (this.type) {
                case 0:
                    this.particleRed = 0.6F;
                    this.particleGreen = 0.3F;
                    this.particleBlue = 0.6F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    break;
                case 1:
                    this.particleRed = 0.6F;
                    this.particleGreen = 0.6F;
                    this.particleBlue = 0.1F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    break;
                case 2:
                    this.particleRed = 0.1F;
                    this.particleGreen = 0.1F;
                    this.particleBlue = 0.6F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    break;
                case 3:
                    this.particleRed = 0.1F;
                    this.particleGreen = 1.0F;
                    this.particleBlue = 0.1F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    break;
                case 4:
                    this.particleRed = 0.6F;
                    this.particleGreen = 0.1F;
                    this.particleBlue = 0.1F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    break;
                case 5:
                    this.particleRed = 0.6F;
                    this.particleGreen = 0.2F;
                    this.particleBlue = 0.6F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    break;
                case 6:
                    this.particleRed = 0.75F;
                    this.particleGreen = 1.0F;
                    this.particleBlue = 1.0F;
                    ma = 0.2F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    break;
                default:
                    // 灰白微青：白 -> 淡青 渐变（与 pass 1 default 同式）
                    float t = Math.min(1.0F, Math.max(0.0F, (float) this.particleAge / (float) this.particleMaxAge));
                    this.particleRed = 0.95F - 0.2F * t;
                    this.particleGreen = 0.97F - 0.12F * t;
                    this.particleBlue = 1.0F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    break;
            }
            // 暗化系数（供消散变黑）：作用于第一趟（外发光）渲染
            this.particleRed *= this.darkScale;
            this.particleGreen *= this.darkScale;
            this.particleBlue *= this.darkScale;
            Minecraft.getMinecraft().renderEngine.bindTexture(GLOW_TEXTURE);
            tess.startDrawingQuads();
            tess.setBrightness(0x00F000F0);
            this.renderBolt(tess, partialframe, cosyaw, cospitch, sinyaw, cossinpitch, 0, ma);
            tess.draw();
            switch (this.type) {
                case 0:
                    this.particleRed = 1.0F;
                    this.particleGreen = 0.6F;
                    this.particleBlue = 1.0F;
                    break;
                case 1:
                    this.particleRed = 1.0F;
                    this.particleGreen = 1.0F;
                    this.particleBlue = 0.1F;
                    break;
                case 2:
                    this.particleRed = 0.1F;
                    this.particleGreen = 0.1F;
                    this.particleBlue = 1.0F;
                    break;
                case 3:
                    this.particleRed = 0.1F;
                    this.particleGreen = 0.6F;
                    this.particleBlue = 0.1F;
                    break;
                case 4:
                    this.particleRed = 1.0F;
                    this.particleGreen = 0.1F;
                    this.particleBlue = 0.1F;
                    break;
                case 5:
                    this.particleRed = 0.0F;
                    this.particleGreen = 0.0F;
                    this.particleBlue = 0.0F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    break;
                case 6:
                    this.particleRed = 0.75F;
                    this.particleGreen = 1.0F;
                    this.particleBlue = 1.0F;
                    ma = 0.2F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    break;
                default:
                    // 灰白微青：白 -> 淡青 渐变
                    float t = Math.min(1.0F, Math.max(0.0F, (float) this.particleAge / (float) this.particleMaxAge));
                    this.particleRed = 0.95F - 0.2F * t;
                    this.particleGreen = 0.97F - 0.12F * t;
                    this.particleBlue = 1.0F;
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    break;
            }
            // 暗化系数：作用于第二趟（核心）渲染
            this.particleRed *= this.darkScale;
            this.particleGreen *= this.darkScale;
            this.particleBlue *= this.darkScale;
            Minecraft.getMinecraft().renderEngine.bindTexture(GLOW_SMALL_TEXTURE);
            tess.startDrawingQuads();
            tess.setBrightness(0x00F000F0);
            this.renderBolt(tess, partialframe, cosyaw, cospitch, sinyaw, cossinpitch, 1, ma);
            tess.draw();
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDepthMask(true);
            GL11.glPopMatrix();
            tess.startDrawingQuads(); // 恢复外层批次
        }
    }

    private class BoltPoint {

        GTSRFXVec point;
        GTSRFXVec basepoint;
        GTSRFXVec offsetvec;

        BoltPoint(GTSRFXVec basepoint, GTSRFXVec offsetvec) {
            this.point = basepoint.copy()
                .add(offsetvec);
            this.basepoint = basepoint;
            this.offsetvec = offsetvec;
        }
    }

    private class Segment {

        BoltPoint startpoint;
        BoltPoint endpoint;
        GTSRFXVec diff;
        Segment prev;
        Segment next;
        GTSRFXVec nextdiff;
        GTSRFXVec prevdiff;
        float sinprev;
        float sinnext;
        float light;
        int segmentno;
        int splitno;

        Segment(BoltPoint start, BoltPoint end, float light, int segmentnumber, int splitnumber) {
            this.startpoint = start;
            this.endpoint = end;
            this.light = light;
            this.segmentno = segmentnumber;
            this.splitno = splitnumber;
            this.calcDiff();
        }

        Segment(GTSRFXVec start, GTSRFXVec end) {
            this(
                new BoltPoint(start, new GTSRFXVec(0.0D, 0.0D, 0.0D)),
                new BoltPoint(end, new GTSRFXVec(0.0D, 0.0D, 0.0D)),
                1.0F,
                0,
                0);
        }

        void calcDiff() {
            this.diff = this.endpoint.point.copy()
                .subtract(this.startpoint.point);
        }

        void calcEndDiffs() {
            GTSRFXVec nextdiffnorm;
            GTSRFXVec thisdiffnorm;
            if (this.prev != null) {
                nextdiffnorm = this.prev.diff.copy()
                    .normalize();
                thisdiffnorm = this.diff.copy()
                    .normalize();
                this.prevdiff = thisdiffnorm.add(nextdiffnorm)
                    .normalize();
                this.sinprev = (float) Math
                    .sin((double) (GTSRFXVec.anglePreNorm(thisdiffnorm, nextdiffnorm.multiply(-1.0F)) / 2.0F));
            } else {
                this.prevdiff = this.diff.copy()
                    .normalize();
                this.sinprev = 1.0F;
            }
            if (this.next != null) {
                nextdiffnorm = this.next.diff.copy()
                    .normalize();
                thisdiffnorm = this.diff.copy()
                    .normalize();
                this.nextdiff = thisdiffnorm.add(nextdiffnorm)
                    .normalize();
                this.sinnext = (float) Math
                    .sin((double) (GTSRFXVec.anglePreNorm(thisdiffnorm, nextdiffnorm.multiply(-1.0F)) / 2.0F));
            } else {
                this.nextdiff = this.diff.copy()
                    .normalize();
                this.sinnext = 1.0F;
            }
        }
    }

    private class SegmentSorter implements Comparator<Segment> {

        @Override
        public int compare(Segment o1, Segment o2) {
            int comp = Integer.valueOf(o1.splitno)
                .compareTo(Integer.valueOf(o2.splitno));
            return comp == 0 ? Integer.valueOf(o1.segmentno)
                .compareTo(Integer.valueOf(o2.segmentno)) : comp;
        }
    }

    private class SegmentLightSorter implements Comparator<Segment> {

        @Override
        public int compare(Segment o1, Segment o2) {
            return Float.compare(o2.light, o1.light);
        }
    }
}
