package com.hailie.adapters.android.ble

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import com.hailie.domain.DeviceId
import com.hailie.domain.DomainError
import com.hailie.domain.EventCount
import com.hailie.domain.EventOffset
import com.hailie.domain.EventRange
import com.hailie.domain.events.Event
import com.hailie.domain.events.EventCountLoaded
import com.hailie.domain.events.EventsAcked
import com.hailie.domain.events.EventsRead
import com.hailie.runtime.ports.BlePort
import com.hailie.runtime.ports.ClockPort
import com.hailie.runtime.ports.TelemetryEvent
import com.hailie.runtime.ports.TelemetryPort
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class AndroidBleAdapter(
    private val context: Context,
    private val clock: ClockPort,
    private val telemetry: TelemetryPort,
    private val resolver: DeviceResolver,
    private val config: BleConfig,
) : BlePort {
    private val gate = Mutex()
    private val sessions = ConcurrentHashMap<String, ConnectionSession>()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect(deviceId: DeviceId): Event =
        runBlockingSerial("connect", deviceId) {
            return@runBlockingSerial runCatching {
                check(BluetoothPermission.hasConnectPermission(context)) { "Missing BLUETOOTH_CONNECT" }

                val dev = resolver.resolve(deviceId)
                val start = clock.now()
                val session = sessions.computeIfAbsent(deviceId.raw) { ConnectionSession(context, dev) }
                withTimeout(config.connectTimeoutMs) { session.connect() }
                telemetry.emit(TelemetryEvent("gatt_connected", start, deviceId.raw))
                com.hailie.domain.events.DeviceConnected(deviceId, start)
            }.getOrElse {
                syncFailed(deviceId, "Missing BLUETOOTH_CONNECT", security = true)
            }
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun disconnect(deviceId: DeviceId): Event =
        runBlockingSerial("disconnect", deviceId) {
            if (!BluetoothPermission.hasConnectPermission(context)) {
                telemetry.emit(
                    TelemetryEvent(
                        "sync_failed",
                        clock.now(),
                        deviceId.raw,
                        mapOf("error_category" to "security", "error_message" to "Missing BLUETOOTH_CONNECT"),
                    ),
                )
                return@runBlockingSerial com.hailie.domain.events.SyncFailed(
                    deviceId,
                    clock.now(),
                    DomainError.Unexpected("Missing BLUETOOTH_CONNECT"),
                )
            }
            sessions.remove(deviceId.raw)?.close()
            telemetry
                .emit(TelemetryEvent("gatt_disconnected", clock.now(), deviceId.raw))
            com.hailie.domain.events.Disconnected(
                deviceId = deviceId,
                at = clock.now(),
                reason = com.hailie.domain.DisconnectReason.Custom("Manual"),
                gattCode = null,
            )
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun bond(deviceId: DeviceId): Event =
        runBlockingSerial("bond", deviceId) {
            if (!BluetoothPermission.hasConnectPermission(context)) {
                return@runBlockingSerial syncFailed(
                    deviceId,
                    "Missing BLUETOOTH_CONNECT",
                    security = true,
                )
            }
            val dev = resolver.resolve(deviceId)
            val start = clock.now()
            val ok = createBondBlocking(dev)
            if (ok) {
                telemetry
                    .emit(TelemetryEvent("bonded", clock.now(), deviceId.raw))
                com.hailie.domain.events.DeviceBonded(deviceId, start)
            } else {
                telemetry.emit(
                    TelemetryEvent(
                        "sync_failed",
                        clock.now(),
                        deviceId.raw,
                        mapOf(
                            "error_category" to "security",
                            "error_message" to "bond_failed",
                        ),
                    ),
                )
                com.hailie.domain.events
                    .SyncFailed(deviceId, clock.now(), DomainError.Unexpected("bond_failed"))
            }
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun readCount(deviceId: DeviceId): Event =
        runBlockingSerial("readCount", deviceId) {
            if (!BluetoothPermission.hasConnectPermission(context)) {
                return@runBlockingSerial syncFailed(
                    deviceId,
                    "Missing BLUETOOTH_CONNECT",
                    security = true,
                )
            }
            val dev = resolver.resolve(deviceId)
            val session =
                sessions
                    .computeIfAbsent(deviceId.raw) { ConnectionSession(context, dev) }
            withTimeout(config.connectTimeoutMs) { session.connect() }

            val start = clock.now()
            val payload = withTimeout(config.readCountTimeoutMs) { session.read(config.countCharacteristicUuid) }

            if (payload.size < 4) return@runBlockingSerial syncFailed(deviceId, "count_payload_short")
            val total =
                ByteBuffer
                    .wrap(payload, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int

            telemetry
                .emit(TelemetryEvent("count_read", clock.now(), deviceId.raw, mapOf("total_count" to total.toString())))
            EventCountLoaded(deviceId, start, total = EventCount(total.toLong()))
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun readPage(
        deviceId: DeviceId,
        offset: EventOffset,
        count: Int,
    ): Event =
        runBlockingSerial("readPage", deviceId) {
            if (!BluetoothPermission.hasConnectPermission(context)) {
                return@runBlockingSerial syncFailed(
                    deviceId,
                    "Missing BLUETOOTH_CONNECT",
                    security = true,
                )
            }
            val dev = resolver.resolve(deviceId)
            val session = sessions.computeIfAbsent(deviceId.raw) { ConnectionSession(context, dev) }
            withTimeout(config.connectTimeoutMs) { session.connect() }

            val req =
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(offset.value.toInt()).putInt(count).array()

            val start = clock.now()
            withTimeout(config.readPageTimeoutMs) { session.write(config.pageCharacteristicUuid, req) }
            val payload = withTimeout(config.readPageTimeoutMs) { session.read(config.pageCharacteristicUuid) }

            // If your device returns a precise end offset in 'payload', parse here.
            val range =
                EventRange(
                    offset,
                    EventOffset(offset.value + count.toLong()),
                )

            telemetry.emit(
                TelemetryEvent(
                    "page_read",
                    clock.now(),
                    deviceId.raw,
                    mapOf(
                        "offset" to offset.value.toString(),
                        "page_size" to count.toString(),
                        "payload_len" to payload.size.toString(),
                    ),
                ),
            )
            EventsRead(deviceId, start, range)
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun ack(
        deviceId: DeviceId,
        upTo: EventOffset,
    ): Event =
        runBlockingSerial("ack", deviceId) {
            if (!BluetoothPermission.hasConnectPermission(context)) {
                return@runBlockingSerial syncFailed(
                    deviceId,
                    "Missing BLUETOOTH_CONNECT",
                    security = true,
                )
            }
            val dev = resolver.resolve(deviceId)
            val session =
                sessions.computeIfAbsent(
                    deviceId.raw,
                ) { ConnectionSession(context, dev) }
            withTimeout(config.connectTimeoutMs) { session.connect() }

            val req =
                ByteBuffer
                    .allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(upTo.value.toInt()).array()

            val start = clock.now()
            withTimeout(config.ackTimeoutMs) { session.write(config.ackCharacteristicUuid, req) }

            telemetry
                .emit(TelemetryEvent("ack_sent", clock.now(), deviceId.raw, mapOf("offset" to upTo.value.toString())))
            EventsAcked(deviceId, start, upTo)
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun createBondBlocking(device: android.bluetooth.BluetoothDevice): Boolean {
        if (!BluetoothPermission.hasConnectPermission(context)) return false
        return runBlocking {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                val r =
                    BondStateReceiver(
                        device.address,
                    ) { ok, ex -> cont.resume(ex == null && ok) }
                r.register(context)
                try {
                    val started = device.createBond()
                    if (!started) {
                        r.unregister(context)
                        cont.resume(false)
                    }
                } catch (_: Throwable) {
                    r.unregister(context)
                    cont.resume(false)
                }
                cont.invokeOnCancellation { r.unregister(context) }
            }
        }
    }

    private fun syncFailed(
        deviceId: DeviceId,
        reason: String,
        security: Boolean = false,
    ): Event {
        telemetry.emit(
            TelemetryEvent(
                "sync_failed",
                clock.now(),
                deviceId.raw,
                mapOf(
                    "error_category" to if (security) "security" else "transport",
                    "error_message" to reason,
                ),
            ),
        )
        val err = if (security) DomainError.Unexpected(reason) else DomainError.Transport(reason)
        return com.hailie.domain.events.SyncFailed(deviceId, clock.now(), err)
    }

    private fun <T> runBlockingSerial(
        op: String,
        deviceId: DeviceId,
        block: suspend () -> T,
    ): T =
        runBlocking {
            print(op)
            print(deviceId)
            gate.withLock { block() }
        }
}
