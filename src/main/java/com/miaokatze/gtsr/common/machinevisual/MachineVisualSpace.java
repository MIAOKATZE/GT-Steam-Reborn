package com.miaokatze.gtsr.common.machinevisual;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/** Display XYZ is (right, up, back); StructureLib ABC is (right, down, back). */
public final class MachineVisualSpace {

    private MachineVisualSpace() {}

    public static int[] transform(ExtendedFacing facing, int x, int y, int z) {
        int[] result = new int[3];
        facing.getWorldOffset(new int[] { x, -y, z }, result);
        return result;
    }

    public static double[] transform(ExtendedFacing facing, double x, double y, double z) {
        double[] result = new double[3];
        facing.getWorldOffset(new double[] { x, -y, z }, result);
        return result;
    }

    public static double handedness(ExtendedFacing facing) {
        double[] x = transform(facing, 1., 0., 0.), y = transform(facing, 0., 1., 0.),
            z = transform(facing, 0., 0., 1.);
        return x[0] * (y[1] * z[2] - y[2] * z[1]) - x[1] * (y[0] * z[2] - y[2] * z[0])
            + x[2] * (y[0] * z[1] - y[1] * z[0]);
    }
}
