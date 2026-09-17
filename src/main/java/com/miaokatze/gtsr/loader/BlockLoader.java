package com.miaokatze.gtsr.loader;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.blocks.BlockRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.blocks.ItemBlockRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockProsperitySurface;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinDebris;
import com.miaokatze.gtsr.common.dimension.prosperity.block.BlockRuinedCasing;
import com.miaokatze.gtsr.common.dimension.prosperity.block.ItemBlockProsperityMeta;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockRiftStone;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredBlackstone;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredDirt;
import com.miaokatze.gtsr.common.dimension.shattered.block.BlockShatteredGrass;
import com.miaokatze.gtsr.main.GTSteamReborn;
import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;

public class BlockLoader {

    public static void initBlocks() {
        // 方块实例统一写入 blocks 包自持持有者（SR-O2-B05），machine/blocks 侧只读，不再反向引用 loader
        BlocksGTSR.runawaySingularity = new BlockRunawaySingularity();
        GameRegistry
            .registerBlock(BlocksGTSR.runawaySingularity, ItemBlockRunawaySingularity.class, "RunawaySingularity");
        GameRegistry.registerTileEntity(TileRunawaySingularity.class, "gtsr.runawaySingularity");
        CreativeTabManager.addItemToTabFirst(new ItemStack(BlocksGTSR.runawaySingularity));
        // 节点三新变体（1=自然/2=稳定/3=失控；0=OLD 保持首位，见 ItemBlockRunawaySingularity）
        CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.runawaySingularity, 1, 1));
        CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.runawaySingularity, 1, 2));
        CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.runawaySingularity, 1, 3));

        // dim1 S2：繁荣维度 meta 族方块（runawaySingularity 自持持有者范式）。
        // 必须先于 ProsperityBiomes.init（群系 top/filler 引用 BlocksGTSR.prosperitySurface，
        // CommonProxy.preInit 顺序：BlockLoader → ProsperityBiomes → DimensionRegistrar）。
        BlocksGTSR.prosperitySurface = new BlockProsperitySurface();
        GameRegistry.registerBlock(BlocksGTSR.prosperitySurface, ItemBlockProsperityMeta.class, "ProsperitySurface");
        BlocksGTSR.ruinDebris = new BlockRuinDebris();
        GameRegistry.registerBlock(BlocksGTSR.ruinDebris, ItemBlockProsperityMeta.class, "RuinDebris");
        BlocksGTSR.ruinedCasing = new BlockRuinedCasing();
        GameRegistry.registerBlock(BlocksGTSR.ruinedCasing, ItemBlockProsperityMeta.class, "RuinedCasing");
        for (int meta = 0; meta < BlockProsperitySurface.META_COUNT; meta++) {
            CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.prosperitySurface, 1, meta));
        }
        for (int meta = 0; meta < BlockRuinDebris.META_COUNT; meta++) {
            CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.ruinDebris, 1, meta));
        }
        for (int meta = 0; meta < BlockRuinedCasing.META_COUNT; meta++) {
            CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.ruinedCasing, 1, meta));
        }
        GTSteamReborn.LOG
            .info("[GTSR] prosperity blocks registered: ProsperitySurface(6)/RuinDebris(4)/RuinedCasing(3)");

        // dim1 S6a：破碎之地方块族（单方块无 meta，runawaySingularity/prosperity 自持持有者范式）。
        // 必须先于 ShatteredBiomes.init（群系 top/filler 引用 BlocksGTSR.shattered*，
        // CommonProxy.preInit 顺序：BlockLoader → ShatteredBiomes.init → DimensionRegistrar）。
        BlocksGTSR.shatteredGrass = new BlockShatteredGrass();
        GameRegistry.registerBlock(BlocksGTSR.shatteredGrass, "ShatteredGrass");
        BlocksGTSR.shatteredDirt = new BlockShatteredDirt();
        GameRegistry.registerBlock(BlocksGTSR.shatteredDirt, "ShatteredDirt");
        BlocksGTSR.riftStone = new BlockRiftStone();
        GameRegistry.registerBlock(BlocksGTSR.riftStone, "RiftStone");
        BlocksGTSR.shatteredBlackstone = new BlockShatteredBlackstone();
        GameRegistry.registerBlock(BlocksGTSR.shatteredBlackstone, "ShatteredBlackstone");
        CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.shatteredGrass));
        CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.shatteredDirt));
        CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.riftStone));
        CreativeTabManager.addItemToTab(new ItemStack(BlocksGTSR.shatteredBlackstone));
        GTSteamReborn.LOG
            .info("[GTSR] shattered blocks registered: ShatteredGrass/ShatteredDirt/RiftStone/ShatteredBlackstone");
    }
}
