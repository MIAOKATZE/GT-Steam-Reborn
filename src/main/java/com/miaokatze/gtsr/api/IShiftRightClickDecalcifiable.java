package com.miaokatze.gtsr.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 允许玩家空手潜行右击控制器方块时执行自定义逻辑（如清除结垢/钙化）。
 * <p>
 * 通过 {@link com.miaokatze.gtsr.mixin.BaseMetaTileEntityMixin} 在
 * {@code BaseMetaTileEntity.onRightclick} 的头部拦截并分发。
 */
public interface IShiftRightClickDecalcifiable {

    /**
     * 当玩家空手潜行右击该 MTE 所属控制器时调用。
     *
     * @param aPlayer 玩家
     * @param side    被点击的面
     * @param aX      点击局部坐标 X
     * @param aY      点击局部坐标 Y
     * @param aZ      点击局部坐标 Z
     * @return 若返回 {@code true}，表示事件已处理，将阻止 GT 默认逻辑继续执行
     */
    boolean onShiftRightClick(EntityPlayer aPlayer, ForgeDirection side, float aX, float aY, float aZ);
}
