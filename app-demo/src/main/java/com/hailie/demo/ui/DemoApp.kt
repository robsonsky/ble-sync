package com.hailie.demo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hailie.runtimeactor.BleSyncActor
import org.koin.compose.koinInject

@Composable
fun demoApp() {
    // This is the symbol that needs the Koin Compose dependency AND the correct import
    val actor = koinInject<BleSyncActor>()

    MaterialTheme {
        Surface {
            Text("Koin ready · Actor = ${actor.hashCode()}")
        }
    }
}
