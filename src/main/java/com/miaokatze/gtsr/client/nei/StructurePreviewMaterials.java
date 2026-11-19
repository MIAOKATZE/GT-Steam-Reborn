// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.nei;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.miaokatze.gtsr.common.blocks.BlockTungstenSteelGearbox;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;

import gregtech.api.GregTechAPI;

/** Static world renderers used by GTSR structures on the GTNH baseline. */
public final class StructurePreviewMaterials {

    private StructurePreviewMaterials() {}

    public static boolean isCacheable(Block block) {
        if (block instanceof CriticalMaterials.MaterialBlock material) return material.materialId.startsWith("v2_");
        if (block == Blocks.stonebrick || block == Blocks.glowstone || block == Blocks.glass
            || block == Blocks.stained_glass || block == GregTechAPI.sBlockCasings1
            || block == GregTechAPI.sBlockCasings2 || block == GregTechAPI.sBlockCasings3
            || block == GregTechAPI.sBlockCasings4 || block instanceof BlockTungstenSteelGearbox) return true;
        // Chisel's ordinary CTM cubes emit geometry from the block, metadata and adjacent cells in the tracked world.
        // Specialized renderers such as layered glow, multipart and tile-backed blocks keep the upstream path.
        return switch (block.getClass().getName()) {
            case "team.chisel.block.BlockCarvable", "team.chisel.block.BlockCarvableColor",
                "team.chisel.block.BlockConcrete" -> true;
            default -> false;
        };
    }
}
