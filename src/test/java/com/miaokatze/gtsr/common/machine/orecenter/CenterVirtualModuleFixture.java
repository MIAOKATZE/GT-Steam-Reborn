// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.common.machine.orecenter;

import static com.miaokatze.gtsr.common.machine.orecenter.CenterRecoveryContracts.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Frame;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Shape;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.structure.error.StructureError;

/** Production install/remove and legacy migration against a world that counts every attempted block write. */
final class CenterVirtualModuleFixture {

    static void run() throws Exception {
        physicalStructureBoundary();
        virtualPackages();
        for (ForgeDirection direction : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
            ForgeDirection.SOUTH, ForgeDirection.WEST }) legacy(direction);
        damagedAndBlocked();
        unloadedLegacyAndRemovedOwnership();
        result.put(
            "virtualModules",
            "all package types/grades, all slots/facings, overlap and unloaded visual chunks: zero writes; durable exact legacy migration preserves foreign blocks and tile entities");
    }

    /** No ready() shortcut: real construction and the production monitors must establish formation. */
    private static void physicalStructureBoundary() throws Exception {
        SparseWorld world = world();
        MTEMineralIntegrationCenter machine = center(world);
        IGregTechTileEntity tile = machine.getBaseMetaTileEntity();
        CenterStructure structure = new CenterStructure();
        set(machine, "structure", structure);
        Frame frame = new Frame(tile);
        world.cells.put(key(frame.x, frame.y, frame.z), new Cell(GregTechAPI.sBlockMachines, 0));
        world.tiles.put(key(frame.x, frame.y, frame.z), (BaseMetaTileEntity) tile);
        check(
            !structure.install(tile, 3, CenterModule.LOGISTICS, 1),
            "module ownership cannot substitute for an absent real center");
        check(structure.construct(tile, 3, 1, false), "native construction builds the real tower and first outer wing");
        check(world.writes > 0, "real construction remains a physical block-writing path");
        Shape tower = CenterStructure.optionalShape("tower-service-3");
        Shape outer = CenterStructure.optionalShape("outer-0");
        Shape sector = CenterStructure.optionalShape("sector-0");
        assertRealPiece(world, frame, tower, "tower");
        assertRealPiece(world, frame, outer, "outer");
        sector.prepareLookup();
        for (int slot = 0; slot < 6; slot++) {
            int[] at = CenterStructure.SLOTS[slot];
            int index = sector.lookup(at[0], at[1] - 1, at[2]);
            check(index >= 0, "every slot seat is part of the real sector template");
            assertRealCell(world, frame, sector, index, "physical slot floor/base " + slot);
        }
        int initialBuildWrites = world.writes;
        placeNativePort(world, frame, 5, new MTEHatchInputBus("boundary_input_bus", 1, new String[0], null));
        placeNativePort(world, frame, -5, new MTEHatchOutputBus("boundary_output_bus", 1, new String[0], null));
        placeNativePort(world, frame, 3, new MTEHatchInput("boundary_input", 1, new String[0], null));
        placeNativePort(world, frame, -3, new MTEHatchOutput("boundary_output", 1, new String[0], null));
        for (int tick = 0; tick < 8000 && !structure.isNewBatchReady(tile); tick++) {
            world.now++;
            structure.tick(tile);
        }
        check(
            structure.isNewBatchReady(tile) && structure.sectorMask(tile) == 1,
            "real tower, outer/sector floor and interfaces pass the original monitor gates");
        check(
            world.writes == initialBuildWrites,
            "formation checking never replaces real construction with virtual writes");
        List<StructureError> errors = new ArrayList<>();
        machine.checkMachine(tile, null, errors);
        check(errors.isEmpty(), "formal hatch collection discovers the four real required ports");
        Map<String, Cell> physical = new HashMap<>(world.cells);
        Map<String, net.minecraft.tileentity.TileEntity> interfaces = new HashMap<>(world.tiles);
        int[] types = (int[]) get(machine, "modules"), grades = (int[]) get(machine, "grades");
        for (int slot : new int[] { 0, 1, 3 }) {
            CenterModule module = slot == 0 ? CenterModule.SPEED
                : slot == 1 ? CenterModule.CRUSHER : CenterModule.LOGISTICS;
            check(structure.install(tile, slot, module, 1), "formed real structure accepts virtual module ownership");
            types[slot] = module.ordinal();
            grades[slot] = 1;
        }
        check(
            machine.graphCapacity() == 1,
            "logistics capability requires both the real formed sector and virtual package");
        check(
            world.writes == initialBuildWrites && sameNonAirCells(world, physical) && world.tiles.equals(interfaces),
            "virtual installation preserves every real tower, outer, seat and interface block");
        NBTTagCompound formed = new NBTTagCompound();
        structure.save(formed, tile);
        structure.detach(tile);
        int towerWall = findCell(tower, true), outerWall = findCell(sector, false), towerFloor = -1;
        for (int i = 0; i < tower.count(); i++)
            if (tower.y[i] == 0 && Math.abs(tower.x[i]) <= 22 && Math.abs(tower.z[i]) <= 22) {
                towerFloor = i;
                break;
            }
        check(towerFloor >= 0, "the real tower includes a ground/foundation layer");
        int[][] damage = { { tower.x[towerWall], tower.y[towerWall], tower.z[towerWall] },
            { sector.x[outerWall], sector.y[outerWall], sector.z[outerWall] },
            { tower.x[towerFloor], tower.y[towerFloor], tower.z[towerFloor] },
            { CenterStructure.SLOTS[0][0], CenterStructure.SLOTS[0][1] - 1, CenterStructure.SLOTS[0][2] },
            { CenterStructure.SLOTS[3][0], CenterStructure.SLOTS[3][1] - 1, CenterStructure.SLOTS[3][2] },
            { 5, 2, -19 } };
        String[] names = { "tower wall", "outer wing", "tower foundation", "raised module seat", "ground module seat",
            "native input bus" };
        for (int i = 0; i < damage.length; i++) {
            CenterStructure restored = new CenterStructure();
            restored.load(formed);
            set(machine, "structure", restored);
            world.now++;
            restored.tick(tile);
            check(
                restored.isExecutionReady(tile),
                "saved ownership still starts from real valid structure: " + names[i]);
            int[] local = damage[i];
            String position = key(
                frame.x(local[0], local[2] + 19),
                frame.y + local[1] - 2,
                frame.z(local[0], local[2] + 19));
            Cell original = world.cells.get(position);
            net.minecraft.tileentity.TileEntity originalTile = world.tiles.remove(position);
            world.cells.put(position, new Cell(Blocks.air, 0));
            restored.changed(frame, local[0], local[1], local[2]);
            check(
                !restored.isExecutionReady(tile) && !restored.isNewBatchReady(tile) && machine.graphCapacity() == 0,
                "missing real structure revokes execution and module ability: " + names[i]);
            check(
                restored.moduleIntact(tile, 3, CenterModule.LOGISTICS, 1) && grades[3] == 1,
                "structural damage suspends ability without destroying the paid package: " + names[i]);
            check(
                !restored.install(tile, 4, CenterModule.LOGISTICS, 1),
                "a package cannot form or repair missing real geometry: " + names[i]);
            errors.clear();
            machine.checkMachine(tile, null, errors);
            check(!errors.isEmpty(), "formal machine checking rejects the damaged physical structure: " + names[i]);
            check(
                world.writes == initialBuildWrites,
                "damage detection and virtual records never regenerate removed real blocks");
            world.cells.put(position, original);
            if (originalTile != null) world.tiles.put(position, originalTile);
            restored.detach(tile);
        }
        CenterStructure missingInterface = new CenterStructure();
        missingInterface.load(formed);
        set(machine, "structure", missingInterface);
        world.now++;
        missingInterface.tick(tile);
        String inputPosition = key(frame.x(5, 0), frame.y, frame.z(5, 0));
        net.minecraft.tileentity.TileEntity inputTile = world.tiles.remove(inputPosition);
        check(
            world.cells.get(inputPosition)
                .block() == GregTechAPI.sBlockMachines,
            "interface test keeps the real machine block while removing its functional tile");
        check(
            !missingInterface.isExecutionReady(tile) && !missingInterface.isNewBatchReady(tile),
            "a matching machine block and virtual records cannot replace a missing native interface tile");
        errors.clear();
        machine.checkMachine(tile, null, errors);
        check(!errors.isEmpty(), "formal required-port checking rejects a missing real input interface");
        check(
            missingInterface.moduleIntact(tile, 3, CenterModule.LOGISTICS, 1) && grades[3] == 1,
            "interface loss pauses operation without destroying paid virtual ownership");
        world.tiles.put(inputPosition, inputTile);
        missingInterface.detach(tile);
        set(machine, "structure", structure);
        for (int slot : new int[] { 0, 1, 3 }) {
            CenterModule module = CenterModule.values()[types[slot]];
            check(structure.remove(tile, slot, module, 1), "normal package removal removes only virtual ownership");
            grades[slot] = 0;
        }
        check(
            world.writes == initialBuildWrites && sameNonAirCells(world, physical) && world.tiles.equals(interfaces),
            "package removal leaves the complete physical center and native interfaces intact");
        result.put(
            "physicalCenterBoundary",
            "native tower/outer construction and full monitor formation; real floor, raised/ground slot bases, wall and bus removal revoke abilities while package ownership survives; package install/remove writes zero blocks");
        CenterClaims.unload(world);
    }

    private static int findCell(Shape shape, boolean tower) {
        for (int i = 0; i < shape.count(); i++) if (shape.y[i] > 20 && (!tower
            || Math.abs(shape.x[i]) <= 22 && Math.abs(shape.z[i]) <= 22 && !CenterStructure.portCasing(shape, i)))
            return i;
        throw new AssertionError("No physical wall fixture cell");
    }

    private static void assertRealPiece(SparseWorld world, Frame frame, Shape shape, String name) {
        for (int i = 0; i < shape.count(); i++) assertRealCell(world, frame, shape, i, name);
    }

    private static void assertRealCell(SparseWorld world, Frame frame, Shape shape, int index, String name) {
        int x = frame.x(shape.x[index], shape.z[index] + 19), y = frame.y + shape.y[index] - 2,
            z = frame.z(shape.x[index], shape.z[index] + 19);
        check(
            world.getBlock(x, y, z) == shape.block(index) && world.getBlockMetadata(x, y, z) == shape.metadata(index),
            "real template cell remains physically present: " + name);
    }

    private static void placeNativePort(SparseWorld world, Frame frame, int localX, MetaTileEntity hatch) {
        BaseMetaTileEntity port = new BaseMetaTileEntity();
        port.setWorldObj(world);
        port.xCoord = frame.x(localX, 0);
        port.yCoord = frame.y;
        port.zCoord = frame.z(localX, 0);
        hatch.setBaseMetaTileEntity(port);
        world.tiles.put(key(port.xCoord, port.yCoord, port.zCoord), port);
        world.cells.put(key(port.xCoord, port.yCoord, port.zCoord), new Cell(GregTechAPI.sBlockMachines, 0));
    }

    private static void virtualPackages() throws Exception {
        for (ForgeDirection direction : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
            ForgeDirection.SOUTH, ForgeDirection.WEST }) {
            for (CenterModule module : CenterModule.values()) for (int grade = 1; grade <= 3; grade++) {
                SparseWorld world = world();
                IGregTechTileEntity tile = tile(world, direction);
                CenterStructure structure = ready(tile);
                int slot = module.kind == 0 ? 0 : module.kind == 1 ? 1 : 3;
                Shape shape = shape(slot, module, grade);
                int[] at = pos(new Frame(tile), shape, 0, slot);
                world.cells.put(key(at), new Cell(Blocks.diamond_block, 0));
                world.tiles.put(key(at), new BaseMetaTileEntity());
                world.missing.add(chunk(at[0] >> 4, at[2] >> 4));
                check(
                    structure.install(tile, slot, module, grade),
                    "virtual package ignores visual-region occupancy and loading");
                check(!structure.install(tile, slot, module, grade), "installed virtual slot cannot be charged twice");
                check(
                    structure.moduleIntact(tile, slot, module, grade),
                    "virtual package ownership is immediately registered");
                NBTTagCompound saved = new NBTTagCompound();
                structure.save(saved, tile);
                CenterStructure restored = new CenterStructure();
                restored.load(saved);
                check(
                    restored.moduleIntact(tile, slot, module, grade),
                    "virtual ownership reloads before a real formation recheck");
                set(restored, "formedSectors", 0);
                restored.invalidateModule(slot);
                check(
                    !restored.moduleBroken(slot, module, grade),
                    "real-sector or obsolete decorative invalidation does not destroy paid ownership");
                check(restored.remove(tile, slot, module, grade), "normal removal uses the saved virtual record");
                check(!restored.remove(tile, slot, module, grade), "normal removal happens once");
                check(
                    world.writes == 0 && world.unloadedReads == 0,
                    "virtual install/save/reload/remove neither reads visual blocks nor writes them");
                check(
                    world.cells.get(key(at))
                        .block() == Blocks.diamond_block && world.tiles.containsKey(key(at)),
                    "overlapping player block and TE survive");
                check(
                    ((Map<?, ?>) get(CenterClaims.get(world), "owners")).isEmpty(),
                    "new module creates no per-voxel ownership");
                cycles++;
                CenterClaims.unload(world);
            }
            for (int slot = 0; slot < 36; slot++) {
                SparseWorld world = world();
                IGregTechTileEntity tile = tile(world, direction);
                CenterStructure structure = ready(tile);
                CenterModule module = slot % 6 == 0 ? CenterModule.SPEED
                    : slot % 6 <= 2 ? CenterModule.CRUSHER : CenterModule.LOGISTICS;
                check(
                    structure.install(tile, slot, module, 3) && structure.remove(tile, slot, module, 3)
                        && world.writes == 0,
                    "all 36 slots and four facings use zero-write lifecycle");
                CenterClaims.unload(world);
            }
        }
    }

    private static CenterStructure oldStructure(IGregTechTileEntity tile, int slot, CenterModule module)
        throws Exception {
        CenterStructure fresh = ready(tile);
        check(fresh.install(tile, slot, module, 1), "prepare old-format fixture");
        NBTTagCompound saved = new NBTTagCompound();
        fresh.save(saved, tile);
        saved.getCompoundTag("oreCenterStructure")
            .removeTag("ModuleFormat");
        CenterStructure old = new CenterStructure();
        old.load(saved);
        return old;
    }

    private static void migrate(CenterStructure structure, IGregTechTileEntity tile) throws Exception {
        Method method = CenterStructure.class
            .getDeclaredMethod("migrateLegacy", IGregTechTileEntity.class, CenterClaims.class);
        method.setAccessible(true);
        method.invoke(structure, tile, CenterClaims.get(tile.getWorld()));
        ((SparseWorld) tile.getWorld()).now++;
    }

    private static void fillModule(SparseWorld world, Frame frame, Shape shape, int slot) {
        for (int i = 0; i < shape.count(); i++)
            world.cells.put(key(pos(frame, shape, i, slot)), new Cell(shape.block(i), shape.metadata(i)));
    }

    private static void legacy(ForgeDirection direction) throws Exception {
        SparseWorld world = world();
        IGregTechTileEntity tile = tile(world, direction);
        int slot = 11;
        CenterModule module = CenterModule.LOGISTICS;
        CenterStructure old = oldStructure(tile, slot, module);
        Frame original = new Frame(tile);
        Shape shape = shape(slot, module, 1);
        fillModule(world, original, shape, slot);
        CenterClaims claims = CenterClaims.get(world);
        claims.claim(original, slot, "module-5-logistics-1", shape);
        // Rotate the controller: migration must continue to use the persisted original facing.
        IGregTechTileEntity turned = tile(
            world,
            direction == ForgeDirection.NORTH ? ForgeDirection.EAST : ForgeDirection.NORTH);
        claims.migrateLegacy(new Frame(turned), old, 1);
        check(world.writes == 1, "a migration slice respects its exact write/sample budget");
        NBTTagCompound claimSave = new NBTTagCompound(), controllerSave = new NBTTagCompound();
        claims.writeToNBT(claimSave);
        old.save(controllerSave, tile);
        CenterClaims.unload(world);
        claims = CenterClaims.get(world);
        claims.readFromNBT(claimSave);
        CenterStructure restored = new CenterStructure();
        restored.load(controllerSave);
        for (int tick = 0; tick < shape.count() + 10 && !restored.moduleIntact(turned, slot, module, 1); tick++)
            migrate(restored, turned);
        check(
            restored.moduleIntact(turned, slot, module, 1),
            "partially completed migration resumes after reload and controller rotation");
        check(world.writes == shape.count() && world.nonAir() == 0, "only saved exact old positions are cleared once");
        int writes = world.writes;
        for (int i = 0; i < 3; i++) migrate(restored, turned);
        check(
            world.writes == writes && !restored.moduleBroken(slot, module, 1),
            "completed migration is idempotent and does not invent damage");
        CenterClaims.unload(world);
    }

    private static void unloadedLegacyAndRemovedOwnership() throws Exception {
        SparseWorld world = world();
        IGregTechTileEntity tile = tile(world, ForgeDirection.WEST);
        CenterStructure old = oldStructure(tile, 0, CenterModule.SPEED);
        Frame frame = new Frame(tile);
        Shape shape = shape(0, CenterModule.SPEED, 1);
        fillModule(world, frame, shape, 0);
        CenterClaims claims = CenterClaims.get(world);
        claims.claim(frame, 0, "module-0-speed-1", shape);
        int[] at = pos(frame, shape, 0, 0);
        world.missing.add(chunk(at[0] >> 4, at[2] >> 4));
        for (int i = 0; i < shape.count() / 16 + 20; i++) migrate(old, tile);
        check(
            world.unloadedReads == 0 && claims.migrationPending(frame),
            "unloaded legacy chunks remain pending without any chunk read");
        NBTTagCompound saved = new NBTTagCompound();
        claims.writeToNBT(saved);
        var entry = saved.getTagList("modules", 10)
            .getCompoundTagAt(0);
        java.util.BitSet removed = java.util.BitSet.valueOf(entry.getByteArray("removed"));
        removed.set(0);
        entry.setByteArray("removed", removed.toByteArray());
        claims.readFromNBT(saved);
        world.missing.clear();
        // The position is now a matching player replacement, but the persisted removed bit revoked ownership.
        for (int i = 0; i < shape.count() / 16 + 20 && claims.migrationPending(frame); i++) migrate(old, tile);
        check(
            world.getBlock(at[0], at[1], at[2]) == shape.block(0)
                && world.getBlockMetadata(at[0], at[1], at[2]) == shape.metadata(0),
            "persistently removed old claim never deletes a later same-encoding player block");
        check(old.moduleBroken(0, CenterModule.SPEED, 1), "old loss bitmap remains loss after virtual migration");
        CenterClaims.unload(world);
    }

    private static void damagedAndBlocked() throws Exception {
        SparseWorld world = world();
        IGregTechTileEntity tile = tile(world, ForgeDirection.NORTH);
        CenterStructure old = oldStructure(tile, 0, CenterModule.SPEED);
        Frame frame = new Frame(tile);
        Shape shape = shape(0, CenterModule.SPEED, 1);
        fillModule(world, frame, shape, 0);
        CenterClaims claims = CenterClaims.get(world);
        claims.claim(frame, 0, "module-0-speed-1", shape);
        int[] foreign = pos(frame, shape, 0, 0), missing = pos(frame, shape, 1, 0), container = pos(frame, shape, 2, 0);
        world.cells.put(key(foreign), new Cell(Blocks.diamond_block, 0));
        world.cells.remove(key(missing));
        world.tiles.put(key(container), new BaseMetaTileEntity());
        for (int tick = 0; tick < shape.count() / 8 + 20; tick++) migrate(old, tile);
        check(
            world.cells.get(key(foreign))
                .block() == Blocks.diamond_block && world.tiles.containsKey(key(container)),
            "foreign block and TE are never deleted");
        check(
            old.moduleBroken(0, CenterModule.SPEED, 1) && !old.moduleIntact(tile, 0, CenterModule.SPEED, 1),
            "missing/foreign legacy voxels cannot become a refundable intact package");
        check(claims.migrationPending(frame), "foreign obstacles preserve an explicit migration obligation");
        check(
            CenterClaims.migrationProblem(world, frame.x, frame.y, frame.z)
                .contains("槽位 1"),
            "interaction feedback exposes the persisted blocked slot and exact world coordinate");
        NBTTagCompound state = new NBTTagCompound();
        claims.writeToNBT(state);
        check(
            state.getTagList("modules", 10)
                .getCompoundTagAt(0)
                .getByteArray("blocked").length > 0,
            "blocked exact positions survive saves");
        // Replacing a rejected player block with a matching material never grants ownership to the cleaner.
        world.cells.put(key(foreign), new Cell(shape.block(0), shape.metadata(0)));
        for (int tick = 0; tick < shape.count() / 8 + 20; tick++) migrate(old, tile);
        check(
            world.cells.get(key(foreign))
                .block() == shape.block(0),
            "a later matching player replacement remains untouched");
        world.cells.remove(key(foreign));
        world.cells.remove(key(container));
        world.tiles.remove(key(container));
        for (int tick = 0; tick < shape.count() / 8 + 20 && claims.migrationPending(frame); tick++) migrate(old, tile);
        check(
            !claims.migrationPending(frame) && old.moduleBroken(0, CenterModule.SPEED, 1),
            "manual obstacle removal completes cleanup without washing away losses");
        check(!old.remove(tile, 0, CenterModule.SPEED, 1), "damaged migrated module cannot refund a fresh package");
        CenterClaims.unload(world);
    }
}
