package com.miaokatze.gtsr.loader;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.blocks.BlockRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.ItemBlockRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;

public class BlockLoader {

    public static Block blockRunawaySingularity;

    public static void initBlocks() {
        blockRunawaySingularity = new BlockRunawaySingularity();
        GameRegistry.registerBlock(blockRunawaySingularity, ItemBlockRunawaySingularity.class, "RunawaySingularity");
        GameRegistry.registerTileEntity(TileRunawaySingularity.class, "gtsr.runawaySingularity");
        CreativeTabManager.addItemToTabFirst(new ItemStack(blockRunawaySingularity));
    }
}
