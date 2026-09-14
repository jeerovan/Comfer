package com.jeerovan.comfer

import com.jeerovan.comfer.ui.ThumbReachState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.*
import org.junit.Test

class ThumbReachTest {
    @Test fun startsAtTopAndPullIsCappedWithoutConsumingHorizontalMotion() {
        val state = ThumbReachState(240f)
        assertEquals(0f, state.offset, 0f)
        assertEquals(Offset(0f, 100f), state.onPostScroll(Offset.Zero, Offset(30f, 100f), NestedScrollSource.UserInput))
        assertEquals(Offset(0f, 140f), state.onPostScroll(Offset.Zero, Offset(0f, 500f), NestedScrollSource.UserInput))
        assertEquals(240f, state.offset, 0f)
        assertEquals(Offset.Zero, state.onPostScroll(Offset.Zero, Offset(0f, 10f), NestedScrollSource.UserInput))
    }
    @Test fun upwardScrollRemovesReachSpaceBeforeHandingRemainderToContent() {
        val state = ThumbReachState(240f)
        state.onPostScroll(Offset.Zero, Offset(0f, 180f), NestedScrollSource.UserInput)
        assertEquals(Offset(0f, -80f), state.onPreScroll(Offset(20f, -80f), NestedScrollSource.UserInput))
        assertEquals(Offset(0f, -100f), state.onPreScroll(Offset(0f, -200f), NestedScrollSource.UserInput))
        assertEquals(0f, state.offset, 0f)
        assertEquals(Offset.Zero, state.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput))
    }
    @Test fun ordinaryConsumedScrollAndProgrammaticMotionDoNotRevealPadding() {
        val state = ThumbReachState(240f)
        assertEquals(Offset.Zero, state.onPostScroll(Offset(0f, 100f), Offset.Zero, NestedScrollSource.UserInput))
        assertEquals(Offset.Zero, state.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.SideEffect))
        assertEquals(0f, state.offset, 0f)
        assertEquals(Offset.Zero, ThumbReachState(0f).onPostScroll(Offset.Zero, Offset(0f, 500f), NestedScrollSource.UserInput))
    }
}
