package com.hailie.domain.commands

import com.hailie.domain.DeviceId
import com.hailie.domain.EventOffset
import com.hailie.domain.EventRange
import com.hailie.domain.RetryReason
import com.hailie.domain.TimestampMs

/**
 * Commands describe "what we intend to do".
 * They are inputs to adapters/saga; they do not execute work directly.
 */
sealed interface Command {
    /** Target device for this command. */
    val deviceId: DeviceId
}

/** Ensure device is bonded (paired) at OS level. */
data class BondDevice(
    override val deviceId: DeviceId,
) : Command

/** Establish a GATT connection with the device. */
data class ConnectGatt(
    override val deviceId: DeviceId,
) : Command

/** Query total number of events available on the device. */
data class ReadEventCount(
    override val deviceId: DeviceId,
) : Command

/**
 * Read a page of events starting at [offset], up to [count] events.
 *
 * @param offset Start position (0-based).
 * @param count Strictly positive page size.
 */
data class ReadEvents(
    override val deviceId: DeviceId,
    val offset: EventOffset,
    val count: Int,
) : Command {
    init {
        require(count > 0) { "ReadEvents.count must be > 0" }
    }
}

/**
 * Deliver a [range] of events to the host app.
 * This is logically separate from "read" to decouple IO and handoff.
 */
data class DeliverToApp(
    override val deviceId: DeviceId,
    val range: EventRange,
) : Command

/**
 * Acknowledge that events below [upTo] are durably handled by the app.
 * Advances the high-water mark ("lastAckedExclusive").
 */
data class Acknowledge(
    override val deviceId: DeviceId,
    val upTo: EventOffset,
) : Command

/**
 * Ask the saga to schedule a retry after [after] due to [reason].
 * Policies decide caps/backoff; this only carries intent/timing.
 */
data class ScheduleRetry(
    override val deviceId: DeviceId,
    val after: TimestampMs,
    val reason: RetryReason,
) : Command

/** Stop the sync flow (e.g., user navigated away). */
data class Stop(
    override val deviceId: DeviceId,
) : Command
