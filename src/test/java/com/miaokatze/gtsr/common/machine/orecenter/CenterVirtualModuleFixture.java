// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.common.machine.orecenter;

import static com.miaokatze.gtsr.common.machine.orecenter.CenterRecoveryContracts.*;

import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Frame;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Shape;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Production install/remove and legacy migration against a world that counts every attempted block write. */
final class CenterVirtualModuleFixture {

    static void run() throws Exception {
        virtualPackages();
        for (ForgeDirection direction : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
            ForgeDirection.SOUTH, ForgeDirection.WEST }) legacy(direction);
        damagedAndBlocked();
        unloadedLegacyAndRemovedOwnership();
        result.put(
            "virtualModules",
            "all package types/grades, all slots/facings, overlap and unloaded visual chunks: zero writes; durable exact legacy migration preserves foreign blocks and tile entities");
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
                check(structure.moduleIntact(tile, slot, module, grade), "virtual package is immediately usable");
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
