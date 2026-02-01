package com.hailie.domain.model

import com.hailie.domain.DeviceId
import com.hailie.domain.DomainError
import com.hailie.domain.EventOffset
import com.hailie.domain.TimestampMs
import com.hailie.domain.events.Disconnected
import com.hailie.domain.events.EventsAcked
import com.hailie.domain.events.SyncFailed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainInvariantsTest {
    @Test
    fun lastAckedExclusive_is_monotonic() {
        val device = DeviceId("mono")
        val t = TimestampMs(10_000)
        val s0 = initialSyncAggregate(device)

        // Apply ack to 50
        val s1 = s0.applyEvent(EventsAcked(device, t, upTo = EventOffset(50)))
        assertEquals(50, s1.lastAckedExclusive.value)

        // Apply ack to 40 (lower) → must NOT decrease
        val s2 = s1.applyEvent(EventsAcked(device, t, upTo = EventOffset(40)))
        assertEquals(50, s2.lastAckedExclusive.value)

        // Apply ack to 50 again (same) → no double-advance
        val s3 = s2.applyEvent(EventsAcked(device, t, upTo = EventOffset(50)))
        assertEquals(50, s3.lastAckedExclusive.value)

        // Apply ack to 80 → advances
        val s4 = s3.applyEvent(EventsAcked(device, t, upTo = EventOffset(80)))
        assertEquals(80, s4.lastAckedExclusive.value)
    }

    @Test
    fun permission_and_user_action_errors_are_surfacable_via_events() {
        val device = DeviceId("perm")
        val t = TimestampMs(11_000)
        val s0 = initialSyncAggregate(device)

        // Surface a permission required error via SyncFailed
        val s1 = s0.applyEvent(SyncFailed(device, t, reason = DomainError.PermissionRequired("android.permission.BLUETOOTH_CONNECT")))
        assertTrue(s1.lastError is DomainError.PermissionRequired)

        // Surface a transport-level disconnect error via Disconnected
        val s2 = s1.applyEvent(Disconnected(device, t, reason = com.hailie.domain.DisconnectReason.Timeout, gattCode = 133))
        assertTrue(s2.lastError is DomainError.Transport)
    }
}
