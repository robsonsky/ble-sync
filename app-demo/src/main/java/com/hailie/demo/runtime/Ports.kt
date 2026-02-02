package com.hailie.demo.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable

// Minimal telemetry event for demo; match your real TelemetryEvent as needed
@Serializable
data class TelemetryEvent(
    val name: String,
    val ts: Long,
    val deviceId: String,
    val data: Map<String, String> = emptyMap(),
)

interface TelemetrySink {
    val telemetry: Flow<TelemetryEvent>

    fun emit(ev: TelemetryEvent)
}

class InMemoryTelemetry : TelemetrySink {
    private val flow = MutableSharedFlow<TelemetryEvent>(extraBufferCapacity = 128)
    override val telemetry: Flow<TelemetryEvent> = flow

    override fun emit(ev: TelemetryEvent) {
        flow.tryEmit(ev)
    }
}

@Serializable
data class SyncSnapshot(val lastAckedExclusive: Long = 0L)

interface StateStore {
    fun write(s: SyncSnapshot)

    fun read(): SyncSnapshot?
}

class InMemoryStateStore : StateStore {
    private var s: SyncSnapshot? = null

    override fun write(s: SyncSnapshot) {
        this.s = s
    }

    override fun read(): SyncSnapshot? = s
}

data class DeviceId(val raw: String = "FA:KE:DE:MO:00:01")
