package com.hailie.demo.di

import com.hailie.fakes.FakeBlePort
import com.hailie.fakes.FakeClockPort
import com.hailie.fakes.FakeDeliveryPort
import com.hailie.fakes.FakeStateStorePort
import com.hailie.fakes.FakeTelemetryPort
import com.hailie.ports.BlePort
import com.hailie.ports.ClockPort
import com.hailie.ports.DeliveryPort
import com.hailie.ports.StateStorePort
import com.hailie.ports.TelemetryPort
import org.koin.dsl.module

val fakesModule =
    module {
        single<BlePort> { FakeBlePort() }
        single<DeliveryPort> { FakeDeliveryPort() }
        single<StateStorePort> { FakeStateStorePort() }
        single<TelemetryPort> { FakeTelemetryPort() }
        single<ClockPort> { FakeClockPort() }
    }
