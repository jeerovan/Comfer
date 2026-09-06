package com.jeerovan.comfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppListReorderTest {
    private val apps = listOf("first", "second", "third", "last")

    @Test fun firstToSecondAndBack() {
        val moved = moveListItem(apps, 0, 1)
        assertEquals(listOf("second", "first", "third", "last"), moved)
        assertEquals(apps, moveListItem(moved, 1, 0))
        assertEquals("first", apps.first())
    }

    @Test fun firstToLastAndLastToFirst() {
        assertEquals(listOf("second", "third", "last", "first"), moveListItem(apps, 0, 3))
        assertEquals(listOf("last", "first", "second", "third"), moveListItem(apps, 3, 0))
    }

    @Test fun staleOrInvalidPositionsAreIgnored() {
        for ((from, to) in listOf(-1 to 0, 0 to -1, 4 to 0, 0 to 4, 0 to 0)) {
            assertSame(apps, moveListItem(apps, from, to))
        }
        assertEquals(emptyList<String>(), moveListItem(emptyList<String>(), 0, 1))
        assertEquals(listOf("only"), moveListItem(listOf("only"), 0, 1))
    }

    @Test fun rapidMovesFollowTheDraggedKeyAfterPositionsChange() {
        val second = moveListItemByKey(apps, "first", "second") { it }
        val third = moveListItemByKey(second, "first", "third") { it }
        assertEquals(listOf("second", "third", "first", "last"), third)
        assertSame(third, moveListItemByKey(third, "removed", "first") { it })
        assertSame(third, moveListItemByKey(third, "first", "removed") { it })
    }

    @Test fun folderReorderPreservesPackagesThatAreTemporarilyUnavailable() {
        val saved = listOf("first", "unavailable", "second", "third")
        assertEquals(listOf("unavailable", "second", "first", "third"),
            moveListItemByKey(saved, "first", "second") { it })
    }
}
