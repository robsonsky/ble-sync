package com.hailie.demo.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hailie.demo.runtime.DeviceId
import com.hailie.demo.runtime.InMemoryStateStore
import com.hailie.demo.runtime.InMemoryTelemetry
import com.hailie.demo.runtime.StateStore
import com.hailie.demo.runtime.SyncSnapshot
import com.hailie.demo.runtime.TelemetryEvent
import com.hailie.demo.scenario.FaultScript
import com.hailie.demo.scenario.ScenarioPreset
import com.hailie.demo.scenario.presetScript
import com.hailie.demo.sim.FakeBlePort
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max

data class Metrics(
    val pairingTimeMs: Long = 0,
    val pairingRetries: Int = 0,
    val breakerOpenMs: Long = 0,
    val disconnects: Int = 0,
    val reconnectLatencyMsAvg: Long = 0,
    val failuresBeforeBreaker: Int = 0,
    val ttfpMs: Long = 0,
    val pagesPerMin: Double = 0.0,
    val bytesPerMin: Double = 0.0,
    val avgPageLatencyMs: Long = 0,
    val pageSizeAvg: Double = 0.0,
    val deliveredPages: Long = 0,
    val ackedUpTo: Long = 0,
    val duplicates: Long = 0,
)

data class UiState(
    val running: Boolean = false,
    val selectedPreset: ScenarioPreset = ScenarioPreset.HappyPath,
    val script: FaultScript = presetScript(ScenarioPreset.HappyPath),
    val deviceId: DeviceId = DeviceId(),
    val progressText: String = "Idle",
    val metrics: Metrics = Metrics(),
    val telemetryJsonPreview: String = "",
    val logLines: List<String> = emptyList(),
    val logExpanded: Boolean = false,
    val coldStartResumeEnabled: Boolean = true,
)

class ScenarioViewModel(app: Application) : AndroidViewModel(app) {
    private val json = Json { encodeDefaults = true }
    private val stateStore: StateStore = InMemoryStateStore()
    private val sink: InMemoryTelemetry = InMemoryTelemetry()
    private val ble = FakeBlePort(viewModelScope, sink)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var runnerJob: Job? = null
    private val logs = mutableListOf<String>()
    private val telemetryBuffer = mutableListOf<TelemetryEvent>()

    init {
        // Observe telemetry into UI and metrics aggregation
        viewModelScope.launch {
            sink.telemetry.collect { ev ->
                telemetryBuffer += ev
                appendLog(
                    "${ev.ts} ${ev.name} ${ev.data}",
                )
                aggregate(ev)
            }
        }
    }

    fun selectPreset(p: ScenarioPreset) {
        _ui.update { it.copy(selectedPreset = p, script = presetScript(p)) }
    }

    fun toggleLogExpanded() {
        _ui.update { it.copy(logExpanded = !it.logExpanded) }
    }

    fun toggleColdStartResume(enabled: Boolean) {
        _ui.update { it.copy(coldStartResumeEnabled = enabled) }
    }

    fun start() {
        if (_ui.value.running) return
        val script = _ui.value.script
        logs.clear()
        telemetryBuffer.clear()
        _ui.update {
            it.copy(
                running = true,
                progressText = "Starting...",
                metrics = Metrics(),
                logLines = emptyList(),
            )
        }
        runnerJob =
            viewModelScope.launch {
                runScenario(script)
                _ui.update { it.copy(running = false, progressText = "Stopped") }
            }
    }

    fun stop() {
        runnerJob?.cancel()
        _ui.update { it.copy(running = false, progressText = "Stopped") }
    }

    @Suppress("TooGenericExceptionThrown")
    fun killAppNow() {
        // Persist snapshot (simulate crash-resume durability)
        val ackedUpTo = _ui.value.metrics.ackedUpTo
        stateStore.write(SyncSnapshot(lastAckedExclusive = ackedUpTo))
        // Simulate crash
        throw RuntimeException("KillAppNow requested")
    }

