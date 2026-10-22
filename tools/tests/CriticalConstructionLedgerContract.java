package com.miaokatze.gtsr.common.critical;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Real resource/NBT contracts for stage payments; no world, client, GL or funding backend. */
public final class CriticalConstructionLedgerContract {

    private static int checks;
    private static final Item A = new Item(), B = new Item();
    private static final Fluid FLUID = new Fluid("gtsr_ledger_contract");

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static ItemStack item(Item item, int count) {
        return new ItemStack(item, count, 0);
    }

    private static NBTTagCompound save(CriticalConstructionLedger ledger) {
        NBTTagCompound tag = new NBTTagCompound();
        ledger.write(tag);
        ledger.writeSpent(tag);
        return tag;
    }

    private static CriticalConstructionLedger load(NBTTagCompound tag) {
        CriticalConstructionLedger ledger = new CriticalConstructionLedger();
        ledger.read(tag);
        ledger.readSpent(tag);
        return ledger;
    }

    private static long count(ItemStack[] stacks, Item item) {
        long count = 0;
        for (ItemStack stack : stacks) if (stack != null && stack.getItem() == item) count += stack.stackSize;
        return count;
    }

    private static void conservation(int first, int second, int fluidAmount) {
        ItemStack[] totals = { first == 0 ? null : item(A, first), null, second == 0 ? null : item(B, second) };
        CriticalConstructionLedger ledger = new CriticalConstructionLedger();
        ledger.snapshotStage(totals, new FluidStack[] { new FluidStack(FLUID, fluidAmount) });
        int rounds = CriticalConstructionLedger.roundCount(totals), previousEnd = 0;
        long paidA = 0, paidB = 0, paidFluid = 0;
        check(rounds >= 1 && rounds <= 200, "Payment round count is bounded even with full-int resources");
        for (int round = 0; round < rounds; round++) {
            CriticalConstructionLedger.Round payment = ledger.planRound(400, 113);
            check(payment.end > previousEnd && payment.end <= 400, "Every round owns a positive bounded time span");
            check(payment.next == payment.end && payment.advance == payment.end - previousEnd,
                "Instant work reaches only the current unpaid boundary");
            paidA += count(payment.items(), A);
            paidB += count(payment.items(), B);
            for (FluidStack fluid : payment.fluids()) paidFluid += fluid.amount;
            ledger.recordRoundPayment(payment.items(), payment.fluids());
            ledger.advanceElapsed(payment.next);
            NBTTagCompound checkpoint = save(ledger);
            ledger = load(checkpoint);
            CriticalConstructionLedger.Round retry = ledger.planRound(400, 113);
            check(ledger.funded() && retry.advance == 0 && retry.next == payment.next,
                "A saved paid placement retry requests no additional elapsed work");
            ledger.recordRoundPayment(payment.items(), payment.fluids());
            check(save(ledger).equals(checkpoint), "Retrying a recorded payment cannot accrue a second debit");
            previousEnd = payment.end;
            if (round + 1 == rounds) check(payment.voxelEnd == 113, "Final boundary includes every target voxel");
            ledger.completeRound();
        }
        check(paidA == first && paidB == second && paidFluid == fluidAmount,
            "All round payments conserve each resource exactly across arbitrary indivisible totals");
        check(ledger.elapsed() == 400 && ledger.round() == rounds && !ledger.funded(),
            "The finished stage retains time until the state machine resets it");
        NBTTagCompound saved = save(ledger);
        ItemStack[] spent = CriticalResourceNbt.readItems(saved.getTagList("spent", 10), -1);
        check(count(spent, A) == first && count(spent, B) == second, "Persisted item recovery ledger conserves payment");
        long fluids = 0;
        for (FluidStack paid : CriticalResourceNbt.readFluids(saved.getTagList("constructionPaidFluids", 10), -1))
            fluids += paid.amount;
        check(fluids == fluidAmount, "Persisted fluid debit ledger conserves payment");
        ledger.resetStage();
        check(!ledger.funded() && !ledger.hasStageSnapshot() && ledger.round() == 0 && ledger.elapsed() == 0,
            "Stage reset clears the checkpoint");
        check(save(ledger).getTagList("spent", 10).equals(saved.getTagList("spent", 10)),
            "Stage reset preserves historical construction assets");
    }

