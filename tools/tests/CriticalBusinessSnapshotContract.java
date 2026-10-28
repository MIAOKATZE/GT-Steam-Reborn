package com.miaokatze.gtsr.common.gui;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import net.minecraft.network.PacketBuffer;

import io.netty.buffer.Unpooled;

/** Real packet codec contract; no world, rendering or ledger mutation is needed. */
public final class CriticalBusinessSnapshotContract {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        energyProjection();
        Constructor<CriticalConstructionSnapshot> mapConstructor = CriticalConstructionSnapshot.class
            .getDeclaredConstructor(int[].class);
        mapConstructor.setAccessible(true);
        int[] map = new int[26];
        map[9] = 3;
        map[10] = 17;
        map[11] = 23;
        map[12] = 3;
        map[17] = 1;
        map[22] = 6;
        map[23] = -123;
        map[24] = 456;
        map[25] = 7;
        CriticalConstructionSnapshot construction = mapConstructor.newInstance((Object) map);
        int[] newCell = map.clone();
        newCell[0] = 1; newCell[1] = 1; newCell[2] = 16;
        newCell[3] = 10; newCell[4] = 40; newCell[12] = 1; newCell[15] = 1;
        newCell[22] = 7; newCell[25] = 1;
        CriticalConstructionSnapshot newCellSample = mapConstructor.newInstance((Object) newCell);
        check(newCellSample.displayCellTicks() == 10 && newCellSample.cellDuration() == 40,
            "Version7 displays the current real cell work without old whole-chunk subdivision");
        check(Math.abs(newCellSample.progress() - 1.25D / 16) < 1e-12,
            "Version7 total progress includes one completed cell and ten of forty current work");
        PacketBuffer newCellPacket = new PacketBuffer(Unpooled.buffer());
        newCellSample.write(newCellPacket);
        CriticalConstructionSnapshot newCellDecoded = CriticalConstructionSnapshot.read(newCellPacket);
        check(newCellDecoded.same(newCellSample) && newCellDecoded.foundationScheduleVersion() == 7
            && newCellDecoded.displayCellTicks() == 10 && newCellPacket.readableBytes() == 0,
            "New cell clocks and completed-cell map survive one bounded S2C packet");
        String exact = "12345678901234567890123456789012345678901234567890";
        String[] text = { "blocked", "next item", "next fluid", "missing item", "missing fluid", "physical",
            "output", exact, exact, exact, "visible unpaid" };
        String[][] identities = { { "S", "S1", "exact identity", exact, "17", "3", exact, "5", "20" } };
        Method project = CriticalBusinessSnapshot.class.getDeclaredMethod("chemicalRows", List.class, NBTTagCompound.class);
        project.setAccessible(true);
        NBTTagCompound chemical = new NBTTagCompound();
        chemical.setBoolean("funded", true);
        chemical.setInteger("chemicalBatchRule", 2);
        chemical.setString("chemicalRecipe", "blend.7.3.3");
        chemical.setInteger("chemicalEfficiencyHundredths", 1234);
        chemical.setString("chemicalBatchHeat", exact);
        chemical.setString("chemicalBatchLitres", exact);
        List<String[]> projected = new ArrayList<>();
        project.invoke(null, projected, chemical);
        String[][] runtime = projected.toArray(new String[0][]);
        check(value(runtime, "chemical.state").equals("current"), "new paid input is current");
        check(value(runtime, "chemical.efficiency").equals("12.34x"), "hundredths preserve the real heat multiplier");
        check(value(runtime, "chemical.heat").equals(exact + " EU"), "large base heat stays exact");
        check(value(runtime, "chemical.litres").equals(exact + " L"), "large total volume stays exact");
        check(Arrays.stream(runtime).noneMatch(row -> row[0].contains("window") || row[0].contains("share.")),
            "compound snapshots must not expose removed selectors or spectra");
        chemical.setInteger("chemicalEfficiencyHundredths", 100000);
        projected.clear();
        project.invoke(null, projected, chemical);
        check(value(projected.toArray(new String[0][]), "chemical.efficiency").equals("100x"), "efficiency display is bounded");
        chemical.removeTag("chemicalBatchRule");
        chemical.removeTag("chemicalRecipe");
        chemical.removeTag("chemicalEfficiencyHundredths");
        chemical.removeTag("chemicalBatchLitres");
        projected.clear();
        project.invoke(null, projected, chemical);
        check(value(projected.toArray(new String[0][]), "chemical.state").equals("legacy"), "old paid assets have a plain recovery state");
        check(value(projected.toArray(new String[0][]), "chemical.efficiency").equals("\u2014")
            && value(projected.toArray(new String[0][]), "chemical.flow").equals("\u2014"),
            "old assets must not invent a multiplier or an input volume");
        chemical.setBoolean("funded", false);
        projected.clear();
        project.invoke(null, projected, chemical);
        check(value(projected.toArray(new String[0][]), "chemical.state").equals("awaiting"), "no current recipe waits for a matching feed");
        int[] configuration = { 6, 2, 3, 1, 40, 400, 5, 1, 1, 0, 0, 0 };
        CriticalMaterialSnapshot[] materials = new CriticalMaterialSnapshot[7];
        Arrays.fill(materials, CriticalMaterialSnapshot.capture(new ItemStack[0], new FluidStack[0]));
        Constructor<CriticalBusinessSnapshot> constructor = CriticalBusinessSnapshot.class.getDeclaredConstructor(
            CriticalConstructionSnapshot.class, String[].class, int.class, String[][].class, String[][].class, int[].class,
            CriticalMaterialSnapshot[].class);
        constructor.setAccessible(true);
        CriticalBusinessSnapshot sample = constructor.newInstance(construction, text, 2, identities, runtime, configuration, materials);
        Method write = CriticalBusinessSnapshot.class.getDeclaredMethod("write", PacketBuffer.class);
        Method read = CriticalBusinessSnapshot.class.getDeclaredMethod("read", PacketBuffer.class);
        write.setAccessible(true);
        read.setAccessible(true);
        CriticalBusinessSnapshot bootstrap = CriticalBusinessSnapshot.empty();
        check(bootstrap.identityCount() == 0 && bootstrap.runtimeCount() == 0
            && bootstrap.construction.chunkCount() == 0, "client bootstrap must not require a target world or tile");
        PacketBuffer bootstrapPacket = new PacketBuffer(Unpooled.buffer());
        write.invoke(bootstrap, bootstrapPacket);
        CriticalBusinessSnapshot bootstrapDecoded = (CriticalBusinessSnapshot) read.invoke(null, bootstrapPacket);
        check(bootstrapPacket.readableBytes() == 0 && bootstrapDecoded.energy().equals("0")
            && !bootstrapDecoded.canCancelLegacyStellarBatch(), "bootstrap must use the normal bounded S2C codec");
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        write.invoke(sample, packet);
        CriticalBusinessSnapshot decoded = (CriticalBusinessSnapshot) read.invoke(null, packet);
        check(packet.readableBytes() == 0, "one complete packet must decode without trailing fields");
        check(decoded.construction.same(construction), "map and dismantle cursor must survive the same sample");
        check(decoded.construction.foundationScheduleVersion() == 6 && decoded.construction.chunkX(0) == -123
            && decoded.construction.chunkZ(0) == 456 && decoded.construction.chunkCells(0) == 7,
            "whole-chunk schedule and visual subcell coordinates survive together");
        check(decoded.identity(0, 3).equals(exact), "large job quantities must remain exact decimal strings");
        check(decoded.energy().equals(exact), "large telemetry values must not pass through floating point");
        check(value(decoded, "chemical.heat").equals(exact + " EU"), "runtime amounts must remain exact after S2C decode");
        check(value(decoded, "chemical.recipe").equals("blend.7.3.3")
            && value(decoded, "chemical.efficiency").equals("12.34x"), "recipe and multiplier survive in the same sample");
        check(value(decoded, "chemical.flow").equals(value(runtime, "chemical.flow")), "flow and volume share the same packet");
        check(decoded.inputs().isEmpty() && decoded.networkInputs().isEmpty(),
            "material displays must use the bounded material descriptors");
        check(decoded.batchTicks() == 40 && decoded.batchDuration() == 400 && decoded.steamMode() == 5,
            "batch cursor and mode must be decoded from the same packet");
        check(decoded.configuration().parallel == 2 && decoded.configuration().speed == 3,
            "configuration must be from the same packet");
        check(decoded.configuration() == decoded.configuration(), "immutable display configuration is reused per sample");
        check(decoded.canCancelLegacyStellarBatch(), "legacy recovery action must belong to the same sample");
        PacketBuffer wrongVersion = new PacketBuffer(Unpooled.buffer());
        wrongVersion.writeVarIntToBuffer(99);
        rejects(read, wrongVersion, "unknown packet version must fail closed");
        PacketBuffer tooManyChunks = new PacketBuffer(Unpooled.buffer());
        tooManyChunks.writeVarIntToBuffer(2);
        tooManyChunks.writeVarIntToBuffer(4097);
        rejects(read, tooManyChunks, "chunk count must be bounded before allocation");
        PacketBuffer tooManyIdentities = new PacketBuffer(Unpooled.buffer());
        tooManyIdentities.writeVarIntToBuffer(2);
        construction.write(tooManyIdentities);
        for (String value : text) tooManyIdentities.writeStringToBuffer(value);
        tooManyIdentities.writeVarIntToBuffer(2);
        tooManyIdentities.writeVarIntToBuffer(65);
        rejects(read, tooManyIdentities, "identity count must be bounded before allocation");
        System.out.println("PASS CriticalBusinessSnapshotContract checks=" + checks);
    }

    private static void energyProjection() throws Exception {
        com.miaokatze.gtsr.common.critical.CriticalMachineState state =
            new com.miaokatze.gtsr.common.critical.CriticalMachineState();
        java.lang.reflect.Field config = state.getClass().getDeclaredField("config");
        config.setAccessible(true);
        config.set(state, new com.miaokatze.gtsr.common.critical.CriticalConfiguration(
            com.miaokatze.gtsr.common.critical.CriticalTier.T3,
            com.miaokatze.gtsr.common.critical.CriticalMachineKind.SOLAR, 0, 0, 0));
        NBTTagCompound data = state.energyRuntimeData();
        data.setBoolean("solarSaltMode", true);
        data.setBoolean("solarBatchSaltMode", false);
        data.setString("solarCoolant", "cryotheum");
        data.setString("solarExciter", "helium");
        java.math.BigInteger max = java.math.BigInteger.valueOf(
            com.miaokatze.gtsr.common.critical.CriticalSolarPolicy.MAX_VOLTAGE);
        data.setString("solarEquivalentEU", max.multiply(java.math.BigInteger.valueOf(864L * 400L))
            .add(java.math.BigInteger.ONE).toString());
        data.setString("solarRatedEUt", "999999999999999");
        data.setDouble("solarLoad", 0.75D);
        Method projection = CriticalBusinessSnapshot.class.getDeclaredMethod("runtime", state.getClass());
        projection.setAccessible(true);
        String[][] rows = (String[][]) projection.invoke(null, state);
        check(value(rows, "solar.route").equals("1") && value(rows, "solar.batch_route").equals("0"),
            "next route and prepaid route remain distinct in one sample");
        check(value(rows, "solar.coolant").equals("cryotheum") && value(rows, "solar.exciter").equals("helium"),
            "paid coolant and exciter identities are sampled together");
        check(value(rows, "solar.equivalent_power").equals("92771293248 EU/t"),
            "native heat power excludes downstream turbine efficiency and uses the actual batch");
        check(value(rows, "solar.load").equals("75.0%"), "partial load is explicit");
        data.setString("solarEquivalentEU", max.multiply(java.math.BigInteger.valueOf(400))
            .divide(java.math.BigInteger.valueOf(3)).toString());
        rows = (String[][]) projection.invoke(null, state);
        check(value(rows, "solar.equivalent_power").equals("35791394 EU/t"),
            "partial native heat rounds without crashing the UI sampler");
        check(!data.getBoolean("funded") && state.getSolarSaltMode(), "projection cannot mutate payment or selection");
        data.setBoolean("solarPlasmaMode", true);
        data.setBoolean("solarBatchPlasmaMode", true);
        data.setString("solarPlasma", "Tin");
        data.setString("solarPlasmaLitres", "150008889");
        rows = (String[][]) projection.invoke(null, state);
        check(state.getSolarRoute() == 2 && value(rows, "solar.route").equals("2")
            && value(rows, "solar.batch_route").equals("2") && value(rows, "solar.plasma").equals("Tin")
            && value(rows, "solar.plasma_litres").equals("150008889 L"),
            "third route samples paid plasma identity and whole batch litres without changing legacy salt bits");
        config.set(state, new com.miaokatze.gtsr.common.critical.CriticalConfiguration(
            com.miaokatze.gtsr.common.critical.CriticalTier.T3,
            com.miaokatze.gtsr.common.critical.CriticalMachineKind.TURBINE, 0, 0, 0));
        data.setInteger("turbineRunTicks", 123);
        data.setInteger("turbineTargetWarmupTicks", 200);
        rows = (String[][]) projection.invoke(null, state);
        check(value(rows, "steam.warmup").equals("123 / 200 t"), "equivalent-heat target replaces old trend thresholds");
        check(Arrays.stream(rows).noneMatch(row -> row[0].equals("ui.runtime.steam.rise")
            || row[0].equals("ui.runtime.steam.fall")), "removed rise/fall rewards cannot leak into the terminal");
        check(rows.length <= 40, "runtime sample remains within the native packet row bound");
    }

    private static String value(String[][] rows, String suffix) {
        for (String[] row : rows) if (row[0].equals("ui.runtime." + suffix)) return row[1];
        return "";
    }

    private static String value(CriticalBusinessSnapshot sample, String suffix) {
        for (int i = 0; i < sample.runtimeCount(); i++)
            if (sample.runtime(i, 0).equals("ui.runtime." + suffix)) return sample.runtime(i, 1);
        return "";
    }

    private static void rejects(Method read, PacketBuffer packet, String message) throws Exception {
        try {
            read.invoke(null, packet);
            throw new AssertionError(message);
        } catch (InvocationTargetException rejected) {
            check(rejected.getCause() instanceof IOException, message);
        }
    }
}
