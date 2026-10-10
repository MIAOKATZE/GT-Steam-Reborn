package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandom;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/** V4 reward planner. All stacks are copied and capacity checked before the persisted award begins. */
public final class SealedChestLoot {

    private static final int[][] ORDINARY = { { 15, 17 }, { 23, 25 }, { 31, 33 }, { 39, 41 }, { 47, 49 } };
    private static final int[][] DUNGEON = { { 4, 6 }, { 8, 12 }, { 12, 18 }, { 16, 24 }, { 20, 30 } };
    private static final JsonArray TIERS = load();

    private SealedChestLoot() {}

    public static void fill(TileEntityUnsealedChest chest, int tier, ItemStack relic) {
        if (!chest.canBeginReward()) return;
        apply(chest, prepare(chest, tier, relic));
    }

    /** Generate against a detached future inventory while the original sealed tile still owns the world position. */
    public static Prepared prepare(TileEntitySealedChest sealed, ItemStack relic) {
        TileEntityUnsealedChest inventory = new TileEntityUnsealedChest();
        inventory.setWorldObj(sealed.getWorldObj());
        inventory.xCoord = sealed.xCoord;
        inventory.yCoord = sealed.yCoord;
        inventory.zCoord = sealed.zCoord;
        return prepare(inventory, sealed.getTier(), relic);
    }

    private static Prepared prepare(TileEntityUnsealedChest chest, int tier, ItemStack relic) {
        tier = Math.max(1, Math.min(5, tier));
        Random root = chest.getWorldObj().rand;
        Random roll = new Random(root.nextLong()), layout = new Random(root.nextLong());
        List<ItemStack> plan = plan(chest, tier, roll);
        if (plan.size() > 53) throw new IllegalStateException("Sealed chest reward exceeds capacity");
        ItemStack[] contents = new ItemStack[54];
        int[] positions = new int[53];
        for (int i = 0, slot = 0; slot < 54; slot++) if (slot != 12) positions[i++] = slot;
        for (int i = positions.length - 1; i > 0; i--) {
            int j = layout.nextInt(i + 1), swap = positions[i];
            positions[i] = positions[j];
            positions[j] = swap;
        }
        for (int i = 0; i < plan.size(); i++) contents[positions[i]] = plan.get(i)
            .copy();
        if (relic != null) {
            ItemStack witness = relic.copy();
            witness.stackSize = 1;
            contents[12] = witness;
        }
        return new Prepared(tier, contents);
    }

    /** Commit an already checked plan once; no registry lookup, generation or random draw occurs here. */
    public static boolean apply(TileEntityUnsealedChest chest, Prepared prepared) {
        if (prepared == null || prepared.applied || !chest.beginReward(prepared.tier)) return false;
        prepared.applied = true;
        for (int slot = 0; slot < prepared.contents.length; slot++)
            if (prepared.contents[slot] != null) chest.setInventorySlotContents(slot, prepared.contents[slot].copy());
        chest.markDirty();
        return true;
    }

    public static final class Prepared {

        private final int tier;
        private final ItemStack[] contents;
        private boolean applied;

        private Prepared(int tier, ItemStack[] contents) {
            this.tier = tier;
            this.contents = contents;
        }
    }

