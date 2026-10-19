package dev.cat.resolution;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

public final class ControlService extends Service {
    public static final String SHOW = "dev.cat.resolution.SHOW";
    public static final String HIDE = "dev.cat.resolution.HIDE";
    public static final String TRACK = "dev.cat.resolution.TRACK";
    private static final String RESTORE_STOP = "dev.cat.resolution.RESTORE_STOP";
    private static final String CHANNEL = "cat-display-controls";
    private static final int NOTIFICATION = 21;
    public static volatile boolean overlayVisible;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AppController controller;
    private WindowManager windows;
    private Context overlayContext;
    private WindowManager.LayoutParams windowParams;
    private View overlay;
    private TextView valueLabel;
    private TextView statusLabel;
    private Button applyButton;
    private Button restoreButton;
    private SeekBar multiplierSlider;
    private PowerManager.WakeLock wakeLock;
    private boolean expanded;
    private boolean working;
    private boolean destroyed;
    private boolean pollingStarted;
    private double multiplier = 1.16;
    private JSONObject currentDisplay;
    private JSONObject preferences = new JSONObject();
    private JSONArray customPresets = new JSONArray();
    private long started;
    private int brand;
    private int surface;
    private int ink;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                controller.worker.execute(() -> {
                    if (Boolean.parseBoolean(controller.store.setting("restoreOnLock", "true"))) {
                        try { controller.displayAction("restore", new JSONObject()); }
                        catch (Exception error) { showError(error); }
                    }
                });
            }
        }
    };

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            controller.worker.execute(() -> {
                JSONObject display = null;
                String error = "";
                try {
                    display = controller.refreshDisplay();
                    preferences = controller.store.preferences();
                    customPresets = controller.store.presets();
                } catch (Exception failure) { error = failure.getMessage(); }
                JSONObject result = display;
                String message = error;
                main.post(() -> {
                    if (destroyed) return;
                    currentDisplay = result;
                    updateOverlay();
                    boolean pending = result != null && result.optBoolean("pending");
                    if (!pending && wakeLock.isHeld()) wakeLock.release();
                    String status = !message.isEmpty() ? "Reconnect Shizuku to restore control" : pending ? "Confirm display change · " + result.optInt("remainingSeconds") + "s" : "Display controls active · tap to open";
                    getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(status));
                    if (!overlayVisible && result != null && !result.optBoolean("active") && !pending && SystemClock.elapsedRealtime() - started > 5000) {
                        stopSelf();
                        return;
                    }
                    main.postDelayed(this, pending ? 1000 : 3000);
                });
            });
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        controller = ((CatApplication) getApplication()).controller();
        android.view.Display display = getSystemService(android.hardware.display.DisplayManager.class).getDisplay(android.view.Display.DEFAULT_DISPLAY);
        overlayContext = createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        windows = overlayContext.getSystemService(WindowManager.class);
        brand = getColor(R.color.brand);
        surface = getColor(R.color.surface);
        ink = getColor(R.color.ink);
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CatResolution:confirmation");
        wakeLock.setReferenceCounted(false);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Display recovery controls", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Restore the original display and stop floating controls.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, filter);
        started = SystemClock.elapsedRealtime();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification initial = notification("Display controls active · tap to open");
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION, initial);
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        if (RESTORE_STOP.equals(action)) perform("restore", true);
        else if (HIDE.equals(action)) removeOverlay();
        else if (SHOW.equals(action)) {
            if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Allow display over other apps first.", Toast.LENGTH_LONG).show(); stopSelf(); return START_NOT_STICKY; }
            multiplier = Math.max(1.01, Math.min(1.3, intent.getDoubleExtra("multiplier", multiplier)));
            showOverlay(false);
        } else if (TRACK.equals(action)) {
            multiplier = Math.max(1.01, Math.min(1.3, intent.getDoubleExtra("multiplier", multiplier)));
            wakeLock.acquire(45000);
        }
        if (!pollingStarted) {
            pollingStarted = true;
            main.post(poll);
        }
        return START_NOT_STICKY;
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent restore = PendingIntent.getService(this, 1, new Intent(this, ControlService.class).setAction(RESTORE_STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("Cat Resolution")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, "Restore & stop", restore).build()).build();
    }

    private int dp(float value) { return Math.round(value * overlayContext.getResources().getDisplayMetrics().density); }

    private GradientDrawable background(int color, int border, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), border);
        return drawable;
    }

    private TextView label(String text, int size, boolean bold) {
        TextView label = new TextView(overlayContext);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(ink);
        label.setTypeface(Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD_ITALIC : Typeface.NORMAL));
        return label;
    }

    private Button button(String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(overlayContext);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setTextColor(primary ? surface : brand);
        button.setBackground(background(primary ? brand : surface, brand, 10));
        button.setOnClickListener(listener);
        return button;
    }

    private void addButton(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(3), dp(4), dp(3), dp(4));
        row.addView(button, params);
    }

    private void showOverlay(boolean expand) {
        int x = windowParams == null ? dp(16) : windowParams.x;
        int y = windowParams == null ? dp(110) : windowParams.y;
        removeOverlay();
        expanded = expand;
        valueLabel = null;
        applyButton = null;
        restoreButton = null;
        multiplierSlider = null;
        statusLabel = null;
        if (!expand) {
            ImageView bubble = new ImageView(overlayContext);
            bubble.setImageResource(R.drawable.cat_emblem);
            bubble.setBackground(background(surface, brand, 100));
            bubble.setClipToOutline(true);
            bubble.setContentDescription("Open Cat Resolution floating controls");
            bubble.setOnClickListener(view -> showOverlay(true));
            makeDraggable(bubble);
            overlay = bubble;
        } else {
            LinearLayout panel = new LinearLayout(overlayContext);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(12), dp(8), dp(12), dp(12));
            panel.setBackground(background(surface, brand, 18));
            LinearLayout header = new LinearLayout(overlayContext);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = label("CAT CONTROL", 18, true);
            title.setTextColor(brand);
            title.setContentDescription("Drag floating controls. Tap to collapse.");
            title.setPadding(dp(4), dp(10), dp(4), dp(10));
            title.setOnClickListener(view -> showOverlay(false));
            makeDraggable(title);
            header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
            Button collapse = button("−", false, view -> showOverlay(false));
            collapse.setContentDescription("Collapse floating controls");
            header.addView(collapse, new LinearLayout.LayoutParams(dp(44), dp(40)));
            panel.addView(header);
            LinearLayout adjust = new LinearLayout(overlayContext);
            adjust.setGravity(Gravity.CENTER_VERTICAL);
            Button minus = button("−", false, view -> setMultiplier(multiplier - 0.01));
            minus.setContentDescription("Decrease multiplier");
            adjust.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(44)));
            valueLabel = label("", 36, true);
            valueLabel.setGravity(Gravity.CENTER);
            adjust.addView(valueLabel, new LinearLayout.LayoutParams(0, dp(60), 1));
            Button plus = button("+", false, view -> setMultiplier(multiplier + 0.01));
            plus.setContentDescription("Increase multiplier");
            adjust.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
            panel.addView(adjust);
            multiplierSlider = new SeekBar(overlayContext);
            multiplierSlider.setMin(101);
            multiplierSlider.setMax(130);
            multiplierSlider.setProgress((int) Math.round(multiplier * 100));
            multiplierSlider.setContentDescription("Stretch multiplier");
            multiplierSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(brand));
            multiplierSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(brand));
            multiplierSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { if (fromUser) setMultiplier(value / 100.0); }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
            panel.addView(multiplierSlider, new LinearLayout.LayoutParams(-1, dp(40)));
            LinearLayout presets = new LinearLayout(overlayContext);
            double[] defaults = {1.05, 1.10, 1.11, 1.15};
            for (int i = 0; i < 4; i++) {
                JSONObject custom = customPresets.optJSONObject(i);
                final double factor = custom == null ? defaults[i] : custom.optDouble("multiplier", defaults[i]);
                addButton(presets, button(String.format(Locale.US, "%.2f×", factor), false, view -> setMultiplier(factor)));
            }
            panel.addView(presets);
            LinearLayout actions = new LinearLayout(overlayContext);
            applyButton = button("Apply", true, view -> perform(currentDisplay != null && currentDisplay.optBoolean("pending") ? "confirm" : "apply", false));
            restoreButton = button("Restore", false, view -> perform(currentDisplay != null && currentDisplay.optBoolean("pending") ? "revert" : "restore", false));
            addButton(actions, applyButton);
            addButton(actions, restoreButton);
            panel.addView(actions);
            statusLabel = label("Confirm changes before the timer ends", 14, false);
            statusLabel.setGravity(Gravity.CENTER);
            statusLabel.setPadding(0, dp(6), 0, 0);
            panel.addView(statusLabel);
            overlay = panel;
        }
        int bubbleSize = dp(preferences.optInt("overlaySize", 56));
        windowParams = new WindowManager.LayoutParams(expand ? dp(292) : bubbleSize, expand ? WindowManager.LayoutParams.WRAP_CONTENT : bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowParams.x = x;
        windowParams.y = y;
        windowParams.alpha = preferences.optInt("overlayOpacity", 90) / 100f;
        try {
            windows.addView(overlay, windowParams);
            overlayVisible = true;
            overlay.post(this::clampPosition);
            updateOverlay();
        } catch (Exception error) { overlay = null; overlayVisible = false; showError(error); }
    }

    private void setMultiplier(double value) {
        multiplier = Math.round(Math.max(1.01, Math.min(1.30, value)) * 100) / 100.0;
        updateOverlay();
    }

    private void updateOverlay() {
        if (overlay == null) return;
        if (!Settings.canDrawOverlays(this)) { removeOverlay(); return; }
        if (valueLabel != null) valueLabel.setText(String.format(Locale.US, "%.2f×", multiplier));
        boolean pending = currentDisplay != null && currentDisplay.optBoolean("pending");
        if (multiplierSlider != null) { multiplierSlider.setProgress((int) Math.round(multiplier * 100)); multiplierSlider.setEnabled(!working && !pending); }
        if (applyButton != null) {
            applyButton.setText(pending ? "Keep · " + currentDisplay.optInt("remainingSeconds") + "s" : "Apply");
            applyButton.setEnabled(!working && currentDisplay != null);
            restoreButton.setText(pending ? "Revert" : "Restore");
            restoreButton.setEnabled(!working && currentDisplay != null);
            statusLabel.setText(currentDisplay == null ? "Reconnect Shizuku to continue" : pending ? "Reverts automatically without confirmation" : currentDisplay.optBoolean("active") ? "Resolution active · restore anytime" : "Ready · density stays unchanged");
        }
        windowParams.alpha = preferences.optInt("overlayOpacity", 90) / 100f;
        try { windows.updateViewLayout(overlay, windowParams); }
        catch (Exception error) { removeOverlay(); }
    }

    private void makeDraggable(View handle) {
        final float[] start = new float[4];
        final boolean[] moved = {false};
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        handle.setOnTouchListener((view, event) -> {
            if (windowParams == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = event.getRawX(); start[1] = event.getRawY(); start[2] = windowParams.x; start[3] = windowParams.y; moved[0] = false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - start[0]; float dy = event.getRawY() - start[1];
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved[0] = true;
                    if (moved[0]) { windowParams.x = (int) (start[2] + dx); windowParams.y = (int) (start[3] + dy); clampPosition(); }
                    return true;
                case MotionEvent.ACTION_UP: if (!moved[0]) view.performClick(); return true;
                case MotionEvent.ACTION_CANCEL: return true;
                default: return false;
            }
        });
    }

    private void clampPosition() {
        if (overlay == null || windowParams == null) return;
        android.view.WindowMetrics metrics = windows.getCurrentWindowMetrics();
        android.graphics.Rect bounds = metrics.getBounds();
        android.graphics.Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars());
        windowParams.x = Math.max(insets.left, Math.min(windowParams.x, Math.max(insets.left, bounds.width() - insets.right - overlay.getMeasuredWidth())));
        windowParams.y = Math.max(insets.top, Math.min(windowParams.y, Math.max(insets.top, bounds.height() - insets.bottom - overlay.getMeasuredHeight())));
        try { windows.updateViewLayout(overlay, windowParams); }
        catch (Exception error) { removeOverlay(); }
    }

    private void perform(String action, boolean stopAfter) {
        if (working) return;
        working = true;
        if ("apply".equals(action)) wakeLock.acquire(45000);
        updateOverlay();
        double selected = multiplier;
        controller.worker.execute(() -> {
            try {
                JSONObject data = controller.displayAction(action, new JSONObject().put("multiplier", selected));
                main.post(() -> {
                    working = false;
                    currentDisplay = data;
                    if (!data.optBoolean("pending") && wakeLock.isHeld()) wakeLock.release();
                    if (stopAfter) { removeOverlay(); stopSelf(); } else updateOverlay();
                });
            } catch (Exception error) {
                main.post(() -> { working = false; updateOverlay(); });
                showError(error);
            }
        });
    }

    private void showError(Exception error) {
        main.post(() -> Toast.makeText(this, error.getMessage() == null ? "Display control failed. Reopen the app." : error.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void removeOverlay() {
        if (overlay != null) {
            try { windows.removeView(overlay); } catch (IllegalArgumentException ignored) { }
            overlay = null;
        }
        overlayVisible = false;
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (overlayVisible) showOverlay(expanded);
    }

    @Override public void onTaskRemoved(Intent rootIntent) { perform("restore", true); }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        removeOverlay();
        unregisterReceiver(screenReceiver);
        if (wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
