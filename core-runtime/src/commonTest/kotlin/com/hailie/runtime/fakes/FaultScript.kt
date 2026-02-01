package com.hailie.runtime.fakes

/**
 * Simple fault script where you can specify how many times an operation should fail, then succeed.
 */
class FaultScript(
    private val failuresBeforeSuccess: Int = 0,
) {
    private var remaining = failuresBeforeSuccess

    fun shouldFail(): Boolean =
        if (remaining > 0) {
            remaining -= 1
            true
        } else {
            false
        }
}
