package com.miaokatze.gtsr.common.gui;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.CriticalEnergyRuntime;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

/** A bounded, read-only S2C sample. No field in this class authorizes payment or construction. */
final class CriticalBusinessSnapshot {

    private static final int VERSION = 1;
    private static final int MAX_TEXT = 4096;
    private static final int TEXT_FIELDS = 11;
    private static final int MAX_IDENTITIES = 64;
    private static final int MAX_VALUE = 256;
    final CriticalConstructionSnapshot construction;
    private final String[] text;
    final int economyVersion;
    private final String[][] identities;
    private final String[][] runtime;
    private final int[] configuration;

    private CriticalBusinessSnapshot(CriticalConstructionSnapshot construction, String[] text, int economyVersion,
        String[][] identities, String[][] runtime, int[] configuration) {
        this.construction = construction;
        this.text = text;
        this.economyVersion = economyVersion;
        this.identities = identities;
        this.runtime = runtime;
        this.configuration = configuration;
    }

    static GenericSyncValue<CriticalBusinessSnapshot, ?> register(PanelSyncManager manager, CriticalMachineState state,
        MTECriticalMachine machine) {
        final CriticalBusinessSnapshot[] last = new CriticalBusinessSnapshot[1];
        final long[] lastTick = { Long.MIN_VALUE };
        GenericSyncValue<CriticalBusinessSnapshot, ?> value = GenericSyncValue.builder(CriticalBusinessSnapshot.class)
            .getter(() -> {
                long tick = state.worldObj == null ? 0 : state.worldObj.getTotalWorldTime();
                if (last[0] == null || tick < lastTick[0] || tick - lastTick[0] >= 5) {
                    last[0] = capture(state, machine);
                    lastTick[0] = tick;
                }
                return last[0];
            })
            .serializer((packet, sample) -> sample.write(packet))
            .deserializer(CriticalBusinessSnapshot::read)
            .equals(
                (a, b) -> a.construction.same(b.construction) && Arrays.equals(a.text, b.text)
                    && a.economyVersion == b.economyVersion
                    && Arrays.deepEquals(a.identities, b.identities)
                    && Arrays.deepEquals(a.runtime, b.runtime)
                    && Arrays.equals(a.configuration, b.configuration))
            .copyImmutable()
            .build();
        manager.syncValue("critical.businessSnapshot", value);
        return value;
    }

    private static CriticalBusinessSnapshot capture(CriticalMachineState state, MTECriticalMachine machine) {
        // All getters run in one server-thread call, before any subsequent machine tick can mutate the ledger.
        String[] text = new String[TEXT_FIELDS];
        text[0] = bounded(state.getStatus());
        text[1] = itemLines(state.getConstructionNextItemCostsForGui());
        text[2] = fluidLines(state.getConstructionNextFluidCostsForGui());
        text[3] = itemLines(state.getConstructionMissingItemsForGui());
        text[4] = fluidLines(state.getConstructionMissingFluidsForGui());
        text[5] = machine == null ? ""
            : bounded(
                itemLines(machine.criticalPhysicalItemsSnapshot()) + "\n"
                    + fluidLines(machine.criticalPhysicalFluidsSnapshot()));
        text[6] = machine == null ? "" : bounded(machine.getCriticalOutputOverviewForGui());
        text[7] = Long.toString(state.getEnergyStored());
        text[8] = Long.toString(state.getEnergyCapacity());
        text[9] = Long.toString(state.getBatchEuPerTick());
        text[10] = machine == null ? ""
            : bounded(
                itemLines(machine.criticalMEItemsSnapshot()) + "\n" + fluidLines(machine.criticalMEFluidsSnapshot()));
        NBTTagCompound economy = state.getConstructionEconomySnapshot();
        NBTTagList rows = economy.getTagList("rows", 10);
        if (rows.tagCount() > MAX_IDENTITIES) throw new IllegalStateException("Too many construction identities");
        String[][] identities = new String[rows.tagCount()][9];
        String[] names = { "family", "key", "name", "demand", "installed", "escrow", "missing", "next", "paid" };
        for (int i = 0; i < identities.length; i++) for (int j = 0; j < names.length; j++) {
            String value = rows.getCompoundTagAt(i)
                .getString(names[j]);
            if (value.length() > MAX_VALUE) throw new IllegalStateException("Oversized construction identity field");
            identities[i][j] = value;
        }
        return new CriticalBusinessSnapshot(
            CriticalConstructionSnapshot.capture(state),
            text,
            economy.getInteger("version"),
            identities,
            runtime(state),
            new int[] { state.getKind()
                .ordinal(), state.getParallelPlugins(), state.getSpeedPlugins(), state.getEfficiencyPlugins(),
                state.getBatchProgress(), state.getBatchDuration(), state.getSteamMode(),
                state.isLoomStructureValid() ? 1 : 0, state.canCancelLegacyStellarBatch() ? 1 : 0 });
    }

