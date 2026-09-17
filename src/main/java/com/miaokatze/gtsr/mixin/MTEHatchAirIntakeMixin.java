package com.miaokatze.gtsr.mixin;

import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.dimension.prosperity.air.ProsperityAirLookup;
import com.miaokatze.gtsr.config.Config;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchAirIntake;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchFluidGenerator;

/**
 * GT5U 进气仓维度分派扩展（dim1 S3，plan S3 ③）。
 * <p>
 * 目标：{@code MTEHatchAirIntake.getFluidToGenerate()}（参考库 :42-54，唯一进气分派点，
 * 返回 1L Fluid 的 Fluid，调用点 :57-59 每次 5000L，:72-77 面朝空气判定在基类另一方法，不受影响）。
 * <p>
 * 仅当仓室所在维度 == {@link Config#prosperityDimId}（且非禁用态 -1）且群系命中繁荣四群系时
 * cancel 并返回对应材料 {@code mGas}；其余（主世界/下界/Everglades/维度内非四群系）一律放行原方法，
 * GT5U 原有三分支行为不回归。摘除 mixins.gtsr.json 本条目即整体退化（plan S3 失败回退）。
 */
@Mixin(value = MTEHatchAirIntake.class, remap = false)
public abstract class MTEHatchAirIntakeMixin extends MTEHatchFluidGenerator {

    public MTEHatchAirIntakeMixin(final String aName, final int aTier, final String[] aDescription,
        final ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Inject(method = "getFluidToGenerate", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtsr$prosperityAir(CallbackInfoReturnable<Fluid> cir) {
        final IGregTechTileEntity base = this.getBaseMetaTileEntity();
        if (base == null) {
            return;
        }
        final World world = base.getWorld();
        if (world == null) {
            return;
        }
        // 禁用态 prosperityDimId 解析为 -1（DimensionRegistrar 冲突链），此时不得劫持下界分支
        if (Config.prosperityDimId < 0 || world.provider.dimensionId != Config.prosperityDimId) {
            return;
        }
        final Materials air = ProsperityAirLookup.of(world, base.getXCoord(), base.getZCoord());
        if (air == null || air.mGas == null) {
            return;
        }
        cir.setReturnValue(air.mGas);
    }
}
