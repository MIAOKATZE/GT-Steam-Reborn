package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.readFluids;
import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.readItems;
import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.writeFluids;
import static com.miaokatze.gtsr.common.critical.CriticalResourceNbt.writeItems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

/** Stage payment checkpoints and the ordered construction recovery journal; never performs world or funding I/O. */
final class CriticalConstructionLedger {

    private final CriticalConstructionProgress progress = new CriticalConstructionProgress();
    private final List<ItemStack> spent = new ArrayList<>();
    private int round, exactSteps, exactVoxelEnd = -1;
    private boolean funded, snapshot;
    private ItemStack[] stageItems = new ItemStack[0];
    private FluidStack[] stageFluids = new FluidStack[0], paidFluids = new FluidStack[0];
    private CriticalConstructionEconomy physicalJob;
    private final List<Account> accounts = new ArrayList<>();
    private final Set<Long> installedPositions = new HashSet<>();
    private ItemStack[] physicalNext = new ItemStack[0];
    private List<CriticalGeometry.Voxel> previewStage;
    private ItemStack[] previewStageCosts;
    private boolean integrityError;
    private NBTTagCompound unreadableEconomy;

    boolean hasIntegrityError() {
        return integrityError;
    }

    private static final class Account {

        final ItemStack item;
        long paid, escrow, installed, destroyed, refunded;

        Account(ItemStack item) {
            this.item = item.copy();
            this.item.stackSize = 1;
        }
    }

    /** Only new jobs opt in. Missing NBT fields always retain the complete legacy economy. */
    void freezeJob(CriticalConstructionEconomy job) {
        if (integrityError || physicalJob != null || !spent.isEmpty() || !accounts.isEmpty())
            throw new IllegalStateException("Cannot replace a paid construction price basis");
        physicalJob = job;
    }

    boolean isPhysicalJob() {
        return physicalJob != null;
    }

    int economyVersion() {
        return isPhysicalJob() ? physicalJob.version() : 0;
    }

    CriticalConstructionEconomy physicalJob() {
        return physicalJob;
    }

    String layoutKey() {
        return physicalJob == null ? "" : physicalJob.layoutKey();
    }

    void resetJob() {
        if (hasSpentItems()) throw new IllegalStateException("Unsettled construction assets");
        physicalJob = null;
        accounts.clear();
        installedPositions.clear();
        resetStage();
    }

    void snapshotPhysicalStage(List<CriticalGeometry.Voxel> current) {
        snapshotStage(
            physicalJob.costs(current, 0, current.size()),
            physicalJob.fluidCosts(current, 0, current.size()));
        if (physicalJob.version() >= 6)
            exactSteps = physicalJob.stepsForStage(physicalJob.stage(current.get(0)), stageItems, stageFluids);
    }

    int stageRoundCount(ItemStack[] items, FluidStack[] fluids, int stage) {
        return economyVersion() >= 6 ? snapshot ? exactSteps : physicalJob.stepsForStage(stage, items, fluids)
            : roundCount(items);
    }

    int stageDuration() {
        return economyVersion() >= 6 ? CriticalConstructionProgress.STAGE_DURATION * Math.max(1, exactSteps)
            : CriticalConstructionProgress.STAGE_DURATION;
    }

    ItemStack[] physicalNextItems(List<CriticalGeometry.Voxel> current) {
        if (!snapshot && previewStage != current) {
            previewStage = current;
            previewStageCosts = physicalJob.costs(current, 0, current.size());
        }
        if (physicalJob.version() >= 6) return exactNextItems(current);
        int rounds = roundCount(snapshot ? stageItems : previewStageCosts);
        int start = (int) ((long) current.size() * round / rounds);
        int end = (int) ((long) current.size() * (round + 1) / rounds);
        List<ItemStack> bill = new ArrayList<>();
        for (ItemStack item : physicalJob.costs(current, start, end)) CriticalConstructionEconomy.merge(bill, item);
        // The complete controller is reserved on the first material payment, before its marker is installed.
        ItemStack controller = physicalJob.controller();
        Account controllerAccount = findAccount(controller);
        if (physicalJob.version() < 5 && controllerAccount.paid == 0) {
            boolean included = false;
            for (ItemStack item : bill) if (same(item, controller)) included = true;
            if (!included) bill.add(controller);
        }
        List<ItemStack> debit = new ArrayList<>();
        for (ItemStack item : bill) {
            long missing = (long) item.stackSize - findAccount(item).escrow;
            if (missing > 0) {
                ItemStack wanted = item.copy();
                wanted.stackSize = (int) missing;
                debit.add(wanted);
            }
        }
        physicalNext = debit.toArray(new ItemStack[0]);
        return copyItems(physicalNext);
    }

