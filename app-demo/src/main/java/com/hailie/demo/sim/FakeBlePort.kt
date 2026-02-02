package com.hailie.demo.sim

import com.hailie.demo.runtime.DeviceId
import com.hailie.demo.runtime.TelemetryEvent
import com.hailie.demo.runtime.TelemetrySink
import com.hailie.demo.scenario.FaultScript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.random.Random

class FakeBlePort(
    private val scope: CoroutineScope,
    private val sink: TelemetrySink,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val rnd: Random = Random(42),
) {
    suspend fun bond(
        deviceId: DeviceId,
        s: FaultScript,
    ): Boolean {
        repeat(s.bondRetryMax + 1) { attempt ->
            print(scope)
            delay(s.bondDelay.inWholeMilliseconds)
            val fail = rnd.nextDouble() < s.bondFailRate
            if (!fail) {
                sink.emit(
                    TelemetryEvent(
                        "bonded",
                        clock(),
                        deviceId.raw,
                        mapOf("attempt" to attempt.toString()),
                    ),
                )
                return true
            }
            sink.emit(
                TelemetryEvent(
                    "bond_failed",
                    clock(),
                    deviceId.raw,
                    mapOf("attempt" to attempt.toString()),
                ),
            )
        }
        return false
    }

    suspend fun connect(
        deviceId: DeviceId,
        s: FaultScript,
    ): Boolean {
        repeat(s.connectRetryMax + 1) { attempt ->
            delay(s.connectDelay.inWholeMilliseconds)
            val drop = rnd.nextDouble() < s.connectDropRate
            if (!drop) {
                sink.emit(
                    TelemetryEvent(
                        "gatt_connected",
                        clock(),
                        deviceId.raw,
                        mapOf("attempt" to attempt.toString()),
                    ),
                )
                return true
            }
            sink.emit(
                TelemetryEvent(
                    "connect_failed",
                    clock(),
                    deviceId.raw,
                    mapOf("attempt" to attempt.toString()),
                ),
            )
        }
        return false
    }

    suspend fun readPage(
        deviceId: DeviceId,
        s: FaultScript,
        offset: Long,
    ): PageResult {
        val jitter = if (s.jitterMs > 0) rnd.nextInt(0, s.jitterMs) else 0
        delay(s.pageLatency.inWholeMilliseconds + jitter)
        val fail = rnd.nextDouble() < s.readFailRate
        val pageSize = s.pageSizeMean.coerceAtLeast(1)
        val bytes = pageSize * s.bytesPerPage
        if (fail) {
            sink.emit(
                TelemetryEvent(
                    "page_failed",
                    clock(),
                    deviceId.raw,
                    mapOf(
                        "offset" to offset.toString(),
                    ),
                ),
            )
            return PageResult(false, offset, 0, 0)
        }
        sink.emit(
            TelemetryEvent(
                "page_read",
                clock(),
                deviceId.raw,
                mapOf(
                    "offset" to offset.toString(),
                    "page_size" to pageSize.toString(),
                    "bytes" to bytes.toString(),
                ),
            ),
        )
        return PageResult(true, offset, pageSize, bytes)
    }

    suspend fun ack(
        deviceId: DeviceId,
        s: FaultScript,
        upTo: Long,
    ): Boolean {
        val fail = (rnd.nextDouble() < s.ackFailRate)
        delay(10)
        if (!fail) {
            sink.emit(
                TelemetryEvent(
                    "ack_sent",
                    clock(),
                    deviceId.raw,
                    mapOf("offset" to upTo.toString()),
                ),
            )
            return true
        }
        sink.emit(
            TelemetryEvent(
                "ack_failed",
                clock(),
                deviceId.raw,
                mapOf("offset" to upTo.toString()),
            ),
        )
        return false
    }

    data class PageResult(val ok: Boolean, val offset: Long, val pageSize: Int, val bytes: Int)
}
