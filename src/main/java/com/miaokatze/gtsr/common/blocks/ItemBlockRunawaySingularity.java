package com.miaokatze.gtsr.common.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 失控奇点方块物品（四 meta 变体，单一方块 ID 不扩注册）：
 * <ul>
 * <li>damage 0 = 失控节点[OLD]（旧存档兼容口径）：保持旧版物品行为——放置后不写 type，
 * 走 TileRunawaySingularity 字段缺省/attribute 派生（attr=-1 → STABLE），与旧版一致；</li>
 * <li>damage 1 = 自然节点（放置 → NATURAL）；damage 2 = 稳定节点（放置 → STABLE）；
 * damage 3 = 失控节点（放置 → RUNAWAY）——分型由 {@link BlockRunawaySingularity#onBlockPlacedBy}
 * 按 meta 显式落 NBT。</li>
 * </ul>
 * 四变体共用同一图标与节点参数（属性可相同），仅名称与分型词条不同。
 * 方块本体在世界中透明无贴图（原版全透明贴图），物品栏图标单独使用物品贴图，
 * 否则 ItemBlock 默认渲染方块图标会在创造物品栏中不可见。
 * 注意：ItemBlock.registerIcons 覆写不调 super，Item.itemIcon 永不填充，须自持 IIcon。
 */
public class ItemBlockRunawaySingularity extends ItemBlock {

    /** 变体 meta 常量（与 onBlockPlacedBy 分型映射共用口径） */
    public static final int META_OLD = 0;
    public static final int META_NATURAL = 1;
    public static final int META_STABLE = 2;
    public static final int META_RUNAWAY = 3;

    /** 变体数（名称/分型词条键尾段；越界 meta 钳到 OLD） */
    private static final String[] VARIANT_KEYS = { "old", "natural", "stable", "runaway" };

    private IIcon singularityIcon;

    public ItemBlockRunawaySingularity(Block block) {
        super(block);
        this.setHasSubtypes(true);
    }

    /** 变体名（item.RunawaySingularity.<variant>.name；越界 meta 钳到 0=OLD） */
    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "item.RunawaySingularity." + VARIANT_KEYS[variantIndex(stack)];
    }

    /** 变体钳位（0..3） */
    public static int variantIndex(ItemStack stack) {
        int meta = stack.getItemDamage();
        return Math.max(0, Math.min(VARIANT_KEYS.length - 1, meta));
    }

    /**
     * 分型词条不串：首行为变体专属分型说明，尾行为四变体共用通用说明。
     */
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(
            EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("gtsr.tooltip.node." + VARIANT_KEYS[variantIndex(stack)]));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.node.common"));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IIconRegister reg) {
        this.singularityIcon = reg.registerIcon("gtsr:RunawaySingularity");
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return this.singularityIcon != null ? this.singularityIcon : super.getIconFromDamage(damage);
    }
}
