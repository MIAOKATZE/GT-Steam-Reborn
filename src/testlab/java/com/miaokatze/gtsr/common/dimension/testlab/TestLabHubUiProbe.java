package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.gui.HubStorageGuiHelper;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.base.MTESteamStorageUnit;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.CommonMetaTileEntity;
import io.netty.buffer.Unpooled;

/** Mutates only isolated probe fixtures; checks the same production widgets and restores source/cache state. */
final class TestLabHubUiProbe {

    private TestLabHubUiProbe() {}

    static void inspect(List<IWidget> widgets, TestLabUiSnapshot.Manager manager, CommonMetaTileEntity machine,
        Map<String, Object> details, List<String> failures) throws Exception {
        boolean steam = machine instanceof MTESteamHubArray;
        if (!steam && !(machine instanceof MTEWaterHubArray)) return;
        if (steam) {
            List<Map<String, Object>> units = new ArrayList<>();
            for (MetaTileEntityID id : new MetaTileEntityID[] { MetaTileEntityID.HUB_STORAGE_UNIT,
                MetaTileEntityID.REINFORCED_HUB_STORAGE_UNIT, MetaTileEntityID.OVERPRESSURE_HUB_STORAGE_UNIT }) {
                Map<String, Object> unitRow = new LinkedHashMap<>();
                unitRow.put("registeredMetaTileId", id.ID);
                if (GregTechAPI.METATILEENTITIES[id.ID] instanceof MTESteamStorageUnit unit) {
                    inspectTextures(unit, unitRow, failures);
                } else failures.add("Registered storage unit unavailable for texture probe: " + id);
                units.add(unitRow);
            }
            details.put("registeredHubStorageTextureProbes", units);
        }
        String[] fieldNames = steam
            ? new String[] { "mSetTier", "mPressureUnitCount", "mReinforcedUnitCount", "mOverpressureUnitCount" }
            : new String[] { "mSetTier", "mHubUnitCount", "mReinforcedHubUnitCount", "mOverpressureHubUnitCount" };
        String[] syncNames = steam
            ? new String[] { "hubSetTier:0", "hubPressureUnitCount:0", "hubReinforcedUnitCount:0",
                "hubOverpressureUnitCount:0" }
            : new String[] { "hubSetTier:0", "hubHubUnitCount:0", "hubReinforcedHubUnitCount:0",
                "hubOverpressureHubUnitCount:0" };
        Field[] fields = new Field[4];
        IntSyncValue[] values = new IntSyncValue[4];
        int[] originalSource = new int[4], originalCache = new int[4];
        for (int i = 0; i < 4; i++) {
            fields[i] = machine.getClass()
                .getField(fieldNames[i]);
            originalSource[i] = fields[i].getInt(machine);
            if (!(manager.handlers.get(syncNames[i]) instanceof IntSyncValue value)) {
                failures.add("Hub storage diagnostic missing native sync handler " + syncNames[i]);
                return;
            }
            values[i] = value;
            originalCache[i] = value.getValue();
        }
        List<Map<String, Object>> samples = new ArrayList<>();
        details.put("hubStorageWarningSamples", samples);
        List<TextWidget<?>> warnings = new ArrayList<>();
        int beforeFailures = failures.size();
        try {
            setSource(machine, fields, new int[] { -1, 0, 0, 0 });
            refresh(values);
            // Identify only our named production widgets. Formatting unrelated native LangKeys
            // can load ClientScreenHandler on a dedicated server before reaching the warnings.
            for (String name : new String[] { HubStorageGuiHelper.MISSING_WARNING_NAME,
                HubStorageGuiHelper.CONSEQUENCE_WARNING_NAME }) {
                TextWidget<?> found = null;
                int count = 0;
                for (IWidget widget : widgets) {
                    if (!name.equals(widget.getName())) continue;
                    count++;
                    if (widget instanceof TextWidget<?>text) found = text;
                }
                if (count != 1 || found == null) {
                    failures.add("Expected one actual hub warning TextWidget named " + name + "; found " + count);
                    return;
                }
                warnings.add(found);
            }
            sample(
                "unknown_empty",
                new int[] { -1, 0, 0, 0 },
                "storage_missing",
                true,
                "storage_no_capacity",
                machine,
                fields,
                values,
                syncNames,
                warnings,
                samples,
                failures);
            sample(
                "bronze_empty",
                new int[] { 1, 0, 0, 0 },
                "storage_missing_normal",
                true,
                "storage_no_capacity",
                machine,
                fields,
                values,
                syncNames,
                warnings,
                samples,
                failures);
            sample(
                "bronze_present",
                new int[] { 1, 1, 0, 0 },
                "",
                false,
                "",
                machine,
                fields,
                values,
                syncNames,
                warnings,
                samples,
                failures);
            sample(
                "bronze_removed",
                new int[] { 1, 0, 0, 0 },
                "storage_missing_normal",
                true,
                "storage_no_capacity",
                machine,
                fields,
                values,
                syncNames,
                warnings,
                samples,
                failures);
            sample(
                "steel_empty",
                new int[] { 2, 0, 0, 0 },
                "storage_missing_reinforced",
                true,
                "storage_no_capacity",
                machine,
                fields,
                values,
                syncNames,
                warnings,
                samples,
                failures);
            sample(
                "steel_present",
                new int[] { 2, 0, 1, 0 },
                "",
                false,
                "",
                machine,
                fields,
                values,
                syncNames,
                warnings,
                samples,
                failures);
            sample(
                "steel_wrong_unit",
                new int[] { 2, 1, 0, 0 },
                "storage_missing_reinforced",
                true,
                "storage_cannot_form",
                machine,
                fields,
                values,
                syncNames,
                warnings,
                samples,
                failures);
            sample(
                "tungstensteel_empty",
                new int[] { 3, 0, 0, 0 },
                "storage_missing_overpressure",
                true,
                "storage_no_capacity",
                machine,
                fields,
                values,
                syncNames,
                warnings,
                samples,
                failures);
            sample(
                "tungstensteel_present",
                new int[] { 3, 0, 0, 1 },
                "",
                false,
                "",
                machine,
                fields,
                values,
                syncNames,
                warnings,
                samples,
                failures);
            sample(
                "tungstensteel_wrong_unit",
                new int[] { 3, 0, 1, 0 },
                "storage_missing_overpressure",
                true,
                "storage_cannot_form",
                machine,
                fields,
                values,
                syncNames,
                warnings,
                samples,
                failures);
        } finally {
            setSource(machine, fields, originalSource);
            for (int i = 0; i < values.length; i++) values[i].setIntValue(originalCache[i], false, false);
            for (TextWidget<?> warning : warnings) update(warning);
            boolean restored = true;
            for (int i = 0; i < fields.length; i++) {
                restored &= fields[i].getInt(machine) == originalSource[i] && values[i].getValue() == originalCache[i];
            }
            details.put("hubStorageSourceAndSyncRestored", restored);
            if (!restored) failures.add("Hub storage probe did not restore source and native sync caches");
        }
        details.put("hubStorageSameWidgetTree", true);
        details.put(
            "hubStorageWarningEvidence",
            failures.size() == beforeFailures
                ? "PASS: ten same-tree production text/visibility states with native sync cache and packet-codec checks"
                : "FAIL: hub storage production widget/sync assertions");
        details
            .put("hubStorageVisualEvidence", "NOT_RUN: client font metrics, collapse layout, GL and actual textures");
    }

