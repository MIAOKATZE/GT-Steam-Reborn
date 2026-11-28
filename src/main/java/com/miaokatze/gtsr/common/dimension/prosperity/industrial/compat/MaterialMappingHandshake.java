// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial.compat;

import java.util.LinkedHashSet;
import java.util.Set;

import com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdRegistration;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.handshake.NetworkDispatcher;

/**
 * Uses FML's initial REGISTER (before mod-ID injection/player initialization), not PlayerLoggedIn.
 * Four <=20-character channel names carry the entire SHA-256 without a custom packet or tick queue.
 * This compatibility boundary can be removed once no persisted numeric GTSR identities remain.
 */
public final class MaterialMappingHandshake {

    private static Set<String> expected;

    private MaterialMappingHandshake() {}

    public static synchronized void register() {
        if (expected != null) return;
        expected = tokens(MaterialIdRegistration.startupIdentity().fingerprint);
        for (String name : expected) NetworkRegistry.INSTANCE.newEventDrivenChannel(name);
        FMLCommonHandler.instance()
            .bus()
            .register(new MaterialMappingHandshake());
    }

    public static Set<String> tokens(String fingerprint) {
        if (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Expected a complete material SHA-256");
        Set<String> result = new LinkedHashSet<>();
        for (int part = 0; part < 4; part++)
            result.add("gi" + part + ":" + fingerprint.substring(part * 16, part * 16 + 16));
        return java.util.Collections.unmodifiableSet(result);
    }

    public static boolean matches(Set<String> remote, String fingerprint) {
        Set<String> actual = new LinkedHashSet<>();
        for (String name : remote) if (name.matches("gi[0-3]:.*")) actual.add(name);
        return actual.equals(tokens(fingerprint));
    }

    @SubscribeEvent
    public void onRegistration(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
        // FML emits this complete salutation on both sides before its mod-list/ID handshake.
        // Later incremental REGISTER/UNREGISTER messages are not initial admission evidence.
        if (!"REGISTER".equals(event.operation) || !event.registrations.contains("FML|HS")) return;
        if (matches(event.registrations, MaterialIdRegistration.startupIdentity().fingerprint)) return;
        NetworkDispatcher dispatcher = NetworkDispatcher.get(event.manager);
        if (dispatcher == null) throw new IllegalStateException("Missing FML dispatcher during material admission");
        dispatcher.rejectHandshake(
            "GTSR material mapping differs (or peer lacks v2 admission). Use the server's matching "
                + "industrial-material-ids-v2.cfg and original legacy cfg, then restart. "
                + "Do not delete existing mappings or hot-remap saved items.");
    }
}
