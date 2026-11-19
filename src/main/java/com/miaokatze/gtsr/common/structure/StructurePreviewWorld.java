// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.structure;

/** Optional preview-world marker; contains no client or BlockRenderer types. */
public interface StructurePreviewWorld {

    void gtsr$markStructurePreview();

    boolean gtsr$isStructurePreview();

    long gtsr$previewRevision();
}
