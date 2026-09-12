package com.jeerovan.comfer

import com.jeerovan.comfer.notifications.*
import org.junit.Assert.*
import org.junit.Test

class NotificationModelTest {
    private fun item(key: String, profile: Int = 0, summary: Boolean = false) =
        NotificationItem(key, "app", profile, "title", "text", 1, "group", summary, true, false)
    @Test fun summariesDoNotDoubleCountOrCrossProfiles() {
        val rows = notificationChildren(listOf(item("a"), item("summary", summary = true), item("work", 10, true)))
        assertEquals(setOf("a", "work"), rows.map { it.key }.toSet())
    }
    @Test fun samePostTimeContentUpdateInvalidatesActions() {
        val ledger = NotificationLedger(); ledger.put(item("a")); val before = ledger.items().single()
        ledger.put(item("a").copy(text = "changed"))
        assertNull(ledger.current("a", before.revision))
        val changed = ledger.items().single(); ledger.reconcile(listOf(item("a").copy(text = "changed")))
        assertEquals(changed.revision, ledger.items().single().revision)
    }
    @Test fun removalAndRepostNeverReuseRevision() {
        val ledger = NotificationLedger(); ledger.put(item("a")); val before = ledger.items().single()
        ledger.remove("a"); ledger.put(item("a")); assertNull(ledger.current("a", before.revision))
    }
    @Test fun thousandRecordsRemainAvailableUnderOneApp() {
        val ledger = NotificationLedger(); ledger.reconcile((1..1000).map { item("$it") })
        assertEquals(1000, notificationChildren(ledger.items()).size)
        ledger.reconcile(listOf(item("remaining"))); assertEquals("remaining", ledger.items().single().key)
    }
    @Test fun reachUsesSameDpHeightOnShortAndTallScreens() {
        assertEquals(360f, reachableHeightDp(600f), .01f)
        assertEquals(360f, reachableHeightDp(640f), .01f)
        assertEquals(360f, reachableHeightDp(1000f), .01f)
    }
    @Test fun reachFitsAvailableSafeHeight() {
        assertEquals(360f, reachableHeightDp(360f), .01f)
        assertEquals(359f, reachableHeightDp(359f), .01f)
        assertEquals(100f, reachableHeightDp(100f), .01f)
        assertEquals(0f, reachableHeightDp(0f), .01f)
        assertEquals(0f, reachableHeightDp(-1f), .01f)
    }
    @Test fun displayGroupsKeepCountsChildrenAndProfilesDistinct() {
        val input = listOf(item("a"), item("b"), item("work", profile = 10))
        val expanded = notificationDisplayRows(input, false, emptySet())
        assertEquals(-1, notificationAnchorIndex(expanded, null))
        assertEquals(-1, notificationAnchorIndex(expanded, "missing"))
        assertEquals(1, notificationAnchorIndex(expanded, "a"))
        assertEquals(listOf(2, 1), expanded.filter { it.notification == null }.map { it.count })
        assertEquals(listOf("a", "b", "work"), expanded.mapNotNull { it.notification?.key })
        val collapsed = notificationDisplayRows(input, false, setOf("0:app"))
        assertEquals(listOf("work"), collapsed.mapNotNull { it.notification?.key })
        assertEquals(2, collapsed.first().count)
        assertEquals(input, notificationDisplayRows(input, true, setOf("0:app")).map { it.notification })
    }
    @Test fun batchEligibilityExcludesProtectedOngoingAndSummaryRecords() {
        assertTrue(canManageNotification(item("a"), emptySet()))
        assertFalse(canManageNotification(item("a"), setOf("0:app")))
        assertFalse(canManageNotification(item("a").copy(clearable = false), emptySet()))
        assertFalse(canManageNotification(item("a").copy(protected = true), emptySet()))
        assertFalse(canManageNotification(item("a", summary = true), emptySet()))
    }
}
