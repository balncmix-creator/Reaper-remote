package com.balncmix.reaperremote;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private EditText host, port;
    private TextView status;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(17,17,19));
        getWindow().setNavigationBarColor(Color.rgb(17,17,19));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22,28,22,24);
        root.setBackgroundColor(Color.rgb(17,17,19));

        TextView title = label("REAPER Remote", 28, Color.WHITE);
        title.setTypeface(null, 1);
        root.addView(title);
        root.addView(label("Control your REAPER workstation privately", 13, 0xffaaaaaa));

        root.addView(label("PC TAILSCALE IP / HOSTNAME", 11, 0xffe05a5a));
        host = new EditText(this);
        host.setSingleLine(true);
        host.setTextColor(Color.WHITE);
        host.setHint("100.x.y.z");
        root.addView(host);

        root.addView(label("WEB CONTROL PORT", 11, 0xffe05a5a));
        port = new EditText(this);
        port.setSingleLine(true);
        port.setInputType(2);
        port.setText("8080");
        port.setTextColor(Color.WHITE);
        root.addView(port);

        Button connect = button("TEST CONNECTION", 0xffb52b2b);
        root.addView(connect);
        status = label("Not connected", 14, 0xffbbbbbb);
        status.setPadding(0,14,0,14);
        root.addView(status);
        connect.setOnClickListener(v -> send("", true));

        root.addView(label("TRANSPORT", 11, 0xffe05a5a));
        LinearLayout r1 = row(root);
        add(r1,"PLAY","play");
        add(r1,"PAUSE","pause");
        add(r1,"STOP","stop");
        add(r1,"REC","record");

        root.addView(label("NAVIGATION", 11, 0xffe05a5a));
        LinearLayout r2 = row(root);
        add(r2,"START","40042");
        add(r2,"PREV MARKER","40172");
        add(r2,"NEXT MARKER","40173");

        TextView warning = label("Remote access: install Tailscale on both devices and use the PC's Tailscale IP. Never expose REAPER Web Control directly to the public internet.",12,0xff999999);
        warning.setPadding(0,24,0,0);
        root.addView(warning);
        setContentView(root);
    }

    private TextView label(String s,int size,int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0,10,0,5);
        return t;
    }
    private Button button(String s,int color) {
        Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE);
        b.setAllCaps(false); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        return b;
    }
    private LinearLayout row(LinearLayout root) {
        LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER);
        root.addView(r); return r;
    }
    private void add(LinearLayout row,String label,String command) {
        Button b = button(label, label.equals("REC") ? 0xff792626 : 0xff303036);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,58,1);
        p.setMargins(3,4,3,4); row.addView(b,p);
        b.setOnClickListener(v -> send(command,false));
    }
    private void send(String command, boolean test) {
        String h=host.getText().toString().trim(), p=port.getText().toString().trim();
        if(h.isEmpty() || p.isEmpty()) { status.setText("Enter the PC address and port first"); return; }
        String base=h.contains("://") ? h : "http://"+h;
        String path=command.isEmpty() ? "/" : (command.matches("[0-9]+") ? "/_/"+command : "/_/ "+command).replace("/_/ ","/_/");
        new Thread(() -> {
            String message;
            try {
                HttpURLConnection c=(HttpURLConnection)new URL(base+":"+p+path).openConnection();
                c.setConnectTimeout(3000); c.setReadTimeout(3000);
                int code=c.getResponseCode(); c.disconnect();
                message="HTTP "+code+(test ? " • Connection reached REAPER" : " • "+command.toUpperCase()+" sent");
            } catch(Exception e) { message="Connection failed: "+e.getMessage(); }
            final String result=message;
            runOnUiThread(() -> status.setText(result));
        }).start();
    }
}
