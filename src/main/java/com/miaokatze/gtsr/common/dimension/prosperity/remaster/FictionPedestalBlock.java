package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

/** One-block classical witness plinth, drawn by the shared node TESR without new textures. */
public final class FictionPedestalBlock extends RemasterBlock {

    public FictionPedestalBlock(boolean starter) {
        super(
            starter ? "gtsr:fiction_starter" : "gtsr:fiction_witness_pedestal",
            "fiction",
            false,
            false,
            "boxes",
            new double[][][] { { { .125, 0, .125, .875, 1, .875 } } },
            new String[][] {
                { "minecraft:quartz_block_side", "minecraft:quartz_block_side", "minecraft:quartz_block_side",
                    "minecraft:quartz_block_side", "minecraft:quartz_block_side", "minecraft:quartz_block_side" } });
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public int getRenderType() {
        return -1;
    }
}
