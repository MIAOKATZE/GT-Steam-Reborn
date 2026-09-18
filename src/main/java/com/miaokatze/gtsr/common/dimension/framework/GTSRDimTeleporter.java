package com.miaokatze.gtsr.common.dimension.framework;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

/**
 * 无门传送器（dim1 S1 落位，S5 /gtsr tpdim 消费）。
 * <p>
 * 不生成任何传送门方块：placeInPortal 直接把实体放到目标世界 (0.5, surfaceY+1, 0.5)
 * （(0,0) 地表上方一格）；placeInExistingPortal/makePortal 恒 true（无门体系无缓存/无搭建）。
 */
public class GTSRDimTeleporter extends Teleporter {

    private final WorldServer destinationWorld;

    public GTSRDimTeleporter(WorldServer destinationWorld) {
        super(destinationWorld);
        this.destinationWorld = destinationWorld;
    }

    @Override
    public void placeInPortal(Entity entity, double oldX, double oldY, double oldZ, float rotationYaw) {
        int surfaceY = findSurfaceY();
        entity.setLocationAndAngles(0.5D, surfaceY + 1.0D, 0.5D, entity.rotationYaw, 0.0F);
        entity.motionX = entity.motionY = entity.motionZ = 0.0D;
    }

    @Override
    public boolean placeInExistingPortal(Entity entity, double oldX, double oldY, double oldZ, float rotationYaw) {
        return true;
    }

    @Override
    public boolean makePortal(Entity entity) {
        return true;
    }

    @Override
    public void removeStalePortalLocations(long worldTime) {}

    /**
     * 自顶向下找 (0,0) 第一个非空气方块；全空（未生成/空维度）回退 64。
     * <p>
     * <b>P3 登记：本列扫刻意不并入框架唯一件
     * {@link GTSRChunkProviderBase#findSurfaceY}</b>（审计 A-5 §1 #1 判为
     * 语义各异），三处差异任一被"统一"都会改变传送落点：
     * <ol>
     * <li><b>无坐标参数</b>——恒查 (0,0) 列（{@code placeInPortal} 的落点就是 (0.5, y+1, 0.5)），
     * 而框架件按传入 (x,z) 扫任意列；</li>
     * <li><b>起点不同</b>——本件从 {@code world.getActualHeight()-1} 起扫（跟随 provider 声明的
     * 实际高度，维度降到 128 时不会白扫），框架件恒从 255 起；</li>
     * <li><b>兜底不同</b>——全空时本件回退 <b>64</b>（一个"大概率不埋不悬"的经验高度，
     * 保证传送不炸），框架件回退 <b>-1</b>（由调用方自行跳过放置）。</li>
     * </ol>
     * 另本件无 {@code block != null} 守卫（直接 {@code getBlock(...).getMaterial()}），
     * 在 {@code World.getBlock} 恒非 null 的实现下与框架件的守卫等价，但依赖 MC 版本行为，
     * 统一它属独立裁决，不在本片。
     */
    private int findSurfaceY() {
        for (int y = this.destinationWorld.getActualHeight() - 1; y > 0; y--) {
            if (this.destinationWorld.getBlock(0, y, 0)
                .getMaterial() != Material.air) {
                return y;
            }
        }
        return 64;
    }
}
