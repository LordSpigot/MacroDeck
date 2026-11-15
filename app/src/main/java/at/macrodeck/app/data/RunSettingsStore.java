package at.macrodeck.app.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent execution options scoped to each macro ID, not the overlay lifetime. */
public final class RunSettingsStore {
    private static final String PREFS = "macrodeck_run_settings";
    private RunSettingsStore() {}

    public static final class Options {
        public final int repeatCount; // 0 = endless
        public final long pauseMs;
        public final boolean avoidRelaunch;

        public Options(int repeatCount, long pauseMs, boolean avoidRelaunch) {
            this.repeatCount = validCount(repeatCount) ? repeatCount : 1;
            this.pauseMs = Math.max(0L, Math.min(30000L, pauseMs));
            this.avoidRelaunch = avoidRelaunch;
        }
    }

    private static boolean validCount(int count) {
        return count == 0 || count == 1 || count == 5 || count == 10 || count == 25;
    }

    private static String key(String id, String setting) {
        return id + "." + setting;
    }

    public static Options load(Context context, String macroId) {
        if (macroId == null || macroId.isEmpty()) return new Options(1, 1500L, true);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            return new Options(prefs.getInt(key(macroId, "repeat"), 1),
                    prefs.getLong(key(macroId, "pause"), 1500L),
                    prefs.getBoolean(key(macroId, "smart_launch"), true));
        } catch (ClassCastException ignored) {
            // Earlier or damaged settings must not prevent a macro from opening.
            return new Options(1, 1500L, true);
        }
    }

    /** commit() returns only once the change is written to durable storage. */
    public static boolean save(Context context, String macroId, Options options) {
        if (macroId == null || macroId.isEmpty() || options == null) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(key(macroId, "repeat"), options.repeatCount)
                .putLong(key(macroId, "pause"), options.pauseMs)
                .putBoolean(key(macroId, "smart_launch"), options.avoidRelaunch)
                .commit();
    }

    public static boolean copy(Context context, String sourceId, String destinationId) {
        return save(context, destinationId, load(context, sourceId));
    }

    public static boolean delete(Context context, String macroId) {
        if (macroId == null || macroId.isEmpty()) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(key(macroId, "repeat"))
                .remove(key(macroId, "pause"))
                .remove(key(macroId, "smart_launch"))
                .commit();
    }

    public static String summary(Options options) {
        String count = options.repeatCount == 0 ? "∞" : options.repeatCount + "×";
        String pause = options.pauseMs % 1000L == 0
                ? (options.pauseMs / 1000L) + " s"
                : String.format(java.util.Locale.GERMAN, "%.1f s", options.pauseMs / 1000d);
        return count + " · " + pause + " Pause";
    }
}