    fun copyLogsToClipboard() {
        val cm =
            getApplication<Application>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = logs.joinToString("\n")
        cm.setPrimaryClip(ClipData.newPlainText("ScenarioLab Logs", text))
    }

    fun exportTelemetryJson(): String {
        return json.encodeToString(telemetryBuffer)
    }

    @Suppress("LongMethod", "Indentation")
    private suspend fun runScenario(s: FaultScript) {
        val device = _ui.value.deviceId
        val startAll = now()
        _ui.update { it.copy(progressText = "Bonding...") }
        val bondStart = now()
        val bonded = ble.bond(device, s)
        val pairingTime = now() - bondStart
        _ui.update { it.copy(metrics = it.metrics.copy(pairingTimeMs = pairingTime)) }
        if (!bonded) {
            appendLog("Bond failed permanently")
            return
        }
        _ui.update { it.copy(progressText = "Connecting...") }
        val connected = ble.connect(device, s)
        if (!connected) {
            appendLog("Connect failed permanently")
            return
        }
        val firstPageStart = now()
        var offset = stateStore.read()?.lastAckedExclusive ?: 0L
        var delivered = 0L
        var sumLat = 0L
        var sumPageSize = 0L
        var pages = 0L
        var bytesSum = 0L

        _ui.update { it.copy(progressText = "Sync in progress...") }

        // Simple loop of 20 pages for demo
        repeat(20) {
            val pre = now()
            val pr = ble.readPage(device, s, offset)
            val lat = now() - pre
            if (!pr.ok) {
                appendLog("Page read failed at offset=$offset; retry after delay")
                delay(200)
                return@repeat
            }
            // Metrics
            pages++
            sumLat += lat
            sumPageSize += pr.pageSize
            bytesSum += pr.bytes
            delivered += pr.pageSize
            offset += pr.pageSize

            // Ack
            val ackOk = ble.ack(device, s, offset)
            if (!ackOk) appendLog("Ack failed at upTo=$offset")

            // Update UI metrics
            val ttfp = if (pages == 1L) now() - firstPageStart else _ui.value.metrics.ttfpMs
            val minElapsed = max(1L, (now() - startAll) / 60_000L).toDouble()
            val ppm = (pages / minElapsed)
            val bpm = (bytesSum / minElapsed)
            val avgPageLatency = if (pages > 0) sumLat / pages else 0
            val avgPageSize = if (pages > 0) sumPageSize.toDouble() / pages.toDouble() else 0.0

            _ui.update {
                it.copy(
                    metrics =
                        it.metrics.copy(
                            ttfpMs = ttfp,
                            pagesPerMin = ppm,
                            bytesPerMin = bpm,
                            avgPageLatencyMs = avgPageLatency,
                            pageSizeAvg = avgPageSize,
                            deliveredPages = delivered,
                            ackedUpTo = offset,
                        ),
                    progressText = "Sync: $pages pages; offset=$offset",
                )
            }
        }

        appendLog("Sync complete (pages=$pages, bytes=$bytesSum)")
    }

    @Suppress("Indentation")
    private fun aggregate(ev: TelemetryEvent) {
        when (ev.name) {
            "gatt_connected" -> _ui.update { it.copy(metrics = it.metrics.copy()) }
            "page_read" -> {} // already aggregated in runScenario
            "ack_sent" -> {}
            "bonded" -> {}
            "connect_failed" ->
                _ui.update {
                    it.copy(
                        metrics =
                            it.metrics.copy(
                                failuresBeforeBreaker = it.metrics.failuresBeforeBreaker + 1,
                            ),
                    )
                }
            "bond_failed" ->
                _ui.update {
                    it.copy(
                        metrics =
                            it.metrics.copy(
                                pairingRetries = it.metrics.pairingRetries + 1,
                            ),
                    )
                }
        }
        // keep right-side metrics up to date as needed
    }

    private fun appendLog(line: String) {
        logs += line
        if (logs.size > 1000) logs.removeFirst()
        _ui.update { it.copy(logLines = logs.toList()) }
    }

    private fun now(): Long = System.currentTimeMillis()
}
