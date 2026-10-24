package com.miaokatze.gtsr.common.critical;

/** Read-only visual snapshot. It owns neither a world nor a controller and cannot advance construction. */
public final class CriticalPreviewDescriptor {

    public final CriticalLayoutDefinition layout;
    public final CriticalConfiguration configuration;
    public final int completedStage, currentStage;
    public final double stageProgress;
    public final boolean demonstration, paused;

    private CriticalPreviewDescriptor(CriticalLayoutDefinition layout, CriticalConfiguration configuration,
        int completedStage, int currentStage, double stageProgress, boolean demonstration, boolean paused) {
        if (layout == null || configuration == null) throw new IllegalArgumentException("Missing preview layout");
        this.layout = layout;
        this.configuration = configuration;
        this.completedStage = Math.max(0, Math.min(4, completedStage));
        this.currentStage = Math.max(0, Math.min(4, currentStage));
        this.stageProgress = Double.isFinite(stageProgress) ? Math.max(0, Math.min(1, stageProgress)) : 0;
        this.demonstration = demonstration;
        this.paused = paused;
    }

    public static CriticalPreviewDescriptor demo(CriticalMachineKind kind, CriticalTier tier) {
        CriticalConfiguration config = new CriticalConfiguration(tier, kind, 8, 0, 0);
        return new CriticalPreviewDescriptor(
            CriticalLayoutDefinition.select(2, 31, false, config.tier, kind),
            config,
            4,
            0,
            1,
            true,
            false);
    }

    public static CriticalPreviewDescriptor construction(CriticalLayoutDefinition layout,
        CriticalConfiguration configuration, int completedStage, int currentStage, double progress, boolean paused) {
        return new CriticalPreviewDescriptor(
            layout,
            configuration,
            completedStage,
            currentStage,
            progress,
            false,
            paused);
    }

    /** Progress and visual time deliberately do not create new geometry cache entries. */
    public String geometryKey() {
        return "miniature-v1:" + layout.kind.key;
    }
}
