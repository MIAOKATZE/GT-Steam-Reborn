// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.critical;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.JsonObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.IVertexBuffer;
import com.miaokatze.gtsr.client.render.ClientMesh;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalGeometry.Model;
import com.miaokatze.gtsr.common.critical.CriticalGeometry.Voxel;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.critical.TileEntityCriticalRenderAnchor;

/** Real group admission and lazy mesh lifecycle against a sparse world; no renderer bootstrap or GL context. */
public final class CriticalDynamicRenderContracts {

    private static int checks;
    private static final Block BLOCK = new Block(Material.iron) {};
    private static final Block OTHER = new Block(Material.iron) {};

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static final class State extends CriticalMachineState {

        @Override
        public int getGeometryVersion() {
            return 2;
        }

        @Override
        public int getV2LayoutRevision() {
            return 30;
        }

        @Override
        public CriticalMachineKind getMachineKind() {
            return CriticalMachineKind.SUN;
        }

        @Override
        public CriticalTier getTier() {
            return CriticalTier.T3;
        }

        @Override
        public int getState() {
            return 2;
        }

        @Override
        public String getStructureId() {
            return "physical-render-contract";
        }

        @Override
        public boolean hasPhysicalPlugins() {
            return false;
        }

        @Override
        public boolean isStructureValidated() {
            return true;
        }

        @Override
        public ForgeDirection getStructureFacing() {
            return ForgeDirection.NORTH;
        }

        @Override
        public double getMeshOriginX() {
            return 0;
        }

        @Override
        public double getMeshOriginZ() {
            return 0;
        }

        @Override
        public int getOriginY() {
            return 0;
        }

        @Override
        public int blockWorldX(int x, int z) {
            return x;
        }

        @Override
        public int blockWorldZ(int x, int z) {
            return z;
        }
    }

    private static final class Cell {

        Block block;
        int metadata;

        Cell(Block block, int metadata) {
            this.block = block;
            this.metadata = metadata;
        }
    }

    private static final class SparseWorld extends World {

        Map<Long, Cell> cells;
        Map<Long, TileEntity> tiles;
        boolean loaded;
        int reads;
        long tick;

        SparseWorld() {
            super(null, "unused", (WorldProvider) null, null, null);
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            reads++;
            Cell cell = cells.get(Voxel.positionKey(x, y, z));
            return cell == null ? OTHER : cell.block;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            reads++;
            Cell cell = cells.get(Voxel.positionKey(x, y, z));
            return cell == null ? 0 : cell.metadata;
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return loaded;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return tiles.get(Voxel.positionKey(x, y, z));
        }

