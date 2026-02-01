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
import kotlinx.coroutines.runBlocking

/**
 * Single-threaded runtime that:
 * - Processes one message at a time (mailbox consumer coroutine)
 * - Applies events to state and decides next commands via Saga
 * - Executes commands via ports (Ble, Delivery) INLINE in the consumer (strict serialization)
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

    // Mailbox and single consumer ensure serialization without experimental APIs
    private val mailbox = Channel<Message>(capacity = Channel.UNLIMITED)
    private val dispatcher = Dispatchers.Default
    private val scope = CoroutineScope(Job() + dispatcher)
    private var runningJob: Job? = null

    private var retryToken: TimerToken? = null
    private var readInFlight: Boolean = false

    fun start() {
        // Bootstrap synchronously: restore snapshot and run initial decision
        runBlocking(dispatcher) {
            handleStart()
        }
        // Start mailbox consumer (single coroutine = strict serialization)
        runningJob =
            scope.launch {
                for (msg in mailbox) {
                    when (msg) {
                        is Message.Start -> { /* bootstrap already handled */ }
                        is Message.DomainEvent -> handleDomainEvent(msg.event)
                        is Message.TimerFired -> handleTimerFired()
                        is Message.Stop -> {
                            cancelRetry()
                            break
                        }
                    }
                }
            }
    }

    fun stop() {
        offer(Message.Stop)
        runningJob?.invokeOnCompletion { mailbox.close() }
    }

    fun offer(msg: Message) {
        // Non-blocking send; handled by the single consumer
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
        when (ev) {
            is com.hailie.domain.events.EventsRead -> {
                readInFlight = true
            }
            is com.hailie.domain.events.EventsAcked -> {
                if (!state.hasInFlight) {
                    readInFlight = false
                }
                // Save snapshot after a successful ack (deterministic)
                snapshot("acked")
            }
            is com.hailie.domain.events.Disconnected -> {
                // Opportunistic snapshot on disconnect
                snapshot("disconnected")
            }
            else -> {
                // no-op
            }
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
                    retryToken = clock.schedule(cmd.after) { offer(Message.TimerFired) }
                    telemetry.emit(
                        TelemetryEvent(
                            name = "retry_scheduled",
                            at = now,
                            deviceId = deviceId.raw,
                            data =
                                mapOf(
                                    "after" to cmd.after.value.toString(),
                                    "reason" to cmd.reason::class.simpleName.orEmpty(),
                                ),
                        ),
                    )
                }
                is BondDevice -> {
                    // INLINE execution to keep strict serialization
                    val ev = ble.bond(cmd.deviceId)
                    offer(Message.DomainEvent(ev))
                }
                is ConnectGatt -> {
                    val ev = ble.connect(cmd.deviceId)
                    offer(Message.DomainEvent(ev))
                }
                is ReadEventCount -> {
                    val ev = ble.readCount(cmd.deviceId)
                    offer(Message.DomainEvent(ev))
                }
                is ReadEvents -> {
                    if (readInFlight) {
                        telemetry.emit(TelemetryEvent("read_skipped_backpressure", now, deviceId.raw))
                    } else {
                        readInFlight = true
                        val ev = ble.readPage(cmd.deviceId, cmd.offset, cmd.count)
                        offer(Message.DomainEvent(ev))
                    }
                }
                is DeliverToApp -> {
                    val ev = delivery.deliver(cmd.deviceId, cmd.range)
                    offer(Message.DomainEvent(ev))
                }
                is Acknowledge -> {
                    val ev = ble.ack(cmd.deviceId, cmd.upTo)
                    offer(Message.DomainEvent(ev))
                }
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