    /** The paid identities are exactly those installed inside this voxel boundary, including carrier equipment. */
    Round planPhysicalRound(int tickBudget, List<CriticalGeometry.Voxel> current) {
        if (physicalJob.version() >= 6) {
            int end = CriticalConstructionProgress.STAGE_DURATION * (round + 1);
            int next = (int) Math.min(end, (long) progress.elapsed() + tickBudget);
            ItemStack[] debit = funded ? new ItemStack[0] : physicalNextItems(current);
            if (!funded || exactVoxelEnd < 0) exactVoxelEnd = affordableVoxelEnd(current, debit);
            return new Round(
                end,
                next,
                next - progress.elapsed(),
                exactVoxelEnd,
                debit,
                funded ? new FluidStack[0] : nextStageFluids());
        }
        Round timing = planRound(tickBudget, current.size());
        physicalNext = physicalNextItems(current);
        int voxelEnd = (int) ((long) current.size() * (round + 1) / roundCount(stageItems));
        return new Round(
            timing.end,
            timing.next,
            timing.advance,
            voxelEnd,
            funded ? new ItemStack[0] : copyItems(physicalNext),
            funded ? new FluidStack[0] : nextStageFluids());
    }

    private ItemStack[] exactNextItems(List<CriticalGeometry.Voxel> current) {
        ItemStack[] totals = snapshot ? stageItems : previewStageCosts;
        FluidStack[] fluids = snapshot ? stageFluids : physicalJob.fluidCosts(current, 0, current.size());
        int steps = stageRoundCount(totals, fluids, current.isEmpty() ? 9 : physicalJob.stage(current.get(0)));
        List<ItemStack> installed = new ArrayList<>();
        for (CriticalGeometry.Voxel voxel : current) if (installedPositions.contains(voxel.positionKey()))
            for (ItemStack item : physicalJob.bill(voxel)) CriticalConstructionEconomy.merge(installed, item);
        List<ItemStack> debit = new ArrayList<>();
        for (ItemStack item : totals) {
            long missing = (long) item.stackSize / steps * (round + 1) - findAccount(item).escrow;
            for (ItemStack placed : installed) if (same(item, placed)) missing -= placed.stackSize;
            if (missing > 0) {
                ItemStack wanted = item.copy();
                wanted.stackSize = Math.toIntExact(missing);
                debit.add(wanted);
            }
        }
        physicalNext = debit.toArray(new ItemStack[0]);
        return copyItems(physicalNext);
    }

    /** A fixed repeated debit can precede its physical installation; escrow must cover each placed identity. */
    private int affordableVoxelEnd(List<CriticalGeometry.Voxel> current, ItemStack[] debit) {
        List<ItemStack> budget = new ArrayList<>();
        for (Account account : accounts) if (account.escrow > 0) {
            ItemStack item = account.item.copy();
            item.stackSize = Math.toIntExact(account.escrow);
            CriticalConstructionEconomy.merge(budget, item);
        }
        for (ItemStack item : debit) CriticalConstructionEconomy.merge(budget, item);
        int end = 0;
        for (CriticalGeometry.Voxel voxel : current) {
            if (!installedPositions.contains(voxel.positionKey())) {
                ItemStack[] bill = physicalJob.bill(voxel);
                for (ItemStack wanted : bill) {
                    boolean covered = false;
                    for (ItemStack available : budget)
                        if (same(wanted, available) && available.stackSize >= wanted.stackSize) covered = true;
                    if (!covered) return end;
                }
                for (ItemStack wanted : bill) for (ItemStack available : budget)
                    if (same(wanted, available)) available.stackSize -= wanted.stackSize;
            }
            end++;
        }
        return end;
    }

    private Account account(ItemStack item) {
        for (Account account : accounts) if (same(account.item, item)) return account;
        Account account = new Account(item);
        accounts.add(account);
        return account;
    }

    private Account findAccount(ItemStack item) {
        for (Account account : accounts) if (same(account.item, item)) return account;
        return new Account(item);
    }

