package com.hailie.domain.events

import com.hailie.domain.DeviceId
import com.hailie.domain.DisconnectReason
import com.hailie.domain.DomainError
import com.hailie.domain.EventCount
import com.hailie.domain.EventOffset
import com.hailie.domain.EventRange
import com.hailie.domain.TimestampMs

/**
 * Domain events are facts emitted after executing commands.
 * They mutate aggregate state (via pure reducers you'll add next).
 */
sealed interface Event {
    /** Device the event refers to. */
    val deviceId: DeviceId

    /** When the event was observed/emitted (UTC ms). */
    val at: TimestampMs
}

/** Device is now bonded (paired). */
data class DeviceBonded(
    override val deviceId: DeviceId,
    override val at: TimestampMs,
) : Event

/** GATT connection established. */
data class DeviceConnected(
    override val deviceId: DeviceId,
    override val at: TimestampMs,
) : Event

/** Total event count measured on device. */
data class EventCountLoaded(
    override val deviceId: DeviceId,
    override val at: TimestampMs,
    val total: EventCount,
) : Event

/** A page of events has been read from device. */
data class EventsRead(
    override val deviceId: DeviceId,
    override val at: TimestampMs,
    val range: EventRange,
) : Event

/** A page/range of events has been delivered to the app. */
data class EventsDelivered(
    override val deviceId: DeviceId,
    override val at: TimestampMs,
    val range: EventRange,
) : Event

/** High-water mark advanced (events below upTo are acknowledged). */
data class EventsAcked(
    override val deviceId: DeviceId,
    override val at: TimestampMs,
    val upTo: EventOffset,
) : Event

/** Link dropped (includes reason and optional low-level code). */
data class Disconnected(
    override val deviceId: DeviceId,
    override val at: TimestampMs,
    val reason: DisconnectReason,
    val gattCode: Int? = null,
) : Event

/** Retry was scheduled to fire at [after]. */
data class RetryScheduled(
    override val deviceId: DeviceId,
    override val at: TimestampMs,
    val after: TimestampMs,
) : Event

/** Sync fully completed (no more work pending). */
data class SyncCompleted(
    override val deviceId: DeviceId,
    override val at: TimestampMs,
) : Event

/** Sync failed with a domain-level error (policy/UI can react). */
data class SyncFailed(
    override val deviceId: DeviceId,
    override val at: TimestampMs,
    val reason: DomainError,
) : Event
