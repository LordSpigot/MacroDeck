package at.macrodeck.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayService extends Service {
    public static final String ACTION_CAPTURE = "at.macrodeck.app.CAPTURE";
    public static final String ACTION_STOP = "at.macrodeck.app.STOP";
    public static final String ACTION_HIDE = "at.macrodeck.app.HIDE";
    public static final String BROADCAST_CAPTURED = "at.macrodeck.app.COORDINATE_CAPTURED";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_SLOT = "slot";

    private WindowManager wm;
    private View overlay;

    public static void capture(Context c, int slot) {
        Intent i = new Intent(c, OverlayService.class).setAction(ACTION_CAPTURE).putExtra(EXTRA_SLOT, slot);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
    public static void showStop(Context c) {
        if (!Settings.canDrawOverlays(c)) return;
        Intent i = new Intent(c, OverlayService.class).setAction(ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
    public static void hide(Context c) { try { c.startService(new Intent(c, OverlayService.class).setAction(ACTION_HIDE)); } catch (Exception ignored) {} }

    @Override public void onCreate() { super.onCreate(); wm = (WindowManager)getSystemService(WINDOW_SERVICE); createChannel(); }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(41, notification("MacroDeck Overlay aktiv"));
        String action = intent == null ? ACTION_HIDE : intent.getAction();
        clear();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_CAPTURE.equals(action)) showCapture(intent.getIntExtra(EXTRA_SLOT, 1));
        else if (ACTION_STOP.equals(action)) showStopBubble();
        else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
        return START_NOT_STICKY;
    }

    private void showCapture(int slot) {
        TextView v = new TextView(this);
        v.setText("Tippe auf die gewünschte Position");
        v.setTextColor(Color.WHITE); v.setTextSize(18); v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(0x66000000);
        v.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                Intent b = new Intent(BROADCAST_CAPTURED).setPackage(getPackageName());
                b.putExtra("x", event.getRawX()); b.putExtra("y", event.getRawY()); b.putExtra(EXTRA_SLOT, slot);
                sendBroadcast(b); clear(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return true;
            }
            return true;
        });
        overlay = v;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START; wm.addView(v, p);
    }

    private void showStopBubble() {
        TextView v = new TextView(this); v.setText("■ STOP"); v.setTextColor(Color.WHITE); v.setTextSize(16); v.setGravity(Gravity.CENTER);
        v.setPadding(28, 16, 28, 16); v.setBackgroundColor(0xE0D8414E); v.setElevation(12);
        v.setOnClickListener(x -> { MacroAccessibilityService s = MacroAccessibilityService.getInstance(); if (s != null) s.cancel(); clear(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); });
        overlay = v;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.END; p.x = 24; p.y = 120; wm.addView(v, p);
    }

    private void clear() { if (overlay != null) { try { wm.removeView(overlay); } catch (Exception ignored) {} overlay = null; } }
    @Override public void onDestroy() { clear(); super.onDestroy(); }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel("macrodeck_overlay", "MacroDeck Overlay", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }
    private Notification notification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "macrodeck_overlay") : new Notification.Builder(this);
        return b.setContentTitle("MacroDeck").setContentText(text).setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build();
    }
}
