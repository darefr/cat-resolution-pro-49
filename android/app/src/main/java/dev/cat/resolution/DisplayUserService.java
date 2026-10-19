package dev.cat.resolution;

import android.os.SystemClock;
import android.system.Os;
import org.json.JSONObject;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DisplayUserService extends IDisplayService.Stub {
    private static final File JOURNAL = new File("/data/local/tmp/dev.cat.resolution.session");
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> rollbackTask;
    private String originalSize;
    private String previousSize;
    private File guardToken;
    private boolean pending;
    private long deadline;
    private long revision;
    private String outcome = "";
    private String lastError = "";

    public DisplayUserService() {
        recoverInterruptedSession();
    }

    private static String command(String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Android display service timed out.");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream stream = process.getInputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > 65536) throw new IllegalStateException("Unexpected Android command output.");
                bytes.write(buffer, 0, count);
            }
        }
        String output = new String(bytes.toByteArray(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) throw new IllegalStateException(output.isEmpty() ? "Android rejected the display command." : output);
        if (output.contains("SecurityException") || output.contains("Error:")) throw new IllegalStateException(output);
        return output;
    }

    private DisplaySpec readDisplay() throws Exception {
        return DisplaySpec.parse(command("/system/bin/wm", "size"));
    }

    private void setSize(String value) throws Exception {
        command("/system/bin/wm", "size", DisplaySpec.validateOverride(value));
        DisplaySpec verified = readDisplay();
        if ("reset".equals(value)) {
            if (!"reset".equals(verified.overrideSize)) throw new IllegalStateException("Android did not clear the display override.");
        } else if (!value.equals(verified.width + "x" + verified.height)) {
            throw new IllegalStateException("Android did not accept the requested resolution.");
        }
    }

    private void record(String name) {
        revision = Math.max(System.currentTimeMillis(), revision + 1);
        outcome = name;
        lastError = "";
    }

    private void saveJournal(String size) throws Exception {
        String safe = DisplaySpec.validateOverride(size);
        File temporary = new File(JOURNAL.getPath() + ".new");
        Files.write(temporary.toPath(), safe.getBytes(StandardCharsets.UTF_8));
        Os.chmod(temporary.getPath(), 0600);
        Files.move(temporary.toPath(), JOURNAL.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        originalSize = safe;
    }

    private synchronized void recoverInterruptedSession() {
        if (!JOURNAL.isFile()) return;
        try {
            originalSize = DisplaySpec.validateOverride(new String(Files.readAllBytes(JOURNAL.toPath()), StandardCharsets.UTF_8).trim());
            setSize(originalSize);
            Files.deleteIfExists(JOURNAL.toPath());
            originalSize = null;
            record("recovered");
        } catch (Exception error) {
            lastError = "Recovery is required: " + error.getMessage();
        }
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void armGuard(String before, int seconds) throws Exception {
        command("/system/bin/sh", "-c", "test -x /system/bin/nohup && test -x /system/bin/timeout");
        previousSize = DisplaySpec.validateOverride(before);
        guardToken = new File("/data/local/tmp/cat-resolution-" + UUID.randomUUID() + ".guard");
        if (!guardToken.createNewFile()) throw new IllegalStateException("Could not arm the recovery watchdog.");
        Os.chmod(guardToken.getPath(), 0600);
        String token = quote(guardToken.getPath());
        String claimed = quote(guardToken.getPath() + ".claimed");
        // A separate shell survives the UI and ordinary Shizuku user-service termination.
        // Atomic rename prevents a watchdog that has already started from being confirmed.
        String script = "sleep " + seconds + "; if /system/bin/mv " + token + " " + claimed
            + " 2>/dev/null; then /system/bin/timeout 5 /system/bin/wm size " + previousSize
            + "; /system/bin/rm -f " + claimed + "; fi";
        command("/system/bin/sh", "-c", "/system/bin/nohup /system/bin/sh -c " + quote(script)
            + " </dev/null >/dev/null 2>&1 &");
        pending = true;
        deadline = SystemClock.elapsedRealtime() + seconds * 1000L;
        rollbackTask = timer.schedule(() -> {
            synchronized (DisplayUserService.this) {
                if (pending) rollback("timed_out");
            }
        }, seconds, TimeUnit.SECONDS);
    }

    private void cancelGuard() {
        if (rollbackTask != null) rollbackTask.cancel(false);
        rollbackTask = null;
        if (guardToken != null) guardToken.delete();
    }

    private JSONObject snapshot() throws Exception {
        if (pending && SystemClock.elapsedRealtime() >= deadline) rollback("timed_out");
        DisplaySpec display = readDisplay();
        int density = 0;
        Matcher matcher = Pattern.compile("(?:Physical|Override) density:\\s*(\\d+)").matcher(command("/system/bin/wm", "density"));
        while (matcher.find()) density = Integer.parseInt(matcher.group(1));
        JSONObject state = new JSONObject()
            .put("nativeWidth", display.nativeWidth).put("nativeHeight", display.nativeHeight)
            .put("currentWidth", display.width).put("currentHeight", display.height)
            .put("density", density).put("active", originalSize != null)
            .put("pending", pending)
            .put("remainingSeconds", pending ? Math.max(0, (deadline - SystemClock.elapsedRealtime() + 999) / 1000) : 0)
            .put("revision", revision).put("lastOutcome", outcome);
        if (!lastError.isEmpty()) state.put("error", lastError);
        return state;
    }

    private String response() throws Exception {
        return new JSONObject().put("ok", true).put("display", snapshot()).toString();
    }

    private String failure(Exception error) {
        lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        try { return new JSONObject().put("ok", false).put("error", lastError).toString(); }
        catch (Exception ignored) { return "{\"ok\":false,\"error\":\"Display operation failed.\"}"; }
    }

    @Override public synchronized String inspect() {
        try { return response(); } catch (Exception error) { return failure(error); }
    }

    @Override public synchronized String apply(double multiplier, int timeoutSeconds) {
        try {
            DisplaySpec.validateMultiplier(multiplier);
            DisplaySpec.validateTimeout(timeoutSeconds);
            if (pending) throw new IllegalStateException("Confirm or revert the current change first.");
            DisplaySpec before = readDisplay();
            if (originalSize == null) saveJournal(before.overrideSize);
            String target = before.projectedSize(multiplier);
            if (guardToken != null && new File(guardToken.getPath() + ".claimed").exists()) {
                throw new IllegalStateException("The recovery watchdog is still finishing. Try again in a moment.");
            }
            armGuard(originalSize, timeoutSeconds);
            try {
                setSize(target);
                record("applied");
            } catch (Exception applyError) {
                rollback("apply_failed");
                throw applyError;
            }
            return response();
        } catch (Exception error) { return failure(error); }
    }

    @Override public synchronized String confirm() {
        try {
            if (!pending) throw new IllegalStateException("There is no pending display change.");
            if (SystemClock.elapsedRealtime() >= deadline || guardToken == null || !guardToken.delete()) {
                rollback("timed_out");
                throw new IllegalStateException("The confirmation window expired. Your previous resolution was restored.");
            }
            cancelGuard();
            pending = false;
            record("confirmed");
            return response();
        } catch (Exception error) { return failure(error); }
    }

    private void rollback(String reason) {
        try {
            // Keep the independent watchdog armed until restoration has been verified.
            setSize(previousSize == null ? (originalSize == null ? "reset" : originalSize) : previousSize);
            cancelGuard();
            pending = false;
            if (originalSize != null && originalSize.equals(previousSize)) {
                Files.deleteIfExists(JOURNAL.toPath());
                originalSize = null;
            }
            record(reason);
        } catch (Exception error) {
            lastError = "Automatic restore failed. Restart Shizuku and tap Restore: " + error.getMessage();
        }
    }

    @Override public synchronized String revert() {
        try {
            if (!pending) throw new IllegalStateException("There is no pending change to revert.");
            rollback("reverted");
            if (pending) throw new IllegalStateException(lastError);
            return response();
        } catch (Exception error) { return failure(error); }
    }

    @Override public synchronized String restore() {
        try {
            String original = originalSize;
            if (original == null && JOURNAL.isFile()) original = new String(Files.readAllBytes(JOURNAL.toPath()), StandardCharsets.UTF_8).trim();
            setSize(original == null ? "reset" : original);
            cancelGuard();
            pending = false;
            originalSize = null;
            previousSize = null;
            Files.deleteIfExists(JOURNAL.toPath());
            record("restored");
            return response();
        } catch (Exception error) { return failure(error); }
    }

    @Override public synchronized void destroy() {
        restore();
        timer.shutdownNow();
        System.exit(0);
    }
}
