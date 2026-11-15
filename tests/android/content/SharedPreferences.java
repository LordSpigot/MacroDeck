package android.content;

public interface SharedPreferences {
    int getInt(String key, int defValue);
    long getLong(String key, long defValue);
    boolean getBoolean(String key, boolean defValue);
    Editor edit();

    interface Editor {
        Editor putInt(String key, int value);
        Editor putLong(String key, long value);
        Editor putBoolean(String key, boolean value);
        Editor remove(String key);
        boolean commit();
    }
}
