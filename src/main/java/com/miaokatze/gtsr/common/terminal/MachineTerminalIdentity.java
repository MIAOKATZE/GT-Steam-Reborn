package com.miaokatze.gtsr.common.terminal;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/** Applied only to GT machine bases. The identity is persisted in world NBT, never machine-item drops. */
public interface MachineTerminalIdentity {

    String gtsr$terminalTargetIdentity();

    /** The exact world-NBT codec used by the injected base-tile lifecycle. */
    final class State {

        private static final String KEY = "gtsr.terminalTargetId";
        private String value = "";

        public boolean initialized() {
            return !value.isEmpty();
        }

        public String identity() {
            if (value.isEmpty()) value = UUID.randomUUID()
                .toString();
            return value;
        }

        public void write(NBTTagCompound tag) {
            if (!value.isEmpty()) tag.setString(KEY, value);
        }

        public void read(NBTTagCompound tag) {
            String saved = tag.getString(KEY);
            try {
                value = saved.length() == 36 ? UUID.fromString(saved)
                    .toString() : "";
            } catch (IllegalArgumentException invalid) {
                value = "";
            }
        }
    }
}
