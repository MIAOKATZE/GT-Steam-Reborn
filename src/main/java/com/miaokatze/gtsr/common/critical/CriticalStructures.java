package com.miaokatze.gtsr.common.critical;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
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

    private static final Map<CriticalMachineKind, IStructureDefinition<MTECriticalMachine>> DEFINITIONS = new EnumMap<>(
        CriticalMachineKind.class);
    // NORTH's StructureLib axes are WEST, DOWN, SOUTH. Shape X and Y are reversed accordingly.
    private static final int MAX_X = 76, MAX_Z = 76;

    private CriticalStructures() {}

    public static synchronized IStructureDefinition<MTECriticalMachine> definition(MTECriticalMachine machine) {
        return machine.isKindSelector() ? CriticalLoomStructure.definition() : fullDefinition(machine);
    }

    public static synchronized IStructureDefinition<MTECriticalMachine> fullDefinition(MTECriticalMachine machine) {
        CriticalMachineKind kind = machine.getCriticalState()
            .getKind();
        return DEFINITIONS.computeIfAbsent(kind, CriticalStructures::create);
    }

    public static List<CriticalGeometry.Voxel> voxels(CriticalTier tier, CriticalMachineKind kind) {
        Map<Long, CriticalGeometry.Voxel> positions = new LinkedHashMap<>();
        for (CriticalGeometry.Voxel voxel : CriticalGeometry.getLoom()) positions.put(voxel.positionKey(), voxel);
        for (CriticalGeometry.Voxel voxel : CriticalGeometry.get(tier, kind)) positions.put(voxel.positionKey(), voxel);
        positions.remove(CriticalGeometry.Voxel.positionKey(40, 0, 32));
        return new ArrayList<>(positions.values());
    }

    public static List<CriticalGeometry.Voxel> hatchPorts(CriticalTier tier, CriticalMachineKind kind) {
        List<CriticalGeometry.Voxel> ports = voxels(tier, kind);
        ports.removeIf(
            v -> v.stage >= 4 || v.y != 0
                || !(v.block instanceof CriticalMaterials.MaterialBlock material)
                || material.proxy
                || material.transparent
                || material.animated
                || CriticalGeometry.getModel(v.model).animations.has(v.group));
        ports.sort(Comparator.comparingInt(v -> Math.abs(v.x - 40) + Math.abs(v.z - 32)));
        Map<Long, CriticalGeometry.Voxel> combined = new LinkedHashMap<>();
        for (CriticalGeometry.Voxel port : CriticalLoomStructure.hatchPorts()) combined.put(port.positionKey(), port);
        for (CriticalGeometry.Voxel port : ports.subList(0, Math.min(8, ports.size())))
            combined.put(port.positionKey(), port);
        return Collections.unmodifiableList(new ArrayList<>(combined.values()));
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
                    builder.addElement(character, hatch ? ofChain(material, new PaidHatchElement()) : material);
                }
                grid[voxel.z][top - voxel.y][MAX_X - voxel.x] = character;
            }
            grid[32][top][MAX_X - 40] = '~';
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
        if (!isPreviewWorld(world)) {
            machine.getCriticalState()
                .construct(trigger, hintsOnly);
            return;
        }
        CriticalMachineKind kind = machine.getCriticalState()
            .getKind();
        CriticalTier tier = CriticalTier
            .byId(trigger == null ? 0 : trigger.stackSize - (machine.isKindSelector() ? 2 : 1));
        int top = top(voxels(tier, kind));
        fullDefinition(machine).buildOrHints(
            machine,
            trigger,
            piece(tier),
            world,
            ExtendedFacing.DEFAULT,
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            MAX_X - 40,
            top,
            32,
            hintsOnly);
        if (!hintsOnly) previewPlugins(
            machine.getCriticalState()
                .getConfiguration(),
            tier,
            tile,
            world);
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
                        tile.getZCoord() - 32
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

    /** Native hatch matching/catalogue, with the paid construction boundary retained. */
    static final class PaidHatchElement implements IStructureElement<MTECriticalMachine> {

        private volatile BlocksToPlace catalogue;
        private final boolean manual;

        PaidHatchElement() {
            this(false);
        }

        PaidHatchElement(boolean manual) {
            this.manual = manual;
        }

        private final IStructureElement<MTECriticalMachine> nativeElement = buildHatchAdder(MTECriticalMachine.class)
            .anyOf(
                HatchElement.InputBus,
                HatchElement.OutputBus,
                HatchElement.InputHatch,
                HatchElement.OutputHatch,
                HatchElement.Energy,
                HatchElement.Dynamo)
            .hatchClasses(
                MTEHatchInputBus.class,
                MTEHatchOutputBus.class,
                MTEHatchInput.class,
                MTEHatchOutput.class,
                MTEHatchEnergy.class,
                MTEHatchDynamo.class)
            .casingIndex(CriticalControllerTextures.casingIndex())
            .hint(1)
            .build();

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
