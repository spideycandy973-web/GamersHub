package com.gamershub.core

import com.gamershub.core.shizuku.Shell

/**
 * Private DNS, applied for real.
 *
 * Android keeps this in the global settings table: `private_dns_mode` plus
 * `private_dns_specifier` for hostname mode. Writing those through the shell identity
 * changes DNS for the whole phone (and is instantly reversible), instead of only
 * flipping a switch in our own preferences the way this card used to.
 */
object DnsEngine {

    /** Hostname-mode providers, in the same order as the card. Last one is "keep it automatic". */
    private val HOSTS = listOf("dns.google", "one.one.one.one", "doh.opendns.com")

    fun apply(index: Int): Boolean {
        if (!Shell.granted()) return false
        return if (index in HOSTS.indices) {
            Shell.ok("settings put global private_dns_mode hostname") &&
                Shell.ok("settings put global private_dns_specifier ${HOSTS[index]}")
        } else {
            // "Custom" falls back to the phone's own opportunistic resolver.
            Shell.ok("settings delete global private_dns_specifier") &&
                Shell.ok("settings put global private_dns_mode opportunistic")
        }
    }

    fun off(): Boolean {
        if (!Shell.granted()) return false
        Shell.ok("settings delete global private_dns_specifier")
        return Shell.ok("settings put global private_dns_mode off")
    }

    /** What the phone is actually using right now. */
    fun current(): String {
        val mode = Shell.exec("settings get global private_dns_mode")?.trim().orEmpty()
        val host = Shell.exec("settings get global private_dns_specifier")?.trim().orEmpty()
        return when {
            mode.isBlank() || mode == "null" -> ""
            mode == "hostname" -> if (host.isNotBlank() && host != "null") "private dns · $host" else "private dns · hostname"
            mode == "opportunistic" -> "automatic"
            else -> mode
        }
    }
}
