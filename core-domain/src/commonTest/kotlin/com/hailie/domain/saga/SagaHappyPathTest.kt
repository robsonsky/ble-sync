package com.hailie.domain.saga

import com.hailie.domain.BondStatus
import com.hailie.domain.ConnectionStatus
import com.hailie.domain.DeviceId
import com.hailie.domain.EventCount
import com.hailie.domain.EventOffset
import com.hailie.domain.EventRange
import com.hailie.domain.PageSize
import com.hailie.domain.TimestampMs
import com.hailie.domain.commands.Acknowledge
import com.hailie.domain.commands.BondDevice
import com.hailie.domain.commands.ConnectGatt
import com.hailie.domain.commands.DeliverToApp
import com.hailie.domain.commands.ReadEventCount
import com.hailie.domain.commands.ReadEvents
import com.hailie.domain.events.DeviceBonded
import com.hailie.domain.events.DeviceConnected
import com.hailie.domain.events.EventCountLoaded
import com.hailie.domain.events.EventsAcked
import com.hailie.domain.events.EventsDelivered
import com.hailie.domain.events.EventsRead
import com.hailie.domain.model.initialSyncAggregate
import com.hailie.domain.policy.AdaptivePageSizingPolicy
import com.hailie.domain.policy.RetryDecision
import com.hailie.domain.policy.RetryPolicy
import com.hailie.domain.policy.SimpleBreakerPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RetryGiveUp : RetryPolicy {
    override fun decide(
        now: TimestampMs,
        attemptsForOp: Int,
        reason: com.hailie.domain.RetryReason,
    ): RetryDecision {
        return RetryDecision.GiveUp
    }
}

private fun defaultSaga(): Saga =
    DefaultSaga(
        retryPolicy = RetryGiveUp(),
        breakerForConnect = SimpleBreakerPolicy(failuresToOpen = 1, coolDownMs = 1),
        breakerForRead = SimpleBreakerPolicy(),
        breakerForDeliver = SimpleBreakerPolicy(),
        breakerForAck = SimpleBreakerPolicy(),
        pageSizingPolicy = AdaptivePageSizingPolicy(minPage = 20, maxPage = 100, growStep = 20, shrinkStep = 20),
    )

class SagaHappyPathTest {
    @Test
    fun decision_sequence_happy_path() {
        val device = DeviceId("d1")
        val t = TimestampMs(1_000)

        // Start: not bonded → BondDevice
        val s0 = initialSyncAggregate(deviceId = device, initialPageSize = 50)
        val c0 = defaultSaga().decide(s0, lastEvent = null, now = t)
        assertTrue(c0.first() is BondDevice)

        // DeviceBonded → ConnectGatt
        val s1 = s0.copy(bondStatus = BondStatus.Bonded)
        val c1 = defaultSaga().decide(s1, lastEvent = DeviceBonded(device, t), now = t)
        assertTrue(c1.first() is ConnectGatt)

        // DeviceConnected → ReadEventCount
        val s2 = s1.copy(connectionStatus = ConnectionStatus.Connected)
        val c2 = defaultSaga().decide(s2, lastEvent = DeviceConnected(device, t), now = t)
        assertTrue(c2.first() is ReadEventCount)

        // EventCountLoaded(total=120) → ReadEvents(offset=0, count=50)
        val s3 =
            s2.copy(
                totalOnDevice = EventCount(120),
                pageSize = PageSize(50),
                lastAckedExclusive = EventOffset(0),
            )
        val c3 = defaultSaga().decide(s3, lastEvent = EventCountLoaded(device, t, total = EventCount(120)), now = t)
        val read1 = c3.first() as ReadEvents
        assertEquals(0, read1.offset.value)
        assertEquals(50, read1.count)

        // EventsRead([0,50)) → DeliverToApp([0,50))
        val range1 = EventRange(EventOffset(0), EventOffset(50))
        val c4 = defaultSaga().decide(s3, lastEvent = EventsRead(device, t, range1), now = t)
        val deliver1 = c4.first() as DeliverToApp
        assertEquals(range1, deliver1.range)

        // EventsDelivered([0,50)) → Acknowledge(upTo=50)
        val c5 = defaultSaga().decide(s3, lastEvent = EventsDelivered(device, t, range1), now = t)
        val ack1 = c5.first() as Acknowledge
        assertEquals(50, ack1.upTo.value)

        // After ack to 50 and total=120 → next ReadEvents(offset=50, count=70) due to tuning (+20 growStep)
        val s4 = s3.copy(lastAckedExclusive = EventOffset(50))
        val c6 = defaultSaga().decide(s4, lastEvent = EventsAcked(device, t, upTo = EventOffset(50)), now = t)
        val read2 = c6.first() as ReadEvents
        assertEquals(50, read2.offset.value)
        assertEquals(70, read2.count) // tuned from 50 → 70 (Stable outcome, +20 growStep)

        // If we caught up (ack upTo == total), next step is ReadEventCount (re-check growth)
        val sDone = s3.copy(lastAckedExclusive = EventOffset(120))
        val cDone = defaultSaga().decide(sDone, lastEvent = EventsAcked(device, t, upTo = EventOffset(120)), now = t)
        assertTrue(cDone.first() is ReadEventCount)
    }
}
