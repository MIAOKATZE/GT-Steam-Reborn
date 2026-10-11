package com.miaokatze.gtsr.common.critical;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/** The permanent, player-built fabrication multiblock; never owned by a target construction journal. */
public final class CriticalLoomStructure {

    public static final String PIECE = "critical_loom";
    private static IStructureDefinition<MTECriticalMachine> definition;
    private static final int[][] PORTS = { { 40, 33 }, { 39, 33 }, { 41, 33 }, { 40, 34 }, { 39, 34 }, { 41, 34 },
        { 38, 34 }, { 42, 34 } };

    private CriticalLoomStructure() {}

    public static List<CriticalGeometry.Voxel> voxels() {
        return CriticalGeometry.getLoom();
    }

    public static boolean isLoomVoxel(CriticalGeometry.Voxel voxel) {
        return "loom".equals(voxel.model);
    }

    public static List<CriticalGeometry.Voxel> hatchPorts() {
        List<CriticalGeometry.Voxel> result = new ArrayList<>();
        for (int[] port : PORTS) for (CriticalGeometry.Voxel voxel : voxels())
            if (voxel.x == port[0] && voxel.y == 0 && voxel.z == port[1]) {
                result.add(voxel);
                break;
            }
        if (result.size() != PORTS.length) throw new IllegalStateException("Missing canonical loom hatch port");
        return Collections.unmodifiableList(result);
    }

    private static boolean isPort(CriticalGeometry.Voxel voxel) {
        for (int[] port : PORTS) if (voxel.x == port[0] && voxel.y == 0 && voxel.z == port[1]) return true;
        return false;
    }

    public static boolean matchesVoxel(World world, int originX, int originY, int originZ,
        CriticalGeometry.Voxel voxel) {
        int x = originX + voxel.x, y = originY + voxel.y, z = originZ + voxel.z;
        if (!world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return false;
        if (isPort(voxel) && CriticalMachineState.isStandardHatchHost(world.getTileEntity(x, y, z))) return true;
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
            boolean port = isPort(voxel);
            String key = voxel.material + ':' + ((CriticalMaterials.MaterialBlock) voxel.block).proxy + ':' + port;
            Character symbol = characters.get(key);
            if (symbol == null) {
                symbol = (char) (0x100 + characters.size());
                characters.put(key, symbol);
                IStructureElement<MTECriticalMachine> material = materialElement(voxel);
                builder.addElement(
                    symbol,
                    port ? ofChain(new CriticalStructures.PaidHatchElement(true), material) : material);
            }
            grid[voxel.z - 32][35 - voxel.y][47 - voxel.x] = symbol;
        }
        grid[0][35][7] = '~';
        String[][] shape = new String[16][36];
        for (int z = 0; z < 16; z++) for (int y = 0; y < 36; y++) shape[z][y] = new String(grid[z][y]);
        return definition = builder.addShape(PIECE, shape)
            .build();
    }

    public static boolean validate(MTECriticalMachine host) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        if (tile == null) return false;
        World world = tile.getWorld();
        for (CriticalGeometry.Voxel voxel : voxels())
            if (!matchesVoxel(world, tile.getXCoord() - 40, tile.getYCoord(), tile.getZCoord() - 32, voxel))
                return false;
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
            0,
            false)) return false;
        boolean items = false, fluids = false, energy = false;
        for (CriticalGeometry.Voxel port : hatchPorts()) {
            if (!(world.getTileEntity(
                tile.getXCoord() - 40 + port.x,
                tile.getYCoord() + port.y,
                tile.getZCoord() - 32 + port.z) instanceof IGregTechTileEntity hatch)) continue;
            Object mte = hatch.getMetaTileEntity();
            items |= mte instanceof MTEHatchInputBus;
            fluids |= mte instanceof MTEHatchInput;
            energy |= mte instanceof MTEHatchEnergy;
        }
        return items && fluids && energy;
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
            0,
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
            0,
            budget,
            env,
            false);
    }

    /** Standard StructureLib placement and inventory consumption, accepting v1.21.16 identities on checks. */
    static IStructureElement<MTECriticalMachine> materialElement(CriticalGeometry.Voxel voxel) {
        Block shell = shell(voxel);
        IStructureElement<MTECriticalMachine> normal = ofBlock(shell, 0);
        return new IStructureElement<MTECriticalMachine>() {

            @Override
            public boolean check(MTECriticalMachine host, World world, int x, int y, int z) {
                return CriticalMaterials
                    .matchesLoomVoxel(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z), voxel);
            }

            @Override
            public boolean spawnHint(MTECriticalMachine host, World world, int x, int y, int z, ItemStack trigger) {
                return normal.spawnHint(host, world, x, y, z, trigger);
            }

            @Override
            public boolean placeBlock(MTECriticalMachine host, World world, int x, int y, int z, ItemStack trigger) {
                if (CriticalStructures.isPreviewWorld(world))
                    return world.setBlock(x, y, z, CriticalMaterials.block(voxel.material, false), 0, 2);
                return normal.placeBlock(host, world, x, y, z, trigger);
            }

            @Override
            public BlocksToPlace getBlocksToPlace(MTECriticalMachine host, World world, int x, int y, int z,
                ItemStack trigger, AutoPlaceEnvironment env) {
                return normal.getBlocksToPlace(host, world, x, y, z, trigger, env);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTECriticalMachine host, World world, int x, int y, int z,
                ItemStack trigger, AutoPlaceEnvironment env) {
                return check(host, world, x, y, z) ? PlaceResult.SKIP
                    : normal.survivalPlaceBlock(host, world, x, y, z, trigger, env);
            }
        };
    }
}
