// app-demo/src/main/java/com/hailie/demo/MainActivity.kt
package com.hailie.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hailie.demo.ui.demoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { demoApp() }
    }
}
