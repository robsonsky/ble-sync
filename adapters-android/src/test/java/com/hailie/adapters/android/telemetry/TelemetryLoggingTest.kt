package com.hailie.adapters.android.telemetry

import com.hailie.domain.TimestampMs
import com.hailie.runtime.ports.TelemetryEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeLogger : StructuredLogger {
    val lines = mutableListOf<Pair<String, Map<String, Any?>>>()

    override fun info(fields: Map<String, Any?>) {
        lines += "INFO" to fields
    }

    override fun warn(fields: Map<String, Any?>) {
        lines += "WARN" to fields
    }

    override fun error(
        fields: Map<String, Any?>,
        throwable: Throwable?,
    ) {
        lines += "ERROR" to (fields + ("ex" to throwable?.message))
    }

    override fun flush() {
        // FLUSH
    }
}

class TelemetryLoggingTest {
    @Test
    fun snapshot_events_are_info_and_flushed() {
        val fake = FakeLogger()
        val t = AndroidTelemetryPort(fake)

        t.emit(
            TelemetryEvent(
                name = "snapshot_saved",
                at = TimestampMs(1L),
                deviceId = "dev1",
                data = mapOf("acked" to "50"),
            ),
        )
        t.emit(
            TelemetryEvent(
                name = "snapshot_restored",
                at = TimestampMs(2L),
                deviceId = "dev1",
                data = emptyMap<String, String>(),
            ),
        )

        assertEquals(2, fake.lines.size)
        assertEquals("INFO", fake.lines[0].first)
        assertTrue(fake.lines[0].second["device_id"] == "dev1")
        assertEquals("INFO", fake.lines[1].first)
        assertTrue(fake.lines[1].second["event"] == "snapshot_restored")
    }

    @Test
    fun sync_failed_is_warn() {
        val fake = FakeLogger()
        val t = AndroidTelemetryPort(fake)

        t.emit(
            TelemetryEvent(
                name = "sync_failed",
                at = TimestampMs(3L),
                deviceId = "dev2",
                data = mapOf("error" to "x"),
            ),
        )

        assertEquals(1, fake.lines.size)
        assertEquals("WARN", fake.lines[0].first)
        assertTrue(fake.lines[0].second["event"] == "sync_failed")
    }
}
