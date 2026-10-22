package com.miaokatze.gtsr.client.nei;

/** Optional preview-world marker; contains no client or BlockRenderer types. */
public interface CriticalPreviewWorld {

    void gtsr$markCriticalPreview();

    boolean gtsr$isCriticalPreview();

    long gtsr$previewRevision();
}
