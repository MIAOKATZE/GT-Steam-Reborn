package com.miaokatze.gtsr.client.critical;

import com.google.gson.JsonObject;

/** Pure scene-space pose math; JSON center coordinates and native mesh corners share one affine transform. */
final class CriticalMechanicalPose {

    private CriticalMechanicalPose() {}

    static double[] matrix(JsonObject animation, double phase, double seconds, String mode) {
        CriticalContinuousMotion.Context context = CriticalAnimation.currentContext();
        double[] affine = context == null ? CriticalAnimation.affineUncached(animation, phase, seconds, mode)
            : context.affine(animation, phase, seconds, mode);
        double[] result = affine.clone();
        // A JSON voxel at (x,y,z) has native mesh center (x+.5,y+.5,z+.5).
        for (int row = 0; row < 3; row++)
            result[12 + row] = affine[12 + row] + .5 - .5 * (result[row] + result[4 + row] + result[8 + row]);
        result[15] = 1;
        return result;
    }
}
