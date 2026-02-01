package com.hailie.domain.policy

import com.hailie.domain.AttemptCounters
import com.hailie.domain.AttemptKey
import com.hailie.domain.RetryReason
import com.hailie.domain.TimestampMs

/**
 * Decision returned by [RetryPolicy].
 */
sealed interface RetryDecision {
    /**
     * Schedule a retry at [at]. The caller (adapter/scheduler) is responsible for firing a timer/event.
     */
    data class Schedule(val at: TimestampMs) : RetryDecision

    /**
     * Do not retry (caps reached, or reason says to stop).
     */
    data object GiveUp : RetryDecision
}

/**
 * Contract for computing retry timing and caps.
 *
 * Inputs:
 * - now: current time
 * - attemptsForOp: how many attempts we've already made for this "operation" (e.g., "ConnectGatt")
 * - reason: why we want to retry (transient GATT, busy radio, etc.)
 *
 * Output:
 * - Either a Schedule(at) or GiveUp
 */
fun interface RetryPolicy {
    fun decide(
        now: TimestampMs,
        attemptsForOp: Int,
        reason: RetryReason,
    ): RetryDecision
}

/**
 * Pragmatic default: exponential backoff with jitter, bounded by [minBackoffMs]..[maxBackoffMs],
 * and capped attempts at [maxAttempts].
 *
 * backoff(attempt) = minBackoffMs * 2^(attempt-1) clamped to [minBackoffMs, maxBackoffMs] then jittered by ±[jitterRatio].
 *
 * Notes:
 * - attempt is 1-based: attempt=1 → base=minBackoffMs; attempt=2 → 2x; etc.
 * - jitter: randomization applied multiplicatively: base ± base*jitterRatio (clamped to bounds).
 * - attemptsForOp: number of attempts already performed. If attemptsForOp >= maxAttempts → GiveUp.
 */
class ExponentialRetryPolicy(
    private val maxAttempts: Int = 6,
    private val minBackoffMs: Long = 500L,
    private val maxBackoffMs: Long = 30_000L,
    // 25% jitter
    private val jitterRatio: Double = 0.25,
    private val random: (minInclusive: Double, maxInclusive: Double)
    -> Double = { a, b -> a + kotlin.random.Random.nextDouble() * (b - a) },
) : RetryPolicy {
    override fun decide(
        now: TimestampMs,
        attemptsForOp: Int,
        reason: RetryReason,
    ): RetryDecision {
        // If we've already attempted maxAttempts times, GiveUp
        if (attemptsForOp >= maxAttempts) {
            return RetryDecision.GiveUp
        }

        // Next attempt index (1-based)
        val nextAttemptIndex = attemptsForOp + 1

        // Exponential growth
        val raw = minBackoffMs * (1L shl (nextAttemptIndex - 1)).coerceAtLeast(1)

        // Clamp to bounds
        val clamped = raw.coerceIn(minBackoffMs, maxBackoffMs)

        // Apply jitter multiplicatively: base * (1 ± jitter)
        val jitterMin = (1.0 - jitterRatio).coerceAtLeast(0.0)
        val jitterMax = (1.0 + jitterRatio)
        val factor = random(jitterMin, jitterMax)
        val jittered = (clamped.toDouble() * factor).toLong().coerceIn(minBackoffMs, maxBackoffMs)

        return RetryDecision.Schedule(at = TimestampMs(now.value + jittered))
    }
}

/**
 * Helper to read attempts: you will typically keep one AttemptKey per operation, e.g.:
 *
 * val CONNECT = AttemptKey("ConnectGatt")
 * val attempts = agg.attempts.get(CONNECT)
 */
fun attemptsFor(
    counters: AttemptCounters,
    op: AttemptKey,
): Int = counters.get(op)
