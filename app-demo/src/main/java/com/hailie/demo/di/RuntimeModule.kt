package com.hailie.demo.di

import com.hailie.runtimeactor.BleSyncActor
import org.koin.dsl.module

val runtimeModule =
    module {
        single {
            BleSyncActor(
//                ble = get<BlePort>(),
//                delivery = get<DeliveryPort>(),
//                store = get<StateStorePort>(),
//                telemetry = get<TelemetryPort>(),
//                clock = get<ClockPort>(),
            )
        }
    }
