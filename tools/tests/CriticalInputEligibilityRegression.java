import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;

import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtsr.common.critical.CriticalEnergyRuntime;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.hatch.critical.MTEHatchCriticalInputME;

import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/** Real production chemical entry guard and real GT/GTSR type hierarchy; stops before any grid/world I/O. */
public final class CriticalInputEligibilityRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("CriticalInputEligibilityRuntime", "5.09.54.205");
    }
}

class CriticalInputEligibilityRuntime {
    private static sun.misc.Unsafe unsafe;

    public static void run(String version) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (sun.misc.Unsafe) field.get(null);
        System.out.println("SOURCE runtime " + CriticalEnergyRuntime.class.getProtectionDomain().getCodeSource().getLocation());
        check(passesGuard(MTEHatchInputME.class), "native ME input remains accepted");
        check(!passesGuard(UnknownInput.class), "unknown ME subclass remains rejected");
        check(passesGuard(MTEHatchCriticalInputME.class), "critical long ME input reaches the supported payment planner");
        System.out.println("PASS CriticalInputEligibilityRegression checks=3 (guard only; no live AE grid)");
    }

    private static boolean passesGuard(Class<? extends MTEHatchInput> type) throws Exception {
        MTECriticalMachine host = (MTECriticalMachine) unsafe.allocateInstance(MTECriticalMachine.class);
        GuardInputs inputs = new GuardInputs();
        inputs.add((MTEHatchInput) unsafe.allocateInstance(type));
        set(host, "mInputHatches", inputs);
        set(host, "mDualInputHatches", new ArrayList<>());
        State state = (State) unsafe.allocateInstance(State.class);
        Method start = CriticalEnergyRuntime.class.getDeclaredMethod("startChemicalConfirmed",
            CriticalMachineState.class, MTECriticalMachine.class, NBTTagCompound.class);
        start.setAccessible(true);
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("chemicalSchema", CriticalEnergyRuntime.CHEMICAL_COMPOUND_SCHEMA);
        try {
            start.invoke(null, state, host, data);
            return false;
        } catch (InvocationTargetException invocation) {
            if (invocation.getCause() instanceof PaymentPlannerReached) return true;
            throw invocation;
        }
    }

    private static void set(Object instance, String name, Object value) throws Exception {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(instance, value);
                return;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    /** First traversal is eligibility; the second is the actual snapshot boundary, deliberately not executed. */
    static class GuardInputs extends ArrayList<MTEHatchInput> {
        private int traversals;
        private void traverse() { if (traversals++ != 0) throw new PaymentPlannerReached(); }
        @Override public Iterator<MTEHatchInput> iterator() { traverse(); return super.iterator(); }
        @Override public Spliterator<MTEHatchInput> spliterator() { traverse(); return super.spliterator(); }
    }
    static class PaymentPlannerReached extends RuntimeException {}
    static class State extends CriticalMachineState {
        @Override public boolean fail(String message) { return false; }
    }
    static class UnknownInput extends MTEHatchInputME {
        UnknownInput() { super("unused", false, 1, new String[0], null); }
    }
}
