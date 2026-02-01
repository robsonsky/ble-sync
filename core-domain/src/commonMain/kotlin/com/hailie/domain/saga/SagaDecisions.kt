package com.hailie.domain.saga

import com.hailie.domain.AttemptKey
import com.hailie.domain.DeviceId
import com.hailie.domain.EventOffset
import com.hailie.domain.PageSize
import com.hailie.domain.TimestampMs
import com.hailie.domain.commands.Acknowledge
import com.hailie.domain.commands.BondDevice
import com.hailie.domain.commands.Command
import com.hailie.domain.commands.ConnectGatt
import com.hailie.domain.commands.DeliverToApp
import com.hailie.domain.commands.ReadEventCount
import com.hailie.domain.commands.ReadEvents
import com.hailie.domain.commands.ScheduleRetry
import com.hailie.domain.events.DeviceBonded
import com.hailie.domain.events.DeviceConnected
import com.hailie.domain.events.Disconnected
import com.hailie.domain.events.Event
import com.hailie.domain.events.EventCountLoaded
import com.hailie.domain.events.EventsAcked
import com.hailie.domain.events.EventsDelivered
import com.hailie.domain.events.EventsRead
import com.hailie.domain.model.SyncAggregate
import com.hailie.domain.policy.BreakerPolicy
import com.hailie.domain.policy.PageOutcome
import com.hailie.domain.policy.PageSizingPolicy
import com.hailie.domain.policy.RetryDecision
import com.hailie.domain.policy.RetryPolicy
import com.hailie.domain.policy.attemptsFor

/**
 * Stable keys for attempt tracking by operation.
 * These names should match how you increment counters around adapter results.
 */
object AttemptKeys {
    val BOND = AttemptKey("BondDevice")
    val CONNECT = AttemptKey("ConnectGatt")
    val READ_COUNT = AttemptKey("ReadEventCount")
    val READ_PAGE = AttemptKey("ReadEvents")
    val DELIVER = AttemptKey("DeliverToApp")
    val ACK = AttemptKey("Acknowledge")
}

/**
 * A saga decides the next commands based on current [state], the [lastEvent] observed,
 * the current time [now], and the injected policies.
 *
 * Pure function: no side effects.
 */
fun interface Saga {
    fun decide(
        state: SyncAggregate,
        lastEvent: Event?,
        now: TimestampMs,
    ): List<Command>
}

/**
 * Default saga: drives bond → connect → count → page (read→deliver→ack) loops.
 * Honors RetryPolicy, BreakerPolicy, and PageSizingPolicy.
 */
