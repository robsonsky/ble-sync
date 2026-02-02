package com.hailie.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hailie.demo.scenario.ScenarioPreset
import com.hailie.demo.ui.ScenarioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { scenarioLabApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun scenarioLabApp(vm: ScenarioViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Scenario Lab") }, actions = {
                    Row(Modifier.padding(end = 8.dp)) {
                        AssistChip(
                            onClick = { vm.selectPreset(ScenarioPreset.HappyPath) },
                            label = { Text("Happy Path") },
                        )
                        Spacer(
                            Modifier.width(8.dp),
                        )
                        AssistChip(
                            onClick = { vm.selectPreset(ScenarioPreset.PairingReliability) },
                            label = { Text("Pairing") },
                        )
                        Spacer(
                            Modifier.width(8.dp),
                        )
                        AssistChip(
                            onClick = { vm.selectPreset(ScenarioPreset.ConnectionStability) },
                            label = { Text("Stability") },
                        )
                        Spacer(
                            Modifier.width(8.dp),
                        )
                        AssistChip(
                            onClick = { vm.selectPreset(ScenarioPreset.SlowSyncPerformance) },
                            label = { Text("Slow Perf") },
                        )
                    }
                })
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Row {
                        Button(
                            onClick = { vm.start() },
                            enabled = !ui.running,
                        ) { Text("Start") }
                        Spacer(
                            Modifier.width(8.dp),
                        )
                        OutlinedButton(
                            onClick = { vm.stop() },
                            enabled = ui.running,
                        ) { Text("Stop") }
                        Spacer(
                            Modifier.width(8.dp),
                        )
                        OutlinedButton(
                            onClick = { vm.killAppNow() },
                        ) { Text("Kill App Now") }
                        Spacer(
                            Modifier.width(16.dp),
                        )
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text("Cold Start Resume")
                            Spacer(Modifier.width(6.dp))
                            Switch(checked = ui.coldStartResumeEnabled, onCheckedChange = vm::toggleColdStartResume)
                        }
                        Spacer(Modifier.width(16.dp))
                        OutlinedButton(
                            onClick = { vm.copyLogsToClipboard() },
                        ) { Text("Copy Logs") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { /* preview */ },
                            enabled = false,
                        ) { Text("Export Telemetry JSON") }
                    }
                }
            },
        ) { pv ->
            Row(Modifier.padding(pv).fillMaxSize()) {
                // Left: Fault Injection Panel (accordion)
                faultPanel(
                    modifier = Modifier.width(280.dp).fillMaxHeight().padding(8.dp),
                    presetName = ui.selectedPreset.name,
                )

                // Center: Live State/Timeline & Progress
                centerPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    progress = ui.progressText,
                )

                // Right: Metrics/Telemetry Cards
                metricsPanel(
                    modifier = Modifier.width(320.dp).fillMaxHeight().padding(8.dp),
                    m = ui.metrics,
                    telemetryJson = ui.telemetryJsonPreview,
                )
            }

            // Bottom: Log Viewer (collapsible)
            if (ui.logExpanded) {
                logPanel(Modifier.fillMaxWidth().height(220.dp), ui.logLines) { vm.toggleLogExpanded() }
            } else {
                smallLogToggler { vm.toggleLogExpanded() }
            }
        }
    }
}

@Composable
fun faultPanel(
    modifier: Modifier,
    presetName: String,
) {
    Column(modifier) {
        Text("Fault Injection ($presetName)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val panels = listOf("Bond", "Connect", "Read/Ack", "Perf", "Environment")
        panels.forEach { title ->
            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text("Configure via preset selection (top).", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun centerPanel(
    modifier: Modifier,
    progress: String,
) {
    Column(modifier) {
        Text("Live State & Timeline", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.padding(12.dp)) {
                Text("Progress: $progress")
                Spacer(Modifier.height(8.dp))
                val state = rememberScrollState()
                Column(Modifier.verticalScroll(state)) {
                    repeat(8) { Text("• Timeline lane $it (add your visualizations)") }
                }
            }
        }
    }
}

@Composable
fun metricsPanel(
    modifier: Modifier,
    m: com.hailie.demo.ui.Metrics,
    telemetryJson: String,
) {
    print(telemetryJson)
    Column(modifier) {
        Text("Metrics & Telemetry", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Pairing")
                Text("• Time to bond: ${m.pairingTimeMs} ms")
                Text("• Retries: ${m.pairingRetries}")
                Text("• Breaker open: ${m.breakerOpenMs} ms")
            }
        }
        Spacer(Modifier.height(8.dp))

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Connection")
                Text("• Disconnects/session: ${m.disconnects}")
                Text("• Reconnect latency avg: ${m.reconnectLatencyMsAvg} ms")
                Text("• Failures before breaker: ${m.failuresBeforeBreaker}")
            }
        }
        Spacer(Modifier.height(8.dp))

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Performance")
                Text("• Time to first page: ${m.ttfpMs} ms")
                Text("• Pages/min: ${"%.1f".format(m.pagesPerMin)}")
                Text("• Bytes/min: ${"%.1f".format(m.bytesPerMin)}")
                Text("• Avg page latency: ${m.avgPageLatencyMs} ms")
                Text("• Page size avg: ${"%.1f".format(m.pageSizeAvg)}")
            }
        }
        Spacer(Modifier.height(8.dp))

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Reliability")
                Text("• Delivered pages: ${m.deliveredPages}")
                Text("• Acked up to: ${m.ackedUpTo}")
                Text("• Duplicates: ${m.duplicates}")
            }
        }
    }
}

@Composable
fun logPanel(
    modifier: Modifier,
    lines: List<String>,
    onToggle: () -> Unit,
) {
    Column(modifier.padding(8.dp)) {
        Row {
            Text("Logs", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onToggle) { Text("Collapse") }
        }
        Spacer(Modifier.height(6.dp))
        ElevatedCard(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                items(lines.size) { idx ->
                    Text(lines[idx])
                }
            }
        }
    }
}

@Composable
fun smallLogToggler(onToggle: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        OutlinedButton(onClick = onToggle) { Text("Show Logs") }
    }
}
