package com.miaokatze.gtsr.common.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.PosGuiData;

/** Remote coordinates are presentation data. Only a non-serialized server grant authorizes model access. */
public class RemoteTerminalGuiData extends PosGuiData {

    public final boolean remote;
    public final int targetDimension;
    public final MachineTerminalHistory.Kind terminalKind;
    private final MachineTerminalHistory.Grant grant;

    public RemoteTerminalGuiData(EntityPlayer player, int x, int y, int z) {
        super(player, x, y, z);
        remote = false;
        targetDimension = player.dimension;
        terminalKind = null;
        grant = null;
    }

    RemoteTerminalGuiData(EntityPlayer player, int x, int y, int z, int dimension, MachineTerminalHistory.Kind kind,
        MachineTerminalHistory.Grant grant) {
        super(player, x, y, z);
        remote = true;
        targetDimension = dimension;
        terminalKind = kind;
        this.grant = grant;
    }

    protected RemoteTerminalGuiData(RemoteTerminalGuiData source) {
        super(source.getPlayer(), source.getX(), source.getY(), source.getZ());
        remote = source.remote;
        targetDimension = source.targetDimension;
        terminalKind = source.terminalKind;
        grant = source.grant;
    }

    @Override
    public World getWorld() {
        return remote && grant != null ? grant.base.getWorld() : super.getWorld();
    }

    @Override
    public TileEntity getTileEntity() {
        if (!remote) return super.getTileEntity();
        // Client targets can be in another dimension or outside render distance; never consult the client world.
        return grant == null ? null : (TileEntity) MachineTerminalHistory.resolve(grant.entry);
    }

    public boolean canUseRemote(EntityPlayer player) {
        return player == getPlayer() && !player.isDead
            && remote
            && (player.worldObj.isRemote || grant != null && grant.valid(player));
    }
}
