package com.hailie.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Text
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
@Suppress("LongMethod", "Indentation")
fun scenarioLabApp(vm: ScenarioViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val scroll = rememberScrollState()

    MaterialTheme {
        Scaffold { pv ->

            Column(
                Modifier
                    .padding(pv)
                    .fillMaxSize()
                    .verticalScroll(scroll),
            ) {
                Spacer(
                    Modifier.width(8.dp),
                )
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
                }
                Row(Modifier.padding(end = 8.dp)) {
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
                Row(Modifier.padding(end = 8.dp)) {
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
                }

                // Left: Fault Injection Panel (accordion)
                faultPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    presetName = ui.selectedPreset.name,
                )

                // Center: Live State/Timeline & Progress
                centerPanel(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(8.dp),
                    progress = ui.progressText,
                )

                // Right: Metrics/Telemetry Cards
                metricsPanel(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(600.dp)
                            .padding(8.dp),
                    m = ui.metrics,
                    telemetryJson = ui.telemetryJsonPreview,
                )

                logPanel(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(500.dp)
                            .padding(8.dp),
                    ui.logLines,
                )
            }
        }
    }
}

@Composable
@Suppress("Indentation")
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
@Suppress("Indentation")
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
            }
        }
    }
}

@Composable
@Suppress("Indentation")
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
@Suppress("Indentation")
fun logPanel(
    modifier: Modifier,
    lines: List<String>,
) {
    Column(modifier.padding(8.dp)) {
        Text("Logs", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
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
