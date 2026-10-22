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

    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("com.miaokatze.gtsr.common.gui.CriticalConstructionSnapshot");
        check(Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()
            .equals(Paths.get(args[0]).toRealPath()), "GUI sample must come from the candidate JAR");
        Constructor<?> constructor = type.getDeclaredConstructor(int[].class);
        constructor.setAccessible(true);
        int[] values = new int[21];
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
        System.out.println("PASS CriticalConstructionViewContract checks=" + checks);
    }
}
