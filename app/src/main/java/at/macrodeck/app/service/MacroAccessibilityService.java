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
import android.widget.CheckBox;
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
        runMacro(macro, 1, 0L, true);
    }

    /**
     * repeatCount: 1 = einmal, >1 = feste Anzahl, 0 = endlos.
     * LAUNCH_APP wird ab dem zweiten Durchlauf immer übersprungen. Beim ersten
     * Durchlauf wird die Ziel-App ebenfalls nicht neu geöffnet, wenn sie bereits
     * im Vordergrund ist.
     */
    public void runMacro(Macro macro, int repeatCount, long repeatPauseMs, boolean avoidRelaunchIfForeground) {
        if (macro == null || macro.steps.isEmpty()) {
            Toast.makeText(this, "Dieses Makro enthält noch keine Schritte.", Toast.LENGTH_SHORT).show();
            return;
        }
        stopRecording(false);
        handler.removeCallbacksAndMessages(null);
        restoreRecorderBubbleAfterRun = recorderBubbleVisible;
        recorderBubbleVisible = false;
        clearOverlay();
        cancelled = false;
        macro.lastRun = System.currentTimeMillis();
        MacroStore.upsert(this, macro);
        showStopBubble();
        runStep(macro.steps, 0, 0, Math.max(0, repeatCount), Math.max(0L, repeatPauseMs), avoidRelaunchIfForeground);
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

    private void finishRun() {
        clearOverlay();
        if (restoreRecorderBubbleAfterRun) {
            restoreRecorderBubbleAfterRun = false;
            showRecorderBubble();
        }
    }

    private void runStep(List<MacroStep> steps, int index, int loopIndex, int repeatCount,
                         long repeatPauseMs, boolean avoidRelaunchIfForeground) {
        if (cancelled) return;
        if (index >= steps.size()) {
            boolean repeat = repeatCount == 0 || loopIndex + 1 < repeatCount;
            if (repeat) {
                handler.postDelayed(
                        () -> runStep(steps, 0, loopIndex + 1, repeatCount, repeatPauseMs, avoidRelaunchIfForeground),
                        repeatPauseMs
                );
            } else {
                finishRun();
            }
            return;
        }

        MacroStep step = steps.get(index);
        Runnable next = () -> runStep(
                steps, index + 1, loopIndex, repeatCount, repeatPauseMs, avoidRelaunchIfForeground
        );

        switch (step.type) {
            case DELAY:
                // Recorder-Makros enthalten direkt nach LAUNCH_APP eine kurze
                // Start-Wartezeit. Wenn die App in diesem Loop gar nicht neu
                // geöffnet wurde, können wir auch diese Start-Wartezeit sparen.
                boolean startupDelayCanBeSkipped = false;
                if (index > 0 && steps.get(index - 1).type == MacroStep.Type.LAUNCH_APP) {
                    String previousTarget = steps.get(index - 1).text == null
                            ? "" : steps.get(index - 1).text.trim();
                    startupDelayCanBeSkipped = loopIndex > 0
                            || (avoidRelaunchIfForeground && !previousTarget.isEmpty() && previousTarget.equals(currentPackage));
                }
                handler.postDelayed(next, startupDelayCanBeSkipped ? 20L : Math.max(0, step.durationMs));
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
                String target = step.text == null ? "" : step.text.trim();
                boolean skipBecauseLoop = loopIndex > 0;
                boolean skipBecauseAlreadyOpen = avoidRelaunchIfForeground && !target.isEmpty() && target.equals(currentPackage);
                if (!skipBecauseLoop && !skipBecauseAlreadyOpen) {
                    try {
                        Intent intent = getPackageManager().getLaunchIntentForPackage(target);
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    } catch (Exception ignored) {}
                    handler.postDelayed(next, 700);
                } else {
                    handler.postDelayed(next, 80);
                }
                break;
            case INPUT_TEXT:
                inputText(step.text);
                handler.postDelayed(next, 300);
                break;
            case TAP:
                gesture(step.x1, step.y1, step.x1, step.y1, 80, next);
                break;
            case LONG_PRESS:
                gesture(step.x1, step.y1, step.x1, step.y1, Math.max(400, step.durationMs), next);
                break;
            case SWIPE:
                gesture(step.x1, step.y1, step.x2, step.y2, Math.max(100, step.durationMs), next);
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
        bubble.setText("⚡");
        bubble.setContentDescription("MacroDeck Schnellmenü");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(23);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(14), dp(11), dp(14), dp(11));
        bubble.setBackground(roundRect(0xFF111111, 24));
        bubble.setElevation(dp(10));
        bubble.setOnClickListener(v -> showQuickMenu());
        bubble.setOnLongClickListener(v -> {
            hideRecorderBubble();
            Toast.makeText(this, "MacroDeck ausgeblendet", Toast.LENGTH_SHORT).show();
            return true;
        });

        WindowManager.LayoutParams params = accessibilityParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(12);
        params.y = dp(130);
        addOverlay(bubble, params);
    }

    private void showQuickMenu() {
        if (recording) return;
        recorderBubbleVisible = true;
        clearOverlay();

        LinearLayout box = overlayPanel();
        TextView title = overlayTitle("⚡ MacroDeck");
        box.addView(title);

        TextView hint = overlayMuted("Makro direkt starten oder eine neue Aufnahme beginnen");
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(2);
        hintLp.bottomMargin = dp(10);
        box.addView(hint, hintLp);

        List<Macro> macros = MacroStore.load(this);
        int shown = 0;
        for (Macro macro : macros) {
            if (!macro.enabled) continue;
            Button macroButton = menuButton("▶  " + macro.name, 0xFF202020);
            macroButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            macroButton.setOnClickListener(v -> showRunOptions(macro));
            box.addView(macroButton, menuLp(5));
            shown++;
        }

        if (shown == 0) {
            TextView empty = overlayMuted("Noch keine aktiven Makros gespeichert.");
            box.addView(empty, menuLp(7));
        }

        Button record = menuButton("●  Neues Makro aufnehmen", 0xFFD74343);
        record.setOnClickListener(v -> startRecording());
        box.addView(record, menuLp(12));

        Button close = menuButton("Zurück zum ⚡-Button", 0xFF202020);
        close.setOnClickListener(v -> showRecorderBubble());
        box.addView(close, menuLp(6));

        Button hide = menuButton("MacroDeck ausblenden", 0xFF202020);
        hide.setOnClickListener(v -> hideRecorderBubble());
        box.addView(hide, menuLp(6));

        showPanel(box);
    }

    private void showRunOptions(Macro macro) {
        clearOverlay();
        LinearLayout box = overlayPanel();
        box.addView(overlayTitle(macro.name));

        TextView sub = overlayMuted("Wiederholungen auswählen. Die Ziel-App wird nur beim ersten Durchlauf geöffnet – und auch dann nicht neu gestartet, wenn sie bereits vorne ist.");
        box.addView(sub, menuLp(5));

        final int[] repeatCount = {1};
        final long[] pauseMs = {1500L};

        TextView repeatLabel = overlayValue("Wiederholung: 1×");
        box.addView(repeatLabel, menuLp(10));

        LinearLayout repeatRow = new LinearLayout(this);
        repeatRow.setOrientation(LinearLayout.HORIZONTAL);
        repeatRow.setGravity(Gravity.CENTER_VERTICAL);
        int[] counts = {1, 5, 10, 25, 0};
        String[] labels = {"1×", "5×", "10×", "25×", "∞"};
        for (int i = 0; i < counts.length; i++) {
            final int count = counts[i];
            Button chip = optionChip(labels[i]);
            chip.setOnClickListener(v -> {
                repeatCount[0] = count;
                repeatLabel.setText("Wiederholung: " + (count == 0 ? "Endlos" : count + "×"));
            });
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (i > 0) chipLp.leftMargin = dp(5);
            repeatRow.addView(chip, chipLp);
        }
        box.addView(repeatRow, menuLp(7));

        TextView pauseLabel = overlayValue("Pause zwischen Durchläufen: 1,5 s");
        box.addView(pauseLabel, menuLp(12));

        LinearLayout pauseRow = new LinearLayout(this);
        pauseRow.setOrientation(LinearLayout.HORIZONTAL);
        pauseRow.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = optionChip("−");
        Button plus = optionChip("+");
        TextView pauseValue = overlayValue("1,5 s");
        pauseValue.setGravity(Gravity.CENTER);
        minus.setOnClickListener(v -> {
            pauseMs[0] = Math.max(0L, pauseMs[0] - 500L);
            String value = formatSeconds(pauseMs[0]);
            pauseValue.setText(value);
            pauseLabel.setText("Pause zwischen Durchläufen: " + value);
        });
        plus.setOnClickListener(v -> {
            pauseMs[0] = Math.min(30000L, pauseMs[0] + 500L);
            String value = formatSeconds(pauseMs[0]);
            pauseValue.setText(value);
            pauseLabel.setText("Pause zwischen Durchläufen: " + value);
        });
        pauseRow.addView(minus, new LinearLayout.LayoutParams(dp(64), dp(44)));
        pauseRow.addView(pauseValue, new LinearLayout.LayoutParams(0, dp(44), 1f));
        pauseRow.addView(plus, new LinearLayout.LayoutParams(dp(64), dp(44)));
        box.addView(pauseRow, menuLp(6));

        CheckBox smartLaunch = new CheckBox(this);
        smartLaunch.setText("Ziel-App nicht neu öffnen, wenn sie bereits aktiv ist");
        smartLaunch.setTextColor(Color.WHITE);
        smartLaunch.setChecked(true);
        smartLaunch.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF6C70FF));
        box.addView(smartLaunch, menuLp(8));

        Button start = menuButton("▶  Makro starten", 0xFF5B5CE2);
        start.setOnClickListener(v -> runMacro(macro, repeatCount[0], pauseMs[0], smartLaunch.isChecked()));
        box.addView(start, menuLp(12));

        Button back = menuButton("←  Zurück", 0xFF202020);
        back.setOnClickListener(v -> showQuickMenu());
        box.addView(back, menuLp(6));

        showPanel(box);
    }

    private LinearLayout overlayPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(roundRect(Color.BLACK, 22));
        box.setElevation(dp(12));
        return box;
    }

    private TextView overlayTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(19);
        view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private TextView overlayMuted(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xFFAAAAAA);
        view.setTextSize(13);
        return view;
    }

    private TextView overlayValue(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        return view;
    }

    private Button menuButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(roundRect(color, 15));
        return button;
    }

    private Button optionChip(String text) {
        Button button = menuButton(text, 0xFF1B1B1B);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(5), 0, dp(5), 0);
        return button;
    }

    private LinearLayout.LayoutParams menuLp(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    private void showPanel(View view) {
        WindowManager.LayoutParams params = accessibilityParams(
                dp(340),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(10);
        params.y = dp(92);
        addOverlay(view, params);
    }

    private String formatSeconds(long millis) {
        if (millis % 1000L == 0L) return (millis / 1000L) + " s";
        return String.format(Locale.getDefault(), "%.1f s", millis / 1000f);
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
        status.setBackground(roundRect(0xE6000000, 18));
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        statusLp.topMargin = dp(18);
        root.addView(status, statusLp);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER);
        tools.setPadding(dp(5), dp(5), dp(5), dp(5));
        tools.setBackground(roundRect(0xF2000000, 20));

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
        box.setBackground(roundRect(Color.BLACK, 20));

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
        input.setBackground(roundRect(0xFF141414, 14));
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
        label.setBackground(roundRect(0xE6000000, 18));
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
        b.setBackground(roundRect(0xFF1B1B1B, 15));
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
