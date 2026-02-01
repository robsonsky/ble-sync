package com.hailie.runtime.actor

import com.hailie.domain.DeviceId
import com.hailie.domain.commands.Acknowledge
import com.hailie.domain.commands.BondDevice
import com.hailie.domain.commands.ConnectGatt
import com.hailie.domain.commands.DeliverToApp
import com.hailie.domain.commands.ReadEventCount
import com.hailie.domain.commands.ReadEvents
import com.hailie.domain.commands.ScheduleRetry
import com.hailie.domain.events.Event
import com.hailie.domain.events.RetryScheduled
import com.hailie.domain.model.SyncAggregate
import com.hailie.domain.model.applyEvent
import com.hailie.domain.saga.Saga
import com.hailie.runtime.ports.BlePort
import com.hailie.runtime.ports.ClockPort
import com.hailie.runtime.ports.DeliveryPort
import com.hailie.runtime.ports.StateStorePort
import com.hailie.runtime.ports.SyncSnapshot
import com.hailie.runtime.ports.TelemetryEvent
import com.hailie.runtime.ports.TelemetryPort
import com.hailie.runtime.ports.TimerToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single-threaded runtime that:
 * - Processes one message at a time (mailbox)
 * - Applies events to state and decides next commands via Saga
 * - Executes commands via ports (Ble, Delivery)
 * - Enforces backpressure (only one ReadEvents in-flight)
 * - Schedules retries via ClockPort (cancellable)
 * - Emits telemetry and snapshots state on significant transitions
 */
class SyncActor(
    initialState: SyncAggregate,
    private val saga: Saga,
    private val clock: ClockPort,
    private val ble: BlePort,
    private val delivery: DeliveryPort,
    private val store: StateStorePort,
    private val telemetry: TelemetryPort,
) {
    private var state: SyncAggregate = initialState
    private val deviceId: DeviceId = initialState.deviceId

    private val mailbox = Channel<Msg>(capacity = Channel.UNLIMITED)
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(Job() + dispatcher)
    private var runningJob: Job? = null

    private var retryToken: TimerToken? = null
    private var readInFlight: Boolean = false

    fun start() {
        runningJob =
            scope.launch {
                for (msg in mailbox) {
                    when (msg) {
                        is Msg.Start -> handleStart()
                        is Msg.DomainEvent -> handleDomainEvent(msg.event)
                        is Msg.TimerFired -> handleTimerFired()
                        is Msg.Stop -> {
                            cancelRetry()
                            break
                        }
                    }
                }
            }
        offer(Msg.Start)
    }

    fun stop() {
        offer(Msg.Stop)
        runningJob?.invokeOnCompletion { mailbox.close() }
    }

    fun offer(msg: Msg) {
        scope.launch(dispatcher) { mailbox.send(msg) }
    }

    private suspend fun handleStart() {
        // Load snapshot (if any) to resume
        store.read(deviceId)?.let { snap ->
            state =
                state.copy(
                    lastAckedExclusive = snap.lastAckedExclusive,
                    pageSize = snap.pageSize,
                    sagaCursor = snap.sagaCursor,
                )
            telemetry.emit(TelemetryEvent("snapshot_restored", clock.now(), deviceId.raw))
        }
        decideAndExecute(lastEvent = null)
    }

    private suspend fun handleDomainEvent(ev: Event) {
        state = state.applyEvent(ev)
        // Adjust runtime flags when liftetime transitions happen
        when (ev) {
            is com.hailie.domain.events.EventsRead -> readInFlight = true
            is com.hailie.domain.events.EventsAcked -> if (!state.hasInFlight) readInFlight = false
            is com.hailie.domain.events.Disconnected -> {
                // Opportunistic snapshot on disconnect
                snapshot("disconnected")
            }
            is com.hailie.domain.events.EventsAcked -> snapshot("acked")
            else -> { /* no-op */ }
        }
        decideAndExecute(lastEvent = ev)
    }

    private suspend fun handleTimerFired() {
        val now = clock.now()
        retryToken = null
        val ev = RetryScheduled(deviceId = deviceId, at = now, after = now)
        state = state.applyEvent(ev)
        decideAndExecute(lastEvent = ev)
    }

    private suspend fun decideAndExecute(lastEvent: Event?) {
        val now = clock.now()
        val commands = saga.decide(state, lastEvent, now)

        for (cmd in commands) {
            when (cmd) {
                is ScheduleRetry -> {
                    cancelRetry()
                    retryToken = clock.schedule(cmd.after) { offer(Msg.TimerFired) }
                    telemetry.emit(
                        TelemetryEvent(
                            name = "retry_scheduled",
                            at = now,
                            deviceId = deviceId.raw,
                            data = mapOf("after" to cmd.after.value.toString(), "reason" to cmd.reason::class.simpleName.orEmpty()),
                        ),
                    )
                }
                is BondDevice -> executeBle { ble.bond(cmd.deviceId) }
                is ConnectGatt -> executeBle { ble.connect(cmd.deviceId) }
                is ReadEventCount -> executeBle { ble.readCount(cmd.deviceId) }
                is ReadEvents -> {
                    if (readInFlight) {
                        telemetry.emit(TelemetryEvent("read_skipped_backpressure", now, deviceId.raw))
                    } else {
                        readInFlight = true
                        executeBle { ble.readPage(cmd.deviceId, cmd.offset, cmd.count) }
                    }
                }
                is DeliverToApp -> executeDelivery { delivery.deliver(cmd.deviceId, cmd.range) }
                is Acknowledge -> executeBle { ble.ack(cmd.deviceId, cmd.upTo) }
                else -> {
                    telemetry.emit(
                        TelemetryEvent(
                            "unknown_command_ignored",
                            now,
                            deviceId.raw,
                            data = mapOf("type" to cmd::class.simpleName.orEmpty()),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun executeBle(block: () -> Event) {
        val ev = withContext(dispatcher) { block() }
        offer(Msg.DomainEvent(ev))
    }

    private suspend fun executeDelivery(block: () -> Event) {
        val ev = withContext(dispatcher) { block() }
        offer(Msg.DomainEvent(ev))
    }

    private fun cancelRetry() {
        retryToken?.let { clock.cancel(it) }
        retryToken = null
    }

    private fun snapshot(reason: String) {
        val snap =
            SyncSnapshot(
                deviceId = deviceId,
                lastAckedExclusive = state.lastAckedExclusive,
                pageSize = state.pageSize,
                sagaCursor = state.sagaCursor,
            )
        store.write(snap)
        telemetry.emit(
            TelemetryEvent(
                name = "snapshot_saved",
                at = clock.now(),
                deviceId = deviceId.raw,
                data =
                    mapOf(
                        "reason" to reason,
                        "acked" to state.lastAckedExclusive.value.toString(),
                        "pageSize" to state.pageSize.value.toString(),
                        "cursor" to state.sagaCursor.label,
                    ),
            ),
        )
    }
}