    private static void persistenceAndRecovery() {
        CriticalConstructionLedger ledger = new CriticalConstructionLedger();
        ledger.recordLegacyItem(item(A, 27));
        ledger.recordLegacyItem(item(B, 41));
        ledger.recordLegacyItem(item(A, 13));
        NBTTagCompound saved = save(ledger);
        ledger = load(saved);
        ItemStack[] recovery = ledger.recoveryItems(12345);
        check(recovery.length == 3 && recovery[0].getItem() == A && recovery[0].stackSize == 2
            && recovery[1].getItem() == B && recovery[1].stackSize == 5
            && recovery[2].getItem() == A && recovery[2].stackSize == 1,
            "Seeded legacy recovery retains entry order and all 81 random draws");
        recovery[0].stackSize = 999;
        check(ledger.recoveryItems(12345)[0].stackSize == 2 && save(ledger).equals(saved),
            "Preparing recovery is deterministic, defensive and does not consume the journal");
        ledger.clearSpentItems();
        check(!ledger.hasSpentItems(), "Only the caller's explicit recovery checkpoint clears spent items");

        ledger = new CriticalConstructionLedger();
        ledger.snapshotStage(new ItemStack[] { item(A, Integer.MAX_VALUE) },
            new FluidStack[] { new FluidStack(FLUID, Integer.MAX_VALUE) });
        ledger.recordRoundPayment(new ItemStack[] { item(A, Integer.MAX_VALUE) },
            new FluidStack[] { new FluidStack(FLUID, Integer.MAX_VALUE) });
        ledger.completeRound();
        ledger.recordRoundPayment(new ItemStack[] { item(A, 1) }, new FluidStack[] { new FluidStack(FLUID, 1) });
        saved = save(load(save(ledger)));
        NBTTagList spent = saved.getTagList("spent", 10), fluids = saved.getTagList("constructionPaidFluids", 10);
        check(spent.tagCount() == 2 && spent.getCompoundTagAt(0).getInteger("fullCount") == Integer.MAX_VALUE
            && spent.getCompoundTagAt(1).getInteger("fullCount") == 1,
            "Item journal splits an overflowing merge and retains full counts");
        check(fluids.tagCount() == 2 && fluids.getCompoundTagAt(0).getInteger("Amount") == Integer.MAX_VALUE
            && fluids.getCompoundTagAt(1).getInteger("Amount") == 1,
            "Fluid journal splits an overflowing merge");

        ledger = load(new NBTTagCompound());
        check(ledger.elapsed() == 0 && ledger.round() == 0 && !ledger.funded() && !ledger.hasStageSnapshot()
            && !ledger.hasSpentItems(), "Missing old-save fields preserve legacy defaults");
        NBTTagCompound old = new NBTTagCompound();
        old.setInteger("constructionElapsed", 999);
        old.setInteger("constructionRound", -20);
        old.setBoolean("constructionRoundFunded", true);
        ItemStack legacy = item(A, 93);
        NBTTagCompound entry = new NBTTagCompound();
        legacy.writeToNBT(entry);
        entry.setInteger("slot", 3);
        NBTTagList sparse = new NBTTagList();
        sparse.appendTag(entry);
        old.setTag("spent", sparse);
        ledger = load(old);
        check(ledger.elapsed() == 400 && ledger.round() == 0 && ledger.funded(),
            "Malformed legacy time/round values retain the original clamps");
        check(save(ledger).getTagList("spent", 10).getCompoundTagAt(0).getInteger("fullCount") == 93,
            "Old vanilla Count without fullCount remains readable through sparse legacy slots");

        ItemStack[] sparseItems = { null, item(A, 1000), null, item(B, 17) };
        ItemStack[] restored = CriticalResourceNbt.readItems(CriticalResourceNbt.writeItems(sparseItems), -1);
        check(restored.length == 4 && restored[0] == null && restored[2] == null && restored[1].stackSize == 1000,
            "Shared codec preserves sparse slot identity and counts larger than one byte");
        ItemStack[] fixed = CriticalResourceNbt.readItems(CriticalResourceNbt.writeItems(sparseItems), 2);
        check(fixed.length == 2 && fixed[1].stackSize == 1000, "Fixed-size inventory drops only out-of-range slots");
    }

