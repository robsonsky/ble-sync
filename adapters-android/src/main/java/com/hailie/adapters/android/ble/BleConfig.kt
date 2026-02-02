package com.hailie.adapters.android.ble

import java.util.UUID

data class BleConfig(
    val serviceUuid: UUID,
    val countCharacteristicUuid: UUID,
    val pageCharacteristicUuid: UUID,
    val ackCharacteristicUuid: UUID,
    val connectTimeoutMs: Long = 10_000,
    val bondTimeoutMs: Long = 20_000,
    val readCountTimeoutMs: Long = 5_000,
    val readPageTimeoutMs: Long = 8_000,
    val ackTimeoutMs: Long = 5_000,
)
