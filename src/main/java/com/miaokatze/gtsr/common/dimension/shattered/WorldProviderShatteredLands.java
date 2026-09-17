package com.miaokatze.gtsr.common.dimension.shattered;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.chunk.Chunk;

import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldProviderBase;
import com.miaokatze.gtsr.common.dimension.shattered.biome.ShatteredBiomes;

/**
 * 破碎之地 WorldProvider（dim1 S6a，04 §1.7 + plan §S6a）。
 * <p>
 * <ul>
 * <li>无太阳视觉：{@link #fixedCelestialAngle()} 恒 0.75F（永夜偏移，太阳/月亮恒在地平线下，
 * 天光经 moonlight 档保持可见不全黑）；</li>
 * <li>天空/雾色：04 §1.1 暗紫三变体按当前群系取色（{@link ShatteredBiomes#skyFogColorFor}），
 * getFogColor 无坐标参数，取最近玩家（渲染端即本地玩家）所在群系；</li>
 * <li>canDoLightning=true（天气层允许闪电，强制雷暴与附加雷击 = S6b 责任）；</li>
 * <li>canRespawnHere=false 继承 GTSRWorldProviderBase（plan §S6a 裁剪口径：死亡回主世界，
 * 比 04 §1.7 的 true 更符合"不做出生系统"；04 §4 出生/堡垒整节不做）；</li>
 * <li>传送门：无自带传送门方块，进入走 /gtsr tpdim B（S5）与 Hub 既有跨维范式（04 §1.7）。</li>
 * </ul>
 * isSurfaceWorld=true 继承基类（保留天光/天气管线，04 §1.7 注：闪电依赖此开关）。
 */
public class WorldProviderShatteredLands extends GTSRWorldProviderBase {

    /** 天穹恒暗角：无太阳昼夜循环渲染（04 §1.7 getCelestialAngle 0.75F）。 */
    @Override
    protected Float fixedCelestialAngle() {
        return 0.75F;
    }

    @Override
    public boolean canDoLightning(Chunk chunk) {
        return true;
    }

    @Override
    public Vec3 getFogColor(float partialTicks, float renderPass) {
        // 渲染端调用；最近玩家即本地玩家（客户端 playerEntities 只含本地玩家）
        final EntityPlayer player = this.worldObj.getClosestPlayer(0.0D, 128.0D, 0.0D, Double.MAX_VALUE);
        final int x = player != null ? MathHelper.floor_double(player.posX) : 0;
        final int z = player != null ? MathHelper.floor_double(player.posZ) : 0;
        return toVec3(ShatteredBiomes.skyFogColorFor(this.worldObj.getBiomeGenForCoords(x, z)));
    }

    @Override
    public Vec3 getSkyColor(Entity cameraEntity, float partialTicks) {
        final int x = cameraEntity != null ? MathHelper.floor_double(cameraEntity.posX) : 0;
        final int z = cameraEntity != null ? MathHelper.floor_double(cameraEntity.posZ) : 0;
        return toVec3(ShatteredBiomes.skyFogColorFor(this.worldObj.getBiomeGenForCoords(x, z)));
    }

    private static Vec3 toVec3(int rgb) {
        return Vec3.createVectorHelper(((rgb >> 16) & 255) / 255.0D, ((rgb >> 8) & 255) / 255.0D, (rgb & 255) / 255.0D);
    }
}
