package com.miaokatze.gtsr.common.gui.mui2.hub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtsr.common.terminal.CacheHubTerminalData.CacheNodeInfo;
import com.miaokatze.gtsr.common.terminal.SingularityHubTerminalData.HubNodeInfo;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.Unpooled;

/** Local UI state only. Replacement is bounded and happens on a received revision, not in draw suppliers. */
public final class HubTerminalViewState {

    public static final int PAGE_SIZE = 32;
    public long revision;
    public final boolean drilling, nearby;
    private final Map<HubNodeKey, Row> rows = new LinkedHashMap<>();
    private final Map<HubNodeKey, String> names = new LinkedHashMap<>();
    private final List<HubNodeKey> visible = new ArrayList<>();
    public HubNodeKey selected;
    public String query = "", numericDraft = "";
    public HubNodeKey editing;
    public int editingAction, page, sort;
    public Function<Row, String> searchText = row -> row.name() + " "
        + row.type()
        + (row.cache == null ? "" : " " + row.cache.fluid);

    public HubTerminalViewState(boolean drilling, boolean nearby) {
        this.drilling = drilling;
        this.nearby = nearby;
    }

    /** Fixture-friendly pure model entry; no world access or server mutation. */
    public void replace(List<CacheNodeInfo> cache, List<HubNodeInfo> workers, long nextRevision) {
        if (nextRevision <= revision) return;
        revision = nextRevision;
        rows.clear();
        if (cache != null) {
            for (CacheNodeInfo info : cache) {
                if (rows.size() == 256) break;
                Row row = new Row(info, null);
                rows.put(row.key, row);
            }
        }
        if (workers != null) {
            for (HubNodeInfo info : workers) {
                if (rows.size() == 256) break;
                Row row = new Row(null, info);
                rows.put(row.key, row);
            }
        }
        names.keySet()
            .retainAll(rows.keySet());
        if (nearby && !rows.isEmpty()) selected = rows.keySet()
            .iterator()
            .next();
        if (selected != null && !rows.containsKey(selected)) selected = null;
        if (editing != null && !rows.containsKey(editing)) cancelEdit();
        refresh();
    }

    public void query(String value) {
        if (query.equals(value)) return;
        query = value;
        page = 0;
        refresh();
    }

    public void nextSort() {
        sort = (sort + 1) % (drilling ? 3 : 6);
        page = 0;
        refresh();
    }

    public void refresh() {
        visible.clear();
        String search = query.trim()
            .toLowerCase(Locale.ROOT);
        for (Row row : rows.values()) {
            String haystack = searchText.apply(row) + " " + row.key;
            if (search.isEmpty() || haystack.toLowerCase(Locale.ROOT)
                .contains(search)) visible.add(row.key);
        }
        if (sort != 0) {
            Comparator<HubNodeKey> comparator;
            if (sort == 1) comparator = Comparator
                .comparing(key -> searchText.apply(rows.get(key)), String.CASE_INSENSITIVE_ORDER);
            else if (sort == 2) comparator = Comparator.naturalOrder();
            else comparator = (a, b) -> Long.compare(
                rows.get(b)
                    .number(sort - 3),
                rows.get(a)
                    .number(sort - 3));
            Collections.sort(visible, comparator.thenComparing(Comparator.naturalOrder()));
        }
        page = Math.max(0, Math.min(page, pages() - 1));
    }

    public int total() {
        return rows.size();
    }

    public int filtered() {
        return visible.size();
    }

    public int pages() {
        return Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public void page(int delta) {
        page = Math.max(0, Math.min(page + delta, pages() - 1));
    }

    public Row row(int poolIndex) {
        int n = page * PAGE_SIZE + poolIndex;
        return n >= 0 && n < visible.size() ? rows.get(visible.get(n)) : null;
    }

    public Row selectedRow() {
        return selected != null && visible.contains(selected) ? rows.get(selected) : null;
    }

    public void select(int poolIndex) {
        Row row = row(poolIndex);
        if (row != null) selected = row.key;
    }

    public String nameDraft() {
        Row row = selectedRow();
        return row == null ? "" : names.getOrDefault(row.key, row.name());
    }

    public void nameDraft(String text) {
        if (selectedRow() != null) names.put(selected, text);
    }

    public void edit(int action) {
        Row row = selectedRow();
        if (row == null || row.cache == null || !row.online() || (action == 8 && !row.capacity())) return;
        editing = row.key;
        editingAction = action;
        numericDraft = Long.toString(action == 7 ? row.cache.actualRate : row.cache.cap);
    }

    public void cancelEdit() {
        editing = null;
        numericDraft = "";
    }

    public byte[] numericPayload(String text) {
        Row row = selectedRow();
        if (row == null || !row.key.equals(editing) || !row.online() || (editingAction == 8 && !row.capacity()))
            throw new IllegalArgumentException("Target changed");
        String value = text.trim();
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException("Integer required");
        long parsed = Long.parseLong(value);
        long max = editingAction == 7 ? row.cache.maxRate : row.cache.maxCap;
        if (parsed < (editingAction == 7 ? 0 : 1) || parsed > max) throw new IllegalArgumentException("Out of range");
        PacketBuffer out = position(row.key);
        out.writeLong(parsed);
        return bytes(out);
    }

    public static PacketBuffer position(HubNodeKey key) {
        PacketBuffer out = new PacketBuffer(Unpooled.buffer(24));
        out.writeInt(key.x);
        out.writeInt(key.y);
        out.writeInt(key.z);
        out.writeInt(key.dim);
        return out;
    }

    public static byte[] bytes(PacketBuffer out) {
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        return bytes;
    }

    public byte[] selectedPayload(Boolean target, String name) {
        Row row = selectedRow();
        if (row == null) return null;
        PacketBuffer out = position(row.key);
        if (target != null) out.writeBoolean(target);
        if (name != null) ByteBufUtils.writeUTF8String(out, name);
        return bytes(out);
    }

    public static final class Row {

        public final HubNodeKey key;
        public final CacheNodeInfo cache;
        public final HubNodeInfo worker;

        Row(CacheNodeInfo cache, HubNodeInfo worker) {
            this.cache = cache;
            this.worker = worker;
            key = cache != null ? new HubNodeKey(cache.x, cache.y, cache.z, cache.dim)
                : new HubNodeKey(worker.x, worker.y, worker.z, worker.dim);
        }

        public String name() {
            return cache != null ? cache.name : worker.name;
        }

        public String type() {
            return cache != null ? cache.type : worker.isMiner ? "miner" : "driller";
        }

        public boolean online() {
            return cache == null || !cache.type.isEmpty();
        }

        public boolean capacity() {
            return cache != null && online() && !cache.type.endsWith("_out");
        }

        public long number(int column) {
            return cache == null ? 0 : column == 0 ? cache.stored : column == 1 ? cache.cap : cache.actualRate;
        }
    }
}
