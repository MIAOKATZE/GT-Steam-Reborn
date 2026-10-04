package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Separate bounded scene snapshot; kind 0 leaves, 1 native tree, 2 factory, 3 foundry. */
public final class SceneBossSignal implements IMessage {

    public int dimension, kind, state, remaining, total, revivalTicks, entity = -1;
    public UUID player;
    public float health, maxHealth;
    public String site = "", code = "";
    public boolean valid;
    /** Client-only clock and floor; neither changes the authoritative network snapshot. */
    public transient float renderRevivalTicks = -1, minimumVisibleHealth;

    public boolean sane() {
        return player != null && kind >= 0
            && kind <= 3
            && state >= 0
            && state <= 3
            && total >= 0
            && total <= 4096
            && remaining >= 0
            && remaining <= total
            && revivalTicks >= 0
            && revivalTicks <= 208
            && entity >= -1
            && Float.isFinite(health)
            && Float.isFinite(maxHealth)
            && health >= 0
            && maxHealth >= 0
            && maxHealth <= 100000
            && health <= maxHealth
            && site.matches("[A-Za-z0-9:_-]{0,192}")
            && (kind == 0 ? site.isEmpty() && code.isEmpty() && total == 0 && health == 0
                : !site.isEmpty() && maxHealth > 0 && code.equals(kind == 1 ? "dc-10" : kind == 2 ? "dc-08" : "dc-02"));
    }

    @Override
    public void fromBytes(ByteBuf b) {
        valid = false;
        if (b.readableBytes() < 48 || b.readableBytes() > 248) return;
        dimension = b.readInt();
        player = new UUID(b.readLong(), b.readLong());
        kind = b.readUnsignedByte();
        state = b.readUnsignedByte();
        remaining = b.readInt();
        total = b.readInt();
        revivalTicks = b.readInt();
        entity = b.readInt();
        health = b.readFloat();
        maxHealth = b.readFloat();
        int n = b.readUnsignedByte();
        if (n > 192 || b.readableBytes() < n + 1) return;
        byte[] text = new byte[n];
        b.readBytes(text);
        site = new String(text, StandardCharsets.US_ASCII);
        n = b.readUnsignedByte();
        if (n > 8 || b.readableBytes() != n) return;
        text = new byte[n];
        b.readBytes(text);
        code = new String(text, StandardCharsets.US_ASCII);
        valid = sane();
    }

    @Override
    public void toBytes(ByteBuf b) {
        b.writeInt(dimension);
        b.writeLong(player.getMostSignificantBits());
        b.writeLong(player.getLeastSignificantBits());
        b.writeByte(kind);
        b.writeByte(state);
        b.writeInt(remaining);
        b.writeInt(total);
        b.writeInt(revivalTicks);
        b.writeInt(entity);
        b.writeFloat(health);
        b.writeFloat(maxHealth);
        byte[] text = site.getBytes(StandardCharsets.US_ASCII);
        b.writeByte(text.length);
        b.writeBytes(text);
        text = code.getBytes(StandardCharsets.US_ASCII);
        b.writeByte(text.length);
        b.writeBytes(text);
    }

    public float visibleHealth() {
        return state == 0 || state == 3 ? 0 : health;
    }

    public float visibleHealth(float partialTicks) {
        if (state != 1 || renderRevivalTicks < 0) return visibleHealth();
        float partial = Float.isFinite(partialTicks) ? Math.max(0, Math.min(1, partialTicks)) : 0;
        float age = Math.min(revivalDuration(), Math.min(revivalTicks + 10, renderRevivalTicks + partial));
        float predicted = 1 + (maxHealth - 1) * age / revivalDuration();
        minimumVisibleHealth = Math.min(maxHealth, Math.max(minimumVisibleHealth, predicted));
        return minimumVisibleHealth;
    }

    public int revivalDuration() {
        return kind == 1 ? 200 : 208;
    }

    public int layers() {
        return (int) Math.ceil(visibleHealth() / 100F);
    }

    public int layers(float partialTicks) {
        return (int) Math.ceil(visibleHealth(partialTicks) / 100F);
    }

    public float segment() {
        int n = layers();
        return n == 0 ? 0 : (visibleHealth() - (n - 1) * 100) / 100F;
    }

    public float segment(float partialTicks) {
        float visible = visibleHealth(partialTicks);
        int n = (int) Math.ceil(visible / 100F);
        return n == 0 ? 0 : (visible - (n - 1) * 100) / 100F;
    }
}
