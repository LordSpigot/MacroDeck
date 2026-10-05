package at.macrodeck.app.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

import at.macrodeck.app.data.MacroStore;
import at.macrodeck.app.model.Macro;
import at.macrodeck.app.model.MacroStep;

public class MacroAccessibilityService extends AccessibilityService {
    private static MacroAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled = false;

    public static MacroAccessibilityService getInstance() { return instance; }
    public static boolean isConnected() { return instance != null; }

    @Override public void onServiceConnected() { super.onServiceConnected(); instance = this; }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { cancel(); }

    public void runMacro(Macro macro) {
        cancel(); cancelled = false;
        macro.lastRun = System.currentTimeMillis();
        MacroStore.upsert(this, macro);
        OverlayService.showStop(this);
        runStep(macro.steps, 0);
    }

    public void cancel() {
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
        OverlayService.hide(this);
    }

    private void runStep(List<MacroStep> steps, int index) {
        if (cancelled || index >= steps.size()) { OverlayService.hide(this); return; }
        MacroStep s = steps.get(index);
        Runnable next = () -> runStep(steps, index + 1);
        switch (s.type) {
            case DELAY:
                handler.postDelayed(next, Math.max(0, s.durationMs));
                break;
            case BACK:
                performGlobalAction(GLOBAL_ACTION_BACK); handler.postDelayed(next, 250); break;
            case HOME:
                performGlobalAction(GLOBAL_ACTION_HOME); handler.postDelayed(next, 350); break;
            case LAUNCH_APP:
                try {
                    Intent i = getPackageManager().getLaunchIntentForPackage(s.text == null ? "" : s.text.trim());
                    if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
                } catch (Exception ignored) {}
                handler.postDelayed(next, 700); break;
            case INPUT_TEXT:
                inputText(s.text); handler.postDelayed(next, 300); break;
            case TAP:
                gesture(s.x1, s.y1, s.x1, s.y1, 80, next); break;
            case LONG_PRESS:
                gesture(s.x1, s.y1, s.x1, s.y1, Math.max(400, s.durationMs), next); break;
            case SWIPE:
                gesture(s.x1, s.y1, s.x2, s.y2, Math.max(100, s.durationMs), next); break;
        }
    }

    private void gesture(float x1, float y1, float x2, float y2, long duration, Runnable next) {
        Path p = new Path(); p.moveTo(x1, y1); if (x1 != x2 || y1 != y2) p.lineTo(x2, y2);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, duration)).build();
        boolean sent = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) { handler.postDelayed(next, 80); }
            @Override public void onCancelled(GestureDescription gestureDescription) { handler.postDelayed(next, 80); }
        }, null);
        if (!sent) handler.postDelayed(next, duration + 100);
    }

    private void inputText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) {
            Bundle b = new Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text == null ? "" : text);
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        }
    }
}
