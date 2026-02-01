package com.hailie.runtime.ports

import com.hailie.domain.DeviceId
import com.hailie.domain.EventOffset
import com.hailie.domain.events.Event

/**
 * All methods are synchronous from the actor point of view to keep serialization trivial.
 * Port implementations may block or simulate latency; tests will use virtual time.
 */
interface BlePort {
    fun bond(deviceId: DeviceId): Event // DeviceBonded or Disconnected/SyncFailed

    fun connect(deviceId: DeviceId): Event // DeviceConnected or Disconnected

    fun disconnect(deviceId: DeviceId): Event // Disconnected

    fun readCount(deviceId: DeviceId): Event // EventCountLoaded or Disconnected/SyncFailed

    fun readPage(deviceId: DeviceId, offset: EventOffset, count: Int): Event // EventsRead or Disconnected/SyncFailed

    fun ack(deviceId: DeviceId, upTo: EventOffset): Event // EventsAcked or Disconnected/SyncFailed
}
