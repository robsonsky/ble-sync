package com.hailie.runtime.fakes

import com.hailie.domain.DeviceId
import com.hailie.runtime.ports.StateStorePort
import com.hailie.runtime.ports.SyncSnapshot

class FakeStateStore : StateStorePort {
    private val map = mutableMapOf<String, SyncSnapshot>()

    override fun read(deviceId: DeviceId): SyncSnapshot? = map[deviceId.raw]

    override fun write(snapshot: SyncSnapshot) {
        map[snapshot.deviceId.raw] = snapshot
    }
}
