// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine;

import net.minecraft.nbt.NBTTagCompound;

/** Per-machine confirmed thermal reserves. Only a fully paid pair authorizes one thermal tick. */
public final class AmmoniaThermalPayment {

    public interface Port {

        boolean available(int amount);

        void debit(int amount, java.util.function.IntConsumer confirmed);
    }

    private long gas, steam;
    private boolean uncertain, waiting;
    private String debitResource = "";
    private int debitAmount;
    private net.minecraft.nbt.NBTBase quarantine;

    public long gasCredit() {
        return gas;
    }

    public long steamCredit() {
        return steam;
    }

    public int status() {
        return uncertain ? 2 : waiting || gas > 0 || steam > 0 ? 1 : 0;
    }

    public boolean available(int gasAmount, int steamAmount, Port gasPort, Port steamPort) {
        if (uncertain || gasAmount < 0 || steamAmount < 0) return false;
        int missingGas = (int) Math.max(0L, gasAmount - gas);
        int missingSteam = (int) Math.max(0L, steamAmount - steam);
        return (missingGas == 0 || gasPort.available(missingGas))
            && (missingSteam == 0 || steamPort.available(missingSteam));
    }

    /** Prepare both deficits before any external debit. Confirmed short payments are never discarded or replayed. */
    public boolean pay(int gasAmount, int steamAmount, Port gasPort, Port steamPort, Runnable checkpoint) {
        if (!available(gasAmount, steamAmount, gasPort, steamPort)) {
            waiting = true;
            return false;
        }
        if (!fund(true, (int) Math.max(0L, gasAmount - gas), gasPort, checkpoint)
            || !fund(false, (int) Math.max(0L, steamAmount - steam), steamPort, checkpoint)) return false;
        // No external calls between retiring the two confirmed credits. One pair buys exactly one tick.
        gas -= gasAmount;
        steam -= steamAmount;
        waiting = false;
        checkpoint.run();
        return true;
    }

    private boolean fund(boolean fuel, int missing, Port port, Runnable checkpoint) {
        if (missing == 0) return true;
        uncertain = true;
        debitResource = fuel ? "refinerygas" : "steam";
        debitAmount = missing;
        checkpoint.run();
        int[] paid = { 0 };
        try {
            port.debit(missing, receipt -> {
                if (receipt < 0 || receipt > missing - paid[0])
                    throw new IllegalStateException("Invalid thermal debit receipt");
                paid[0] += receipt;
                if (fuel) gas += receipt;
                else steam += receipt;
                debitAmount = missing - paid[0];
                checkpoint.run();
            });
        } catch (RuntimeException failure) {
            waiting = true;
            checkpoint.run();
            return false;
        }
        uncertain = false;
        debitResource = "";
        debitAmount = 0;
        waiting = paid[0] < missing;
        checkpoint.run();
        return !waiting;
    }

    public void write(NBTTagCompound root) {
        if (quarantine != null) {
            root.setTag("gtsrAmmoniaThermalPayment", quarantine.copy());
            return;
        }
        NBTTagCompound record = new NBTTagCompound();
        record.setInteger("schema", 1);
        record.setLong("gas", gas);
        record.setLong("steam", steam);
        record.setBoolean("uncertain", uncertain);
        record.setString("debitResource", debitResource);
        record.setInteger("debitAmount", debitAmount);
        root.setTag("gtsrAmmoniaThermalPayment", record);
    }

    public void read(NBTTagCompound root) {
        gas = steam = 0;
        uncertain = waiting = false;
        debitResource = "";
        debitAmount = 0;
        quarantine = null;
        if (!root.hasKey("gtsrAmmoniaThermalPayment")) return;
        if (!root.hasKey("gtsrAmmoniaThermalPayment", 10)) {
            quarantine = root.getTag("gtsrAmmoniaThermalPayment")
                .copy();
            uncertain = true;
            return;
        }
        NBTTagCompound record = root.getCompoundTag("gtsrAmmoniaThermalPayment");
        if (record.getInteger("schema") != 1 || !record.hasKey("gas", 4)
            || !record.hasKey("steam", 4)
            || record.getLong("gas") < 0
            || record.getLong("steam") < 0
            || record.getLong("gas") > Integer.MAX_VALUE
            || record.getLong("steam") > Integer.MAX_VALUE) {
            quarantine = root.getTag("gtsrAmmoniaThermalPayment")
                .copy();
            uncertain = true;
            return;
        }
        gas = record.getLong("gas");
        steam = record.getLong("steam");
        uncertain = record.getBoolean("uncertain");
        debitResource = record.getString("debitResource");
        debitAmount = record.getInteger("debitAmount");
    }
}
