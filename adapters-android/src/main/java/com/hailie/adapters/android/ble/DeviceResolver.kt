package com.hailie.adapters.android.ble

import android.bluetooth.BluetoothDevice
import com.hailie.domain.DeviceId

fun interface DeviceResolver {
    fun resolve(deviceId: DeviceId): BluetoothDevice
}
