package com.hailie.adapters.android.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal object BluetoothPermission {
    fun hasConnectPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat
                .checkSelfPermission(
                    ctx,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            // On older Android, BLUETOOTH/ADMIN are normal permissions (granted at install)
            true
        }
    }

    fun requireConnect(ctx: Context) {
        if (!hasConnectPermission(ctx)) {
            throw SecurityException(
                "Missing BLUETOOTH_CONNECT permission (Android 12+). Request it at runtime before using BLE.",
            )
        }
    }
}
