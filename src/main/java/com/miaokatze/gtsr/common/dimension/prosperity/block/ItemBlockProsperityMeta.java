package com.miaokatze.gtsr.common.dimension.prosperity.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 繁荣维度 meta 族方块物品（dim1 S2）：ProsperitySurface(6) / RuinDebris(4) / RuinedCasing(3) 共用。
 * <p>
 * hasSubtypes + 按 meta 后缀取本地化名（tile.&lt;名&gt;.&lt;meta&gt;.name）；放置/物品形态 meta 钳位到
 * 各自值域；NEI/创造栏按 meta 枚举子项（创造栏堆叠由 BlockLoader 统一 addItemToTab）。
 * 单一 ItemBlock 类服务三方块，避免逐方块派生（BlockRunawaySingularity 的四变体语言条目口径在此不适用——
 * 本族按 meta 直命名，无分型词条）。
 */
public class ItemBlockProsperityMeta extends ItemBlock {

    /** 所属方块的 meta 值域。 */
    private final int variantCount;

    public ItemBlockProsperityMeta(Block block) {
        super(block);
        this.setHasSubtypes(true);
        this.variantCount = resolveVariantCount(block);
    }

    /** 按 block 实例解析 meta 值域（未知方块退化为 1，防御性）。 */
    private static int resolveVariantCount(Block block) {
        if (block instanceof BlockProsperitySurface) {
            return BlockProsperitySurface.META_COUNT;
        }
        if (block instanceof BlockRuinDebris) {
            return BlockRuinDebris.META_COUNT;
        }
        if (block instanceof BlockRuinedCasing) {
            return BlockRuinedCasing.META_COUNT;
        }
        return 1;
    }

    /** tile.<方块名>.<meta>（lang 键尾段）。 */
    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "."
            + BlockProsperitySurface.clampWithin(stack.getItemDamage(), this.variantCount);
    }

    /** 放置与物品形态 meta 钳位（越界 damage 不外溢到世界）。 */
    @Override
    public int getMetadata(int damage) {
        return BlockProsperitySurface.clampWithin(damage, this.variantCount);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubItems(Item item, CreativeTabs tab, List subItems) {
        for (int meta = 0; meta < this.variantCount; meta++) {
            subItems.add(new ItemStack(item, 1, meta));
        }
    }
}
