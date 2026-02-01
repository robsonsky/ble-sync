package com.hailie.domain

import com.hailie.domain.model.initialSyncAggregate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Minimal sanity checks to validate commonTest wiring and basic invariants.
 */
class DomainSmokeTest {
    @Test
    fun createAggregate_defaultsAreSane() {
        val device = DeviceId("demo-123")
        val agg = initialSyncAggregate(deviceId = device, initialPageSize = 32)

        assertEquals(device, agg.deviceId)
        assertEquals(32, agg.pageSize.value)
        assertEquals(0L, agg.lastAckedExclusive.value)
        assertTrue(agg.connectionStatus == ConnectionStatus.Disconnected)
        assertTrue(!agg.hasInFlight)
    }
}
