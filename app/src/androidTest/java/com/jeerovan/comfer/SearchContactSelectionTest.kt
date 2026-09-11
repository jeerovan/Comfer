package com.jeerovan.comfer

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class SearchContactSelectionTest {
    @get:Rule val compose = createComposeRule()
    private var settings: SettingsViewModel? = null

    @After fun cleanUp() { settings?.viewModelScope?.cancel() }

    @Test fun portraitDoubleTapUsesDraggedSelectionAndUpdatedContact() =
        checkSelection(Configuration.ORIENTATION_PORTRAIT)

    @Test fun landscapeDoubleTapUsesDraggedSelectionAndUpdatedContact() =
        checkSelection(Configuration.ORIENTATION_LANDSCAPE)

    private fun checkSelection(orientation: Int) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val dialed = CopyOnWriteArrayList<String>()
        // Observe the real dialer intent without opening an external app or placing a call.
        val context = object : ContextWrapper(target) {
            override fun startActivity(intent: Intent) {
                check(intent.action == Intent.ACTION_DIAL)
                dialed += requireNotNull(intent.data).schemeSpecificPart
            }
        }
        val contacts = mutableStateOf(listOf(
            Contact(1, "First contact", null, "5550101"),
            Contact(2, "Second contact", null, "5550102"),
        ))
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { this.orientation = orientation }
            val model = androidx.compose.runtime.remember {
                SettingsViewModel(target.applicationContext as Application).also { settings = it }
            }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) {
                MaterialTheme {
                    SearchListOverlay(emptyList(), emptyList(), contacts.value, {}, {}, model, true)
                }
            }
        }
        compose.onNodeWithText(target.getString(R.string.contacts)).performClick()
        compose.waitForIdle()
        val list = compose.onNode(hasScrollAction())
        fun drag(up: Boolean) {
            val bounds = list.fetchSemanticsNode().boundsInRoot
            // The list consumes drags itself. Its horizontal padding belongs to
            // the overlay and exercises detectVerticalDragGestures directly.
            compose.onRoot().performTouchInput {
                val low = Offset(bounds.left - 2f, bounds.bottom - 10f)
                val high = Offset(bounds.left - 2f, bounds.top + 10f)
                swipe(if (up) low else high, if (up) high else low, 600)
            }
            compose.waitForIdle()
        }
        fun doubleTap(expectedNumber: String) {
            val count = dialed.size
            val bounds = list.fetchSemanticsNode().boundsInRoot
            compose.onRoot().performTouchInput { doubleClick(Offset(bounds.left - 2f, bounds.center.y)) }
            compose.waitUntil(5_000) { dialed.size > count }
            assertEquals(count + 1, dialed.size)
            assertEquals(expectedNumber, dialed.last())
        }
        drag(up = true)
        doubleTap("5550102")
        compose.runOnIdle {
            contacts.value = contacts.value.map { if (it.id == 2L) it.copy(number = "5550199") else it }
        }
        compose.waitForIdle()
        doubleTap("5550199")
        drag(up = false)
        doubleTap("5550101")
    }
}
