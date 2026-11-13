// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.common.dimension.testlab;

import java.nio.file.Path;

/** Explicit proximity entry point sharing the legacy native turbine fixture lifecycle. */
public final class MegaTurbineProximityFixture extends MegaTurbineProfileFixture {

    public interface Hooks extends MegaTurbineProfileFixture.Hooks {
    }

    public MegaTurbineProximityFixture(Path plan, Hooks hooks) throws Exception {
        super(plan, hooks);
    }
}
