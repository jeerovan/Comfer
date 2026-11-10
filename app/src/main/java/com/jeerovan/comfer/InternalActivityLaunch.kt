package com.jeerovan.comfer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Keep internal navigation in its existing task, including when Android rejects a launch. */
internal fun launchInternalActivity(context: Context, intent: Intent, options: Bundle? = null): Boolean {
    return try {
        if (options == null) context.startActivity(intent) else context.startActivity(intent, options)
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.ui_app_not_found, Toast.LENGTH_SHORT).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.ui_app_launch_restricted, Toast.LENGTH_LONG).show()
        false
    }
}
