package com.miaokatze.gtsr.common.critical;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.common.blocks.ItemMachines;

/** Canonical StructureLib geometry, shared by GT structure viewers and construction hints. */
public final class CriticalStructures {

    private static final Map<String, IStructureDefinition<MTECriticalMachine>> DEFINITIONS = new HashMap<>();
    private static final Map<IStructureDefinition<MTECriticalMachine>, IStructureDefinition<MTECriticalMachine>> ALL_DEFINITIONS = new HashMap<>();
    // NORTH's StructureLib axes are WEST, DOWN, SOUTH. Shape X and Y are reversed accordingly.
    public static final int CONTROLLER_Z = 33;
    private static final int MAX_X = 76, MAX_Z = 76;

    private CriticalStructures() {}

    public static synchronized IStructureDefinition<MTECriticalMachine> definition(MTECriticalMachine machine) {
        IStructureDefinition<MTECriticalMachine> full = fullDefinition(machine);
        return ALL_DEFINITIONS.computeIfAbsent(full, ignored -> {
            IStructureDefinition<MTECriticalMachine> loom = CriticalLoomStructure.definition(machine);
            return new IStructureDefinition<>() {

                private IStructureDefinition<MTECriticalMachine> forPiece(String piece) {
                    return CriticalLoomStructure.PIECE.equals(piece) ? loom : full;
                }

                @Override
                public IStructureElement<MTECriticalMachine>[] getStructureFor(String piece) {
                    return forPiece(piece).getStructureFor(piece);
                }

                @Override
                public boolean isContainedInStructure(String piece, int a, int b, int c) {
                    return forPiece(piece).isContainedInStructure(piece, a, b, c);
                }
            };
        });
    }

    public static synchronized IStructureDefinition<MTECriticalMachine> fullDefinition(MTECriticalMachine machine) {
        CriticalMachineKind kind = machine.getCriticalState()
            .getKind();
        if (CriticalGeometry.isV2(machine.getCriticalState())) return DEFINITIONS.computeIfAbsent(
            CriticalGeometry.visualMachineSlug(machine.getCriticalState()) + ':'
                + CriticalGeometry.visualLoomSlug(machine.getCriticalState())
                + ':'
                + machine.getLocalCriticalState()
                    .getControllerZ()
                + ':'
                + machine.getCriticalState()
                    .getParallelPlugins()
                + ':'
                + machine.getCriticalState()
                    .getSpeedPlugins()
                + ':'
                + machine.getCriticalState()
                    .getEfficiencyPlugins(),
            key -> createV2(machine.getCriticalState(), false, machine.isKindSelector()));
        return DEFINITIONS.computeIfAbsent(kind.name(), key -> create(kind));
    }

