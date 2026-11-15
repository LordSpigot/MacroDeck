package android.content;

import java.util.HashMap;
import java.util.Map;

public class Context {
    public static final int MODE_PRIVATE = 0;
    private static final Map<String, SharedPreferences> PREFS = new HashMap<>();

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return PREFS.computeIfAbsent(name, x -> new FakePrefs());
    }

    private static final class FakePrefs implements SharedPreferences {
        final Map<String, Object> data = new HashMap<>();
        public int getInt(String key, int defValue) { return (Integer) data.getOrDefault(key, defValue); }
        public long getLong(String key, long defValue) { return (Long) data.getOrDefault(key, defValue); }
        public boolean getBoolean(String key, boolean defValue) { return (Boolean) data.getOrDefault(key, defValue); }
        public Editor edit() {
            return new Editor() {
                Map<String, Object> writes = new HashMap<>();
                public Editor putInt(String key, int value) { writes.put(key, value); return this; }
                public Editor putLong(String key, long value) { writes.put(key, value); return this; }
                public Editor putBoolean(String key, boolean value) { writes.put(key, value); return this; }
                public Editor remove(String key) { writes.put(key, null); return this; }
                public boolean commit() {
                    for (Map.Entry<String, Object> entry : writes.entrySet()) {
                        if (entry.getValue() == null) data.remove(entry.getKey());
                        else data.put(entry.getKey(), entry.getValue());
                    }
                    return true;
                }
            };
        }
    }
}
