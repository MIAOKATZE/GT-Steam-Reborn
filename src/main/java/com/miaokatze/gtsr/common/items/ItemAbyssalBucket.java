package com.miaokatze.gtsr.common.items;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBucket;

import com.miaokatze.gtsr.register.CreativeTabManager;

/**
 * 深渊执念桶（v1.20.40 P19 plan §I；U1 注册面）：ItemBucket 同款桶装交互面，isFull =
 * {@code BlocksGTSR.abyssalFluid}。
 * <p>
 * 交互矩阵（配合 AbyssalFluidConversionHandler，全部 ALLOW 短路路径见其类注释）：
 * <ul>
 * <li>dim78 内右击：FillBucketEvent → handler 算 face 偏移格放 {@code BlockAbyssalFluid}
 * （ItemBucket.java:107-135 同款偏移），桶回空原版桶；ALLOW 短路使原版
 * tryPlaceContainedLiquid（ItemBucket.java:177-216）不再执行。</li>
 * <li>dim78 外右击：handler 不接管，回落原版 ItemBucket 逻辑直接放自有方块
 * （isFull 路径，:142 tryPlaceContainedLiquid）；舀取则走 handler 任意维度舀取分支。</li>
 * <li>原版空桶舀自有源块：handler → 本桶（任意维度）。</li>
 * </ul>
 * <p>
 * 容器互认（1000mB ↔ 原版空桶）在 {@code ItemLoader.initAbyssalBucket()} 于 init 段经
 * FluidContainerRegistry.registerFluidContainer(FluidStack, filled, empty) 登记
 * （FluidContainerRegistry.java:100 三参重载）——不放构造函数：注册面统一收敛在 loader，
 * 与 BlockLoader.initAbyssalFluid 同段同时序。贴图 gtsr:AbyssalObsessionBucket（16px，
 * tools/artgen/dim7879/gen_abyssal_bucket.py 一次性生成）。
 */
public class ItemAbyssalBucket extends ItemBucket {

    public ItemAbyssalBucket(Block fluidBlock) {
        super(fluidBlock);
        setUnlocalizedName("AbyssalObsessionBucket");
        setTextureName("gtsr:AbyssalObsessionBucket");
        // ItemBucket 构造默认 tabMisc（ItemBucket.java:25），按仓内惯例改自有创造页签
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
    }
}
