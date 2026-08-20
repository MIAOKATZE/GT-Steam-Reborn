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

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 枢纽终端：手持右击任意枢纽控制器（奇点钻井/蒸汽枢纽阵列/蓄水枢纽阵列），打开对应的枢纽终端状态管理界面。
 * 取代旧的「手持蒸汽纠缠奇点右击打开状态UI」交互，奇点回归纯合成材料定位。
 * 另可右击缓存节点循环传输速率（走节点 onRightclick），潜行右击缓存节点切换输入/输出模式
 * （由本类 onItemUseFirst 优先拦截处理）。
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

    /**
     * 持终端潜行右击缓存节点/接收仓：循环容量档（先于方块 onBlockActivated 触发）。
     * 输出仓不支持容量档，仅发送不可调提示；普通右击不拦截，走目标自身逻辑。
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return false;
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity gte) || !(gte.getMetaTileEntity() instanceof IHubCacheNode node))
            return false;
        // 客户端仅消费事件，防止 vanilla 行为；实际切换在服务端执行
        if (world.isRemote) return true;
        if (!node.isBoundToHub()) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.cache_node.need_bind_first"));
            return true;
        }
        if (!node.supportsCapacityTier()) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.cache_node.capacity_locked"));
            return true;
        }
        int percent = node.cycleCapacityLimitPercent();
        String msg = StatCollector.translateToLocal("gtsr.cache_node.capacity_limit") + " "
            + percent
            + "% ("
            + String.format("%,d", node.getFluidCapacityLong())
            + " "
            + StatCollector.translateToLocal("gtsr.tooltip.shared.l")
            + ")";
        GTUtility.sendChatToPlayer(player, msg);
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
