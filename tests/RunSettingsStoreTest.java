import android.content.Context;
import at.macrodeck.app.data.RunSettingsStore;

public class RunSettingsStoreTest {
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Context beforeRestart = new Context();
        RunSettingsStore.Options defaults = RunSettingsStore.load(beforeRestart, "macro-a");
        check(defaults.repeatCount == 1 && defaults.pauseMs == 1500 && defaults.avoidRelaunch,
                "New macros should use defaults");

        check(RunSettingsStore.save(beforeRestart, "macro-a",
                new RunSettingsStore.Options(10, 3500, false)), "Save should succeed");
        Context afterRestart = new Context(); // fresh Context simulates re-creating the Activity/service
        RunSettingsStore.Options restored = RunSettingsStore.load(afterRestart, "macro-a");
        check(restored.repeatCount == 10 && restored.pauseMs == 3500 && !restored.avoidRelaunch,
                "Selection should survive a new context");
        check(RunSettingsStore.load(afterRestart, "macro-b").repeatCount == 1,
                "Options must not leak across macros");

        check(RunSettingsStore.copy(afterRestart, "macro-a", "macro-copy"), "Copy should succeed");
        RunSettingsStore.Options copy = RunSettingsStore.load(afterRestart, "macro-copy");
        check(copy.repeatCount == 10 && copy.pauseMs == 3500 && !copy.avoidRelaunch,
                "Copy should preserve options");
        check(RunSettingsStore.delete(afterRestart, "macro-a"), "Delete should succeed");
        check(RunSettingsStore.load(afterRestart, "macro-a").repeatCount == 1,
                "Deleted macros should have no stale options");
        check(RunSettingsStore.load(afterRestart, "macro-copy").repeatCount == 10,
                "Deleting a macro must not affect a copy");
        check(new RunSettingsStore.Options(-9, 999999, true).repeatCount == 1,
                "Invalid repeat counts must be normalized");
        check(new RunSettingsStore.Options(0, 999999, true).pauseMs == 30000,
                "Pause should be bounded");
        check(new RunSettingsStore.Options(0, -999, true).pauseMs == 0,
                "Negative pause should be normalized");
        System.out.println("PASS: RunSettingsStore persistence, isolation, copy, delete and validation");
    }
}
