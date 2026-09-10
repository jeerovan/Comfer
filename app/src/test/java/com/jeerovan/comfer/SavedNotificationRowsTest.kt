package com.jeerovan.comfer

import com.jeerovan.comfer.notifications.*
import org.junit.Assert.*
import org.junit.Test

class SavedNotificationRowsTest {
    private val copies = listOf(
        SavedNotification("a", "chat", 0, "A", "A", 3, 1),
        SavedNotification("b", "chat", 0, "B", "B", 1, 3),
        SavedNotification("c", "mail", 0, "C", "C", 2, 2),
        SavedNotification("work", "chat", 10, "Work", "Work", 4, 4),
    )

    @Test fun chronologicalUsesNotificationTimeAndIgnoresCollapsedGroups() {
        val rows = savedNotificationRows(copies, true, setOf("0:chat"), setOf("0:mail"))
        assertEquals(listOf("work", "a", "c", "b"), rows.map { it.record?.id })
    }

    @Test fun groupingRespectsProfilePriorityAndCollapsedGroups() {
        val rows = savedNotificationRows(copies, false, setOf("0:chat"), setOf("0:mail"))
        assertEquals(listOf("saved-group:0:mail", "saved:c", "saved-group:0:chat", "saved-group:10:chat", "saved:work"), rows.map { it.key })
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }

    @Test fun expandedGroupOrdersNewestFirst() {
        val rows = savedNotificationRows(copies, false, emptySet(), emptySet())
        assertEquals(listOf("a", "b"), rows.filter { it.appId == "0:chat" }.mapNotNull { it.record?.id })
    }
}
