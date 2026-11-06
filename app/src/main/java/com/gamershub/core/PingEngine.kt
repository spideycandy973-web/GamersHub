package com.gamershub.core

import android.content.Context
import com.gamershub.core.shizuku.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** A real-network target the optimizer measures (TCP handshake = closest thing to game latency). */
data class PingTarget(val name: String, val host: String, val port: Int, val tag: String)

data class PingResult(
    val target: PingTarget,
    val latency: Int,
    val jitter: Int,
    val loss: Int,
    val samples: List<Int>
) {
    val grade: String
        get() = when {
            latency < 0 -> "N/A"
            loss > 20 -> "POOR"
            latency <= 40 && jitter <= 8 -> "EXCELLENT"
            latency <= 70 && jitter <= 16 -> "GOOD"
            latency <= 110 -> "FAIR"
            else -> "POOR"
        }

    val bars: Int
        get() = when {
            latency < 0 -> 0
            latency <= 40 -> 4
            latency <= 70 -> 3
            latency <= 110 -> 2
            else -> 1
        }
}

/**
 * Ping / network optimizer engine.
 *
 * Everything here is measured, never simulated: TCP connect timing to real
 * game endpoints, jitter from sample deltas, packet loss from failed attempts.
 * The optimizer applies real private-DNS + radio tweaks through Shizuku.
 */
object PingEngine {

    val targets = listOf(
        PingTarget("Google DNS", "8.8.8.8", 53, "GLOBAL"),
        PingTarget("Cloudflare", "1.1.1.1", 53, "GLOBAL"),
        PingTarget("Quad9", "9.9.9.9", 53, "GLOBAL"),
        PingTarget("Steam", "store.steampowered.com", 443, "GAME CDN"),
        PingTarget("Epic Games", "www.epicgames.com", 443, "GAME CDN"),
        PingTarget("Riot Games", "www.riotgames.com", 443, "GAME CDN"),
        PingTarget("Mobile Legends", "m.mobilelegends.com", 443, "MOBA"),
        PingTarget("PUBG Mobile", "www.pubgmobile.com", 443, "BATTLE ROYALE")
    )

    private fun tcpLatency(host: String, port: Int, timeoutMs: Int = 1200): Int = try {
        val started = System.nanoTime()
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        val ms = ((System.nanoTime() - started) / 1_000_000L).toInt()
        runCatching { socket.close() }
        ms.coerceAtLeast(0)
    } catch (_: Throwable) {
        -1
    }

    /** Single TCP latency sample in ms, or -1. */
    fun probe(host: String, port: Int = 53): Int = tcpLatency(host, port)

    suspend fun measure(target: PingTarget, attempts: Int = 4): PingResult =
        withContext(Dispatchers.IO) {
            val samples = ArrayList<Int>(attempts)
            var fails = 0
            repeat(attempts) {
                val ms = tcpLatency(target.host, target.port)
                if (ms >= 0) samples.add(ms) else fails++
            }
            val avg = if (samples.isEmpty()) -1 else samples.sum() / samples.size
            val jitter = if (samples.size < 2) {
                0
            } else {
                val diffs = samples.zipWithNext { a, b -> kotlin.math.abs(a - b) }
                diffs.sum() / diffs.size
            }
            PingResult(
                target = target,
                latency = avg,
                jitter = jitter,
                loss = (fails * 100) / attempts.coerceAtLeast(1),
                samples = samples
            )
        }

    suspend fun measureAll(): List<PingResult> = withContext(Dispatchers.IO) {
        targets.map { target -> async { measure(target) } }.awaitAll()
    }

    suspend fun best(): PingResult? =
        measureAll().filter { it.latency >= 0 }.minByOrNull { it.latency }

    /** DNS resolution time through the system resolver (real, includes cache misses). */
    suspend fun dnsLatency(host: String = "dns.google"): Int = withContext(Dispatchers.IO) {
        try {
            val started = System.nanoTime()
            InetAddress.getAllByName(host)
            ((System.nanoTime() - started) / 1_000_000L).toInt()
        } catch (_: Throwable) {
            -1
        }
    }

    data class OptimizeReport(
        val beforeMs: Int,
        val afterMs: Int,
        val dnsHost: String,
        val applied: Boolean
    ) {
        val gained: Int get() = if (beforeMs > 0 && afterMs > 0) beforeMs - afterMs else 0
    }

    val dnsProviders = listOf(
        "dns.google" to "Google",
        "1dot1dot1dot1.cloudflare-dns.com" to "Cloudflare",
        "dns.quad9.net" to "Quad9"
    )

    fun applyPrivateDns(host: String): Boolean = try {
        Shell.ok("settings put global private_dns_mode hostname") &&
            Shell.ok("settings put global private_dns_specifier $host")
    } catch (_: Throwable) {
        false
    }

    fun resetPrivateDns(): Boolean = try {
        Shell.ok("settings put global private_dns_mode off")
    } catch (_: Throwable) {
        false
    }

    /** Turn off background radio scanning that adds Wi-Fi jitter during a match. */
    fun applyRadioTweaks(): Boolean = try {
        var ok = false
        ok = Shell.ok("settings put global wifi_scan_always_enabled 0") || ok
        ok = Shell.ok("settings put global wifi_wakeup_enabled 0") || ok
        ok = Shell.ok("settings put global mobile_data_always_on 0") || ok
        ok
    } catch (_: Throwable) {
        false
    }

    fun restoreRadioTweaks(): Boolean = try {
        var ok = false
        ok = Shell.ok("settings put global wifi_scan_always_enabled 1") || ok
        ok = Shell.ok("settings put global mobile_data_always_on 1") || ok
        ok
    } catch (_: Throwable) {
        false
    }

    suspend fun optimize(context: Context, dnsHost: String = "1dot1dot1dot1.cloudflare-dns.com"): OptimizeReport =
        withContext(Dispatchers.IO) {
            val before = probe("1.1.1.1", 53).let { if (it < 0) dnsLatency() else it }
            val applied = applyPrivateDns(dnsHost) && applyRadioTweaks()
            // Give the resolver a moment to bind the new DoT endpoint.
            Thread.sleep(600)
            val after = probe("1.1.1.1", 53).let { if (it < 0) dnsLatency() else it }
            OptimizeReport(beforeMs = before, afterMs = after, dnsHost = dnsHost, applied = applied)
        }

    fun currentPrivateDns(): String = try {
        val out = Shell.exec("settings get global private_dns_mode") ?: ""
        out.lines().firstOrNull { it.trim().isNotEmpty() }?.trim() ?: "off"
    } catch (_: Throwable) {
        "off"
    }
}