    private void write(PacketBuffer packet) throws IOException {
        packet.writeVarIntToBuffer(VERSION);
        construction.write(packet);
        for (String value : text) packet.writeStringToBuffer(value);
        packet.writeVarIntToBuffer(economyVersion);
        packet.writeVarIntToBuffer(identities.length);
        for (String[] row : identities) for (String value : row) packet.writeStringToBuffer(value);
        packet.writeVarIntToBuffer(runtime.length);
        for (String[] row : runtime) for (String value : row) packet.writeStringToBuffer(value);
        for (int value : configuration) packet.writeInt(value);
    }

    private static CriticalBusinessSnapshot read(PacketBuffer packet) throws IOException {
        int version = packet.readVarIntFromBuffer();
        if (version != VERSION) throw new IOException("Unsupported critical business snapshot: " + version);
        CriticalConstructionSnapshot construction = CriticalConstructionSnapshot.read(packet);
        String[] text = new String[TEXT_FIELDS];
        for (int i = 0; i < text.length; i++) text[i] = packet.readStringFromBuffer(MAX_TEXT);
        int economyVersion = packet.readVarIntFromBuffer();
        int count = packet.readVarIntFromBuffer();
        if (count < 0 || count > MAX_IDENTITIES) throw new IOException("Invalid construction identity count: " + count);
        String[][] identities = new String[count][9];
        for (String[] row : identities)
            for (int j = 0; j < row.length; j++) row[j] = packet.readStringFromBuffer(MAX_VALUE);
        int runtimeCount = packet.readVarIntFromBuffer();
        if (runtimeCount < 0 || runtimeCount > 40) throw new IOException("Invalid runtime row count: " + runtimeCount);
        String[][] runtime = new String[runtimeCount][2];
        for (String[] row : runtime)
            for (int j = 0; j < row.length; j++) row[j] = packet.readStringFromBuffer(MAX_VALUE);
        int[] configuration = new int[9];
        for (int i = 0; i < configuration.length; i++) configuration[i] = packet.readInt();
        return new CriticalBusinessSnapshot(construction, text, economyVersion, identities, runtime, configuration);
    }

    String detail() {
        return text[0];
    }

    String nextItems() {
        return text[1];
    }

    String nextFluids() {
        return text[2];
    }

    String missingItems() {
        return text[3];
    }

    String missingFluids() {
        return text[4];
    }

    String inputs() {
        return text[5];
    }

    String outputs() {
        return text[6];
    }

    String energy() {
        return text[7];
    }

    String capacity() {
        return text[8];
    }

    String power() {
        return text[9];
    }

    String networkInputs() {
        return text[10];
    }

    int identityCount() {
        return identities.length;
    }

    String identity(int index, int field) {
        return identities[index][field];
    }

    int runtimeCount() {
        return runtime.length;
    }

    String runtime(int index, int field) {
        return runtime[index][field];
    }

