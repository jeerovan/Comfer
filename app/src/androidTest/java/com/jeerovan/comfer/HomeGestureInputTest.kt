package com.jeerovan.comfer

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HomeGestureInputTest {
    @get:Rule val compose = createComposeRule()
    private val actions = mutableListOf<String>()
    private var scale = 1f
    private var childScroll: androidx.compose.foundation.ScrollState? = null
    private fun show(withScroll: Boolean = false) {
        compose.setContent {
            scale = LocalDensity.current.density
            Box(Modifier.fillMaxSize().testTag("home")
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { actions += "double" }, onLongPress = { actions += "long" }) }
                .detectGestures(onSwipeUp = { actions += "up" }, onSwipeDown = { actions += "down" },
                    onSwipeLeft = { actions += "left" }, onSwipeRight = { actions += "right" },
                    onCircular = { actions += "circle" }, onLPatternDetected = { actions += it }, onInbox = { actions += "inbox" })) {
                Box(Modifier.size(64.dp).testTag("child").clickable { actions += "child" })
                if (withScroll) {
                    val scroll = rememberScrollState(500)
                    childScroll = scroll
                    Column(Modifier.align(Alignment.TopEnd).size(180.dp).testTag("scroll-child").verticalScroll(scroll)) {
                        Spacer(Modifier.height(800.dp))
                    }
                }
            }
        }
    }
    @Test fun returnDispatchesOnlyInboxAndOnlyAfterRelease() {
        show()
        val home = compose.onNodeWithTag("home")
        home.performTouchInput { down(center); moveBy(Offset(0f, 120f * scale), 180); moveBy(Offset(0f, -120f * scale), 180) }
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        home.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf("inbox"), actions) }
    }
    @Test fun ordinaryVerticalAndHorizontalSwipesRemainExclusive() {
        show()
        val home = compose.onNodeWithTag("home")
        for (delta in listOf(Offset(0f, 120f), Offset(0f, -120f), Offset(-120f, 0f), Offset(120f, 0f))) {
            home.performTouchInput { swipe(center, center + delta * scale, 300) }
        }
        compose.runOnIdle { assertEquals(listOf("down", "up", "left", "right"), actions) }
    }
    @Test fun incompleteReturnAndCancellationDoNotLeakIntoNextStroke() {
        show()
        val home = compose.onNodeWithTag("home")
        home.performTouchInput { down(center); moveBy(Offset(0f, 160f * scale), 180); moveBy(Offset(0f, -60f * scale), 120); up() }
        home.performTouchInput { down(center); moveBy(Offset(0f, 120f * scale), 180); cancel() }
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        home.performTouchInput { swipe(center, center + Offset(0f, -120f * scale), 300) }
        compose.runOnIdle { assertEquals(listOf("up"), actions) }
    }
    @Test fun secondFingerCancelsBeforeAndAfterDragStarts() {
        show()
        val home = compose.onNodeWithTag("home")
        for (started in listOf(false, true)) home.performTouchInput {
            down(0, center)
            if (started) moveBy(0, Offset(0f, 120f * scale), 150)
            down(1, center + Offset(30f * scale, 0f))
            moveBy(0, Offset(0f, if (started) -120f * scale else 120f * scale), 150)
            up(0); up(1)
        }
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }
    @Test fun childTapDoubleTapAndLongPressKeepTheirOwners() {
        show()
        compose.onNodeWithTag("child").performTouchInput { click() }
        compose.onNodeWithTag("home").performTouchInput { doubleClick(center) }
        compose.onNodeWithTag("home").performTouchInput { longClick(center) }
        compose.runOnIdle { assertEquals(listOf("child", "double", "long"), actions) }
    }
    @Test fun swipeStartingOnClickableChildReachesHomeWithoutClicking() {
        show()
        compose.onNodeWithTag("child").performTouchInput {
            swipe(center, center + Offset(0f, -120f * scale), 300)
        }
        compose.runOnIdle { assertEquals(listOf("up"), actions) }
    }

    @Test fun circularSearchPassesSwipesToSharedHomeParent() = verifyLayoutSwipes(true)
    @Test fun fiveColumnSearchPassesSwipesToSharedHomeParent() = verifyLayoutSwipes(false)

    private fun verifyLayoutSwipes(circular: Boolean) {
        val apps = listOf(AppInfo(
            null, android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE),
            "Test folder", 1f, "folder_swipe_test", null, null, null,
        ))
        compose.setContent {
            scale = LocalDensity.current.density
            Box(Modifier.fillMaxSize().testTag("home")
                .pointerInput(Unit) { detectTapGestures(onLongPress = { actions += "long" }) }
                .detectGestures(onSwipeUp = { actions += "up" }, onSwipeDown = { actions += "down" },
                    onInbox = { actions += "inbox" }), contentAlignment = Alignment.Center) {
                if (circular) CircularLayout(apps, emptyList(), 48.dp,
                    androidx.compose.foundation.shape.CircleShape, { actions += "search" },
                    false, null, false, onTappingFolder = { actions += "folder" }, showGestureGuide = false)
                else FiveColumnLayout(apps, emptyList(), 48.dp,
                    androidx.compose.foundation.shape.CircleShape, { actions += "search" },
                    false, null, false, onTappingFolder = { actions += "folder" })
            }
        }
        val search = compose.onNodeWithTag("home-search-button")
        for (delta in listOf(-120f, 120f)) search.performTouchInput {
            swipe(center, center + Offset(0f, delta * scale), 300)
        }
        search.performTouchInput {
            down(center)
            moveBy(Offset(0f, 120f * scale), 180)
            moveBy(Offset(0f, -120f * scale), 180)
            up()
        }
        search.performTouchInput { click() }
        val icon = compose.onNodeWithContentDescription("Test folder", useUnmergedTree = true)
        icon.performTouchInput { swipe(center, center + Offset(0f, -120f * scale), 300) }
        icon.performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("up", "down", "inbox", "search", "up", "folder"), actions) }
    }
    @Test fun allCornerStrokesAndBothCirclesKeepTheirConfiguredCallbacks() {
        show()
        val home = compose.onNodeWithTag("home")
        val cases = listOf(
            Triple(Offset(0f, 100f), Offset(100f, 100f), "TopRight"),
            Triple(Offset(0f, 100f), Offset(-100f, 100f), "TopLeft"),
            Triple(Offset(0f, -100f), Offset(100f, -100f), "BottomRight"),
            Triple(Offset(0f, -100f), Offset(-100f, -100f), "BottomLeft"),
            Triple(Offset(100f, 0f), Offset(100f, 100f), "BottomLeft"),
            Triple(Offset(100f, 0f), Offset(100f, -100f), "TopLeft"),
            Triple(Offset(-100f, 0f), Offset(-100f, 100f), "BottomRight"),
            Triple(Offset(-100f, 0f), Offset(-100f, -100f), "TopRight"))
        for ((corner, end, _) in cases) home.performTouchInput {
            val origin = center
            down(origin)
            for (i in 1..20) moveTo(origin + corner * scale * (i / 20f), 12)
            for (i in 1..20) moveTo(origin + (corner + (end - corner) * (i / 20f)) * scale, 12)
            up()
        }
        for (direction in listOf(-1, 1)) home.performTouchInput {
            val origin = center
            down(origin + Offset(80f * scale, 0f))
            for (i in 1..60) {
                val angle = direction * 2 * kotlin.math.PI * i / 60
                moveTo(origin + Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat()) * (80f * scale), 12)
            }
            up()
        }
        compose.runOnIdle { assertEquals(cases.map { it.third } + listOf("circle", "circle"), actions) }
    }
    @Test fun scrollableChildOwnsItsDownAndReturnStroke() {
        show(withScroll = true)
        val child = compose.onNodeWithTag("scroll-child")
        var before = 0
        compose.runOnIdle { before = childScroll!!.value }
        child.performTouchInput { down(center); moveBy(Offset(0f, 100f * scale), 200) }
        compose.runOnIdle { assertTrue(childScroll!!.value < before); assertTrue(actions.isEmpty()) }
        child.performTouchInput { moveBy(Offset(0f, -100f * scale), 200); up() }
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }
}
