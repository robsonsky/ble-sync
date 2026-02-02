package com.hailie.adapters.android.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.hailie.adapters.android.ble.AndroidBleAdapter
import com.hailie.adapters.android.ble.BleConfig
import com.hailie.adapters.android.ble.DeviceResolver
import com.hailie.adapters.android.store.AndroidStateStore
import com.hailie.adapters.android.telemetry.AndroidStructuredLogger
import com.hailie.adapters.android.telemetry.AndroidTelemetryPort
import com.hailie.adapters.android.telemetry.StructuredLogger
import com.hailie.domain.DeviceId
import com.hailie.runtime.ports.BlePort
import com.hailie.runtime.ports.StateStorePort
import com.hailie.runtime.ports.TelemetryPort
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.UUID

val androidAdaptersModule =
    module {

        // Logger + Telemetry
        single<StructuredLogger> {
            AndroidStructuredLogger(
                context = androidContext(),
                sessionId = java.util.UUID.randomUUID().toString(),
            )
        }
        single<TelemetryPort> { AndroidTelemetryPort(get()) }

        // State Store
        single<StateStorePort> { AndroidStateStore(androidContext(), Json) }

        // BLE Config — inject your real UUIDs here
        single {
            BleConfig(
                serviceUuid = UUID.fromString("0000xxxx-0000-1000-8000-00805f9b34fb"),
                countCharacteristicUuid = UUID.fromString("0000yyyy-0000-1000-8000-00805f9b34fb"),
                pageCharacteristicUuid = UUID.fromString("0000zzzz-0000-1000-8000-00805f9b34fb"),
                ackCharacteristicUuid = UUID.fromString("0000aaaa-0000-1000-8000-00805f9b34fb"),
            )
        }

        // Device resolver (MAC-based example)
        single<DeviceResolver> {
            val ctx: Context = androidContext()
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            DeviceResolver { deviceId: DeviceId -> adapter.getRemoteDevice(deviceId.raw) }
        }

        // BLE Adapter
        single<BlePort> { AndroidBleAdapter(androidContext(), get(), get(), get(), get()) }
    }
