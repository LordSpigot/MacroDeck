package at.macrodeck.app.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, int dp) { return Math.round(dp * c.getResources().getDisplayMetrics().density); }
    public static boolean dark(Context c) { return true; }
    public static int bg(Context c) { return Color.BLACK; }
    public static int card(Context c) { return Color.rgb(14,14,14); }
    public static int text(Context c) { return Color.rgb(248,248,250); }
    public static int muted(Context c) { return Color.rgb(170,170,178); }
    public static int brand() { return Color.rgb(91,92,226); }
    public static int danger() { return Color.rgb(220,65,78); }

    public static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, (int) radiusDp)); return g;
    }

    public static TextView title(Context c, String s, int sp) {
        TextView v = new TextView(c); v.setText(s); v.setTextColor(text(c)); v.setTextSize(sp); v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }

    public static TextView body(Context c, String s, int sp) {
        TextView v = new TextView(c); v.setText(s); v.setTextColor(muted(c)); v.setTextSize(sp); return v;
    }

    public static Button button(Context c, String s, int color) {
        Button b = new Button(c); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setBackground(rounded(color, 16, c));
        b.setPadding(dp(c,16), dp(c,8), dp(c,16), dp(c,8)); return b;
    }

    public static void cardify(View v, Context c) {
        v.setBackground(rounded(card(c), 22, c)); v.setElevation(dp(c, 2));
    }
}
