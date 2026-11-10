package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.cluster.ClusterStructureDef;
import com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;

/** Resolve native mounting points and legal orientations without keeping a second shape or pad-coordinate table. */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class TestLabClusterLayout {

    private TestLabClusterLayout() {}

    /** Pure P elements require an actual hatch; a casing-only candidate search cannot fill their empty cells. */
    static IGregTechTileEntity energyPort(WorldServer world, MTEClusterUnitBase unit, int metaId) throws Exception {
        IStructureDefinition definition = unit.getStructureDefinition();
        if (!(definition instanceof StructureDefinition))
            throw new IllegalStateException("Unknown unit definition implementation");
        IStructureElement port = (IStructureElement) ((StructureDefinition) definition).getElements()
            .get('P');
        if (port == null) throw new IllegalStateException("Powered cluster unit has no native P element");
        IGregTechTileEntity base = unit.getBaseMetaTileEntity();
        List<int[]> positions = new ArrayList<>();
        definition.iterate(
            "main",
            world,
            unit.getExtendedFacing(),
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            TestLabStructure.invokeInt(unit, "getStructureOffsetA"),
            TestLabStructure.invokeInt(unit, "getStructureOffsetB"),
            TestLabStructure.invokeInt(unit, "getStructureOffsetC"),
            (element, ignoredWorld, x, y, z, a, b, c) -> {
                if (element == port) positions.add(new int[] { x, y, z });
                return true;
            });
        if (positions.size() != 1)
            throw new IllegalStateException("Expected one native P energy port, found " + positions.size());
        int[] position = positions.get(0);
        if (!world.isAirBlock(position[0], position[1], position[2])) throw new IllegalStateException(
            "Native P energy port is occupied at " + position[0] + ',' + position[1] + ',' + position[2]);
        IGregTechTileEntity hatch = TestLabFixtures.machine(world, position[0], position[1], position[2], metaId);
        unit.clearHatches();
        if (!(hatch.getMetaTileEntity() instanceof MTEHatchEnergy)
            || !port.check(unit, world, position[0], position[1], position[2]))
            throw new IllegalStateException("Native P element rejected the actual energy hatch");
        hatch.enableWorking();
        return hatch;
    }

    static IGregTechTileEntity mount(WorldServer world, MTESteamMineralLogisticsCluster cluster, MetaTileEntityID id,
        char pad, int segment) throws Exception {
        IStructureDefinition definition = cluster.getStructureDefinition();
        if (!(definition instanceof StructureDefinition))
            throw new IllegalStateException("Unknown cluster definition implementation");
        IStructureElement slot = (IStructureElement) ((StructureDefinition) definition).getElements()
            .get(pad);
        if (slot == null) throw new IllegalStateException("Missing native cluster pad " + pad);
        IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
        List<int[]> positions = new ArrayList<>();
        definition.iterate(
            segment == 0 ? ClusterStructureDef.PIECE_MAIN : ClusterStructureDef.PIECE_EXT,
            world,
            cluster.getExtendedFacing(),
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            segment == 0 ? ClusterStructureDef.mainOffsetA() : ClusterStructureDef.extOffsetA(segment - 1),
            segment == 0 ? ClusterStructureDef.mainOffsetB() : ClusterStructureDef.extOffsetB(),
            segment == 0 ? ClusterStructureDef.mainOffsetC() : ClusterStructureDef.extOffsetC(segment - 1),
            (element, ignoredWorld, x, y, z, a, b, c) -> {
                if (element == slot) positions.add(new int[] { x, y, z });
                return true;
            });
        for (int[] position : positions) {
            if (!world.isAirBlock(position[0], position[1], position[2])) continue;
            IGregTechTileEntity tile = TestLabFixtures.machine(world, position[0], position[1], position[2], id.ID);
            MTEClusterUnitBase unit = (MTEClusterUnitBase) tile.getMetaTileEntity();
            for (ForgeDirection facing : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
                ForgeDirection.SOUTH, ForgeDirection.WEST }) {
                tile.setFrontFacing(facing);
                if (fits(world, unit)) return tile;
            }
            // The candidate is still empty inventory and disabled. Do not invoke ordinary breaking/drop paths.
            world.removeTileEntity(position[0], position[1], position[2]);
            world.setBlockToAir(position[0], position[1], position[2]);
        }
        throw new IllegalStateException(
            "No collision-free native " + pad + " pad/orientation for " + id + " segment=" + segment);
    }

    private static boolean fits(WorldServer world, MTEClusterUnitBase unit) throws Exception {
        IGregTechTileEntity base = unit.getBaseMetaTileEntity();
        boolean[] fits = { true };
        unit.getStructureDefinition()
            .iterate(
                "main",
                world,
                unit.getExtendedFacing(),
                base.getXCoord(),
                base.getYCoord(),
                base.getZCoord(),
                TestLabStructure.invokeInt(unit, "getStructureOffsetA"),
                TestLabStructure.invokeInt(unit, "getStructureOffsetB"),
                TestLabStructure.invokeInt(unit, "getStructureOffsetC"),
                (element, ignoredWorld, x, y, z, a, b, c) -> {
                    if (x == base.getXCoord() && y == base.getYCoord() && z == base.getZCoord()) return true;
                    if (!world.isAirBlock(x, y, z) && !element.check(unit, world, x, y, z)) fits[0] = false;
                    return true;
                });
        return fits[0];
    }
}
