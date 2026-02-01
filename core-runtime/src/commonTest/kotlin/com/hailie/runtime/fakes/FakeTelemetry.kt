package com.hailie.runtime.fakes

import com.hailie.runtime.ports.TelemetryEvent
import com.hailie.runtime.ports.TelemetryPort

class FakeTelemetry : TelemetryPort {
    private val _events = mutableListOf<TelemetryEvent>()
    val events: List<TelemetryEvent> get() = _events.toList()

    override fun emit(event: TelemetryEvent) {
        _events += event
    }

    fun clear() {
        _events.clear()
    }
}
