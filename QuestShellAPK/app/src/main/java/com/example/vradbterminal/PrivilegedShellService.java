package com.example.vradbterminal;

import android.system.Os;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class PrivilegedShellService extends IPrivilegedShell.Stub {
    private static final int MAX_OUTPUT = 128 * 1024;

    @Override public String exec(String command) {
        if (command == null || command.trim().isEmpty()) return "[error] Empty command\n";
        if (command.length() > 8192) return "[error] Command too long (8192 character limit)\n";
        Process process = null;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        final boolean[] truncated = {false};
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true).start();
            final Process running = process;
            Thread reader = new Thread(() -> {
                byte[] buffer = new byte[4096];
                try (InputStream in = running.getInputStream()) {
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        synchronized (captured) {
                            int remain = MAX_OUTPUT - captured.size();
                            if (remain > 0) captured.write(buffer, 0, Math.min(remain, count));
                            if (count > remain) truncated[0] = true;
                        }
                    }
                } catch (Exception ignored) { }
            }, "vradb-output-reader");
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            reader.join(2000);

            String result;
            synchronized (captured) {
                result = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            }
            if (truncated[0]) result += "\n[output truncated at 128 KiB]\n";
            if (!finished) result += "\n[command timed out after 120 seconds]\n";
            else result += "\n[exit code: " + process.exitValue() + "]\n";
            return result;
        } catch (Exception e) {
            return "[shell error] " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\n";
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    @Override public int getUid() {
        return Os.getuid();
    }

    @Override public void destroy() {
        System.exit(0);
    }
}
