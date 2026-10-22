package com.miaokatze.gtsr.common.critical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared, bounded cache of immutable canonical placement data. Never contains world or controller references. */
public final class CriticalCompiledLayout {

    public enum Role {
        LOOM,
        CONTROLLER,
        HATCH,
        OPTIONAL_WORK_PORT,
        PAID_CHAMBER
    }

    // Layouts contain large bodies. Retain recent configurations without retaining every historical combination.
    private static final Role[] ROLE_VALUES = Role.values();
    private static final int CACHE_LIMIT = 8;
    private static final Map<String, CriticalCompiledLayout> CACHE = new LinkedHashMap<>(16, .75f, true);
    private static final List<Set<Role>> ROLE_SETS = roleSets();
    private final List<CriticalGeometry.Voxel> voxels, loom, plugins, optionalPorts, hatchPorts;
    private final Map<Role, List<CriticalGeometry.Voxel>> roleVoxels;
    private final Map<Long, Set<Role>> roles;
    private final int top, loomTop;

    private CriticalCompiledLayout(CriticalLayoutDefinition layout, CriticalConfiguration config) {
        List<CriticalGeometry.Voxel> loomSource = new ArrayList<>(CriticalGeometry.getModel(layout.loomSlug).voxels);
        if (!layout.v2) loomSource.removeIf(v -> v.x == 40 && v.y == 0 && v.z == 33);
        loom = Collections.unmodifiableList(loomSource);
        plugins = layout.v2 ? Collections.unmodifiableList(CriticalGeometry.compilePhysicalPlugins(config, layout))
            : Collections.emptyList();
        optionalPorts = CriticalGeometry.compileOptionalWorkPorts(layout);
        Map<Long, CriticalGeometry.Voxel> cells = new LinkedHashMap<>();
        if (!layout.v2 || layout.legacy) put(cells, loom);
        if (layout.v2) {
            put(cells, CriticalGeometry.getModel(layout.baseSlug).voxels);
            put(cells, CriticalGeometry.getModel(layout.machineSlug).voxels);
            put(cells, plugins);
            // Manual loom ownership wins shared coordinates, except for the frozen legacy merge order.
            if (!layout.legacy) put(cells, loom);
        } else {
            put(cells, CriticalGeometry.get(layout.tier, layout.kind));
            cells.remove(CriticalGeometry.Voxel.positionKey(40, 0, 33));
        }
        List<CriticalGeometry.Voxel> ordered = new ArrayList<>(cells.values());
        if (layout.v2) ordered.sort(
            java.util.Comparator.comparingInt((CriticalGeometry.Voxel v) -> v.stage)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.x)
                .thenComparingInt(v -> v.z));
        voxels = Collections.unmodifiableList(ordered);
        top = top(ordered);
        loomTop = top(loom);
        Map<Long, CriticalGeometry.Voxel> ports = new LinkedHashMap<>();
        Map<Role, List<CriticalGeometry.Voxel>> index = new EnumMap<>(Role.class);
        for (Role role : ROLE_VALUES) index.put(role, new ArrayList<>());
        Map<Long, Set<Role>> byPosition = new LinkedHashMap<>();
        List<CriticalGeometry.Voxel> all = new ArrayList<>(ordered);
        all.addAll(optionalPorts);
        for (CriticalGeometry.Voxel voxel : all) {
            int mask = 0;
            if (CriticalLayoutDefinition.isLoom(voxel)) mask |= 1 << Role.LOOM.ordinal();
            if (CriticalLayoutDefinition.isController(voxel)) mask |= 1 << Role.CONTROLLER.ordinal();
            if (layout.isHatch(voxel)) {
                mask |= 1 << Role.HATCH.ordinal();
                ports.put(voxel.positionKey(), voxel);
            }
            if (CriticalLayoutDefinition.isOptionalWorkPort(voxel)) mask |= 1 << Role.OPTIONAL_WORK_PORT.ordinal();
            if (CriticalLayoutDefinition.isPaidChamber(voxel)) mask |= 1 << Role.PAID_CHAMBER.ordinal();
            for (Role role : ROLE_VALUES) if ((mask & 1 << role.ordinal()) != 0) index.get(role)
                .add(voxel);
            if (mask != 0) byPosition.put(voxel.positionKey(), ROLE_SETS.get(mask));
        }
        for (Role role : ROLE_VALUES) index.put(role, Collections.unmodifiableList(index.get(role)));
        roleVoxels = Collections.unmodifiableMap(index);
        roles = Collections.unmodifiableMap(byPosition);
        hatchPorts = Collections.unmodifiableList(new ArrayList<>(ports.values()));
    }

    public static CriticalCompiledLayout forState(CriticalMachineState state) {
        return get(CriticalLayoutDefinition.forState(state), state.getConfiguration());
    }

    public static synchronized CriticalCompiledLayout get(CriticalLayoutDefinition layout,
        CriticalConfiguration config) {
        String key = layout.cacheKey()
            + (layout.v2 ? ":" + config.parallel + ':' + config.speed + ':' + config.economy : "");
        CriticalCompiledLayout cached = CACHE.get(key);
        if (cached == null) {
            cached = new CriticalCompiledLayout(layout, config);
            CACHE.put(key, cached);
            if (CACHE.size() > CACHE_LIMIT) CACHE.remove(
                CACHE.keySet()
                    .iterator()
                    .next());
        }
        return cached;
    }

    /** V1 preview entry point preserves the historical loom-first, unsorted merged order. */
    public static CriticalCompiledLayout v1(CriticalTier tier, CriticalMachineKind kind) {
        return get(
            CriticalLayoutDefinition.select(1, 0, false, tier, kind),
            new CriticalConfiguration(tier, kind, 0, 0, 0));
    }

    public List<CriticalGeometry.Voxel> voxels() {
        return voxels;
    }

    public List<CriticalGeometry.Voxel> loomVoxels() {
        return loom;
    }

    public List<CriticalGeometry.Voxel> plugins() {
        return plugins;
    }

    public List<CriticalGeometry.Voxel> optionalWorkPorts() {
        return optionalPorts;
    }

    public List<CriticalGeometry.Voxel> hatchPorts() {
        return hatchPorts;
    }

    public List<CriticalGeometry.Voxel> voxels(Role role) {
        return roleVoxels.get(role);
    }

    public Set<Role> roles(long position) {
        return roles.getOrDefault(position, Collections.emptySet());
    }

    public int top() {
        return top;
    }

    public int loomTop() {
        return loomTop;
    }

    private static List<Set<Role>> roleSets() {
        List<Set<Role>> combinations = new ArrayList<>();
        for (int mask = 0; mask < 1 << ROLE_VALUES.length; mask++) {
            EnumSet<Role> roles = EnumSet.noneOf(Role.class);
            for (Role role : ROLE_VALUES) if ((mask & 1 << role.ordinal()) != 0) roles.add(role);
            combinations.add(Collections.unmodifiableSet(roles));
        }
        return Collections.unmodifiableList(combinations);
    }

    private static void put(Map<Long, CriticalGeometry.Voxel> cells, List<CriticalGeometry.Voxel> source) {
        for (CriticalGeometry.Voxel voxel : source) cells.put(voxel.positionKey(), voxel);
    }

    private static int top(List<CriticalGeometry.Voxel> source) {
        int result = 0;
        for (CriticalGeometry.Voxel voxel : source) result = Math.max(result, voxel.y);
        return result;
    }
}
