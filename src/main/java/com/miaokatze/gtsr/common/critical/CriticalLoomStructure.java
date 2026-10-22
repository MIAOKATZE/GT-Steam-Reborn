package com.miaokatze.gtsr.common.critical;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** The permanent, player-built fabrication multiblock; never owned by a target construction journal. */
public final class CriticalLoomStructure {

    public static final String PIECE = "critical_loom";
    private static IStructureDefinition<MTECriticalMachine> definition;
    private static final Map<String, IStructureDefinition<MTECriticalMachine>> v2Definitions = new HashMap<>();

    private CriticalLoomStructure() {}

    public static List<CriticalGeometry.Voxel> voxels() {
        return CriticalCompiledLayout.v1(CriticalTier.T3, CriticalMachineKind.SUN)
            .loomVoxels();
    }

    public static List<CriticalGeometry.Voxel> voxels(CriticalMachineState state) {
        return CriticalCompiledLayout.forState(state)
            .loomVoxels();
    }

    public static boolean isLoomVoxel(CriticalGeometry.Voxel voxel) {
        return CriticalLayoutDefinition.isLoom(voxel);
    }

    private static int top(CriticalMachineState state) {
        return CriticalCompiledLayout.forState(state)
            .loomTop();
    }

    public static List<CriticalGeometry.Voxel> hatchPorts() {
        List<CriticalGeometry.Voxel> result = new ArrayList<>(voxels());
        result.removeIf(v -> !CriticalStructures.isHatchPosition(v));
        return Collections.unmodifiableList(result);
    }

    public static boolean matchesVoxel(World world, int originX, int originY, int originZ,
        CriticalGeometry.Voxel voxel) {
        return matchesVoxel(world, originX, originY, originZ, voxel, CriticalMachineKind.SUN);
    }

    public static boolean matchesVoxel(World world, int originX, int originY, int originZ, CriticalGeometry.Voxel voxel,
        CriticalMachineKind kind) {
        int x = originX + voxel.x, y = originY + voxel.y, z = originZ + voxel.z;
        if (!world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return false;
        if (CriticalStructures.isController(voxel))
            return new CriticalStructures.ControllerElement(voxel.z == 47, kind).check(null, world, x, y, z);
        if (CriticalStructures.isHatchPosition(voxel)
            && CriticalMachineState.isLoomHatchHost(world.getTileEntity(x, y, z))) return true;
        return CriticalMaterials.matchesLoomVoxel(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z), voxel);
    }

    public static boolean matchesVoxel(CriticalMachineState state, CriticalGeometry.Voxel voxel) {
        World world = state.getWorldObj();
        int x = state.blockWorldX(voxel), y = state.blockWorldY(voxel.y), z = state.blockWorldZ(voxel);
        if (world == null || !world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return false;
        if (CriticalStructures.isController(voxel))
            return new CriticalStructures.ControllerElement(voxel.z == 47, state.getKind(), state.getStructureFacing())
                .check(null, world, x, y, z);
        if (CriticalStructures.isHatchPosition(voxel)
            && CriticalMachineState.isLoomHatchHost(world.getTileEntity(x, y, z))) return true;
        return CriticalMaterials.matchesLoomVoxel(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z), voxel);
    }

    public static Block shell(CriticalGeometry.Voxel voxel) {
        return voxel.block;
    }

    public static synchronized IStructureDefinition<MTECriticalMachine> definition() {
        if (definition != null) return definition;
        StructureDefinition.Builder<MTECriticalMachine> builder = StructureDefinition.builder();
        Map<String, Character> characters = new HashMap<>();
        // NORTH axes are WEST, DOWN, SOUTH. Include the controller immediately before the cylinder.
        int sizeZ = CriticalLayoutDefinition.V1_LOOM_SIZE_Z, sizeX = CriticalLayoutDefinition.V1_LOOM_SIZE_X;
        int top = CriticalLayoutDefinition.V1_LOOM_TOP;
        char[][][] grid = new char[sizeZ][top + 1][sizeX];
        for (char[][] slice : grid) for (char[] row : slice) java.util.Arrays.fill(row, ' ');
        for (CriticalGeometry.Voxel voxel : voxels()) {
            boolean port = CriticalStructures.isHatchPosition(voxel);
            String key = voxel.material + ':' + port;
            Character symbol = characters.get(key);
            if (symbol == null) {
                symbol = (char) (0x100 + characters.size());
                characters.put(key, symbol);
                IStructureElement<MTECriticalMachine> material = materialElement(voxel);
                builder.addElement(symbol, port ? CriticalStructures.loomHatchElement(shell(voxel)) : material);
            }
            grid[voxel.z - CriticalLayoutDefinition.V1_LOOM_MIN_Z][top - voxel.y][CriticalLayoutDefinition.V1_LOOM_MAX_X
                - voxel.x] = symbol;
        }
        grid[CriticalLayoutDefinition.V1_LOOM_CONTROLLER_C][top][CriticalLayoutDefinition.V1_LOOM_CONTROLLER_A] = '~';
        String[][] shape = new String[sizeZ][top + 1];
        for (int z = 0; z < sizeZ; z++) for (int y = 0; y <= top; y++) shape[z][y] = new String(grid[z][y]);
        IStructureDefinition<MTECriticalMachine> result = builder.addShape(PIECE, shape)
            .build();
        return definition = result;
    }