    CriticalConfiguration configuration() {
        int a = Math.max(0, Math.min(8, configuration[1]));
        int b = Math.max(0, Math.min(8 - a, configuration[2]));
        int c = Math.max(0, Math.min(8 - a - b, configuration[3]));
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setInteger("tier", construction.tier());
        snapshot.setInteger("kind", configuration[0]);
        snapshot.setInteger("parallel", a);
        snapshot.setInteger("speed", b);
        snapshot.setInteger("economy", c);
        return CriticalConfiguration.read(snapshot);
    }

    int batchTicks() {
        return configuration[4];
    }

    int batchDuration() {
        return configuration[5];
    }

    int steamMode() {
        return configuration[6];
    }

    boolean loomValid() {
        return configuration[7] != 0;
    }

    boolean canCancelLegacyStellarBatch() {
        return configuration[8] != 0;
    }

    private static String[][] runtime(CriticalMachineState state) {
        NBTTagCompound data = (NBTTagCompound) state.energyRuntimeData()
            .copy();
        List<String[]> rows = new ArrayList<>();
        if (state.getKind() != CriticalMachineKind.SUN)
            row(rows, "sample_scope", data.getBoolean("funded") ? "active" : "last");
        if (state.getKind() == CriticalMachineKind.BATTERY) {
            row(rows, "chemical.window", Integer.toString(CriticalEnergyRuntime.getChemicalWindow(data)));
            row(rows, "chemical.batch_window", Integer.toString(data.getInteger("chemicalBatchWindow")));
            BigInteger total = number(data, "chemicalBatchHeat");
            for (int i = 0; i < 3; i++) {
                BigInteger heat = number(data, "chemicalHeat" + i);
                row(rows, "chemical.heat." + i, heat + " EU");
                row(
                    rows,
                    "chemical.share." + i,
                    total.signum() == 0 ? "—"
                        : new BigDecimal(heat).multiply(BigDecimal.valueOf(100))
                            .divide(new BigDecimal(total), 4, java.math.RoundingMode.DOWN)
                            .stripTrailingZeros()
                            .toPlainString() + "%");
                row(rows, "chemical.flow." + i, decimal(number(data, "chemicalLitres" + i), 20) + " L/s");
            }
            row(rows, "chemical.bottleneck", data.getString("chemicalBottleneck"));
            row(rows, "batch_wireless", number(data, "eu") + " EU");
            row(rows, "mean_power", decimal(number(data, "eu"), 400) + " EU/t");
            row(rows, "last_wireless", number(data, "lastGeneratedEU") + " EU");
        } else if (state.getKind() == CriticalMachineKind.TURBINE) {
            BigInteger heat = number(data, "turbineRawHeat");
            row(rows, "steam.heat", decimal(heat, 2) + " EU");
            row(rows, "steam.multiplier", data.getInteger("efficiency") + "%");
            row(rows, "steam.rise", decimal(heat.multiply(BigInteger.valueOf(11)), 20) + " EU");
            row(rows, "steam.fall", decimal(heat.multiply(BigInteger.valueOf(9)), 20) + " EU");
            row(rows, "steam.water_remainder", Integer.toString(data.getInteger("waterRemainder")));
            row(rows, "steam.return_water", number(data, "turbineWater") + " L");
            for (int i = 0; i < 6; i++) row(rows, "steam.volume." + i, number(data, "turbineVolume" + i) + " L");
            row(rows, "steam.generation_remainder", data.getInteger("generationRemainder") + " / 200 EU");
            row(rows, "batch_wireless", number(data, "eu") + " EU");
            row(rows, "mean_power", decimal(number(data, "eu"), 400) + " EU/t");
            row(rows, "last_wireless", number(data, "lastGeneratedEU") + " EU");
        } else if (state.getKind() == CriticalMachineKind.SOLAR) {
            row(rows, "solar.mode", Integer.toString(state.getSteamMode()));
            row(rows, "solar.batch_mode", Integer.toString(data.getInteger("solarBatchSteamMode")));
            row(
                rows,
                "solar.dimension",
                data.hasKey("solarFactor") ? Double.toString(data.getDouble("solarFactor")) : "—");
            row(rows, "solar.powder_cap", "256 / 400 t");
            row(rows, "solar.water", number(data, "solarWater") + " L");
            row(rows, "solar.salt", number(data, "solarSalt") + " L");
            row(rows, "solar.water_status", data.getString("solarWaterStatus"));
            row(rows, "solar.salt_status", data.getString("solarSaltStatus"));
            row(rows, "solar.moon_dust", Integer.toString(data.getInteger("solarMoonDust")));
            row(rows, "solar.stone_dust", Integer.toString(data.getInteger("solarStoneDust")));
            row(rows, "solar.night", Boolean.toString(state.holdsNightForGui()));
        } else if (state.getKind() == CriticalMachineKind.SUN) {
            NBTTagCompound stellar = state.getStellarRuntimeSnapshot();
            row(rows, "sun.phase", Integer.toString(stellar.getInteger("phase")));
            row(rows, "sun.production_paused", Boolean.toString(stellar.getBoolean("productionPaused")));
            row(
                rows,
                "sun.ignition_paid",
                Long.toString(stellar.getLong("ignitionPaid")) + " / "
                    + Long.toString(stellar.getLong("ignitionRequired"))
                    + " EU");
            row(
                rows,
                "sun.reserve",
                Long.toString(stellar.getLong("reserve")) + " / "
                    + Long.toString(stellar.getLong("reserveCapacity"))
                    + " EU");
            row(rows, "sun.hold_power", Long.toString(stellar.getLong("holdEuPerTick")) + " EU/t");
            row(rows, "sun.run_power", Long.toString(stellar.getLong("runEuPerTick")) + " EU/t");
            row(rows, "sun.lost_ticks", Integer.toString(stellar.getInteger("lostTicks")));
            row(rows, "sun.initializing", Integer.toString(stellar.getInteger("initializingTicks")));
            row(rows, "sun.refund", Long.toString(stellar.getLong("refund")) + " EU");
        }
        if (state.getKind() != CriticalMachineKind.SUN) row(
            rows,
            "settlement",
            data.getBoolean("uncertain") ? "uncertain" : data.getBoolean("funded") ? "funded" : "waiting");
        return rows.toArray(new String[0][]);
    }

