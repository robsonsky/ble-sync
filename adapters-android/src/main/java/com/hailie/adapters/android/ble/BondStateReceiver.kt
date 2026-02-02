package com.hailie.adapters.android.ble

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

internal class BondStateReceiver(
    private val targetAddress: String,
    private val callback: (ok: Boolean, ex: Exception?) -> Unit,
) : BroadcastReceiver() {
    fun register(context: Context) {
        context.registerReceiver(this, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (_: Throwable) {
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val dev: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        if (dev?.address?.equals(targetAddress, ignoreCase = true) != true) return
        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
            BluetoothDevice.BOND_BONDED -> callback(true, null)
            BluetoothDevice.BOND_NONE -> callback(false, null)
        }
    }
}
