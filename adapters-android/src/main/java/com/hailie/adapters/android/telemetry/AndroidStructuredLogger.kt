package com.hailie.adapters.android.telemetry

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AndroidStructuredLogger(
    private val context: Context,
    private val sessionId: String,
    private val json: Json = Json { encodeDefaults = true },
) : StructuredLogger {
    private val tag = "BleSync"
    private val file = File(context.filesDir, "telemetry.log")
    private val maxBytes = 1_000_000L

    override fun info(fields: Map<String, Any?>) = write("INFO", fields)

    override fun warn(fields: Map<String, Any?>) = write("WARN", fields)

    override fun error(
        fields: Map<String, Any?>,
        throwable: Throwable?,
    ) = write("ERROR", fields + ("error_stack" to (throwable?.stackTraceToString())))

    override fun flush() { /* appendText writes directly; no-op */ }

    private fun write(
        level: String,
        fields: Map<String, Any?>,
    ) {
        val payload =
            mapOf(
                "level" to level,
                "ts" to System.currentTimeMillis(),
                "session_id" to sessionId,
            ) + fields
        val line = json.encodeToString(payload)

        val prio =
            when (level) {
                "INFO" -> Log.INFO
                "WARN" -> Log.WARN
                else -> Log.ERROR
            }
        Log.println(prio, tag, line)

        rollIfNeeded()
        file.appendText(line + "\n")
    }

    private fun rollIfNeeded() {
        if (file.length() > maxBytes) {
            val bak = File(file.parentFile, "telemetry.${System.currentTimeMillis()}.log")
            file.copyTo(bak, overwrite = true)
            file.writeText("")
        }
    }
}