    public static List<CriticalGeometry.Voxel> voxels(CriticalMachineState state) {
        if (!CriticalGeometry.isV2(state)) return voxels(state.getTier(), state.getKind());
        Map<Long, CriticalGeometry.Voxel> cells = new LinkedHashMap<>();
        if (state.usesLegacyV2Geometry())
            for (CriticalGeometry.Voxel v : CriticalLoomStructure.voxels(state)) cells.put(v.positionKey(), v);
        for (String slug : new String[] { CriticalGeometry.visualBaseSlug(state),
            CriticalGeometry.visualMachineSlug(state) })
            for (CriticalGeometry.Voxel v : CriticalGeometry.getModel(slug).voxels) cells.put(v.positionKey(), v);
        for (CriticalGeometry.Voxel v : CriticalGeometry.physicalPlugins(state)) cells.put(v.positionKey(), v);
        // The complete manual loom owns every shared coordinate; paid bodies and addons cannot reclassify it.
        if (!state.usesLegacyV2Geometry())
            for (CriticalGeometry.Voxel v : CriticalLoomStructure.voxels(state)) cells.put(v.positionKey(), v);
        List<CriticalGeometry.Voxel> result = new ArrayList<>(cells.values());
        result.sort(
            java.util.Comparator.comparingInt((CriticalGeometry.Voxel v) -> v.stage)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.x)
                .thenComparingInt(v -> v.z));
        return result;
    }

    static boolean isController(CriticalGeometry.Voxel v) {
        return v.model.startsWith("v2_") && v.x == 56 && v.y == 4 && (v.z == 35 || v.z == 47);
    }

    static IStructureDefinition<MTECriticalMachine> createV2(CriticalMachineState state, boolean loomOnly,
        boolean selector) {
        StructureDefinition.Builder<MTECriticalMachine> builder = StructureDefinition.builder();
        Map<String, Character> characters = new HashMap<>();
        List<CriticalGeometry.Voxel> source = loomOnly ? CriticalLoomStructure.voxels(state) : voxels(state);
        int top = top(source);
        char[][][] grid = new char[112][top + 1][112];
        for (char[][] slice : grid) for (char[] row : slice) java.util.Arrays.fill(row, ' ');
        for (CriticalGeometry.Voxel v : source) {
            String key = isController(v) ? "controller:" + v.z
                : v.material + ':'
                    + ((CriticalMaterials.MaterialBlock) v.block).proxy
                    + ':'
                    + CriticalLoomStructure.isLoomVoxel(v)
                    + ':'
                    + isHatchPosition(v);
            Character symbol = characters.get(key);
            if (symbol == null) {
                symbol = (char) (0x100 + characters.size());
                characters.put(key, symbol);
                IStructureElement<MTECriticalMachine> material = CriticalLoomStructure.isLoomVoxel(v)
                    ? CriticalLoomStructure.materialElement(v)
                    : new MaterialElement(v.block, CriticalMaterials.block(v.material, false));
                builder.addElement(
                    symbol,
                    isController(v) ? new ControllerElement(v.z == 47, state.getKind())
                        : isHatchPosition(v)
                            ? ofChain(material, new PaidHatchElement(CriticalLoomStructure.isLoomVoxel(v)))
                            : material);
            }
            // Each controller declares the same canonical layout in its own native facing axes.
            grid[CriticalStructureAlignment.shapeC(v.z, selector)][top - v.y][CriticalStructureAlignment
                .shapeA(v.x, selector)] = symbol;
        }
        grid[CriticalStructureAlignment.controllerC(selector)][top - state.getControllerY()][CriticalStructureAlignment
            .controllerA(selector)] = '~';
        String[][] shape = new String[112][top + 1];
        for (int z = 0; z < 112; z++) for (int y = 0; y <= top; y++) shape[z][y] = new String(grid[z][y]);
        if (loomOnly) builder.addShape(CriticalLoomStructure.PIECE, shape);
        else for (CriticalTier tier : CriticalTier.values()) builder.addShape(piece(tier), shape);
        return builder.build();
    }

    static final class ControllerElement implements IStructureElement<MTECriticalMachine> {

        private final boolean selector;
        private final CriticalMachineKind kind;
        private final net.minecraftforge.common.util.ForgeDirection frame;

        ControllerElement(boolean selector, CriticalMachineKind kind) {
            this(selector, kind, net.minecraftforge.common.util.ForgeDirection.NORTH);
        }

        ControllerElement(boolean selector, CriticalMachineKind kind,
            net.minecraftforge.common.util.ForgeDirection frame) {
            this.selector = selector;
            this.kind = kind;
            this.frame = frame;
        }

        private net.minecraftforge.common.util.ForgeDirection facing(MTECriticalMachine machine) {
            net.minecraftforge.common.util.ForgeDirection direction = machine == null ? frame
                : machine.getCriticalState()
                    .getStructureFacing();
            return selector ? direction : direction.getOpposite();
        }

        @Override
        public boolean check(MTECriticalMachine machine, World world, int x, int y, int z) {
            return world.getTileEntity(x, y, z) instanceof IGregTechTileEntity gt
                && gt.getMetaTileEntity() instanceof MTECriticalMachine other
                && other.isKindSelector() == selector
                && (selector || other.getPresetKind() == kind)
                && (selector || machine == null
                    || isPreviewWorld(world)
                    || !machine.getCriticalState()
                        .isLoomJournalOwner()
                    || other.getLocalCriticalState()
                        .isLoomOwnedMainOf(machine.getCriticalState()))
                && gt.getFrontFacing() == facing(machine);
        }

        private ItemStack stack() {
            return selector ? CriticalRuntime.getLoomControllerStack()
                : kind == CriticalMachineKind.SOLAR
                    ? com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSolar.get(1)
                    : com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSun.get(1);
        }

        @Override
        public boolean spawnHint(MTECriticalMachine m, World w, int x, int y, int z, ItemStack trigger) {
            StructureLibAPI.hintParticle(w, x, y, z, gregtech.api.GregTechAPI.sBlockMachines, stack().getItemDamage());
            return true;
        }

        @Override
        public boolean placeBlock(MTECriticalMachine m, World w, int x, int y, int z, ItemStack trigger) {
            if (!isPreviewWorld(w)) return false;
            return placeController(m, w, x, y, z);
        }

        /** Paid placement uses GT's real controller registry and initialisation, never a decorative casing. */
        boolean placeController(MTECriticalMachine machine, World w, int x, int y, int z) {
            ItemStack controller = stack();
            if (controller == null || controller.getItemDamage() <= 0
                || controller.getItemDamage() >= GregTechAPI.METATILEENTITIES.length) return false;
            IMetaTileEntity prototype = GregTechAPI.METATILEENTITIES[controller.getItemDamage()];
            boolean preview = isPreviewWorld(w);
            if (!(prototype instanceof MTECriticalMachine) || !preview && (machine == null || !w.isAirBlock(x, y, z)))
                return false;
            Block previous = w.getBlock(x, y, z);
            int previousMetadata = w.getBlockMetadata(x, y, z);
            if (!w.setBlock(x, y, z, GregTechAPI.sBlockMachines, prototype.getTileEntityBaseType(), 2)) return false;
            boolean initialized = false;
            try {
                if (!(w.getTileEntity(x, y, z) instanceof IGregTechTileEntity tile)) return false;
                if (!preview) {
                    IGregTechTileEntity ownerHost = machine.getBaseMetaTileEntity();
                    tile.setOwnerName(ownerHost.getOwnerName());
                    tile.setOwnerUuid(ownerHost.getOwnerUuid());
                }
                tile.setInitialValuesAsNBT(null, (short) controller.getItemDamage());
                tile.setFrontFacing(facing(machine));
                if (!selector && !preview && tile.getMetaTileEntity() instanceof MTECriticalMachine main)
                    main.getLocalCriticalState()
                        .setLoomOwnedMain(machine.getCriticalState());
                initialized = check(machine, w, x, y, z);
                return initialized;
            } catch (RuntimeException failedInitialization) {
                return false;
            } finally {
                if (!initialized) w.setBlock(x, y, z, previous, previousMetadata, 2);
            }
        }

        @Override
        public BlocksToPlace getBlocksToPlace(MTECriticalMachine m, World w, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            return BlocksToPlace.create(stack());
        }
    }

    public static List<CriticalGeometry.Voxel> voxels(CriticalTier tier, CriticalMachineKind kind) {
        Map<Long, CriticalGeometry.Voxel> positions = new LinkedHashMap<>();
        for (CriticalGeometry.Voxel voxel : CriticalGeometry.getLoom()) positions.put(voxel.positionKey(), voxel);
        for (CriticalGeometry.Voxel voxel : CriticalGeometry.get(tier, kind)) positions.put(voxel.positionKey(), voxel);
        positions.remove(CriticalGeometry.Voxel.positionKey(40, 0, CONTROLLER_Z));
        return new ArrayList<>(positions.values());
    }

    /** V2 loom interfaces and its controller-height reinforced shell accept supported GT chambers. */
    public static boolean isHatchPosition(CriticalGeometry.Voxel voxel) {
        if (voxel.model.startsWith("v2_")) {
            if (isController(voxel) || voxel.model.startsWith("v2_plugin_")) return false;
            if ("v2_loom".equals(voxel.model) || "v2_v25_loom".equals(voxel.model) || "v2_v27_loom".equals(voxel.model))
                return voxel.y == 4
                    && (voxel.z == 47 && (voxel.x == 50 || voxel.x == 53 || voxel.x == 59 || voxel.x == 62)
                        || "static".equals(voxel.group) && voxel.x >= 47
                            && voxel.x <= 65
                            && voxel.z >= 47
                            && voxel.z <= 65
                            && voxel.block instanceof CriticalMaterials.MaterialBlock shell
                            && "v2_reinforced_panel".equals(shell.materialId));
            return voxel.block instanceof CriticalMaterials.MaterialBlock block
                && ("v2_alloy_frame".equals(block.materialId) || "v2_service_hatch".equals(block.materialId)
                    || "v2_gt_hatch".equals(block.materialId))
                || "v2_legacy_loom".equals(voxel.model) && voxel.y == 4
                    && voxel.z == 36
                    && (voxel.x == 50 || voxel.x == 53 || voxel.x == 59 || voxel.x == 62);
        }
        return voxel.block instanceof CriticalMaterials.MaterialBlock material
            && "alloy_frame".equals(material.materialId);
    }

    static boolean isPaidChamberPosition(CriticalGeometry.Voxel voxel) {
        return !CriticalLoomStructure.isLoomVoxel(voxel) && isHatchPosition(voxel)
            && voxel.y == 4
            && voxel.z == 34
            && voxel.x >= 50
            && voxel.x <= 62;
    }

    public static List<CriticalGeometry.Voxel> hatchPorts(CriticalTier tier, CriticalMachineKind kind) {
        List<CriticalGeometry.Voxel> ports = voxels(tier, kind);
        ports.removeIf(v -> !isHatchPosition(v));
        return Collections.unmodifiableList(ports);
    }

    private static IStructureDefinition<MTECriticalMachine> create(CriticalMachineKind kind) {
        StructureDefinition.Builder<MTECriticalMachine> builder = StructureDefinition.builder();
        Map<String, Character> characters = new HashMap<>();
        for (CriticalTier tier : CriticalTier.values()) {
            List<CriticalGeometry.Voxel> voxels = voxels(tier, kind);
            int top = top(voxels);
            char[][][] grid = new char[MAX_Z + 1][top + 1][MAX_X + 1];
            for (char[][] slice : grid) for (char[] row : slice) java.util.Arrays.fill(row, ' ');
            java.util.Set<Long> ports = new java.util.HashSet<>();
            for (CriticalGeometry.Voxel port : hatchPorts(tier, kind)) ports.add(port.positionKey());
            for (CriticalGeometry.Voxel voxel : voxels) {
                boolean hatch = ports.contains(voxel.positionKey());
                boolean loom = CriticalLoomStructure.isLoomVoxel(voxel);
                String key = voxel.material + ':'
                    + ((CriticalMaterials.MaterialBlock) voxel.block).proxy
                    + ':'
                    + hatch
                    + ':'
                    + loom;
                Character character = characters.get(key);
                if (character == null) {
                    character = (char) (0x100 + characters.size());
                    characters.put(key, character);
                    IStructureElement<MTECriticalMachine> material = loom ? CriticalLoomStructure.materialElement(voxel)
                        : new MaterialElement(voxel.block, CriticalMaterials.block(voxel.material, false));
                    builder.addElement(
                        character,
                        hatch
                            ? (loom ? loomHatchElement(CriticalLoomStructure.shell(voxel))
                                : ofChain(material, new PaidHatchElement()))
                            : material);
                }
                grid[voxel.z][top - voxel.y][MAX_X - voxel.x] = character;
            }
            grid[CONTROLLER_Z][top][MAX_X - 40] = '~';
            String[][] shape = new String[grid.length][top + 1];
            for (int z = 0; z < grid.length; z++) for (int y = 0; y <= top; y++) shape[z][y] = new String(grid[z][y]);
            builder.addShape(piece(tier), shape);
        }
        return builder.build();
    }

    private static int top(List<CriticalGeometry.Voxel> voxels) {
        int top = 0;
        for (CriticalGeometry.Voxel voxel : voxels) top = Math.max(top, voxel.y);
        return top;
    }

    public static String piece(CriticalTier tier) {
        return "critical_" + tier.name();
    }

    /** Only BlockRenderer's isolated preview worlds may receive unpaid visual blocks. */
    public static boolean isPreviewWorld(World world) {
        if (world == null) return false;
        for (Class<?> type = world.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName()
                .equals("blockrenderer6343.client.world.DummyWorld")) return true;
        }
        return false;
    }

    public static void construct(MTECriticalMachine machine, ItemStack trigger, boolean hintsOnly) {
        IGregTechTileEntity tile = machine.getBaseMetaTileEntity();
        if (tile == null) return;
        World world = tile.getWorld();
        if (machine.isKindSelector() && (trigger == null || trigger.stackSize <= 1)) {
            CriticalLoomStructure.construct(machine, trigger, hintsOnly);
            return;
        }
        if (!isPreviewWorld(world) && !hintsOnly) {
            // The loom is player-built for every controller and every projection tier.
            CriticalLoomStructure.construct(machine, trigger, hintsOnly);
            return;
        }
        CriticalMachineKind kind = machine.getCriticalState()
            .getKind();
        CriticalTier tier = CriticalTier
            .byId(trigger == null ? 0 : trigger.stackSize - (machine.isKindSelector() ? 2 : 1));
        CriticalMachineState current = machine.getCriticalState();
        if (CriticalGeometry.isV2(current)) {
            int top = top(voxels(current));
            machine.buildCriticalPiece(
                piece(tier),
                trigger,
                hintsOnly,
                CriticalStructureAlignment.controllerA(machine.isKindSelector()),
                top - current.getControllerY(),
                CriticalStructureAlignment.controllerC(machine.isKindSelector()));
            if (!hintsOnly) for (CriticalGeometry.Voxel v : CriticalGeometry.physicalPlugins(current))
                world.setBlock(current.blockWorldX(v), current.blockWorldY(v.y), current.blockWorldZ(v), v.block, 0, 2);
            return;
        }
        int top = top(voxels(tier, kind));
        machine.buildCriticalPiece(piece(tier), trigger, hintsOnly, MAX_X - 40, top, CONTROLLER_Z);
        if (!hintsOnly) previewPlugins(
            machine.getCriticalState()
                .getConfiguration(),
            tier,
            tile,
            world);
    }

    /** Only the permanent loom may be built from the player's inventory; target bodies remain funded. */
    public static int survivalConstruct(MTECriticalMachine machine, ItemStack trigger, int budget,
        ISurvivalBuildEnvironment env) {
        IGregTechTileEntity tile = machine.getBaseMetaTileEntity();
        if (tile != null && isPreviewWorld(tile.getWorld())) {
            // BlockRenderer takes only the survival callback for ISurvivalConstructable controllers.
            // Its isolated world gets the selected full preview; live survival remains loom-only.
            machine.constructPreview(trigger);
            return -1;
        }
        return CriticalLoomStructure.survivalConstruct(machine, trigger, budget, env);
    }

    /** Installed plugins are virtual meshes in the live machine, never required physical structure blocks. */
    private static void previewPlugins(CriticalConfiguration configuration, CriticalTier tier, IGregTechTileEntity tile,
        World world) {
        String tierKey = tier.name()
            .toLowerCase(java.util.Locale.ROOT);
        com.google.gson.JsonArray slots = CriticalGeometry.getModel("base_" + tierKey).meta
            .getAsJsonArray("pluginSlots");
        int[] counts = { configuration.parallel, configuration.speed, configuration.economy };
        String[] names = { "parallel", "speed", "efficiency" };
        int slot = 0;
        for (int kind = 0; kind < names.length; kind++) {
            for (int count = 0; count < counts[kind] && slot < 8; count++, slot++) {
                CriticalGeometry.Model model = CriticalGeometry.getModel("plugin_" + names[kind] + '_' + tierKey);
                com.google.gson.JsonArray position = slots.get(slot)
                    .getAsJsonArray();
                int offsetY = model.meta.has("installOffsetY") ? model.meta.get("installOffsetY")
                    .getAsInt() : 12;
                for (CriticalGeometry.Voxel voxel : model.voxels) {
                    world.setBlock(
                        tile.getXCoord() - 40
                            + position.get(0)
                                .getAsInt()
                            + voxel.x,
                        tile.getYCoord() + offsetY + voxel.y,
                        tile.getZCoord() - CONTROLLER_Z
                            + position.get(1)
                                .getAsInt()
                            + voxel.z,
                        CriticalMaterials.block(voxel.material, false),
                        0,
                        2);
                }
            }
        }
    }

    private static final class MaterialElement implements IStructureElement<MTECriticalMachine> {

        private final Block actual, visible;

        private MaterialElement(Block actual, Block visible) {
            this.actual = actual;
            this.visible = visible;
        }

        @Override
        public boolean check(MTECriticalMachine machine, World world, int x, int y, int z) {
            return world.getBlock(x, y, z) == actual && world.getBlockMetadata(x, y, z) == 0;
        }

        @Override
        public boolean spawnHint(MTECriticalMachine machine, World world, int x, int y, int z, ItemStack trigger) {
            StructureLibAPI.hintParticle(world, x, y, z, visible, 0);
            return true;
        }

        @Override
        public boolean placeBlock(MTECriticalMachine machine, World world, int x, int y, int z, ItemStack trigger) {
            return isPreviewWorld(world) && world.setBlock(x, y, z, visible, 0, 2);
        }

        @Override
        public BlocksToPlace getBlocksToPlace(MTECriticalMachine machine, World world, int x, int y, int z,
            ItemStack trigger, AutoPlaceEnvironment env) {
            return BlocksToPlace.create(visible, 0);
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTECriticalMachine machine, World world, int x, int y, int z,
            ItemStack trigger, AutoPlaceEnvironment env) {
            return check(machine, world, x, y, z) ? PlaceResult.SKIP : PlaceResult.REJECT;
        }
    }

    static IStructureElement<MTECriticalMachine> loomHatchElement(Block shell) {
        return buildHatchAdder(MTECriticalMachine.class)
            .anyOf(
                HatchElement.InputBus,
                HatchElement.OutputBus,
                HatchElement.InputHatch,
                HatchElement.OutputHatch,
                HatchElement.Energy,
                HatchElement.MultiAmpEnergy,
                HatchElement.Dynamo)
            .casingIndex(CriticalControllerTextures.casingIndex())
            .hint(1)
            .buildAndChain(shell, 0);
    }

    /** Native hatch matching/catalogue, with the paid construction boundary retained. */
    static final class PaidHatchElement implements IStructureElement<MTECriticalMachine> {

        private volatile BlocksToPlace catalogue;
        private final boolean manual;

        PaidHatchElement() {
            this(false, false);
        }

        PaidHatchElement(boolean manual) {
            this(manual, true);
        }

        private PaidHatchElement(boolean manual, boolean v2) {
            this.manual = manual;
            nativeElement = nativeElement(v2);
        }

        private final IStructureElement<MTECriticalMachine> nativeElement;

        private static IStructureElement<MTECriticalMachine> nativeElement(boolean v2) {
            return buildHatchAdder(MTECriticalMachine.class)
                .anyOf(
                    HatchElement.InputBus,
                    HatchElement.OutputBus,
                    HatchElement.InputHatch,
                    HatchElement.OutputHatch,
                    HatchElement.Energy,
                    HatchElement.MultiAmpEnergy,
                    HatchElement.Dynamo)
                .hatchClasses(
                    MTEHatchInputBus.class,
                    MTEHatchOutputBus.class,
                    MTEHatchInput.class,
                    MTEHatchOutput.class,
                    MTEHatchEnergy.class,
                    tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti.class,
                    MTEHatchDynamo.class)
                .casingIndex(CriticalControllerTextures.casingIndex(v2))
                .hint(1)
                .build();
        }

        @Override
        public boolean check(MTECriticalMachine machine, World world, int x, int y, int z) {
            return couldBeValid(machine, world, x, y, z, null) && nativeElement.check(machine, world, x, y, z);
        }

        @Override
        public boolean couldBeValid(MTECriticalMachine machine, World world, int x, int y, int z, ItemStack trigger) {
            if (!(world.getTileEntity(x, y, z) instanceof IGregTechTileEntity tile)
                || !CriticalMachineState.isSupportedHatchMeta(tile.getMetaTileEntity())) return false;
            return nativeElement.couldBeValid(machine, world, x, y, z, trigger);
        }

        @Override
        public boolean spawnHint(MTECriticalMachine machine, World world, int x, int y, int z, ItemStack trigger) {
            return nativeElement.spawnHint(machine, world, x, y, z, trigger);
        }

        @Override
        public boolean placeBlock(MTECriticalMachine machine, World world, int x, int y, int z, ItemStack trigger) {
            return manual && nativeElement.placeBlock(machine, world, x, y, z, trigger);
        }

        @Override
        public synchronized BlocksToPlace getBlocksToPlace(MTECriticalMachine machine, World world, int x, int y, int z,
            ItemStack trigger, AutoPlaceEnvironment env) {
            if (catalogue != null) return catalogue;
            BlocksToPlace nativeBlocks = nativeElement.getBlocksToPlace(machine, world, x, y, z, trigger, env);
            if (nativeBlocks == null) return catalogue = BlocksToPlace.createEmpty();
            List<ItemStack> stacks = new ArrayList<>();
            if (nativeBlocks.getStacks() != null) {
                for (ItemStack stack : nativeBlocks.getStacks()) {
                    if (stack != null
                        && CriticalMachineState.isSupportedHatchMeta(ItemMachines.getMetaTileEntity(stack)))
                        stacks.add(stack.copy());
                }
            }
            // Native hatch elements expose a predicate. BlockRenderer 1.4.24 reads only getStacks(),
            // so enumerate the frozen GT registry once, when the first NEI scan requests a catalogue.
            if (nativeBlocks.getStacks() == null && nativeBlocks.getPredicate() != null) {
                for (IMetaTileEntity mte : GregTechAPI.METATILEENTITIES) {
                    if (!CriticalMachineState.isSupportedHatchMeta(mte)) continue;
                    ItemStack stack = mte.getStackForm(1);
                    if (stack != null && nativeBlocks.getPredicate()
                        .test(stack)) stacks.add(stack);
                }
            }
            return catalogue = BlocksToPlace.create(Collections.unmodifiableList(stacks));
        }

        @Override
        public List<String> getDescription(MTECriticalMachine machine) {
            return nativeElement.getDescription(machine);
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTECriticalMachine machine, World world, int x, int y, int z,
            ItemStack trigger, AutoPlaceEnvironment env) {
            return manual ? nativeElement.survivalPlaceBlock(machine, world, x, y, z, trigger, env)
                : PlaceResult.REJECT;
        }
    }
}