        @Override
        public long getTotalWorldTime() {
            return tick;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected int func_152379_p() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entity getEntityByID(int id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Vao implements IVertexArrayObject {

        int deleted;
        final boolean fail;

        Vao(boolean fail) {
            this.fail = fail;
        }

        public void bind() {
            throw new AssertionError("CPU contract attempted a draw");
        }

        public void unbind() {}

        public void draw() {
            throw new AssertionError("CPU contract attempted a draw");
        }

        public void draw(int first, int count) {
            draw();
        }

        public void draw(int mode, int first, int count) {
            draw();
        }

        public IVertexBuffer getVBO() {
            return null;
        }

        public void delete() {
            check(++deleted == 1, "owned buffer is deleted once");
            if (fail) throw new IllegalStateException("driver deletion failure");
        }
    }

    private static Voxel voxel(int x, String group, String model) throws Exception {
        Constructor<Voxel> constructor = Voxel.class.getDeclaredConstructor(
            int.class,
            int.class,
            int.class,
            Block.class,
            int.class,
            String.class,
            String.class,
            String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(x, 0, 0, BLOCK, 4, "alloy_frame", group, model);
    }

    private static Model model(List<Voxel> voxels, JsonObject animations) throws Exception {
        Constructor<Model> constructor = Model.class
            .getDeclaredConstructor(List.class, JsonObject.class, JsonObject.class);
        constructor.setAccessible(true);
        return constructor.newInstance(voxels, animations, new JsonObject());
    }

    private static ClientMesh resource(Vao vao) throws Exception {
        Constructor<ClientMesh> constructor = ClientMesh.class.getDeclaredConstructor(IVertexArrayObject.class);
        constructor.setAccessible(true);
        return constructor.newInstance(vao);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        SparseWorld world = (SparseWorld) unsafe.allocateInstance(SparseWorld.class);
        world.cells = new HashMap<>();
        world.tiles = new HashMap<>();
        world.loaded = true;
        State state = new State();
        state.worldObj = world;
        String main = CriticalGeometry.visualMachineSlug(state), base = CriticalGeometry.visualBaseSlug(state),
            loom = CriticalGeometry.visualLoomSlug(state);
        Field modelsField = CriticalGeometry.class.getDeclaredField("MODELS");
        modelsField.setAccessible(true);
        Map<String, Model> models = (Map<String, Model>) modelsField.get(null);
        Map<String, Model> previous = new HashMap<>();
        for (String key : new String[] { main, base, loom }) previous.put(key, models.get(key));
        JsonObject animations = new JsonObject();
        animations.add("moving", new JsonObject());
        JsonObject staticZone = new JsonObject();
        staticZone.addProperty("kind", "static-material-zone");
        animations.add("zone", staticZone);
        List<Voxel> voxels = new ArrayList<>();
        voxels.add(voxel(0, "moving", main));
        voxels.add(voxel(1, "moving", main));
        voxels.add(voxel(2, "zone", main));
        voxels.add(voxel(3, "fixed", main));
        try {
            models.put(main, model(voxels, animations));
            models.put(base, model(new ArrayList<>(), new JsonObject()));
            models.put(loom, model(new ArrayList<>(), new JsonObject()));
            CriticalGroupVisibility admission = new CriticalGroupVisibility(state);
            String group = main + ":moving";
            check(
                admission.update(state)
                    .isEmpty(),
                "absent paid structure does not authorize proxy drawing");
            for (Voxel voxel : voxels) world.cells.put(voxel.positionKey(), new Cell(BLOCK, 0));
            world.tick++;
            check(
                admission.update(state)
                    .contains(group),
                "complete real block/meta group is admitted");
            int sampled = world.reads;
            admission.update(state);
            check(world.reads == sampled, "multiple passes reuse one tick's physical admission");
            world.cells.get(
                voxels.get(1)
                    .positionKey()).metadata = 1;
            world.tick++;
            check(
                !admission.update(state)
                    .contains(group),
                "one metadata mismatch revokes the entire group");
            world.cells.get(
                voxels.get(1)
                    .positionKey()).metadata = 0;
            world.cells.get(
                voxels.get(1)
                    .positionKey()).block = OTHER;
            world.tick++;
            check(
                !admission.update(state)
                    .contains(group),
                "foreign block never authorizes matching appearance");
            world.cells.get(
                voxels.get(1)
                    .positionKey()).block = BLOCK;
            world.tick++;
            check(
                admission.update(state)
                    .contains(group),
                "repaired real group can be admitted again");
            world.loaded = false;
            world.tick++;
            sampled = world.reads;
            check(
                !admission.update(state)
                    .contains(group) && world.reads == sampled,
                "unloaded cells revoke authorization without reading or loading their blocks");
            world.loaded = true;

            TileEntityCriticalRenderAnchor anchor = new TileEntityCriticalRenderAnchor();
            anchor.setWorldObj(world);
            check(!CriticalControllerRenderer.worldAnchor(anchor), "a synthetic anchor has no TESR authority");
            world.tiles.put(Voxel.positionKey(0, 0, 0), anchor);
            check(CriticalControllerRenderer.worldAnchor(anchor), "the actual world anchor authorizes TESR dispatch");
            world.tiles.clear();
            check(!CriticalControllerRenderer.worldAnchor(anchor), "removal immediately revokes anchor authority");

            CriticalMeshCache cache = new CriticalMeshCache();
            List<CriticalMeshCache.Mesh> first = cache.get(main);
            check(
                first.size() == 1 && first.get(0).visibilityKey.equals(group),
                "only the original dynamic group enters proxy meshes; both static categories stay in chunks");
            check(first == cache.get(main), "same model shares the immutable CPU mesh and eventual GPU upload");
            while (com.miaokatze.gtsr.client.render.ClientRenderFrame.tryBuild()) {}
            java.util.Set<String> pending = new java.util.HashSet<>();
            CriticalControllerRenderer.prepareGroups(first, java.util.Collections.singleton(group), pending);
            check(
                pending.contains(group) && first.get(0).albedo == null,
                "deferred upload does not authorize hiding real source cells");
            Vao albedo = new Vao(true), emission = new Vao(false);
            first.get(0).albedo = resource(albedo);
            first.get(0).emission = resource(emission);
            try {
                cache.clear();
                throw new AssertionError("driver cleanup failure swallowed");
            } catch (IllegalStateException expected) {
                check(
                    albedo.deleted == 1 && emission.deleted == 1,
                    "cache release attempts all buffers after a failure");
            }
            cache.clear();
            check(cache.get(main) != first, "reload discards model ownership before failed driver deletion");
            cache.clear();
        } finally {
            for (Map.Entry<String, Model> entry : previous.entrySet()) {
                if (entry.getValue() == null) models.remove(entry.getKey());
                else models.put(entry.getKey(), entry.getValue());
            }
        }
        System.out.println("PASS critical physical dynamic render contracts: " + checks);
        com.miaokatze.gtsr.client.render.ClientMeshContracts.main(args);
    }
}
