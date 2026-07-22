package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.machine.base.MTEFilteredCacheNode;
import com.miaokatze.gtsr.register.CreativeTabManager;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 枢纽终端：手持右击任意枢纽控制器（钻井/蒸汽/蓄水），打开对应的枢纽终端状态管理界面。
 * 取代旧的「手持蒸汽纠缠奇点右击打开状态UI」交互，奇点回归纯合成材料定位。
 * 另可右击缓存节点循环传输速率（走节点 onRightclick），潜行右击缓存节点切换输入/输出模式
 * （由本类 onItemUseFirst 优先拦截处理）。
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
     * 潜行右击缓存节点：切换节点输入/输出模式（先于方块 onRightclick 触发）。
     * 普通右击不拦截（返回 false），走节点自身 onRightclick 的调速逻辑。
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return false;
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity gte) || !(gte.getMetaTileEntity() instanceof MTEFilteredCacheNode node))
            return false;
        // 客户端仅消费事件，防止 vanilla 行为；实际切换在服务端执行
        if (world.isRemote) return true;
        if (!node.isBoundToHub()) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.cache_node.need_bind_first"));
            return true;
        }
        node.toggleOutputModeFromTerminal(player);
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.hub_terminal.desc"));
        list.add(
            EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.added_by")
                + " "
                + EnumChatFormatting.AQUA
                + "GT"
                + EnumChatFormatting.GREEN
                + "-"
                + EnumChatFormatting.GOLD
                + "Steam"
                + EnumChatFormatting.RED
                + "-"
                + EnumChatFormatting.BLUE
                + "Reborn");
    }
}
