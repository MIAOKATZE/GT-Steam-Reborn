package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.register.CreativeTabManager;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 枢纽终端：手持右击枢纽或拟态织机控制器，打开对应的管理界面。
 * 取代旧的「手持蒸汽纠缠奇点右击打开状态UI」交互，奇点回归纯合成材料定位。
 * 织机从道具优先入口发送独立终端请求；其他枢纽与缓存节点保留原有交互。
 *
 */
public class HubTerminal extends Item {

    public HubTerminal() {
        super();
        setUnlocalizedName("HubTerminal");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setTextureName("gtsr:HubTerminal");
        setMaxStackSize(1);
        com.miaokatze.gtsr.common.gui.CriticalMachineTerminalFactory.register();
    }

    /** 消费织机点击前明确发送请求，避免 GT 方块先打开普通机器界面。 */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!(world.getTileEntity(x, y, z) instanceof IGregTechTileEntity base)
            || !(base.getMetaTileEntity() instanceof MTECriticalMachine)) return false;
        if (world.isRemote) com.miaokatze.gtsr.common.network.WandNet
            .sendTerminalTarget(x, y, z, player.dimension, base.getMetaTileID(), player.inventory.currentItem);
        else com.miaokatze.gtsr.common.gui.CriticalMachineTerminalFactory.open(player, base);
        return true;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return false;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity base && base.getMetaTileEntity() instanceof MTECriticalMachine) {
            // The first-use route owns this controller; do not open a second terminal here.
            return true;
        }
        if (!(te instanceof IGregTechTileEntity gte) || !(gte.getMetaTileEntity() instanceof IHubCacheNode node))
            return super.onItemUse(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
        if (!node.isBoundToHub()) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.cache_node.need_bind_first"));
            return true;
        }
        openNodeEditor(player, gte);
        return true;
    }

    public static boolean openNodeEditor(EntityPlayer player, IGregTechTileEntity base) {
        if (!(player instanceof net.minecraft.entity.player.EntityPlayerMP serverPlayer)
            || !com.miaokatze.gtsr.common.terminal.CacheNodeTerminalData.canUse(player, base)) return false;
        if (com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes.cacheNodeEditor) {
            return com.miaokatze.gtsr.common.gui.mui2.hub.CacheNodeEditorFactory.open(player, base);
        }
        com.miaokatze.gtsr.common.terminal.TerminalNet.sendOpen(
            com.miaokatze.gtsr.common.terminal.TerminalUiType.CACHE_NODE,
            serverPlayer,
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            0);
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
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.hub_terminal.desc.7"));
        list.add(GTSRUtils.getAddedByLine());
    }
}