    private static void sample(String name, int[] state, String missingKey, boolean visible, String consequenceKey,
        CommonMetaTileEntity machine, Field[] fields, IntSyncValue[] values, String[] syncNames,
        List<TextWidget<?>> warnings, List<Map<String, Object>> samples, List<String> failures) throws Exception {
        setSource(machine, fields, state);
        refresh(values);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("state", name);
        row.put("sameProductionWidgetInstances", true);
        List<Map<String, Object>> syncRows = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
            try {
                values[i].write(packet);
                IntSyncValue decoded = new IntSyncValue(() -> 0);
                decoded.readOnClient(0, packet);
                Map<String, Object> sync = new LinkedHashMap<>();
                sync.put("id", syncNames[i]);
                sync.put("source", fields[i].getInt(machine));
                sync.put("cache", values[i].getValue());
                sync.put("decoded", decoded.getValue());
                syncRows.add(sync);
                if (values[i].getValue() != state[i] || decoded.getValue() != state[i])
                    failures.add(name + " native storage sync/codec mismatch: " + syncNames[i]);
            } finally {
                packet.release();
            }
        }
        row.put("nativeSync", syncRows);
        List<Map<String, Object>> warningRows = new ArrayList<>();
        for (int i = 0; i < warnings.size(); i++) {
            TextWidget<?> warning = warnings.get(i);
            update(warning);
            String actual = text(warning);
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("text", actual);
            observed.put("enabled", warning.isEnabled());
            observed.put("expectedVisible", visible);
            warningRows.add(observed);
            if (warning.isEnabled() != visible) failures.add(name + " warning " + i + " visibility mismatch");
            if (visible && !actual.equals(tr(i == 0 ? missingKey : consequenceKey)))
                failures.add(name + " warning " + i + " text mismatch: " + actual);
        }
        row.put("productionWarnings", warningRows);
        samples.add(row);
    }

    private static void setSource(CommonMetaTileEntity machine, Field[] fields, int[] state)
        throws IllegalAccessException {
        for (int i = 0; i < fields.length; i++) fields[i].setInt(machine, state[i]);
    }

    private static void refresh(IntSyncValue[] values) {
        for (IntSyncValue value : values) value.updateCacheFromSource(false);
    }

    @SuppressWarnings("unchecked")
    private static void update(TextWidget<?> warning) {
        Consumer<IWidget> update = (Consumer<IWidget>) (Consumer<?>) warning.getOnUpdateListener();
        if (update != null) update.accept(warning);
    }

    private static String text(TextWidget<?> warning) {
        // Vanilla's formatting-strip helper is client-only in the dedicated-server distribution.
        return warning.getKey()
            .getFormatted()
            .replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private static String tr(String suffix) {
        return StatCollector.translateToLocal("gtsr.gui.hub." + suffix);
    }

    private static void inspectTextures(MTESteamStorageUnit unit, Map<String, Object> details, List<String> failures) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (ForgeDirection facing : ForgeDirection.VALID_DIRECTIONS) {
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                ITexture[] textures = unit.getTexture(null, side, facing, -1, false, false);
                int expected = side == facing || side == ForgeDirection.UP || side == ForgeDirection.DOWN ? 2 : 1;
                boolean valid = textures != null && textures.length == expected;
                if (textures != null) for (ITexture texture : textures) valid &= texture != null;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("facing", facing.name());
                row.put("side", side.name());
                row.put("layers", textures == null ? null : textures.length);
                row.put("valid", valid);
                samples.add(row);
                if (!valid) failures.add("Storage texture layer/null mismatch " + facing + "/" + side);
            }
        }
        details.put("hubStorageTextureSamples", samples);
        details.put(
            "hubStorageTextureEvidence",
            "PRODUCTION_GET_TEXTURE: 6 facings x 6 sides, null base tile; client GL/appearance NOT_RUN");
    }
}
