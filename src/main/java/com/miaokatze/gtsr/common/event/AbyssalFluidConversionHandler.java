package com.miaokatze.gtsr.common.event;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.FillBucketEvent;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 深渊执念流体桶装转换事件面（v1.20.40 P19 plan §I；U1 注册面）。
 * <p>
 * <b>短路机制依据</b>：{@code ItemBucket.onItemRightClick} 在一切原版舀取/放置逻辑之前投递
 * FillBucketEvent（ItemBucket.java:42-46，MinecraftForge.EVENT_BUS），handler 置
 * {@code result + Result.ALLOW} 后原版直接进入 :48-66 分支返回（消耗一格并交出 result），
 * <b>不再到达</b> :67-147 的原版舀水判定与 :177-216 {@code tryPlaceContainedLiquid}——
 * dim78 内原版水桶的"放原版水"即被此短路取消，改放自有块。原版水桶/自有桶在 dim78 外、
 * 原版空桶舀原版水均不接管（result 不动、Result 保持 DEFAULT → 原版逻辑照常）。
 * <p>
 * 覆盖矩阵：
 * <ul>
 * <li>原版空桶 + 命中 {@code BlockAbyssalFluid} 源（meta0）：置 air + 给自有桶（ALLOW，
 * 任意维度——自有块仅由 dim78 worldgen/转换产生，域外命中按同一语义收敛）。</li>
 * <li>原版水桶 + dim78：ItemBucket.java:107-135 同款 sideHit 偏移算目标格 + canPlayerEdit +
 * 可置性（air 或非 solid，对位 tryPlaceContainedLiquid :185-191）→ 放自有块 meta0，
 * 桶回空（ALLOW）。</li>
 * <li>自有深渊执念桶 + dim78：同水桶分支（自有桶放进 dim78 也统一走转换；域外不接管，
 * 回落原版 isFull 直放路径）。</li>
 * <li>dim78 外原版水桶：不干预（原版放水不变）；流动 meta&gt;0 不舀（对位原版 meta==0 判定）。</li>
 * </ul>
 * <p>
 * 注册口径：照仓内事件注册模式（DimensionInterferenceGuard 同款 static register 入口 +
 * CommonProxy.init 一行调用；1.7.10 Forge 无 @Mod.EventBusSubscriber）。失败开放：本 handler
 * 未注册时回落原版行为，不产生新崩溃面。worldgen 换块（GTSRRiverPlacer 等 Blocks.water →
 * 自有块）不属本类职责（P19 批2 U34）。
 */
public final class AbyssalFluidConversionHandler {

    private static final AbyssalFluidConversionHandler INSTANCE = new AbyssalFluidConversionHandler();

    /** 自有桶 Item 引用（register 期一次性解析，事件路径零分配；降级缺失时为 null → handler 全程不接管）。 */
    private static net.minecraft.item.Item abyssalBucket;

    private AbyssalFluidConversionHandler() {}

    /**
     * 注册入口（CommonProxy.init 一行调用，时序在 ItemLoader.initAbyssalBucket 之后）：
     * FillBucketEvent 在 EVENT_BUS（ItemBucket.java:43）。
     */
    public static void register() {
        final ItemStack bucketStack = GTSRItemList.AbyssalObsessionBucket.get(1);
        abyssalBucket = bucketStack != null ? bucketStack.getItem() : null;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        GTSteamReborn.LOG.info("[GTSR] abyssal fluid conversion handler registered: scoop=ownBucket place=dim78");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFillBucket(FillBucketEvent event) {
        // 降级守卫：方块/桶任一未注册（材料链异常降级）时整体不接管，回落原版行为
        if (BlocksGTSR.abyssalFluid == null || abyssalBucket == null) {
            return;
        }
        final World world = event.world;
        final MovingObjectPosition target = event.target;
        if (world == null || target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        final ItemStack current = event.current;
        if (current == null || current.getItem() == null) {
            return;
        }

        // 舀取：原版空桶命中自有源块（任意维度；meta==0 对位 ItemBucket.java:88 原版水判定）
        if (current.getItem() == Items.bucket) {
            if (world.getBlock(target.blockX, target.blockY, target.blockZ) == BlocksGTSR.abyssalFluid
                && world.getBlockMetadata(target.blockX, target.blockY, target.blockZ) == 0) {
                if (!world.isRemote && playerCanEdit(event, target)) {
                    world.setBlockToAir(target.blockX, target.blockY, target.blockZ);
                }
                event.result = new ItemStack(abyssalBucket);
                event.setResult(Event.Result.ALLOW);
            }
            return;
        }

        // 放置转换：原版水桶 / 自有桶，仅 dim78（dim 判定同仓内惯例 Config.prosperityDimId
        // ——ProsperityWorldGenerator.java:130 同款；worldgen 换块归 P19 批2 U34，此处零耦合）
        final boolean filledBucket = current.getItem() == Items.water_bucket || current.getItem() == abyssalBucket;
        if (!filledBucket || !isProsperityDim(world)) {
            return;
        }
        final int x = offset(target.blockX, target.sideHit, 2, 3);
        final int y = offset(target.blockY, target.sideHit, 0, 1);
        final int z = offset(target.blockZ, target.sideHit, 4, 5);
        // 可置性对位 tryPlaceContainedLiquid（ItemBucket.java:185-191）：目标格须 air 或非 solid
        if (world.isAirBlock(x, y, z) || !world.getBlock(x, y, z)
            .getMaterial()
            .isSolid()) {
            if (!world.isRemote && playerCanEdit(event, target)) {
                // flags=3 对位原版 tryPlaceContainedLiquid（ItemBucket.java:210 setBlock(...,0,3)）
                world.setBlock(x, y, z, BlocksGTSR.abyssalFluid, 0, 3);
            }
            event.result = new ItemStack(Items.bucket);
            event.setResult(Event.Result.ALLOW);
        }
    }

    /** sideHit → 坐标偏移（ItemBucket.java:107-135 同表：0/1=y∓，2/3=z∓，4/5=x∓）。 */
    private static int offset(int base, int sideHit, int sideLow, int sideHigh) {
        if (sideHit == sideLow) {
            return base - 1;
        }
        return sideHit == sideHigh ? base + 1 : base;
    }

    /** 编辑权判定对位原版（ItemBucket.java:80/:137 canPlayerEdit，用命中面而非偏移格，同原版口径）。 */
    private static boolean playerCanEdit(FillBucketEvent event, MovingObjectPosition target) {
        return event.entityPlayer != null && event.entityPlayer.capabilities != null
            && event.entityPlayer
                .canPlayerEdit(target.blockX, target.blockY, target.blockZ, target.sideHit, event.current);
    }

    private static boolean isProsperityDim(World world) {
        return world.provider != null && world.provider.dimensionId == com.miaokatze.gtsr.config.Config.prosperityDimId;
    }
}
