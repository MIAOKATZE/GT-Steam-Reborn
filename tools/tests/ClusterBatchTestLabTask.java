package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.ICommandSender;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicAmplifierUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

/**
 * External QA helper: reuse the existing real TestLab structure/recipe scenario with titanium construction,
 * two ordinary stacks of 64 inputs, native batch mode and a finite speed-amplifier supply. Production classes
 * are never overlaid. Only the owned TestLab station is modified; no progress or output is written by this helper.
 */
public final class ClusterBatchTestLabTask implements TestLabTask {

    private final TestLabClusterTask delegate = new TestLabClusterTask(MetaTileEntityID.CLUSTER_BOOSTER_SPEED);
    private boolean steamConfigured;
    private boolean seeded;
    private boolean sawCombinedRun;
    private int combinedDuration;
    private TestLabReport.Result productionResult;
    private int completedAt;
    private Map<String, Integer> delivered;
    private long processedAfterDelivery;
    private int amplifierAfterDelivery;

    /** Invoke on the server thread through the existing owned QA helper or UiQaScript static invocation. */
    public static void begin(ICommandSender sender) throws Exception {
        if (!"Server thread".equals(Thread.currentThread().getName()))
            throw new IllegalStateException("Start this fixture on the Minecraft server thread");
        if (TestLabRuntime.INSTANCE.busy()) throw new IllegalStateException("TestLab already has an active task");
        Method begin = TestLabRuntime.class.getDeclaredMethod(
            "begin", List.class, String.class, String.class, ICommandSender.class);
        begin.setAccessible(true);
        begin.invoke(TestLabRuntime.INSTANCE, List.of(new ClusterBatchTestLabTask()),
            "cluster_batch_v215_titanium_64x2", "CLUSTER_BATCH", sender);
        // The native runtime has initialized/validated the queue; the queued task starts on a later server tick.
        String station = "cluster_booster_speed";
        TestLabFixtures.reset(TestLabDimension.world(), TestLabRuntime.originX(station), TestLabRuntime.originZ(station));
    }

    /** Keep the catalog ID so TestLabRuntime reserves the original speed-booster station. */
    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public String displayName() {
        return "GT batch titanium / 64 parallel x 2 rounds / single delivery";
    }

    @Override
    public int timeoutTicks() {
        return delegate.timeoutTicks() + 100;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) throws Exception {
        delegate.start(world, x, y, z);
        constructionTier();
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) throws Exception {
        if (productionResult != null) return observeDelivery(elapsedTicks);
        TestLabReport.Result result = delegate.tick(world, elapsedTicks);
        if ("BUILD".equals(value("phase"))) constructionTier();
        configureSteam(world);
        MTEBasicLogisticsUnit logistics = (MTEBasicLogisticsUnit) value("logistics");
        if (!seeded && "RUN".equals(value("phase"))) {
            MTESteamMineralLogisticsCluster cluster = (MTESteamMineralLogisticsCluster) value("cluster");
            if (cluster.getStructureTierIndex() != 2 || logistics.getUnitStructureTier() != 2)
                return fail("Native construction did not produce titanium tier 2");
            ItemStack input = (ItemStack) value("input");
            input.stackSize = 128;
            MTEHatchInputBus bus = (MTEHatchInputBus) value("inputBus");
            for (int slot = 0; slot < 2; slot++) {
                ItemStack stack = input.copy();
                stack.stackSize = 64;
                inventory(bus, "setInventorySlotContents|func_70299_a", slot, stack);
            }
            logistics.setBatchMode(true);
            seeded = true;
        }
        if (seeded && logistics.isWorkInProgress()) {
            if (logistics.getLastBatchParallel() != 64 || logistics.getLastBatchSize() != 128
                || logistics.getLastBatchChargedRounds() != 2)
                return fail("Expected one merged 128-input batch with width 64 and two billed rounds");
            sawCombinedRun = true;
            combinedDuration = logistics.mMaxProgresstime;
        }
        if (result == null || result.status != TestLabReport.Status.PASS) return result;
        if (!seeded || !sawCombinedRun || logistics.hasPendingOutputs())
            return fail("The real combined processing/delivery window was not observed");
        MTEBasicAmplifierUnit booster = (MTEBasicAmplifierUnit) value("booster");
        MTEHatchInput hatch = (MTEHatchInput) value("boosterHatch");
        int initial = (Integer) value("boosterInitial");
        if (initial - hatch.getFluidAmount() != booster.batchCost(2))
            return fail("The native finite amplifier hatch did not pay exactly two base amounts");
        productionResult = result;
        completedAt = elapsedTicks;
        delivered = outputs(logistics);
        processedAfterDelivery = ((MTESteamMineralLogisticsCluster) value("cluster")).getTotalProcessedOre();
        amplifierAfterDelivery = hatch.getFluidAmount();
        return null;
    }

