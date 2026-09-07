package com.miaokatze.gtsr.common.machine.tcds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.util.Vec3Impl;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * TCDS 工作粒子 FX 客户端渲染入口（控制器 onPostTick 客户端分支调用，调用方以 mWorkingForFX 为唯一
 * 工作态权威判据）：从 {@link TcdsParticleFx} 候选位池取位每 tick 喷 2 个上升 cloud 粒子
 * （EntityCloudFX 经 multipleParticleScaleBy(0.5F) 细颗粒；+0.5 居中、0.8 格抖动、vy=0.3；
 * 集群 ClusterParticleFxClient 同参数范式）。无候选位则无粒子。
 *
 * <p>
 * 分离原因（服务端类加载安全，与集群同 rationale）：客户端类（Minecraft/EntityCloudFX）不得混入
 * 公共登记簿，否则专用服务器任何 register/clear 调用触发 J8 类校验并抛
 * {@code NoClassDefFoundError}。本类仅客户端调用路径可达。
 */
public final class TcdsParticleFxClient {

    /** 每 tick 喷粒数量（集群 spawnParticles 同款 2/tick）。 */
    private static final int PARTICLES_PER_TICK = 2;

    private TcdsParticleFxClient() {}

    /**
     * 客户端入口（控制器 onPostTick 客户端分支调用，调用方以 mWorkingForFX 为唯一工作态权威判据）：
     * 从候选位池随机取位每 tick 喷 {@value #PARTICLES_PER_TICK} 个上升 cloud 粒子；无候选位则无粒子。
     */
    public static void spawnParticles(MTEThermoChemicalDenseSteamGenerator controller) {
        if (controller == null || controller.getBaseMetaTileEntity() == null) return;
        World world = controller.getBaseMetaTileEntity()
            .getWorld();
        if (world == null) return;
        pruneStaleControllers(world);
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<MTEThermoChemicalDenseSteamGenerator, List<int[]>> entry : TcdsParticleFx.candidates
            .entrySet()) {
            MTEThermoChemicalDenseSteamGenerator registered = entry.getKey();
            IGregTechTileEntity base = registered.getBaseMetaTileEntity();
            if (base == null) continue;
            for (int[] off : entry.getValue()) {
                candidates.add(
                    new Candidate(
                        base.getXCoord(),
                        base.getYCoord(),
                        base.getZCoord(),
                        registered.getExtendedFacing()
                            .getWorldOffset(new Vec3Impl(off[0], off[1], off[2]))));
            }
        }
        if (candidates.isEmpty()) return;
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            Candidate candidate = candidates.get(world.rand.nextInt(candidates.size()));
            spawnOne(candidate.x, candidate.y, candidate.z, candidate.offset, world);
        }
    }

    private static final class Candidate {

        private final int x;
        private final int y;
        private final int z;
        private final Vec3Impl offset;

        private Candidate(int x, int y, int z, Vec3Impl offset) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.offset = offset;
        }
    }

    /** 清理基座已失效（区块卸载残留）的控制器注册项。 */
    private static void pruneStaleControllers(World world) {
        TcdsParticleFx.candidates.keySet()
            .removeIf(
                controller -> controller.getBaseMetaTileEntity() == null || controller.getBaseMetaTileEntity()
                    .getWorld() != world);
    }

    /**
     * 在世界坐标（控制器 + 朝向偏移）处喷一个上升 cloud 粒子（EntityCloudFX 经
     * multipleParticleScaleBy(0.5F) 细颗粒；+0.5 居中、0.8 格抖动、vy=0.3 语义不变），
     * 加入客户端 EffectRenderer（仅客户端调用路径触达）。
     */
    private static void spawnOne(int x, int y, int z, Vec3Impl worldOff, World world) {
        EntityCloudFX fx = new EntityCloudFX(
            world,
            x + worldOff.get0() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.8D,
            y + worldOff.get1() + 0.5D,
            z + worldOff.get2() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.8D,
            0.0D,
            0.3D,
            0.0D);
        fx.multipleParticleScaleBy(0.5F);
        Minecraft.getMinecraft().effectRenderer.addEffect(fx);
    }
}
