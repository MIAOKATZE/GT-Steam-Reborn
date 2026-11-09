package com.miaokatze.gtsr.client.render;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.client.preview.PreviewRenderScope;

/** Owns an unpublished GTSR list and an initially inactive native tessellator batch. */
public final class ClientDisplayList {

    private ClientDisplayList() {}

    interface Snapshot {

        void restore() throws Exception;
    }

    interface Driver {

        Snapshot snapshot() throws Exception;

        int current();

        int allocate();

        void begin(int list);

        void end();

        void delete(int list);
    }

    static final Driver NATIVE = new Driver() {

        @Override
        public Snapshot snapshot() throws Exception {
            PreviewRenderScope.ClientTessState state = new PreviewRenderScope.ClientTessState(Tessellator.instance);
            return state::restore;
        }

        @Override
        public int current() {
            return GL11.glGetInteger(GL11.GL_LIST_INDEX);
        }

        @Override
        public int allocate() {
            return GL11.glGenLists(1);
        }

        @Override
        public void begin(int list) {
            GL11.glNewList(list, GL11.GL_COMPILE);
        }

        @Override
        public void end() {
            GL11.glEndList();
        }

        @Override
        public void delete(int list) {
            GL11.glDeleteLists(list, 1);
        }
    };

    public static int compile(Runnable commands) {
        return compile(commands, NATIVE);
    }

    static int compile(Runnable commands, Driver driver) {
        final Snapshot snapshot;
        try {
            snapshot = driver.snapshot();
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot capture inactive tessellator", failure);
        }
        if (driver.current() != 0) throw new IllegalStateException("Cannot compile inside an ambient display list");
        int list = driver.allocate();
        if (list == 0) throw new IllegalStateException("GL display list allocation failed");
        boolean open = false, compiled = false;
        Throwable primary = null;
        try {
            driver.begin(list);
            open = true;
            commands.run();
            open = false;
            driver.end();
            compiled = true;
            return list;
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable cleanup = null;
            if (open) try {
                driver.end();
            } catch (RuntimeException | Error failure) {
                cleanup = failure;
            }
            try {
                snapshot.restore();
            } catch (Throwable failure) {
                if (cleanup == null) cleanup = failure;
                else cleanup.addSuppressed(failure);
            }
            if (!compiled || cleanup != null) try {
                driver.delete(list);
            } catch (RuntimeException | Error failure) {
                if (cleanup == null) cleanup = failure;
                else cleanup.addSuppressed(failure);
            }
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw new IllegalStateException("Failed to release display-list compilation", cleanup);
            }
        }
    }

    /** All lists in this group are still unpublished; failure cannot leave an unreachable prefix allocated. */
    public static void rollback(java.util.List<Integer> lists, Throwable primary) {
        rollback(lists, primary, NATIVE);
    }

    static void rollback(java.util.List<Integer> lists, Throwable primary, Driver driver) {
        for (int list : lists) try {
            driver.delete(list);
        } catch (RuntimeException | Error failure) {
            primary.addSuppressed(failure);
        }
    }
}
