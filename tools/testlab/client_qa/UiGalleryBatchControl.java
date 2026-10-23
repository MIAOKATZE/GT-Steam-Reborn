package audit;

/** Pure lifecycle decision used before all client-stage early returns. */
final class UiGalleryBatchControl {
    static final int NONE = 0, CLEANUP = 1, STOP_WITHOUT_SERVER = 2, FAIL_SERVER_STOPPED = 3;

    static int decide(int stage, boolean stopRequested, boolean serverStopped, boolean cleaned, boolean canCleanup) {
        if (serverStopped && (stage != 90 || !cleaned)) return FAIL_SERVER_STOPPED;
        if (stage == 90 && !cleaned && !canCleanup) return FAIL_SERVER_STOPPED;
        if (stopRequested && stage != 90) return canCleanup ? CLEANUP : STOP_WITHOUT_SERVER;
        return NONE;
    }

    private UiGalleryBatchControl() {}
}
