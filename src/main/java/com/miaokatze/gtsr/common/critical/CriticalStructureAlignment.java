package com.miaokatze.gtsr.common.critical;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/** Controller-relative declarations for the canonical fabrication layout. */
public final class CriticalStructureAlignment {

    public static final int V2_MAX = CriticalLayoutDefinition.V2_SIZE - 1;

    private CriticalStructureAlignment() {}

    public static ForgeDirection horizontal(ForgeDirection direction) {
        return direction != null && direction != ForgeDirection.UNKNOWN && direction.offsetY == 0 ? direction
            : ForgeDirection.NORTH;
    }

    public static ExtendedFacing facing(ForgeDirection frame, boolean selector) {
        return ExtendedFacing.of(selector ? horizontal(frame) : horizontal(frame).getOpposite());
    }

    public static int rotateX(int x, int z, ForgeDirection frame) {
        return (int) rotateX((double) x, z, frame);
    }

    public static int rotateZ(int x, int z, ForgeDirection frame) {
        return (int) rotateZ((double) x, z, frame);
    }

    public static double rotateX(double x, double z, ForgeDirection frame) {
        return switch (horizontal(frame)) {
            case EAST -> -z;
            case SOUTH -> -x;
            case WEST -> z;
            default -> x;
        };
    }

    public static double rotateZ(double x, double z, ForgeDirection frame) {
        return switch (horizontal(frame)) {
            case EAST -> x;
            case SOUTH -> -z;
            case WEST -> -x;
            default -> z;
        };
    }

    public static double unrotateX(double x, double z, ForgeDirection frame) {
        return switch (horizontal(frame)) {
            case EAST -> z;
            case SOUTH -> -x;
            case WEST -> -z;
            default -> x;
        };
    }

    public static double unrotateZ(double x, double z, ForgeDirection frame) {
        return switch (horizontal(frame)) {
            case EAST -> -x;
            case SOUTH -> -z;
            case WEST -> x;
            default -> z;
        };
    }

    public static double yawDegrees(ForgeDirection frame) {
        return switch (horizontal(frame)) {
            case EAST -> -90D;
            case SOUTH -> 180D;
            case WEST -> 90D;
            default -> 0D;
        };
    }

    public static int shapeA(int x, boolean selector) {
        return selector ? V2_MAX - x : x;
    }

    public static int shapeC(int z, boolean selector) {
        return selector ? z : V2_MAX - z;
    }

    public static int controllerA(boolean selector) {
        return shapeA(CriticalLayoutDefinition.V2_CONTROLLER_X, selector);
    }

    public static int controllerC(boolean selector) {
        return shapeC(selector ? CriticalLayoutDefinition.V2_LOOM_Z : CriticalLayoutDefinition.V2_MAIN_Z, selector);
    }
}
