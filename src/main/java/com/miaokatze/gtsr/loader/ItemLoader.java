package com.miaokatze.gtsr.loader;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.VeinPyrolyzerChip;

public class ItemLoader {

    public static void initItems() {
        registerPyrolyzerChips();
    }

    private static void registerPyrolyzerChips() {
        GTSRItemList.VeinPyrolyzerChipT1
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChipT1", 1), "VeinPyrolyzerChipT1", true);

        GTSRItemList.VeinPyrolyzerChipT2
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChipT2", 3), "VeinPyrolyzerChipT2", true);

        GTSRItemList.VeinPyrolyzerChipT3
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChipT3", 7), "VeinPyrolyzerChipT3", true);
    }
}
