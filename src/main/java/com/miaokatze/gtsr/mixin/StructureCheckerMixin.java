package com.miaokatze.gtsr.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace;
import com.miaokatze.gtsr.common.api.compat.IGTSRHatchCasingProvider;
import com.miaokatze.gtsr.common.api.compat.IGTSRRedstoneHatchCheck;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch;
import com.miaokatze.gtsr.common.structure.GTSRRedstoneHatchCheck;
import com.miaokatze.gtsr.common.structure.GTSRRedstoneHatchLimitError;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.PositionedStructureError;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;

/**
 * 结构校验放行 mixin：任意多方块机器的结构校验访问到红石仓所在位置时，放行该位置（返回 true，
 * 红石仓可装进任何机器），并把控制器元实体注册给红石仓（setController），供其读取机器词条/
 * GT 标准键并输出红石信号。同一轮结构校验最多放行 4 个红石仓：第 5 个起该位置校验失败
 * （visit 返回 false 同时终止遍历，结构不成立），并向结构错误列表写入超限位置与提示文案。
 */
@Mixin(value = gregtech.api.structure.StructureChecker.class, remap = false)
public abstract class StructureCheckerMixin {

    /** 控制器元实体（StructureChecker<T>.instance，泛型擦除后为 Object，实际为 IMetaTileEntity） */
    @Shadow
    public Object instance;

    /** 结构错误列表（StructureChecker.errors，final；无 GUI 通道时为 null，只校验不提示） */
    @Shadow
    @Final
    public List<StructureError> errors;

    /** 本轮校验是否失败（StructureChecker.success；checkStructureImpl 以它为最终结果） */
    @Shadow
    public boolean success;

    /** 直接使用独立 checker 时的兼容回退；普通控制器校验使用完整事务计数。 */
    @Unique
    private int gtsr$redstoneHatchCount;

    /** 独立 checker 的兼容回退绑定集合；控制器完整校验由事务统一提交/回滚。 */
    @Unique
    private final List<MTEGTSRRedstoneHatch> gtsr$boundRedstoneHatches = new ArrayList<>();

    @Inject(method = "visit", at = @At("HEAD"), cancellable = true)
    private void gtsr$onVisit(IStructureElement element, World world, int x, int y, int z, int a, int b, int c,
        CallbackInfoReturnable<Boolean> cir) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity) {
            IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            if (mte instanceof MTEGTSRRedstoneHatch) {
                MTEGTSRRedstoneHatch hatch = (MTEGTSRRedstoneHatch) mte;
                GTSRRedstoneHatchCheck check = instance instanceof IGTSRRedstoneHatchCheck
                    ? ((IGTSRRedstoneHatchCheck) instance).getGTSRRedstoneHatchCheck()
                    : null;
                boolean transactional = check != null && check.isChecking();
                int count = transactional ? check.visit(hatch) : ++gtsr$redstoneHatchCount;
                if (count > GTSRRedstoneHatchCheck.LIMIT) {
                    // 超出上限：本位置校验失败。原方法体被跳过，需手动置失败标志，
                    // 并补写结构错误（超限位置 + 世界高亮、超限文案）供控制器 GUI 呈现。
                    this.success = false;
                    gtsr$invalidateBoundHatches();
                    if (errors != null) {
                        errors.add(new PositionedStructureError(x, y, z));
                        errors.add(new GTSRRedstoneHatchLimitError(count));
                    }
                    cir.setReturnValue(false);
                    return;
                }
                if (!transactional) {
                    hatch.setController((IMetaTileEntity) instance);
                    gtsr$boundRedstoneHatches.add(hatch);
                }
                // 解析结果显式返回；解析失败不否决结构，确保红石仓仍可替换任意结构位。
                applyStructureTexture(element, world, x, y, z, hatch);
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * 结构施加底材：红石仓替换机器结构的一格外壳位，其位置的结构期望方块（通常为机器外壳）即该
     * 机器对仓室施加的底材来源——与 GT5U 输入仓由结构 adder 施加 casingIndex 的机制同源。经
     * IStructureElement.getBlocksToPlace 提取自动放置候选，再求 GTUtility.getCasingTextureIndex 并推送；
     * 固定方块元素可直接解析，多候选、分级或 adder 元素可能不提供唯一 casing，解析失败时保持当前底材。
     */
    @Unique
    private boolean applyStructureTexture(IStructureElement element, World world, int x, int y, int z,
        MTEGTSRRedstoneHatch hatch) {
        if (instance instanceof IGTSRHatchCasingProvider) {
            int casingIndex = ((IGTSRHatchCasingProvider) instance).getGTSRHatchCasingTextureIndex();
            if (casingIndex != Textures.BlockIcons.ERROR_TEXTURE_INDEX && casingIndex > 0) {
                hatch.applyStructureTexture(casingIndex);
                return true;
            }
        }
        try {
            BlocksToPlace blocks = element
                .getBlocksToPlace(instance, world, x, y, z, null, AutoPlaceEnvironment.fromLegacy(null, null, null));
            if (blocks == null || blocks.getStacks() == null) return false;
            for (ItemStack stack : blocks.getStacks()) {
                if (stack == null || stack.getItem() == null) continue;
                int idx = GTUtility
                    .getCasingTextureIndex(Block.getBlockFromItem(stack.getItem()), stack.getItemDamage());
                if (idx != Textures.BlockIcons.ERROR_TEXTURE_INDEX) {
                    hatch.applyStructureTexture(idx);
                    return true;
                }
            }
        } catch (Exception ignored) {
            // 结构元素不支持提取期望方块（如 hatch adder 匿名元素）：由返回值显式报告失败。
        }
        return false;
    }

    /** 独立 checker 超限时清理回退绑定；完整校验的失败由控制器事务收尾。 */
    @Unique
    private void gtsr$invalidateBoundHatches() {
        for (MTEGTSRRedstoneHatch hatch : gtsr$boundRedstoneHatches) {
            hatch.setController(null);
        }
        gtsr$boundRedstoneHatches.clear();
    }
}
