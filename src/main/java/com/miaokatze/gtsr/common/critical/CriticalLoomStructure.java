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

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/** The permanent, player-built fabrication multiblock; never owned by a target construction journal. */
public final class CriticalLoomStructure {

    public static final String PIECE = "critical_loom";
    private static IStructureDefinition<MTECriticalMachine> definition;

    private CriticalLoomStructure() {}

    public static List<CriticalGeometry.Voxel> voxels() {
        List<CriticalGeometry.Voxel> result = new ArrayList<>(CriticalGeometry.getLoom());
        result.removeIf(v -> v.x == 40 && v.y == 0 && v.z == CriticalStructures.CONTROLLER_Z);
        return Collections.unmodifiableList(result);
    }

    public static boolean isLoomVoxel(CriticalGeometry.Voxel voxel) {
        return "loom".equals(voxel.model);
    }

    public static List<CriticalGeometry.Voxel> hatchPorts() {
        List<CriticalGeometry.Voxel> result = new ArrayList<>(voxels());
        result.removeIf(v -> !CriticalStructures.isHatchPosition(v));
        return Collections.unmodifiableList(result);
    }

    public static boolean matchesVoxel(World world, int originX, int originY, int originZ,
        CriticalGeometry.Voxel voxel) {
        int x = originX + voxel.x, y = originY + voxel.y, z = originZ + voxel.z;
        if (!world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return false;
        if (CriticalStructures.isHatchPosition(voxel)
            && CriticalMachineState.isStandardHatchHost(world.getTileEntity(x, y, z))) return true;
        return CriticalMaterials.matchesLoomVoxel(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z), voxel);
    }

    public static Block shell(CriticalGeometry.Voxel voxel) {
        return CriticalMaterials.loomBlock(voxel.material, ((CriticalMaterials.MaterialBlock) voxel.block).proxy);
    }

    public static synchronized IStructureDefinition<MTECriticalMachine> definition() {
        if (definition != null) return definition;
        StructureDefinition.Builder<MTECriticalMachine> builder = StructureDefinition.builder();
        Map<String, Character> characters = new HashMap<>();
        // NORTH axes are WEST, DOWN, SOUTH. Include the controller immediately before the cylinder.
        char[][][] grid = new char[16][36][15];
        for (char[][] slice : grid) for (char[] row : slice) java.util.Arrays.fill(row, ' ');
        for (CriticalGeometry.Voxel voxel : voxels()) {
            boolean port = CriticalStructures.isHatchPosition(voxel);
            String key = voxel.material + ':' + ((CriticalMaterials.MaterialBlock) voxel.block).proxy + ':' + port;
            Character symbol = characters.get(key);
            if (symbol == null) {
                symbol = (char) (0x100 + characters.size());
                characters.put(key, symbol);
                IStructureElement<MTECriticalMachine> material = materialElement(voxel);
                builder.addElement(symbol, port ? CriticalStructures.loomHatchElement(shell(voxel)) : material);
            }
            grid[voxel.z - 32][35 - voxel.y][47 - voxel.x] = symbol;
        }
        grid[1][35][7] = '~';
        String[][] shape = new String[16][36];
        for (int z = 0; z < 16; z++) for (int y = 0; y < 36; y++) shape[z][y] = new String(grid[z][y]);
        IStructureDefinition<MTECriticalMachine> result = builder.addShape(PIECE, shape)
            .build();
        return definition = result;
    }

    public static boolean validate(MTECriticalMachine host) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        if (tile == null) return false;
        World world = tile.getWorld();
        for (CriticalGeometry.Voxel voxel : voxels()) if (!matchesVoxel(
            world,
            tile.getXCoord() - 40,
            tile.getYCoord(),
            tile.getZCoord() - CriticalStructures.CONTROLLER_Z,
            voxel)) return false;
        if (!definition().check(
            host,
            PIECE,
            world,
            ExtendedFacing.DEFAULT,
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            7,
            35,
            1,
            false)) return false;
        boolean items = false, fluids = false, energy = false;
        for (CriticalGeometry.Voxel port : hatchPorts()) {
            if (!(world.getTileEntity(
                tile.getXCoord() - 40 + port.x,
                tile.getYCoord() + port.y,
                tile.getZCoord() - CriticalStructures.CONTROLLER_Z + port.z) instanceof IGregTechTileEntity hatch))
                continue;
            Object mte = hatch.getMetaTileEntity();
            items |= mte instanceof MTEHatchInputBus;
            fluids |= mte instanceof MTEHatchInput;
            energy |= CriticalMachineState.isEnergyInputHatchMeta(hatch.getMetaTileEntity());
        }
        CriticalMachineState state = host.getCriticalState();
        boolean construction = state.getState() == 0 || state.getState() == 1 || state.getState() == 4;
        int requirements = construction ? state.getConstructionDebugRequirements() : 0;
        boolean ignoreItems = requirements == 1 || requirements == 3;
        boolean ignoreFluids = requirements == 2 || requirements == 3;
        boolean ignoreEnergy = construction && state.getConstructionDebugIgnoreEnergy();
        // Only resource interfaces may be waived. Every loom voxel above must still match the real structure.
        return (items || ignoreItems) && (fluids || ignoreFluids) && (energy || ignoreEnergy);
    }

    public static void construct(MTECriticalMachine host, ItemStack trigger, boolean hintsOnly) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        if (tile == null) return;
        definition().buildOrHints(
            host,
            trigger,
            PIECE,
            tile.getWorld(),
            ExtendedFacing.DEFAULT,
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            7,
            35,
            1,
            hintsOnly);
    }

    public static int survivalConstruct(MTECriticalMachine host, ItemStack trigger, int budget,
        ISurvivalBuildEnvironment env) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        if (tile == null) return -1;
        return definition().survivalBuild(
            host,
            trigger,
            PIECE,
            tile.getWorld(),
            ExtendedFacing.DEFAULT,
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            7,
            35,
            1,
            budget,
            env,
            false);
    }

    /** Native elements remain visible to StructureLib-aware construction tools. */
    static IStructureElement<MTECriticalMachine> materialElement(CriticalGeometry.Voxel voxel) {
        return ofBlock(shell(voxel), 0);
    }
}
