package at.macrodeck.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Macro {
    public String id = UUID.randomUUID().toString();
    public String name = "Neues Makro";
    public String description = "";
    public boolean enabled = true;
    public long lastRun = 0;
    public final List<MacroStep> steps = new ArrayList<>();

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id); o.put("name", name); o.put("description", description);
        o.put("enabled", enabled); o.put("lastRun", lastRun);
        JSONArray a = new JSONArray();
        for (MacroStep s : steps) a.put(s.toJson());
        o.put("steps", a);
        return o;
    }

    public static Macro fromJson(JSONObject o) throws JSONException {
        Macro m = new Macro();
        m.id = o.optString("id", UUID.randomUUID().toString());
        m.name = o.optString("name", "Makro");
        m.description = o.optString("description", "");
        m.enabled = o.optBoolean("enabled", true);
        m.lastRun = o.optLong("lastRun", 0);
        JSONArray a = o.optJSONArray("steps");
        if (a != null) for (int i = 0; i < a.length(); i++) m.steps.add(MacroStep.fromJson(a.getJSONObject(i)));
        return m;
    }

    public Macro copy() {
        try {
            Macro c = fromJson(toJson());
            c.id = UUID.randomUUID().toString();
            c.name = name + " – Kopie";
            c.lastRun = 0;
            return c;
        } catch (JSONException e) {
            Macro c = new Macro(); c.name = name + " – Kopie"; return c;
        }
    }
}