    private TestLabReport.Result observeDelivery(int elapsedTicks) throws Exception {
        MTEBasicLogisticsUnit logistics = (MTEBasicLogisticsUnit) value("logistics");
        MTESteamMineralLogisticsCluster cluster = (MTESteamMineralLogisticsCluster) value("cluster");
        MTEHatchInput hatch = (MTEHatchInput) value("boosterHatch");
        if (logistics.hasPendingOutputs() || !delivered.equals(outputs(logistics))
            || cluster.getTotalProcessedOre() != processedAfterDelivery
            || hatch.getFluidAmount() != amplifierAfterDelivery)
            return fail("Completed batch replayed output, throughput or amplifier payment");
        if (elapsedTicks - completedAt < 40) return null;
        Map<String, Object> details = new LinkedHashMap<>(productionResult.details);
        details.put("batchMode", logistics.isBatchModeEnabled());
        details.put("structureTier", cluster.getStructureTierIndex());
        details.put("actualParallel", logistics.getLastBatchParallel());
        details.put("actualInputCount", logistics.getLastBatchSize());
        details.put("billedRounds", logistics.getLastBatchChargedRounds());
        details.put("observedRecipeTicks", combinedDuration);
        details.put("deliveredItems", delivered);
        details.put("noReplayTicks", elapsedTicks - completedAt);
        details.put("fixtureActions", "Native construction trigger count=3; two normal 64-item input stacks; native batch flag=true; tier-compatible dense supercritical steam supply");
        return new TestLabReport.Result(TestLabReport.Status.PASS,
            "GT batch merged 64x2, billed two rounds, delivered once and stayed unchanged for 40 ticks", details);
    }

    private void constructionTier() throws Exception {
        Object building = value("building");
        Field trigger = building.getClass().getDeclaredField("trigger");
        trigger.setAccessible(true);
        ((ItemStack) trigger.get(building)).stackSize = 3;
    }

    private void configureSteam(WorldServer world) throws Exception {
        IGregTechTileEntity feed = (IGregTechTileEntity) value("steamFeed");
        if (steamConfigured || feed == null) return;
        MTEHatchInput hatch = (MTEHatchInput) feed.getMetaTileEntity();
        hatch.drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE, true);
        var steam = FluidRegistry.getFluid("densesupercriticalsteam");
        if (steam == null) throw new IllegalStateException("GT dense supercritical steam is not registered");
        TestLabSupply.add(world, feed, new FluidStack(steam, 1));
        steamConfigured = true;
    }

    private Object value(String name) throws Exception {
        Field field = TestLabClusterTask.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(delegate);
    }

    private static Map<String, Integer> outputs(MTEBasicLogisticsUnit logistics) throws Exception {
        Map<String, Integer> output = new LinkedHashMap<>();
        for (MTEHatchOutputBus bus : logistics.getLogisticsOutputBusses()) {
            int slots = (Integer) inventory(bus, "getSizeInventory|func_70302_i_");
            for (int slot = 0; slot < slots; slot++) {
                ItemStack stack = (ItemStack) inventory(bus, "getStackInSlot|func_70301_a", slot);
                if (stack == null || stack.stackSize <= 0) continue;
                String key = Item.getIdFromItem(stack.getItem()) + ":" + stack.getItemDamage() + ":" + stack.getTagCompound();
                output.merge(key, stack.stackSize, Integer::sum);
            }
        }
        return output;
    }

    private static Object inventory(Object bus, String names, Object... arguments) throws Exception {
        for (String name : names.split("\\|")) {
            for (Method method : bus.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == arguments.length)
                    return method.invoke(bus, arguments);
            }
        }
        throw new NoSuchMethodException(names);
    }

    private static TestLabReport.Result fail(String detail) {
        return new TestLabReport.Result(TestLabReport.Status.FAIL, detail);
    }

    @Override
    public void stop(WorldServer world) {
        delegate.stop(world);
    }

    @Override
    public String debug(WorldServer world) {
        return "batch64x2 seeded=" + seeded + " observed=" + sawCombinedRun + "; " + delegate.debug(world);
    }
}
