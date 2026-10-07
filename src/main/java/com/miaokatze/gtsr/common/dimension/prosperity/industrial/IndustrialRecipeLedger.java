package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;

/** Packaged integer R6 batches. No accounting multipliers or recipe-status filters are applied. */
public final class IndustrialRecipeLedger {

    public List<MaterialDefinition> materials;
    public List<Stage> recipes;

    private static final IndustrialRecipeLedger INSTANCE = read();

    public static IndustrialRecipeLedger get() {
        return INSTANCE;
    }

    private static IndustrialRecipeLedger read() {
        InputStream stream = IndustrialRecipeLedger.class
            .getResourceAsStream("/assets/gtsr/industrial/r6-recipes.json");
        if (stream == null) throw new IllegalStateException("Missing packaged prosperity R6 recipe ledger");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            IndustrialRecipeLedger ledger = new Gson().fromJson(reader, IndustrialRecipeLedger.class);
            if (ledger.materials.isEmpty() || ledger.recipes.isEmpty()) {
                throw new IllegalStateException("Prosperity industrial ledger must contain materials and stages");
            }
            return ledger;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read packaged prosperity R6 ledger", e);
        }
    }

    public static final class MaterialDefinition {

        public String id, name, en, zh, phase;
        public int temperatureK = 295;
        /** Zero identifies legacy definitions whose saved IDs must already exist during an upgrade. */
        public int introducedVersion;
    }

    public static final class Stage {

        public String id, lineId, machineMap;
        public int EUt, ticks;
        public List<Amount> inputs, outputs;
    }

    public static final class Amount {

        public String id, kind, unit;
        public int amount, meta;
        public int chance = 10000;
        public Boolean consumed;
    }
}
