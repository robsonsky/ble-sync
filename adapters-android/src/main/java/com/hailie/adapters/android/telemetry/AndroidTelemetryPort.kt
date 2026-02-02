package com.hailie.adapters.android.telemetry

import com.hailie.runtime.ports.TelemetryEvent
import com.hailie.runtime.ports.TelemetryPort

class AndroidTelemetryPort(
    private val logger: StructuredLogger,
) : TelemetryPort {
    override fun emit(ev: TelemetryEvent) {
        val fields =
            buildMap<String, Any?> {
                put("device_id", ev.deviceId)
                put("event", ev.name)
                ev.data?.let { putAll(it) }
            }
        when (ev.name) {
            "snapshot_saved", "snapshot_restored", "retry_scheduled" -> {
                logger.info(fields)
                logger.flush()
            }
            "sync_failed" -> {
                logger.warn(fields)
                logger.flush()
            }
            else -> logger.info(fields)
        }
    }
}
