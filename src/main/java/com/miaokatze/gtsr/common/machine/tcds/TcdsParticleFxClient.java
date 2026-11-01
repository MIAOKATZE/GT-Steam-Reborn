package com.miaokatze.gtsr.common.machine.tcds;

import java.util.List;

import com.miaokatze.gtsr.client.workeffects.CloudParticleQueue;
import com.miaokatze.gtsr.common.workeffects.WorkEffectDefinition.Emitter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Existing TCDS tick entry: two half-size native clouds, owned by this controller's original twenty e outlets. */
@SideOnly(Side.CLIENT)
public final class TcdsParticleFxClient {

    private TcdsParticleFxClient() {}

    /** The caller retains the original mWorkingForFX gate; there is no second TCDS emission loop. */
    public static void spawnParticles(MTEThermoChemicalDenseSteamGenerator controller) {
        if (controller == null) return;
        List<Emitter> emitters = TcdsParticleFx.candidates.get(controller);
        if (emitters == null || emitters.isEmpty()) return;
        // Previously each caller sampled the global pool again, multiplying other machines' emission rates.
        // Replace that pool with exact owner sampling and the single shared per-client-tick budget.
        CloudParticleQueue.submit(controller, controller.getExtendedFacing(), emitters, 2, .5F);
    }
}
