package com.miaokatze.gtsr.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.config.Config;
import com.miaokatze.miaokatzecore.client.preview.PreviewRenderScope;

/** Explicit test-lab client entry. Call only on the owning client render thread with a current GL context. */
public final class TestLabDisplayListProbe {

    private TestLabDisplayListProbe() {}

    private static final class Tracked implements ClientDisplayList.Driver {

        final List<Integer> allocated = new ArrayList<>();
        int ends, deletes;

        @Override
        public ClientDisplayList.Snapshot snapshot() throws Exception {
            return ClientDisplayList.NATIVE.snapshot();
        }

        @Override
        public int current() {
            return ClientDisplayList.NATIVE.current();
        }

        @Override
        public int allocate() {
            int id = ClientDisplayList.NATIVE.allocate();
            allocated.add(id);
            return id;
        }

        @Override
        public void begin(int id) {
            ClientDisplayList.NATIVE.begin(id);
        }

        @Override
        public void end() {
            ends++;
            ClientDisplayList.NATIVE.end();
        }

        @Override
        public void delete(int id) {
            deletes++;
            ClientDisplayList.NATIVE.delete(id);
        }
    }

    private static void check(Map<String, Object> result, String key, boolean passed) {
        result.put(key, passed);
        if (!passed) throw new IllegalStateException(key);
    }

    private static void quad() {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertex(0, 0, 0);
        tess.addVertex(0, 1, 0);
        tess.addVertex(1, 1, 0);
        tess.addVertex(1, 0, 0);
        tess.draw();
    }

    public static Map<String, Object> run() throws Exception {
        if (!Config.testLabEnabled) throw new IllegalStateException("GTSR testlab is disabled");
        Map<String, Object> result = new LinkedHashMap<>();
        Tracked driver = new Tracked();
        PreviewRenderScope.ClientTessState initial = new PreviewRenderScope.ClientTessState(Tessellator.instance);
        if (GL11.glGetInteger(GL11.GL_LIST_INDEX) != 0)
            throw new IllegalStateException("Probe requires no ambient list");
        try {
            IllegalStateException injected = new IllegalStateException("testlab-injected-vertex-failure");
            try {
                ClientDisplayList.compile(() -> {
                    Tessellator.instance.startDrawingQuads();
                    Tessellator.instance.addVertex(0, 0, 0);
                    throw injected;
                }, driver);
                throw new AssertionError("Expected vertex failure");
            } catch (IllegalStateException failure) {
                check(result, "original_failure_preserved", failure == injected);
            }
            check(
                result,
                "failed_list_ended_deleted",
                driver.ends == 1 && driver.deletes == 1
                    && !GL11.glIsList(driver.allocated.get(0))
                    && GL11.glGetInteger(GL11.GL_LIST_INDEX) == 0);
            new PreviewRenderScope.ClientTessState(Tessellator.instance);
            check(result, "failed_own_batch_restored_inactive", true);
            int good = ClientDisplayList.compile(TestLabDisplayListProbe::quad, driver);
            check(result, "next_compile_succeeds", GL11.glIsList(good));
            driver.delete(good);

            PreviewRenderScope.ClientTessState beforeAmbient = new PreviewRenderScope.ClientTessState(
                Tessellator.instance);
            int allocations = driver.allocated.size();
            try {
                Tessellator.instance.startDrawingQuads();
                Tessellator.instance.addVertex(3, 4, 5);
                try {
                    ClientDisplayList
                        .compile(() -> { throw new AssertionError("Ambient callback must not run"); }, driver);
                    throw new AssertionError("Expected ambient batch rejection");
                } catch (IllegalStateException expected) {}
                check(result, "ambient_batch_not_owned_or_flushed", driver.allocated.size() == allocations);
            } finally {
                beforeAmbient.restore();
            }

            int ambient = GL11.glGenLists(1);
            try {
                GL11.glNewList(ambient, GL11.GL_COMPILE);
                try {
                    ClientDisplayList
                        .compile(() -> { throw new AssertionError("Ambient callback must not run"); }, driver);
                    throw new AssertionError("Expected ambient list rejection");
                } catch (IllegalStateException expected) {}
                check(
                    result,
                    "ambient_list_not_ended_or_deleted",
                    GL11.glGetInteger(GL11.GL_LIST_INDEX) == ambient && driver.allocated.size() == allocations);
            } finally {
                GL11.glEndList();
                GL11.glDeleteLists(ambient, 1);
            }

            List<Integer> group = new ArrayList<>();
            try {
                group.add(ClientDisplayList.compile(TestLabDisplayListProbe::quad, driver));
                group.add(ClientDisplayList.compile(TestLabDisplayListProbe::quad, driver));
                ClientDisplayList.compile(() -> { throw injected; }, driver);
                throw new AssertionError("Expected group failure");
            } catch (IllegalStateException failure) {
                ClientDisplayList.rollback(group, failure, driver);
            }
            boolean released = true;
            for (int list : driver.allocated) released &= !GL11.glIsList(list);
            check(result, "failed_unpublished_group_released", released);
            result.put("checks", result.size());
            result.put("status", "PASS");
            result.put(
                "scope",
                "Real current-context GL lists and Core inactive Tessellator snapshot; no complete renderer asset/visual assertion");
            return result;
        } finally {
            initial.restore();
            for (int list : driver.allocated) if (GL11.glIsList(list)) GL11.glDeleteLists(list, 1);
        }
    }
}
