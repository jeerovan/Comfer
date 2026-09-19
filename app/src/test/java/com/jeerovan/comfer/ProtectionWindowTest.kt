package com.jeerovan.comfer

import org.junit.Assert.*
import org.junit.Test

class ProtectionWindowTest {
    private var elapsed = 0L
    private val session = ProtectionWindow { elapsed }

    @Test fun activeModuleStaysUnlockedAndTimeoutStartsWhenItLeaves() {
        val notes = Any()
        session.enter(notes, 30)
        session.authorize(30)
        elapsed = 3_600_000
        assertTrue(session.authorized(30))
        assertNull(session.remainingMillis())
        session.leave(notes)
        elapsed += 29_999
        assertTrue(session.authorized(30))
        elapsed++
        assertFalse(session.authorized(30))
    }
    @Test fun returningBeforeExpiryPausesTimeoutAndNextExitStartsFresh() {
        val journal = Any()
        session.authorize(30)
        elapsed = 29_000
        assertTrue(session.enter(journal, 30))
        elapsed += 600_000
        assertTrue(session.authorized(30))
        session.leave(journal)
        elapsed += 29_999
        assertTrue(session.authorized(30))
        elapsed++
        assertFalse(session.authorized(30))
    }
    @Test fun rotationAndModuleOverlapWaitForLastOwnerToLeave() {
        val first = Any(); val second = Any()
        session.enter(first, 30); session.authorize(30)
        session.enter(second, 30); session.leave(first)
        elapsed = 600_000
        assertTrue(session.authorized(30))
        session.leave(second)
        elapsed += 29_000
        session.leave(second) // Duplicate lifecycle callbacks cannot restart the timer.
        session.leave(first)
        elapsed += 1000
        assertFalse(session.authorized(30))
    }
    @Test fun openingAfterExpiryStillRequiresVerification() {
        session.authorize(30)
        elapsed = 30_000
        assertFalse(session.enter(Any(), 30))
        assertFalse(session.authorized(30))
        session.authorize(30)
        elapsed += 600_000
        assertTrue(session.authorized(30))
    }

    @Test fun coldStartAndExplicitLockRequireVerification() {
        assertFalse(session.authorized(120))
        session.authorize(120)
        assertTrue(session.authorized(120))
        session.lock()
        assertFalse(session.authorized(120))
        assertFalse(ProtectionWindow { elapsed }.authorized(120))
    }
    @Test fun everyOptionExpiresAtItsExactBoundaryWithoutRenewalOnAccess() {
        for (seconds in listOf(30, 120, 300)) {
            elapsed = 1000
            session.authorize(seconds)
            elapsed += seconds * 1000L - 1
            repeat(5) { assertTrue(session.authorized(seconds)) }
            elapsed++
            assertFalse(session.authorized(seconds))
        }
    }
    @Test fun shorteningTimeoutImmediatelyExpiresOlderVerification() {
        session.authorize(120)
        elapsed = 31_000
        assertFalse(session.authorized(30))
        assertFalse(session.authorized(300))
    }
    @Test fun increasingTimeoutDoesNotRestartTheClock() {
        session.authorize(30)
        elapsed = 29_000
        assertTrue(session.authorized(300))
        elapsed = 300_000
        assertFalse(session.authorized(300))
    }
    @Test fun increasingTimeoutCannotReviveAnUnobservedExpiredSession() {
        session.authorize(30)
        elapsed = 30_000
        assertFalse(session.authorized(300))
    }
    @Test fun successfulReverificationStartsANewWindow() {
        session.authorize(30)
        elapsed = 40_000
        session.authorize(30)
        elapsed = 69_999
        assertTrue(session.authorized(30))
        elapsed++
        assertFalse(session.authorized(30))
    }
    @Test fun invalidPreferenceUsesTwoMinuteDefault() {
        assertEquals(120, ProtectionSession.DEFAULT_SECONDS)
        for (invalid in listOf(-1, 0, 60, Int.MAX_VALUE)) {
            elapsed = 0
            session.authorize(invalid)
            elapsed = 119_999
            assertTrue(session.authorized(invalid))
            elapsed++
            assertFalse(session.authorized(invalid))
        }
    }
}
