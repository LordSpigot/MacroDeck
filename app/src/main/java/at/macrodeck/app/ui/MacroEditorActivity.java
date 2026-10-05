package at.macrodeck.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import at.macrodeck.app.data.MacroStore;
import at.macrodeck.app.model.Macro;
import at.macrodeck.app.model.MacroStep;
import at.macrodeck.app.service.MacroAccessibilityService;

public class MacroEditorActivity extends Activity {
    private Macro macro;
    private EditText name, desc;
    private LinearLayout stepsBox;
    private MacroStep editingStep;
    private EditText x1Field,y1Field,x2Field,y2Field;

    private final BroadcastReceiver captureReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!MacroAccessibilityService.BROADCAST_CAPTURED.equals(intent.getAction())) return;
            int slot=intent.getIntExtra(MacroAccessibilityService.EXTRA_SLOT,1); float x=intent.getFloatExtra("x",0), y=intent.getFloatExtra("y",0);
            if(slot==1 && x1Field!=null){x1Field.setText(String.valueOf(Math.round(x)));y1Field.setText(String.valueOf(Math.round(y)));}
            if(slot==2 && x2Field!=null){x2Field.setText(String.valueOf(Math.round(x)));y2Field.setText(String.valueOf(Math.round(y)));}
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id=getIntent().getStringExtra("id"); macro=id==null?new Macro():MacroStore.find(this,id); if(macro==null)macro=new Macro();
        buildUi(); renderSteps();
        IntentFilter f=new IntentFilter(MacroAccessibilityService.BROADCAST_CAPTURED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(captureReceiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(captureReceiver,f);
    }
    @Override protected void onDestroy(){try{unregisterReceiver(captureReceiver);}catch(Exception ignored){}super.onDestroy();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.bg(this));root.setPadding(Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,18),Ui.dp(this,14));
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=Ui.button(this,"‹ Zurück",0xFF666A78);back.setOnClickListener(v->finish());bar.addView(back);
        TextView t=Ui.title(this,"Makro bearbeiten",24);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(Ui.dp(this,12),0,0,0);bar.addView(t,tp);
        Button save=Ui.button(this,"Speichern",Ui.brand());save.setOnClickListener(v->save());bar.addView(save);root.addView(bar);
        name=field("Name",false);name.setText(macro.name);root.addView(name,marginTop(14));
        desc=field("Beschreibung (optional)",false);desc.setText(macro.description);root.addView(desc,marginTop(8));
        LinearLayout sh=new LinearLayout(this);sh.setOrientation(LinearLayout.HORIZONTAL);sh.setGravity(Gravity.CENTER_VERTICAL);TextView st=Ui.title(this,"Schritte",20);sh.addView(st,new LinearLayout.LayoutParams(0,-2,1));Button add=Ui.button(this,"+ Schritt",Ui.brand());add.setOnClickListener(v->chooseType());sh.addView(add);root.addView(sh,marginTop(16));
        ScrollView sc=new ScrollView(this);stepsBox=new LinearLayout(this);stepsBox.setOrientation(LinearLayout.VERTICAL);sc.addView(stepsBox);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private LinearLayout.LayoutParams marginTop(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,Ui.dp(this,v),0,0);return p;}
    private EditText field(String hint,boolean number){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(Ui.text(this));e.setHintTextColor(Ui.muted(this));e.setTextSize(16);e.setSingleLine(true);e.setPadding(Ui.dp(this,14),Ui.dp(this,11),Ui.dp(this,14),Ui.dp(this,11));e.setBackground(Ui.rounded(Ui.card(this),16,this));if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}

    private void renderSteps(){
        stepsBox.removeAllViews();
        if(macro.steps.isEmpty()){TextView e=Ui.body(this,"Noch keine Schritte. Füge z. B. „Tippen“ oder „Warten“ hinzu.",15);e.setPadding(0,Ui.dp(this,28),0,0);stepsBox.addView(e);return;}
        for(int i=0;i<macro.steps.size();i++){final int idx=i;MacroStep s=macro.steps.get(i);LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);Ui.cardify(c,this);c.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));LinearLayout.LayoutParams cp=marginTop(9);c.setLayoutParams(cp);
            LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);TextView n=Ui.title(this,(i+1)+". "+s.summary(),16);top.addView(n,new LinearLayout.LayoutParams(0,-2,1));
            Button up=mini("↑");up.setEnabled(i>0);up.setOnClickListener(v->{MacroStep q=macro.steps.remove(idx);macro.steps.add(idx-1,q);renderSteps();});top.addView(up);
            Button dn=mini("↓");dn.setEnabled(i<macro.steps.size()-1);dn.setOnClickListener(v->{MacroStep q=macro.steps.remove(idx);macro.steps.add(idx+1,q);renderSteps();});top.addView(dn);
            Button ed=mini("✎");ed.setOnClickListener(v->editStep(s));top.addView(ed);Button del=mini("×");del.setTextColor(Color.WHITE);del.setBackground(Ui.rounded(Ui.danger(),12,this));del.setOnClickListener(v->{macro.steps.remove(idx);renderSteps();});top.addView(del);c.addView(top);stepsBox.addView(c);}
    }
    private Button mini(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Ui.text(this));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),0);b.setBackground(Ui.rounded(Ui.dark(this)?0xFF3A3C47:0xFFE9EAF1,12,this));return b;}

    private void chooseType(){String[] labels={"Tippen","Langer Tap","Wischen","Warten","Text eingeben","App starten","Zurück","Home"};MacroStep.Type[] types={MacroStep.Type.TAP,MacroStep.Type.LONG_PRESS,MacroStep.Type.SWIPE,MacroStep.Type.DELAY,MacroStep.Type.INPUT_TEXT,MacroStep.Type.LAUNCH_APP,MacroStep.Type.BACK,MacroStep.Type.HOME};new AlertDialog.Builder(this).setTitle("Schritt hinzufügen").setItems(labels,(d,w)->editStep(new MacroStep(types[w]))).show();}

    private void editStep(MacroStep s){editingStep=s;if(s.type==MacroStep.Type.BACK||s.type==MacroStep.Type.HOME){if(!macro.steps.contains(s))macro.steps.add(s);renderSteps();return;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int pad=Ui.dp(this,18);box.setPadding(pad,0,pad,0);
        x1Field=y1Field=x2Field=y2Field=null;
        if(s.type==MacroStep.Type.TAP||s.type==MacroStep.Type.LONG_PRESS||s.type==MacroStep.Type.SWIPE){x1Field=field("X Start",true);y1Field=field("Y Start",true);x1Field.setText(num(s.x1));y1Field.setText(num(s.y1));box.addView(x1Field,marginTop(6));box.addView(y1Field,marginTop(6));Button capture=Ui.button(this,"Position aufnehmen",Ui.brand());capture.setOnClickListener(v->capture(1));box.addView(capture,marginTop(7));}
        if(s.type==MacroStep.Type.SWIPE){x2Field=field("X Ende",true);y2Field=field("Y Ende",true);x2Field.setText(num(s.x2));y2Field.setText(num(s.y2));box.addView(x2Field,marginTop(10));box.addView(y2Field,marginTop(6));Button capture2=Ui.button(this,"Endposition aufnehmen",Ui.brand());capture2.setOnClickListener(v->capture(2));box.addView(capture2,marginTop(7));}
        EditText duration=null,text=null;
        if(s.type==MacroStep.Type.DELAY||s.type==MacroStep.Type.LONG_PRESS||s.type==MacroStep.Type.SWIPE){duration=field("Dauer in ms",true);duration.setText(String.valueOf(s.durationMs));box.addView(duration,marginTop(10));}
        if(s.type==MacroStep.Type.INPUT_TEXT){text=field("Text",false);text.setText(s.text);box.addView(text,marginTop(8));}
        if(s.type==MacroStep.Type.LAUNCH_APP){text=field("Paketname, z. B. com.spotify.music",false);text.setText(s.text);box.addView(text,marginTop(8));TextView h=Ui.body(this,"Tipp: Den Paketnamen findest du häufig in der Play-Store-URL hinter id=.",12);box.addView(h);}
        final EditText durF=duration,textF=text;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(titleFor(s.type)).setView(box).setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{if(x1Field!=null){s.x1=Float.parseFloat(x1Field.getText().toString());s.y1=Float.parseFloat(y1Field.getText().toString());}if(x2Field!=null){s.x2=Float.parseFloat(x2Field.getText().toString());s.y2=Float.parseFloat(y2Field.getText().toString());}if(durF!=null)s.durationMs=Long.parseLong(durF.getText().toString());if(textF!=null)s.text=textF.getText().toString();if(!macro.steps.contains(s))macro.steps.add(s);renderSteps();dialog.dismiss();}catch(Exception ex){Toast.makeText(this,"Bitte gültige Werte eingeben.",Toast.LENGTH_SHORT).show();}}));dialog.show();
    }
    private String num(float v){return String.valueOf(Math.round(v));}
    private String titleFor(MacroStep.Type t){switch(t){case TAP:return"Tippen";case LONG_PRESS:return"Langer Tap";case SWIPE:return"Wischen";case DELAY:return"Warten";case INPUT_TEXT:return"Text eingeben";case LAUNCH_APP:return"App starten";default:return t.name();}}
    private void capture(int slot){if(!MacroAccessibilityService.isConnected()){Toast.makeText(this,"Bitte zuerst die MacroDeck-Bedienungshilfe aktivieren.",Toast.LENGTH_LONG).show();startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}MacroAccessibilityService.getInstance().captureCoordinate(slot);}
    private void save(){String n=name.getText().toString().trim();if(n.isEmpty()){Toast.makeText(this,"Bitte einen Namen eingeben.",Toast.LENGTH_SHORT).show();return;}macro.name=n;macro.description=desc.getText().toString().trim();MacroStore.upsert(this,macro);Toast.makeText(this,"Gespeichert",Toast.LENGTH_SHORT).show();finish();}
}
