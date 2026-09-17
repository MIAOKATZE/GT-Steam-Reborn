package com.miaokatze.gtsr.common.dimension.prosperity.biome;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.BiomeDictionary;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 起雾沼泽（dim78 群系 183，plan S2 / 02 §1.1 参数表；02 文档旧称"汽雾沼泽"）。
 * <p>
 * 权重 10；top=锈泥炭(meta3)/filler=锈黏(meta4)；高度 -0.08/0.16；常雨（温/雨 0.8/0.9，原版 swampland 同款）；
 * 草色 = 雾浊橄榄固定 RGB。终日雾属 Provider 视觉（后续切片），不在群系参数内。
 */
public class BiomeFumaroleSwamp extends GTSRBiomeBase {

    /** 群系权重（02 §1.1：45/30/15/10 之 10）。 */
    public static final int WEIGHT = 10;
    /** topBlock meta：锈泥炭（BlockProsperitySurface.meta3）。 */
    public static final int TOP_META = 3;
    /** fillerBlock meta：锈黏（BlockProsperitySurface.meta4）。 */
    public static final int FILLER_META = 4;
    /** 群系草色/叶色（雾浊橄榄，S7a 艺术轮可调）。 */
    public static final int GRASS_COLOR = 0x5C6B3F;

    public BiomeFumaroleSwamp(int biomeId) {
        // 常雨：温 0.8（无雪），湿 0.9
        super(biomeId, "Fumarole Swamp", new Height(-0.08F, 0.16F), 0.8F, 0.9F);
        this.topBlock = BlocksGTSR.prosperitySurface;
        this.field_150604_aj = TOP_META;
        this.fillerBlock = BlocksGTSR.prosperitySurface;
        this.theBiomeDecorator.treesPerChunk = 0;
        this.theBiomeDecorator.grassPerChunk = 0;
        this.theBiomeDecorator.flowersPerChunk = 0;
        // plan §1.2 S2：SWAMP + WET；不调 addSpawnBiome（维度专属群系）
        BiomeDictionary.registerBiomeType(this, BiomeDictionary.Type.SWAMP, BiomeDictionary.Type.WET);
    }

    /** 补齐 fillerBlock meta（原版只写 top meta，见 {@link ProsperityBiomes#applyFillerMeta}）。 */
    @Override
    public void genTerrainBlocks(World world, Random random, Block[] blocks, byte[] metadata, int x, int z,
        double stoneNoise) {
        super.genTerrainBlocks(world, random, blocks, metadata, x, z, stoneNoise);
        ProsperityBiomes.applyFillerMeta(blocks, metadata, this.fillerBlock, FILLER_META);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeGrassColor(int x, int y, int z) {
        return GRASS_COLOR;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeFoliageColor(int x, int y, int z) {
        return GRASS_COLOR;
    }
}
