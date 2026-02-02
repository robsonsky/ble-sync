package com.hailie.demo.scenario

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class FaultScript(
    // Bond/Pairing
    val bondFailRate: Double = 0.0,
    val bondRetryMax: Int = 0,
    val bondDelay: Duration = 500.milliseconds,
    // Connect
    val connectDropRate: Double = 0.0,
    val connectRetryMax: Int = 0,
    val connectDelay: Duration = 400.milliseconds,
    // Read/Ack
    val readFailRate: Double = 0.0,
    val ackFailRate: Double = 0.0,
    val pageLatency: Duration = 120.milliseconds,
    val pageSizeMean: Int = 50,
    // Performance
    val bytesPerPage: Int = 120,
    val jitterMs: Int = 40,
    // Environment (background loss, radio interference)
    val randomDisconnectRatePerMinute: Double = 0.0,
)

enum class ScenarioPreset { HappyPath, PairingReliability, ConnectionStability, SlowSyncPerformance }

fun presetScript(p: ScenarioPreset): FaultScript =
    when (p) {
        ScenarioPreset.HappyPath -> FaultScript()
        ScenarioPreset.PairingReliability ->
            FaultScript(
                bondFailRate = 0.35,
                bondRetryMax = 3,
                bondDelay = 1.2.seconds,
            )
        ScenarioPreset.ConnectionStability ->
            FaultScript(
                connectDropRate = 0.20,
                connectRetryMax = 4,
                connectDelay = 1.seconds,
                randomDisconnectRatePerMinute = 0.6,
            )
        ScenarioPreset.SlowSyncPerformance ->
            FaultScript(
                pageLatency = 900.milliseconds,
                pageSizeMean = 30,
                bytesPerPage = 64,
                jitterMs = 180,
            )
    }
