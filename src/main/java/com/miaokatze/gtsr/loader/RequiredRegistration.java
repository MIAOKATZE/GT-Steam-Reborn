// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader;

/** Required content cannot leave startup running against a partially populated item or machine catalog. */
public final class RequiredRegistration {

    private RequiredRegistration() {}

    public static void run(String phase, String group, Runnable registration) {
        try {
            registration.run();
        } catch (RuntimeException | LinkageError cause) {
            throw new IllegalStateException(
                "[GTSR] Required registration failed: phase=" + phase + ", group=" + group,
                cause);
        }
    }
}
