package com.miaokatze.gtsr.common.gui.mui2.worker;

import net.minecraft.network.PacketBuffer;

import cpw.mods.fml.common.network.ByteBufUtils;

/** Display data only; it never owns inventory, output buffers, upgrade costs or persisted node state. */
public final class WorkerUiSnapshot {

    public final boolean miner, flag, crushed;
    public final String statusKey, fluidName, title;
    public final int depth, tier, extracted, displayRate;

    public WorkerUiSnapshot(boolean miner, String statusKey, int depth, boolean flag, int tier, boolean crushed,
        int extracted, int displayRate, String fluidName, String title) {
        this.miner = miner;
        this.statusKey = statusKey;
        this.depth = depth;
        this.flag = flag;
        this.tier = tier;
        this.crushed = crushed;
        this.extracted = extracted;
        this.displayRate = displayRate;
        this.fluidName = fluidName;
        this.title = title;
    }

    public void write(PacketBuffer out) {
        out.writeBoolean(miner);
        ByteBufUtils.writeUTF8String(out, statusKey);
        out.writeInt(depth);
        out.writeBoolean(flag);
        out.writeInt(tier);
        out.writeBoolean(crushed);
        out.writeInt(extracted);
        out.writeInt(displayRate);
        ByteBufUtils.writeUTF8String(out, fluidName);
        ByteBufUtils.writeUTF8String(out, title);
    }

    public static WorkerUiSnapshot read(PacketBuffer in) {
        return new WorkerUiSnapshot(
            in.readBoolean(),
            ByteBufUtils.readUTF8String(in),
            in.readInt(),
            in.readBoolean(),
            in.readInt(),
            in.readBoolean(),
            in.readInt(),
            in.readInt(),
            ByteBufUtils.readUTF8String(in),
            ByteBufUtils.readUTF8String(in));
    }
}
