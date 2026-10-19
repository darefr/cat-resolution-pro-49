package dev.cat.resolution;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class MainActivity extends Activity {
    private static final String ASSET_ORIGIN = "https://appassets.androidplatform.net";
    private WebView webView;
    private AppController controller;
    private final Runnable connectionListener = () -> {
        if (webView != null && !isDestroyed()) webView.evaluateJavascript("window.dispatchEvent(new Event('cat-device-changed'));", null);
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        controller = ((CatApplication) getApplication()).controller();
        PackageInfo engine = WebView.getCurrentWebViewPackage();
        if (engine != null && engine.versionName != null) {
            try {
                if (Integer.parseInt(engine.versionName.split("\\.")[0]) < 111) {
                    new AlertDialog.Builder(this).setTitle("Update Android System WebView")
                        .setMessage("This offline interface needs WebView 111 or newer. Update Android System WebView or Chrome, then reopen Cat Resolution.")
                        .setPositiveButton("Open update page", (dialog, which) -> openExternal("https://play.google.com/store/apps/details?id=com.google.android.webview"))
                        .setNegativeButton("Close", (dialog, which) -> finish()).setCancelable(false).show();
                    return;
                }
            } catch (NumberFormatException ignored) { }
        }
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
            .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost())) {
                    WebResourceResponse response = assets.shouldInterceptRequest(uri);
                    if (response != null) return response;
                }
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().equals(ASSET_ORIGIN + "/index.html");
            }
        });
        webView.addJavascriptInterface(new NativeBridge(), "CatNative");
        controller.addStateListener(connectionListener);
        webView.loadUrl(ASSET_ORIGIN + "/index.html");
    }

    @Override protected void onResume() {
        super.onResume();
        if (controller != null) controller.onForeground();
    }

    @Override protected void onPause() {
        if (controller != null) controller.onBackground();
        super.onPause();
    }

    private void openShizuku() {
        controller.prepareForShizuku();
        Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) startActivity(launch);
        else openExternal("https://shizuku.rikka.app/download/");
    }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception error) { Toast.makeText(this, "No browser is available to open this link.", Toast.LENGTH_LONG).show(); }
    }

    private void onUi(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        runOnUiThread(() -> {
            try { if (isFinishing() || isDestroyed()) throw new IllegalStateException("Reopen Cat Resolution and try again."); action.run(); }
            catch (RuntimeException failure) { error.set(failure); }
            finally { done.countDown(); }
        });
        if (!done.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("The Android interface is not responding.");
        if (error.get() != null) throw error.get();
    }

    private void requireRecoveryNotifications() throws Exception {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            onUi(() -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 402));
            throw new IllegalStateException("Allow recovery notifications, then try again. They provide a Restore action outside the app.");
        }
    }

    private void startController(String action, double multiplier) {
        startForegroundService(new Intent(this, ControlService.class).setAction(action).putExtra("multiplier", multiplier));
    }

    private final class NativeBridge {
        @JavascriptInterface public void request(String raw) {
            if (raw == null || raw.length() > 8192) return;
            controller.worker.execute(() -> {
                String id = "";
                JSONObject response = new JSONObject();
                try {
                    JSONObject message = new JSONObject(raw);
                    id = message.getString("id");
                    if (id.length() > 80) return;
                    String action = message.getString("action");
                    JSONObject input = message.optJSONObject("payload");
                    if (input == null) input = new JSONObject();
                    switch (action) {
                        case "state": break;
                        case "requestShizuku": onUi(() -> { if (!controller.requestPermission()) openShizuku(); }); break;
                        case "reconnectShizuku": onUi(() -> { if (!controller.reconnectDisplay()) openShizuku(); }); break;
                        case "recheckShizuku": onUi(controller::recheckConnection); break;
                        case "openShizuku": onUi(MainActivity.this::openShizuku); break;
                        case "overlayPermission": onUi(() -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))); break;
                        case "notificationPermission":
                            onUi(() -> {
                                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 402);
                                else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                            });
                            break;
                        case "startOverlay":
                            requireRecoveryNotifications();
                            if (!Settings.canDrawOverlays(MainActivity.this)) throw new IllegalStateException("Allow display over other apps first.");
                            controller.refreshDisplay();
                            onUi(() -> startController(ControlService.SHOW, 1.16));
                            break;
                        case "stopOverlay": onUi(() -> startController(ControlService.HIDE, 1.16)); break;
                        case "apply":
                            requireRecoveryNotifications();
                            double multiplier = input.getDouble("multiplier");
                            DisplaySpec.validateMultiplier(multiplier);
                            controller.refreshDisplay();
                            onUi(() -> startController(ControlService.TRACK, multiplier));
                            controller.displayAction(action, input);
                            break;
                        case "confirm": case "restore": case "revert": controller.displayAction(action, input); break;
                        case "savePreset": controller.store.savePreset(input.getString("name"), input.getDouble("multiplier")); break;
                        case "deletePreset": controller.store.deletePreset(input.getLong("id")); break;
                        case "clearHistory": controller.store.clearHistory(); break;
                        case "saveSettings": controller.store.savePreferences(input); break;
                        default: throw new IllegalArgumentException("Unknown native action.");
                    }
                    response.put("id", id).put("result", controller.state());
                } catch (Exception error) {
                    try { response.put("id", id).put("error", error.getMessage() == null ? "The Android operation failed." : error.getMessage()); }
                    catch (Exception ignored) { }
                }
                String json = response.toString();
                runOnUiThread(() -> {
                    if (webView != null && !isDestroyed()) webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('cat-native-result',{detail:" + json + "}));", null);
                });
            });
        }
    }

    @Override protected void onDestroy() {
        if (controller != null) controller.removeStateListener(connectionListener);
        if (webView != null) {
            webView.removeJavascriptInterface("CatNative");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
