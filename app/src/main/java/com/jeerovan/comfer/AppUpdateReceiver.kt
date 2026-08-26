package com.jeerovan.comfer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val activityIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(activityIntent)
                } catch (e: RuntimeException) {
                    // Background activity starts may be rejected by the system or
                    // by OEM launch policies after an in-place app update.
                    Log.w("AppUpdateReceiver", "Could not reopen launcher after update", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
