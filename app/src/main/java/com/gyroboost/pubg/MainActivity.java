package com.gyroboost.pubg;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private static final int MIN_HZ = 50;
    private static final int MAX_HZ = 500;

    private TextView tvStatus;
    private TextView tvTargetHz;
    private SeekBar seekBar;
    private Button btnToggle;
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvTargetHz = findViewById(R.id.tvTargetHz);
        seekBar = findViewById(R.id.seekBar);
        btnToggle = findViewById(R.id.btnToggle);
        TextView tvHardwareInfo = findViewById(R.id.tvHardwareInfo);

        // 请求通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        SharedPreferences sp = getSharedPreferences(GyroBoostService.PREFS, MODE_PRIVATE);
        int targetHz = sp.getInt(GyroBoostService.KEY_TARGET_HZ, GyroBoostService.DEFAULT_TARGET_HZ);

        seekBar.setMax(MAX_HZ - MIN_HZ);
        seekBar.setProgress(targetHz - MIN_HZ);
        tvTargetHz.setText("目标采样率：" + targetHz + " Hz");

        // 显示硬件理论上限，帮助用户判断430Hz是否可达
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyro == null) {
            tvHardwareInfo.setText("警告：本设备未检测到陀螺仪传感器");
            btnToggle.setEnabled(false);
        } else {
            int minDelayUs = gyro.getMinDelay();
            int hardwareMaxHz = minDelayUs > 0 ? (int) (1_000_000L / minDelayUs) : 0;
            tvHardwareInfo.setText(hardwareMaxHz > 0
                    ? "本机陀螺仪硬件理论上限约：" + hardwareMaxHz + " Hz"
                    : "本机陀螺仪未上报硬件上限（连续模式）");
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int hz = progress + MIN_HZ;
                tvTargetHz.setText("目标采样率：" + hz + " Hz");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int hz = seekBar.getProgress() + MIN_HZ;
                getSharedPreferences(GyroBoostService.PREFS, MODE_PRIVATE)
                        .edit().putInt(GyroBoostService.KEY_TARGET_HZ, hz).apply();
                if (GyroBoostService.isRunning) {
                    // 修改后需要重启服务才能生效
                    restartService();
                    Toast.makeText(MainActivity.this, "已更新，服务重启后生效", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnToggle.setOnClickListener(v -> {
            if (GyroBoostService.isRunning) {
                stopService(new Intent(this, GyroBoostService.class));
            } else {
                Intent intent = new Intent(this, GyroBoostService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            }
            uiHandler.postDelayed(this::refreshStatus, 300);
        });

        refreshStatus();
        uiHandler.post(statusUpdater);
    }

    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            uiHandler.postDelayed(this, 1000);
        }
    };

    private void restartService() {
        stopService(new Intent(this, GyroBoostService.class));
        Intent intent = new Intent(this, GyroBoostService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void refreshStatus() {
        boolean running = GyroBoostService.isRunning;
        btnToggle.setText(running ? "停止加速" : "启动加速");
        if (running) {
            float measured = GyroBoostService.currentMeasuredHz;
            tvStatus.setText(measured > 0
                    ? String.format("运行中 ｜ 实测采样率约 %.0f Hz", measured)
                    : "运行中 ｜ 正在测速…");
        } else {
            tvStatus.setText("未运行");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }
}
