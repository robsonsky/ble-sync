package com.hailie.domain.saga

import com.hailie.domain.BondStatus
import com.hailie.domain.ConnectionStatus
import com.hailie.domain.DeviceId
import com.hailie.domain.EventCount
import com.hailie.domain.EventOffset
import com.hailie.domain.PageSize
import com.hailie.domain.TimestampMs
import com.hailie.domain.commands.ConnectGatt
import com.hailie.domain.commands.ReadEventCount
import com.hailie.domain.commands.ReadEvents
import com.hailie.domain.events.DeviceBonded
import com.hailie.domain.events.DeviceConnected
import com.hailie.domain.events.EventCountLoaded
import com.hailie.domain.model.SyncAggregate
import com.hailie.domain.model.initialSyncAggregate
import com.hailie.domain.policy.AdaptivePageSizingPolicy
import com.hailie.domain.policy.RetryDecision
import com.hailie.domain.policy.RetryPolicy
import com.hailie.domain.policy.SimpleBreakerPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class NoopRetryPolicy : RetryPolicy {
    override fun decide(
        now: TimestampMs,
        attemptsForOp: Int,
        reason: com.hailie.domain.RetryReason,
    ): RetryDecision {
        return RetryDecision.GiveUp
    }
}

class SagaDecisionTableTest {
    private fun saga(): Saga =
        DefaultSaga(
            retryPolicy = NoopRetryPolicy(),
            breakerForConnect = SimpleBreakerPolicy(failuresToOpen = 1, coolDownMs = 1),
            breakerForRead = SimpleBreakerPolicy(),
            breakerForDeliver = SimpleBreakerPolicy(),
            breakerForAck = SimpleBreakerPolicy(),
            pageSizingPolicy = AdaptivePageSizingPolicy(minPage = 20, maxPage = 100, growStep = 20, shrinkStep = 20),
        )

    @Test
    fun notBonded_thenBond_thenConnect_thenCount() {
        val device = DeviceId("d1")
        val t0 = TimestampMs(1_000)

        // Start
        val s0 = initialSyncAggregate(deviceId = device, initialPageSize = 50)
        val cmds0 = saga().decide(s0, lastEvent = null, now = t0)
        // Not bonded → should emit BondDevice
        assertEquals("BondDevice", cmds0.first()::class.simpleName)

        // After DeviceBonded → expect ConnectGatt
        val s1 = s0.copy(bondStatus = BondStatus.Bonded)
        val cmds1 = saga().decide(s1, lastEvent = DeviceBonded(device, t0), now = t0)
        assertTrue(cmds1.first() is ConnectGatt)

        // After DeviceConnected → expect ReadEventCount
        val s2 = s1.copy(connectionStatus = ConnectionStatus.Connected)
        val cmds2 = saga().decide(s2, lastEvent = DeviceConnected(device, t0), now = t0)
        assertTrue(cmds2.first() is ReadEventCount)
    }

    @Test
    fun afterCount_loaded_then_read_from_highWater() {
        val device = DeviceId("d1")
        val t0 = TimestampMs(2_000)
        val s =
            SyncAggregate(
                deviceId = device,
                bondStatus = BondStatus.Bonded,
                connectionStatus = ConnectionStatus.Connected,
                lastAckedExclusive = EventOffset(0),
                totalOnDevice = EventCount(120),
                pageSize = PageSize(50),
            )

        val cmds = saga().decide(s, lastEvent = EventCountLoaded(device, t0, total = EventCount(120)), now = t0)
        val c = cmds.first() as ReadEvents
        assertEquals(0, c.offset.value)
        assertEquals(50, c.count)
    }
}
