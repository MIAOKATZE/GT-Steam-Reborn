package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.readFluids;
import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.readItems;
import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.writeFluids;
import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.writeItems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

/** Stage payment checkpoints and the ordered construction recovery journal; never performs world or funding I/O. */
final class CriticalConstructionLedger {

    private final CriticalConstructionProgress progress = new CriticalConstructionProgress();
    private final List<ItemStack> spent = new ArrayList<>();
    private int round;
    private boolean funded, snapshot;
    private ItemStack[] stageItems = new ItemStack[0];
    private FluidStack[] stageFluids = new FluidStack[0], paidFluids = new FluidStack[0];

    /** One payment boundary. Placement may retry this boundary after its payment/time have been persisted. */
    static final class Round {

        final int end, next, advance, voxelEnd;
        private final ItemStack[] items;
        private final FluidStack[] fluids;

        private Round(int end, int next, int advance, int voxelEnd, ItemStack[] items, FluidStack[] fluids) {
            this.end = end;
            this.next = next;
            this.advance = advance;
            this.voxelEnd = voxelEnd;
            this.items = items;
            this.fluids = fluids;
        }

        ItemStack[] items() {
            return copyItems(items);
        }

        FluidStack[] fluids() {
            return copyFluids(fluids);
        }
    }

    int elapsed() {
        return progress.elapsed();
    }

    int round() {
        return round;
    }

    boolean funded() {
        return funded;
    }

    boolean hasStageSnapshot() {
        return snapshot;
    }

    boolean hasSpentItems() {
        return !spent.isEmpty();
    }

    void resetStage() {
        progress.reset();
        round = 0;
        funded = false;
        snapshot = false;
        stageItems = new ItemStack[0];
        stageFluids = new FluidStack[0];
    }

    void snapshotStage(ItemStack[] items, FluidStack[] fluids) {
        stageItems = copyItems(items);
        stageFluids = copyFluids(fluids);
        snapshot = true;
    }

    ItemStack[] stageItems() {
        return copyItems(stageItems);
    }

    FluidStack[] stageFluids() {
        return copyFluids(stageFluids);
    }

    static int roundCount(ItemStack[] costs) {
        long count = 0;
        for (ItemStack item : costs) if (item != null) count += item.stackSize;
        return (int) Math.max(1L, Math.min(200L, count));
    }

    ItemStack[] nextItems(ItemStack[] totals) {
        long units = 0;
        for (ItemStack item : totals) if (item != null) units += item.stackSize;
        int rounds = roundCount(totals);
        long start = units * round / rounds, end = units * (round + 1) / rounds, offset = 0;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : totals) if (item != null) {
            long overlap = Math.max(0, Math.min(end, offset + item.stackSize) - Math.max(start, offset));
            if (overlap > 0) {
                ItemStack cost = item.copy();
                cost.stackSize = (int) overlap;
                items.add(cost);
            }
            offset += item.stackSize;
        }
        return items.toArray(new ItemStack[0]);
    }

    FluidStack[] nextFluids(FluidStack[] totals, ItemStack[] itemTotals) {
        List<FluidStack> costs = new ArrayList<>();
        int rounds = roundCount(itemTotals);
        for (FluidStack total : totals) if (total != null) {
            int delta = (int) ((long) total.amount * (round + 1) / rounds - (long) total.amount * round / rounds);
            if (delta > 0) {
                FluidStack fluid = total.copy();
                fluid.amount = delta;
                costs.add(fluid);
            }
        }
        return costs.toArray(new FluidStack[0]);
    }

    FluidStack[] nextStageFluids() {
        return nextFluids(stageFluids, stageItems);
    }

    Round planRound(int tickBudget, int voxelCount) {
        int end = CriticalConstructionProgress.STAGE_DURATION * (round + 1) / roundCount(stageItems);
        int next = (int) Math.min(end, (long) progress.elapsed() + tickBudget);
        int voxelEnd = (int) ((long) voxelCount * end / CriticalConstructionProgress.STAGE_DURATION);
        return new Round(
            end,
            next,
            next - progress.elapsed(),
            voxelEnd,
            funded ? new ItemStack[0] : nextItems(stageItems),
            funded ? new FluidStack[0] : nextStageFluids());
    }

    void recordRoundPayment(ItemStack[] items, FluidStack[] fluids) {
        if (funded) return;
        for (ItemStack item : items) {
            boolean merged = false;
            for (ItemStack existing : spent)
                if (same(existing, item) && existing.stackSize <= Integer.MAX_VALUE - item.stackSize) {
                    existing.stackSize += item.stackSize;
                    merged = true;
                    break;
                }
            if (!merged) spent.add(item.copy());
        }
        for (FluidStack fluid : fluids) {
            boolean merged = false;
            for (FluidStack existing : paidFluids)
                if (existing.isFluidEqual(fluid) && existing.amount <= Integer.MAX_VALUE - fluid.amount) {
                    existing.amount += fluid.amount;
                    merged = true;
                    break;
                }
            if (!merged) {
                FluidStack[] next = Arrays.copyOf(paidFluids, paidFluids.length + 1);
                next[next.length - 1] = fluid.copy();
                paidFluids = next;
            }
        }
        funded = true;
    }

    void advanceElapsed(int next) {
        progress.advance(next);
    }

    void completeRound() {
        funded = false;
        round++;
    }

    /** Legacy escrow appends without merging: entry order determines the seeded recovery result. */
    void recordLegacyItem(ItemStack item) {
        spent.add(item.copy());
    }

    ItemStack[] recoveryItems(long seed) {
        List<ItemStack> refund = new ArrayList<>();
        Random random = new Random(seed);
        for (ItemStack stack : spent) {
            int count = 0;
            for (int i = 0; i < stack.stackSize; i++) if (random.nextInt(10) == 0) count++;
            if (count > 0) {
                ItemStack recovered = stack.copy();
                recovered.stackSize = count;
                refund.add(recovered);
            }
        }
        return refund.toArray(new ItemStack[0]);
    }

    void clearSpentItems() {
        spent.clear();
    }

    void clearPaidFluids() {
        paidFluids = new FluidStack[0];
    }

    void write(NBTTagCompound n) {
        n.setBoolean("constructionRoundFunded", funded);
        n.setInteger("constructionRound", round);
        progress.write(n);
        n.setBoolean("constructionCostSnapshot", snapshot);
        n.setTag("constructionStageCosts", writeItems(stageItems));
        n.setTag("constructionStageFluidCosts", writeFluids(stageFluids));
        n.setTag("constructionPaidFluids", writeFluids(paidFluids));
    }

    void read(NBTTagCompound n) {
        funded = n.getBoolean("constructionRoundFunded");
        round = Math.max(0, n.getInteger("constructionRound"));
        progress.read(n);
        snapshot = n.getBoolean("constructionCostSnapshot");
        stageItems = readItems(n.getTagList("constructionStageCosts", 10), -1);
        stageFluids = readFluids(n.getTagList("constructionStageFluidCosts", 10), -1);
        paidFluids = readFluids(n.getTagList("constructionPaidFluids", 10), -1);
    }

    void writeSpent(NBTTagCompound n) {
        n.setTag("spent", writeItems(spent.toArray(new ItemStack[0])));
    }

    void readSpent(NBTTagCompound n) {
        spent.clear();
        for (ItemStack item : readItems(n.getTagList("spent", 10), -1)) if (item != null) spent.add(item);
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static ItemStack[] copyItems(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].copy();
        return result;
    }

    private static FluidStack[] copyFluids(FluidStack[] source) {
        FluidStack[] result = new FluidStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].copy();
        return result;
    }
}
