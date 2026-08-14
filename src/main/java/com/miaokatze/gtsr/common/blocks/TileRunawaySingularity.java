package com.miaokatze.gtsr.common.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.network.GTSRFXNet;
import com.miaokatze.gtsr.loader.BlockLoader;

/**
 * 失控奇点方块实体
 * S2：服务端吸收/牵引/衰减逻辑已实现。
 * 时间语义：duration 单位=tick（600=30 秒），speed 单位=方块/20tick，damage 单位=伤害/20tick。
 * 颜色：color 为 16 原版染料色之一（默认 white），仅影响视觉表现（主体光效与光片）。
 * 特殊状态：attributeId=-1 表示 special=null，纯动画，不吸引/不破坏/不吸收任何方块与实体；
 * attributeId=-2 表示 special=onlypull，只牵引不吸收（不吸收方块、不处理掉落物、牵引力度减半、伤害照常）；
 * attributeId=-3 表示 special=nullplus，null 基础上无电弧无粒子（吸积盘/电弧跳过），光片/辉光保留。
 * 光效半径：fxRadius（默认 10，钳制 [0.5,128]）仅影响视觉（光片/辉光），与吸收/牵引半径 range 独立。
 * 无 NBT 默认参数：(10 0 0 600 null white 10)——NBT 丢失时回退为惰性有限时长奇点，绝不摧毁周边机器。
 */
public class TileRunawaySingularity extends TileEntity {

    private static final int SCAN_INTERVAL = 4; // 吸收扫描间隔 tick
    private static final int DAMAGE_INTERVAL = 20; // 实体伤害间隔 tick（speed/damage 均按每 20 tick 计量）
    private static final DamageSource SINGULARITY_DAMAGE = new DamageSource("gtsrSingularity").setDamageBypassesArmor()
        .setDamageIsAbsolute();

    /**
     * 16 原版染料色 → RGB（0~1），大小写不敏感；未知色回退 white
     */
    private static final Map<String, float[]> COLOR_RGB = buildColorRgb();

    private static Map<String, float[]> buildColorRgb() {
        Map<String, float[]> map = new HashMap<String, float[]>();
        map.put("white", new float[] { 1.0F, 1.0F, 1.0F });
        map.put("orange", new float[] { 0.847F, 0.498F, 0.2F });
        map.put("magenta", new float[] { 0.698F, 0.298F, 0.847F });
        map.put("light_blue", new float[] { 0.4F, 0.6F, 0.847F });
        map.put("yellow", new float[] { 0.898F, 0.898F, 0.2F });
        map.put("lime", new float[] { 0.498F, 0.8F, 0.098F });
        map.put("pink", new float[] { 0.949F, 0.498F, 0.647F });
        map.put("gray", new float[] { 0.298F, 0.298F, 0.298F });
        map.put("silver", new float[] { 0.6F, 0.6F, 0.6F });
        map.put("cyan", new float[] { 0.298F, 0.498F, 0.6F });
        map.put("purple", new float[] { 0.498F, 0.247F, 0.698F });
        map.put("blue", new float[] { 0.2F, 0.298F, 0.698F });
        map.put("brown", new float[] { 0.4F, 0.298F, 0.2F });
        map.put("green", new float[] { 0.4F, 0.498F, 0.2F });
        map.put("red", new float[] { 0.6F, 0.2F, 0.2F });
        map.put("black", new float[] { 0.098F, 0.098F, 0.098F });
        return map;
    }

    /**
     * 是否为合法的 16 原版染料色（大小写不敏感）
     */
    public static boolean isValidColor(String name) {
        return name != null && COLOR_RGB.containsKey(name.toLowerCase());
    }

    /**
     * 颜色名规范化：未知或 null → "white"，已知 → 原样返回
     */
    public static String normalizeColor(String name) {
        if (isValidColor(name)) {
            return name;
        }
        return "white";
    }

    public static final int ATTRIBUTE_NULL = -1; // 特殊状态 null：纯动画，不吸引/不破坏/不吸收任何方块与实体
    public static final int ATTRIBUTE_ONLY_PULL = -2; // 特殊状态 onlypull：只牵引不吸收（不吸收方块、不处理掉落物、牵引力度减半、伤害照常）
    public static final int ATTRIBUTE_NULL_PLUS = -3; // 特殊状态 nullplus：null 基础上无电弧无粒子（吸积盘/电弧跳过），光片/辉光保留
    public static final int ATTRIBUTE_NATURE = -4; // 特殊状态 nature：自然生成专用——不吸引/伤害实体，只牵引破坏掉落物+吸收方块，硬度4空手可挖，挖后爆炸

