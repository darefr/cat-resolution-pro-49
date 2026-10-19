package dev.cat.resolution;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public final class AppController {
    public final ExecutorService worker = Executors.newSingleThreadExecutor();
    public final CatStore store;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs serviceArgs;
    private volatile IDisplayService remote;
    private volatile boolean binding;
    private volatile String connectionError = "";
    private volatile JSONObject display;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            binding = false;
            remote = IDisplayService.Stub.asInterface(binder);
            connectionError = "";
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            remote = null;
            binding = false;
            connectionError = "The display service disconnected. Reconnect Shizuku to restore control.";
        }
        @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
        @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
    };

    public AppController(Context context) {
        this.context = context.getApplicationContext();
        store = new CatStore(this.context);
        serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(this.context, DisplayUserService.class))
            .daemon(true).processNameSuffix("cat_display").debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE).tag("cat-display");
        Shizuku.addBinderReceivedListenerSticky(() -> main.post(this::ensureBound));
        Shizuku.addBinderDeadListener(() -> {
            remote = null;
            binding = false;
            connectionError = "Shizuku stopped. Start it again before changing or restoring the display.";
        });
        Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
            if (requestCode == 401 && grantResult == PackageManager.PERMISSION_GRANTED) main.post(this::ensureBound);
        });
    }

    public void ensureBound() {
        try {
            if (remote != null && !remote.asBinder().isBinderAlive()) remote = null;
            if (remote != null || binding || !Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;
            binding = true;
            connectionError = "";
            Shizuku.bindUserService(serviceArgs, connection);
            main.postDelayed(() -> {
                if (binding && remote == null) {
                    binding = false;
                    connectionError = "The privileged service did not start. Open Shizuku, check authorization, then try again.";
                }
            }, 12000);
        } catch (Exception error) { binding = false; connectionError = error.getMessage(); }
    }

    public void requestPermission() {
        if (!Shizuku.pingBinder()) throw new IllegalStateException("Start Shizuku using wireless debugging, then return here.");
        if (Shizuku.isPreV11()) throw new IllegalStateException("Update Shizuku to version 13 or newer.");
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { ensureBound(); return; }
        if (Shizuku.shouldShowRequestPermissionRationale()) throw new IllegalStateException("Allow Cat Resolution in Shizuku → Authorized applications, then return here.");
        Shizuku.requestPermission(401);
    }

    private String shizukuState() {
        try {
            if (!Shizuku.pingBinder()) return "stopped";
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return "permission";
            if (remote != null && remote.asBinder().isBinderAlive()) return "ready";
            return binding ? "connecting" : "permission";
        } catch (Exception ignored) { return "stopped"; }
    }

    private JSONObject decode(String raw) throws Exception {
        JSONObject response = new JSONObject(raw);
        if (!response.optBoolean("ok")) throw new IllegalStateException(response.optString("error", "The Android operation failed."));
        JSONObject result = response.getJSONObject("display");
        display = result;
        store.recordDisplay(result);
        return result;
    }

    public JSONObject refreshDisplay() throws Exception {
        IDisplayService service = remote;
        if (service == null || !"ready".equals(shizukuState())) throw new IllegalStateException("Authorize Shizuku and wait for the connection before applying a resolution.");
        return decode(service.inspect());
    }

    public JSONObject displayAction(String action, JSONObject input) throws Exception {
        IDisplayService service = remote;
        if (service == null || !"ready".equals(shizukuState())) throw new IllegalStateException("Shizuku is not connected. Start it and authorize Cat Resolution first.");
        String result;
        switch (action) {
            case "apply": result = service.apply(input.getDouble("multiplier"), store.preferences().getInt("safetyTimeout")); break;
            case "confirm": result = service.confirm(); break;
            case "revert": result = service.revert(); break;
            case "restore": result = service.restore(); break;
            default: throw new IllegalArgumentException("Unsupported display action.");
        }
        return decode(result);
    }

    public JSONObject cachedDisplay() { return display; }

    public JSONObject state() throws Exception {
        JSONObject current = null;
        String stateError = connectionError == null ? "" : connectionError;
        if ("ready".equals(shizukuState())) {
            try { current = refreshDisplay(); }
            catch (Exception error) { stateError = error.getMessage(); }
        }
        JSONObject state = new JSONObject().put("native", true).put("shizuku", shizukuState())
            .put("overlayPermission", Settings.canDrawOverlays(context))
            .put("overlayRunning", ControlService.overlayVisible)
            .put("notificationPermission", Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            .put("deviceName", Build.MANUFACTURER + " " + Build.MODEL)
            .put("androidVersion", Build.VERSION.RELEASE)
            .put("display", current == null ? JSONObject.NULL : current)
            .put("presets", store.presets()).put("history", store.history()).put("preferences", store.preferences());
        if (stateError != null && !stateError.isEmpty()) state.put("error", stateError);
        return state;
    }
}
