import java.lang.reflect.*;

import net.minecraft.world.IBlockAccess;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility;
import com.miaokatze.gtsr.mixin.WorldRendererMachineVisualMixin;
import com.miaokatze.gtsr.mixin.angelica.MachineVisualMeshingMixin;
import com.miaokatze.gtsr.mixin.angelica.MachineVisualDeferredReleaseMixin;

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

    static class DeferredRelease extends MachineVisualDeferredReleaseMixin {
    }

    static class NativeTask extends com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuilderMeshingTask {

        NativeTask() {
            super(null, 0, null);
        }

        protected net.minecraft.client.renderer.Tessellator getTessellator() {
            return null;
        }

        protected void setRenderPass(int pass) {}

        protected IBlockAccess getBlockAccess() {
            return null;
        }

        protected com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache getBiomeColorCache() {
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

    @SuppressWarnings("unchecked")
    static java.util.Map<Object, Object> builds() throws Exception {
        return (java.util.Map<Object, Object>) field("BUILDS").get(null);
    }

    @SuppressWarnings("unchecked")
    static java.util.Map<Object, Object> outputs() throws Exception {
        return (java.util.Map<Object, Object>) field("OUTPUTS").get(null);
    }

    static Object execute(Angelica owner, boolean cancelled, Operation<?> original) throws Exception {
        return invoke(
            owner,
            "gtsr$releaseFailedSection",
            new Class<?>[] { org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext.class,
                org.embeddedt.embeddium.impl.util.task.CancellationToken.class, Operation.class },
            null,
            new org.embeddedt.embeddium.impl.util.task.CancellationToken() {

                private boolean value = cancelled;

                public boolean isCancelled() {
                    return value;
                }

                public void setCancelled() {
                    value = true;
                }
            },
            original);
    }

    static void releaseDeferred(Object owner) throws Exception {
        Class<?> meshType = Class.forName("com.gtnewhorizons.angelica.rendering.celeritas.DeferredSectionMesh");
        Object mesh = new com.gtnewhorizons.angelica.rendering.celeritas.DeferredSectionMesh(
            (com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuilderMeshingTask) owner,
            null, null, null, null, null, null, 0, false, 0, 0);
        invoke(
            new DeferredRelease(),
            "gtsr$releaseMachineMasks",
            new Class<?>[] { meshType, org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class },
            mesh,
            null);
    }

    @SuppressWarnings("unchecked")
    static Object addHiddenDecision(Object receipt, int x) throws Exception {
        Class<?> ownerType = Class.forName("com.miaokatze.gtsr.client.machinevisual.MachineVisualClient$Entry");
        Object owner = unsafe.allocateInstance(ownerType);
        Class<?> cellType = Class.forName("com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility$CellEntry");
        Constructor<?> cellCtor = cellType.getDeclaredConstructors()[0];
        cellCtor.setAccessible(true);
        Object cell = cellCtor.newInstance(owner, 1, x, 0, 0, null, 0);
        Class<?> decisionType = Class.forName("com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility$CellDecision");
        Constructor<?> decisionCtor = decisionType.getDeclaredConstructor(boolean.class, long.class);
        decisionCtor.setAccessible(true);
        Object decision = decisionCtor.newInstance(true, 1L);
        Field cells = receipt.getClass().getDeclaredField("cells");
        cells.setAccessible(true);
        ((java.util.Map<Object, Object>) cells.get(receipt)).put(cell, decision);
        Field key = cellType.getDeclaredField("key");
        key.setAccessible(true);
        ((java.util.Map<Object, Object>) field("CELLS").get(null)).put(key.get(cell), cell);
        return cell;
    }

    static boolean uploadedHidden(Object cell) throws Exception {
        Field uploaded = cell.getClass().getDeclaredField("uploaded"), hidden = cell.getClass().getDeclaredField("uploadedHidden");
        uploaded.setAccessible(true);
        hidden.setAccessible(true);
        return uploaded.getBoolean(cell) && hidden.getBoolean(cell);
    }

    static void deferredLifecycle() throws Exception {
        Angelica owner = new Angelica();
        seed(owner);
        Object workerReceipt = builds().get(owner);
        Object worker = addHiddenDecision(workerReceipt, 0);
        check(execute(owner, false, args -> null) == null, "deferred handoff returns native null");
        check(builds().get(owner) == workerReceipt, "normal null handoff retains exact worker receipt");
        check(((ThreadLocal<?>) field("BUILD").get(null)).get() == null, "handoff clears worker BUILD thread local");
        Object deferred = addHiddenDecision(builds().get(owner), 1);
        Object output = new Object();
        MachineVisualVisibility.finishBuild(owner, output);
        check(!builds().containsKey(owner) && outputs().get(output) == workerReceipt,
            "later finalize transfers same mixed worker/deferred receipt");
        MachineVisualVisibility.uploaded(output);
        check(uploadedHidden(worker) && uploadedHidden(deferred), "real uploaded method acknowledges both hidden cells");
        check(!outputs().containsKey(output), "mixed output consumed once");

        seed(owner);
        Object synchronous = unsafe.allocateInstance(org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput.class);
        Object syncReceipt = builds().get(owner);
        execute(owner, false, args -> {
            MachineVisualVisibility.finishBuild(owner, synchronous);
            return synchronous;
        });
        check(clear(owner) && outputs().get(synchronous) == syncReceipt, "synchronous finalization keeps output ownership");
        outputs().remove(synchronous);

        seed(owner);
        execute(owner, true, args -> null);
        check(clear(owner), "execute cancellation releases receipt and BUILD");

        // The actual scheduler release seam takes a real native task-typed owner, not a Mixin fixture object.
        Object nativeTask = unsafe.allocateInstance(NativeTask.class);
        for (String terminal : new String[] { "obsolete", "capture failure", "finish failure", "orphan shutdown" }) {
            seed(nativeTask);
            Object ambient = ((ThreadLocal<?>) field("BUILD").get(null)).get();
            releaseDeferred(nativeTask);
            check(!builds().containsKey(nativeTask), terminal + " terminal release discards unpublished receipt");
            check(((ThreadLocal<?>) field("BUILD").get(null)).get() == ambient,
                terminal + " release does not clear an unrelated worker BUILD");
            ((ThreadLocal<?>) field("BUILD").get(null)).remove();
        }
        seed(nativeTask);
        Object ready = new Object(), receipt = builds().get(nativeTask);
        MachineVisualVisibility.finishBuild(nativeTask, ready);
        releaseDeferred(nativeTask);
        check(outputs().get(ready) == receipt, "successful deferred release cannot discard transferred output");
        outputs().remove(ready);
        ((ThreadLocal<?>) field("BUILD").get(null)).remove();
        ((java.util.Map<?, ?>) field("CELLS").get(null)).clear();
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
        deferredLifecycle();
        System.out.println("PASS visual exceptional scopes checks=" + checks);
    }
}
