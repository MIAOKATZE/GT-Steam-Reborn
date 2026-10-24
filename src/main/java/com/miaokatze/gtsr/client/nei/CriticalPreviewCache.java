package com.miaokatze.gtsr.client.nei;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;
import com.miaokatze.miaokatzecore.client.render.TessellatorSlice;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

/** CPU-only slices append into the original batch, including its original attribute flags and ordering. */
public final class CriticalPreviewCache {

    private static final int MAX_SEGMENTS = 256;
    private static final int MAX_POSITIONS = 2_000_000;
    private static final int MAX_VERTEX_INTS = 8_388_608;
    private static WeakReference<CriticalPreviewCache> active = new WeakReference<>(null);
    private static boolean registered;
    private static long lifecycleGeneration;

    private final World world;
    private long revision = Long.MIN_VALUE;
    private long generation = Long.MIN_VALUE;
    private final List<Segment> segments = new ArrayList<>();
    private LongArrayList tiles;
    private LongCollection collection;
    private int retainedInts;
    private boolean overflow;

    public CriticalPreviewCache(World world) {
        this.world = world;
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(new Lifecycle());
            registered = true;
        }
        CriticalPreviewCache previous = active.get();
        if (previous != null) previous.invalidate();
        active = new WeakReference<>(this);
    }

    public void invalidate() {
        revision = Long.MIN_VALUE;
        generation = Long.MIN_VALUE;
        collection = null;
        segments.clear();
        tiles = null;
        retainedInts = 0;
        overflow = false;
    }

    private void refresh() {
        CriticalPreviewCache previous = active.get();
        if (previous != this) {
            if (previous != null) previous.invalidate();
            active = new WeakReference<>(this);
        }
        long current = ((CriticalPreviewWorld) world).gtsr$previewRevision();
        if (revision == current && generation == lifecycleGeneration) return;
        invalidate();
        revision = current;
        generation = lifecycleGeneration;
    }

    /** The camera-sorted faded collection deliberately keeps BlockRenderer's live path. */
    public boolean prepare(LongCollection positions) {
        refresh();
        if (collection == positions) return !overflow;
        collection = positions;
        segments.clear();
        retainedInts = 0;
        overflow = false;
        if (positions.size() > MAX_POSITIONS) return !(overflow = true);
        for (int pass = 0; pass < 2; pass++) {
            Segment tail = null;
            LongIterator iterator = positions.iterator();
            while (iterator.hasNext()) {
                long position = iterator.nextLong();
                Block block = block(position);
                boolean own = block instanceof CriticalMaterials.MaterialBlock material
                    && material.materialId.startsWith("v2_");
                if (own && !block.canRenderInPass(pass)) continue;
                if (tail == null || tail.own != own) {
                    tail = new Segment(pass, own);
                    segments.add(tail);
                    if (segments.size() > MAX_SEGMENTS) {
                        segments.clear();
                        return !(overflow = true);
                    }
                }
                tail.positions.add(position);
            }
        }
        return true;
    }

    public void append(Tessellator tessellator, RenderBlocks renderer, boolean allFaces) {
        long renderRevision = revision;
        for (Segment segment : segments) {
            if (segment.own && !segment.captureRejected
                && renderRevision == ((CriticalPreviewWorld) world).gtsr$previewRevision()
                && CriticalPreviewTessellatorSupport.allows(tessellator)) {
                // renderV2 changes no RenderBlocks AO or override-texture fields.
                setup(renderer, allFaces);
                if (segment.slice != null && segment.slice.replay(tessellator)) continue;
                if (segment.slice != null) {
                    retainedInts -= segment.slice.retainedInts();
                    segment.slice = null;
                }
                TessellatorSlice captured = TessellatorSlice
                    .capture(tessellator, () -> render(segment, renderer, allFaces));
                if (captured != null && captured.retainedInts() <= MAX_VERTEX_INTS - retainedInts) {
                    segment.slice = captured;
                    retainedInts += captured.retainedInts();
                } else segment.captureRejected = true;
            } else render(segment, renderer, allFaces);
        }
    }

    /** Preserve the original TE lambda and its per-pass decisions, reducing only empty-coordinate lookups. */
    public void forEachTile(LongSet positions, LongConsumer consumer) {
        refresh();
        if (positions.size() > MAX_POSITIONS) {
            LongIterator iterator = positions.iterator();
            while (iterator.hasNext()) consumer.accept(iterator.nextLong());
            return;
        }
        if (tiles == null) {
            tiles = new LongArrayList();
            LongIterator iterator = positions.iterator();
            long last = 0;
            boolean lastWasEmpty = false;
            while (iterator.hasNext()) {
                long position = iterator.nextLong();
                last = position;
                lastWasEmpty = world.getTileEntity(
                    CoordinatePacker.unpackX(position),
                    CoordinatePacker.unpackY(position),
                    CoordinatePacker.unpackZ(position)) == null;
                if (!lastWasEmpty) tiles.add(position);
            }
            // Upstream resets pass state even at empty coordinates. Retain its final reset when present.
            if (lastWasEmpty) tiles.add(last);
        }
        LongIterator iterator = tiles.iterator();
        while (iterator.hasNext()) consumer.accept(iterator.nextLong());
    }

    private void render(Segment segment, RenderBlocks renderer, boolean allFaces) {
        LongIterator iterator = segment.positions.iterator();
        while (iterator.hasNext()) {
            long position = iterator.nextLong();
            Block block = block(position);
            if (block == Blocks.air || !block.canRenderInPass(segment.pass)) continue;
            setup(renderer, allFaces);
            renderer.renderBlockByRenderType(
                block,
                CoordinatePacker.unpackX(position),
                CoordinatePacker.unpackY(position),
                CoordinatePacker.unpackZ(position));
        }
    }

    private Block block(long position) {
        return world.getBlock(
            CoordinatePacker.unpackX(position),
            CoordinatePacker.unpackY(position),
            CoordinatePacker.unpackZ(position));
    }

    private void setup(RenderBlocks renderer, boolean allFaces) {
        renderer.blockAccess = world;
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
        renderer.renderAllFaces = allFaces;
    }

    private static final class Segment {

        private final int pass;
        private final boolean own;
        private final LongArrayList positions = new LongArrayList();
        private TessellatorSlice slice;
        // Retry only after the collection/model/resource generation changes. Capture already drew this frame.
        private boolean captureRejected;

        private Segment(int pass, boolean own) {
            this.pass = pass;
            this.own = own;
        }
    }

    public static final class Lifecycle {

        @SubscribeEvent
        public void guiChanged(GuiOpenEvent event) {
            invalidateAll();
        }

        @SubscribeEvent
        public void atlasChanged(TextureStitchEvent.Post event) {
            invalidateAll();
        }

        private static void invalidateAll() {
            lifecycleGeneration++;
            CriticalPreviewCache cache = active.get();
            if (cache != null) cache.invalidate();
        }
    }
}
