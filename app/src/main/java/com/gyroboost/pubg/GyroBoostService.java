package com.gyroboost.pubg;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

/**
 * 核心前台服务：注册陀螺仪监听，请求尽可能接近目标频率(默认430Hz)的采样周期。
 *
 * 重要说明：
 * registerListener 的第三个参数 samplingPeriodUs 只是"请求值"，
 * 系统会依据硬件驱动支持的档位就近量化，无 root 情况下无法强制精确锁定到某个数值。
 * 实际能达到的速率取决于 sensor.getMinDelay()（硬件支持的最快周期）。
 */
public class GyroBoostService extends Service implements SensorEventListener {

    public static final String CHANNEL_ID = "gyro_boost_channel";
    public static final String PREFS = "gyro_boost_prefs";
    public static final String KEY_TARGET_HZ = "target_hz";
    public static final int DEFAULT_TARGET_HZ = 430;

    public static volatile boolean isRunning = false;
    public static volatile float currentMeasuredHz = 0f;

    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private long lastTimestampNs = 0L;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (gyroSensor == null) {
            // 设备没有陀螺仪，直接停止
            stopSelf();
            return START_NOT_STICKY;
        }

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int targetHz = sp.getInt(KEY_TARGET_HZ, DEFAULT_TARGET_HZ);

        // 目标周期(微秒) = 1,000,000 / 目标频率
        int requestedPeriodUs = (int) (1_000_000L / targetHz);

        // 硬件支持的最快周期，用于告知用户理论上限
        int hardwareMinDelayUs = gyroSensor.getMinDelay();
        if (hardwareMinDelayUs > 0 && requestedPeriodUs < hardwareMinDelayUs) {
            // 请求值超出硬件能力，退化为硬件最快速率
            requestedPeriodUs = hardwareMinDelayUs;
        }

        sensorManager.registerListener(this, gyroSensor, requestedPeriodUs);

        startForeground(1, buildNotification(targetHz, 0));
        isRunning = true;

        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // 通过相邻两次回调的时间戳差值，反推系统实际给出的采样率
        if (lastTimestampNs != 0) {
            long deltaNs = event.timestamp - lastTimestampNs;
            if (deltaNs > 0) {
                currentMeasuredHz = 1_000_000_000f / deltaNs;
            }
        }
        lastTimestampNs = event.timestamp;

        // 每隔一段时间刷新一次通知显示的实时速率（避免过于频繁刷新通知）
        if (notificationManager != null) {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            int targetHz = sp.getInt(KEY_TARGET_HZ, DEFAULT_TARGET_HZ);
            notificationManager.notify(1, buildNotification(targetHz, currentMeasuredHz));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 无需处理
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        isRunning = false;
        currentMeasuredHz = 0f;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "陀螺仪加速服务", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(int targetHz, float measuredHz) {
        Intent stopIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);

        String text = measuredHz > 0
                ? String.format("目标 %dHz ｜ 实测约 %.0fHz", targetHz, measuredHz)
                : String.format("目标 %dHz ｜ 正在测速…", targetHz);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("陀螺仪加速运行中")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();
    }
}
