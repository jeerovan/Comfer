package com.jeerovan.comfer

import org.junit.Assert.*
import org.junit.Test

class HomeGestureGuideTest {
    @Test fun homeSequenceStartsWithSwipeUpAndEndsWithInbox() {
        val completed = mutableSetOf<HomeGuideStep>()
        for (step in listOf(HomeGuideStep.SWIPE_UP, HomeGuideStep.SETTINGS,
            HomeGuideStep.WIDGETS, HomeGuideStep.RECENTS, HomeGuideStep.CLOCK_TAP,
            HomeGuideStep.CLOCK_LONG_PRESS, HomeGuideStep.INBOX)) {
            assertEquals(step, nextHomeGuideStep(completed, true, true))
            completed += step
        }
        assertNull(nextHomeGuideStep(completed, true, true))
    }

    @Test fun inboxWaitsForEveryApplicablePredecessorEvenWithOutOfOrderStoredFlags() {
        val steps = HomeGuideStep.entries
        for (mask in 0 until (1 shl steps.size)) {
            val completed = steps.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            val next = nextHomeGuideStep(completed, true, true)
            if (next == HomeGuideStep.INBOX) {
                assertTrue(completed.containsAll(steps.filter { it != HomeGuideStep.INBOX }))
            }
            assertEquals(steps.firstOrNull { it !in completed }, next)
        }
    }

    @Test fun accessGrantShowsPendingInboxAndRevocationHidesItWithoutCompletingIt() {
        val earlier = HomeGuideStep.entries.toSet() - HomeGuideStep.INBOX
        assertNull(nextHomeGuideStep(earlier, true, false))
        assertEquals(HomeGuideStep.INBOX, nextHomeGuideStep(earlier, true, true))
        assertNull(nextHomeGuideStep(earlier, true, false))
        assertEquals(HomeGuideStep.INBOX, nextHomeGuideStep(earlier, true, true))
        assertNull(nextHomeGuideStep(earlier + HomeGuideStep.INBOX, true, true))
    }

    @Test fun absentOrCustomClockSkipsOnlyClockGuidesWithoutMarkingThemCompleted() {
        val completed = setOf(HomeGuideStep.SWIPE_UP, HomeGuideStep.SETTINGS,
            HomeGuideStep.WIDGETS, HomeGuideStep.RECENTS)
        assertEquals(HomeGuideStep.INBOX, nextHomeGuideStep(completed, false, true))
        assertNull(nextHomeGuideStep(completed, false, false))
        assertEquals(HomeGuideStep.CLOCK_TAP, nextHomeGuideStep(completed, true, true))
        assertEquals(HomeGuideStep.SWIPE_UP, nextHomeGuideStep(emptySet(), false, true))
    }
}
