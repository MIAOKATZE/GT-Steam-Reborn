package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.registry.GameRegistry;

/** Graphs are stored in display order. Forward-only links make the saved order a topological order. */
public final class CenterGraph {

    public static final int MAX_GRAPHS = 18;
    public static final int MAX_NODES = 64;
    public static final int MAX_RULES = 128;
    public static final int BUS = -1;
    public static final int UNCONNECTED = -2;
    public static final int AUTO_POSITION = Integer.MIN_VALUE;
    static final int MAX_DEPTH = 32;
    static final int MAX_EDGES = 256;

    public static final class OutletFilter {

        public final int slot;
        public final boolean allow;
        private final List<String> rules;
        private final List<CenterOreRule> compiled;

        public OutletFilter(int slot, boolean allow, List<String> rules) {
            if (slot < 0 || slot >= 64) throw new IllegalArgumentException("Invalid outlet slot");
            this.slot = slot;
            this.allow = allow;
            this.rules = immutableRules(rules);
            this.compiled = compile(this.rules);
        }

        public List<String> rules() {
            return rules;
        }

        boolean accepts(ItemStack stack) {
            try {
                return matchesRules(compiled, stack, tags(stack)) == allow;
            } catch (CenterOreRule.Limit ignored) {
                return false;
            }
        }
    }

    public static final class Node {

        public final CenterProcess process;
        private final int[] targets;
        private final List<OutletFilter> filters;
        private final OutletFilter[] bySlot;
        public final int circuit;
        public final int x, y;
        private final String[] auxiliary;

        public Node(CenterProcess process, int[] targets) {
            this(process, targets, 0);
        }

        public Node(CenterProcess process, int[] targets, int circuit) {
            this(process, targets, circuit, Collections.emptyList());
        }

        public Node(CenterProcess process, int[] targets, int circuit, List<OutletFilter> filters) {
            this(process, targets, circuit, filters, AUTO_POSITION, AUTO_POSITION);
        }

        private Node(CenterProcess process, int[] targets, int circuit, List<OutletFilter> filters, int x, int y) {
            this(process, targets, circuit, filters, x, y, null);
        }

        private Node(CenterProcess process, int[] targets, int circuit, List<OutletFilter> filters, int x, int y,
            String[] auxiliary) {
            if (process == null || targets.length > 64 || circuit < 0 || circuit > 24) {
                throw new IllegalArgumentException("Invalid processing node");
            }
            if (circuit != 0 && !process.hasCircuitSlot()) throw new IllegalArgumentException("Unsupported NC slot");
            this.process = process;
            this.targets = targets.clone();
            this.circuit = circuit;
            this.x = x;
            this.y = y;
            this.auxiliary = auxiliary == null ? null : auxiliary.clone();
            if (filters.size() > 64) throw new IllegalArgumentException("Too many outlet filters");
            this.filters = Collections.unmodifiableList(new ArrayList<>(filters));
            bySlot = filters.isEmpty() ? null : new OutletFilter[64];
            for (OutletFilter filter : filters) {
                if (bySlot[filter.slot] != null) throw new IllegalArgumentException("Repeated outlet filter");
                bySlot[filter.slot] = filter;
            }
        }

        public int target(int slot) {
            return slot < targets.length ? targets[slot] : BUS;
        }

        public int[] targets() {
            return targets.clone();
        }

        public List<OutletFilter> filters() {
            return filters;
        }

        public Node withPosition(int x, int y) {
            if (x < -8192 || x > 8192 || y < -8192 || y > 8192)
                throw new IllegalArgumentException("Node position outside canvas bounds");
            return new Node(process, targets, circuit, filters, x, y, auxiliary);
        }

        public Node withConfiguration(int[] targets, int circuit, List<OutletFilter> filters) {
            return new Node(process, targets, circuit, filters, x, y, auxiliary);
        }

        public Node withEmptyAuxiliary() {
            if (process.auxiliarySlots() == 0) return this;
            String[] keys = new String[process.auxiliarySlots() * 2];
            java.util.Arrays.fill(keys, "");
            return new Node(process, targets, circuit, filters, x, y, keys);
        }

        public Node withAuxiliary(int slot, String key) {
            int size = process.auxiliarySlots();
            if (slot < 0 || slot >= size * 2 || key.length() > 190
                || !key.isEmpty() && !(slot < size ? key.matches("[A-Za-z0-9_.|/-]+:[A-Za-z0-9_.|/-]+@[0-9]{1,5}")
                    : key.matches("[A-Za-z0-9_.:/-]+"))) throw new IllegalArgumentException("Invalid auxiliary slot");
            Node base = auxiliary == null ? withEmptyAuxiliary() : this;
            String[] keys = base.auxiliary.clone();
            keys[slot] = key;
            return new Node(process, targets, circuit, filters, x, y, keys);
        }

