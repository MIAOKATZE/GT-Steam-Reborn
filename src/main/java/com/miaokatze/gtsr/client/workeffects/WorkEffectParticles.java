// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.workeffects;

import net.minecraft.world.World;

import com.miaokatze.gtsr.client.machinevisual.MachineVisualClient;
import com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray;
import com.miaokatze.gtsr.common.machine.MTELargeGeothermalSteamBoiler;
import com.miaokatze.gtsr.common.machine.MTELargeSolarOverpressureArray;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;
import com.miaokatze.gtsr.common.workeffects.WorkEffectDefinition;
import com.miaokatze.gtsr.common.workeffects.WorkEffectDefinitions;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client host adapter; it consumes existing heat or the shared authoritative work snapshot, never recipe methods. */
@SideOnly(Side.CLIENT)
public final class WorkEffectParticles {

    private static long solarClientTicks;
    private static long geothermalClientTicks;
    private static long solarFreshTicks;
    private static long geothermalFreshTicks;

    private WorkEffectParticles() {}

    public static void tick(MachineVisualHost host, long tick) {
        if (host instanceof MTELargeSolarOverpressureArray) solarClientTicks++;
        if (host instanceof MTELargeGeothermalSteamBoiler) geothermalClientTicks++;
        if (!MachineVisualClient.isFresh(host)) return;
        WorkEffectDefinition definition = WorkEffectDefinitions.forHost(host);
        if (definition == null || definition.emitters()
            .isEmpty()) return;
        if (host instanceof MTEKineticProcessingArray) {
            if (!MachineVisualClient.isWorking(host) || !MachineVisualClient.isAnimating(host)) return;
            long phaseMs = Math.floorMod(MachineVisualClient.decorativeTime(host), 3000L);
            boolean impact = phaseMs >= 1695L && phaseMs <= 2070L;
            if (impact && tick % 2 == 0 && (MachineVisualClient.uploadedGroupMask(host) & 1) != 0) {
                CloudParticleQueue.submit(
                    host,
                    host.getExtendedFacing(),
                    definition.emitters()
                        .subList(0, 2),
                    2,
                    1F);
            } else if (tick % 10 == 0) {
                CloudParticleQueue.submitEach(
                    host,
                    host.getExtendedFacing(),
                    definition.emitters()
                        .subList(2, 6),
                    1F);
            }
            return;
        }
        World world = host.getBaseMetaTileEntity()
            .getWorld();
        double heat;
        if (host instanceof MTELargeSolarOverpressureArray solar) {
            solarFreshTicks++;
            heat = solar.mHeat;
        } else if (host instanceof MTELargeGeothermalSteamBoiler geothermal) {
            geothermalFreshTicks++;
            heat = geothermal.mHeat;
        } else {
            if (tick % 10 == 0 && MachineVisualClient.isWorking(host)) {
                CloudParticleQueue.submit(host, host.getExtendedFacing(), definition.emitters(), 1, 1F);
            }
            return;
        }
        // This is the original controller-wide 2*heat expectation, sampled over its nine existing outlets once.
        if (!Double.isFinite(heat) || heat <= 0D) return;
        double expected = Math.min(CloudParticleQueue.MAX_PER_OWNER, heat / .5D);
        int count = (int) expected;
        if (world.rand.nextDouble() < expected - count) count++;
        CloudParticleQueue.submit(host, host.getExtendedFacing(), definition.emitters(), count, 1F);
    }

    public static String diagnostics() {
        return "solarClientTicks=" + solarClientTicks
            + ",solarFreshTicks="
            + solarFreshTicks
            + ",geothermalClientTicks="
            + geothermalClientTicks
            + ",geothermalFreshTicks="
            + geothermalFreshTicks;
    }
}
