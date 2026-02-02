package com.hailie.adapters.android.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import com.hailie.domain.DomainError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal class ConnectionSession(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private val stateLock = Mutex()
    private var gatt: BluetoothGatt? = null
    private val callback = RoutingCallback()

    private class Pending<T>(val uuid: UUID, val cont: CompletableDeferred<T>)

    private val pendingServiceDiscovery = CompletableDeferred<Unit>()
    private var pendingRead: Pending<ByteArray>? = null
    private var pendingWrite: Pending<Unit>? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Suppress("UseCheckOrError")
    suspend fun connect(): BluetoothGatt {
        return stateLock.withLock {
            if (gatt == null) {
                if (!BluetoothPermission.hasConnectPermission(context)) {
                    pendingServiceDiscovery
                        .completeExceptionally(IllegalStateException("missing_bluetooth_connect_permission"))
                    throw IllegalStateException("missing_bluetooth_connect_permission")
                }
                val created =
                    if (Build.VERSION.SDK_INT >= 23) {
                        device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                    } else {
                        @Suppress("DEPRECATION")
                        device.connectGatt(context, false, callback)
                    }
                gatt = created
                pendingServiceDiscovery.await()
            }
            requireNotNull(gatt) { "GATT not available after connect" }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Suppress("UseCheckOrError")
    suspend fun read(chrUuid: UUID): ByteArray {
        if (!BluetoothPermission.hasConnectPermission(context)) {
            throw IllegalStateException("missing_bluetooth_connect_permission")
        }
        val g = requireNotNull(gatt) { "Not connected" }
        val chr = findCharacteristicOrError(g, chrUuid)
        val p = Pending(chrUuid, CompletableDeferred<ByteArray>())
        stateLock.withLock {
            pendingRead = p
            check(g.readCharacteristic(chr)) { "readCharacteristic=false" }
        }
        return p.cont.await()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Suppress("UseCheckOrError")
    suspend fun write(
        chrUuid: UUID,
        value: ByteArray,
    ) {
        if (!BluetoothPermission.hasConnectPermission(context)) {
            throw IllegalStateException("missing_bluetooth_connect_permission")
        }
        val g = requireNotNull(gatt) { "Not connected" }
        val chr = findCharacteristicOrError(g, chrUuid)
        chr.value = value
        val p = Pending(chrUuid, CompletableDeferred<Unit>())
        stateLock.withLock {
            pendingWrite = p
            check(g.writeCharacteristic(chr)) { "writeCharacteristic=false" }
        }
        p.cont.await()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun close() {
        try {
            gatt?.close()
        } catch (_: Throwable) {
        }
        gatt = null
    }

    private fun findCharacteristicOrError(
        gatt: BluetoothGatt,
        uuid: UUID,
    ): BluetoothGattCharacteristic {
        val chr =
            gatt
                .services.asSequence().flatMap { it.characteristics.asSequence() }.firstOrNull { it.uuid == uuid }
        return chr ?: error("Characteristic not found: $uuid")
    }

    private inner class RoutingCallback : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(
            g: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                val started = g.discoverServices()
                if (!started) {
                    pendingServiceDiscovery
                        .completeExceptionally(IllegalStateException("discoverServices=false"))
                }
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val ex = IllegalStateException("disconnected (gatt=$status)")
                if (!pendingServiceDiscovery.isCompleted) {
                    pendingServiceDiscovery
                        .completeExceptionally(ex)
                }
                pendingRead?.cont?.completeExceptionally(ex)
                pendingWrite?.cont?.completeExceptionally(ex)
                close()
                return
            }
            val ex = IllegalStateException(domainMessage(mapGatt(status, "connect")))
            if (!pendingServiceDiscovery.isCompleted) {
                pendingServiceDiscovery
                    .completeExceptionally(ex)
            }
            pendingRead?.cont?.completeExceptionally(ex)
            pendingWrite?.cont?.completeExceptionally(ex)
            close()
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pendingServiceDiscovery.complete(Unit)
            } else {
                pendingServiceDiscovery.completeExceptionally(
                    IllegalStateException(domainMessage(mapGatt(status, "services"))),
                )
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val p = pendingRead ?: return
            if (p.uuid != characteristic.uuid) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pendingRead = null
                p.cont.complete(characteristic.value ?: ByteArray(0))
            } else {
                pendingRead = null
                p.cont.completeExceptionally(IllegalStateException(domainMessage(mapGatt(status, "char_read"))))
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val p = pendingWrite ?: return
            if (p.uuid != characteristic.uuid) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pendingWrite = null
                p.cont.complete(Unit)
            } else {
                pendingWrite = null
                p.cont.completeExceptionally(IllegalStateException(domainMessage(mapGatt(status, "char_write"))))
            }
        }
    }

    private fun mapGatt(
        code: Int,
        op: String,
    ): DomainError =
        when (code) {
            8, 19 -> DomainError.Transport("$op:gatt=$code")
            133 -> DomainError.Unexpected("$op:gatt=$code")
            else -> DomainError.Protocol("$op:gatt=$code")
        }

    private fun domainMessage(err: DomainError): String =
        when (err) {
            is DomainError.Transport -> "transport ${err.message}"
            is DomainError.Unexpected -> "unavailable ${err.message}"
            is DomainError.Protocol -> "protocol ${err.message}"
            else -> err.toString()
        }
}