    /** Called only after a genuinely new placement. Existing player repairs leave their paid allocation in escrow. */
    void markPhysicalInstalled(CriticalGeometry.Voxel voxel) {
        if (physicalJob == null || installedPositions.contains(voxel.positionKey())) return;
        ItemStack[] bill = physicalJob.bill(voxel);
        // Creative/debug placements never manufacture a salvage entitlement.
        for (ItemStack item : bill) if (account(item).escrow < item.stackSize) return;
        for (ItemStack item : bill) {
            Account account = account(item);
            account.escrow -= item.stackSize;
            account.installed += item.stackSize;
        }
        installedPositions.add(voxel.positionKey());
    }

    /** Called once when the repair scan confirms a missing economic carrier; adjacent panels cost only themselves. */
    void markPhysicalDamaged(CriticalGeometry.Voxel voxel) {
        if (physicalJob == null || !installedPositions.remove(voxel.positionKey())) return;
        for (ItemStack item : physicalJob.bill(voxel)) {
            Account account = account(item);
            long count = Math.min(account.installed, item.stackSize);
            account.installed -= count;
            account.destroyed += count;
        }
    }

    /** Server-owned exact balances; visible ME stock is deliberately absent. */
    NBTTagCompound guiSnapshot() {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("version", economyVersion());
        n.setString("layoutKey", layoutKey());
        n.setBoolean("integrityError", integrityError);
        NBTTagList rows = new NBTTagList();
        if (integrityError) {
            n.setTag("rows", rows);
            return n;
        }
        if (physicalJob != null) {
            List<ItemStack> identities = new ArrayList<>();
            for (ItemStack item : physicalJob.displayDemand()) identities.add(item);
            for (Account account : accounts) {
                boolean present = false;
                for (ItemStack item : identities) if (same(item, account.item)) present = true;
                if (!present) {
                    ItemStack item = account.item.copy();
                    item.stackSize = 0;
                    identities.add(item);
                }
            }
            for (ItemStack item : identities) {
                Account account = findAccount(item);
                NBTTagCompound row = new NBTTagCompound();
                row.setString("family", physicalJob.family(item));
                row.setString("key", physicalJob.identity(item));
                row.setString("name", item.getDisplayName());
                row.setString("demand", Long.toString(item.stackSize));
                if (physicalJob.family(item)
                    .length() == 1) {
                    long batches = ((long) item.stackSize + 15) / 16;
                    row.setString("manufacturingBatches", Long.toString(batches));
                    row.setString("manufacturingOutput", Long.toString(batches * 16));
                    row.setString("manufacturingSurplus", Long.toString(batches * 16 - item.stackSize));
                }
                row.setString("paid", Long.toString(account.paid));
                row.setString("installed", Long.toString(account.installed));
                row.setString("escrow", Long.toString(account.escrow));
                row.setString("destroyed", Long.toString(account.destroyed));
                row.setString("refunded", Long.toString(account.refunded));
                row.setString(
                    "missing",
                    Long.toString(Math.max(0, (long) item.stackSize - account.installed - account.escrow)));
                long next = 0;
                if (!funded) for (ItemStack wanted : physicalNext) if (same(wanted, item)) next += wanted.stackSize;
                row.setString("next", Long.toString(next));
                rows.appendTag(row);
            }
        }
        n.setTag("rows", rows);
        return n;
    }

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
        if (integrityError) return true;
        for (Account account : accounts) if (account.escrow > 0 || account.installed > 0) return true;
        return !spent.isEmpty();
    }

    void resetStage() {
        progress.reset();
        round = 0;
        exactSteps = 0;
        exactVoxelEnd = -1;
        funded = false;
        snapshot = false;
        stageItems = new ItemStack[0];
        stageFluids = new FluidStack[0];
        physicalNext = new ItemStack[0];
        previewStage = null;
        previewStageCosts = null;
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
        int rounds = economyVersion() >= 6 && snapshot ? Math.max(1, exactSteps) : roundCount(itemTotals);
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
        if (physicalJob != null) {
            for (ItemStack item : items) {
                Account account = account(item);
                account.paid += item.stackSize;
                account.escrow += item.stackSize;
            }
            recordFluids(fluids);
            funded = true;
            return;
        }
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
        recordFluids(fluids);
        funded = true;
    }

    private void recordFluids(FluidStack[] fluids) {
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
    }

    void advanceElapsed(int next) {
        progress.advance(next, stageDuration());
    }

    void completeRound() {
        exactVoxelEnd = -1;
        funded = false;
        round++;
    }

    /** Legacy escrow appends without merging: entry order determines the seeded recovery result. */
    void recordLegacyItem(ItemStack item) {
        spent.add(item.copy());
    }

    ItemStack[] recoveryItems(long seed) {
        if (integrityError) throw new IllegalStateException("Construction economy requires manual verification");
        if (physicalJob != null) {
            List<ItemStack> refund = new ArrayList<>();
            for (Account account : accounts) {
                long installedRefund = physicalJob.isController(account.item) ? Math.min(1L, account.installed)
                    : account.installed / 10;
                long count = account.escrow + installedRefund;
                while (count > 0) {
                    ItemStack recovered = account.item.copy();
                    recovered.stackSize = (int) Math.min(Integer.MAX_VALUE, count);
                    refund.add(recovered);
                    count -= recovered.stackSize;
                }
            }
            return refund.toArray(new ItemStack[0]);
        }
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
        for (Account account : accounts) {
            long recovered = physicalJob.isController(account.item) ? Math.min(1L, account.installed)
                : account.installed / 10;
            account.refunded += account.escrow + recovered;
            account.destroyed += account.installed - recovered;
            account.escrow = account.installed = 0;
        }
        installedPositions.clear();
    }

    void clearPaidFluids() {
        paidFluids = new FluidStack[0];
    }

    void write(NBTTagCompound n) {
        n.setBoolean("constructionRoundFunded", funded);
        n.setInteger("constructionRound", round);
        if (economyVersion() >= 6 && funded && exactVoxelEnd >= 0)
            n.setInteger("constructionRoundVoxelEnd", exactVoxelEnd);
        else n.removeTag("constructionRoundVoxelEnd");
        if (economyVersion() >= 6) n.setInteger("constructionStepCount", exactSteps);
        else n.removeTag("constructionStepCount");
        progress.write(n);
        n.setBoolean("constructionCostSnapshot", snapshot);
        n.setTag("constructionStageCosts", writeItems(stageItems));
        n.setTag("constructionStageFluidCosts", writeFluids(stageFluids));
        n.setTag("constructionPaidFluids", writeFluids(paidFluids));
        n.setInteger("constructionEconomyVersion", economyVersion());
        if (physicalJob != null) {
            NBTTagCompound job = new NBTTagCompound();
            physicalJob.write(job);
            n.setTag("constructionPhysicalJob", job);
        } else n.removeTag("constructionPhysicalJob");
        NBTTagList balances = new NBTTagList();
        for (Account account : accounts) {
            NBTTagCompound row = new NBTTagCompound();
            row.setTag("item", writeItems(new ItemStack[] { account.item }));
            row.setLong("paid", account.paid);
            row.setLong("escrow", account.escrow);
            row.setLong("installed", account.installed);
            row.setLong("destroyed", account.destroyed);
            row.setLong("refunded", account.refunded);
            balances.appendTag(row);
        }
        n.setTag("constructionPhysicalBalances", balances);
        long[] positions = new long[installedPositions.size()];
        int index = 0;
        for (Long position : installedPositions) positions[index++] = position;
        java.util.Arrays.sort(positions);
        java.nio.ByteBuffer installed = java.nio.ByteBuffer.allocate(positions.length * Long.BYTES);
        for (long position : positions) installed.putLong(position);
        n.setByteArray("constructionPhysicalInstalled", installed.array());
        n.setBoolean("constructionEconomyUncertain", integrityError);
        if (unreadableEconomy != null) {
            // Keep the complete original machine record as evidence, and preserve every original economic field.
            n.setTag("constructionEconomyQuarantine", unreadableEconomy.copy());
            for (String key : new String[] { "constructionEconomyVersion", "constructionPhysicalJob",
                "constructionPhysicalBalances", "constructionPhysicalInstalled" }) {
                if (unreadableEconomy.hasKey(key)) n.setTag(
                    key,
                    unreadableEconomy.getTag(key)
                        .copy());
                else n.removeTag(key);
            }
        } else n.removeTag("constructionEconomyQuarantine");
    }

    void read(NBTTagCompound n) {
        funded = n.getBoolean("constructionRoundFunded");
        round = Math.max(0, n.getInteger("constructionRound"));
        exactVoxelEnd = -1;
        progress.read(n);
        snapshot = n.getBoolean("constructionCostSnapshot");
        stageItems = readItems(n.getTagList("constructionStageCosts", 10), -1);
        stageFluids = readFluids(n.getTagList("constructionStageFluidCosts", 10), -1);
        paidFluids = readFluids(n.getTagList("constructionPaidFluids", 10), -1);
        int version = n.getInteger("constructionEconomyVersion");
        integrityError = n.getBoolean("constructionEconomyUncertain")
            || version != 0 && !CriticalConstructionEconomy.supportsVersion(version)
            || version >= 2 && !n.hasKey("constructionPhysicalJob");
        unreadableEconomy = null;
        physicalJob = null;
        accounts.clear();
        installedPositions.clear();
        previewStage = null;
        previewStageCosts = null;
        physicalNext = new ItemStack[0];
        try {
            physicalJob = CriticalConstructionEconomy.supportsVersion(version) && n.hasKey("constructionPhysicalJob")
                ? CriticalConstructionEconomy.read(n.getCompoundTag("constructionPhysicalJob"))
                : null;
            if (physicalJob != null && physicalJob.version() != version) integrityError = true;
            exactSteps = version >= 6 ? n.getInteger("constructionStepCount") : 0;
            if (version >= 6 && funded && n.hasKey("constructionRoundVoxelEnd")) {
                exactVoxelEnd = n.getInteger("constructionRoundVoxelEnd");
                long voxelCount = 0;
                for (ItemStack item : stageItems) if (physicalJob.isController(item) || physicalJob.family(item)
                    .length() == 1) voxelCount += item.stackSize;
                if (exactVoxelEnd < 0 || exactVoxelEnd > voxelCount || exactVoxelEnd < n.getInteger("cursor"))
                    integrityError = true;
            }
            int maxSteps = version >= 7 ? CriticalConstructionEconomy.MAX_WORK_STEPS : 200;
            if (version >= 6 && (snapshot && (exactSteps < 1 || exactSteps > maxSteps) || !snapshot && exactSteps != 0
                || snapshot && round >= exactSteps)) integrityError = true;
            if (version >= 6 && snapshot && exactSteps > 0) {
                for (ItemStack item : stageItems) if (item.stackSize % exactSteps != 0) integrityError = true;
                for (FluidStack fluid : stageFluids) if (fluid.amount % exactSteps != 0) integrityError = true;
            }
            if (version >= 6) progress.advance(n.getInteger("constructionElapsed"), stageDuration());
            accounts.clear();
            NBTTagList balances = n.getTagList("constructionPhysicalBalances", 10);
            if (physicalJob != null) for (int i = 0; i < balances.tagCount(); i++) {
                NBTTagCompound row = balances.getCompoundTagAt(i);
                Account account = new Account(readItems(row.getTagList("item", 10), 1)[0]);
                account.paid = row.getLong("paid");
                account.escrow = row.getLong("escrow");
                account.installed = row.getLong("installed");
                account.destroyed = row.getLong("destroyed");
                account.refunded = row.getLong("refunded");
                if (account.paid < 0 || account.escrow < 0
                    || account.installed < 0
                    || account.destroyed < 0
                    || account.refunded < 0
                    || account.paid != account.escrow + account.installed + account.destroyed + account.refunded)
                    integrityError = true;
                accounts.add(account);
            }
            installedPositions.clear();
            java.nio.ByteBuffer positions = java.nio.ByteBuffer.wrap(n.getByteArray("constructionPhysicalInstalled"));
            if (physicalJob != null)
                while (positions.remaining() >= Long.BYTES) installedPositions.add(positions.getLong());
            if (physicalJob != null && positions.remaining() != 0) integrityError = true;
        } catch (RuntimeException invalidEconomy) {
            integrityError = true;
            physicalJob = null;
            accounts.clear();
            installedPositions.clear();
        }
        if (integrityError) {
            unreadableEconomy = (NBTTagCompound) (n.hasKey("constructionEconomyQuarantine")
                ? n.getCompoundTag("constructionEconomyQuarantine")
                    .copy()
                : n.copy());
            unreadableEconomy.removeTag("constructionEconomyQuarantine");
        }
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
