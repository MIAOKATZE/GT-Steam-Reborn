// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.machinevisual;

/** Client chunk-local revisions for machine-motion clearance. Lighting does not change occupancy. */
public interface MotionSectionChanges {

    long gtsr$motionRevision(int section);
}
