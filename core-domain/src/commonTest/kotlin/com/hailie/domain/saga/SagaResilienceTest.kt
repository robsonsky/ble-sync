package com.hailie.domain.saga

import com.hailie.domain.AttemptCounters
import com.hailie.domain.AttemptKey
import com.hailie.domain.BondStatus
import com.hailie.domain.ConnectionStatus
import com.hailie.domain.DeviceId
import com.hailie.domain.DisconnectReason
import com.hailie.domain.DomainError
import com.hailie.domain.EventCount
import com.hailie.domain.EventOffset
import com.hailie.domain.PageSize
import com.hailie.domain.TimestampMs
import com.hailie.domain.commands.ConnectGatt
import com.hailie.domain.commands.ReadEventCount
import com.hailie.domain.commands.ReadEvents
import com.hailie.domain.commands.ScheduleRetry
import com.hailie.domain.events.DeviceConnected
import com.hailie.domain.events.Disconnected
import com.hailie.domain.events.EventCountLoaded
import com.hailie.domain.policy.AdaptivePageSizingPolicy
import com.hailie.domain.policy.BreakerPolicy
import com.hailie.domain.policy.RetryDecision
import com.hailie.domain.policy.RetryPolicy
import com.hailie.domain.policy.SimpleBreakerPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RetryScheduleFixed(private val delayMs: Long, private val cap: Int = Int.MAX_VALUE) : RetryPolicy {
    override fun decide(
        now: TimestampMs,
        attemptsForOp: Int,
        reason: com.hailie.domain.RetryReason,
    ): RetryDecision {
        return if (attemptsForOp >= cap) {
            RetryDecision.GiveUp
        } else {
            RetryDecision.Schedule(TimestampMs(now.value + delayMs))
        }
    }
}

private class BreakerAlwaysAllowed : BreakerPolicy {
    override fun isCallAllowed(
        now: TimestampMs,
        state: com.hailie.domain.BreakerState,
    ): Boolean = true

    override fun onSuccess(
        now: TimestampMs,
        state: com.hailie.domain.BreakerState,
    ): com.hailie.domain.BreakerState = state

    override fun onFailure(
        now: TimestampMs,
        state: com.hailie.domain.BreakerState,
        error: DomainError,
    ): com.hailie.domain.BreakerState = state
}

private class BreakerAllowedAfter(private val allowAtMs: Long) : BreakerPolicy {
    override fun isCallAllowed(
        now: TimestampMs,
        state: com.hailie.domain.BreakerState,
    ): Boolean {
        return now.value >= allowAtMs
    }

    override fun onSuccess(
        now: TimestampMs,
        state: com.hailie.domain.BreakerState,
    ): com.hailie.domain.BreakerState = state

    override fun onFailure(
        now: TimestampMs,
        state: com.hailie.domain.BreakerState,
        error: DomainError,
    ): com.hailie.domain.BreakerState = state
}

private fun sagaWith(
    retry: RetryPolicy,
    connectBreaker: BreakerPolicy,
): Saga =
    DefaultSaga(
        retryPolicy = retry,
        breakerForConnect = connectBreaker,
        breakerForRead = SimpleBreakerPolicy(),
        breakerForDeliver = SimpleBreakerPolicy(),
        breakerForAck = SimpleBreakerPolicy(),
        pageSizingPolicy = AdaptivePageSizingPolicy(minPage = 20, maxPage = 100, growStep = 20, shrinkStep = 20),
    )

/**
 * 1) Disconnect during paging → reconnect → resume at lastAckedExclusive
 * 2) Breaker denies at t0 (schedules retry) and allows at t1 (connects)
 * 3) Capped retries → stop scheduling when attempts reach cap
 */
