// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.api.compat;

/** A controller that owns its steam/input/output implementation. */
public interface ISelfManagedSteamMachine {

    boolean gtsr$allowsCoolingRegistration();
}
