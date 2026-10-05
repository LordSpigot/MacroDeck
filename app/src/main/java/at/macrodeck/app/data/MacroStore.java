package at.macrodeck.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import at.macrodeck.app.model.Macro;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MacroStore {
    private static final String PREFS = "macrodeck";
    private static final String KEY = "macros";

    public static List<Macro> load(Context context) {
        List<Macro> out = new ArrayList<>();
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray a = new JSONArray(p.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Macro.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public static void save(Context context, List<Macro> macros) {
        JSONArray a = new JSONArray();
        try {
            for (Macro m : macros) a.put(m.toJson());
        } catch (Exception ignored) {}
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply();
    }

    public static Macro find(Context context, String id) {
        for (Macro m : load(context)) if (m.id.equals(id)) return m;
        return null;
    }

    public static void upsert(Context context, Macro macro) {
        List<Macro> list = load(context);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(macro.id)) { list.set(i, macro); found = true; break; }
        }
        if (!found) list.add(0, macro);
        save(context, list);
    }

    public static void delete(Context context, String id) {
        List<Macro> list = load(context);
        list.removeIf(m -> m.id.equals(id));
        save(context, list);
    }
}
