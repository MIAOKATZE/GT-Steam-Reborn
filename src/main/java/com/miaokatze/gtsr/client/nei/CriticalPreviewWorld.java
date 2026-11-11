// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.nei;

/** Optional preview-world marker; contains no client or BlockRenderer types. */
public interface CriticalPreviewWorld {

    void gtsr$markCriticalPreview();

    boolean gtsr$isCriticalPreview();

    long gtsr$previewRevision();
}
