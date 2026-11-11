// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.weapons;

public class Snapshot {

    public int dimension, entityId, kind, magazine, reserve, reloadTicks, reloadDuration, shotSerial;
    public int ammoType, remoteAmmoType, remoteTicks;
    public int mode, chargeTicks, chargeDuration;
    public int shotInterval, shotCooldown, shotAge;
    public float heat, spin;
    public boolean focusing, overheated;
}