class DefaultSaga(
    private val retryPolicy: RetryPolicy,
    private val breakerForConnect: BreakerPolicy,
    private val breakerForRead: BreakerPolicy,
    private val breakerForDeliver: BreakerPolicy,
    private val breakerForAck: BreakerPolicy,
    private val pageSizingPolicy: PageSizingPolicy,
) : Saga {
    override fun decide(
        state: SyncAggregate,
        lastEvent: Event?,
        now: TimestampMs,
    ): List<Command> {
        // 0) If we’re not bonded → bond first
        if (state.bondStatus != com.hailie.domain.BondStatus.Bonded) {
            return listOf(BondDevice(deviceId = state.deviceId))
        }

        // 1) Ensure connection is up (respect connect breaker)
        if (state.connectionStatus != com.hailie.domain.ConnectionStatus.Connected) {
            val allowed = breakerForConnect.isCallAllowed(now, state.connectBreaker)
            return if (allowed) {
                listOf(ConnectGatt(deviceId = state.deviceId))
            } else {
                retryOrGiveUp(
                    deviceId = state.deviceId,
                    now = now,
                    attempts = attemptsFor(state.attempts, AttemptKeys.CONNECT),
                    reason = com.hailie.domain.RetryReason.BackoffAfterFailure,
                )
            }
        }

        // 2) If we don’t know the total yet or need to re-check growth → read event count
        // we never set negatives; kept for completeness
        val needsCount = state.totalOnDevice.value < 0

        if (state.totalOnDevice == com.hailie.domain.EventCount(0L) && state.lastAckedExclusive.value == 0L) {
            // Initial connected state: ask for count
            return listOf(ReadEventCount(deviceId = state.deviceId))
        }

        // 3) Handle by last event (decision table core)
        return when (lastEvent) {
            null -> decideOnNoEvent(state)
            is DeviceBonded -> listOf(ConnectGatt(state.deviceId))
            is DeviceConnected -> listOf(ReadEventCount(state.deviceId))
            is EventCountLoaded -> decideAfterCount(state)
            is EventsRead -> decideAfterRead(state, lastEvent)
            is EventsDelivered -> decideAfterDelivered(state, lastEvent)
            is EventsAcked -> decideAfterAck(state, lastEvent)
            is Disconnected -> {
                // Next step is reconnect, but respect breaker & retry policy
                val allowed = breakerForConnect.isCallAllowed(now, state.connectBreaker)
                if (allowed) {
                    listOf(ConnectGatt(state.deviceId))
                } else {
                    retryOrGiveUp(
                        deviceId = state.deviceId,
                        now = now,
                        attempts = attemptsFor(state.attempts, AttemptKeys.CONNECT),
                        reason = com.hailie.domain.RetryReason.TemporaryGattError,
                    )
                }
            }
            else -> emptyList()
        }
    }

    private fun decideOnNoEvent(state: SyncAggregate): List<Command> {
        // Fresh connected state without events: read count
        return listOf(ReadEventCount(deviceId = state.deviceId))
    }

    private fun decideAfterCount(state: SyncAggregate): List<Command> {
        // If everything already acked → check growth once more or stop
        if (state.isFullyAcked) {
            // We can either stop or re-check count to detect growth.
            // Choose to re-check count to keep syncing if device keeps producing data.
            return listOf(ReadEventCount(deviceId = state.deviceId))
        }
        // Otherwise, start (or continue) paging from high-water
        val start = com.hailie.domain.EventOffset(state.lastAckedExclusive.value)
        val count = state.pageSize.value
        return listOf(ReadEvents(deviceId = state.deviceId, offset = start, count = count))
    }

    private fun decideAfterRead(
        state: SyncAggregate,
        ev: EventsRead,
    ): List<Command> {
        // After reading a page, deliver it
        return listOf(DeliverToApp(deviceId = state.deviceId, range = ev.range))
    }

    private fun decideAfterDelivered(
        state: SyncAggregate,
        ev: EventsDelivered,
    ): List<Command> {
        // After delivery, request ack to advance high-water
        return listOf(Acknowledge(deviceId = state.deviceId, upTo = ev.range.endExclusive))
    }

    private fun decideAfterAck(
        state: SyncAggregate,
        ev: EventsAcked,
    ): List<Command> {
        // If still behind the observed total → next page
        return if (state.lastAckedExclusive.value < state.totalOnDevice.value) {
            val nextOffset = com.hailie.domain.EventOffset(state.lastAckedExclusive.value)
            val tunedPageSize = tunePageSizeAfterAck(state)
            listOf(ReadEvents(deviceId = state.deviceId, offset = nextOffset, count = tunedPageSize.value))
        } else {
            // High-water caught up → read count again to detect growth
            listOf(ReadEventCount(deviceId = state.deviceId))
        }
    }

    private fun tunePageSizeAfterAck(state: SyncAggregate): com.hailie.domain.PageSize {
        val outcome = if (state.lastError == null) PageOutcome.Stable else PageOutcome.MostlyStable
        return pageSizingPolicy.next(state.pageSize, outcome)
    }

    private fun retryOrGiveUp(
        deviceId: DeviceId,
        now: TimestampMs,
        attempts: Int,
        reason: com.hailie.domain.RetryReason,
    ): List<Command> {
        return when (val d = retryPolicy.decide(now, attempts, reason)) {
            is RetryDecision.Schedule -> listOf(ScheduleRetry(deviceId, d.at, reason))
            is RetryDecision.GiveUp -> emptyList()
        }
    }
}
