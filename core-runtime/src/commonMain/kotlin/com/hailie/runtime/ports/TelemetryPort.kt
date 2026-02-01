package com.hailie.runtime.ports

import com.hailie.domain.TimestampMs

/**
 * Structured telemetry. Keep it flat and cheap to serialize.
 */
data class TelemetryEvent(
    val name: String,
    val at: TimestampMs,
    val deviceId: String,
    val data: Map<String, String> = emptyMap(),
)

interface TelemetryPort {
    fun emit(event: TelemetryEvent)
}