    private double range = 10.0D;
    private double speed = 0.0D;
    private double damage = 0.0D;
    private int duration = 600; // tick，600 = 30 秒，-1=无限
    private int attributeId = -1; // -1=ATTRIBUTE_NULL（纯动画）；-2=ATTRIBUTE_ONLY_PULL（只牵引不吸收）；无 NBT 默认即惰性奇点
    private String color = "white"; // 16 原版染料色之一，默认 white，仅影响视觉表现
    private double fxRadius = 10.0D; // 光效半径，NBT 缺省默认 10；仅影响视觉（光片/辉光），与 range 独立
    private boolean destroyBlocks = true; // 自然生成奇点是否吸收破坏方块（absorbScan 方块吸收），NBT 缺省默认 true（向后兼容）；仅服务端逻辑字段
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
        if (data.hasKey("color")) {
            this.color = data.getString("color");
        }
        if (data.hasKey("fxRadius")) {
            this.fxRadius = data.getDouble("fxRadius"); // 缺省保持 10
        }
        if (data.hasKey("destroyBlocks")) {
            this.destroyBlocks = data.getBoolean("destroyBlocks"); // 缺省保持 true（旧 NBT 无键 → 向后兼容）
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
        data.setString("color", this.color);
        data.setDouble("fxRadius", this.fxRadius);
        data.setBoolean("destroyBlocks", this.destroyBlocks);
        data.setInteger("elapsed", this.elapsedTicks);
        tag.setTag("gtsrSingularity", data);
    }

    /**
     * NBT 同步到客户端：客户端 TE 默认 duration=600，不同步会导致动画按 30 秒消散（节点本体仍在原位）。
     */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
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

    public String getColor() {
        return color;
    }

    public double getFxRadius() {
        return fxRadius;
    }

    public void setDestroyBlocks(boolean destroyBlocks) {
        this.destroyBlocks = destroyBlocks; // 服务端逻辑字段，不需 markBlockForUpdate（不参与客户端渲染同步）
    }

    /**
     * 当前颜色 RGB（0~1），返回副本；未知名回退 white 的 RGB
     */
    public float[] getColorRGB() {
        float[] rgb = COLOR_RGB.get(normalizeColor(color).toLowerCase());
        return new float[] { rgb[0], rgb[1], rgb[2] };
    }

    public boolean isInfinite() {
        return duration == -1;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public void setParams(double range, double speed, double damage, int duration, int attributeId, String color,
        double fxRadius) {
        this.range = Math.max(0.5D, Math.min(128.0D, range));
        this.speed = Math.max(0.0D, Math.min(100.0D, speed));
        this.damage = Math.max(0.0D, Math.min(1000.0D, damage));
        this.attributeId = attributeId == ATTRIBUTE_NULL || attributeId == ATTRIBUTE_ONLY_PULL
            || attributeId == ATTRIBUTE_NULL_PLUS
            || attributeId == ATTRIBUTE_NATURE ? attributeId : Math.max(0, Math.min(999, attributeId)); // 仅放行四个特殊值（-1
                                                                                                        // null 纯动画 / -2
                                                                                                        // onlypull
                                                                                                        // 只牵引不吸收 /
                                                                                                        // -3 nullplus
                                                                                                        // null
                                                                                                        // 基础上无电弧无粒子 /
                                                                                                        // -4 nature
                                                                                                        // 自然生成专用），其余
                                                                                                        // 0-999 钳制
        this.fxRadius = Math.max(0.5D, Math.min(128.0D, fxRadius));
        if (duration == -1) {
            this.duration = -1;
        } else {
            this.duration = Math.max(1, Math.min(360000, duration));
        }
        this.color = normalizeColor(color);
        if (worldObj != null) {
            worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord); // 触发 getDescriptionPacket，同步 NBT 到客户端
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
     * 静态生成助手（11 参版本，供命令/机器调用）：委托 12 参版本，destroyBlocks 缺省 true（向后兼容）
     */
    public static void spawnSingularity(World world, int x, int y, int z, double range, double speed, double damage,
        int duration, int attributeId, String color, double fxRadius) {
        spawnSingularity(world, x, y, z, range, speed, damage, duration, attributeId, color, fxRadius, true);
    }

    /**
     * 静态生成助手（12 参版本）：destroyBlocks=false 时自然生成奇点不吸收破坏方块（absorbScan 跳过），实体/掉落物处理照常
     */
    public static void spawnSingularity(World world, int x, int y, int z, double range, double speed, double damage,
        int duration, int attributeId, String color, double fxRadius, boolean destroyBlocks) {
        world.setBlock(x, y, z, BlockLoader.blockRunawaySingularity);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileRunawaySingularity) {
            TileRunawaySingularity teSingularity = (TileRunawaySingularity) te;
            teSingularity.setParams(range, speed, damage, duration, attributeId, color, fxRadius);
            teSingularity.setDestroyBlocks(destroyBlocks);
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
        if (attributeId == ATTRIBUTE_NULL || attributeId == ATTRIBUTE_NULL_PLUS) {
            return; // 特殊状态 null/nullplus：纯动画，不吸引/不破坏/不吸收任何方块与实体
        }
        boolean onlyPull = attributeId == ATTRIBUTE_ONLY_PULL;
        boolean nature = attributeId == ATTRIBUTE_NATURE;
        double factor = getActiveFactor();
        double effRange = range * factor;
        if (!onlyPull && (!nature || destroyBlocks)) {
            absorbScan(effRange, factor); // onlypull：跳过吸收扫描（absorbScan 只扫方块，不含实体）；nature 且 destroyBlocks=false
                                          // 同样跳过方块吸收，其余 nature 行为（掉落物牵引/销毁）handleEntities 照常
        }
        handleEntities(effRange, factor, onlyPull, nature);
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
        int cap = Math.max(1, (int) Math.round(speed * SCAN_INTERVAL / 20.0D)); // speed=每20tick方块数；speed=1 →
                                                                                // 每4tick期望0.2块
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
                GTSRFXNet.sendAbsorb(worldObj, t[0], t[1], t[2], xCoord, yCoord, zCoord);
                count++;
                if (count >= cap) {
                    break;
                }
            }
        }
    }

