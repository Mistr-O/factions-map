package com.example.vradbterminal;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(102, 227, 180);
    private static final int BG = Color.rgb(11, 17, 27);
    private static final int PANEL = Color.rgb(18, 28, 42);
    private static final int FG = Color.rgb(231, 238, 247);
    private static final int MUTED = Color.rgb(145, 163, 184);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status, output;
    private EditText command;
    private ScrollView scroll;
    private Button connect, run;
    private IPrivilegedShell shell;
    private Shizuku.UserServiceArgs serviceArgs;
    private boolean binding;
    private static final int PERMISSION_REQUEST = 6701;

    private final Shizuku.OnBinderReceivedListener binderReceived = this::refreshStatus;
    private final Shizuku.OnBinderDeadListener binderDead = () -> main.post(() -> {
        shell = null;
        binding = false;
        setStatus("SHIZUKU STOPPED", 0xFFFFC66D);
        append("\n[Shizuku stopped. Restart its service and reconnect.]\n");
        updateButtons();
    });
    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> {
        if (requestCode != PERMISSION_REQUEST) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            append("[Shizuku permission granted]\n");
            bindShell();
        } else {
            setStatus("PERMISSION DENIED", 0xFFFF7D88);
            append("[Permission denied. Allow this app in Shizuku and retry.]\n");
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            shell = IPrivilegedShell.Stub.asInterface(binder);
            binding = false;
            IPrivilegedShell connected = shell;
            worker.execute(() -> {
                try {
                    int uid = connected.getUid();
                    main.post(() -> {
                        setStatus(uid == 0 ? "ROOT SHELL · UID 0" : "SHIZUKU SHELL · UID " + uid, GREEN);
                        append("[Connected to local shell, UID " + uid + "]\nType help for examples.\n");
                        updateButtons();
                    });
                } catch (Exception e) {
                    main.post(() -> append("[Connection error] " + e.getMessage() + "\n"));
                }
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            shell = null;
            binding = false;
            refreshStatus();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        makeUi();
        append("VR ADB TERMINAL · SHIZUKU EDITION\n");
        append("A windowed terminal for running commands locally on this Quest.\n");
        append("Start Shizuku first, then grant access below.\n\n");
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionResult);
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        Shizuku.removeRequestPermissionResultListener(permissionResult);
        try {
            if (serviceArgs != null && Shizuku.pingBinder()) {
                Shizuku.unbindUserService(serviceArgs, connection, true);
            }
        } catch (Exception ignored) { }
        worker.shutdownNow();
        super.onDestroy();
    }

    private void makeUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));
        setContentView(root);

        TextView title = label("VR ADB TERMINAL", 23, GREEN, true);
        root.addView(title);
        TextView subtitle = label("QUEST 2D WINDOW  /  LOCAL SHELL THROUGH SHIZUKU", 11, MUTED, true);
        root.addView(subtitle);
        gap(root, 8);

        status = label("CHECKING SHIZUKU…", 12, 0xFFFFC66D, true);
        status.setPadding(dp(10), dp(8), dp(10), dp(8));
        status.setBackground(round(PANEL, 0xFF263548, 1, 8));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        gap(root, 8);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(buttons, new LinearLayout.LayoutParams(-1, dp(42)));
        Button open = button("OPEN SHIZUKU", PANEL, FG);
        buttons.addView(open, new LinearLayout.LayoutParams(dp(155), -1));
        open.setOnClickListener(v -> openShizuku());
        connect = button("GRANT ACCESS / CONNECT", GREEN, BG);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1f);
        cp.leftMargin = dp(8);
        buttons.addView(connect, cp);
        connect.setOnClickListener(v -> requestOrConnect());
        gap(root, 8);

        TextView hint = label("COMMAND  ·  Enter a command or prefix it with 'adb shell'", 12, MUTED, false);
        root.addView(hint);
        gap(root, 5);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row, new LinearLayout.LayoutParams(-1, dp(44)));
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
        command.setBackground(round(PANEL, 0xFF263548, 1, 8));
        row.addView(command, new LinearLayout.LayoutParams(0, -1, 1f));
        run = button("RUN  ▶", GREEN, BG);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(105), -1);
        rp.leftMargin = dp(8);
        row.addView(run, rp);
        run.setOnClickListener(v -> runCommand());
        command.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEND) { runCommand(); return true; }
            return false;
        });
        gap(root, 8);

        TextView outLabel = label("OUTPUT", 11, GREEN, true);
        root.addView(outLabel);
        gap(root, 4);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(round(0xFF070C13, 0xFF263548, 1, 8));
        output = label("", 13, FG, false);
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(12), dp(10), dp(12), dp(10));
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        gap(root, 5);
        root.addView(label("Local shell only · Android permissions vary · Type help for examples", 10, MUTED, false));
        updateButtons();
    }

    private void requestOrConnect() {
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("SHIZUKU NOT RUNNING", 0xFFFFC66D);
                append("[Start the Shizuku service, then return here.]\n");
                Toast.makeText(this, "Start Shizuku first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindShell();
            else Shizuku.requestPermission(PERMISSION_REQUEST);
        } catch (Exception e) {
            setStatus("SHIZUKU ERROR", 0xFFFF7D88);
            append("[Shizuku error] " + e.getMessage() + "\n");
        }
    }

    private void refreshStatus() {
        if (status == null) return;
        main.post(() -> {
            try {
                if (!Shizuku.pingBinder()) setStatus("SHIZUKU NOT RUNNING", 0xFFFFC66D);
                else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                    setStatus(shell == null ? "PERMISSION GRANTED" : "SHELL CONNECTED", GREEN);
                else setStatus("GRANT SHIZUKU PERMISSION", 0xFFFFC66D);
            } catch (Exception e) {
                setStatus("SHIZUKU ERROR", 0xFFFF7D88);
            }
            updateButtons();
        });
    }

    private void bindShell() {
        if (shell != null || binding) return;
        if (!Shizuku.pingBinder()) { refreshStatus(); return; }
        try {
            serviceArgs = new Shizuku.UserServiceArgs(
                    new ComponentName(getPackageName(), PrivilegedShellService.class.getName()))
                    .daemon(false).processNameSuffix("quest_shell")
                    .tag("quest-vr-adb-terminal").version(1);
            binding = true;
            setStatus("CONNECTING SHELL…", 0xFFFFC66D);
            updateButtons();
            Shizuku.bindUserService(serviceArgs, connection);
        } catch (Exception e) {
            binding = false;
            setStatus("SHELL FAILED", 0xFFFF7D88);
            append("[Could not bind shell] " + e.getMessage() + "\n");
            updateButtons();
        }
    }

    private void openShizuku() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (i != null) startActivity(i);
            else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Shizuku", Toast.LENGTH_SHORT).show();
        }
    }

    private void runCommand() {
        String entered = command.getText().toString().trim();
        if (entered.isEmpty()) return;
        if (entered.equalsIgnoreCase("help")) {
            append("\n$ help\nExamples: getprop ro.product.model, pm list packages, settings list system, dumpsys activity activities, cmd package list packages, id\nUse 'adb shell <command>' if you prefer. Host commands like adb install/push/pull/devices are not included.\n");
            command.setText("");
            return;
        }
        String actual = entered;
        if (actual.regionMatches(true, 0, "adb shell ", 0, 10)) actual = actual.substring(10).trim();
        else if (actual.regionMatches(true, 0, "shell ", 0, 6)) actual = actual.substring(6).trim();
        else if (actual.toLowerCase(java.util.Locale.ROOT).startsWith("adb ")) {
            append("\n[This APK runs local shell commands, not the desktop adb client. Try 'adb shell pm list packages'.]\n");
            return;
        }
        final String cmd = actual;
        final IPrivilegedShell active = shell;
        if (active == null) {
            append("[No shell connection. Press GRANT ACCESS / CONNECT first.]\n");
            requestOrConnect();
            return;
        }
        command.setText("");
        append("\n$ " + entered + "\n");
        run.setEnabled(false);
        worker.execute(() -> {
            try {
                String result = active.exec(cmd);
                main.post(() -> append(result == null || result.isEmpty() ? "(no output)\n" : result));
            } catch (Exception e) {
                main.post(() -> append("[Command error] " + e.getMessage() + "\n"));
            } finally {
                main.post(() -> run.setEnabled(true));
            }
        });
    }

    private Button button(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(bg, bg == GREEN ? GREEN : 0xFF263548, 1, 8));
        b.setStateListAnimator(null);
        return b;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int color, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (width > 0) d.setStroke(dp(width), stroke);
        return d;
    }

    private void gap(LinearLayout root, int height) {
        root.addView(new android.view.View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setStatus(String text, int color) {
        if (status != null) { status.setText("●  " + text); status.setTextColor(color); }
    }

    private void append(String text) {
        if (output == null) return;
        output.append(text);
        if (output.length() > 45000) output.setText(output.getText().subSequence(output.length() - 35000, output.length()));
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void updateButtons() {
        if (connect != null) connect.setText(shell != null ? "SHELL CONNECTED" : (binding ? "CONNECTING…" : "GRANT ACCESS / CONNECT"));
        if (connect != null) connect.setEnabled(!binding);
        if (run != null && shell == null) run.setAlpha(0.85f);
    }
}
