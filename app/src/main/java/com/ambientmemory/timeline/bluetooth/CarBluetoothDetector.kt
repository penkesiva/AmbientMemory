package com.ambientmemory.timeline.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.core.content.ContextCompat
import java.util.Locale

object CarBluetoothDetector {
    fun hasBluetoothConnectPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Returns the address of any currently connected bonded BT device that matches common car audio
     * profiles (A2DP/Headset). Used for quick "set device from current connection".
     */
    fun getAnyConnectedDeviceAddress(context: Context): String? {
        if (!hasBluetoothConnectPermission(context)) return null
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        val bonded = adapter.bondedDevices ?: return null

        val candidates = bonded.mapNotNull { d -> d to isDeviceConnected(adapter, d) }.filter { it.second }
        // Deterministic choice: sort by address
        return candidates.map { it.first.address }.sorted().firstOrNull()
    }

    /**
     * Checks whether the specific device address is currently connected (A2DP or Headset profile).
     */
    fun isDeviceConnected(context: Context, deviceAddress: String): Boolean {
        if (deviceAddress.isBlank()) return false
        if (!hasBluetoothConnectPermission(context)) return false
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val bonded = adapter.bondedDevices ?: return false
        val device = bonded.firstOrNull { it.address.equals(deviceAddress, ignoreCase = true) } ?: return false
        return isDeviceConnected(adapter, device)
    }

    private fun isDeviceConnected(adapter: BluetoothAdapter, device: BluetoothDevice): Boolean {
        val a2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP, device)
        if (a2dp == BluetoothProfile.STATE_CONNECTED) return true
        val headset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET, device)
        return headset == BluetoothProfile.STATE_CONNECTED
    }

    private fun hasBluetoothConnectPermission(context: Context): Boolean =
        hasBluetoothConnectPermission(context)
}

