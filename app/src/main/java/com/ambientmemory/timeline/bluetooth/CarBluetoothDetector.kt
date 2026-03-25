package com.ambientmemory.timeline.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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
        if (hasBluetoothConnectPermission(context) == false) return null
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        val bonded = adapter.bondedDevices ?: return null
        val btManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return null

        val connected =
            getConnectedDevicesCompat(btManager, BluetoothProfile.A2DP) +
                getConnectedDevicesCompat(btManager, BluetoothProfile.HEADSET)

        val bondedAddrs = bonded.map { it.address.lowercase(Locale.getDefault()) }.toSet()
        return connected
            .map { it.address.lowercase(Locale.getDefault()) }
            .filter { it in bondedAddrs }
            .sorted()
            .firstOrNull()
    }

    /**
     * Checks whether the specific device address is currently connected (A2DP or Headset profile).
     */
    fun isDeviceConnected(context: Context, deviceAddress: String): Boolean {
        if (deviceAddress.isBlank()) return false
        if (hasBluetoothConnectPermission(context) == false) return false
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val bonded = adapter.bondedDevices ?: return false
        val bondedDevice =
            bonded.firstOrNull { it.address.equals(deviceAddress, ignoreCase = true) } ?: return false

        val btManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false

        val connectedAddrs =
            (getConnectedDevicesCompat(btManager, BluetoothProfile.A2DP) +
                getConnectedDevicesCompat(btManager, BluetoothProfile.HEADSET))
                .map { it.address }
                .toSet()

        return connectedAddrs.any { it.equals(bondedDevice.address, ignoreCase = true) }
    }

    /**
     * Compatibility wrapper: different Android framework stubs expose different signatures.
     * We use reflection to ask for `BluetoothManager.getConnectedDevices(int profile)`.
     */
    private fun getConnectedDevicesCompat(
        btManager: BluetoothManager,
        profile: Int,
    ): List<BluetoothDevice> {
        return runCatching {
            val method =
                btManager.javaClass.methods.firstOrNull {
                    it.name == "getConnectedDevices" && it.parameterTypes.size == 1
                } ?: return emptyList()
            val raw = method.invoke(btManager, profile)
            (raw as? List<*>)?.mapNotNull { it as? BluetoothDevice } ?: emptyList()
        }.getOrDefault(emptyList())
    }

}

