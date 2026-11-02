package com.miaokatze.gtsr.common.machine.cluster;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.EnumSet;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import cpw.mods.fml.common.asm.transformers.AccessTransformer;
import com.google.common.io.CharSource;
import net.minecraft.launchwrapper.Launch;

/** Real logistics controller and GT .265 NBT calls; isolated registries, without a world/game launch. */
public final class ClusterBatchNbtRegression {

    private static int checks;

    public static void main(String[] args) throws Exception {
        Class.forName("Issue37RegistrationRegression")
            .getMethod("launch", String.class, String.class)
            .invoke(null, ClusterBatchNbtRegression.class.getName(), "5.09.54.265");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        System.out.println("PASS " + message);
    }

    private static NBTTagCompound save(MTEBasicLogisticsUnit unit) {
        NBTTagCompound nbt = new NBTTagCompound();
        unit.saveNBTData(nbt);
        return nbt;
    }

    public static void run(String version) throws Exception {
        var logSide = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
        logSide.setAccessible(true);
        logSide.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        Launch.classLoader.registerTransformer(GtNbtAccess.class.getName());
        Method bootstrap = Class.forName("Issue37Runtime").getDeclaredMethod("bootstrap", String.class);
        bootstrap.setAccessible(true);
        bootstrap.invoke(null, version);

        Logistics unit = new Logistics();
        unit.setBatchMode(true);
        unit.mMaxProgresstime = 100;
        unit.mProgresstime = 37;
        unit.setChainCooldownTicks(63);
        unit.stashPendingOutputs(List.of(new ItemStack(Items.iron_ingot, 640)));
        NBTTagCompound checkpoint = save(unit);
        check(checkpoint.hasKey("clusterPendingOutputs", 9), "paid pending output is persisted");
        check(new Logistics().supportsBatchMode() && !new Logistics().isBatchModeEnabled(),
            "batch mode is optional and initially off");
        unit.recordBatchSize(64, 640);
        checkpoint = save(unit);
        check(checkpoint.getTagList("clusterPendingOutputs", 10).getCompoundTagAt(0).getInteger("Count") == 640,
            "pending count above 127 keeps full integer precision");

        Logistics restored = new Logistics();
        restored.loadNBTData(checkpoint);
        check(restored.hasPendingOutputs(), "reload retains paid pending output");
        check(restored.mProgresstime == 37 && restored.mMaxProgresstime == 100,
            "reload retains the original GT recipe progress");
        check(restored.getChainCooldownTicks() == 63, "reload rebuilds cooldown from remaining progress");
        check(restored.isBatchModeEnabled(), "GT native batch flag survives reload");
        check(restored.getLastBatchSize() == 640 && restored.getLastBatchParallel() == 64,
            "reload retains actual input quantity and parallel width");
        check(restored.getLastBatchChargedRounds() == 10, "ten merged rounds retain their billing count");
        check(save(restored).getTagList("clusterPendingOutputs", 10).getCompoundTagAt(0).getInteger("Count") == 640,
            "repeated save preserves the paid output quantity");

        restored.recordBatchSize(64, 96);
        check(restored.getLastBatchDurationMultiplier() == 1.5 && restored.getLastBatchChargedRounds() == 2,
            "fractional GT tail has 1.5 duration but two billed rounds");
        restored.recordBatchSize(32, 32);
        check(restored.getLastBatchChargedRounds() == 1, "partial first round is billed once");
        restored.recordBatchSize(0, 0);
        check(restored.getLastBatchChargedRounds() == 0, "empty input has no billed round");

        restored.mProgresstime = 0;
        restored.mMaxProgresstime = 0;
        Logistics waiting = new Logistics();
        waiting.loadNBTData(save(restored));
        check(waiting.hasPendingOutputs() && waiting.getChainCooldownTicks() == 0,
            "completed output can wait through reload without restarting the recipe");
        waiting.abortPendingRun();
        waiting.saveNBTData(checkpoint);
        check(!checkpoint.hasKey("clusterPendingOutputs"), "cleared output removes an existing saved pending key");
        Logistics cleared = new Logistics();
        cleared.loadNBTData(checkpoint);
        check(!cleared.hasPendingOutputs() && cleared.mMaxProgresstime == 0,
            "cleared paid output cannot reappear after reload");

        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("mMaxProgresstime", 100);
        legacy.setInteger("mProgresstime", 37);
        Logistics old = new Logistics();
        old.loadNBTData(legacy);
        check(!old.hasPendingOutputs() && old.mMaxProgresstime == 0 && old.mProgresstime == 0,
            "old saves without pending outputs retain the idle compatibility rule");

        Amplifier amplifier = new Amplifier();
        check(amplifier.batchCost(0) == 0 && amplifier.batchCost(1) == 2 && amplifier.batchCost(10) == 13,
            "amplifier cost rounds once after multiplying the exact unit price");
        startupWindow();
        System.out.println("RESULT cluster-batch-nbt PASS checks=" + checks);
    }

