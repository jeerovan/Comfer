package com.jeerovan.comfer.compat

import android.annotation.SuppressLint
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test

@SuppressLint("NewApi")
class FrameworkCompatibilityTest {
    @SdkSuppress(minSdkVersion = 30) // WindowInsets.Type does not exist before API 30.
    @Test fun androidXApi34OverlayImplementationIsActuallyGuarded() {
        val method = Class.forName("androidx.core.view.WindowInsetsCompat\$TypeImpl34")
            .getDeclaredMethod("toPlatformType", Int::class.javaPrimitiveType).apply { isAccessible = true }
        val status = androidx.core.view.WindowInsetsCompat.Type.statusBars()
        val overlays = androidx.core.view.WindowInsetsCompat.Type.systemOverlays()
        assertEquals(WindowInsets.Type.statusBars() or FrameworkCompatibility.systemOverlays(),
            method.invoke(null, status or overlays))
    }

    @Test fun androidXApi34AccessibilityImplementationIsActuallyGuarded() {
        val method = Class.forName("androidx.core.view.accessibility.AccessibilityEventCompat\$Api34Impl")
            .getDeclaredMethod("setAccessibilityDataSensitive", AccessibilityEvent::class.java,
                Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val event = AccessibilityEvent.obtain()
        try { method.invoke(null, event, true); method.invoke(null, event, false) }
        finally { event.recycle() }
    }

    @Test fun androidXApi34NodeImplementationIsActuallyGuardedAndPreservesMetadata() {
        val method = Class.forName("androidx.core.view.accessibility.AccessibilityNodeInfoCompat\$Api34Impl")
            .getDeclaredMethod("setAccessibilityDataSensitive", AccessibilityNodeInfo::class.java,
                Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        for (text in listOf("Example", "مثال")) {
            val node = AccessibilityNodeInfo.obtain()
            try {
                node.text = text
                node.isPassword = true
                node.setBoundsInScreen(Rect(10, 20, 110, 70))
                node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                for (sensitive in listOf(true, false, true)) {
                    method.invoke(null, node, sensitive)
                    try { assertEquals(sensitive, node.isAccessibilityDataSensitive) }
                    catch (_: NoSuchMethodError) { /* Actual pre-34 or incomplete framework. */ }
                    assertEquals(text, node.text.toString())
                    assertTrue(node.isPassword)
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    assertEquals(Rect(10, 20, 110, 70), bounds)
                    assertTrue(node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK))
                }
            } finally { node.recycle() }
        }
    }

    @SdkSuppress(minSdkVersion = 30)
    @Test fun overlayTypeMatchesPlatformOrFallsBackWhenTheMethodIsMissing() {
        val expected = try { WindowInsets.Type.systemOverlays() } catch (_: NoSuchMethodError) { 0 }
        assertEquals(expected, FrameworkCompatibility.systemOverlays())
        assertEquals(expected, FrameworkCompatibility.systemOverlays())
    }

    @Test fun sensitivityPreservesEventAndUsesThePlatformWhenAvailable() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        try {
            event.contentDescription = "Example"
            event.isPassword = true
            for (sensitive in listOf(true, false)) {
                FrameworkCompatibility.setAccessibilityDataSensitive(event, sensitive)
                try { assertEquals(sensitive, event.isAccessibilityDataSensitive) }
                catch (_: NoSuchMethodError) { /* The missing-method runtime under test. */ }
                assertEquals("Example", event.contentDescription.toString())
                assertTrue(event.isPassword)
                assertEquals(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, event.eventType)
            }
        } finally { event.recycle() }
    }

    @Test fun unrelatedErrorsAreNotSwallowed() {
        try {
            FrameworkCompatibility.setAccessibilityDataSensitive(null as AccessibilityEvent?, true)
            // On a framework missing the method, linkage can fail before the null check.
            try { AccessibilityEvent::class.java.getMethod("setAccessibilityDataSensitive", Boolean::class.javaPrimitiveType) }
            catch (_: NoSuchMethodException) { return }
            fail("Null event must fail when the platform method exists")
        } catch (_: NullPointerException) { }
    }
}
