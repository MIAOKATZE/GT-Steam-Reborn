// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.bootstrap;

/** Frozen profiles are exact matches; nearby, decorated and missing versions stay unverified. */
public final class GTNHCompatibilityBootstrapContract {

    public static void main(String[] args) {
        require("2.9.0".equals(GTNHCompatibilityBootstrap.profileFor("5.09.54.265")));
        require("2.9.1".equals(GTNHCompatibilityBootstrap.profileFor("5.09.54.275")));
        require("2.9.2".equals(GTNHCompatibilityBootstrap.profileFor("5.09.54.277")));
        for (String unknown : new String[] { null, "", "missing", "5.09.54.264", "5.09.54.278", "5.09.54.277-dirty" }) {
            require(GTNHCompatibilityBootstrap.profileFor(unknown) == null);
        }
        System.out.println("GTNH bootstrap profiles: 9 checks PASS");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected GTNH compatibility profile");
    }
}
