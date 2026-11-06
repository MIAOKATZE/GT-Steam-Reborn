package com.miaokatze.gtsr.common.machinevisual;
import net.minecraft.entity.player.EntityPlayerMP;
/** Detached network boundary: the real observer runs, without opening FML channels. */
public final class MachineVisualNetwork {
    static long session(){return 1;}
    static boolean canObserve(EntityPlayerMP player,MachineVisualHost host){throw new AssertionError("No observers in lifecycle test");}
    static void send(EntityPlayerMP player,MachineVisualSnapshot snapshot){throw new AssertionError("No network in lifecycle test");}
}
