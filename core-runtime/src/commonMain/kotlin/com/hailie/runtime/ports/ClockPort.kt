package com.hailie.runtime.ports

import com.hailie.domain.TimestampMs

interface ClockPort {
    fun now(): TimestampMs

    fun schedule(
        at: TimestampMs,
        onFire: () -> Unit,
    ): TimerToken

    fun cancel(token: TimerToken)
}

interface TimerToken
