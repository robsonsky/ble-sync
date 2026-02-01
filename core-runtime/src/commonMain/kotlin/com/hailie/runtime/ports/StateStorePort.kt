package com.hailie.runtime.ports

import com.hailie.domain.DeviceId
import com.hailie.domain.EventOffset
import com.hailie.domain.PageSize
import com.hailie.domain.SagaCursor

/**
 * Minimal snapshot for resume. Avoid storing large payloads. Immutable value.
 */
data class SyncSnapshot(
    val deviceId: DeviceId,
    val lastAckedExclusive: EventOffset,
    val pageSize: PageSize,
    val sagaCursor: SagaCursor,
)

interface StateStorePort {
    fun read(deviceId: DeviceId): SyncSnapshot?

    fun write(snapshot: SyncSnapshot)
}
