import java.lang.reflect.*;

import net.minecraft.world.IBlockAccess;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility;
import com.miaokatze.gtsr.mixin.WorldRendererMachineVisualMixin;
import com.miaokatze.gtsr.mixin.angelica.MachineVisualMeshingMixin;

/** Actual production wrapper methods and BUILD/maps, with injected originals; no GL calls or game launch. */
public final class GtsrVisualScopeContract {

    static int checks;
    static sun.misc.Unsafe unsafe;

    static class Vanilla extends WorldRendererMachineVisualMixin {
    }

    static class Angelica extends MachineVisualMeshingMixin {

        protected IBlockAccess getBlockAccess() {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("GtsrVisualScopeContract", "5.09.54.265");
    }

    static Field field(String key) throws Exception {
        Field f = MachineVisualVisibility.class.getDeclaredField(key);
        f.setAccessible(true);
        return f;
    }

    @SuppressWarnings("unchecked")
    static void seed(Object task) throws Exception {
        Class<?> context = Class
            .forName("com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility$BuildContext");
        ((ThreadLocal<Object>) field("BUILD").get(null)).set(unsafe.allocateInstance(context));
        Class<?> receipt = Class
            .forName("com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility$BuildReceipt");
        Constructor<?> ctor = receipt.getDeclaredConstructor();
        ctor.setAccessible(true);
        ((java.util.Map<Object, Object>) field("BUILDS").get(null)).put(task, ctor.newInstance());
    }

    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }

    static Object invoke(Object task, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = task.getClass()
            .getSuperclass()
            .getDeclaredMethod(method, types);
        m.setAccessible(true);
        try {
            return m.invoke(task, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw e;
        }
    }

    static boolean clear(Object task) throws Exception {
        return ((ThreadLocal<?>) field("BUILD").get(null)).get() == null
            && !((java.util.Map<?, ?>) field("BUILDS").get(null)).containsKey(task);
    }

    public static void run(String version) throws Exception {
        unsafe = Issue37Runtime.bootstrap(version);
        Vanilla v = new Vanilla();
        v.needsUpdate = true;
        Operation<Void> fail = args -> {
            try {
                seed(v);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            throw new IllegalStateException("renderer failure");
        };
        try {
            invoke(
                v,
                "gtsr$compileMachineMasks",
                new Class<?>[] { net.minecraft.entity.EntityLivingBase.class, Operation.class },
                null,
                fail);
            throw new AssertionError();
        } catch (IllegalStateException expected) {
            check(
                expected.getMessage()
                    .equals("renderer failure"),
                "native renderer cause propagates");
        }
        check(clear(v), "vanilla failure releases actual BUILD and outstanding receipt");
        long uploads = MachineVisualVisibility.committedOutputs;
        Operation<Void> success = args -> {
            try {
                seed(v);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        };
        invoke(
            v,
            "gtsr$compileMachineMasks",
            new Class<?>[] { net.minecraft.entity.EntityLivingBase.class, Operation.class },
            null,
            success);
        check(
            clear(v) && MachineVisualVisibility.committedOutputs == uploads + 1,
            "subsequent normal vanilla output commits exactly once");
        Angelica a = new Angelica();
        Operation<Object> taskFail = args -> {
            try {
                seed(a);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            throw new IllegalStateException("worker failure");
        };
        try {
            invoke(
                a,
                "gtsr$releaseFailedSection",
                new Class<?>[] { org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext.class,
                    org.embeddedt.embeddium.impl.util.task.CancellationToken.class, Operation.class },
                null,
                null,
                taskFail);
            throw new AssertionError();
        } catch (IllegalStateException expected) {
            check(
                expected.getMessage()
                    .equals("worker failure"),
                "native worker cause propagates");
        }
        check(clear(a), "Angelica execute failure releases actual BUILD and abandoned receipt");
        System.out.println("PASS visual exceptional scopes checks=" + checks);
    }
}
