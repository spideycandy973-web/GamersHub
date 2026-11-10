package com.gamershub.core

import com.gamershub.core.shizuku.Shell

/**
 * Reads the touch panel's own sampling rate.
 *
 * There is no public API for this, and no way to raise it on a stock phone, so the
 * old "boost" toggle could only ever lie. This reports what the device actually
 * advertises, and says so plainly when the vendor hides it.
 */
object TouchProbe {

    fun summary(): String {
        if (!Shell.granted()) return "Needs Shizuku to read the touch panel"
        val dump = Shell.exec("dumpsys input") ?: return "Not exposed by this phone"
        val lines = dump.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { line ->
                line.contains("sampling", ignoreCase = true) ||
                    line.contains("touch", ignoreCase = true) && line.contains("hz", ignoreCase = true)
            }
            .distinct()
            .take(2)
            .toList()
        return if (lines.isEmpty()) {
            "Not exposed by this phone — the panel reports nothing"
        } else {
            lines.joinToString(" · ").take(90)
        }
    }
}
