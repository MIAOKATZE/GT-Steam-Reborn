package com.miaokatze.gtsr.loader;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.VeinPyrolyzerChip;
import com.miaokatze.gtsr.register.CreativeTabManager;

public class ItemLoader {

    public static void initItems() {
        registerPyrolyzerChips();
    }

    private static void registerPyrolyzerChips() {
        GTSRItemList.VeinPyrolyzerChipT1
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChipT1", 1), "VeinPyrolyzerChipT1", true);
        CreativeTabManager.addItemToTab(GTSRItemList.VeinPyrolyzerChipT1.get(1));

        GTSRItemList.VeinPyrolyzerChipT2
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChipT2", 3), "VeinPyrolyzerChipT2", true);
        CreativeTabManager.addItemToTab(GTSRItemList.VeinPyrolyzerChipT2.get(1));

        GTSRItemList.VeinPyrolyzerChipT3
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChipT3", 7), "VeinPyrolyzerChipT3", true);
        CreativeTabManager.addItemToTab(GTSRItemList.VeinPyrolyzerChipT3.get(1));
    }
}