        public String auxiliary(int slot) {
            return auxiliary == null || slot < 0 || slot >= auxiliary.length ? "" : auxiliary[slot];
        }

        boolean acceptsAuxiliary(ItemStack item) {
            if (auxiliary == null) return true;
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(item.getItem());
            if (id == null) return false;
            String key = id + "@" + item.getItemDamage();
            for (int i = 0; i < process.auxiliarySlots(); i++) if (key.equals(auxiliary[i])) return true;
            return false;
        }

        boolean acceptsAuxiliary(FluidStack fluid) {
            if (auxiliary == null) return true;
            for (int i = process.auxiliarySlots(); i < auxiliary.length; i++)
                if (fluid.getFluid().getName().equals(auxiliary[i])) return true;
            return false;
        }

        int target(int slot, ItemStack stack) {
            if (process == CenterProcess.SIFTER) slot = Math.min(5, slot);
            int target = target(slot);
            if (target < 0 || bySlot == null || slot >= bySlot.length) return target;
            OutletFilter filter = bySlot[slot];
            return filter != null && !filter.accepts(stack) ? BUS : target;
        }
    }

    public final int priority;
    public final boolean enabled;
    public final long id;
    public final String name;
    private final List<String> rules;
    private final List<CenterOreRule> compiled;
    public final List<Node> nodes;
    private final boolean runnable;

    public CenterGraph(int priority, boolean enabled, List<String> oreRules, List<Node> nodes) {
        this(0, "", priority, enabled, oreRules, nodes);
    }

