package com.miaokatze.gtsr.client.critical;

import com.google.gson.JsonObject;

/** Pure scene-space pose math; JSON center coordinates and native mesh corners share one affine transform. */
final class CriticalMechanicalPose {

    private CriticalMechanicalPose() {}

    static double[] matrix(JsonObject animation, double phase, double seconds, String mode) {
        double[] origin = CriticalAnimation.transform(new double[3], animation, phase, seconds, mode);
        double[] result = new double[16];
        for (int column = 0; column < 3; column++) {
            double[] unit = new double[3];
            unit[column] = 1;
            double[] moved = CriticalAnimation.transform(unit, animation, phase, seconds, mode);
            for (int row = 0; row < 3; row++) result[column * 4 + row] = moved[row] - origin[row];
        }
        // A JSON voxel at (x,y,z) has native mesh center (x+.5,y+.5,z+.5).
        for (int row = 0; row < 3; row++)
            result[12 + row] = origin[row] + .5 - .5 * (result[row] + result[4 + row] + result[8 + row]);
        result[15] = 1;
        return result;
    }
}
