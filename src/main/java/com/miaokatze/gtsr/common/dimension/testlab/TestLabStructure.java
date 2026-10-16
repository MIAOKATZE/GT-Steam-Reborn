package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalLoomStructure;
import com.miaokatze.gtsr.common.critical.CriticalStructureAlignment;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.cluster.ClusterStructureDef;
import com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.machine.turbine.MegaSteamTurbineStructureDef;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Reads the machine's live StructureLib definition, never a second copy of a machine shape. */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class TestLabStructure {

    private final WorldServer world;
    private final MTEMultiBlockBase controller;
    private final IGregTechTileEntity tile;
    private final ItemStack trigger;
    private final List<Cell> cells = new ArrayList<>();
    private final Map<IStructureElement, List<Cell>> elementGroups = new IdentityHashMap<>();
    private final List<IStructureElement> elementOrder = new ArrayList<>();
    private int cursor;
    private int placed;
    private int failed;

    static boolean checkStructure(MTEMultiBlockBase machine, IGregTechTileEntity tile) {
        long timing = TestLabPerformance.begin();
        try {
            return machine.checkStructure(true, tile);
        } finally {
            TestLabPerformance.end("structure.canonicalCheck", timing);
        }
    }

    TestLabStructure(WorldServer world, MTEMultiBlockBase controller, ItemStack trigger) throws Exception {
        this.world = world;
        this.controller = controller;
        this.tile = controller.getBaseMetaTileEntity();
        this.trigger = trigger;
        if (controller instanceof MTECriticalMachine) {
            MTECriticalMachine critical = (MTECriticalMachine) controller;
            int top = 35;
            if (CriticalGeometry.isV2(critical.getCriticalState())) {
                top = 0;
                for (CriticalGeometry.Voxel voxel : CriticalLoomStructure.voxels(critical.getCriticalState()))
                    top = Math.max(top, voxel.y);
            }
            boolean v2 = CriticalGeometry.isV2(critical.getCriticalState());
            piece(
                CriticalLoomStructure.PIECE,
                v2 ? CriticalStructureAlignment.controllerA(critical.isKindSelector()) : 7,
                v2 ? top - critical.getCriticalState()
                    .getControllerY() : 35,
                v2 ? CriticalStructureAlignment.controllerC(critical.isKindSelector()) : 1);
        } else if (controller instanceof MTESteamMineralLogisticsCluster) {
            piece(
                ClusterStructureDef.PIECE_MAIN,
                ClusterStructureDef.mainOffsetA(),
                ClusterStructureDef.mainOffsetB(),
                ClusterStructureDef.mainOffsetC());
        } else if (controller instanceof MTEClusterUnitBase) {
            piece(
                "main",
                invokeInt(controller, "getStructureOffsetA"),
                invokeInt(controller, "getStructureOffsetB"),
                invokeInt(controller, "getStructureOffsetC"));
        } else if (controller instanceof MTEMegaSteamTurbineArray) {
            // Native construct has no exported offset constants; these are piece origins, not copied geometry.
            piece(MegaSteamTurbineStructureDef.STRUCTURE_PIECE_BASE, 6, 5, 0);
            piece(
                MegaSteamTurbineStructureDef.STRUCTURE_PIECE_CAP,
                6,
                MegaSteamTurbineStructureDef.BASE_TOTAL_HEIGHT - 2,
                0);
        } else {
            int a = readInt(controller, "HORIZONTAL_OFF_SET", "OFFSET_X");
            int b = readInt(controller, "VERTICAL_OFF_SET", "OFFSET_Y");
            int c = readInt(controller, "DEPTH_OFF_SET", "OFFSET_Z");
            if (controller instanceof MTESteamHubArray) {
                piece("cap", a, -1, c);
                piece("base", a, b, c);
                piece("stack", a, 1, c);
            } else if (controller instanceof MTEWaterHubArray) {
                piece("base", a, b, c);
                piece("stack", a, 1, c);
            } else piece("main", a, b, c);
        }
    }

    void piece(String name, int a, int b, int c) throws Exception {
        long timing = TestLabPerformance.begin();
        try {
            pieceInternal(name, a, b, c);
        } finally {
            TestLabPerformance.end("structure.definitionScan", timing);
        }
    }

    private void pieceInternal(String name, int a, int b, int c) throws Exception {
        IStructureDefinition definition = (IStructureDefinition) controller.getClass()
            .getMethod("getStructureDefinition")
            .invoke(controller);
        definition.iterate(
            name,
            world,
            ((gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase) controller).getExtendedFacing(),
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            a,
            b,
            c,
            (element, ignoredWorld, x, y, z, ignoredA, ignoredB, ignoredC) -> {
                if (x == tile.getXCoord() && y == tile.getYCoord() && z == tile.getZCoord()) return true;
                Cell cell = new Cell(element, name, x, y, z);
                cells.add(cell);
                if (!elementGroups.containsKey(element)) {
                    elementGroups.put(element, new ArrayList<>());
                    elementOrder.add(element);
                }
                elementGroups.get(element)
                    .add(cell);
                return true;
            });
    }

    boolean buildTick() {
        long timing = TestLabPerformance.begin();
        try {
            return buildTickInternal();
        } finally {
            TestLabPerformance.end("structure.placeBatch", timing);
        }
    }

    private boolean buildTickInternal() {
        for (int budget = 0; budget < 128 && cursor < cells.size(); budget++, cursor++) {
            Cell cell = cells.get(cursor);
            TestLabFixtures.track(world, cell.x, cell.y, cell.z);
            if (cell.element.placeBlock(controller, world, cell.x, cell.y, cell.z, trigger)) placed++;
            else if (!cell.element.check(controller, world, cell.x, cell.y, cell.z)) failed++;
        }
        return cursor == cells.size();
    }

    /** Locate a real compatible port by asking the canonical element to attach the actual hatch. */
    IGregTechTileEntity install(int metaId) throws Exception {
        long timing = TestLabPerformance.begin();
        try {
            return installInternal(metaId);
        } finally {
            TestLabPerformance.end("structure.hatchInstall", timing);
        }
    }

    private IGregTechTileEntity installInternal(int metaId) throws Exception {
        for (IStructureElement element : elementOrder) {
            List<Cell> group = elementGroups.get(element);
            // All cells sharing this immutable element have the same port acceptance; skip air / existing tiles.
            Cell candidate = null;
            for (Cell cell : group)
                if (!world.isAirBlock(cell.x, cell.y, cell.z) && world.getTileEntity(cell.x, cell.y, cell.z) == null) {
                    candidate = cell;
                    break;
                }
            if (candidate == null) continue;
            Cell cell = candidate;
            Block previous = world.getBlock(cell.x, cell.y, cell.z);
            int previousMeta = world.getBlockMetadata(cell.x, cell.y, cell.z);
            IGregTechTileEntity hatch = TestLabFixtures.machine(world, cell.x, cell.y, cell.z, metaId);
            boolean accepted;
            if (hatch.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch) {
                // Redstone compatibility belongs to StructureCheckerMixin.visit, not to element.check or
                // the controller's hatch lists. Ask the real complete checker to form and bind it.
                accepted = TestLabStructure.checkStructure(controller, tile)
                    && ((com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch) hatch.getMetaTileEntity())
                        .getController() == controller;
            } else {
                controller.clearHatches();
                accepted = cell.element.check(controller, world, cell.x, cell.y, cell.z)
                    && attached(controller, hatch.getMetaTileEntity());
            }
            if (accepted) return hatch;
            world.removeTileEntity(cell.x, cell.y, cell.z);
            world.setBlock(cell.x, cell.y, cell.z, previous, previousMeta, 2);
        }
        controller.clearHatches();
        return null;
    }

    private static boolean attached(MTEMultiBlockBase machine, IMetaTileEntity hatch) throws IllegalAccessException {
        if (machine instanceof com.miaokatze.gtsr.common.machine.base.IHubController
            && (hatch instanceof com.miaokatze.gtsr.common.machine.base.MTESteamStorageUnit
                || hatch instanceof com.miaokatze.gtsr.common.machine.base.IHubCacheNode))
            return true;
        for (Class<?> type = machine.getClass(); type != null; type = type.getSuperclass())
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || !Collection.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Collection<?> values = (Collection<?>) field.get(machine);
                if (values != null && (values.contains(hatch) || values.contains(hatch.getBaseMetaTileEntity())))
                    return true;
            }
        return false;
    }

    String describe() {
        return "elements=" + cells.size() + " placed=" + placed + " rejected=" + failed + " cursor=" + cursor;
    }

    /** Failure-only probe. Never scans terrain outside the already planned canonical structure. */
    String diagnoseFailure() {
        final int maxChecks = 8192, maxMismatches = 16;
        StringBuilder result = new StringBuilder("structureDiagnostic{checkBudget=").append(maxChecks)
            .append(" mismatchLimit=")
            .append(maxMismatches)
            .append(" nativeRechecks=1 plannedElements=")
            .append(cells.size())
            .append(" nativeErrors=")
            .append(nativeErrors())
            .append(" acceptedPortsAndTiers=")
            .append(portSummary());
        int checked = 0, mismatches = 0;
        try {
            // Native elements can attach a hatch while checking. Start with empty native lists, and always
            // restore the complete native check afterwards rather than leave diagnostic attachments behind.
            controller.clearHatches();
            for (Cell cell : cells) {
                if (checked >= maxChecks || mismatches >= maxMismatches) break;
                checked++;
                boolean matches;
                String exception = "";
                try {
                    matches = cell.element.check(controller, world, cell.x, cell.y, cell.z);
                } catch (RuntimeException | LinkageError failure) {
                    matches = false;
                    exception = " checkException=" + failure.getClass()
                        .getName() + ':' + failure.getMessage();
                }
                if (matches) continue;
                mismatches++;
                result.append(" mismatch{")
                    .append("piece=")
                    .append(cell.piece)
                    .append(" pos=")
                    .append(cell.x)
                    .append(',')
                    .append(cell.y)
                    .append(',')
                    .append(cell.z)
                    .append(" element=")
                    .append(
                        cell.element.getClass()
                            .getName())
                    .append(' ')
                    .append(actual(cell.x, cell.y, cell.z))
                    .append(exception)
                    .append('}');
            }
            result.append(" checked=")
                .append(checked)
                .append(" mismatchesReported=")
                .append(mismatches)
                .append(" classification=")
                .append(
                    mismatches > 0 ? "LOCAL_ELEMENT_MISMATCH"
                        : checked == cells.size() ? "GLOBAL_CONSTRAINTS_ALL_PLANNED_ELEMENTS_MATCH"
                            : "PARTIAL_NO_LOCAL_MISMATCH_WITHIN_BUDGET")
                .append(" truncated=")
                .append(checked < cells.size());
        } catch (RuntimeException | LinkageError failure) {
            result.append(" diagnosticException=")
                .append(
                    failure.getClass()
                        .getName())
                .append(':')
                .append(failure.getMessage());
        } finally {
            try {
                result.append(" restoredNativeFormation=")
                    .append(TestLabStructure.checkStructure(controller, tile));
            } catch (RuntimeException | LinkageError failure) {
                result.append(" nativeRestoreException=")
                    .append(
                        failure.getClass()
                            .getName())
                    .append(':')
                    .append(failure.getMessage());
            }
        }
        return result.append('}')
            .toString();
    }

    private String actual(int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        StringBuilder result = new StringBuilder("block=").append(Block.blockRegistry.getNameForObject(block))
            .append(" meta=")
            .append(world.getBlockMetadata(x, y, z));
        net.minecraft.tileentity.TileEntity found = world.getTileEntity(x, y, z);
        if (found instanceof IGregTechTileEntity) {
            IGregTechTileEntity gt = (IGregTechTileEntity) found;
            result.append(" gtMteId=")
                .append(gt.getMetaTileID())
                .append(" gtMteClass=")
                .append(
                    gt.getMetaTileEntity() == null ? "null"
                        : gt.getMetaTileEntity()
                            .getClass()
                            .getName());
        } else if (found != null) result.append(" tileClass=")
            .append(
                found.getClass()
                    .getName());
        return result.toString();
    }

    private String nativeErrors() {
        try {
            Field field = MTEMultiBlockBase.class.getDeclaredField("structureErrors");
            field.setAccessible(true);
            Collection<?> errors = (Collection<?>) field.get(controller);
            StringBuilder result = new StringBuilder("[");
            int count = 0;
            for (Object error : errors) {
                if (count++ == 16) {
                    result.append("truncated");
                    break;
                }
                result.append('{')
                    .append(
                        error.getClass()
                            .getName());
                try {
                    result.append(" id=")
                        .append(
                            error.getClass()
                                .getMethod("getId")
                                .invoke(error));
                } catch (ReflectiveOperationException ignored) {}
                Integer x = null, y = null, z = null;
                for (Class<?> type = error.getClass(); type != null; type = type.getSuperclass())
                    for (Field detail : type.getDeclaredFields()) {
                        String name = detail.getName();
                        if (!name.equals("x") && !name.equals("y")
                            && !name.equals("z")
                            && !name.equals("expectedBlocks")) continue;
                        detail.setAccessible(true);
                        Object value = detail.get(error);
                        result.append(' ')
                            .append(name)
                            .append('=')
                            .append(value);
                        if (value instanceof Integer) {
                            if (name.equals("x")) x = (Integer) value;
                            if (name.equals("y")) y = (Integer) value;
                            if (name.equals("z")) z = (Integer) value;
                        }
                    }
                if (x != null && y != null && z != null) result.append(' ')
                    .append(actual(x, y, z));
                result.append('}');
            }
            return result.append(']')
                .toString();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return "unavailable:" + failure.getClass()
                .getName();
        }
    }

    private String portSummary() {
        StringBuilder result = new StringBuilder("[");
        int count = 0;
        for (Class<?> type = controller.getClass(); type != null; type = type.getSuperclass())
            for (Field field : type.getDeclaredFields()) {
                if (count >= 48) return result.append("truncated]")
                    .toString();
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                String name = field.getName()
                    .toLowerCase(java.util.Locale.ROOT);
                boolean ports = name.contains("hatch") || name.contains("bus")
                    || name.contains("steaminput")
                    || name.contains("steamoutput");
                boolean tierOrCount = name.contains("tier") || name.contains("casingcount")
                    || name.contains("casingamount")
                    || name.contains("unitcount");
                if (!ports && !tierOrCount) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(controller);
                    if (value instanceof Collection) {
                        Collection<?> entries = (Collection<?>) value;
                        java.util.Map<String, Integer> types = new java.util.TreeMap<>();
                        int sampled = 0;
                        for (Object entry : entries) {
                            if (sampled++ == 32) break;
                            if (entry instanceof IMetaTileEntity) types.merge(
                                entry.getClass()
                                    .getName(),
                                1,
                                Integer::sum);
                        }
                        result.append(field.getName())
                            .append('=')
                            .append(entries.size())
                            .append(types)
                            .append(entries.size() > 32 ? "(typesSampled32)" : "")
                            .append(';');
                        count++;
                    } else if (value instanceof Number) {
                        result.append(field.getName())
                            .append('=')
                            .append(value)
                            .append(';');
                        count++;
                    }
                } catch (IllegalAccessException | RuntimeException ignored) {}
            }
        return result.append(']')
            .toString();
    }

    static int invokeInt(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) try {
            Method method = type.getDeclaredMethod(name);
            method.setAccessible(true);
            return ((Number) method.invoke(target)).intValue();
        } catch (NoSuchMethodException ignored) {}
        throw new NoSuchMethodException(
            target.getClass()
                .getName() + "."
                + name);
    }

    static int readInt(Object target, String... names) throws Exception {
        for (String name : names)
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return ((Number) field.get(target)).intValue();
            } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(
            target.getClass()
                .getName() + ":"
                + java.util.Arrays.toString(names));
    }

    private static final class Cell {

        final IStructureElement element;
        final String piece;
        final int x, y, z;

        Cell(IStructureElement element, String piece, int x, int y, int z) {
            this.element = element;
            this.piece = piece;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
