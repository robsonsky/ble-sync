package com.hailie.runtime.ports

import com.hailie.domain.DeviceId
import com.hailie.domain.EventRange
import com.hailie.domain.events.Event

/**
 * Synchronous delivery: return EventsDelivered on success or a failure event on error.
 */
interface DeliveryPort {
    fun deliver(deviceId: DeviceId, range: EventRange): Event // EventsDelivered or SyncFailed/Disconnected
}
