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
    private final ShizukuConnectionState connectionState = new ShizukuConnectionState();
    private final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile IDisplayService remote;
    private volatile boolean binderReceived;
    private volatile String connectionError = "";
    private volatile JSONObject display;
    private ServiceConnection activeConnection;
    private IBinder.DeathRecipient remoteDeath;
    private boolean foreground;
    private boolean authorizeOnReturn;
    private long permissionAttempt;

    public AppController(Context context) {
        this.context = context.getApplicationContext();
        store = new CatStore(this.context);
        serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(this.context, DisplayUserService.class))
            .daemon(true).processNameSuffix("cat_display").debuggable(false)
            .version(BuildConfig.VERSION_CODE).tag("cat-display");
        Shizuku.addBinderReceivedListenerSticky(() -> {
            binderReceived = true;
            connectionError = "";
            if (foreground && authorizeOnReturn) requestPermission();
            else ensureBound();
            notifyChanged();
        }, main);
        Shizuku.addBinderDeadListener(() -> {
            binderReceived = false;
            detachDisplayService();
            connectionState.serverStopped();
            ++permissionAttempt;
            connectionError = "Shizuku stopped. Open Shizuku, tap Start, and wait for 'Shizuku is running'. Pairing alone does not start it.";
            notifyChanged();
        }, main);
        Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
            if (requestCode != 401) return;
            ++permissionAttempt;
            authorizeOnReturn = false;
            boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
            connectionState.permissionResult(granted);
            connectionError = granted ? "" : "Access was not allowed. Open Shizuku → Authorized applications → Cat Resolution, enable access, then return.";
            if (granted) ensureBound();
            notifyChanged();
        }, main);
    }

    public void addStateListener(Runnable listener) { listeners.add(listener); }
    public void removeStateListener(Runnable listener) { listeners.remove(listener); }
    private void notifyChanged() { main.post(() -> { for (Runnable listener : listeners) listener.run(); }); }

    public void onForeground() {
        foreground = true;
        connectionState.resetReconnectAttempts();
        if (authorizeOnReturn && binderReceived && Shizuku.pingBinder()) requestPermission();
        else ensureBound();
        notifyChanged();
    }

    public void onBackground() { foreground = false; }
    public void prepareForShizuku() { authorizeOnReturn = true; }

    public boolean reconnectDisplay() {
        detachDisplayService();
        return requestPermission();
    }

    public void recheckConnection() {
        connectionError = "";
        if (hasPermission()) {
            connectionState.permissionResult(true);
            authorizeOnReturn = false;
            ensureBound();
        } else if (!Shizuku.pingBinder()) {
            connectionError = "No Shizuku connection. In Shizuku, tap Start after pairing. If it already says running, restart Shizuku and reopen this app in the same Android profile.";
        }
        notifyChanged();
    }

    private boolean hasPermission() {
        try {
            return binderReceived && Shizuku.pingBinder() && !Shizuku.isPreV11()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException ignored) { return false; }
    }

    private boolean displayServiceAlive() {
        IDisplayService service = remote;
        return service != null && service.asBinder().pingBinder();
    }

    private void detachDisplayService() {
        connectionState.invalidateBinding();
        IDisplayService service = remote;
        remote = null;
        if (service != null && remoteDeath != null) {
            try { service.asBinder().unlinkToDeath(remoteDeath, 0); }
            catch (RuntimeException ignored) { }
        }
        remoteDeath = null;
        ServiceConnection previous = activeConnection;
        activeConnection = null;
        if (binderReceived && Shizuku.pingBinder()) {
            // Also clear SDK callbacks left behind by a dead Shizuku server; never kill the recovery daemon.
            try { Shizuku.unbindUserService(serviceArgs, previous, false); }
            catch (RuntimeException ignored) { }
        }
    }

    public void ensureBound() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::ensureBound); return; }
        if (!hasPermission() || displayServiceAlive() || connectionState.isBinding()) return;
        detachDisplayService();
        long attempt = connectionState.beginBinding();
        connectionError = "";
        activeConnection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                main.post(() -> {
                    if (!connectionState.finishBinding(attempt)) return;
                    try {
                        if (binder == null || !binder.pingBinder()) throw new IllegalStateException("The display service returned an inactive connection.");
                        remoteDeath = () -> main.post(() -> disconnected(attempt));
                        binder.linkToDeath(remoteDeath, 0);
                        remote = IDisplayService.Stub.asInterface(binder);
                        connectionError = "";
                    } catch (Exception error) {
                        detachDisplayService();
                        connectionError = "Shizuku access is allowed, but display control failed: " + message(error);
                    }
                    notifyChanged();
                });
            }
            @Override public void onServiceDisconnected(ComponentName name) { main.post(() -> disconnected(attempt)); }
            @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
            @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
        };
        try {
            Shizuku.bindUserService(serviceArgs, activeConnection);
            main.postDelayed(() -> {
                if (!connectionState.finishBinding(attempt)) return;
                detachDisplayService();
                connectionError = "Shizuku permission is granted, but the display service did not start. Tap Reconnect display control. If it fails again, restart Shizuku and reopen Cat Resolution.";
                notifyChanged();
            }, 15000);
        } catch (RuntimeException error) {
            detachDisplayService();
            connectionError = "Could not connect the display service: " + message(error);
        }
        notifyChanged();
    }

    private void disconnected(long attempt) {
        if (!connectionState.isCurrentBinding(attempt)) return;
        detachDisplayService();
        boolean retry = connectionState.takeAutomaticReconnect();
        connectionError = retry ? "Display control disconnected. Reconnecting to Shizuku…"
            : "The display service keeps disconnecting. Restart Shizuku, then tap Reconnect display control.";
        notifyChanged();
        if (retry) main.postDelayed(this::ensureBound, 500);
    }

    public boolean requestPermission() {
        connectionState.resetReconnectAttempts();
        authorizeOnReturn = true;
        connectionError = "";
        if (!Shizuku.pingBinder()) {
            connectionError = "Pairing is complete only for debugging. In Shizuku, tap Start, wait for 'Shizuku is running', then return here to allow Cat Resolution.";
            notifyChanged();
            return false;
        }
        if (!binderReceived) { notifyChanged(); return true; }
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 13) {
                connectionError = "Update Shizuku to version 13 or newer, start it again, then return here.";
                authorizeOnReturn = false;
                notifyChanged();
                return false;
            }
            if (hasPermission()) {
                connectionState.permissionResult(true);
                authorizeOnReturn = false;
                ++permissionAttempt;
                ensureBound();
                notifyChanged();
                return true;
            }
            if (connectionState.isRequestingPermission()) return true;
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                connectionState.permissionResult(false);
                connectionError = "Access was previously denied. In Shizuku → Authorized applications, turn on Cat Resolution, then return here.";
                notifyChanged();
                return false;
            }
            connectionState.permissionRequested();
            authorizeOnReturn = false;
            long attempt = ++permissionAttempt;
            Shizuku.requestPermission(401);
            main.postDelayed(() -> {
                if (attempt != permissionAttempt || !connectionState.isRequestingPermission()) return;
                connectionState.permissionRequestTimedOut();
                if (hasPermission()) ensureBound();
                else connectionError = "No permission response. Open Shizuku → Authorized applications → Cat Resolution and enable access, then tap Recheck connection.";
                notifyChanged();
            }, 30000);
            notifyChanged();
            return true;
        } catch (RuntimeException error) {
            connectionState.permissionRequestTimedOut();
            connectionError = "Shizuku authorization failed: " + message(error);
            notifyChanged();
            return false;
        }
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private String shizukuState() {
        if (Shizuku.pingBinder() && !binderReceived) return "waiting";
        return connectionState.status(Shizuku.pingBinder(), hasPermission(), displayServiceAlive());
    }

    private boolean shizukuInstalled() {
        try { context.getPackageManager().getApplicationInfo("moe.shizuku.privileged.api", 0); return true; }
        catch (PackageManager.NameNotFoundException ignored) { return false; }
    }

    private JSONObject decode(String raw) throws Exception {
        JSONObject response = new JSONObject(raw);
        if (!response.optBoolean("ok")) throw new IllegalStateException(response.optString("error", "The Android operation failed."));
        JSONObject result = response.getJSONObject("display");
        display = result;
        store.recordDisplay(result);
        return result;
    }

    private String connectionHelp() {
        if (!Shizuku.pingBinder()) return "Shizuku is not running. Open Shizuku, tap Start after pairing, and return here.";
        if (!hasPermission()) return "Allow Cat Resolution in Shizuku → Authorized applications, then return here.";
        return "Shizuku permission is already granted. Open the connection screen and tap Reconnect display control.";
    }

    public JSONObject refreshDisplay() throws Exception {
        IDisplayService service = remote;
        if (service == null || !"ready".equals(shizukuState())) throw new IllegalStateException(connectionHelp());
        return decode(service.inspect());
    }

    public JSONObject displayAction(String action, JSONObject input) throws Exception {
        IDisplayService service = remote;
        if (service == null || !"ready".equals(shizukuState())) throw new IllegalStateException(connectionHelp());
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
        String status = shizukuState();
        String stateError = connectionError == null ? "" : connectionError;
        if ("ready".equals(status)) {
            try { current = refreshDisplay(); stateError = ""; }
            catch (Exception error) { status = "error"; stateError = "Shizuku is authorized, but Android display access failed: " + message(error); }
        }
        JSONObject state = new JSONObject().put("native", true).put("shizuku", status)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("shizukuInstalled", shizukuInstalled())
            .put("shizukuRunning", Shizuku.pingBinder())
            .put("shizukuPermissionGranted", hasPermission())
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