    public static synchronized IStructureDefinition<MTECriticalMachine> definition(MTECriticalMachine host) {
        CriticalMachineState state = host.getCriticalState();
        return CriticalGeometry.isV2(state) ? v2Definitions.computeIfAbsent(
            CriticalGeometry.visualLoomSlug(state) + ':' + host.isKindSelector() + ':' + state.getKind(),
            ignored -> CriticalStructures.createV2(state, true, host.isKindSelector())) : definition();
    }

    public static boolean validate(MTECriticalMachine host) {
        return validateGeometry(host) && validateInterfaces(host);
    }

    /** Geometry forms independently of recipe and construction resource availability. */
    public static boolean validateGeometry(MTECriticalMachine host) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        if (tile == null) return false;
        World world = tile.getWorld();
        CriticalMachineState local = host.getCriticalState();
        if (CriticalGeometry.isV2(local) && tile.getFrontFacing() != local.getControllerFacing(host.isKindSelector()))
            return false;
        for (CriticalGeometry.Voxel voxel : voxels(local)) if (!matchesVoxel(local, voxel)) return false;
        int top = CriticalGeometry.isV2(local) ? top(local) : CriticalLayoutDefinition.V1_LOOM_TOP;
        if (!definition(host).check(
            host,
            PIECE,
            world,
            host.getExtendedFacing(),
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            CriticalGeometry.isV2(local) ? CriticalStructureAlignment.controllerA(host.isKindSelector())
                : CriticalLayoutDefinition.V1_LOOM_CONTROLLER_A,
            top - local.getControllerY(),
            CriticalGeometry.isV2(local) ? CriticalStructureAlignment.controllerC(host.isKindSelector())
                : CriticalLayoutDefinition.V1_LOOM_CONTROLLER_C,
            false)) return false;
        return true;
    }

    public static boolean validateInterfaces(MTECriticalMachine host) {
        // Chambers are optional for formation; each operation still pays through the journal.
        return true;
    }

    public static void construct(MTECriticalMachine host, ItemStack trigger, boolean hintsOnly) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        if (tile == null) return;
        CriticalMachineState local = host.getCriticalState();
        host.buildCriticalPiece(
            PIECE,
            trigger,
            hintsOnly,
            CriticalGeometry.isV2(local) ? CriticalStructureAlignment.controllerA(host.isKindSelector())
                : CriticalLayoutDefinition.V1_LOOM_CONTROLLER_A,
            CriticalGeometry.isV2(local) ? top(local) - local.getControllerY() : CriticalLayoutDefinition.V1_LOOM_TOP,
            CriticalGeometry.isV2(local) ? CriticalStructureAlignment.controllerC(host.isKindSelector())
                : CriticalLayoutDefinition.V1_LOOM_CONTROLLER_C);
    }

    public static int survivalConstruct(MTECriticalMachine host, ItemStack trigger, int budget,
        ISurvivalBuildEnvironment env) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        if (tile == null) return -1;
        CriticalMachineState local = host.getCriticalState();
        return host.survivalBuildCriticalPiece(
            PIECE,
            trigger,
            CriticalGeometry.isV2(local) ? CriticalStructureAlignment.controllerA(host.isKindSelector())
                : CriticalLayoutDefinition.V1_LOOM_CONTROLLER_A,
            CriticalGeometry.isV2(local) ? top(local) - local.getControllerY() : CriticalLayoutDefinition.V1_LOOM_TOP,
            CriticalGeometry.isV2(local) ? CriticalStructureAlignment.controllerC(host.isKindSelector())
                : CriticalLayoutDefinition.V1_LOOM_CONTROLLER_C,
            budget,
            env);
    }

    /** Native elements remain visible to StructureLib-aware construction tools. */
    static IStructureElement<MTECriticalMachine> materialElement(CriticalGeometry.Voxel voxel) {
        return ofBlock(shell(voxel), 0);
    }
}
