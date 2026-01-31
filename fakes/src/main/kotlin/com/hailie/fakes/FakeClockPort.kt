package com.hailie.fakes

import com.hailie.ports.ClockPort

class FakeClockPort : ClockPort {
    override fun nowMs(): Long = System.currentTimeMillis()
}
