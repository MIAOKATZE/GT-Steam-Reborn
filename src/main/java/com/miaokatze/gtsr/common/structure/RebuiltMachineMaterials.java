// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.structure;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.util.CasingTier;

/** The rebuilt machines share a voltage cabinet and one steel/titanium/tungstensteel material channel. */
public final class RebuiltMachineMaterials {

    private RebuiltMachineMaterials() {}

    public static List<Pair<Block, Integer>> voltageChoices() {
        List<Pair<Block, Integer>> choices = new ArrayList<>();
        for (Pair<Block, Integer> casing : CasingTier.getCasingList())
            if (voltageTier(casing.getLeft(), casing.getRight()) != null) choices.add(casing);
        return choices;
    }

    public static Integer voltageTier(Block block, int meta) {
        Integer tier = CasingTier.getCasingBlockTier(block, meta);
        // GT's casing tiers include ULV as 1; recipe voltage tiers include ULV as 0.
        return tier != null && tier >= 2 && tier <= 13 ? tier - 1 : null;
    }

    public static List<Pair<Block, Integer>> shellChoices() {
        return ImmutableList.of(
            Pair.of(GregTechAPI.sBlockCasings2, 0),
            Pair.of(GregTechAPI.sBlockCasings4, 2),
            Pair.of(GregTechAPI.sBlockCasings4, 0));
    }

    public static Integer shellTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings4 && meta == 2) return 2;
        if (block == GregTechAPI.sBlockCasings4 && meta == 0) return 3;
        return null;
    }

    public static List<Pair<Block, Integer>> pipeChoices() {
        return ImmutableList.of(
            Pair.of(GregTechAPI.sBlockCasings2, 13),
            Pair.of(GregTechAPI.sBlockCasings2, 14),
            Pair.of(GregTechAPI.sBlockCasings2, 15));
    }

    public static Integer pipeTier(Block block, int meta) {
        return block == GregTechAPI.sBlockCasings2 && meta >= 13 && meta <= 15 ? meta - 12 : null;
    }

    public static List<Pair<Block, Integer>> frameChoices() {
        return ImmutableList.of(
            Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID),
            Pair.of(GregTechAPI.sBlockFrames, Materials.Titanium.mMetaItemSubID),
            Pair.of(GregTechAPI.sBlockFrames, Materials.TungstenSteel.mMetaItemSubID));
    }

    public static Integer frameTier(Block block, int meta) {
        if (block != GregTechAPI.sBlockFrames) return null;
        if (meta == Materials.Steel.mMetaItemSubID) return 1;
        if (meta == Materials.Titanium.mMetaItemSubID) return 2;
        if (meta == Materials.TungstenSteel.mMetaItemSubID) return 3;
        return null;
    }

    public static List<Pair<Block, Integer>> gearChoices() {
        return ImmutableList.of(
            Pair.of(GregTechAPI.sBlockCasings2, 3),
            Pair.of(GregTechAPI.sBlockCasings2, 4),
            Pair.of(BlocksGTSR.tungstenSteelGearbox, 0));
    }

    public static Integer gearTier(Block block, int meta) {
        if (block == BlocksGTSR.tungstenSteelGearbox && meta == 0) return 3;
        return block == GregTechAPI.sBlockCasings2 && meta >= 3 && meta <= 4 ? meta - 2 : null;
    }
}
