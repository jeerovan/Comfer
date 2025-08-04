package com.jeerovan.comfer

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class RecentsAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RecentsAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This service does not need to handle events, only perform actions.
    }

    override fun onInterrupt() {
        // Handle interruptions to the service.
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
