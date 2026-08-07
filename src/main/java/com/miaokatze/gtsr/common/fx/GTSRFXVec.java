package com.miaokatze.gtsr.common.fx;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 轻量 3D 向量，替代 TC4 的 WRVector3，自包含实现。
 */
@SideOnly(Side.CLIENT)
public class GTSRFXVec {

    public float x;
    public float y;
    public float z;

    public GTSRFXVec(double x, double y, double z) {
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
    }

    public GTSRFXVec add(GTSRFXVec vec) {
        this.x += vec.x;
        this.y += vec.y;
        this.z += vec.z;
        return this;
    }

    public GTSRFXVec subtract(GTSRFXVec vec) {
        this.x -= vec.x;
        this.y -= vec.y;
        this.z -= vec.z;
        return this;
    }

    public GTSRFXVec multiply(float scale) {
        this.x *= scale;
        this.y *= scale;
        this.z *= scale;
        return this;
    }

    public GTSRFXVec scalarMultiply(float scale) {
        return this.multiply(scale);
    }

    public float magnitude() {
        return (float) Math.sqrt((double) (this.x * this.x + this.y * this.y + this.z * this.z));
    }

    public float length() {
        return this.magnitude();
    }

    public float lengthPow2() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public GTSRFXVec normalize() {
        float length = this.length();
        this.x /= length;
        this.y /= length;
        this.z /= length;
        return this;
    }

    public GTSRFXVec copy() {
        return new GTSRFXVec((double) this.x, (double) this.y, (double) this.z);
    }

    public static GTSRFXVec crossProduct(GTSRFXVec vec1, GTSRFXVec vec2) {
        return new GTSRFXVec(
            (double) (vec1.y * vec2.z - vec1.z * vec2.y),
            (double) (vec1.z * vec2.x - vec1.x * vec2.z),
            (double) (vec1.x * vec2.y - vec1.y * vec2.x));
    }

    public static GTSRFXVec xCrossProduct(GTSRFXVec vec) {
        return new GTSRFXVec(0.0D, (double) vec.z, (double) (-vec.y));
    }

    public static GTSRFXVec zCrossProduct(GTSRFXVec vec) {
        return new GTSRFXVec((double) (-vec.y), (double) vec.x, 0.0D);
    }

    public static float dotProduct(GTSRFXVec vec1, GTSRFXVec vec2) {
        return vec1.x * vec2.x + vec1.y * vec2.y + vec1.z * vec2.z;
    }

    public static float anglePreNorm(GTSRFXVec vec1, GTSRFXVec vec2) {
        return (float) Math.acos((double) dotProduct(vec1, vec2));
    }

    /**
     * 绕任意轴旋转（角度制），Rodrigues 公式，等价 WRVector3.rotate 的矩阵实现。
     */
    public GTSRFXVec rotate(float angle, GTSRFXVec axis) {
        double rad = Math.toRadians((double) angle);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        GTSRFXVec ax = axis.copy()
            .normalize();
        double dot = dotProduct(ax, this);
        GTSRFXVec cross = crossProduct(ax, this);
        this.x = (float) ((double) this.x * c + (double) cross.x * s + (double) ax.x * dot * (1.0D - c));
        this.y = (float) ((double) this.y * c + (double) cross.y * s + (double) ax.y * dot * (1.0D - c));
        this.z = (float) ((double) this.z * c + (double) cross.z * s + (double) ax.z * dot * (1.0D - c));
        return this;
    }

    public static GTSRFXVec getPerpendicular(GTSRFXVec vec) {
        return vec.z == 0.0F ? zCrossProduct(vec) : xCrossProduct(vec);
    }

    public boolean isZero() {
        return this.x == 0.0F && this.y == 0.0F && this.z == 0.0F;
    }

    @Override
    public String toString() {
        return "[" + this.x + "," + this.y + "," + this.z + "]";
    }
}
