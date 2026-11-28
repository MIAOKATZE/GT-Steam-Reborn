// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.storage.RegionFile;

/** Synthetic native-NBT wire-layout contracts; no game, registry, live instance or user-world access. */
final class MaterialIdFluidCellCopyContracts {

    private static int checks;
    private static final String KEY = "planned:native_acid_cut";

    private MaterialIdFluidCellCopyContracts() {}

    static int run(Path scratch) throws Exception {
        NBTTagCompound player = new NBTTagCompound();
        NBTTagList inventory = new NBTTagList();
        NBTTagCompound acid = stack(1000, 30705, 37), water = stack(1000, 30705, 9);
        acid.getCompoundTag("tag")
            .setString("custom", "preserved");
        inventory.appendTag(acid);
        inventory.appendTag(water);
        inventory.appendTag(stack(1001, 30705, 8));
        player.setTag("Inventory", inventory);
        NBTTagList ender = new NBTTagList();
        ender.appendTag(stack(1000, 30705, 3));
        player.setTag("EnderItems", ender);
        MaterialIdOfflineCopy.Context c = context(scratch);
        meaning(c, "player.dat.Inventory[0]", KEY);
        meaning(c, "player.dat.Inventory[1]", "native:ic2distilledwater");
        meaning(c, "player.dat.EnderItems[0]", KEY);
        NBTTagCompound originalWater = (NBTTagCompound) water.copy();
        NBTTagCompound unrelated = (NBTTagCompound) inventory.getCompoundTagAt(2)
            .copy();
        MaterialIdOfflineCopy.rewrite(player, "player.dat", c, 0);
        filled(acid, 37);
        check(originalWater.equals(water), ".277 native water stays byte-semantically unchanged");
        check(unrelated.equals(inventory.getCompoundTagAt(2)), "other numeric Item with same Damage is unchanged");
        check(
            acid.getCompoundTag("tag")
                .getString("custom")
                .equals("preserved"),
            "custom item NBT conserved");
        filled(ender.getCompoundTagAt(0), 3);
        NBTTagCompound once = (NBTTagCompound) player.copy();
        MaterialIdOfflineCopy.rewrite(player, "player.dat", c, 0);
        check(player.equals(once), "already converted universal cells are idempotent");
        MaterialIdFluidCellCopy.finish(c, new HashMap<String, Object>());
        check(c.quantities.get(KEY) == 40L, "only proven acid quantities reported");
        for (boolean gzip : Arrays.asList(false, true)) check(
            MaterialIdOfflineCopy.decode(MaterialIdOfflineCopy.encode(player, gzip), gzip)
                .equals(player),
            "v2 converted native ItemStack/Fluid NBT survives raw/gzip native codec round trip");
        for (String dimension : Arrays.asList("region/r.0.0.mca", "DIM-1/region/r.0.0.mca")) {
            Path sourceRegion = scratch.resolve("v2-source")
                .resolve(dimension);
            Path targetRegion = scratch.resolve("v2-target")
                .resolve(dimension);
            Files.createDirectories(sourceRegion.getParent());
            NBTTagCompound root = new NBTTagCompound(), levelData = new NBTTagCompound();
            NBTTagList entities = new NBTTagList();
            NBTTagCompound entity = new NBTTagCompound();
            entity.setString("id", "Item");
            entity.setTag("Item", stack(1000, 30705, 18));
            entity.setShort("Age", (short) 55);
            entities.appendTag(entity);
            levelData.setTag("Entities", entities);
            root.setTag("Level", levelData);
            RegionFile nativeInput = new RegionFile(sourceRegion.toFile());
            try (DataOutputStream out = nativeInput.getChunkDataOutputStream(2, 3)) {
                CompressedStreamTools.write(root, out);
            } finally {
                nativeInput.close();
            }
            String sourceHash = MaterialIdOfflineCopy.hash(sourceRegion);
            MaterialIdOfflineCopy.Context regionContext = context(scratch);
            meaning(regionContext, dimension + "#98.Level.Entities[0].Item", KEY);
            MaterialIdOfflineCopy.region(sourceRegion, targetRegion, dimension, regionContext, true);
            check(!Files.exists(targetRegion), "v2 region dry-run creates no target");
            regionContext = context(scratch);
            meaning(regionContext, dimension + "#98.Level.Entities[0].Item", KEY);
            MaterialIdOfflineCopy.region(sourceRegion, targetRegion, dimension, regionContext, false);
            RegionFile nativeOutput = new RegionFile(targetRegion.toFile());
            try (DataInputStream in = nativeOutput.getChunkDataInputStream(2, 3)) {
                NBTTagCompound converted = CompressedStreamTools
                    .func_152456_a(in, new NBTSizeTracker(MaterialIdOfflineCopy.MAX_NBT));
                NBTTagCompound convertedEntity = converted.getCompoundTag("Level")
                    .getTagList("Entities", 10)
                    .getCompoundTagAt(0);
                filled(convertedEntity.getCompoundTag("Item"), 18);
                check(
                    convertedEntity.getShort("Age") == 55,
                    "v2 native item-entity unrelated state preserved in unloaded region");
            } finally {
                nativeOutput.close();
            }
            check(
                sourceHash.equals(MaterialIdOfflineCopy.hash(sourceRegion)),
                "v2 cross-item unloaded region source remains byte-identical");
        }

        c = context(scratch);
        c.plan.sourceGt = "5.09.54.265";
        MaterialIdOfflineCopy.Context ambiguous = c;
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(stack(1000, 30705, 1), "mixed", ambiguous, 0),
            ".265 version alone cannot prove every 30705 in a mixed-history world");
        meaning(c, "wrong", "another:key");
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(stack(1000, 30705, 1), "wrong", ambiguous, 0),
            "contradictory semantic evidence fails");
        NBTTagCompound conflictingTag = stack(1000, 30705, 1);
        conflictingTag.getCompoundTag("tag")
            .setString("Fluid", "preexisting unknown");
        meaning(c, "conflict", KEY);
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(conflictingTag, "conflict", ambiguous, 0),
            "existing Fluid NBT is never overwritten");
        meaning(c, "unseen", KEY);
        rejected(
            () -> MaterialIdFluidCellCopy.finish(ambiguous, new HashMap<String, Object>()),
            "stale/unused source-location assertions fail completion");

        c = context(scratch);
        NBTTagCompound gt = new NBTTagCompound();
        gt.setString("id", "GT_MetaTileEntity");
        gt.setInteger("mID", 1000);
        gt.setInteger("mProgresstime", 199);
        gt.setInteger("mMaxProgresstime", 200);
        gt.setInteger("mEUt", 128);
        gt.setBoolean("mActive", true);
        NBTTagList gtItems = new NBTTagList();
        NBTTagCompound bulk = stack(1000, 30705, 1);
        bulk.setInteger("Count", 50000);
        bulk.setInteger("IntSlot", 7);
        gtItems.appendTag(bulk);
        gt.setTag("Inventory", gtItems);
        gt.setInteger("mOutputItemsLength", 2);
        NBTTagCompound paid = stack(1000, 30705, 1);
        paid.setInteger("Count", 2000000000);
        gt.setTag("mOutputItem1", paid);
        NBTTagCompound gtBefore = (NBTTagCompound) gt.copy();
        meaning(c, "machine.Inventory[0]", KEY);
        meaning(c, "machine.mOutputItem1", KEY);
        MaterialIdOfflineCopy.rewrite(gt, "machine", c, 0);
        filled(bulk, 50000);
        filled(paid, 2000000000);
        check(bulk.hasKey("Count", 3) && bulk.getInteger("IntSlot") == 7, "GT Count type and IntSlot conserved");
        gtBefore.setTag(
            "Inventory",
            gt.getTag("Inventory")
                .copy());
        gtBefore.setTag(
            "mOutputItem1",
            gt.getTag("mOutputItem1")
                .copy());
        check(gtBefore.equals(gt), "paid-output progress/energy/activity and unrelated state conserved");
        gt.setTag("UnknownCoverInventory", new NBTTagCompound());
        MaterialIdOfflineCopy.Context gtContext = c;
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(gt, "machine", gtContext, 0),
            "unknown GT cover/delegate refuses whole migration");

        c = context(scratch);
        NBTTagCompound disk = stack(1003, 0, 1), diskData = disk.getCompoundTag("tag");
        NBTTagCompound stored = ae(1000, 30705, 5_000_000_000L);
        stored.setLong("Req", 8_000_000_000L);
        stored.setBoolean("Craft", true);
        stored.setLong("ReqMade", 7000000000L);
        diskData.setShort("it", (short) 1);
        diskData.setLong("ic", 5_000_000_000L);
        diskData.setTag("#0", stored);
        diskData.setLong("@0", 5_000_000_000L);
        meaning(c, "disk.tag.#0", KEY);
        MaterialIdOfflineCopy.rewrite(disk, "disk", c, 0);
        filled(stored, 0);
        check(
            stored.getLong("Cnt") == 5_000_000_000L && stored.getLong("Req") == 8_000_000_000L
                && stored.getBoolean("Craft")
                && stored.getLong("ReqMade") == 7000000000L,
            "AE long item/request/craft counts never narrow");
        check(
            diskData.getLong("@0") == 5_000_000_000L && diskData.getLong("ic") == 5_000_000_000L
                && diskData.getShort("it") == 1,
            "AE slot/total/type counters conserved");
        check(c.quantities.get(KEY) == 5_000_000_000L, "AE quantity report uses true long count");
        NBTTagCompound brokenDisk = (NBTTagCompound) disk.copy();
        brokenDisk.getCompoundTag("tag")
            .setLong("@0", 4L);
        MaterialIdOfflineCopy.Context aeContext = c;
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(brokenDisk, "broken", aeContext, 0),
            "AE divergent @N/Cnt rejected");
        NBTTagCompound opaque = new NBTTagCompound();
        opaque.setByteArray("compressed", new byte[] { 1, 2, 3 });
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(opaque, "external", aeContext, 0),
            "unknown compressed storage blocked");

        for (boolean ultimate : Arrays.asList(false, true)) {
            c = context(scratch);
            NBTTagCompound pattern = stack(1004, 0, 1), data = pattern.getCompoundTag("tag");
            NBTTagList in = new NBTTagList(), out = new NBTTagList();
            NBTTagCompound input = ultimate ? ae(1000, 30705, 4_000_000_000L) : stack(1000, 30705, 12);
            if (ultimate) input.setString("StackType", "item");
            in.appendTag(input);
            in.appendTag(new NBTTagCompound());
            NBTTagCompound output = ultimate ? ae(1000, 30705, 2_000_000_000L) : stack(1000, 30705, 1);
            if (ultimate) output.setString("StackType", "item");
            out.appendTag(output);
            if (ultimate) {
                NBTTagCompound fluid = new NBTTagCompound();
                fluid.setString("StackType", "fluid");
                fluid.setString("FluidName", "water");
                fluid.setByte("Count", (byte) 0);
                fluid.setLong("Cnt", 1000);
                fluid.setLong("Req", 0);
                fluid.setBoolean("Craft", false);
                out.appendTag(fluid);
            }
            data.setTag("in", in);
            data.setTag("out", out);
            data.setBoolean("crafting", !ultimate);
            data.setBoolean("substitute", false);
            data.setBoolean("beSubstitute", false);
            meaning(c, "pattern.tag.in[0]", KEY);
            meaning(c, "pattern.tag.out[0]", KEY);
            MaterialIdOfflineCopy.rewrite(pattern, "pattern", c, 0);
            filled(input, ultimate ? 0 : 12);
            filled(output, ultimate ? 0 : 1);
            check(
                in.getCompoundTagAt(1)
                    .hasNoTags(),
                "pattern empty slot preserved");
            check(
                data.getBoolean("crafting") == !ultimate && !data.getBoolean("substitute")
                    && !data.getBoolean("beSubstitute"),
                "pattern crafting and substitution flags unchanged");
            if (ultimate) check(
                out.getCompoundTagAt(1)
                    .getString("FluidName")
                    .equals("water"),
                "ultimate fluid stack untouched");
        }

        c = context(scratch);
        NBTTagCompound unknownType = ae(1000, 30705, 10);
        unknownType.setString("StackType", "external:opaque");
        MaterialIdOfflineCopy.Context finalContext = c;
        rejected(
            () -> MaterialIdOfflineCopy.rewrite(unknownType, "unknown", finalContext, 0),
            "unknown ultimate StackType fails closed");
        NBTTagCompound level = level();
        check(
            MaterialIdFluidCellCopy.snapshot(level)
                .get("\u0002IC2:itemFluidCell") == 1002,
            "native FML numeric item mapping parsed");
        level.getCompoundTag("FML")
            .getTagList("ItemData", 10)
            .appendTag(registryEntry("\u0002fixture:duplicate", 1002));
        rejected(() -> MaterialIdFluidCellCopy.snapshot(level), "duplicate native numeric item ID rejected");
        return checks;
    }

    static NBTTagCompound level() {
        NBTTagCompound level = new NBTTagCompound(), fml = new NBTTagCompound();
        NBTTagList items = new NBTTagList();
        items.appendTag(registryEntry("\u0002gregtech:gt.metaitem.01", 1000));
        items.appendTag(registryEntry("\u0002fixture:other", 1001));
        items.appendTag(registryEntry("\u0002IC2:itemFluidCell", 1002));
        items.appendTag(registryEntry("\u0002appliedenergistics2:item.ItemBasicStorageCell.1k", 1003));
        items.appendTag(registryEntry("\u0002appliedenergistics2:item.ItemEncodedPattern", 1004));
        fml.setTag("ItemData", items);
        fml.setIntArray("BlockedItemIds", new int[0]);
        level.setTag("FML", fml);
        return level;
    }

    private static NBTTagCompound registryEntry(String key, int value) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("K", key);
        entry.setInteger("V", value);
        return entry;
    }

    private static MaterialIdOfflineCopy.Context context(Path scratch) throws IOException {
        MaterialIdOfflineCopy.Plan plan = new MaterialIdOfflineCopy.Plan();
        plan.sourceGt = plan.targetGt = "5.09.54.277";
        MaterialIdFluidCellCopy.FluidRule rule = new MaterialIdFluidCellCopy.FluidRule();
        rule.key = KEY;
        rule.fluidName = "gtsr_industrial_native_acid_cut";
        rule.oldMaterialId = 705;
        rule.numericItemId = 1000;
        rule.targetNumericItemId = 1002;
        rule.targetRegistryName = "IC2:itemFluidCell";
        plan.fluidRules = Arrays.asList(rule);
        Map<String, Integer> snapshot = MaterialIdFluidCellCopy.snapshot(level());
        Map<Integer, String> registry = new HashMap<>();
        for (Map.Entry<String, Integer> entry : snapshot.entrySet()) registry.put(
            entry.getValue(),
            entry.getKey()
                .substring(1));
        MaterialIdOfflineCopy.Context result = new MaterialIdOfflineCopy.Context(plan, registry);
        result.fluids = new MaterialIdFluidCellCopy.State(new Properties(), snapshot, scratch.resolve("level.dat"));
        return result;
    }

    private static void meaning(MaterialIdOfflineCopy.Context c, String path, String meaning) {
        c.fluids.semantics.setProperty(path, meaning);
    }

    private static NBTTagCompound ae(int id, int damage, long count) {
        NBTTagCompound stack = stack(id, damage, 0);
        stack.setLong("Cnt", count);
        stack.setLong("Req", 0);
        stack.setBoolean("Craft", false);
        return stack;
    }

    private static NBTTagCompound stack(int id, int damage, int count) {
        NBTTagCompound stack = new NBTTagCompound();
        stack.setShort("id", (short) id);
        stack.setShort("Damage", (short) damage);
        stack.setByte("Count", (byte) count);
        stack.setTag("tag", new NBTTagCompound());
        return stack;
    }

    private static void filled(NBTTagCompound stack, int count) {
        check(
            (stack.getShort("id") & 65535) == 1002 && stack.getShort("Damage") == 0,
            "old GT cell changes to target numeric IC2 universal item");
        NBTTagCompound fluid = stack.getCompoundTag("tag")
            .getCompoundTag("Fluid");
        check(
            fluid.getString("FluidName")
                .equals("gtsr_industrial_native_acid_cut") && fluid.getInteger("Amount") == 1000,
            "native IC2 Fluid NBT preserves 1000L per cell");
        check(
            (stack.hasKey("Count", 3) ? stack.getInteger("Count") : stack.getByte("Count")) == count,
            "original Count and numeric type preserved");
    }

    private interface Operation {

        void run() throws Exception;
    }

    private static void rejected(Operation operation, String message) throws Exception {
        try {
            operation.run();
        } catch (IOException | IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
