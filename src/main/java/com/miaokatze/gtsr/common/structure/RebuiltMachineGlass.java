package com.miaokatze.gtsr.common.structure;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import org.apache.commons.lang3.tuple.Pair;

import bartworks.API.BorosilicateGlass;
import gregtech.api.GregTechAPI;
import gregtech.api.util.GlassTier;

/** Glass voltage ceiling shared by the two rebuilt twelve-tier machines. */
public final class RebuiltMachineGlass {

    private RebuiltMachineGlass() {}

    public static List<Pair<Block, Integer>> choices() {
        List<Pair<Block, Integer>> choices = new ArrayList<>();
        choices.add(Pair.of(Blocks.glass, 0));
        choices.add(Pair.of(GregTechAPI.sBlockGlass1, 10));
        for (int meta = 0; meta <= 9; meta++) choices.add(Pair.of(BorosilicateGlass.getGlassBlock(), meta));
        return choices;
    }

    public static Integer tier(Block block, int meta) {
        if (block == Blocks.glass && meta == 0) return 1;
        if (block == GregTechAPI.sBlockGlass1 && meta == 10) return 2;
        // GT265 initializes GlassTier during BartWorks init; the deprecated BorosilicateGlass reverse map is separate.
        Integer tier = GlassTier.getGlassBlockTier(block, meta);
        return tier != null && tier >= 3 && tier <= 12 ? tier : null;
    }
}
