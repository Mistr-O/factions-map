package com.example.vradbterminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.LocalServices;
import io.github.muntashirakon.adb.android.AndroidUtils;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 15, 23);
    private static final int PANEL = Color.rgb(20, 30, 44);
    private static final int FG = Color.rgb(232, 239, 247);
    private static final int MUTED = Color.rgb(143, 161, 181);
    private static final int GREEN = Color.rgb(102, 227, 180);
    private static final int AMBER = Color.rgb(255, 199, 102);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final StringBuilder transcript = new StringBuilder();
    private TextView status, output;
    private EditText command;
    private ScrollView outputScroll;
    private Button runButton;
    private volatile boolean connected;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        makeUi();
        append("VR ADB TERMINAL  /  WIRELESS DEBUGGING\n");
        append("Connect this app to the Quest's own Wireless Debugging service. No Shizuku or PC connection is required.\n\n");
        autoConnect(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!connected && status != null) autoConnect(false);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void makeUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.setBackgroundColor(BG);
        setContentView(root);

        root.addView(label("VR ADB TERMINAL", 24, GREEN, true));
        root.addView(label("WIRELESS ADB  ·  QUEST 2D WINDOW", 11, MUTED, true));
        gap(root, 8);
        status = label("Looking for Wireless Debugging…", 13, AMBER, true);
        status.setPadding(dp(11), dp(9), dp(11), dp(9));
        status.setBackground(round(PANEL, 0xff2a3b4d, 1, 8));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        gap(root, 8);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(42)));
        Button auto = button("AUTO CONNECT", GREEN, BG);
        actions.addView(auto, new LinearLayout.LayoutParams(0, -1, 1));
        auto.setOnClickListener(v -> autoConnect(false));
        Button pair = button("PAIR DEVICE", PANEL, FG);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, -1, 1);
        pp.leftMargin = dp(7);
        actions.addView(pair, pp);
        pair.setOnClickListener(v -> showPairDialog());
        Button manual = button("MANUAL CONNECT", PANEL, FG);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -1, 1);
        mp.leftMargin = dp(7);
        actions.addView(manual, mp);
        manual.setOnClickListener(v -> showConnectDialog());

        gap(root, 8);
        root.addView(label("QUICK COMMANDS", 11, GREEN, true));
        gap(root, 4);
        HorizontalScrollView quickScroll = new HorizontalScrollView(this);
        quickScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quickScroll.addView(quick);
        root.addView(quickScroll, new LinearLayout.LayoutParams(-1, dp(36)));
        addQuick(quick, "Device info", "getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk");
        addQuick(quick, "Packages", "pm list packages");
        addQuick(quick, "Storage", "df -h /data");
        addQuick(quick, "UID", "id");

        gap(root, 8);
        root.addView(label("SHELL COMMAND", 11, MUTED, true));
        gap(root, 4);
        LinearLayout commandRow = new LinearLayout(this);
        commandRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(commandRow, new LinearLayout.LayoutParams(-1, dp(44)));
        command = new EditText(this);
        command.setSingleLine(true);
        command.setTextSize(14);
        command.setTextColor(FG);
        command.setHintTextColor(MUTED);
        command.setTypeface(Typeface.MONOSPACE);
        command.setHint("getprop ro.product.model");
        command.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        command.setImeOptions(EditorInfo.IME_ACTION_SEND);
        command.setPadding(dp(10), 0, dp(10), 0);
        command.setBackground(round(PANEL, 0xff2a3b4d, 1, 8));
        commandRow.addView(command, new LinearLayout.LayoutParams(0, -1, 1));
        runButton = button("RUN  ▶", GREEN, BG);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(100), -1);
        rp.leftMargin = dp(8);
        commandRow.addView(runButton, rp);
        runButton.setOnClickListener(v -> runCommand());
        command.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEND) { runCommand(); return true; }
            return false;
        });

        gap(root, 8);
        root.addView(label("OUTPUT", 11, GREEN, true));
        gap(root, 4);
        outputScroll = new ScrollView(this);
        outputScroll.setFillViewport(true);
        outputScroll.setBackground(round(0xff060b11, 0xff263547, 1, 8));
        output = label("", 13, FG, false);
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(12), dp(10), dp(12), dp(10));
        outputScroll.addView(output);
        root.addView(outputScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        gap(root, 5);
        root.addView(label("Uses the headset's Wireless Debugging permission · Type help for examples", 10, MUTED, false));
        updateButtons();
    }

    private void addQuick(LinearLayout parent, String text, String cmd) {
        Button b = button(text, PANEL, FG);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -1);
        p.rightMargin = dp(6);
        parent.addView(b, p);
        b.setOnClickListener(v -> execute(cmd));
    }

    private void autoConnect(boolean startup) {
        if (connected) {
            setStatus("CONNECTED TO WIRELESS ADB", GREEN);
            return;
        }
        setStatus("Looking for Wireless Debugging…", AMBER);
        append(startup ? "[Searching for Wireless Debugging services…]\n" : "\n[Auto-connect: searching for Wireless Debugging services…]\n");
        updateButtons();
        worker.execute(() -> {
            try {
                AbsAdbConnectionManager manager = AdbConnectionManager.getInstance(getApplicationContext());
                boolean ok = manager.autoConnect(getApplicationContext(), 8000);
                if (!ok) {
                    main.post(() -> {
                        connected = false;
                        setStatus("Not connected · tap Auto Connect", AMBER);
                        append("[No active Wireless Debugging connection found. Enable Wireless Debugging in Quest Settings, then try again.]\n");
                        updateButtons();
                    });
                } else connectedNow("Auto-connect succeeded.");
            } catch (Throwable e) {
                main.post(() -> {
                    connected = false;
                    setStatus("Not connected · pair or retry", AMBER);
                    append("[Auto-connect] " + message(e) + "\n");
                    updateButtons();
                });
            }
        });
    }

    private void showPairDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(4));
        TextView help = label("On Quest, open Settings → System → Developer → Wireless Debugging, then choose “Pair device with pairing code”. Keep that panel open while entering the details below.", 13, MUTED, false);
        form.addView(help);
        gap(form, 10);
        EditText pin = edit("6-digit pairing code", InputType.TYPE_CLASS_NUMBER);
        pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        form.addView(pin, new LinearLayout.LayoutParams(-1, dp(44)));
        gap(form, 8);
        EditText endpoint = edit("Pairing port or IP:port", InputType.TYPE_CLASS_TEXT);
        form.addView(endpoint, new LinearLayout.LayoutParams(-1, dp(44)));
        new AlertDialog.Builder(this).setTitle("Pair Wireless Debugging")
                .setView(form)
                .setPositiveButton("PAIR", (dialog, which) -> {
                    String code = pin.getText().toString().trim();
                    Endpoint ep;
                    try { ep = parseEndpoint(endpoint.getText().toString().trim()); }
                    catch (Exception e) { append("[Invalid pairing port or address]\n"); return; }
                    if (code.length() != 6) { append("[Pairing code must be 6 digits]\n"); return; }
                    pair(ep.host, ep.port, code);
                })
                .setNegativeButton("CANCEL", null).show();
    }

    private void showConnectDialog() {
        EditText endpoint = edit("Wireless debugging port or IP:port", InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Manual ADB Connect")
                .setMessage("Use the port shown on the main Wireless Debugging screen (not the pairing port). You can enter just the port or IP:port.")
                .setView(endpoint)
                .setPositiveButton("CONNECT", (dialog, which) -> {
                    Endpoint ep;
                    try { ep = parseEndpoint(endpoint.getText().toString().trim()); }
                    catch (Exception e) { append("[Enter a port or IP:port, for example 192.168.1.5:37641]\n"); return; }
                    connect(ep.host, ep.port);
                })
                .setNegativeButton("CANCEL", null).show();
    }

    private void pair(String host, int port, String code) {
        setStatus("PAIRING…", AMBER);
        append("[Pairing with " + host + ":" + port + "]\n");
        updateButtons();
        worker.execute(() -> {
            try {
                AbsAdbConnectionManager manager = AdbConnectionManager.getInstance(getApplicationContext());
                boolean ok = manager.pair(host, port, code);
                if (!ok) {
                    main.post(() -> {
                        setStatus("Pairing failed · check code/port", 0xffff7887);
                        append("[Pairing failed. Make sure the pairing panel is still open and the port/code match.]\n");
                        updateButtons();
                    });
                    return;
                }
                main.post(() -> append("[Pairing successful. Trying to connect…]\n"));
                boolean connected = manager.autoConnect(getApplicationContext(), 8000);
                if (connected) connectedNow("Paired and connected.");
                else main.post(() -> {
                    setStatus("PAIRED · TAP AUTO CONNECT", AMBER);
                    append("[Paired successfully. Tap Auto Connect after returning to the Wireless Debugging screen.]\n");
                    updateButtons();
                });
            } catch (Throwable e) {
                main.post(() -> {
                    setStatus("PAIRING ERROR", 0xffff7887);
                    append("[Pairing error] " + message(e) + "\n");
                    updateButtons();
                });
            }
        });
    }

    private void connect(String host, int port) {
        setStatus("CONNECTING…", AMBER);
        append("[Connecting to " + host + ":" + port + "]\n");
        updateButtons();
        worker.execute(() -> {
            try {
                boolean ok = AdbConnectionManager.getInstance(getApplicationContext()).connect(host, port);
                if (ok) connectedNow("Manual connection succeeded.");
                else main.post(() -> {
                    connected = false;
                    setStatus("Connection failed · check port", 0xffff7887);
                    append("[Could not connect. Check the main Wireless Debugging port and confirm the feature is still enabled.]\n");
                    updateButtons();
                });
            } catch (Throwable e) {
                main.post(() -> {
                    connected = false;
                    setStatus("CONNECTION ERROR", 0xffff7887);
                    append("[Connection error] " + message(e) + "\n");
                    updateButtons();
                });
            }
        });
    }

    private void connectedNow(String note) {
        main.post(() -> {
            connected = true;
            setStatus("CONNECTED TO WIRELESS ADB", GREEN);
            append("[" + note + "]\n");
            updateButtons();
        });
    }

    private void runCommand() {
        String text = command.getText().toString().trim();
        if (text.isEmpty()) return;
        if (text.equalsIgnoreCase("help")) {
            append("\n$ help\nExamples: getprop ro.product.model · pm list packages · df -h /data · settings list system · dumpsys activity activities · id\nPrefixing a command with “adb shell” is supported. Host commands such as adb install/push/pull/devices are not part of this app.\n");
            command.setText("");
            return;
        }
        execute(text);
    }

    private void execute(String entered) {
        String cmd = entered.trim();
        if (cmd.regionMatches(true, 0, "adb shell ", 0, 10)) cmd = cmd.substring(10).trim();
        else if (cmd.regionMatches(true, 0, "shell ", 0, 6)) cmd = cmd.substring(6).trim();
        else if (cmd.equalsIgnoreCase("adb") || cmd.toLowerCase(java.util.Locale.ROOT).startsWith("adb ")) {
            append("\n[This app runs local shell commands, not desktop ADB host commands. Try: adb shell getprop ro.product.model]\n");
            return;
        }
        if (cmd.isEmpty()) return;
        if (!connected) {
            append("\n[Not connected. Enable Wireless Debugging and tap Auto Connect, or use Pair Device.]\n");
            return;
        }
        final String actual = cmd;
        main.post(() -> {
            command.setText("");
            append("\n$ " + actual + "\n");
            runButton.setEnabled(false);
        });
        worker.execute(() -> {
            String result;
            try {
                AbsAdbConnectionManager manager = AdbConnectionManager.getInstance(getApplicationContext());
                StringBuilder data = new StringBuilder();
                try (AdbStream stream = manager.openStream(LocalServices.SHELL, actual)) {
                    InputStream input = stream.openInputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (data.length() < 120000) {
                            int keep = Math.min(count, 120000 - data.length());
                            data.append(new String(buffer, 0, keep, StandardCharsets.UTF_8));
                        }
                    }
                } catch (IOException readEnd) {
                    String msg = readEnd.getMessage();
                    if (data.length() == 0 && msg != null && !msg.toLowerCase(java.util.Locale.ROOT).contains("stream closed")) throw readEnd;
                }
                result = data.toString();
                if (result.isEmpty()) result = "(no output)\n";
                if (data.length() >= 120000) result += "\n[output truncated]\n";
            } catch (Throwable e) {
                result = "[Command error] " + message(e) + "\n";
            }
            final String display = result;
            main.post(() -> {
                append(display);
                runButton.setEnabled(connected);
                if (outputScroll != null) outputScroll.post(() -> outputScroll.fullScroll(ScrollView.FOCUS_DOWN));
            });
        });
    }

    private Endpoint parseEndpoint(String value) throws Exception {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("empty endpoint");
        String text = value.trim();
        String host = AndroidUtils.getHostIpAddress(getApplicationContext());
        int colon = text.lastIndexOf(':');
        int port;
        if (colon > 0 && colon < text.length() - 1) {
            host = text.substring(0, colon).trim();
            port = Integer.parseInt(text.substring(colon + 1).trim());
        } else {
            port = Integer.parseInt(text);
        }
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("no Wi-Fi address");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid port");
        return new Endpoint(host, port);
    }

    private static final class Endpoint {
        final String host; final int port;
        Endpoint(String host, int port) { this.host = host; this.port = port; }
    }

    private EditText edit(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setTextColor(FG); e.setHintTextColor(MUTED);
        e.setSingleLine(true); e.setTextSize(14); e.setInputType(inputType);
        e.setPadding(dp(10), 0, dp(10), 0); e.setBackground(round(PANEL, 0xff2a3b4d, 1, 8));
        return e;
    }

    private void updateButtons() {
        if (runButton != null) runButton.setEnabled(connected);
    }

    private void setStatus(String text, int color) {
        main.post(() -> {
            if (status != null) {
                status.setText(text);
                status.setTextColor(color);
            }
        });
    }

    private void append(String text) {
        main.post(() -> {
            transcript.append(text);
            if (transcript.length() > 160000) transcript.delete(0, transcript.length() - 140000);
            if (output != null) output.setText(transcript.toString());
            if (outputScroll != null) outputScroll.post(() -> outputScroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private String message(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String value = t.getMessage();
        return value == null || value.trim().isEmpty() ? t.getClass().getSimpleName() : value;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private Button button(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(11); b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false); b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(round(bg, 0xff34465a, 1, 8));
        return b;
    }

    private GradientDrawable round(int color, int stroke, int strokeWidth, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(strokeWidth), stroke); return d;
    }

    private void gap(LinearLayout parent, int height) {
        TextView spacer = new TextView(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}