    /**
     * 辅吸收机制：概率节流单条随机射线（仿 Thaumcraft 饕餮节点）。
     * speed=1 时平均每 20 tick 发 0.25 条，作为吸收主机制的少量补充。
     */
    private void absorbRays(double effRange, double factor) {
        if (worldObj.rand.nextDouble() >= speed * factor * 0.25D / 20.0D) {
            return;
        }
        double cx = xCoord + 0.5D;
        double cy = yCoord + 0.5D;
        double cz = zCoord + 0.5D;
        int absorbed = 0;
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
            absorbed++;
            break; // 单条射线至多吸收 1 块
        }
    }

    /**
     * 实体牵引/伤害/掉落物/飞行取消
     * onlyPull=true（attributeId=-2）：掉落物完全不处理（不牵引、不销毁）；
     * 其他实体（含玩家）牵引力度减半；伤害与玩家飞行处理照常。
     * nature=true（attributeId=-4）：只处理 EntityItem（牵引 + d<1.5 销毁），跳过所有活体/玩家牵引、伤害、禁飞。
     */
    private void handleEntities(double effRange, double factor, boolean onlyPull, boolean nature) {
        double cx = xCoord + 0.5D;
        double cy = yCoord + 0.5D;
        double cz = zCoord + 0.5D;
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(cx - effRange, cy - effRange, cz - effRange, cx + effRange, cy + effRange, cz + effRange);
        List<Entity> list = worldObj.getEntitiesWithinAABB(Entity.class, box);
        int inRange = 0;
        int players = 0;
        int itemsVanish = 0;
        int living = 0;
        for (Entity entity : list) {
            double dx = cx - entity.posX;
            double dy = cy - entity.posY;
            double dz = cz - entity.posZ;
            double d2 = dx * dx + dy * dy + dz * dz;
            double d = Math.sqrt(d2);
            if (d > effRange) {
                continue; // 球过滤
            }
            inRange++;
            if (entity instanceof EntityPlayer && ((EntityPlayer) entity).capabilities.isCreativeMode) {
                continue; // 创造玩家完全豁免
            }
            if (onlyPull && entity instanceof EntityItem) {
                continue; // onlypull：掉落物完全不处理（既不牵引也不执行近距销毁 setDead）
            }
            if (nature && !(entity instanceof EntityItem)) {
                continue; // nature：只处理掉落物，跳过所有活体/玩家（不牵引、不伤害、不禁飞）
            }
            double pull = 1.0D - d / effRange;
            double force = pull * pull * factor;
            if (onlyPull) {
                force *= 0.5D; // onlypull：牵引力度减半（伤害与玩家飞行处理照常）
            }
            if (d > 0.0D && !(entity instanceof EntityPlayer)) {
                entity.addVelocity(dx / d * force * 0.15D, dy / d * force * 0.25D, dz / d * force * 0.15D);
            }
            if (entity instanceof EntityItem && d < 1.5D) {
                entity.setDead();
                itemsVanish++;
                continue;
            }
            if (entity instanceof EntityLivingBase) {
                living++;
                // 伤害按每 20 tick 一次结算，damage=每20tick伤害值（speed=1 → 每秒 1 点）
                if (worldObj.getWorldTime() % DAMAGE_INTERVAL == 0) {
                    entity.attackEntityFrom(SINGULARITY_DAMAGE, (float) (damage * factor));
                }
            }
            if (entity instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) entity;
                players++;
                if (p.capabilities.allowFlying) {
                    p.capabilities.allowFlying = false;
                    p.capabilities.isFlying = false;
                    p.sendPlayerAbilities();
                }
                if (d > 0.0D) {
                    p.motionX += dx / d * force * 0.15D;
                    p.motionY += dy / d * force * 0.25D;
                    p.motionZ += dz / d * force * 0.15D;
                }
                ((EntityPlayerMP) p).playerNetServerHandler.sendPacket(new S12PacketEntityVelocity(p));
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
