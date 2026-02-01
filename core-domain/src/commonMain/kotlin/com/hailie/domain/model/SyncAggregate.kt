package com.hailie.domain.model

import com.hailie.domain.AttemptCounters
import com.hailie.domain.BondStatus
import com.hailie.domain.BreakerState
import com.hailie.domain.ConnectionStatus
import com.hailie.domain.DeviceId
import com.hailie.domain.DomainError
import com.hailie.domain.EventCount
import com.hailie.domain.EventOffset
import com.hailie.domain.PageSize
import com.hailie.domain.SagaCursor

/**
 * Snapshot of sync state for a single device.
 *
 * This is the only mutable concept in the domain, but we keep it immutable by
 * creating new copies on changes (via pure reducers you'll add next).
 */
data class SyncAggregate(
    /** The device this aggregate represents. */
    val deviceId: DeviceId,
    /** OS bond status. */
    val bondStatus: BondStatus = BondStatus.Unknown,
    /** Transport connection status. */
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    /**
     * High-water mark: all events with offset < lastAckedExclusive are acknowledged.
     * Invariant: monotonic non-decreasing.
     */
    val lastAckedExclusive: EventOffset = EventOffset(0),
    /**
     * Current page being read (null if idle). Helps resume after disconnect.
     */
    val inFlightOffset: EventOffset? = null,
    /**
     * Observed total number of events on the device.
     * May grow while syncing if device keeps generating events.
     */
    val totalOnDevice: EventCount = EventCount(0),
    /**
     * Desired page size for ReadEvents commands (policy-tunable).
     */
    val pageSize: PageSize = PageSize(50),
    /**
     * Attempt counters per operation key (for retry caps/backoff).
     */
    val attempts: AttemptCounters = AttemptCounters(),
    /** Circuit breakers per major operation. */
    val bondBreaker: BreakerState = BreakerState(),
    val connectBreaker: BreakerState = BreakerState(),
    val readBreaker: BreakerState = BreakerState(),
    val deliverBreaker: BreakerState = BreakerState(),
    val ackBreaker: BreakerState = BreakerState(),
    /** Last error for observability/UI. */
    val lastError: DomainError? = null,
    /** Human-readable marker for saga progress. */
    val sagaCursor: SagaCursor = SagaCursor("Init"),
) {
    /** True when we've acknowledged everything observed so far. */
    val isFullyAcked: Boolean get() = lastAckedExclusive.value >= totalOnDevice.value

    /** True when a page read is currently in progress. */
    val hasInFlight: Boolean get() = inFlightOffset != null
}

/**
 * Helper to create a fresh aggregate with a chosen initial page size.
 *
 * @param deviceId Target device.
 * @param initialPageSize First guess for paging (will be tuned by policy later).
 */
fun initialSyncAggregate(
    deviceId: DeviceId,
    initialPageSize: Int = 50,
): SyncAggregate =
    SyncAggregate(
        deviceId = deviceId,
        pageSize = PageSize(initialPageSize),
    )
