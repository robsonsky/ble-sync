package com.hailie.runtime.fakes

import com.hailie.domain.DeviceId
import com.hailie.domain.DisconnectReason
import com.hailie.domain.DomainError
import com.hailie.domain.EventCount
import com.hailie.domain.EventOffset
import com.hailie.domain.EventRange
import com.hailie.domain.events.DeviceBonded
import com.hailie.domain.events.DeviceConnected
import com.hailie.domain.events.Disconnected
import com.hailie.domain.events.Event
import com.hailie.domain.events.EventCountLoaded
import com.hailie.domain.events.EventsAcked
import com.hailie.domain.events.EventsDelivered
import com.hailie.domain.events.EventsRead
import com.hailie.domain.events.SyncFailed
import com.hailie.runtime.ports.BlePort
import com.hailie.runtime.ports.DeliveryPort

class FakeBlePort(
    private val clock: FakeClockPort,
    private val bondScript: FaultScript = FaultScript(0),
    private val connectScript: FaultScript = FaultScript(0),
    private val readCountScript: FaultScript = FaultScript(0),
    private val readPageScript: FaultScript = FaultScript(0),
    private val ackScript: FaultScript = FaultScript(0),
    private val totalOnDevice: EventCount = EventCount(120),
) : BlePort {
    override fun bond(deviceId: DeviceId): Event {
        return if (bondScript.shouldFail()) {
            Disconnected(deviceId, clock.now(), reason = DisconnectReason.Timeout, gattCode = 133)
        } else {
            DeviceBonded(deviceId, clock.now())
        }
    }

    override fun connect(deviceId: DeviceId): Event {
        return if (connectScript.shouldFail()) {
            Disconnected(deviceId, clock.now(), reason = DisconnectReason.Timeout, gattCode = 8)
        } else {
            DeviceConnected(deviceId, clock.now())
        }
    }

    override fun disconnect(deviceId: DeviceId): Event {
        return Disconnected(deviceId, clock.now(), reason = DisconnectReason.PeerClosed, gattCode = null)
    }

    override fun readCount(deviceId: DeviceId): Event {
        return if (readCountScript.shouldFail()) {
            Disconnected(deviceId, clock.now(), reason = DisconnectReason.Timeout, gattCode = 22)
        } else {
            EventCountLoaded(deviceId, clock.now(), total = totalOnDevice)
        }
    }

    override fun readPage(
        deviceId: DeviceId,
        offset: EventOffset,
        count: Int,
    ): Event {
        return if (readPageScript.shouldFail()) {
            Disconnected(deviceId, clock.now(), reason = DisconnectReason.GattError, gattCode = 42)
        } else {
            val end = EventOffset(offset.value + count)
            EventsRead(deviceId, clock.now(), EventRange(offset, end))
        }
    }

    override fun ack(
        deviceId: DeviceId,
        upTo: EventOffset,
    ): Event {
        return if (ackScript.shouldFail()) {
            SyncFailed(deviceId, clock.now(), reason = DomainError.Transport("ack failed"))
        } else {
            EventsAcked(deviceId, clock.now(), upTo)
        }
    }
}

class FakeDeliveryPort(
    private val clock: FakeClockPort,
    private val failN: Int = 0,
) : DeliveryPort {
    private var remaining = failN

    override fun deliver(
        deviceId: DeviceId,
        range: EventRange,
    ): Event {
        return if (remaining > 0) {
            remaining -= 1
            SyncFailed(deviceId, clock.now(), reason = DomainError.Transport("delivery failed"))
        } else {
            EventsDelivered(deviceId, clock.now(), range)
        }
    }
}