    private static void row(List<String[]> rows, String key, String value) {
        if (value.length() > MAX_VALUE) throw new IllegalStateException("Oversized critical runtime value");
        rows.add(new String[] { "ui.runtime." + key, value });
    }

    private static BigInteger number(NBTTagCompound data, String key) {
        String value = data.getString(key);
        if (value.isEmpty()) return BigInteger.ZERO;
        try {
            return new BigInteger(value);
        } catch (NumberFormatException invalid) {
            return BigInteger.ZERO;
        }
    }

    private static String decimal(BigInteger number, int divisor) {
        return new BigDecimal(number).divide(BigDecimal.valueOf(divisor))
            .stripTrailingZeros()
            .toPlainString();
    }

    private static String itemLines(ItemStack[] items) {
        StringBuilder text = new StringBuilder();
        for (ItemStack stack : items) {
            if (stack == null) continue;
            if (text.length() > 0) text.append('\n');
            text.append(stack.getDisplayName())
                .append(" × ")
                .append(stack.stackSize);
            if (text.length() >= MAX_TEXT) break;
        }
        return bounded(text.toString());
    }

    private static String fluidLines(FluidStack[] fluids) {
        StringBuilder text = new StringBuilder();
        for (FluidStack stack : fluids) {
            if (stack == null) continue;
            if (text.length() > 0) text.append('\n');
            text.append(stack.getLocalizedName())
                .append(" × ")
                .append(stack.amount)
                .append(" mB");
            if (text.length() >= MAX_TEXT) break;
        }
        return bounded(text.toString());
    }

    static String bounded(String value) {
        if (value == null) return "";
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT - 1) + "…";
    }
}
