package com.miaokatze.gtsr.mixin.gtnl;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.compat.ICoolingHatchHolder;
import com.miaokatze.gtsr.api.compat.SteamCoolingSupport;
import com.miaokatze.gtsr.config.Config;

import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

/**
 * GTNL 蒸汽机基类专用 Mixin。
 * <p>
 * GTNL 的 {@code SteamMultiMachineBase} 覆写了 {@code onRunningTick} 且不调用 super，
 * 导致 GTSR 父类 mixin（注入 {@code MTESteamMultiBlockBase.onRunningTick} HEAD）对 GTNL 机器不触发
 * （Java 虚方法分派走子类方法体）。本 mixin 直接注入 GTNL 子类的 {@code onRunningTick} HEAD，
 * 在配置开关 {@link Config#gtnlEnhancement} 开启时 cancel 原 GTNL 方法体，走 GTSR 完整增强逻辑：
 * <ul>
 * <li>过热蒸汽 4 倍消耗 4 倍速（aSteamVal*=4 + mMaxProgresstime/=4）</li>
 * <li>冷却舱室产物推送（通过 {@link ICoolingHatchHolder} 接口访问父类注入的字段）</li>
 * </ul>
 * 关闭开关时（默认）直接 return 不 cancel，GTNL 机器走原生行为，mixin 完全沉默。
 * <p>
 * 本 mixin 仅在 GTNL 加载时应用（由 {@link com.miaokatze.gtsr.mixin.GTSRMixinPlugin} 控制），
 * 是 GTSR 单方面适配，不修改 GTNL 任何代码。
 * <p>
 * 使用 {@link Pseudo} 注解：GTNL 的 {@code SteamMultiMachineBase} 不在 GTSR 编译 classpath 中
 * （GTNL 是可选依赖，不作为 compileOnly 引入以避免 beta-1/beta-2 GT5U API 冲突）。
 * {@code @Pseudo} 让 Mixin 注解处理器跳过编译期目标类存在性验证；运行时若目标类不存在，
 * mixin 被静默跳过，与 {@link com.miaokatze.gtsr.mixin.GTSRMixinPlugin} 的双重控制互补。
 *
 * @author GTSR
 */
@Pseudo
@Mixin(targets = "com.science.gtnl.common.machine.multiMachineBase.SteamMultiMachineBase", remap = false)
public abstract class SteamMultiMachineBaseGTNLMixin {

    /**
     * 注入 GTNL {@code onRunningTick} HEAD，开启配置时替换为 GTSR 完整增强逻辑。
     * <p>
     * tryConsumeSteam 虚分派到 GTNL 覆写（efficiencyFactor=10），
     * 过热蒸汽消耗 aSteamVal*4/10，实现 4 倍消耗 4 倍速。
     *
     * @param aStack 当前处理的物品栈
     * @param cir    返回值回调信息
     */
    @Inject(method = "onRunningTick(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void gtsr$onRunningTickHead(ItemStack aStack, CallbackInfoReturnable<Boolean> cir) {
        // 关闭开关：mixin 沉默，走 GTNL 原生 onRunningTick
        if (!Config.gtnlEnhancement) return;

        // 强转访问父类 public 字段与方法（lEUt/mEfficiency/mProgresstime/mMaxProgresstime/tryConsumeSteam/stopMachine）
        MTESteamMultiBlockBase self = (MTESteamMultiBlockBase) (Object) this;

        if (self.lEUt < 0) {
            // 蒸汽消耗公式：aSteamVal = (-lEUt * 10000) / max(1000, mEfficiency)
            long aSteamVal = ((-self.lEUt * 10000) / Math.max(1000, self.mEfficiency));
            boolean isSuperheated = SteamCoolingSupport.hasSuperheatedSteam(self);
            if (isSuperheated) {
                // 过热蒸汽：4 倍消耗 + 4 倍速（mMaxProgresstime / 4，仅在配方开始时 mProgresstime==0）
                aSteamVal *= 4;
                if (self.mProgresstime == 0) {
                    self.mMaxProgresstime = Math.max(1, self.mMaxProgresstime / 4);
                }
            }
            // tryConsumeSteam 虚分派到 GTNL 覆写（efficiencyFactor=10）
            if (!self.tryConsumeSteam((int) aSteamVal)) {
                self.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                cir.setReturnValue(false);
                return;
            }
            // 推送冷却产物到冷却舱室（通过接口访问父类 mixin 注入的冷却舱室列表）
            SteamCoolingSupport.pushCoolingProducts((ICoolingHatchHolder) this, (int) aSteamVal, isSuperheated);
        }
        // cancel 原 GTNL onRunningTick 方法体
        cir.setReturnValue(true);
    }
}
