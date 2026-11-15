package at.macrodeck.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import at.macrodeck.app.data.MacroStore;
import at.macrodeck.app.data.RunSettingsStore;
import at.macrodeck.app.model.Macro;
import at.macrodeck.app.service.MacroAccessibilityService;

public class MainActivity extends Activity {
    private LinearLayout listBox;
    private TextView setupStatus;
    private TextView recorderStatus;
    private Button recorderButton;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
        // HyperOS may reconnect an AccessibilityService a moment after returning
        // from Settings. Refresh twice so the UI follows the real service state.
        uiHandler.postDelayed(this::render, 400);
        uiHandler.postDelayed(this::render, 1200);
    }

    @Override protected void onPause() {
        uiHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));
        root.setPadding(Ui.dp(this,18), Ui.dp(this,18), Ui.dp(this,18), Ui.dp(this,14));

        TextView title = Ui.title(this, "MacroDeck", 30);
        root.addView(title);
        TextView sub = Ui.body(this, "Makros aufnehmen, bearbeiten und lokal ausführen", 15);
        root.addView(sub);

        LinearLayout setup = new LinearLayout(this);
        setup.setOrientation(LinearLayout.VERTICAL);
        Ui.cardify(setup, this);
        setup.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,14));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.setMargins(0,Ui.dp(this,16),0,Ui.dp(this,12));
        root.addView(setup, sp);

        setup.addView(Ui.title(this,"1. Bedienungshilfe",18));
        setupStatus = Ui.body(this,"Status: –",14);
        setup.addView(setupStatus);
        TextView setupHint = Ui.body(this,
                "MacroDeck nutzt den Android-Bedienungshilfedienst für Recorder-Overlays und gespeicherte Gesten. Android kann daher die Sicherheitsmeldung „Zugriffseinstellungen prüfen“ anzeigen. Die Berechtigung lässt sich jederzeit in den Systemeinstellungen entziehen.",
                13);
        setupHint.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,8));
        setup.addView(setupHint);
        Button access = Ui.button(this,"Bedienungshilfe öffnen",Ui.brand());
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        setup.addView(access);

        LinearLayout recorder = new LinearLayout(this);
        recorder.setOrientation(LinearLayout.VERTICAL);
        Ui.cardify(recorder, this);
        recorder.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,14));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2);
        rp.setMargins(0,0,0,Ui.dp(this,16));
        root.addView(recorder, rp);

        recorder.addView(Ui.title(this,"2. Schnellmenü & Recorder",18));
        recorderStatus = Ui.body(this,"Recorder: –",14);
        recorder.addView(recorderStatus);
        TextView recorderHint = Ui.body(this,
                "Schnellmenü einblenden → Ziel-App öffnen → auf ⚡ tippen. Einstellungen für Wiederholungen und Pausen werden pro Makro gespeichert (auch über ⋯). Ausblenden beendet nur das Overlay, nicht die Bedienungshilfe.",
                13);
        recorderHint.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,8));
        recorder.addView(recorderHint);
        recorderButton = Ui.button(this,"⚡ Schnellmenü einblenden",Ui.brand());
        recorderButton.setOnClickListener(v -> toggleRecorder());
        recorder.addView(recorderButton);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView my = Ui.title(this,"Meine Makros",22);
        head.addView(my,new LinearLayout.LayoutParams(0,-2,1));
        Button plus = Ui.button(this,"+ Neues Makro",Ui.brand());
        plus.setOnClickListener(v -> openEditor(null));
        head.addView(plus);
        root.addView(head);

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void render() {
        boolean enabled = MacroAccessibilityService.isEnabled(this);
        boolean connected = MacroAccessibilityService.isConnected();
        if (setupStatus != null) {
            String status = connected
                    ? "aktiv ✓"
                    : enabled
                        ? "aktiviert – Dienst wird verbunden …"
                        : "nicht aktiviert";
            setupStatus.setText("Status: " + status);
        }

        boolean bubble = connected && MacroAccessibilityService.getInstance().isRecorderBubbleVisible();
        boolean recording = connected && MacroAccessibilityService.getInstance().isRecording();
        if (recorderStatus != null) {
            recorderStatus.setText(recording
                    ? "Recorder: Aufnahme läuft ●"
                    : bubble
                        ? "Schnellmenü: eingeblendet ✓"
                        : enabled && !connected
                            ? "Recorder: Bedienungshilfe wartet auf Verbindung …"
                            : "Schnellmenü: ausgeblendet");
        }
        if (recorderButton != null) {
            recorderButton.setText(bubble ? "⚡ ausblenden (Dienst bleibt aktiv)" : "⚡ Schnellmenü einblenden");
            recorderButton.setEnabled(connected || !enabled);
            recorderButton.setAlpha((enabled && !connected) ? 0.55f : 1f);
        }

        listBox.removeAllViews();
        List<Macro> macros = MacroStore.load(this);
        if (macros.isEmpty()) {
            TextView empty = Ui.body(this,
                    "Noch keine Makros. Du kannst eines manuell erstellen oder über das ⚡-Schnellmenü in der Ziel-App aufnehmen.",
                    16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0,Ui.dp(this,56),0,0);
            listBox.addView(empty);
            return;
        }
        for (Macro m : macros) listBox.addView(macroCard(m));
    }

    private void toggleRecorder() {
        if (!MacroAccessibilityService.isConnected()) {
            if (MacroAccessibilityService.isEnabled(this)) {
                Toast.makeText(this,
                        "Die Bedienungshilfe ist weiterhin aktiviert. Android/HyperOS verbindet den Dienst gerade neu – du musst den Systemschalter nicht erneut einschalten.",
                        Toast.LENGTH_LONG).show();
                uiHandler.postDelayed(this::render, 800);
                uiHandler.postDelayed(this::render, 2000);
            } else {
                Toast.makeText(this,"Bitte zuerst die MacroDeck-Bedienungshilfe aktivieren.",Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            return;
        }
        MacroAccessibilityService s = MacroAccessibilityService.getInstance();
        if (s.isRecorderBubbleVisible()) {
            s.hideRecorderBubble();
            Toast.makeText(this,"⚡ ausgeblendet – Bedienungshilfe bleibt aktiv",Toast.LENGTH_SHORT).show();
        } else {
            s.showRecorderBubble();
            Toast.makeText(this,"Jetzt die Ziel-App öffnen und dort auf ⚡ tippen.",Toast.LENGTH_LONG).show();
        }
        render();
    }

    private View macroCard(Macro m) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        Ui.cardify(card,this);
        card.setPadding(Ui.dp(this,16),Ui.dp(this,14),Ui.dp(this,16),Ui.dp(this,14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(0,Ui.dp(this,10),0,0);
        card.setLayoutParams(cp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.title(this,m.name,19);
        top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        CheckBox enabled = new CheckBox(this);
        enabled.setChecked(m.enabled);
        enabled.setOnCheckedChangeListener((b, checked) -> {
            boolean previous = m.enabled;
            m.enabled = checked;
            if (!MacroStore.upsert(this, m)) {
                m.enabled = previous;
                Toast.makeText(this, "Status konnte nicht gespeichert werden", Toast.LENGTH_LONG).show();
            }
            render(); // keep Start button in sync with the saved enabled state
        });
        top.addView(enabled);
        card.addView(top);

        if (!m.description.trim().isEmpty()) card.addView(Ui.body(this,m.description,14));
        String meta = m.steps.size()+" Schritte · " + RunSettingsStore.summary(RunSettingsStore.load(this, m.id))
                + (m.lastRun>0 ? " · zuletzt "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(m.lastRun)) : "");
        card.addView(Ui.body(this,meta,13));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.START);
        Button run = Ui.button(this,"▶ Start",Ui.brand());
        run.setEnabled(m.enabled && !m.steps.isEmpty());
        run.setOnClickListener(v->runMacro(m));
        actions.addView(run);
        Button edit = Ui.button(this,"Bearbeiten",0xFF666A78);
        edit.setOnClickListener(v->openEditor(m.id));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);
        bp.setMargins(Ui.dp(this,8),0,0,0);
        actions.addView(edit,bp);
        Button more = Ui.button(this,"⋯",0xFF666A78);
        more.setOnClickListener(v->more(m));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-2,-2);
        mp.setMargins(Ui.dp(this,8),0,0,0);
        actions.addView(more,mp);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);
        ap.setMargins(0,Ui.dp(this,10),0,0);
        card.addView(actions,ap);
        return card;
    }

    private void runMacro(Macro m) {
        if (!MacroAccessibilityService.isConnected()) {
            Toast.makeText(this,"Bitte zuerst die MacroDeck-Bedienungshilfe aktivieren.",Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        RunSettingsStore.Options options = RunSettingsStore.load(this, m.id);
        if (options.repeatCount == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Endlosschleife starten?")
                    .setMessage("Dieses Makro wird so lange wiederholt, bis du auf ■ STOP tippst.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Starten", (d, which) -> startMacro(m, options))
                    .show();
        } else {
            startMacro(m, options);
        }
    }

    private void startMacro(Macro m, RunSettingsStore.Options options) {
        MacroAccessibilityService service = MacroAccessibilityService.getInstance();
        if (service == null) return;
        service.runMacro(m, options.repeatCount, options.pauseMs, options.avoidRelaunch);
        Toast.makeText(this, "Makro gestartet · " + RunSettingsStore.summary(options), Toast.LENGTH_SHORT).show();
        render();
    }

    private void more(Macro m) {
        new AlertDialog.Builder(this).setTitle(m.name).setItems(new String[]{"⚙ Ausführung einstellen","Duplizieren","Löschen"},(d,which)->{
            if (which == 0) { showRunSettings(m); return; }
            if(which==1) {
                Macro copy = m.copy();
                if (MacroStore.upsert(this, copy)) RunSettingsStore.copy(this, m.id, copy.id);
            }
            else new AlertDialog.Builder(this)
                    .setTitle("Makro löschen?")
                    .setMessage(m.name)
                    .setNegativeButton("Abbrechen",null)
                    .setPositiveButton("Löschen",(x,y)->{
                        if (MacroStore.delete(this,m.id)) RunSettingsStore.delete(this, m.id);
                        else Toast.makeText(this,"Makro konnte nicht gelöscht werden",Toast.LENGTH_LONG).show();
                        render();
                    })
                    .show();
            render();
        }).show();
    }

    private void showRunSettings(Macro macro) {
        RunSettingsStore.Options saved = RunSettingsStore.load(this, macro.id);
        int[] repeats = {1, 5, 10, 25, 0};
        String[] labels = {"1×", "5×", "10×", "25×", "∞ Endlos"};
        int selected = 0;
        for (int i = 0; i < repeats.length; i++) {
            if (saved.repeatCount == repeats[i]) selected = i;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), Ui.dp(this, 4));
        box.addView(Ui.body(this, "Wiederholungen", 15));
        Spinner picker = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        picker.setAdapter(adapter);
        picker.setSelection(selected);
        box.addView(picker, marginTop(7));

        TextView pauseLabel = Ui.body(this, "Pause: " + formatPause(saved.pauseMs), 15);
        box.addView(pauseLabel, marginTop(18));
        SeekBar pause = new SeekBar(this);
        pause.setMax(60); // 0–30 seconds in half-second increments
        pause.setProgress((int) (saved.pauseMs / 500L));
        pause.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                pauseLabel.setText("Pause: " + formatPause(progress * 500L));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        box.addView(pause);

        CheckBox smart = new CheckBox(this);
        smart.setText("Aktive Ziel-App nicht neu öffnen");
        smart.setTextColor(Ui.text(this));
        smart.setChecked(saved.avoidRelaunch);
        box.addView(smart, marginTop(12));

        new AlertDialog.Builder(this)
                .setTitle("Ausführung · " + macro.name)
                .setView(box)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", (d, which) -> {
                    RunSettingsStore.Options options = new RunSettingsStore.Options(
                            repeats[picker.getSelectedItemPosition()],
                            pause.getProgress() * 500L, smart.isChecked());
                    if (!RunSettingsStore.save(this, macro.id, options)) {
                        Toast.makeText(this, "Einstellungen konnten nicht gespeichert werden", Toast.LENGTH_LONG).show();
                    }
                    render();
                })
                .show();
    }

    private String formatPause(long ms) {
        return ms % 1000L == 0
                ? (ms / 1000L) + " s"
                : String.format(java.util.Locale.GERMAN, "%.1f s", ms / 1000d);
    }

    private LinearLayout.LayoutParams marginTop(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(this, dp);
        return params;
    }

    private void openEditor(String id) {
        Intent i=new Intent(this,MacroEditorActivity.class);
        if(id!=null)i.putExtra("id",id);
        startActivity(i);
    }
}
