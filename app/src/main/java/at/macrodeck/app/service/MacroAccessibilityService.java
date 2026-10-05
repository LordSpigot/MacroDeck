package at.macrodeck.app.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import at.macrodeck.app.data.MacroStore;
import at.macrodeck.app.model.Macro;
import at.macrodeck.app.model.MacroStep;

public class MacroAccessibilityService extends AccessibilityService {
    public static final String BROADCAST_CAPTURED = "at.macrodeck.app.COORDINATE_CAPTURED";
    public static final String EXTRA_SLOT = "slot";

    private static MacroAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlayView;

    private volatile boolean cancelled = false;
    private boolean recorderBubbleVisible = false;
    private boolean recording = false;
    private boolean restoreRecorderBubbleAfterRun = false;
    private String currentPackage = "";
    private Macro recordingMacro;
    private long lastRecordedActionEndedAt = 0L;

    private float gestureDownX;
    private float gestureDownY;
    private long gestureDownAt;

    public static MacroAccessibilityService getInstance() { return instance; }
    public static boolean isConnected() { return instance != null; }

    /**
     * Reads Android's persisted Accessibility setting instead of relying only on
     * the in-process service instance. HyperOS can return to the Activity before
     * onServiceConnected() has finished, which made the old UI incorrectly show
     * "nicht aktiviert" for a short time.
     */
    public static boolean isEnabled(Context context) {
        try {
            if (Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
                return false;
            }

            String services = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (services == null || services.isEmpty()) return false;

            ComponentName expected = new ComponentName(context, MacroAccessibilityService.class);
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(services);
            while (splitter.hasNext()) {
                ComponentName enabled = ComponentName.unflattenFromString(splitter.next());
                if (expected.equals(enabled)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        clearOverlay();
        if (instance == this) instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        clearOverlay();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {
            String pkg = event.getPackageName().toString();
            if (!pkg.equals(getPackageName()) && !pkg.equals("com.android.systemui")) {
                currentPackage = pkg;
            }
        }
    }

    @Override public void onInterrupt() { cancel(); }

    // -------------------------------------------------------------------------
    // Macro execution
    // -------------------------------------------------------------------------

    public void runMacro(Macro macro) {
        stopRecording(false);
        handler.removeCallbacksAndMessages(null);
        clearOverlay();
        restoreRecorderBubbleAfterRun = recorderBubbleVisible;
        recorderBubbleVisible = false;
        clearOverlay();
        cancelled = false;
        macro.lastRun = System.currentTimeMillis();
        MacroStore.upsert(this, macro);
        showStopBubble();
        runStep(macro.steps, 0);
    }

    public void cancel() {
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
        clearOverlay();
        if (restoreRecorderBubbleAfterRun) {
            restoreRecorderBubbleAfterRun = false;
            showRecorderBubble();
        }
    }

    private void runStep(List<MacroStep> steps, int index) {
        if (cancelled || index >= steps.size()) {
            clearOverlay();
            if (restoreRecorderBubbleAfterRun) {
                restoreRecorderBubbleAfterRun = false;
                showRecorderBubble();
            }
            return;
        }

        MacroStep s = steps.get(index);
        Runnable next = () -> runStep(steps, index + 1);
        switch (s.type) {
            case DELAY:
                handler.postDelayed(next, Math.max(0, s.durationMs));
                break;
            case BACK:
                performGlobalAction(GLOBAL_ACTION_BACK);
                handler.postDelayed(next, 250);
                break;
            case HOME:
                performGlobalAction(GLOBAL_ACTION_HOME);
                handler.postDelayed(next, 350);
                break;
            case LAUNCH_APP:
                try {
                    Intent i = getPackageManager().getLaunchIntentForPackage(s.text == null ? "" : s.text.trim());
                    if (i != null) {
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    }
                } catch (Exception ignored) {}
                handler.postDelayed(next, 700);
                break;
            case INPUT_TEXT:
                inputText(s.text);
                handler.postDelayed(next, 300);
                break;
            case TAP:
                gesture(s.x1, s.y1, s.x1, s.y1, 80, next);
                break;
            case LONG_PRESS:
                gesture(s.x1, s.y1, s.x1, s.y1, Math.max(400, s.durationMs), next);
                break;
            case SWIPE:
                gesture(s.x1, s.y1, s.x2, s.y2, Math.max(100, s.durationMs), next);
                break;
        }
    }

    private void gesture(float x1, float y1, float x2, float y2, long duration, Runnable next) {
        Path p = new Path();
        p.moveTo(x1, y1);
        if (x1 != x2 || y1 != y2) p.lineTo(x2, y2);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, duration))
                .build();
        boolean sent = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) { handler.postDelayed(next, 80); }
            @Override public void onCancelled(GestureDescription gestureDescription) { handler.postDelayed(next, 80); }
        }, null);
        if (!sent) handler.postDelayed(next, duration + 100);
    }

    // -------------------------------------------------------------------------
    // Recorder launcher bubble
    // -------------------------------------------------------------------------

    public boolean isRecorderBubbleVisible() { return recorderBubbleVisible && !recording; }
    public boolean isRecording() { return recording; }

    public void showRecorderBubble() {
        if (recording) return;
        recorderBubbleVisible = true;
        clearOverlay();

        TextView bubble = new TextView(this);
        bubble.setText("● REC");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(15);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(16), dp(11), dp(16), dp(11));
        bubble.setBackground(roundRect(0xE6D74343, 22));
        bubble.setElevation(dp(10));
        bubble.setOnClickListener(v -> startRecording());
        bubble.setOnLongClickListener(v -> {
            hideRecorderBubble();
            Toast.makeText(this, "MacroDeck-Recorder ausgeblendet", Toast.LENGTH_SHORT).show();
            return true;
        });

        WindowManager.LayoutParams p = accessibilityParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        );
        p.gravity = Gravity.TOP | Gravity.END;
        p.x = dp(14);
        p.y = dp(130);
        addOverlay(bubble, p);
    }

    public void hideRecorderBubble() {
        recorderBubbleVisible = false;
        if (!recording) clearOverlay();
    }

    private void startRecording() {
        recording = true;
        recorderBubbleVisible = false;
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
        clearOverlay();

        recordingMacro = new Macro();
        String targetPkg = currentPackage;
        String appLabel = appLabel(targetPkg);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        recordingMacro.name = "Aufnahme – " + (appLabel.isEmpty() ? time : appLabel + " " + time);
        recordingMacro.description = targetPkg.isEmpty()
                ? "Mit dem MacroDeck Bildschirm-Recorder aufgenommen."
                : "Aufgenommen in " + appLabel + " (" + targetPkg + ").";

        if (!targetPkg.isEmpty() && !targetPkg.equals(getPackageName())) {
            MacroStep launch = new MacroStep(MacroStep.Type.LAUNCH_APP);
            launch.text = targetPkg;
            recordingMacro.steps.add(launch);
            MacroStep wait = new MacroStep(MacroStep.Type.DELAY);
            wait.durationMs = 1200;
            recordingMacro.steps.add(wait);
        }

        lastRecordedActionEndedAt = 0L;
        MacroStore.upsert(this, recordingMacro);
        showRecorderOverlay();
        Toast.makeText(this, "Aufnahme läuft – Gesten werden direkt nachgespielt", Toast.LENGTH_SHORT).show();
    }

    private void stopRecording(boolean save) {
        if (!recording) return;
        recording = false;
        clearOverlay();
        if (save && recordingMacro != null) {
            MacroStore.upsert(this, recordingMacro);
            Toast.makeText(this, "Makro gespeichert: " + recordingMacro.name, Toast.LENGTH_LONG).show();
        }
        recordingMacro = null;
        lastRecordedActionEndedAt = 0L;
        showRecorderBubble();
    }

    private void showRecorderOverlay() {
        if (!recording || recordingMacro == null) return;
        clearOverlay();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x08000000);

        TextView status = new TextView(this);
        status.setText("● REC  ·  " + visibleRecordedStepCount() + " Aktionen");
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        status.setBackground(roundRect(0xB51C1D23, 18));
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        statusLp.topMargin = dp(18);
        root.addView(status, statusLp);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER);
        tools.setPadding(dp(5), dp(5), dp(5), dp(5));
        tools.setBackground(roundRect(0xD91C1D23, 20));

        Button back = recorderButton("←");
        back.setContentDescription("Zurück aufnehmen");
        back.setOnClickListener(v -> recordGlobalAction(MacroStep.Type.BACK));
        tools.addView(back);

        Button text = recorderButton("⌨");
        text.setContentDescription("Text eingeben");
        text.setOnClickListener(v -> showTextEntryOverlay());
        tools.addView(text);

        Button stop = recorderButton("■ STOP");
        stop.setTextColor(Color.WHITE);
        stop.setBackground(roundRect(0xFFD74343, 15));
        stop.setOnClickListener(v -> stopRecording(true));
        tools.addView(stop);

        FrameLayout.LayoutParams toolsLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        toolsLp.topMargin = dp(58);
        toolsLp.rightMargin = dp(10);
        root.addView(tools, toolsLp);

        root.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gestureDownX = event.getRawX();
                    gestureDownY = event.getRawY();
                    gestureDownAt = SystemClock.uptimeMillis();
                    return true;
                case MotionEvent.ACTION_UP:
                    float upX = event.getRawX();
                    float upY = event.getRawY();
                    long duration = Math.max(1L, SystemClock.uptimeMillis() - gestureDownAt);
                    recordAndReplayGesture(gestureDownX, gestureDownY, upX, upY, duration);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });

        WindowManager.LayoutParams p = accessibilityParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        p.gravity = Gravity.TOP | Gravity.START;
        addOverlay(root, p);
    }

    private void recordAndReplayGesture(float x1, float y1, float x2, float y2, long duration) {
        if (!recording || recordingMacro == null) return;

        addAutomaticDelay();

        float dx = x2 - x1;
        float dy = y2 - y1;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float threshold = dp(28);

        MacroStep step;
        if (distance < threshold && duration >= 550) {
            step = new MacroStep(MacroStep.Type.LONG_PRESS);
            step.x1 = x1;
            step.y1 = y1;
            step.durationMs = Math.min(Math.max(duration, 550), 5000);
        } else if (distance < threshold) {
            step = new MacroStep(MacroStep.Type.TAP);
            step.x1 = x1;
            step.y1 = y1;
        } else {
            step = new MacroStep(MacroStep.Type.SWIPE);
            step.x1 = x1;
            step.y1 = y1;
            step.x2 = x2;
            step.y2 = y2;
            step.durationMs = Math.min(Math.max(duration, 120), 4000);
        }

        recordingMacro.steps.add(step);
        MacroStore.upsert(this, recordingMacro);

        clearOverlay();
        handler.postDelayed(() -> replayRecordedGesture(step), 70);
    }

    private void replayRecordedGesture(MacroStep step) {
        if (!recording) return;
        float x2 = step.type == MacroStep.Type.SWIPE ? step.x2 : step.x1;
        float y2 = step.type == MacroStep.Type.SWIPE ? step.y2 : step.y1;
        long duration;
        if (step.type == MacroStep.Type.LONG_PRESS || step.type == MacroStep.Type.SWIPE) duration = step.durationMs;
        else duration = 80;

        Path path = new Path();
        path.moveTo(step.x1, step.y1);
        if (step.x1 != x2 || step.y1 != y2) path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();

        boolean sent = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                lastRecordedActionEndedAt = SystemClock.uptimeMillis();
                handler.postDelayed(() -> { if (recording) showRecorderOverlay(); }, 90);
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                lastRecordedActionEndedAt = SystemClock.uptimeMillis();
                handler.postDelayed(() -> { if (recording) showRecorderOverlay(); }, 90);
            }
        }, null);

        if (!sent) {
            lastRecordedActionEndedAt = SystemClock.uptimeMillis();
            handler.postDelayed(() -> { if (recording) showRecorderOverlay(); }, duration + 120);
        }
    }

    private void recordGlobalAction(MacroStep.Type type) {
        if (!recording || recordingMacro == null) return;
        addAutomaticDelay();
        recordingMacro.steps.add(new MacroStep(type));
        MacroStore.upsert(this, recordingMacro);
        clearOverlay();
        handler.postDelayed(() -> {
            if (type == MacroStep.Type.BACK) performGlobalAction(GLOBAL_ACTION_BACK);
            else if (type == MacroStep.Type.HOME) performGlobalAction(GLOBAL_ACTION_HOME);
            lastRecordedActionEndedAt = SystemClock.uptimeMillis();
            handler.postDelayed(() -> { if (recording) showRecorderOverlay(); }, 160);
        }, 60);
    }

    private void addAutomaticDelay() {
        if (recordingMacro == null || lastRecordedActionEndedAt <= 0) return;
        long now = SystemClock.uptimeMillis();
        long delay = now - lastRecordedActionEndedAt;
        if (delay >= 180) {
            MacroStep wait = new MacroStep(MacroStep.Type.DELAY);
            wait.durationMs = Math.min(delay, 60000);
            recordingMacro.steps.add(wait);
        }
    }

    private int visibleRecordedStepCount() {
        if (recordingMacro == null) return 0;
        int n = 0;
        for (MacroStep step : recordingMacro.steps) {
            if (step.type != MacroStep.Type.DELAY && step.type != MacroStep.Type.LAUNCH_APP) n++;
        }
        return n;
    }

    // -------------------------------------------------------------------------
    // Text recording
    // -------------------------------------------------------------------------

    private void showTextEntryOverlay() {
        if (!recording || recordingMacro == null) return;
        addAutomaticDelay();
        MacroStore.upsert(this, recordingMacro);
        clearOverlay();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.setBackground(roundRect(0xFA22242B, 20));

        TextView title = new TextView(this);
        title.setText("Text aufnehmen");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        box.addView(title);

        EditText input = new EditText(this);
        input.setHint("Text, der eingefügt werden soll");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFFB4B6C0);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setBackground(roundRect(0xFF353842, 14));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(dp(300), LinearLayout.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = dp(12);
        box.addView(input, inputLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);

        Button cancel = recorderButton("Abbrechen");
        cancel.setOnClickListener(v -> showRecorderOverlay());
        row.addView(cancel);

        Button insert = recorderButton("Einfügen");
        insert.setBackground(roundRect(0xFF5B67F1, 15));
        insert.setOnClickListener(v -> {
            String text = input.getText().toString();
            if (text.isEmpty()) {
                Toast.makeText(this, "Bitte Text eingeben", Toast.LENGTH_SHORT).show();
                return;
            }
            MacroStep step = new MacroStep(MacroStep.Type.INPUT_TEXT);
            step.text = text;
            recordingMacro.steps.add(step);
            MacroStore.upsert(this, recordingMacro);
            clearOverlay();
            handler.postDelayed(() -> {
                inputText(text);
                lastRecordedActionEndedAt = SystemClock.uptimeMillis();
                handler.postDelayed(() -> { if (recording) showRecorderOverlay(); }, 180);
            }, 160);
        });
        LinearLayout.LayoutParams insertLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        insertLp.leftMargin = dp(8);
        row.addView(insert, insertLp);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(10);
        box.addView(row, rowLp);

        WindowManager.LayoutParams p = accessibilityParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        );
        p.gravity = Gravity.CENTER;
        addOverlay(box, p);

        input.requestFocus();
        handler.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    private void inputText(String text) {
        AccessibilityNodeInfo focused = findFocusedEditableNode();
        if (focused != null) {
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text == null ? "" : text);
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
            focused.recycle();
        }
    }

    private AccessibilityNodeInfo findFocusedEditableNode() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root == null) continue;
                    AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    root.recycle();
                    if (focused != null && focused.isEditable()) return focused;
                    if (focused != null) focused.recycle();
                }
            }
        } catch (Exception ignored) {}

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        root.recycle();
        if (focused != null && focused.isEditable()) return focused;
        if (focused != null) focused.recycle();
        return null;
    }

    // -------------------------------------------------------------------------
    // Coordinate capture for the manual editor
    // -------------------------------------------------------------------------

    public void captureCoordinate(int slot) {
        if (recording) return;
        clearOverlay();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x12000000);
        TextView label = new TextView(this);
        label.setText("Tippe auf die gewünschte Position");
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setPadding(dp(14), dp(10), dp(14), dp(10));
        label.setBackground(roundRect(0xCC1C1D23, 18));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(28);
        root.addView(label, lp);

        root.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                Intent b = new Intent(BROADCAST_CAPTURED).setPackage(getPackageName());
                b.putExtra("x", event.getRawX());
                b.putExtra("y", event.getRawY());
                b.putExtra(EXTRA_SLOT, slot);
                sendBroadcast(b);
                clearOverlay();
                return true;
            }
            return true;
        });

        WindowManager.LayoutParams p = accessibilityParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        p.gravity = Gravity.TOP | Gravity.START;
        addOverlay(root, p);
    }

    // -------------------------------------------------------------------------
    // Stop bubble while playing a macro
    // -------------------------------------------------------------------------

    private void showStopBubble() {
        clearOverlay();
        TextView v = new TextView(this);
        v.setText("■ STOP");
        v.setTextColor(Color.WHITE);
        v.setTextSize(15);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(18), dp(11), dp(18), dp(11));
        v.setBackground(roundRect(0xE6D74343, 20));
        v.setElevation(dp(10));
        v.setOnClickListener(x -> cancel());

        WindowManager.LayoutParams p = accessibilityParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        );
        p.gravity = Gravity.TOP | Gravity.END;
        p.x = dp(14);
        p.y = dp(130);
        addOverlay(v, p);
    }

    // -------------------------------------------------------------------------
    // Window helpers
    // -------------------------------------------------------------------------

    private WindowManager.LayoutParams accessibilityParams(int width, int height, int flags) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
        );
        p.packageName = getPackageName();
        return p;
    }

    private void addOverlay(View view, WindowManager.LayoutParams params) {
        clearOverlay();
        overlayView = view;
        try {
            windowManager.addView(view, params);
        } catch (Exception e) {
            overlayView = null;
            Toast.makeText(this, "Recorder-Overlay konnte nicht angezeigt werden.", Toast.LENGTH_LONG).show();
        }
    }

    private void clearOverlay() {
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
        }
    }

    private Button recorderButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setBackground(roundRect(0xFF3A3D47, 15));
        return b;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String appLabel(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "";
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)
            ).toString();
        } catch (Exception ignored) {
            return packageName;
        }
    }
}
