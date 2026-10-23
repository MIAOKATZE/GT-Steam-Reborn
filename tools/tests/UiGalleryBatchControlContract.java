package audit;

/** Actual gallery lifecycle policy, independent of Minecraft startup and server/network availability. */
public final class UiGalleryBatchControlContract {
    private static int checks;

    private static void expect(int expected, int stage, boolean stop, boolean stopped, boolean cleaned, boolean active) {
        checks++;
        int actual = UiGalleryBatchControl.decide(stage, stop, stopped, cleaned, active);
        if (actual != expected) throw new AssertionError("stage=" + stage + " expected=" + expected + " actual=" + actual);
    }

    public static void main(String[] args) {
        for (int stage : new int[] { 0, 1, 2, 11, 18, 20, 21 }) {
            expect(UiGalleryBatchControl.STOP_WITHOUT_SERVER, stage, true, false, false, false);
            expect(UiGalleryBatchControl.CLEANUP, stage, true, false, false, true);
        }
        expect(UiGalleryBatchControl.FAIL_SERVER_STOPPED, 2, false, true, false, false);
        expect(UiGalleryBatchControl.FAIL_SERVER_STOPPED, 20, false, true, false, false);
        expect(UiGalleryBatchControl.FAIL_SERVER_STOPPED, 90, true, true, false, false);
        expect(UiGalleryBatchControl.FAIL_SERVER_STOPPED, 90, true, false, false, false);
        expect(UiGalleryBatchControl.NONE, 90, true, true, true, false);
        expect(UiGalleryBatchControl.NONE, 90, true, false, false, true);
        expect(UiGalleryBatchControl.NONE, 2, false, false, false, false);
        System.out.println("PASS UiGalleryBatchControlContract checks=" + checks + "; game/server NOT_RUN");
    }
}
