package com.hailie.runtime.fakes

import com.hailie.domain.TimestampMs
import com.hailie.runtime.ports.ClockPort
import com.hailie.runtime.ports.TimerToken

class FakeClockPort(startAtMs: Long = 0L) : ClockPort {
    private var nowMs: Long = startAtMs

    private data class Token(val id: Int) : TimerToken

    private var nextId: Int = 1

    private data class Timer(val token: Token, val at: Long, val cb: () -> Unit, var cancelled: Boolean = false)

    private val timers = mutableListOf<Timer>()

    override fun now(): TimestampMs = TimestampMs(nowMs)

    override fun schedule(
        at: TimestampMs,
        onFire: () -> Unit,
    ): TimerToken {
        val token = Token(nextId++)
        timers += Timer(token, at.value, onFire)
        return token
    }

    override fun cancel(token: TimerToken) {
        val t = token as Token
        timers.firstOrNull { it.token == t }?.let { it.cancelled = true }
    }

    fun advanceBy(delta: Long) = advanceTo(nowMs + delta)

    fun advanceTo(target: Long) {
        require(target >= nowMs) { "Cannot go back in time" }
        nowMs = target
        val due = timers.filter { !it.cancelled && it.at <= nowMs }.sortedBy { it.at }
        due.forEach { it.cb.invoke() }
        timers.removeAll { it.cancelled || it.at <= nowMs }
    }
}
