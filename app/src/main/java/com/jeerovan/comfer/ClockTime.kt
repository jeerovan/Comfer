package com.jeerovan.comfer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

/** One IO lifetime owns registration and its matching cleanup, even after late cancellation. */
internal fun clockTimeUpdates(context: Context, now: () -> Long = System::currentTimeMillis) = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { trySend(now()) }
    }
    var registered = false
    try {
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        registered = true
        ensureActive()
        trySend(now())
        awaitCancellation()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: RuntimeException) {
        Log.w("ClockTime", "Clock receiver unavailable", error)
    } finally {
        channel.close()
        if (registered) {
            try { context.unregisterReceiver(receiver) }
            catch (error: RuntimeException) { Log.w("ClockTime", "Clock receiver cleanup failed", error) }
        }
    }
}.conflate().flowOn(Dispatchers.IO)

@Composable
internal fun rememberClockTime(): State<Long> {
    val context = LocalContext.current
    val time = remember(context) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(context) { clockTimeUpdates(context).collect { time.longValue = it } }
    return time
}
