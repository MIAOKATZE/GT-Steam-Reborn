package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.Iterator;

import net.minecraft.nbt.NBTTagCompound;

/** Only outstanding admissions need an identity; a consumed admission cannot die twice. */
final class RemasterSpawnerLedger {

    private RemasterSpawnerLedger() {}

    /** Retain unloaded survivors and cumulative counters; remove only completed legacy identities. */
    static boolean compact(NBTTagCompound site, NBTTagCompound spawner, String cage) {
        if (spawner.getInteger("pendingLedgerVersion") >= 1) return false;
        String admitted = "spawner-admitted:" + cage + ":", dead = "spawner-dead:" + cage + ":";
        // Two passes avoid both iterator invalidation and dependence on NBT map iteration order.
        for (Iterator<?> keys = site.func_150296_c().iterator(); keys.hasNext();) {
            String key = (String) keys.next();
            if (key.startsWith(admitted) && serial(key.substring(admitted.length()))
                && site.getBoolean(dead + key.substring(admitted.length()))) keys.remove();
        }
        for (Iterator<?> keys = site.func_150296_c().iterator(); keys.hasNext();) {
            String key = (String) keys.next();
            if (key.startsWith(dead) && serial(key.substring(dead.length())) && site.getBoolean(key)) keys.remove();
        }
        spawner.setInteger("pendingLedgerVersion", 1);
        return true;
    }

    static boolean died(NBTTagCompound site, NBTTagCompound spawner, String cage, int serial) {
        if (serial < 0) return false;
        String member = cage + ":" + serial, admitted = "spawner-admitted:" + member;
        if (!site.getBoolean(admitted) || site.getBoolean("spawner-dead:" + member)) return false;
        site.removeTag(admitted);
        spawner.setInteger("deadCount", spawner.getInteger("deadCount") + 1);
        return true;
    }

    private static boolean serial(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 && Integer.toString(parsed).equals(value);
        } catch (NumberFormatException invalid) {
            return false;
        }
    }
}