    private static void defensiveSnapshotAndPartialTime() {
        CriticalConstructionLedger ledger = new CriticalConstructionLedger();
        ItemStack[] items = { item(A, 2) };
        FluidStack[] fluids = { new FluidStack(FLUID, 17) };
        ledger.snapshotStage(items, fluids);
        items[0].stackSize = 100;
        fluids[0].amount = 100;
        ledger.stageItems()[0].stackSize = 200;
        ledger.stageFluids()[0].amount = 200;
        check(ledger.stageItems()[0].stackSize == 2 && ledger.stageFluids()[0].amount == 17,
            "Caller and GUI snapshots cannot mutate the saved cost basis");
        CriticalConstructionLedger.Round first = ledger.planRound(1, 113);
        ledger.recordRoundPayment(first.items(), first.fluids());
        ledger.advanceElapsed(first.next);
        ledger = load(save(ledger));
        CriticalConstructionLedger.Round next = ledger.planRound(10, 113);
        check(ledger.funded() && ledger.elapsed() == 1 && next.next == 11 && next.advance == 10 && next.end == 200,
            "Save/reload preserves partially timed paid rounds without moving the payment boundary");
        check(next.items().length == 0 && next.fluids().length == 0,
            "Paid retry plans contain no resource debits");
        check(Arrays.stream(ledger.nextItems(ledger.stageItems())).mapToInt(i -> i.stackSize).sum() == 1
            && ledger.nextStageFluids()[0].amount == 8,
            "GUI preview still describes the same paid round after partial work");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            for (Class<?> type : new Class<?>[] {
                CriticalConstructionLedger.class, CriticalConstructionProgress.class, CriticalResourceNbt.class }) {
                java.nio.file.Path origin = java.nio.file.Path.of(
                    type.getProtectionDomain().getCodeSource().getLocation().toURI());
                check(origin.toRealPath().equals(java.nio.file.Path.of(args[0]).toRealPath()),
                    "Requested product source bound: " + type.getSimpleName());
                System.out.println("SOURCE " + type.getName() + "=" + origin.toAbsolutePath());
            }
        }
        // Seed only this detached process's real registry; normal mod registration requires FML startup.
        java.lang.reflect.Method register = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 31000, "gtsr:ledger_contract_a", A);
        register.invoke(Item.itemRegistry, 31001, "gtsr:ledger_contract_b", B);
        java.lang.reflect.Method registerBlock = Block.blockRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        registerBlock.setAccessible(true);
        registerBlock.invoke(Block.blockRegistry, 9, "minecraft:water", new Block(Material.water) {});
        registerBlock.invoke(Block.blockRegistry, 11, "minecraft:lava", new Block(Material.lava) {});
        java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
        java.lang.reflect.Field loader = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
        loader.setAccessible(true);
        loader.set(null, unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        FluidRegistry.registerFluid(FLUID);
        conservation(0, 0, 17);
        conservation(1, 0, 1);
        conservation(17, 23, 399);
        conservation(99, 100, 401);
        conservation(100, 100, Integer.MAX_VALUE);
        conservation(101, 100, 1_000_003);
        conservation(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        persistenceAndRecovery();
        defensiveSnapshotAndPartialTime();
        System.out.println("PASS CriticalConstructionLedgerContract checks=" + checks);
    }
}