    public CenterGraph(long id, String name, int priority, boolean enabled, List<String> oreRules, List<Node> nodes) {
        if (nodes.size() > MAX_NODES || oreRules.size() > MAX_RULES) {
            throw new IllegalArgumentException("Graph exceeds its node or rule budget");
        }
        this.priority = priority;
        this.enabled = enabled;
        if (id < 0 || name == null
            || name.length() > 32
            || name.chars()
                .anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid graph identity");
        }
        this.id = id;
        this.name = name;
        this.rules = immutableRules(oreRules);
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        int[] depth = new int[nodes.size()];
        if (!nodes.isEmpty()) depth[0] = 1;
        boolean reachable = !nodes.isEmpty();
        int edges = 0;
        int ruleChars = rules.stream()
            .mapToInt(String::length)
            .sum();
        int ruleCount = rules.size();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            for (OutletFilter filter : node.filters) {
                ruleCount += filter.rules.size();
                ruleChars += filter.rules.stream()
                    .mapToInt(String::length)
                    .sum();
                if (ruleCount > MAX_RULES || ruleChars > 16384)
                    throw new IllegalArgumentException("Graph filter budget exceeded");
            }
            if (depth[i] > MAX_DEPTH) throw new IllegalArgumentException("Graph too deep");
            if (depth[i] == 0) reachable = false;
            for (int target : node.targets) {
                if (++edges > MAX_EDGES || target < UNCONNECTED
                    || target >= 0 && (node.process.direct || target <= i || target >= nodes.size())) {
                    throw new IllegalArgumentException("Invalid or cyclic link");
                }
                if (target == UNCONNECTED) reachable = false;
                if (target >= 0 && depth[i] != 0) depth[target] = Math.max(depth[target], depth[i] + 1);
            }
        }
        runnable = reachable;
        this.compiled = compile(rules);
    }

    public List<String> rules() {
        return rules;
    }

    CenterGraph withId(long replacement) {
        return new CenterGraph(replacement, name, priority, enabled, rules, nodes);
    }

    private static List<String> immutableRules(List<String> source) {
        if (source.size() > MAX_RULES || source.stream()
            .mapToInt(String::length)
            .sum() > 16384) {
            throw new IllegalArgumentException("Ore-rule budget exceeded");
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static List<CenterOreRule> compile(List<String> rules) {
        List<CenterOreRule> result = new ArrayList<>();
        for (String rule : rules) result.add(new CenterOreRule(rule));
        return result;
    }

    public boolean fallback() {
        return rules.isEmpty();
    }

    public boolean runnable() {
        return runnable;
    }

    boolean matches(ItemStack stack, String[] tags) {
        try {
            return matchesRules(compiled, stack, tags);
        } catch (CenterOreRule.Limit ignored) {
            return false;
        }
    }

    private static boolean matchesRules(List<CenterOreRule> rules, ItemStack stack, String[] tags) {
        for (CenterOreRule rule : rules) if (rule.matches(stack, tags)) return true;
        return false;
    }

    public static String[] tags(ItemStack stack) {
        int[] ids = OreDictionary.getOreIDs(stack);
        String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; i++) names[i] = OreDictionary.getOreName(ids[i]);
        return names;
    }

    public NBTTagCompound save() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("Id", id);
        tag.setString("Name", name);
        tag.setInteger("Priority", priority);
        tag.setBoolean("Enabled", enabled);
        NBTTagList allow = new NBTTagList(), steps = new NBTTagList();
        for (String rule : rules) allow.appendTag(new NBTTagString(rule));
        for (Node node : nodes) {
            NBTTagCompound step = new NBTTagCompound();
            step.setString("Process", node.process.name());
            step.setInteger("Circuit", node.circuit);
            if (node.x != AUTO_POSITION) {
                step.setInteger("X", node.x);
                step.setInteger("Y", node.y);
            }
            step.setIntArray("Targets", node.targets);
            if (node.auxiliary != null) {
                NBTTagList slots = new NBTTagList();
                for (String item : node.auxiliary) slots.appendTag(new NBTTagString(item));
                step.setTag("Auxiliary", slots);
            }
            NBTTagList filters = new NBTTagList();
            for (OutletFilter filter : node.filters) {
                NBTTagCompound row = new NBTTagCompound();
                row.setInteger("Slot", filter.slot);
                row.setBoolean("Allow", filter.allow);
                NBTTagList entries = new NBTTagList();
                for (String rule : filter.rules) entries.appendTag(new NBTTagString(rule));
                row.setTag("Rules", entries);
                filters.appendTag(row);
            }
            step.setTag("Filters", filters);
            steps.appendTag(step);
        }
        tag.setTag("Rules", allow);
        tag.setTag("Nodes", steps);
        return tag;
    }

    public static CenterGraph load(NBTTagCompound tag) {
        NBTTagList rules = tag.getTagList("Rules", 8), nodes = tag.getTagList("Nodes", 10);
        if (rules.tagCount() > MAX_RULES || nodes.tagCount() > MAX_NODES) throw new IllegalArgumentException();
        List<String> names = new ArrayList<>();
        List<Node> steps = new ArrayList<>();
        for (int i = 0; i < rules.tagCount(); i++) names.add(rules.getStringTagAt(i));
        for (int i = 0; i < nodes.tagCount(); i++) {
            NBTTagCompound step = nodes.getCompoundTagAt(i);
            NBTTagList filters = step.getTagList("Filters", 10);
            if (filters.tagCount() > 64) throw new IllegalArgumentException("Too many outlet filters");
            List<OutletFilter> entries = new ArrayList<>();
            for (int j = 0; j < filters.tagCount(); j++) {
                NBTTagCompound filter = filters.getCompoundTagAt(j);
                NBTTagList lines = filter.getTagList("Rules", 8);
                if (lines.tagCount() > MAX_RULES) throw new IllegalArgumentException("Too many rules");
                List<String> selection = new ArrayList<>();
                for (int k = 0; k < lines.tagCount(); k++) selection.add(lines.getStringTagAt(k));
                entries.add(new OutletFilter(filter.getInteger("Slot"), filter.getBoolean("Allow"), selection));
            }
            Node node = new Node(
                CenterProcess.valueOf(step.getString("Process")),
                step.getIntArray("Targets"),
                step.getInteger("Circuit"),
                entries);
            if (step.hasKey("Auxiliary", 9)) {
                NBTTagList slots = step.getTagList("Auxiliary", 8);
                if (slots.tagCount() != node.process.auxiliarySlots() * 2) throw new IllegalArgumentException();
                for (int slot = 0; slot < slots.tagCount(); slot++)
                    node = node.withAuxiliary(slot, slots.getStringTagAt(slot));
            }
            steps.add(step.hasKey("X", 3) && step.hasKey("Y", 3)
                ? node.withPosition(step.getInteger("X"), step.getInteger("Y")) : node);
        }
        return new CenterGraph(
            tag.getLong("Id"),
            tag.getString("Name"),
            tag.getInteger("Priority"),
            tag.getBoolean("Enabled"),
            names,
            steps);
    }
}
