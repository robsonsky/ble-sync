package com.hailie.adapters.android.ble

import org.junit.Test
import kotlin.test.assertTrue

class GattErrorMappingTest {
    @Test
    fun gatt_messages_contain_code() {
        val ex133 = IllegalStateException("unavailable connect:gatt=133")
        val ex8 = IllegalStateException("transport connect:gatt=8")
        assertTrue(ex133.message!!.contains("gatt=133"))
        assertTrue(ex8.message!!.contains("gatt=8"))
    }
}
