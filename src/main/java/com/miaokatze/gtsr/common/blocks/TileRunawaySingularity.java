package com.miaokatze.gtsr.common.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.miaokatze.gtsr.loader.BlockLoader;

/**
 * 失控奇点方块实体
 * S2：服务端吸收/牵引/衰减逻辑已实现。
 */
public class TileRunawaySingularity extends TileEntity {

    private static final int SCAN_INTERVAL = 4; // 吸收扫描间隔 tick
    private static final int SCAN_CAP = 16; // 单次扫描吸收上限
    private static final int DAMAGE_INTERVAL = 20; // 实体伤害间隔 tick（每秒一次）
    private static final DamageSource SINGULARITY_DAMAGE = new DamageSource("gtsrSingularity").setDamageBypassesArmor()
        .setDamageIsAbsolute();

    private double range = 10.0D;
    private double speed = 1.0D;
    private double damage = 1.0D;
    private int duration = 30; // 秒，-1=无限
    private int attributeId = 0;
    private int elapsedTicks = 0; // 服务端与客户端各自递增

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        NBTTagCompound data = tag.getCompoundTag("gtsrSingularity");
        if (data.hasKey("range")) {
            this.range = data.getDouble("range");
        }
        if (data.hasKey("speed")) {
            this.speed = data.getDouble("speed");
        }
        if (data.hasKey("damage")) {
            this.damage = data.getDouble("damage");
        }
        if (data.hasKey("duration")) {
            this.duration = data.getInteger("duration");
        }
        if (data.hasKey("attribute")) {
            this.attributeId = data.getInteger("attribute");
        }
        this.elapsedTicks = data.getInteger("elapsed"); // 无键保持 0
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagCompound data = new NBTTagCompound();
        data.setDouble("range", this.range);
        data.setDouble("speed", this.speed);
        data.setDouble("damage", this.damage);
        data.setInteger("duration", this.duration);
        data.setInteger("attribute", this.attributeId);
        data.setInteger("elapsed", this.elapsedTicks);
        tag.setTag("gtsrSingularity", data);
    }

    public double getRange() {
        return range;
    }

    public double getSpeed() {
        return speed;
    }

    public double getDamage() {
        return damage;
    }

    public int getDuration() {
        return duration;
    }

    public int getAttributeId() {
        return attributeId;
    }

    public boolean isInfinite() {
        return duration == -1;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public void setParams(double range, double speed, double damage, int duration, int attributeId) {
        this.range = Math.max(0.5D, Math.min(128.0D, range));
        this.speed = Math.max(0.01D, Math.min(100.0D, speed));
        this.damage = Math.max(0.0D, Math.min(1000.0D, damage));
        this.attributeId = Math.max(0, Math.min(999, attributeId));
        if (duration == -1) {
            this.duration = -1;
        } else {
            this.duration = Math.max(1, Math.min(360000, duration));
        }
        if (worldObj != null) {
            worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
        }
    }

    /**
     * 衰减系数骨架：无限 1.0；时间耗尽 0.0；前 80% 持续时间 1.0；最后 20% 线性衰减
     */
    public double getActiveFactor() {
        if (isInfinite()) {
            return 1.0D;
        }
        if (elapsedTicks >= duration) {
            return 0.0D;
        }
        if (elapsedTicks < duration * 0.8D) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (duration - elapsedTicks) / (duration * 0.2D)));
    }

    /**
     * 静态生成助手（供后续多方块爆炸调用）
     */
    public static void spawnSingularity(World world, int x, int y, int z, double range, double speed, double damage,
        int duration, int attributeId) {
        world.setBlock(x, y, z, BlockLoader.blockRunawaySingularity);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileRunawaySingularity) {
            ((TileRunawaySingularity) te).setParams(range, speed, damage, duration, attributeId);
            te.markDirty();
        }
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (isInfinite()) {
            // 无限：只跑吸收/实体逻辑
        } else {
            elapsedTicks++;
        }
        if (worldObj.isRemote) {
            return; // 客户端粒子由 SingularityClientFXHandler 生成（另一切片）
        }
        if (!isInfinite() && elapsedTicks >= duration) {
            worldObj.setBlockToAir(xCoord, yCoord, zCoord); // 时间耗尽，奇点销毁，事件结束
            return;
        }
        double factor = getActiveFactor();
        double effRange = range * factor;
        absorbScan(effRange, factor);
        absorbRays(effRange, factor);
        handleEntities(effRange, factor);
    }

    /**
     * 主吸收机制：球形扫描 + 距离排序 + 概率吸收
     * 扫描半径上限 48：超大范围时外层区域由射线机制覆盖，避免每 4 tick 全量立方体遍历造成卡顿。
     */
    private void absorbScan(double effRange, double factor) {
        if (worldObj.getWorldTime() % SCAN_INTERVAL != 0) {
            return;
        }
        double scanRange = Math.min(effRange, 48.0D);
        int r = (int) Math.ceil(scanRange);
        double rangeSq = scanRange * scanRange;
        List<int[]> targets = new ArrayList<int[]>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (d2 > rangeSq) {
                        continue;
                    }
                    int wx = xCoord + dx;
                    int wy = yCoord + dy;
                    int wz = zCoord + dz;
                    Block block = worldObj.getBlock(wx, wy, wz);
                    if (block.isAir(worldObj, wx, wy, wz) || block == Blocks.air) {
                        continue;
                    }
                    if (block == BlockLoader.blockRunawaySingularity) {
                        continue;
                    }
                    float h = block.getBlockHardness(worldObj, wx, wy, wz);
                    if (h < 0.0F) {
                        continue; // 硬度 -1 不可吸收
                    }
                    targets.add(new int[] { wx, wy, wz });
                }
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        Collections.sort(targets, new Comparator<int[]>() {

            @Override
            public int compare(int[] a, int[] b) {
                int da2 = (a[0] - xCoord) * (a[0] - xCoord) + (a[1] - yCoord) * (a[1] - yCoord)
                    + (a[2] - zCoord) * (a[2] - zCoord);
                int db2 = (b[0] - xCoord) * (b[0] - xCoord) + (b[1] - yCoord) * (b[1] - yCoord)
                    + (b[2] - zCoord) * (b[2] - zCoord);
                return da2 - db2;
            }
        });
        int count = 0;
        for (int[] t : targets) {
            double dx = t[0] - xCoord;
            double dy = t[1] - yCoord;
            double dz = t[2] - zCoord;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double f = speedFactor(d, scanRange);
            double jitter = 0.5D + worldObj.rand.nextDouble();
            double p = Math.min(1.0D, speed * factor * f * jitter * SCAN_INTERVAL / 20.0D);
            if (worldObj.rand.nextDouble() < p) {
                worldObj.setBlockToAir(t[0], t[1], t[2]);
                count++;
                if (count >= SCAN_CAP) {
                    break;
                }
            }
        }
    }

    /**
     * 辅吸收机制：随机射线（仿 Thaumcraft 饕餮节点）
     */
    private void absorbRays(double effRange, double factor) {
        int n = Math.max(1, Math.min(8, (int) Math.round(speed * factor * 0.25D)));
        double cx = xCoord + 0.5D;
        double cy = yCoord + 0.5D;
        double cz = zCoord + 0.5D;
        for (int i = 0; i < n; i++) {
            double yaw = worldObj.rand.nextDouble() * 2.0D * Math.PI - Math.PI;
            double pitch = worldObj.rand.nextDouble() * Math.PI - Math.PI / 2.0D;
            double dirX = Math.cos(pitch) * Math.cos(yaw);
            double dirY = Math.sin(pitch);
            double dirZ = Math.cos(pitch) * Math.sin(yaw);
            for (double t = 0.0D; t <= effRange; t += 0.75D) {
                int bx = (int) Math.floor(cx + dirX * t);
                int by = (int) Math.floor(cy + dirY * t);
                int bz = (int) Math.floor(cz + dirZ * t);
                Block block = worldObj.getBlock(bx, by, bz);
                if (block.isAir(worldObj, bx, by, bz) || block == Blocks.air) {
                    continue;
                }
                if (block == BlockLoader.blockRunawaySingularity) {
                    continue;
                }
                if (block.getBlockHardness(worldObj, bx, by, bz) < 0.0F) {
                    continue; // 不可吸收方块，继续沿射线步进
                }
                worldObj.setBlockToAir(bx, by, bz);
                break; // 每条射线至多吸收 1 块
            }
        }
    }

    /**
     * 实体牵引/伤害/掉落物/飞行取消
     */
    private void handleEntities(double effRange, double factor) {
        double cx = xCoord + 0.5D;
        double cy = yCoord + 0.5D;
        double cz = zCoord + 0.5D;
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(cx - effRange, cy - effRange, cz - effRange, cx + effRange, cy + effRange, cz + effRange);
        List<Entity> list = worldObj.getEntitiesWithinAABB(Entity.class, box);
        if (list.isEmpty()) {
            return;
        }
        for (Entity entity : list) {
            double dx = entity.posX - cx;
            double dy = entity.posY - cy;
            double dz = entity.posZ - cz;
            double d2 = dx * dx + dy * dy + dz * dz;
            double d = Math.sqrt(d2);
            if (d > effRange) {
                continue; // 球过滤
            }
            if (entity instanceof EntityPlayer && ((EntityPlayer) entity).capabilities.isCreativeMode) {
                continue; // 创造玩家完全豁免
            }
            double pull = 1.0D - d / effRange;
            double force = pull * pull * factor;
            if (d > 0.0D) {
                entity.addVelocity(dx / d * force * 0.15D, dy / d * force * 0.25D, dz / d * force * 0.15D);
            }
            if (entity instanceof EntityItem && d < 1.5D) {
                entity.setDead();
                continue;
            }
            if (entity instanceof EntityLivingBase && worldObj.getWorldTime() % DAMAGE_INTERVAL == 0) {
                entity.attackEntityFrom(SINGULARITY_DAMAGE, (float) (damage * factor));
            }
            if (entity instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) entity;
                if (p.capabilities.allowFlying) {
                    p.capabilities.allowFlying = false;
                    p.capabilities.isFlying = false;
                    p.sendPlayerAbilities();
                }
            }
        }
    }

    /**
     * 分段线性速度曲线，结果夹在 [0.2, 5.0]
     */
    private double speedFactor(double d, double r) {
        if (r <= 0.0D) {
            return 1.0D;
        }
        double f;
        if (d <= r * 0.5D) {
            f = 5.0D - 8.0D * d / r; // 中心 5×，半距处 =1
        } else {
            f = 1.0D - 1.6D * (d - r * 0.5D) / r; // 半距 =1，边缘 0.2×
        }
        return Math.max(0.2D, Math.min(5.0D, f));
    }
}