    private static List<ItemStack> plan(IInventory chest, int tier, Random random) {
        int target = between(random, ORDINARY[tier - 1]);
        JsonObject table = TIERS.get(tier - 1)
            .getAsJsonObject();
        List<ItemStack> plan = new ArrayList<>();
        // Guarantees precede dungeon generation, so native results never displace the concrete GT circuit or ingot.
        List<Candidate> circuits = candidates(table.getAsJsonArray("circuits"));
        if (circuits.isEmpty()) throw new IllegalStateException("No concrete GT circuit for chest tier " + tier);
        add(plan, choose(random, circuits).roll(random), target);
        Materials metal = tier == 5 ? WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial()
            : new Materials[] { Materials.Aluminium, Materials.StainlessSteel, Materials.Titanium,
                Materials.TungstenSteel }[tier - 1];
        ItemStack ingot = GTOreDictUnificator.get(OrePrefixes.ingot, metal, 1);
        if (ingot == null) throw new IllegalStateException("No GT ingot for chest tier " + tier);
        ingot = ingot.copy();
        ingot.stackSize = 1 + random.nextInt(3);
        add(plan, ingot, target);
        for (JsonElement element : table.getAsJsonArray("rare")) {
            JsonObject reward = element.getAsJsonObject();
            if (random.nextDouble() * 100 >= reward.get("chancePercent")
                .getAsDouble()) continue;
            JsonObject definition = reward;
            if (reward.has("variants")) {
                JsonArray variants = reward.getAsJsonArray("variants");
                definition = variants.get(random.nextInt(variants.size()))
                    .getAsJsonObject();
            }
            ItemStack stack = resolve(definition);
            if (stack != null) {
                stack.stackSize = 1;
                add(plan, stack, target);
            }
        }
        int dungeonTarget = between(random, DUNGEON[tier - 1]);
        List<WeightedRandomChestContent> pool = new ArrayList<>(
            Arrays.asList(
                ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST)
                    .getItems(random)));
        pool.removeIf(entry -> entry.itemWeight <= 0 || entry.theItemId == null);
        List<ItemStack> dungeon = new ArrayList<>();
        while (!pool.isEmpty() && dungeon.size() < dungeonTarget) {
            WeightedRandomChestContent entry = (WeightedRandomChestContent) WeightedRandom.getRandomItem(random, pool);
            pool.remove(entry);
            // Every native emission is captured, even when vanilla randomly chooses the same slot twice.
            Capture capture = new Capture(chest);
            WeightedRandomChestContent
                .generateChestContents(random, new WeightedRandomChestContent[] { entry }, capture, 1);
            List<ItemStack> combined = new ArrayList<>(dungeon);
            combined.addAll(capture.stacks);
            List<ItemStack> generated = normalize(combined);
            if (generated.isEmpty() || generated.size() > dungeonTarget) continue;
            dungeon = generated;
            pool.removeIf(other -> same(entry.theItemId, other.theItemId));
        }
        if (dungeon.size() != dungeonTarget) throw new IllegalStateException(
            "Insufficient live dungeon rewards: tier=" + tier
                + ", target="
                + dungeonTarget
                + ", actual="
                + dungeon.size());
        plan.addAll(dungeon);
        List<ItemStack> merged = normalize(plan);
        plan.clear();
        plan.addAll(merged);
        List<Candidate> known = candidates(table.getAsJsonArray("parts"));
        known.addAll(circuits);
        known.addAll(candidates(table.getAsJsonArray("treasures")));
        known.removeIf(candidate -> contains(plan, candidate.stack));
        while (plan.size() < target && !known.isEmpty()) {
            Candidate next = choose(random, known);
            known.removeIf(candidate -> same(candidate.stack, next.stack));
            add(plan, next.roll(random), target);
        }
        if (plan.size() != target) throw new IllegalStateException(
            "Insufficient distinct sealed chest rewards: tier=" + tier
                + ", target="
                + target
                + ", actual="
                + plan.size());
        return plan;
    }

    private static boolean add(List<ItemStack> plan, ItemStack stack, int limit) {
        if (stack == null) return false;
        List<ItemStack> combined = new ArrayList<>(plan);
        combined.add(stack);
        List<ItemStack> split = normalize(combined);
        if (split.size() > limit) return false;
        plan.clear();
        plan.addAll(split);
        return true;
    }

    private static List<ItemStack> normalize(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : stacks) if (stack != null && stack.getItem() != null && stack.stackSize > 0) {
            int left = stack.stackSize, max = Math.min(64, stack.getMaxStackSize());
            if (max < 1) throw new IllegalStateException("Invalid reward maxStackSize");
            for (ItemStack existing : result) if (same(existing, stack) && existing.stackSize < max) {
                int transferred = Math.min(left, max - existing.stackSize);
                existing.stackSize += transferred;
                left -= transferred;
                if (left == 0) break;
            }
            while (left > 0) {
                ItemStack copy = stack.copy();
                copy.stackSize = Math.min(left, max);
                result.add(copy);
                left -= copy.stackSize;
            }
        }
        return result;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.getItem() == b.getItem()
            && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static boolean contains(List<ItemStack> stacks, ItemStack stack) {
        for (ItemStack other : stacks) if (same(other, stack)) return true;
        return false;
    }

    private static int between(Random random, int[] range) {
        return range[0] + random.nextInt(range[1] - range[0] + 1);
    }

    private static List<Candidate> candidates(JsonArray entries) {
        List<Candidate> result = new ArrayList<>();
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            int min = 1, max = 3;
            if (entry.has("countRange")) {
                min = entry.getAsJsonArray("countRange")
                    .get(0)
                    .getAsInt();
                max = entry.getAsJsonArray("countRange")
                    .get(1)
                    .getAsInt();
            }
            if (entry.has("variants")) {
                for (JsonElement variant : entry.getAsJsonArray("variants")) {
                    ItemStack variantStack = resolve(variant.getAsJsonObject());
                    if (variantStack != null) result.add(new Candidate(variantStack, 1, min, max));
                }
            } else {
                ItemStack stack = resolve(entry);
                if (stack != null) result.add(
                    new Candidate(
                        stack,
                        entry.get("weight")
                            .getAsInt(),
                        min,
                        max));
            }
        }
        return result;
    }

    private static ItemStack resolve(JsonObject entry) {
        if (entry.has("itemlist")) {
            ItemList item = ItemList.valueOf(
                entry.get("itemlist")
                    .getAsString());
            return item.hasBeenSet() ? item.get(1) : null;
        }
        String[] registry = entry.get("item")
            .getAsString()
            .split(":", 2);
        Item item = GameRegistry.findItem(registry[0], registry[1]);
        return item == null ? null
            : new ItemStack(
                item,
                1,
                entry.has("damage") ? entry.get("damage")
                    .getAsInt() : 0);
    }

    private static Candidate choose(Random random, List<Candidate> pool) {
        int total = 0;
        for (Candidate candidate : pool) total += candidate.weight;
        int value = random.nextInt(total);
        for (Candidate candidate : pool) {
            value -= candidate.weight;
            if (value < 0) return candidate;
        }
        throw new AssertionError();
    }

    private static JsonArray load() {
        try (InputStream stream = SealedChestLoot.class.getResourceAsStream("/assets/gtsr/loot/sealed-chest-v4.json")) {
            if (stream == null) throw new IllegalStateException("Missing sealed chest candidate table");
            return new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonArray("tiers");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class Candidate {

        final ItemStack stack;
        final int weight, min, max;

        Candidate(ItemStack stack, int weight, int min, int max) {
            this.stack = stack;
            this.weight = weight;
            this.min = min;
            this.max = max;
        }

        ItemStack roll(Random random) {
            ItemStack copy = stack.copy();
            copy.stackSize = min + random.nextInt(max - min + 1);
            return copy;
        }
    }

    private static final class Capture implements IInventory {

        final IInventory delegate;
        final List<ItemStack> stacks = new ArrayList<>();

        Capture(IInventory delegate) {
            this.delegate = delegate;
        }

        public int getSizeInventory() {
            return delegate.getSizeInventory();
        }

        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        public ItemStack decrStackSize(int slot, int count) {
            return null;
        }

        public ItemStack getStackInSlotOnClosing(int slot) {
            return null;
        }

        public void setInventorySlotContents(int slot, ItemStack stack) {
            if (stack != null) stacks.add(stack.copy());
        }

        public String getInventoryName() {
            return delegate.getInventoryName();
        }

        public boolean hasCustomInventoryName() {
            return delegate.hasCustomInventoryName();
        }

        public int getInventoryStackLimit() {
            return delegate.getInventoryStackLimit();
        }

        public void markDirty() {}

        public boolean isUseableByPlayer(EntityPlayer player) {
            return delegate.isUseableByPlayer(player);
        }

        public void openInventory() {}

        public void closeInventory() {}

        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return delegate.isItemValidForSlot(slot, stack);
        }
    }
}
