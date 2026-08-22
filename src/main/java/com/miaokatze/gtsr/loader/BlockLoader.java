package com.miaokatze.gtsr.loader;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.blocks.BlockRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.blocks.ItemBlockRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
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
    }
}
