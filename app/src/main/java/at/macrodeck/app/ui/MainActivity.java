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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import at.macrodeck.app.data.MacroStore;
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
                "MacroDeck nutzt den Android-Bedienungshilfedienst für Recorder-Overlays und zum Nachspielen deiner gespeicherten Gesten. Eine separate Overlay-Berechtigung ist nicht nötig.",
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

        recorder.addView(Ui.title(this,"2. Bildschirm-Recorder",18));
        recorderStatus = Ui.body(this,"Recorder: –",14);
        recorder.addView(recorderStatus);
        TextView recorderHint = Ui.body(this,
                "Recorder einblenden → Ziel-App öffnen → auf ● REC tippen → deine Taps, langen Taps und Swipes ausführen → ■ STOP. Wartezeiten werden automatisch mitgespeichert.",
                13);
        recorderHint.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,8));
        recorder.addView(recorderHint);
        recorderButton = Ui.button(this,"● Recorder einblenden",0xFFD74343);
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
                        ? "Recorder: eingeblendet ✓"
                        : enabled && !connected
                            ? "Recorder: Bedienungshilfe wartet auf Verbindung …"
                            : "Recorder: ausgeblendet");
        }
        if (recorderButton != null) {
            recorderButton.setText(bubble ? "Recorder ausblenden" : "● Recorder einblenden");
            recorderButton.setEnabled(connected || !enabled);
            recorderButton.setAlpha((enabled && !connected) ? 0.55f : 1f);
        }

        listBox.removeAllViews();
        List<Macro> macros = MacroStore.load(this);
        if (macros.isEmpty()) {
            TextView empty = Ui.body(this,
                    "Noch keine Makros. Du kannst eines manuell erstellen oder oben den Bildschirm-Recorder verwenden.",
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
                        "Die Bedienungshilfe ist aktiviert, der Dienst wurde von Android aber noch nicht verbunden. Bitte MacroDeck in den Bedienungshilfen einmal AUS und wieder EIN schalten.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,"Bitte zuerst die MacroDeck-Bedienungshilfe aktivieren.",Toast.LENGTH_LONG).show();
            }
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        MacroAccessibilityService s = MacroAccessibilityService.getInstance();
        if (s.isRecorderBubbleVisible()) {
            s.hideRecorderBubble();
            Toast.makeText(this,"Recorder ausgeblendet",Toast.LENGTH_SHORT).show();
        } else {
            s.showRecorderBubble();
            Toast.makeText(this,"Jetzt die Ziel-App öffnen und dort auf ● REC tippen.",Toast.LENGTH_LONG).show();
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
        enabled.setOnCheckedChangeListener((b,c)->{ m.enabled=c; MacroStore.upsert(this,m); });
        top.addView(enabled);
        card.addView(top);

        if (!m.description.trim().isEmpty()) card.addView(Ui.body(this,m.description,14));
        String meta = m.steps.size()+" Schritte" + (m.lastRun>0 ? " · zuletzt "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(m.lastRun)) : "");
        card.addView(Ui.body(this,meta,13));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.START);
        Button run = Ui.button(this,"▶ Start",Ui.brand());
        run.setEnabled(m.enabled);
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
        MacroAccessibilityService.getInstance().runMacro(m);
        Toast.makeText(this,"Makro gestartet",Toast.LENGTH_SHORT).show();
        render();
    }

    private void more(Macro m) {
        new AlertDialog.Builder(this).setTitle(m.name).setItems(new String[]{"Duplizieren","Löschen"},(d,which)->{
            if(which==0) MacroStore.upsert(this,m.copy());
            else new AlertDialog.Builder(this)
                    .setTitle("Makro löschen?")
                    .setMessage(m.name)
                    .setNegativeButton("Abbrechen",null)
                    .setPositiveButton("Löschen",(x,y)->{MacroStore.delete(this,m.id);render();})
                    .show();
            render();
        }).show();
    }

    private void openEditor(String id) {
        Intent i=new Intent(this,MacroEditorActivity.class);
        if(id!=null)i.putExtra("id",id);
        startActivity(i);
    }
}
