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

    /** 自顶向下找 (0,0) 第一个非空气方块；全空（未生成/空维度）回退 64。 */
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
