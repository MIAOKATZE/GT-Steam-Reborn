package com.miaokatze.gtsr.common.machine;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.miaokatze.gtsr.common.machine.base.MTEHubArrayBase;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 枢纽绑定放置拦截护栏：玩家手持缓存节点类物品（节点/奇点仓/枢纽节点）shift 右击枢纽控制器时，
 * 原版 ItemInWorldManager.activateBlockOrUseItem 判定 {@code useBlock=false}（潜行且手持非空），
 * 机器 onRightclick 收不到事件，手持物品被直接放置。本护栏在服务端 RIGHT_CLICK_BLOCK 事件上
 * 识别「潜行 + 手持可绑定物 + 目标为枢纽控制器」组合，转调控制器绑定入口
 * （tryHandleNodeBindClick：shift=整组绑定、已绑定堆叠走翻转/解绑）后取消本次事件，
 * 放置不再发生（取消后服务端回发 S23 方块修正包，客户端预测方块自愈）。
 * <p>
 * 仅服务端取消：1.7.10 客户端取消 RIGHT_CLICK_BLOCK 会阻断 C08 包发送，服务端事件将不触发。
 * <p>
 * 类与方法必须 public：事件总线为每个监听器由独立 classloader 生成 ASMEventHandler 包装类
 * （cpw.mods.fml.common.eventhandler）跨包引用监听类（v1.11.28 TerminalNet$ServerDrain
 * 可见性红线教训）。
 */
public class HubBindPlacementGuard {

    @SubscribeEvent
    public void onPlayerRightClickBlock(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (event.world.isRemote) return;
        if (!event.entityPlayer.isSneaking()) return;
        ItemStack held = event.entityPlayer.getHeldItem();
        if (held == null) return;
        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof IGregTechTileEntity gte)) return;
        IMetaTileEntity meta = gte.getMetaTileEntity();
        boolean handled;
        if (meta instanceof MTEHubArrayBase hub) {
            handled = hub.tryHandleNodeBindClick(gte, event.entityPlayer);
        } else if (meta instanceof MTESingularityDrillingHub drillingHub) {
            handled = drillingHub.tryHandleNodeBindClick(gte, event.entityPlayer);
        } else {
            return;
        }
        if (handled) {
            event.setCanceled(true);
        }
    }
}
