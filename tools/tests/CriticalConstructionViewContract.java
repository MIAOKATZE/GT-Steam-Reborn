package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Paths;

/** The real synced GUI sample must show dismantling instead of a stale foundation clock. */
public final class CriticalConstructionViewContract {

    private static int checks;

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    private static Object call(Object sample, String method) throws Exception {
        Method getter = sample.getClass().getDeclaredMethod(method);
        getter.setAccessible(true);
        return getter.invoke(sample);
    }

    private static double part(Object sample, int part) throws Exception {
        Method getter = sample.getClass().getDeclaredMethod("partProgress", int.class);
        getter.setAccessible(true);
        return (Double) getter.invoke(sample, part);
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("com.miaokatze.gtsr.common.gui.CriticalConstructionSnapshot");
        check(Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()
            .equals(Paths.get(args[0]).toRealPath()), "GUI sample must come from the candidate JAR");
        Constructor<?> constructor = type.getDeclaredConstructor(int[].class);
        constructor.setAccessible(true);
        int[] values = new int[23];
        values[0] = 2; // Persistent foundation is still curing when dismantling begins.
        values[5] = 720000;
        values[6] = 1440000;
        values[10] = 40;
        values[11] = 100;
        values[12] = 3;
        values[15] = 1;
        values[18] = 1;
        Object dismantling = constructor.newInstance((Object) values.clone());
        check(!(Boolean) call(dismantling, "showsFoundation"), "Dismantling must not show the stopped curing grid");
        check(Math.abs((Double) call(dismantling, "progress") - .4) < .000001,
            "Dismantling progress must use its cursor, not the old curing clock");
        values[10] = 80;
        Object advanced = constructor.newInstance((Object) values.clone());
        check(Math.abs((Double) call(advanced, "progress") - .8) < .000001,
            "A later dismantling cursor must visibly advance progress");
        values[12] = 4;
        Object repairing = constructor.newInstance((Object) values.clone());
        check(!(Boolean) call(repairing, "showsFoundation"), "Repair must not reactivate the foundation display");
        values[12] = 2;
        Object complete = constructor.newInstance((Object) values.clone());
        check(!(Boolean) call(complete, "showsFoundation"), "Completed structure must override stale foundation flags");
        check((Double) call(complete, "progress") == 1, "Completed structure progress is complete");
        values[12] = 1;
        Object curing = constructor.newInstance((Object) values.clone());
        check((Boolean) call(curing, "showsFoundation"), "Active foundation work retains the grid");
        check((Double) call(curing, "progress") == .5, "Active curing retains its remaining-time progress");
        values[0] = 4;
        values[13] = 9000;
        values[14] = 36000;
        Object heating = constructor.newInstance((Object) values.clone());
        check((Boolean) call(heating, "showsFoundation"), "Heat curing retains the foundation grid");
        check((Double) call(heating, "progress") == .25, "Heat curing uses its own paid-time clock");
        check((Integer) call(heating, "heatingRemaining") == 27000, "Heat curing remaining time is authoritative");
        values[0] = 3;
        values[21] = 1;
        values[9] = 5;
        values[10] = 50;
        values[11] = 100;
        Object body = constructor.newInstance((Object) values.clone());
        check(!(Boolean) call(body, "equipmentStage"), "New stage five remains body work, not legacy equipment");
        check((Double) call(body, "bodyProgress") == .5, "Middle of the second body part is half complete");
        values[21] = 0;
        Object legacyEquipment = constructor.newInstance((Object) values.clone());
        check((Boolean) call(legacyEquipment, "equipmentStage"), "Legacy stage five retains its equipment meaning");
        values[0] = 1;
        values[12] = 1;
        values[22] = 6;
        values[1] = 8;
        values[2] = 16;
        values[3] = 213;
        values[4] = 400;
        Object wholeChunk = constructor.newInstance((Object) values.clone());
        check((Integer) call(wholeChunk, "displayCellTicks") == 208,
            "v6 whole-chunk paid work maps to the current display subdivision remainder");
        check(Math.abs((Double) call(wholeChunk, "progress") - 213D / 400) < .000001,
            "v6 global progress preserves the whole-chunk clock without multiplying completion twice");
        values[22] = 5;
        Object oldCell = constructor.newInstance((Object) values.clone());
        check((Integer) call(oldCell, "displayCellTicks") == 213,
            "uncertain version-five work retains the original per-small-cell clock");
        values[0] = 3;
        values[21] = 1;
        values[10] = 50;
        values[11] = 100;
        for (int stage = 1; stage <= 9; stage++) {
            values[9] = stage;
            Object sample = constructor.newInstance((Object) values.clone());
            check((Integer) call(sample, "activePart") == (stage - 1) / 3,
                "Every new stage highlights its own base/body/equipment group: " + stage);
            for (int group = 0; group < 3; group++) {
                double expected = Math.max(0, Math.min(1, (stage - 1 - group * 3 + .5) / 3));
                check(Math.abs(part(sample, group) - expected) < .000001,
                    "Each segment follows its own authoritative stage cursor: " + stage + "/" + group);
            }
        }
        values[9] = 9;
        values[12] = 2;
        Object finished = constructor.newInstance((Object) values.clone());
        for (int group = 0; group < 3; group++) check(part(finished, group) == 1,
            "All three groups remain visible at completion, including equipment");
        check((Integer) call(finished, "activePart") == -1, "Completion has no active segment");
        values[12] = 0;
        Object planned = constructor.newInstance((Object) values.clone());
        for (int group = 0; group < 3; group++) check(part(planned, group) == 0,
            "A planned job never inherits filled segments from a stale stage");
        values[12] = 1;
        values[0] = 4;
        Object foundation = constructor.newInstance((Object) values.clone());
        check((Integer) call(foundation, "activePart") == -1, "Curing does not highlight a structural segment");
        System.out.println("PASS CriticalConstructionViewContract checks=" + checks);
    }
}
