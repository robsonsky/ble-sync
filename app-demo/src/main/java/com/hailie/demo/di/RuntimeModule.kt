package com.hailie.demo.di

import com.hailie.domain.DeviceId
import com.hailie.domain.model.initialSyncAggregate
import com.hailie.domain.policy.AdaptivePageSizingPolicy
import com.hailie.domain.policy.ExponentialRetryPolicy
import com.hailie.domain.policy.SimpleBreakerPolicy
import com.hailie.domain.saga.DefaultSaga
import com.hailie.domain.saga.Saga
import com.hailie.runtime.actor.SyncActor
import com.hailie.runtime.ports.BlePort
import com.hailie.runtime.ports.ClockPort
import com.hailie.runtime.ports.DeliveryPort
import com.hailie.runtime.ports.StateStorePort
import com.hailie.runtime.ports.TelemetryPort
import org.koin.dsl.module

val runtimeModule =
    module {
        // Policies (provide here, or reuse existing beans if you already have them)
        single { AdaptivePageSizingPolicy(minPage = 20, maxPage = 200, growStep = 20, shrinkStep = 20) }
        // connect
        single { SimpleBreakerPolicy(failuresToOpen = 1, coolDownMs = 300) }
        // read
        single { SimpleBreakerPolicy() }
        // deliver
        single { SimpleBreakerPolicy() }
        // ack
        single { SimpleBreakerPolicy() }
        single {
            ExponentialRetryPolicy(
                maxAttempts = 3,
                minBackoffMs = 100,
                maxBackoffMs = 1_000,
                jitterRatio = 0.0,
            )
        }

        // Saga
        single<Saga> {
            DefaultSaga(
                retryPolicy = get(),
                // if you need distinct instances per stage, name them
                breakerForConnect = get(),
                breakerForRead = get(),
                breakerForDeliver = get(),
                breakerForAck = get(),
                pageSizingPolicy = get(),
            )
        }

        // SyncActor factory – takes a DeviceId at creation time
        factory { (deviceId: DeviceId) ->
            SyncActor(
                initialState = initialSyncAggregate(deviceId = deviceId, initialPageSize = 50),
                saga = get<Saga>(),
                clock = get<ClockPort>(),
                ble = get<BlePort>(),
                delivery = get<DeliveryPort>(),
                store = get<StateStorePort>(),
                telemetry = get<TelemetryPort>(),
            )
        }
    }
