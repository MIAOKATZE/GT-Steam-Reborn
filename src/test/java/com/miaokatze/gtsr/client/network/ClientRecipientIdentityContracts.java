// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.network;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.network.NetworkManager;

import com.mojang.authlib.GameProfile;

/** Offline identity/lifecycle contracts: no Minecraft singleton, GL, server or live connection is started. */
public final class ClientRecipientIdentityContracts {

    private static int checks;

    private ClientRecipientIdentityContracts() {}

    private static void check(boolean accepted, String name) {
        if (!accepted) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        NetworkManager manager = (NetworkManager) unsafe.allocateInstance(NetworkManager.class);
        NetworkManager absent = (NetworkManager) unsafe.allocateInstance(NetworkManager.class);
        Method lookup = ClientRecipientIdentity.class.getDeclaredMethod("login", NetworkManager.class);
        lookup.setAccessible(true);
        Object handler = new Object(), world = new Object(), player = new Object();
        UUID sessionId = UUID.fromString("4eb427f7-d481-4c1c-a412-718224f6c287");
        UUID offlineId = UUID.nameUUIDFromBytes("OfflinePlayer:IdentityFixture".getBytes(StandardCharsets.UTF_8));
        check(!sessionId.equals(offlineId), "offline fixture really has different launcher and server UUIDs");

        ClientRecipientIdentity.recordLogin(manager, new GameProfile(sessionId, "IdentityFixture"));
        Object onlineEpoch = lookup.invoke(null, manager);
        ClientRecipientIdentity.Audience online = audience(handler, world, player, onlineEpoch, sessionId);
        check(
            online.matches(handler, world, player, onlineEpoch, 541, sessionId, true),
            "online matching UUID accepted");

        ClientRecipientIdentity.recordLogin(manager, new GameProfile(offlineId, "IdentityFixture"));
        Object epoch = lookup.invoke(null, manager);
        Field serverId = epoch.getClass()
            .getDeclaredField("player");
        serverId.setAccessible(true);
        check(offlineId.equals(serverId.get(epoch)), "registry stores the real S02 GameProfile UUID");
        ClientRecipientIdentity.Audience offline = audience(handler, world, player, epoch, offlineId);
        check(
            offline.matches(handler, world, player, epoch, 541, offlineId, true),
            "offline server recipient accepted despite different launcher Session UUID");
        check(
            !audience(handler, world, player, epoch, sessionId)
                .matches(handler, world, player, epoch, 541, offlineId, true),
            "the local Session UUID is not an alternate packet recipient");
        check(
            !audience(handler, world, player, epoch, UUID.randomUUID())
                .matches(handler, world, player, epoch, 541, offlineId, true),
            "another player's recipient UUID rejected");
        check(
            !audience(handler, world, player, epoch, null).matches(handler, world, player, epoch, 541, offlineId, true),
            "missing recipient rejected");
        check(lookup.invoke(null, absent) == null, "uncaptured connection has no authority");
        check(
            !audience(handler, world, player, null, offlineId)
                .matches(handler, world, player, null, 541, offlineId, true),
            "missing login capture fails closed");
        check(!offline.matches(handler, world, player, epoch, 541, offlineId, false), "closed connection rejected");
        check(!offline.matches(null, world, player, epoch, 541, offlineId, true), "disconnected client rejected");
        check(
            !offline.matches(new Object(), world, player, epoch, 541, offlineId, true),
            "old-channel callback rejected after reconnect with the same UUID");
        check(
            !offline.matches(handler, new Object(), player, epoch, 541, offlineId, true),
            "old-world callback rejected even in the same dimension");
        check(
            !offline.matches(handler, world, new Object(), epoch, 541, offlineId, true),
            "replaced player object rejected after respawn");
        check(!offline.matches(handler, world, player, epoch, 0, offlineId, true), "wrong dimension rejected");
        ClientRecipientIdentity.recordLogin(manager, new GameProfile(offlineId, "IdentityFixture"));
        Object newEpoch = lookup.invoke(null, manager);
        check(newEpoch != epoch, "a new login always creates a distinct epoch even with the same UUID");
        check(
            !offline.matches(handler, world, player, newEpoch, 541, offlineId, true),
            "queued effect cannot cross a new login epoch on a reused connection object");
        check(
            audience(handler, world, player, newEpoch, offlineId)
                .matches(handler, world, player, newEpoch, 541, offlineId, true),
            "new login retains its own valid recipient");
        ClientRecipientIdentity.recordLogin(manager, new GameProfile(null, "IdentityFixture"));
        check(lookup.invoke(null, manager) == null, "incomplete login profile removes prior identity");
        ClientRecipientIdentity.recordLogin(manager, null);
        check(lookup.invoke(null, manager) == null, "null profile does not resurrect identity");
        Field registry = ClientRecipientIdentity.class.getDeclaredField("LOGINS");
        registry.setAccessible(true);
        check(registry.get(null) instanceof WeakHashMap, "registry does not retain disconnected NetworkManagers");
        for (Field member : epoch.getClass()
            .getDeclaredFields())
            check(member.getType() == UUID.class, "login epoch contains no back-reference to its weak key");
        System.out.println("PASS client recipient identity contracts: " + checks);
    }

    private static ClientRecipientIdentity.Audience audience(Object handler, Object world, Object player, Object epoch,
        UUID recipient) {
        return new ClientRecipientIdentity.Audience(handler, world, player, epoch, 541, recipient);
    }
}
