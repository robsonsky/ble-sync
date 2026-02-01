package com.hailie.domain.policy

import com.hailie.domain.BreakerPhase
import com.hailie.domain.BreakerState
import com.hailie.domain.DomainError
import com.hailie.domain.TimestampMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoliciesSmokeTest {
    @Test
    fun retryPolicy_schedules_within_bounds_and_caps() {
        val policy =
            ExponentialRetryPolicy(
                maxAttempts = 3,
                minBackoffMs = 100,
                maxBackoffMs = 1000,
                // deterministic
                jitterRatio = 0.0,
                // use min factor (no jitter)
                random = { a, _ -> a },
            )
        val now = TimestampMs(1_000)

        // attempts=0 → next attempt index = 1 → min backoff (100ms)
        val d1 = policy.decide(now, attemptsForOp = 0, reason = com.hailie.domain.RetryReason.TemporaryGattError)
        assertEquals(TimestampMs(1_100), (d1 as RetryDecision.Schedule).at)

        // attempts=1 → base 200ms
        val d2 = policy.decide(now, attemptsForOp = 1, reason = com.hailie.domain.RetryReason.TemporaryGattError)
        assertEquals(TimestampMs(1_200), (d2 as RetryDecision.Schedule).at)

        // attempts=3 → at cap → GiveUp
        val d3 = policy.decide(now, attemptsForOp = 3, reason = com.hailie.domain.RetryReason.TemporaryGattError)
        assertTrue(d3 is RetryDecision.GiveUp)
    }

    @Test
    fun breakerPolicy_flows_through_phases() {
        val policy = SimpleBreakerPolicy(failuresToOpen = 1, coolDownMs = 500)
        val t0 = TimestampMs(1_000)
        val t1 = TimestampMs(1_200)
        val t2 = TimestampMs(1_600)

        var s = BreakerState()

        // Initially closed, allowed
        assertTrue(policy.isCallAllowed(t0, s))

        // Failure → Open
        s = policy.onFailure(t0, s, DomainError.Transport("x"))
        assertEquals(BreakerPhase.Open, s.phase)

        // Not allowed during cool-down
        assertTrue(!policy.isCallAllowed(t1, s))

        // After cool-down → allowed (probe)
        assertTrue(policy.isCallAllowed(t2, s))

        // Probe fails → Open again
        s = policy.onFailure(t2, s, DomainError.Transport("y"))
        assertEquals(BreakerPhase.Open, s.phase)
    }

    @Test
    fun pageSizing_grow_and_shrink_within_bounds() {
        val policy = AdaptivePageSizingPolicy(minPage = 20, maxPage = 100, growStep = 20, shrinkStep = 20)

        // Grow from 20 → 40
        val s1 = policy.next(com.hailie.domain.PageSize(20), PageOutcome.Stable)
        assertEquals(40, s1.value)

        // Hard failure shrinks aggressively: 40 → 0 (clamped to 20)
        val s2 = policy.next(s1, PageOutcome.HardFailure)
        assertEquals(20, s2.value)

        // Grow half step on MostlyStable: 20 → 30
        val s3 = policy.next(s2, PageOutcome.MostlyStable)
        assertEquals(30, s3.value)
    }
}
