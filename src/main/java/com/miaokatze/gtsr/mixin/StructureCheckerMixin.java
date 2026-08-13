package com.miaokatze.gtsr.mixin;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 结构校验放行 mixin：任意多方块机器的结构校验访问到红石仓所在位置时，放行该位置（返回 true，
 * 红石仓可装进任何机器），并把控制器元实体注册给红石仓（setController），供其读取机器词条/
 * GT 标准键并输出红石信号。
 */
@Mixin(value = gregtech.api.structure.StructureChecker.class, remap = false)
public abstract class StructureCheckerMixin {

    /** 控制器元实体（StructureChecker<T>.instance，泛型擦除后为 Object，实际为 IMetaTileEntity） */
    @Shadow
    public Object instance;

    @Inject(method = "visit", at = @At("HEAD"), cancellable = true)
    private void gtsr$onVisit(IStructureElement element, World world, int x, int y, int z, int a, int b, int c,
        CallbackInfoReturnable<Boolean> cir) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity) {
            IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            if (mte instanceof MTEGTSRRedstoneHatch) {
                ((MTEGTSRRedstoneHatch) mte).setController((IMetaTileEntity) instance);
                cir.setReturnValue(true);
            }
        }
    }
}
