package at.macrodeck.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import at.macrodeck.app.model.Macro;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Stores all macros atomically in one private SharedPreferences value. */
public final class MacroStore {
    private static final String TAG = "MacroDeck/MacroStore";
    private static final String PREFS = "macrodeck"; // keep existing storage for upgrade compatibility
    private static final String KEY = "macros";
    private static final String LAST_GOOD = "macros_previous";
    private MacroStore() {}

    public static synchronized List<Macro> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            return parse(prefs.getString(KEY, "[]"));
        } catch (Exception ex) {
            Log.e(TAG, "Primary macro data unavailable; trying previous version", ex);
            try {
                return parse(prefs.getString(LAST_GOOD, "[]"));
            } catch (Exception ex2) {
                Log.e(TAG, "Previous macro data also unavailable", ex2);
                return new ArrayList<>();
            }
        }
    }

    private static List<Macro> parse(String serialized) throws Exception {
        JSONArray entries = new JSONArray(serialized);
        List<Macro> result = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            try {
                result.add(Macro.fromJson(entries.getJSONObject(i)));
            } catch (Exception invalidItem) {
                // Never lose all other macros because one entry was malformed.
                Log.w(TAG, "Skipping invalid macro at position " + i, invalidItem);
            }
        }
        return result;
    }

    public static synchronized boolean save(Context context, List<Macro> macros) {
        try {
            JSONArray entries = new JSONArray();
            for (Macro macro : macros) entries.put(macro.toJson());
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String previous = prefs.getString(KEY, "[]");
            SharedPreferences.Editor editor = prefs.edit().putString(KEY, entries.toString());
            // Previous known good value offers a fallback if data gets damaged.
            try { new JSONArray(previous); editor.putString(LAST_GOOD, previous); }
            catch (Exception invalidPrevious) { Log.w(TAG, "Existing data is invalid; preserving prior backup"); }
            return editor.commit();
        } catch (Exception ex) {
            Log.e(TAG, "Could not serialize/save macros", ex);
            return false;
        }
    }

    public static synchronized Macro find(Context context, String id) {
        for (Macro m : load(context)) if (m.id.equals(id)) return m;
        return null;
    }

    public static synchronized boolean upsert(Context context, Macro macro) {
        List<Macro> list = load(context);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(macro.id)) { list.set(i, macro); found = true; break; }
        }
        if (!found) list.add(0, macro);
        return save(context, list);
    }

    public static synchronized boolean delete(Context context, String id) {
        List<Macro> list = load(context);
        list.removeIf(m -> m.id.equals(id));
        return save(context, list);
    }
}
