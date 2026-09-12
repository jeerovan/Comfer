package com.jeerovan.comfer

import org.junit.Assert.*
import org.junit.Test

class HomeGestureGuideTest {
    @Test fun homeSequenceShowsInboxBeforeClockGuides() {
        val completed = mutableSetOf<HomeGuideStep>()
        for (step in listOf(HomeGuideStep.SWIPE_UP, HomeGuideStep.SETTINGS,
            HomeGuideStep.WIDGETS, HomeGuideStep.RECENTS, HomeGuideStep.INBOX,
            HomeGuideStep.CLOCK_TAP, HomeGuideStep.CLOCK_LONG_PRESS)) {
            assertEquals(step, nextHomeGuideStep(completed, true))
            completed += step
        }
        assertNull(nextHomeGuideStep(completed, true))
    }

    @Test fun inboxWaitsForEveryApplicablePredecessorEvenWithOutOfOrderStoredFlags() {
        val steps = listOf(HomeGuideStep.SWIPE_UP, HomeGuideStep.SETTINGS,
            HomeGuideStep.WIDGETS, HomeGuideStep.RECENTS, HomeGuideStep.INBOX,
            HomeGuideStep.CLOCK_TAP, HomeGuideStep.CLOCK_LONG_PRESS)
        for (mask in 0 until (1 shl steps.size)) {
            val completed = steps.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            val next = nextHomeGuideStep(completed, true)
            if (next == HomeGuideStep.INBOX) {
                assertTrue(completed.containsAll(steps.take(4)))
            }
            assertEquals(steps.firstOrNull { it !in completed }, next)
        }
    }

    @Test fun pendingInboxDoesNotRequirePermissionAndCompletedInboxDoesNotReappear() {
        val completed = HomeGuideStep.entries.toSet() - HomeGuideStep.INBOX
        assertEquals(HomeGuideStep.INBOX, nextHomeGuideStep(completed, true))
        assertNull(nextHomeGuideStep(completed + HomeGuideStep.INBOX, true))
    }

    @Test fun absentOrCustomClockSkipsOnlyClockGuidesWithoutMarkingThemCompleted() {
        val completed = setOf(HomeGuideStep.SWIPE_UP, HomeGuideStep.SETTINGS,
            HomeGuideStep.WIDGETS, HomeGuideStep.RECENTS)
        assertEquals(HomeGuideStep.INBOX, nextHomeGuideStep(completed, false))
        assertNull(nextHomeGuideStep(completed + HomeGuideStep.INBOX, false))
        assertEquals(HomeGuideStep.CLOCK_TAP, nextHomeGuideStep(completed + HomeGuideStep.INBOX, true))
        assertEquals(HomeGuideStep.SWIPE_UP, nextHomeGuideStep(emptySet(), false))
    }

    @Test fun pendingInboxPrecedesRemainingClockGuideWithOlderCompletionFlags() {
        val completed = HomeGuideStep.entries.toSet() - HomeGuideStep.INBOX - HomeGuideStep.CLOCK_LONG_PRESS
        assertEquals(HomeGuideStep.INBOX, nextHomeGuideStep(completed, true))
        assertEquals(HomeGuideStep.CLOCK_LONG_PRESS, nextHomeGuideStep(completed + HomeGuideStep.INBOX, true))
    }
}
