package com.hailie.adapters.android.telemetry

interface StructuredLogger {
    fun info(fields: Map<String, Any?>)

    fun warn(fields: Map<String, Any?>)

    fun error(
        fields: Map<String, Any?>,
        throwable: Throwable? = null,
    )

    fun flush()
}
