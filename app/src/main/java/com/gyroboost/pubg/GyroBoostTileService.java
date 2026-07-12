package com.gyroboost.pubg;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class GyroBoostTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, GyroBoostService.class);
        if (GyroBoostService.isRunning) {
            stopService(intent);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
        // 给服务一点时间更新状态标志
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(this::updateTile, 300);
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(GyroBoostService.isRunning ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("陀螺仪加速");
        tile.updateTile();
    }
}
