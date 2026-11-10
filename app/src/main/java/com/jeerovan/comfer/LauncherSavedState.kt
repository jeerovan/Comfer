package com.jeerovan.comfer

import android.os.Bundle

/**
 * Home has no application-owned android.app.Fragments. Its framework fragment
 * state belongs to AndroidX's lifecycle ReportFragment, which AndroidX injects
 * again on creation. Old releases obfuscated that class differently (v56: vj4),
 * so restoring it after an update can instantiate an unrelated class and crash.
 * Keep the rest of the saved UI/AndroidX state, and never modify the caller's bundle.
 */
internal fun launcherSavedState(state: Bundle?): Bundle? = state?.let {
    Bundle(it).apply { remove("android:fragments") }
}