    private static void startupWindow() {
        Cluster original = new Cluster();
        original.getPreheat().setProgress(1D);
        original.recordBatchLinks(EnumSet.of(ChainLink.MAGNETIC_SEPARATOR));
        NBTTagCompound clusterNbt = new NBTTagCompound();
        original.saveNBTData(clusterNbt);
        Cluster cluster = new Cluster();
        cluster.loadNBTData(clusterNbt);
        check(cluster.getLastBatchLinks().contains(ChainLink.MAGNETIC_SEPARATOR),
            "reload retains the powered processing link participation");

        Logistics paid = new Logistics();
        paid.mMaxProgresstime = 100;
        paid.mProgresstime = 37;
        paid.stashPendingOutputs(List.of(new ItemStack(Items.iron_ingot, 640)));
        Logistics restored = new Logistics();
        restored.loadNBTData(save(paid));
        check(cluster.getmStartUpCheck() == 100 && restored.getmStartUpCheck() == 100,
            "loaded controllers start with the native 100-tick structure-check window");
        for (int tick = 1; tick < 100; tick++) {
            cluster.onPostTick(cluster.getBaseMetaTileEntity(), tick);
            restored.onPostTick(restored.getBaseMetaTileEntity(), tick);
        }
        check(cluster.isPreheatReady() && cluster.getPreheatProgress() == 100D,
            "native startup window does not erase saved full heat");
        check(restored.hasPendingOutputs() && restored.mProgresstime == 37 && restored.mMaxProgresstime == 100,
            "native startup window retains pending output and pauses recipe progress");
        check(restored.getChainCooldownTicks() == 63,
            "native startup window cannot expire the paid recipe cooldown early");
        check(cluster.isMachineEnabled() && cluster.getTotalProcessedOre() == 0,
            "empty startup topology does not abort or replay the loaded batch");
    }

    private static IGregTechTileEntity tile(IMetaTileEntity machine) {
        return (IGregTechTileEntity) Proxy.newProxyInstance(
            ClusterBatchNbtRegression.class.getClassLoader(), new Class<?>[] { IGregTechTileEntity.class },
            (proxy, method, args) -> {
                if (method.getName().equals("getFrontFacing")) return ForgeDirection.NORTH;
                if (method.getName().equals("getMetaTileEntity")) return machine;
                if (method.getName().equals("isServerSide") || method.getName().equals("isAllowedToWork")) return true;
                if (method.getName().equals("isActive")) return false;
                if (method.getReturnType() == void.class) return null;
                throw new UnsupportedOperationException("Unexpected startup/serialization fixture call: " + method);
            });
    }

    /** Apply the real GT rule, translated from SRG to the MCP names used by patchedMc. */
    public static final class GtNbtAccess extends AccessTransformer {

        public GtNbtAccess() throws IOException {
            String config;
            try (var input = getClass().getClassLoader().getResourceAsStream("META-INF/gregtech_at.cfg")) {
                if (input == null) throw new IOException("GT access-transformer rules missing");
                config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            String rule = config.lines()
                .filter(line -> line.startsWith("public net.minecraft.nbt.NBTTagList field_74747_a"))
                .findFirst().orElseThrow(() -> new IOException("GT NBTTagList access rule missing"));
            processATFile(CharSource.wrap(rule.replace("field_74747_a", "tagList")));
        }
    }

    /** Serialization needs a native facing; no world is attached and dirty notifications are suppressed. */
    private static final class Logistics extends MTEBasicLogisticsUnit {

        Logistics() {
            super("fixture.cluster.batch.nbt");
            setBaseMetaTileEntity(tile(this));
        }

        @Override
        public void markDirty() {}
    }

    private static final class Cluster extends MTESteamMineralLogisticsCluster {

        Cluster() {
            super("fixture.cluster.batch.startup");
            setBaseMetaTileEntity(tile(this));
        }

        @Override
        public void markDirty() {}
    }

    private static final class Amplifier extends MTEUnitParallelBooster {

        Amplifier() {
            super("fixture.cluster.batch.price");
        }

        @Override
        public double amplifierFluidPerSecExact() {
            return 1.25D;
        }
    }
}
