package com.jeerovan.comfer

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

class WidgetProviderRefreshTest {
    private fun provider(name: String) = AppWidgetProviderInfo().apply {
        provider = ComponentName(name, "$name.Widget")
    }

    @Test fun failedLabelKeepsProvidersAndStopsFurtherResourceLoads() {
        val providers = listOf(provider("example.one"), provider("example.two"))
        var calls = 0
        val result = readWidgetProviderInventory({ providers }) {
            calls++
            throw OutOfMemoryError("controlled loadLabel failure")
        }!!
        assertEquals(providers, result.providers)
        assertEquals(listOf("example.one", "example.two"), result.groups.map { it.appName })
        assertEquals("No repeated resource allocations after the first OOM", 1, calls)
        val recovered = readWidgetProviderInventory({ providers }) { "Healthy label" }!!
        assertEquals(listOf("Healthy label", "Healthy label"), recovered.groups.map { it.appName })
    }

    @Test fun unavailableInventoryDiffersFromConfirmedEmptyAndCancellationPropagates() {
        for (failure in listOf(OutOfMemoryError("query"), SecurityException("query"))) {
            assertNull(readWidgetProviderInventory({ throw failure }) { it })
        }
        assertTrue(readWidgetProviderInventory({ emptyList() }) { it }!!.providers.isEmpty())
        for (atQuery in listOf(true, false)) {
            try {
                readWidgetProviderInventory(
                    { if (atQuery) throw CancellationException("query") else listOf(provider("example.one")) },
                    { throw CancellationException("label") },
                )
                fail("Cancellation must propagate")
            } catch (_: CancellationException) { }
        }
    }

    @Test fun pickerPolicyDoesNotEraseSavedProviderInventoryAndMetadataFailureKeepsGroup() {
        val providers = listOf(provider("com.hihonor.gallery"), provider("example.safe"))
        val result = readWidgetProviderInventory({ providers }) { throw SecurityException("metadata") }!!
        assertEquals(providers, result.providers)
        assertEquals(listOf("example.safe"), result.groups.map { it.packageName })
        assertEquals("example.safe", result.groups.single().appName)
    }

    @Test fun labelAllocationFailureDoesNotEscapeProviderDiscovery() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        check(base.packageName.endsWith(".notificationtest"))
        assertTrue("Use real installed providers, not an empty fixture",
            AppWidgetManager.getInstance(base).installedProviders.any {
                !WidgetInflationGuard.isKnownUnsafe(it.provider.flattenToShortString())
            })
        var labelCalls = 0
        val context = object : ContextWrapper(base) {
            override fun getPackageManager(): PackageManager {
                labelCalls++
                throw OutOfMemoryError("controlled provider metadata allocation failure")
            }
        }
        // Reach the existing private production method without changing its baseline behavior.
        val method = Class.forName("com.jeerovan.comfer.MainActivityKt").getDeclaredMethod(
            "getGroupedWidgetProviders", Context::class.java, Continuation::class.java
        ).apply { isAccessible = true }
        val result = suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
            method.invoke(null, context, continuation)
        }
        assertNotNull("A label failure must not lose the available provider inventory", result)
        assertTrue("The actual label loading path must execute", labelCalls > 0)
    }
}