class SagaResilienceTest {
    @Test
    fun disconnect_during_paging_reconnect_and_resume_from_high_water() {
        val device = DeviceId("dev-paging")
        val t0 = TimestampMs(10_000)

        // We were in the middle of paging previously (ack at 50, total 120, pageSize 50), but got disconnected.
        val s0 =
            com.hailie.domain.model.SyncAggregate(
                deviceId = device,
                bondStatus = BondStatus.Bonded,
                connectionStatus = ConnectionStatus.Disconnected,
                lastAckedExclusive = EventOffset(50),
                totalOnDevice = EventCount(120),
                pageSize = PageSize(50),
            )

        // On Disconnected: breaker allows → ConnectGatt
        val commands0 =
            sagaWith(
                retry = RetryScheduleFixed(delayMs = 1_000),
                connectBreaker = BreakerAlwaysAllowed(),
            ).decide(s0, lastEvent = Disconnected(device, t0, reason = DisconnectReason.Timeout, gattCode = null), now = t0)
        assertTrue(commands0.first() is ConnectGatt)

        // After connected: ReadEventCount
        val s1 = s0.copy(connectionStatus = ConnectionStatus.Connected)
        val commands1 =
            sagaWith(
                retry = RetryScheduleFixed(delayMs = 1_000),
                connectBreaker = BreakerAlwaysAllowed(),
            ).decide(s1, lastEvent = DeviceConnected(device, t0), now = t0)
        assertTrue(commands1.first() is ReadEventCount)

        // Count loaded (still behind): next ReadEvents must resume at offset=50 with current pageSize (50 here)
        val s2 = s1.copy(totalOnDevice = EventCount(120))
        val commands2 =
            sagaWith(
                retry = RetryScheduleFixed(delayMs = 1_000),
                connectBreaker = BreakerAlwaysAllowed(),
            ).decide(s2, lastEvent = EventCountLoaded(device, t0, total = EventCount(120)), now = t0)

        val read = commands2.first() as ReadEvents
        assertEquals(50, read.offset.value)
        assertEquals(50, read.count)
    }

    @Test
    fun breaker_half_open_probe_denied_then_allowed() {
        val device = DeviceId("dev-halfopen")
        val t0 = TimestampMs(5_000)
        val t1 = TimestampMs(6_000)

        val s =
            com.hailie.domain.model.SyncAggregate(
                deviceId = device,
                bondStatus = BondStatus.Bonded,
                connectionStatus = ConnectionStatus.Disconnected,
                lastAckedExclusive = EventOffset(0),
                totalOnDevice = EventCount(0),
                pageSize = PageSize(50),
            )

        // At t0 breaker denies → should schedule retry (since we injected a scheduling retry policy)
        val commands0 =
            sagaWith(
                retry = RetryScheduleFixed(delayMs = 500),
                // denies at 5_000
                connectBreaker = BreakerAllowedAfter(allowAtMs = 5_500),
            ).decide(s, lastEvent = Disconnected(device, t0, reason = DisconnectReason.Timeout, gattCode = null), now = t0)
        assertTrue(commands0.first() is ScheduleRetry)

        // At t1 breaker allows → should ConnectGatt
        val commands1 =
            sagaWith(
                retry = RetryScheduleFixed(delayMs = 500),
                // allows at 6_000
                connectBreaker = BreakerAllowedAfter(allowAtMs = 5_500),
            ).decide(s, lastEvent = Disconnected(device, t1, reason = DisconnectReason.Timeout, gattCode = null), now = t1)
        assertTrue(commands1.first() is ConnectGatt)
    }

    @Test
    fun capped_retries_stop_scheduling_when_attempts_reach_cap() {
        val device = DeviceId("dev-cap")
        val now = TimestampMs(7_000)

        // Already attempted once for ConnectGatt
        val attempts =
            AttemptCounters(
                byKey = mapOf(AttemptKey("ConnectGatt") to 1),
            )

        val s =
            com.hailie.domain.model.SyncAggregate(
                deviceId = device,
                bondStatus = BondStatus.Bonded,
                connectionStatus = ConnectionStatus.Disconnected,
                lastAckedExclusive = EventOffset(0),
                totalOnDevice = EventCount(0),
                pageSize = PageSize(50),
                attempts = attempts,
            )

        // Retry policy cap = 1 → with attemptsForOp == 1, policy returns GiveUp → no commands should be emitted
        val commands =
            sagaWith(
                retry = RetryScheduleFixed(delayMs = 500, cap = 1),
                // keep denying path to invoke retry logic
                connectBreaker = BreakerAllowedAfter(allowAtMs = 100_000),
            ).decide(s, lastEvent = Disconnected(device, now, reason = DisconnectReason.Timeout, gattCode = null), now = now)

        assertTrue(commands.isEmpty())
    }
}
