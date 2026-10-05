package at.macrodeck.app.model;

import org.json.JSONException;
import org.json.JSONObject;

public class MacroStep {
    public enum Type { TAP, LONG_PRESS, SWIPE, DELAY, INPUT_TEXT, LAUNCH_APP, BACK, HOME }

    public Type type;
    public float x1, y1, x2, y2;
    public long durationMs;
    public String text;

    public MacroStep(Type type) {
        this.type = type;
        this.durationMs = defaultDuration(type);
        this.text = "";
    }

    private static long defaultDuration(Type type) {
        if (type == Type.DELAY) return 1000;
        if (type == Type.LONG_PRESS) return 800;
        if (type == Type.SWIPE) return 450;
        return 0;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", type.name());
        o.put("x1", x1); o.put("y1", y1); o.put("x2", x2); o.put("y2", y2);
        o.put("durationMs", durationMs);
        o.put("text", text == null ? "" : text);
        return o;
    }

    public static MacroStep fromJson(JSONObject o) throws JSONException {
        MacroStep s = new MacroStep(Type.valueOf(o.getString("type")));
        s.x1 = (float) o.optDouble("x1", 0); s.y1 = (float) o.optDouble("y1", 0);
        s.x2 = (float) o.optDouble("x2", 0); s.y2 = (float) o.optDouble("y2", 0);
        s.durationMs = o.optLong("durationMs", defaultDuration(s.type));
        s.text = o.optString("text", "");
        return s;
    }

    public String summary() {
        switch (type) {
            case TAP: return "Tippen · " + Math.round(x1) + ", " + Math.round(y1);
            case LONG_PRESS: return "Langer Tap · " + Math.round(x1) + ", " + Math.round(y1) + " · " + durationMs + " ms";
            case SWIPE: return "Wischen · " + Math.round(x1) + "," + Math.round(y1) + " → " + Math.round(x2) + "," + Math.round(y2);
            case DELAY: return "Warten · " + durationMs + " ms";
            case INPUT_TEXT: return "Text eingeben · " + text;
            case LAUNCH_APP: return "App starten · " + text;
            case BACK: return "Zurück";
            case HOME: return "Home";
            default: return type.name();
        }
    }
}
