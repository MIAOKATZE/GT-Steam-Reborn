package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.register.CreativeTabManager;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 枢纽终端：手持右击任意枢纽控制器（奇点钻井/蒸汽枢纽阵列/蓄水枢纽阵列），打开对应的枢纽终端状态管理界面。
 * 取代旧的「手持蒸汽纠缠奇点右击打开状态UI」交互，奇点回归纯合成材料定位。
 * 服务端权威状态处理由 onItemUse 执行，潜行右击循环容量档，非潜行右击作为速率兜底
 * （正常非潜行速率由机器侧 onRightclick 处理）。
 *
 */
public class HubTerminal extends Item {

    public HubTerminal() {
        super();
        setUnlocalizedName("HubTerminal");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setTextureName("gtsr:HubTerminal");
        setMaxStackSize(1);
    }

    /** 服务端权威处理缓存节点终端状态：潜行循环容量档，非潜行作为速率兜底。 */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return false;
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity gte) || !(gte.getMetaTileEntity() instanceof IHubCacheNode node))
            return super.onItemUse(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
        if (!node.isBoundToHub()) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.cache_node.need_bind_first"));
            return true;
        }
        IMetaTileEntity meta = gte.getMetaTileEntity();
        if (player.isSneaking()) {
            if (!node.supportsCapacityTier()) {
                GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.cache_node.capacity_locked"));
                return true;
            }
            int percent = node.cycleCapacityLimitPercent();
            GTUtility.sendChatToPlayer(
                player,
                StatCollector.translateToLocal("gtsr.cache_node.capacity_limit") + " "
                    + percent
                    + "% ("
                    + String.format("%,d", node.getFluidCapacityLong())
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.shared.l")
                    + ")");
        } else {
            int percent = node.cycleTransferRatePercent();
            GTUtility.sendChatToPlayer(
                player,
                StatCollector.translateToLocal("gtsr.cache_node.transfer_rate") + " "
                    + percent
                    + "% ("
                    + String.format("%,d", node.getEffectiveHubTransferRate())
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s")
                    + ")");
        }
        if (meta != null && meta.getBaseMetaTileEntity() != null) meta.getBaseMetaTileEntity()
            .issueTileUpdate();
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.hub_terminal.desc.1"));
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.hub_terminal.desc.2"));
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.hub_terminal.desc.3"));
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.hub_terminal.desc.4"));
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.hub_terminal.desc.5"));
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.hub_terminal.desc.6"));
        list.add(GTSRUtils.getAddedByLine());
    }
}
