package com.miaokatze.gtsr.common.critical;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

/** Complete-row time allocation through the real payment/progress journals; no Minecraft startup. */
public final class CriticalConstructionDebugContract {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        CriticalFoundationContract.source(CriticalConstructionDebugPolicy.class, args);
        for (int duration : new int[] {400, 400 * 32, 400 * 200, 400 * 2048, 400 * 4096}) {
            CriticalConstructionProgress progress = new CriticalConstructionProgress();
            int ticks = 0;
            while (progress.elapsed() < duration) {
                int advance = CriticalConstructionDebugPolicy.work(16, 3, duration);
                progress.advance((int) Math.min(duration, (long) progress.elapsed() + advance), duration);
                ticks++;
                if (ticks == 15) {
                    NBTTagCompound n = new NBTTagCompound(); n.setInteger("elapsed", progress.elapsed());
                    progress = new CriticalConstructionProgress(); progress.advance(n.getInteger("elapsed"), duration);
                }
                check(ticks <= 31, "Complete frozen row is bounded to 30-31 effective work ticks even at 4096 steps");
            }
            check(ticks >= 30 && ticks <= 31, "Speed3 preserves a visible full row instead of accelerating each small step");
            check(progress.elapsed() == duration, "Work never exceeds the frozen full-row duration");
        }
        check(CriticalConstructionDebugPolicy.work(16, 0, 400) == 16
            && CriticalConstructionDebugPolicy.work(16, 1, 400) == 32
            && CriticalConstructionDebugPolicy.work(16, 2, 400) == 160,
            "Normal and intermediate settings preserve their baseline and 2x/10x multipliers");
        check(CriticalConstructionDebugPolicy.work(0, 3, 400) == 0,
            "Fast mode cannot turn an unavailable power baseline into valid progress");
        check(CriticalConstructionDebugPolicy.placementBudget(100000) >= 3334
            && CriticalConstructionDebugPolicy.placementBudget(Integer.MAX_VALUE) == 8192
            && CriticalConstructionDebugPolicy.placementBudget(1) == 128,
            "Physical placement budget scales with a complete row and stays bounded");
        paidRow();
        System.out.println("PASS CriticalConstructionDebugContract checks=" + checks);
    }
    private static void paidRow() throws Exception {
        Item item = new Item();
        java.lang.reflect.Method register = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true); register.invoke(Item.itemRegistry, 31002, "gtsr:debug_contract", item);
        CriticalConstructionLedger ledger = new CriticalConstructionLedger();
        ledger.snapshotStage(new ItemStack[] {new ItemStack(item, 400, 0)}, new FluidStack[0]);
        int ticks = 0, debited = 0;
        while (ledger.elapsed() < ledger.stageDuration()) {
            int budget = CriticalConstructionDebugPolicy.work(1, 3, ledger.stageDuration());
            while (budget > 0 && ledger.elapsed() < ledger.stageDuration()) {
                CriticalConstructionLedger.Round round = ledger.planRound(budget, 1);
                if (!ledger.funded()) {
                    for (ItemStack input : round.items()) debited += input.stackSize;
                    ledger.recordRoundPayment(round.items(), round.fluids());
                }
                ledger.advanceElapsed(round.next); budget -= round.advance;
                if (round.next == round.end) ledger.completeRound();
                else break;
            }
            ticks++;
            // Serialization uses the same persisted progress that the live State consumes after reload.
            NBTTagCompound saved = new NBTTagCompound(); ledger.write(saved); ledger.writeSpent(saved);
            ledger = new CriticalConstructionLedger(); ledger.read(saved); ledger.readSpent(saved);
        }
        check(ticks >= 30 && ticks <= 31 && debited == 400,
            "Many payment boundaries share one full-row budget and NBT reload never repeats their debits");
    }
}
