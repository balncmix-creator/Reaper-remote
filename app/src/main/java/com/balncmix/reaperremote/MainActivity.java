package com.balncmix.reaperremote;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int BG=0xff202124, PANEL=0xff292b2e, FIELD=0xff34373b;
    private static final int TEXT=0xfff1f1f1, MUTED=0xffaeb2b7, ACCENT=0xffe45b32;
    private EditText host, port;
    private TextView status;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(28),dp(20),dp(24));
        scroll.addView(root);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=new TextView(this);
        mark.setText("R");
        mark.setTextColor(ACCENT);
        mark.setTextSize(30);
        mark.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(FIELD,dp(12)));
        header.addView(mark,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout heading=new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(13),0,0,0);
        TextView title=label("REAPER Remote",25,TEXT);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        heading.addView(title);
        heading.addView(label("REMOTE CONTROL",10,ACCENT));
        header.addView(heading);
        root.addView(header);
        TextView subtitle=label("Control your REAPER workstation privately",13,MUTED);
        subtitle.setPadding(0,dp(10),0,dp(22));
        root.addView(subtitle);

        LinearLayout connection=panel(root);
        connection.addView(section("CONNECTION"));
        connection.addView(label("PC TAILSCALE IP / HOSTNAME",10,MUTED));
        host=edit("100.x.y.z",false);
        connection.addView(host,match());
        connection.addView(label("WEB CONTROL PORT",10,MUTED));
        port=edit("8080",true);
        port.setText("8080");
        connection.addView(port,match());
        Button connect=button("TEST CONNECTION",ACCENT,TEXT);
        LinearLayout.LayoutParams cp=match(); cp.topMargin=dp(14);
        connection.addView(connect,cp);
        status=label("●  NOT CONNECTED",12,MUTED);
        status.setPadding(0,dp(12),0,dp(2));
        connection.addView(status);
        connect.setOnClickListener(v->send("",true));

        LinearLayout transport=panel(root);
        transport.addView(section("TRANSPORT"));
        LinearLayout r1=new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        transport.addView(r1,match());
        add(r1,"▶","PLAY", "play",0xff4f9b62);
        add(r1,"Ⅱ","PAUSE","pause",0xffc79b3b);
        add(r1,"■","STOP","stop",0xff555b62);
        add(r1,"●","REC","record",0xffc7473d);

        LinearLayout navigation=panel(root);
        navigation.addView(section("NAVIGATION"));
        LinearLayout r2=new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        navigation.addView(r2,match());
        add(r2,"|◀","START","40042",FIELD);
        add(r2,"◀|","PREV MARKER","40172",FIELD);
        add(r2,"▶|","NEXT MARKER","40173",FIELD);

        TextView note=label("REMOTE ACCESS  •  Install Tailscale on both devices and use your PC's Tailscale IP. Never expose REAPER Web Control directly to the public internet.",11,MUTED);
        note.setPadding(dp(2),dp(8),dp(2),0);
        root.addView(note);
        setContentView(scroll);
    }

    private LinearLayout panel(LinearLayout root) {
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(15),dp(13),dp(15),dp(15));
        p.setBackground(round(PANEL,dp(14)));
        LinearLayout.LayoutParams lp=match(); lp.bottomMargin=dp(14);
        root.addView(p,lp); return p;
    }
    private TextView section(String s) {
        TextView t=label(s,11,ACCENT);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,0,0,dp(9)); return t;
    }
    private TextView label(String s,int size,int color) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0,dp(5),0,dp(4)); return t;
    }
    private EditText edit(String hint,boolean numeric) {
        EditText e=new EditText(this);
        e.setSingleLine(true); e.setTextColor(TEXT); e.setHintTextColor(MUTED);
        e.setHint(hint); e.setTextSize(15);
        e.setPadding(dp(12),0,dp(12),0);
        e.setBackground(round(FIELD,dp(8)));
        if(numeric)e.setInputType(2);
        LinearLayout.LayoutParams lp=match(); lp.height=dp(48); lp.bottomMargin=dp(10);
        return e;
    }
    private Button button(String s,int color,int textColor) {
        Button b=new Button(this); b.setText(s); b.setTextColor(textColor);
        b.setTextSize(12); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setAllCaps(false); b.setPadding(dp(8),0,dp(8),0);
        b.setBackground(round(color,dp(8)));
        return b;
    }
    private void add(LinearLayout row,String glyph,String title,String command,int color) {
        LinearLayout item=new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        Button b=button(glyph,color,TEXT);
        b.setTextSize(20);
        item.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView t=label(title,9,MUTED); t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        item.addView(t);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMargins(dp(3),0,dp(3),0); row.addView(item,lp);
        b.setOnClickListener(v->send(command,false));
    }
    private GradientDrawable round(int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    private void send(String command,boolean test) {
        String h=host.getText().toString().trim(), p=port.getText().toString().trim();
        if(h.isEmpty()||p.isEmpty()){status.setText("Enter the PC address and port first");return;}
        String base=h.contains("://")?h:"http://"+h;
        String path=command.isEmpty()?"/":(command.matches("[0-9]+")?"/_/"+
            command:"/_/"+command);
        new Thread(()->{
            String message;
            try {
                HttpURLConnection c=(HttpURLConnection)new URL(base+":"+p+path).openConnection();
                c.setConnectTimeout(3000); c.setReadTimeout(3000);
                int code=c.getResponseCode(); c.disconnect();
                message="●  HTTP "+code+(test?" • CONNECTION REACHED REAPER":" • "+command.toUpperCase()+" SENT");
            } catch(Exception e){message="●  CONNECTION FAILED: "+e.getMessage();}
            final String result=message;
            runOnUiThread(()->status.setText(result));
        }).start();
    }
}