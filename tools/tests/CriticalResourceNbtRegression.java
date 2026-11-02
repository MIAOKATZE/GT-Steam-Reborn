package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.init.Items;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Real controller/NBT and ledger calls, with isolated registries; never starts a game. */
public final class CriticalResourceNbtRegression {

    private static int checks;
    private static boolean red;
    private static sun.misc.Unsafe unsafe;

    public static void main(String[] args) throws Exception {
        System.setProperty("critical.nbt.red", Boolean.toString(args.length > 0 && args[0].equals("red")));
        Class.forName("Issue37RegistrationRegression")
            .getMethod("launch", String.class, String.class)
            .invoke(null, CriticalResourceNbtRegression.class.getName(), "5.09.54.265");
    }

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
    }

    private static Object member(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }

    private static NBTTagCompound item(int slot, int count) {
        NBTTagCompound tag = new NBTTagCompound();
        new ItemStack(Items.iron_ingot, 1).writeToNBT(tag);
        tag.setInteger("slot", slot);
        tag.setInteger("fullCount", count);
        return tag;
    }

    private static NBTTagCompound fluid(int slot, int count) {
        NBTTagCompound tag = new NBTTagCompound();
        new FluidStack(FluidRegistry.WATER, count).writeToNBT(tag);
        tag.setInteger("slot", slot);
        return tag;
    }

    private static NBTTagList list(NBTTagCompound... tags) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound tag : tags) list.appendTag(tag);
        return list;
    }

    private static NBTTagCompound base() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("state", 0);
        tag.setString("ownerUuid", "resource-recovery-owner");
        tag.setString("fixtureEvidence", "keep original fields");
        tag.setLong("pendingGenerated", 1234);
        tag.setTag("criticalEnergyRuntime", new NBTTagCompound());
        return tag;
    }

    private static NBTTagCompound save(CriticalMachineState state) {
        NBTTagCompound tag = new NBTTagCompound();
        state.writeToNBT(tag);
        return tag;
    }

    private static void isolated(String key, NBTBase rows) throws Exception {
        NBTTagCompound original = base();
        original.setTag(key, rows);
        CriticalMachineState state = new CriticalMachineState();
        state.readFromNBT(original);
        check((Boolean) member(state, "nativeFundingUncertain"), "must lock malformed " + key);
        check(state.getStatus().contains("完整性"), "must explain resource integrity failure " + key);
        check(!state.canRemove(), "must retain controller with quarantined assets " + key);
        check(!state.canUse(null), "must deny commands before accessing world or player " + key);
        NBTTagCompound checkpoint = save(state);
        check(checkpoint.getTag(key).equals(rows), "must retain original journal " + key);
        check(checkpoint.getCompoundTag("resourceNbtQuarantine").equals(original), "must retain complete original " + key);
        for (int i = 0; i < 4; i++) {
            CriticalMachineState restored = new CriticalMachineState();
            restored.readFromNBT(checkpoint);
            checkpoint = save(restored);
            check(checkpoint.getCompoundTag("resourceNbtQuarantine").equals(original), "must not nest quarantine " + key);
            check(checkpoint.getTag(key).equals(rows), "must retain journal on repeated save " + key);
        }
        check(original.getTag(key).equals(rows) && !original.hasKey("resourceNbtQuarantine"), "load must not mutate source " + key);
    }

    private static NBTTagCompound physicalJob() {
        NBTTagCompound job = new NBTTagCompound();
        job.setInteger("version", 2);
        job.setString("layout", "frozen-legacy-fixture");
        job.setTag("controller", list(item(0, 1)));
        job.setTag("demand", list(item(0, 17)));
        return job;
    }

    private static void invalidJournals() throws Exception {
        // MAX_VALUE overflows the old reader to size zero. The red run never requests a large array.
        isolated("uncertainFundingItems", list(item(Integer.MAX_VALUE, 1000)));
        if (red) return;
        for (String key : new String[] { "uncertainFundingItems", "inventory", "escrow", "pendingItems",
            "repairEscrow", "constructionStageCosts", "spent" }) {
            isolated(key, list(item(-1, 1000)));
            isolated(key, list(item(0, 1000), item(0, 17)));
            isolated(key, list(item(Integer.MAX_VALUE, 1000)));
            isolated(key, list(item(1_000_000_000, 1000)));
        }
        for (String key : new String[] { "uncertainFundingFluids", "tanks", "fluidEscrow", "pendingFluids",
            "constructionStageFluidCosts", "constructionPaidFluids" }) {
            isolated(key, list(fluid(-1, 1000)));
            isolated(key, list(fluid(0, 1000), fluid(0, 17)));
            isolated(key, list(fluid(Integer.MAX_VALUE, 1000)));
            isolated(key, list(fluid(1_000_000_000, 1000)));
        }
        NBTTagList many = new NBTTagList();
        for (int i = 0; i < 37; i++) many.appendTag(item(i, 1));
        isolated("inventory", many);
        many = new NBTTagList();
        for (int i = 0; i < 17; i++) many.appendTag(fluid(i, 1));
        isolated("tanks", many);
        NBTTagCompound missing = item(0, 1);
        missing.removeTag("slot");
        isolated("escrow", list(missing));
        isolated("escrow", list(item(65_536, 1)));
        isolated("pendingFluids", list(fluid(65_536, 1)));
        NBTTagList oversized = new NBTTagList();
        NBTTagCompound shared = item(0, 1);
        for (int i = 0; i <= 65_536; i++) oversized.appendTag(shared);
        try {
            CriticalResourceNbt.readItems(oversized, -1);
            throw new AssertionError("must reject oversized dynamic item list before decoding");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("entries"), "dynamic item entry budget applies before duplicate decoding");
        }
        try {
            CriticalResourceNbt.readFluids(oversized, -1);
            throw new AssertionError("must reject oversized dynamic fluid list before decoding");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("entries"), "dynamic fluid entry budget applies before duplicate decoding");
        }
        missing = item(0, 1);
        missing.setLong("slot", 0);
        isolated("escrow", list(missing));
        isolated("pendingItems", item(0, 1));
        NBTTagList wrongEntries = new NBTTagList();
        wrongEntries.appendTag(new net.minecraft.nbt.NBTTagString("invalid compound"));
        isolated("constructionStageCosts", wrongEntries);
        for (String key : new String[] { "controller", "demand" }) {
            NBTTagCompound job = physicalJob();
            job.setTag(key, list(item(Integer.MAX_VALUE, 1000)));
            NBTTagCompound original = base();
            original.setInteger("constructionEconomyVersion", 2);
            original.setTag("constructionPhysicalJob", job);
            CriticalMachineState state = new CriticalMachineState();
            state.readFromNBT(original);
            check(save(state).getCompoundTag("resourceNbtQuarantine").equals(original), "nested frozen " + key + " is locally isolated");
        }
        boolean[] decoded = { false };
        NBTTagCompound probe = new NBTTagCompound() {
            @Override public short getShort(String key) {
                if (key.equals("id")) decoded[0] = true;
                return super.getShort(key);
            }
        };
        new ItemStack(Items.iron_ingot, 1000).writeToNBT(probe);
        probe.setInteger("slot", 0);
        try {
            CriticalResourceNbt.readItems(list(probe, item(-1, 1)), -1);
            throw new AssertionError("must reject complete invalid index before item decoding");
        } catch (IllegalArgumentException expected) {
            check(!decoded[0], "late invalid item slot is detected before any item decode");
        }
    }

    private static void lockAndRecovery() throws Exception {
        NBTTagCompound original = base();
        original.setTag("inventory", list(item(0, 9)));
        original.setTag("tanks", list(fluid(0, 1000)));
        original.setTag("escrow", list(item(Integer.MAX_VALUE, 1000)));
        CriticalMachineState state = new CriticalMachineState();
        state.readFromNBT(original);
        check(state.getStackInSlot(0) == null && state.decrStackSize(0, 9) == null, "partial decoded inventory cannot be withdrawn and replayed");
        check(state.drain(ForgeDirection.DOWN, 1000, true) == null, "partial decoded fluids cannot be withdrawn and replayed");
        check(!state.canInsertItem(0, new ItemStack(Items.iron_ingot), 1)
            && !state.isItemValidForSlot(0, new ItemStack(Items.iron_ingot)), "quarantined inventory rejects new material");
        check(!state.canFill(ForgeDirection.UP, FluidRegistry.WATER)
            && state.fill(ForgeDirection.UP, new FluidStack(FluidRegistry.WATER, 1000), true) == 0,
            "quarantined tanks reject new fluid without consuming it");
        state.setInventorySlotContents(0, new ItemStack(Items.iron_ingot));
        check(state.getStackInSlot(0) == null, "direct inventory writes cannot create unsaved material in quarantine");
        state.worldObj = (World) unsafe.allocateInstance(NoAccessWorld.class);
        NBTTagCompound checkpoint = save(state);
        for (int i = 0; i < 3; i++) state.updateEntity();
        check(save(state).equals(checkpoint) && (Long) member(state, "pendingGenerated") == 1234L,
            "quarantine returns before world access, energy settlement, debit or refund work");
        NBTTagCompound repaired = (NBTTagCompound) checkpoint.getCompoundTag("resourceNbtQuarantine").copy();
        repaired.getTagList("escrow", 10).getCompoundTagAt(0).setInteger("slot", 40);
        CriticalMachineState recovered = new CriticalMachineState();
        recovered.readFromNBT(repaired);
        NBTTagCompound restored = save(recovered);
        check(!restored.hasKey("resourceNbtQuarantine") && !restored.getBoolean("nativeFundingUncertain"),
            "explicit repaired original record resumes without persistent quarantine");
        check(recovered.getStackInSlot(0).stackSize == 9 && recovered.drain(ForgeDirection.DOWN, 1000, false).amount == 1000,
            "recovery restores original live inventory and tanks exactly once");
        check(CriticalResourceNbt.readItems(restored.getTagList("escrow", 10), -1)[40].stackSize == 1000,
            "recovery preserves full escrow count at repaired sparse slot");
        com.miaokatze.gtsr.common.machine.MTECriticalMachine machine =
            new com.miaokatze.gtsr.common.machine.MTECriticalMachine("resource_nbt_diagnostic", CriticalMachineKind.SOLAR);
        Field local = machine.getClass().getDeclaredField("state");
        local.setAccessible(true);
        local.set(machine, state);
        gregtech.api.metatileentity.BaseMetaTileEntity host = new gregtech.api.metatileentity.BaseMetaTileEntity();
        Field base = gregtech.api.metatileentity.MetaTileEntity.class.getDeclaredField("mBaseMetaTileEntity");
        base.setAccessible(true);
        base.set(machine, host);
        host.setMetaTileEntity(machine);
        check(java.util.Arrays.stream(machine.getInfoData()).anyMatch(line -> line.contains("资源NBT完整性错误")),
            "existing native GT scanner diagnostics expose quarantine error without GUI access");
    }

    private static void validJournals() throws Exception {
        NBTTagCompound old = item(3, 93);
        old.removeTag("fullCount");
        old.setByte("Count", (byte) 93);
        ItemStack[] items = CriticalResourceNbt.readItems(list(item(0, 1000), old, item(35, Integer.MAX_VALUE)), -1);
        check(items.length == 36 && items[1] == null && items[3].stackSize == 93, "sparse legacy slot and Count survive");
        check(items[0].stackSize == 1000 && items[35].stackSize == Integer.MAX_VALUE, "fullCount above byte/int boundary survives");
        FluidStack[] fluids = CriticalResourceNbt.readFluids(list(fluid(2, 65_537), fluid(15, Integer.MAX_VALUE)), -1);
        check(fluids.length == 16 && fluids[0] == null && fluids[2].amount == 65_537, "sparse fluids survive");
        check(fluids[15].amount == Integer.MAX_VALUE, "full fluid amount survives");
        ItemStack[] highSparse = CriticalResourceNbt.readItems(list(item(40, 129), item(1024, 1000)), -1);
        check(highSparse.length == 1025 && highSparse[40].stackSize == 129 && highSparse[1024].stackSize == 1000,
            "legacy high sparse slots survive independently of entry count");
        check(CriticalResourceNbt.readFluids(list(fluid(1024, 1000)), -1).length == 1025,
            "legacy high sparse fluid slots survive");
        check(CriticalResourceNbt.readItems(list(item(65_535, 1)), -1).length == 65_536,
            "last permitted item slot is readable");
        check(CriticalResourceNbt.readFluids(list(fluid(65_535, 1)), -1).length == 65_536,
            "last permitted fluid slot is readable");
        NBTTagCompound original = base();
        original.setTag("escrow", CriticalResourceNbt.writeItems(items));
        original.setTag("pendingFluids", CriticalResourceNbt.writeFluids(fluids));
        CriticalMachineState state = new CriticalMachineState();
        state.readFromNBT(original);
        NBTTagCompound restored = save(state);
        check(!restored.getBoolean("nativeFundingUncertain") && !restored.hasKey("resourceNbtQuarantine"), "valid old controller remains unlocked");
        check(restored.getTag("escrow").equals(original.getTag("escrow")), "valid item journal round trip");
        check(restored.getTag("pendingFluids").equals(original.getTag("pendingFluids")), "valid fluid journal round trip");
        ItemStack[] dense = new ItemStack[512];
        for (int i = 0; i < dense.length; i++) dense[i] = new ItemStack(Items.iron_ingot, 1);
        check(CriticalResourceNbt.readItems(CriticalResourceNbt.writeItems(dense), -1).length == 512,
            "long dense cumulative legacy journals are not truncated");
    }

    private static void refunds() throws Exception {
        CriticalConstructionLedger ledger = new CriticalConstructionLedger();
        for (int count : new int[] { 27, 41, 13 }) ledger.recordLegacyItem(new ItemStack(Items.iron_ingot, count));
        ItemStack[] expected = ledger.recoveryItems(12345);
        NBTTagCompound checkpoint = new NBTTagCompound();
        ledger.write(checkpoint);
        ledger.writeSpent(checkpoint);
        CriticalMachineState state = new CriticalMachineState();
        state.readFromNBT(checkpoint);
        checkpoint = save(state);
        CriticalConstructionLedger restored = new CriticalConstructionLedger();
        restored.read(checkpoint);
        restored.readSpent(checkpoint);
        ItemStack[] actual = restored.recoveryItems(12345);
        check(actual.length == expected.length, "legacy refund entry order survives controller reload");
        for (int i = 0; i < expected.length; i++) check(ItemStack.areItemStacksEqual(expected[i], actual[i]), "legacy seeded refund conserves every entry");
        restored.clearSpentItems();
        check(restored.recoveryItems(12345).length == 0, "settled legacy refund cannot replay");
        ledger = new CriticalConstructionLedger();
        ledger.recordRoundPayment(new ItemStack[] { new ItemStack(Items.iron_ingot, Integer.MAX_VALUE) },
            new FluidStack[] { new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE) });
        ledger.completeRound();
        ledger.recordRoundPayment(new ItemStack[] { new ItemStack(Items.iron_ingot, 1) },
            new FluidStack[] { new FluidStack(FluidRegistry.WATER, 1) });
        checkpoint = new NBTTagCompound();
        ledger.write(checkpoint);
        ledger.writeSpent(checkpoint);
        state = new CriticalMachineState();
        state.readFromNBT(checkpoint);
        checkpoint = save(state);
        long paidItems = 0, paidFluids = 0;
        for (ItemStack item : CriticalResourceNbt.readItems(checkpoint.getTagList("spent", 10), -1)) paidItems += item.stackSize;
        for (FluidStack fluid : CriticalResourceNbt.readFluids(checkpoint.getTagList("constructionPaidFluids", 10), -1)) paidFluids += fluid.amount;
        check(paidItems == 1L + Integer.MAX_VALUE && paidFluids == paidItems, "split int-overflow paid journals conserve both resources");
        ledger = new CriticalConstructionLedger();
        ledger.freezeJob(CriticalConstructionEconomy.read(physicalJob()));
        ledger.recordRoundPayment(new ItemStack[] { new ItemStack(Items.iron_ingot, 1000) },
            new FluidStack[] { new FluidStack(FluidRegistry.WATER, 65_537) });
        checkpoint = new NBTTagCompound();
        ledger.write(checkpoint);
        ledger.writeSpent(checkpoint);
        state = new CriticalMachineState();
        state.readFromNBT(checkpoint);
        checkpoint = save(state);
        check(!checkpoint.hasKey("resourceNbtQuarantine"), "valid frozen physical economy remains readable");
        restored = new CriticalConstructionLedger();
        restored.read(checkpoint);
        restored.readSpent(checkpoint);
        actual = restored.recoveryItems(1);
        check(actual.length == 1 && actual[0].stackSize == 1000, "uninstalled physical escrow refunds fullCount exactly");
        restored.clearSpentItems();
        checkpoint = new NBTTagCompound();
        restored.write(checkpoint);
        restored.writeSpent(checkpoint);
        state = new CriticalMachineState();
        state.readFromNBT(checkpoint);
        restored = (CriticalConstructionLedger) member(state, "constructionLedger");
        check(restored.recoveryItems(1).length == 0, "physical refund checkpoint cannot replay after controller reload");
    }

    public static void run(String version) throws Exception {
        Class<?> runtime = Class.forName("Issue37Runtime");
        Method bootstrap = runtime.getDeclaredMethod("bootstrap", String.class);
        bootstrap.setAccessible(true);
        unsafe = (sun.misc.Unsafe) bootstrap.invoke(null, version);
        red = Boolean.getBoolean("critical.nbt.red");
        System.out.println("SOURCE codec=" + CriticalResourceNbt.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("SOURCE state=" + CriticalMachineState.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("SOURCE NBT=" + NBTTagCompound.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("SOURCE GT=" + gregtech.api.GregTechAPI.class.getProtectionDomain().getCodeSource().getLocation());
        invalidJournals();
        if (!red) {
            validJournals();
            refunds();
            lockAndRecovery();
        }
        System.out.println("RESULT resource-nbt checks=" + checks + " PASS");
    }

    private static final class NoAccessWorld extends World {
        private NoAccessWorld() { super(null, "unused", (WorldProvider) null, null, null); }
        @Override public long getTotalWorldTime() { throw new AssertionError("quarantined tick accessed world"); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
    }
}
