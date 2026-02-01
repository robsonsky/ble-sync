package com.hailie.domain

/**
 * Strongly typed device identifier (non-blank).
 *
 * Why: avoid "primitive obsession" and accidental mixups with other strings.
 */
@JvmInline
value class DeviceId(val raw: String) {
    init {
        require(raw.isNotBlank()) { "DeviceId cannot be blank" }
    }

    override fun toString(): String = raw
}

/**
 * Zero-based offset in the event stream/log.
 *
 * Invariants:
 * - Must be >= 0
 * - Use [plus] to move forward safely
 */
@JvmInline
value class EventOffset(val value: Long) {
    init {
        require(value >= 0) { "EventOffset must be >= 0" }
    }

    operator fun plus(count: Long): EventOffset = EventOffset(value + count)
}

/**
 * Count of events, >= 0.
 */
@JvmInline
value class EventCount(val value: Long) {
    init {
        require(value >= 0) { "EventCount must be >= 0" }
    }
}

/**
 * Page size used when reading events from device.
 *
 * Invariant: strictly > 0.
 */
@JvmInline
value class PageSize(val value: Int) {
    init {
        require(value > 0) { "PageSize must be > 0" }
    }
}

/**
 * UTC timestamp in milliseconds since epoch.
 * Kept as value class for portability and testability.
 */
@JvmInline
value class TimestampMs(val value: Long)

/**
 * Half-open range [startInclusive, endExclusive) of events.
 *
 * Invariants:
 * - endExclusive >= startInclusive
 * - count = endExclusive - startInclusive
 */
data class EventRange(
    val startInclusive: EventOffset,
    val endExclusive: EventOffset,
) {
    init {
        require(endExclusive.value >= startInclusive.value) { "Invalid range: end < start" }
    }

    /** True when start == end (no events). */
    val isEmpty: Boolean get() = endExclusive.value == startInclusive.value

    /** Number of events in the range. */
    val count: Long get() = endExclusive.value - startInclusive.value
}

/** Current bond (pairing) status with the device. */
enum class BondStatus { Unknown, NotBonded, Bonding, Bonded }

/** High-level connection state for GATT transport. */
enum class ConnectionStatus { Disconnected, Connecting, Connected }

/**
 * Reasons that trigger retry/backoff logic.
 *
 * These are domain-level intents, not platform exceptions.
 */
sealed interface RetryReason {
    /** Transient GATT problem; should stabilize after backoff. */
    data object TemporaryGattError : RetryReason

    /** Radio stack is busy; try again later. */
    data object RadioBusy : RetryReason

    /** Generic "backoff after failure" bucket. */
    data object BackoffAfterFailure : RetryReason

    /** Custom reason for debugging/experiments. */
    data class Custom(val message: String) : RetryReason
}

/**
 * Why a link disconnected.
 */
sealed interface DisconnectReason {
    /** Peer closed the link. */
    data object PeerClosed : DisconnectReason

    /** Timeout on connect/IO. */
    data object Timeout : DisconnectReason

    /** Platform signaled a GATT error. */
    data object GattError : DisconnectReason

    /** Custom reason for diagnostics. */
    data class Custom(val message: String) : DisconnectReason
}

/**
 * Domain-level errors surfaced for decision/policy/UI.
 */
sealed interface DomainError {
    /** OS/app permission is missing; requires user consent. */
    data class PermissionRequired(val permission: String) : DomainError

    /** User must perform an action (e.g. turn on Bluetooth). */
    data class UserActionRequired(val action: String) : DomainError

    /** Transport-level failure (e.g. GATT), with optional code. */
    data class Transport(val message: String, val code: Int? = null) : DomainError

    /** Protocol/format invariants were violated. */
    data class Protocol(val message: String) : DomainError

    /** Catch-all for unexpected conditions. */
    data class Unexpected(val message: String) : DomainError
}

/**
 * Circuit-breaker phases:
 * - Closed: traffic flows.
 * - Open: block for cool-down after failures.
 * - HalfOpen: probe a single request; close on success or open again on failure.
 */
enum class BreakerPhase { Closed, Open, HalfOpen }

/**
 * Circuit-breaker state for a particular concern (connect/read/deliver/ack).
 *
 * @property phase Current breaker phase.
 * @property openedAt When the breaker moved to Open (for cool-down).
 * @property lastFailure Last domain error observed (for diagnostics/telemetry).
 */
data class BreakerState(
    val phase: BreakerPhase = BreakerPhase.Closed,
    val openedAt: TimestampMs? = null,
    val lastFailure: DomainError? = null,
)

/**
 * Key for tracking attempt counts by operation kind (e.g., "ConnectGatt").
 */
@JvmInline
value class AttemptKey(val raw: String) {
    override fun toString(): String = raw
}

/**
 * Immutable map of attempts per key (for retry caps/backoff).
 *
 * Use [inc] to increment safely and get a new instance.
 */
data class AttemptCounters(
    val byKey: Map<AttemptKey, Int> = emptyMap(),
) {
    /** Returns a new counters map with [key] incremented by 1. */
    fun inc(key: AttemptKey): AttemptCounters {
        val next = (byKey[key] ?: 0) + 1
        return copy(byKey = byKey + (key to next))
    }

    /** Current attempt count for [key] (0 if none). */
    fun get(key: AttemptKey): Int = byKey[key] ?: 0
}

/**
 * Lightweight human-readable marker for "where in the flow" we are.
 *
 * Tip: keep labels stable for analytics/troubleshooting.
 */
@JvmInline
value class SagaCursor(val label: String) {
    override fun toString(): String = label
}
