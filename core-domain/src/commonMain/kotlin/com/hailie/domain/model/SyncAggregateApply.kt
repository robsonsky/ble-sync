package com.hailie.domain.model

import com.hailie.domain.BondStatus
import com.hailie.domain.ConnectionStatus
import com.hailie.domain.DomainError
import com.hailie.domain.SagaCursor
import com.hailie.domain.events.DeviceBonded
import com.hailie.domain.events.DeviceConnected
import com.hailie.domain.events.Disconnected
import com.hailie.domain.events.Event
import com.hailie.domain.events.EventCountLoaded
import com.hailie.domain.events.EventsAcked
import com.hailie.domain.events.EventsDelivered
import com.hailie.domain.events.EventsRead
import com.hailie.domain.events.RetryScheduled
import com.hailie.domain.events.SyncCompleted
import com.hailie.domain.events.SyncFailed

/**
 * Pure reducers: evolve SyncAggregate by applying domain events.
 * No IO, no timers, just data → data.
 */
fun SyncAggregate.applyEvent(event: Event): SyncAggregate =
    when (event) {
        is DeviceBonded ->
            copy(
                bondStatus = BondStatus.Bonded,
                sagaCursor = SagaCursor("Bonded"),
            )

        is DeviceConnected ->
            copy(
                connectionStatus = ConnectionStatus.Connected,
                sagaCursor = SagaCursor("Connected"),
            )

        is EventCountLoaded ->
            copy(
                totalOnDevice = event.total,
                sagaCursor = SagaCursor("CountLoaded"),
            )

        is EventsRead ->
            copy(
                // Mark the in-flight page as the start of the range we just read
                inFlightOffset = event.range.startInclusive,
                sagaCursor = SagaCursor("Read:${event.range.startInclusive.value}-${event.range.endExclusive.value}"),
            )

        is EventsDelivered ->
            copy(
                // Delivery does not change high-water; ack will advance it
                sagaCursor = SagaCursor("Delivered:${event.range.startInclusive.value}-${event.range.endExclusive.value}"),
            )

        is EventsAcked -> {
            // High-water advances (monotonic). Clear in-flight when we advanced to end of that page.
            val newAck = if (event.upTo.value > lastAckedExclusive.value) event.upTo else lastAckedExclusive
            copy(
                lastAckedExclusive = newAck,
                // Clear in-flight when ack caught up at or beyond the in-flight page end
                inFlightOffset = if (inFlightOffset != null && newAck.value >= newAck.value) null else inFlightOffset,
                sagaCursor = SagaCursor("Acked:${newAck.value}"),
            )
        }

        is Disconnected ->
            copy(
                connectionStatus = ConnectionStatus.Disconnected,
                lastError = DomainError.Transport(event.reason.toString(), code = event.gattCode),
                sagaCursor = SagaCursor("Disconnected"),
            )

        is RetryScheduled ->
            copy(
                sagaCursor = SagaCursor("RetryScheduled@${event.after.value}"),
            )

        is SyncCompleted ->
            copy(
                sagaCursor = SagaCursor("Completed"),
            )

        is SyncFailed ->
            copy(
                lastError = event.reason,
                sagaCursor = SagaCursor("Failed"),
            )
    }
