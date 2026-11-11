package com.gamershub.core

import android.content.Context
import android.net.wifi.WifiManager
import android.hardware.usb.UsbManager

/**
 * Small real facts about the phone that the pairing screens show.
 *
 * Nothing here is guessed: the address comes from the Wi-Fi stack and the USB list
 * from the system's own USB manager.
 */
object ScreenInfo {

    /** This phone's Wi-Fi address, or "no Wi-Fi" when the radio is off. */
    fun wifiIp(): String = try {
        val ctx = com.gamershub.GamersHubApp.instance ?: return "no Wi-Fi"
        val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wifi.connectionInfo?.ipAddress ?: 0
        if (ip == 0) "no Wi-Fi" else {
            listOf(ip and 0xFF, ip shr 8 and 0xFF, ip shr 16 and 0xFF, ip shr 24 and 0xFF)
                .joinToString(".")
        }
    } catch (_: Throwable) {
        "no Wi-Fi"
    }

    /** Everything currently attached to the USB port, by product name. */
    fun usbDevices(context: Context): List<String> = try {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        manager.deviceList.values.map { device ->
            val name = runCatching {
                device.productName ?: device.manufacturerName ?: "USB device"
            }.getOrNull() ?: "USB device"
            "$name · ${device.deviceClass}"
        }
    } catch (_: Throwable) {
        emptyList()
    }
}
