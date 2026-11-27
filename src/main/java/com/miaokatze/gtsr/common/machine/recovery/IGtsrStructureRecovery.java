// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.recovery;

/** Implemented by the scoped native GT lifecycle mixin. */
public interface IGtsrStructureRecovery {

    GtsrStructureRecovery.State gtsr$getStructureRecovery();
}
