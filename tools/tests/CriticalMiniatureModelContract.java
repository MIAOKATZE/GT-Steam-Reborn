package com.miaokatze.gtsr.client.critical;

import com.miaokatze.gtsr.common.critical.CriticalMachineKind;

/** Self-contained data/pose contract; compile with the model and enum only, with no Minecraft or GL libraries. */
public final class CriticalMiniatureModelContract {

    public static void main(String[] args) {
        for (CriticalMachineKind kind : CriticalMachineKind.releasedValues()) {
            CriticalMiniatureModel model = CriticalMiniatureModel.create(kind);
            if (model.faces <= 0 || model.faces > 2000 || model.parts.size() > 28)
                throw new AssertionError("Unbounded miniature " + kind);
            boolean[] stages = new boolean[5];
            int moving = 0;
            for (CriticalMiniatureModel.Part part : model.parts) {
                stages[part.stage] = true;
                if (part.name.matches("confinement_[0-9]+")) outward(part, 2);
                if (part.name.matches("rotor_[0-9]+") || part.name.equals("chamber_drive_0")) outward(part, 0);
                if (part.name.startsWith("bank_half_hoop_")) outward(part, 1);
                if (part.motion != CriticalMiniatureModel.Motion.STILL) {
                    moving++;
                    double[] first = CriticalMiniatureModel.transform(part, 1, .7, .4, 0);
                    double[] later = CriticalMiniatureModel.transform(part, 1, .7, .4, 1);
                    double distance = 0;
                    for (int axis = 0; axis < 3; axis++) distance += Math.abs(first[axis] - later[axis]);
                    if (distance < .001) throw new AssertionError("Invisible motion " + part.name);
                }
            }
            for (int stage = 1; stage <= 4; stage++)
                if (!stages[stage]) throw new AssertionError("Lost construction stage " + stage);
            if (moving < 3) throw new AssertionError("Missing mechanism motions " + kind);
            double width = model.projectedHigh[0] - model.projectedLow[0];
            double height = model.projectedHigh[1] - model.projectedLow[1];
            if (!Double.isFinite(width + height) || width < 15 || height < 6)
                throw new AssertionError("Invalid silhouette " + kind);
            System.out.println("PASS " + kind + " faces=" + model.faces + " groups=" + model.parts.size()
                + " moving=" + moving + " projectedAspect=" + width / height);
        }
    }

    private static void outward(CriticalMiniatureModel.Part part, int axis) {
        float[] q = part.quads.get(0);
        double ax = q[3]-q[0], ay = q[4]-q[1], az = q[5]-q[2];
        double bx = q[6]-q[0], by = q[7]-q[1], bz = q[8]-q[2];
        double[] n = { az*by-ay*bz, ax*bz-az*bx, ay*bx-ax*by };
        double dot = 0;
        for (int dimension=0;dimension<3;dimension++) if(dimension!=axis)
            dot += n[dimension]*(q[dimension]+q[3+dimension]+q[6+dimension]+q[9+dimension])*.25;
        if(dot<=0)throw new AssertionError("Inward ring/shaft normal: "+part.name);
    }
}
