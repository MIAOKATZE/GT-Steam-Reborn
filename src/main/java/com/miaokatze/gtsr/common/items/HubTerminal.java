package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 枢纽终端：手持右击任意枢纽控制器（奇点钻井/蒸汽枢纽阵列/蓄水枢纽阵列），打开对应的枢纽终端状态管理界面。
 * 取代旧的「手持蒸汽纠缠奇点右击打开状态UI」交互，奇点回归纯合成材料定位。
 * 另可右击缓存节点循环传输速率（走节点 onRightclick），潜行右击缓存节点切换输入/输出模式
 * （由本类 onItemUseFirst 优先拦截处理）。
 * S4 起本类兼挂空手 Shift+右击容量档事件（见 {@link #onPlayerInteract}）。
 */
public class HubTerminal extends Item {

    public HubTerminal() {
        super();
        setUnlocalizedName("HubTerminal");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setTextureName("gtsr:HubTerminal");
        setMaxStackSize(1);
        // S4：空手 Shift+右击的容量档事件宿主（ItemLoader 双端各构造一次，事件总线双端注册）
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 潜行右击缓存节点/奇点仓：切换输入/输出模式（先于方块 onRightclick 触发；奇点仓模式锁定，
     * 服务端只发锁定提示不切换）。普通右击不拦截（返回 false），走目标自身 onRightclick 逻辑。
     * S1 类型拓宽：统一面向 IHubCacheNode（缓存节点与四仓共同接口）。
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
        node.toggleOutputModeFromTerminal(player);
        return true;
    }

    /**
     * S4 空手 Shift+右击容量档（服务端 PlayerInteractEvent，先于 Block.onBlockActivated 的 GUI 链）。
     * 拦截点依据（GT5U 5.09.54.20 核实）：BlockMachines.onBlockActivated 对空手潜行放行到
     * BaseMetaTileEntity.onRightclick，但后者六参分发被 !isSneaking() 门控——空手潜行右击在
     * mte 层无任何可达覆写点，故走本事件（与持终端 onItemUseFirst、持工具白名单互不抢占：
     * 手持任何物品时本处理器直接返回）。
     * 容量档作用于缓存节点与接收类奇点仓；发送类仓（supportsCapacityTier=false）不响应。
     */
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (event.entityPlayer == null || event.world == null) return;
        // 仅空手潜行（客户端直接返回，取消须在服务端——客户端取消会拦截 C15/C08 包导致服务端收不到）
        if (!event.entityPlayer.isSneaking()) return;
        if (event.entityPlayer.getCurrentEquippedItem() != null) return;
        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof IGregTechTileEntity gte)) return;
        if (!(gte.getMetaTileEntity() instanceof IHubCacheNode node)) return;
        if (!node.supportsCapacityTier()) return;
        if (event.world.isRemote) return;
        // 服务端取消后续方块交互链（空手潜行右击本无 GUI，此处防御其他 mod 挂钩）
        event.setCanceled(true);
        node.cycleCapacityLimitPercent();
        String msg = StatCollector.translateToLocal("gtsr.cache_node.capacity_limit") + " "
            + node.getCapacityLimitPercent()
            + "% ("
            + String.format("%,d", node.getFluidCapacityLong())
            + " "
            + StatCollector.translateToLocal("gtsr.tooltip.shared.l")
            + ")";
        GTUtility.sendChatToPlayer(event.entityPlayer, msg);
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
