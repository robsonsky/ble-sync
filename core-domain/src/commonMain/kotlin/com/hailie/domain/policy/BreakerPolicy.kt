package com.hailie.domain.policy

import com.hailie.domain.BreakerPhase
import com.hailie.domain.BreakerState
import com.hailie.domain.DomainError
import com.hailie.domain.TimestampMs

/**
 * Contract for managing a simple circuit breaker state.
 *
 * Typical flow:
 * - onFailure(...) transitions from Closed → Open when threshold reached; records lastFailure and openedAt.
 * - isCallAllowed(now, state) returns false during Open cool-down.
 * - after cool-down, isCallAllowed allows a single probe in HalfOpen; onSuccess closes, onFailure reopens.
 */
interface BreakerPolicy {
    /** Should we attempt a call now based on [state] and [now]? */
    fun isCallAllowed(
        now: TimestampMs,
        state: BreakerState,
    ): Boolean

    /** Apply a SUCCESS result to [state] at [now]. */
    fun onSuccess(
        now: TimestampMs,
        state: BreakerState,
    ): BreakerState

    /** Apply a FAILURE with [error] to [state] at [now]. */
    fun onFailure(
        now: TimestampMs,
        state: BreakerState,
        error: DomainError,
    ): BreakerState
}

/**
 * Simple breaker:
 * - Closed: all calls allowed; first failure moves to Open immediately (or after [failuresToOpen] failures).
 * - Open: block calls until coolDownMs elapses; then allow one probe (HalfOpen).
 * - HalfOpen: onSuccess → Closed, onFailure → Open (reset openedAt).
 */
class SimpleBreakerPolicy(
    private val failuresToOpen: Int = 1,
    private val coolDownMs: Long = 5_000L,
) : BreakerPolicy {
    // We keep an internal counter per BreakerState using lastFailure as a marker. For simplicity and purity,
    // we derive behavior from phase and timestamps only. If you need "N consecutive failures", inject a counter
    // into the aggregate; for now we open on first failure if failuresToOpen == 1.

    override fun isCallAllowed(
        now: TimestampMs,
        state: BreakerState,
    ): Boolean {
        return when (state.phase) {
            BreakerPhase.Closed -> true
            BreakerPhase.Open -> {
                // Allow probe after cool-down → transitions to HalfOpen when the caller actually tries
                val openedAt = state.openedAt ?: return false
                (now.value - openedAt.value) >= coolDownMs
            }
            BreakerPhase.HalfOpen -> {
                // Allow exactly one probe; the caller must ensure single in-flight attempt
                true
            }
        }
    }

    override fun onSuccess(
        now: TimestampMs,
        state: BreakerState,
    ): BreakerState {
        // Success closes breaker and clears last failure
        return state.copy(
            phase = BreakerPhase.Closed,
            openedAt = null,
            lastFailure = null,
        )
    }

    override fun onFailure(
        now: TimestampMs,
        state: BreakerState,
        error: DomainError,
    ): BreakerState {
        return when (state.phase) {
            BreakerPhase.Closed -> {
                // If we wanted N failures to open, we'd track a counter. We open immediately if failuresToOpen == 1.
                if (failuresToOpen <= 1) {
                    state.copy(phase = BreakerPhase.Open, openedAt = now, lastFailure = error)
                } else {
                    // Minimal variant without a counter: keep Closed but record lastFailure (upgrade later if needed)
                    state.copy(lastFailure = error)
                }
            }
            BreakerPhase.Open -> {
                // Re-open (reset clock)
                state.copy(phase = BreakerPhase.Open, openedAt = now, lastFailure = error)
            }
            BreakerPhase.HalfOpen -> {
                // Half-open probe failed → open again
                state.copy(phase = BreakerPhase.Open, openedAt = now, lastFailure = error)
            }
        }
    }
}

/**
 * Helper used by caller:
 *
 * If state.phase == Open and cool-down elapsed, your orchestration can set
 * state = state.copy(phase = BreakerPhase.HalfOpen) before attempting the probe,
 * or you can attempt and let onSuccess/onFailure handle the transition.
 */
fun moveToHalfOpenIfCooled(
    now: TimestampMs,
    state: BreakerState,
    coolDownMs: Long,
): BreakerState {
    if (state.phase != BreakerPhase.Open) return state
    val openedAt = state.openedAt ?: return state
    return if ((now.value - openedAt.value) >= coolDownMs) {
        state.copy(phase = BreakerPhase.HalfOpen)
    } else {
        state
    }
}
