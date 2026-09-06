package com.jeerovan.comfer

import android.app.Activity
import android.os.Bundle

/**
 * Compatibility tombstone for subscription screens left in an activity task by v32 or older.
 *
 * The subscription feature was removed in v33. The old class name must remain loadable long
 * enough for Android and OEM launchers to discard any task records that still reference it.
 */
class SubscriptionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isTaskRoot) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }
}
