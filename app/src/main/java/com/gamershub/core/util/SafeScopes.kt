package com.gamershub.core.util

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Crash-proof coroutine scopes for long-lived engine workers.
 *
 * Every engine loop runs inside one of these. The [CoroutineExceptionHandler]
 * guarantees that an exception in ANY worker is logged and dropped instead of
 * reaching the default uncaught-exception handler and killing the whole app
 * process — the "app keeps closing by itself" class of bugs.
 */
object SafeScopes {
    private const val TAG = "GamersHub"

    private fun handler(name: String) = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "[$name] worker error (process kept alive)", t)
    }

    fun io(name: String): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + handler(name))

    fun default(name: String): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler(name))

    fun main(name: String): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main + handler(name))
}
