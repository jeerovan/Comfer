package com.jeerovan.comfer

import com.jeerovan.comfer.notifications.*
import org.junit.Test
import org.junit.Assert.*

class NotificationRulesTest {
    private val item = NotificationItem("k", "app", 0, "Big ＳＡＬＥ", "Save today. Delivery tomorrow", 1, null, false, true, false)
    private val rule = NotificationRule("id", "Sales", terms = listOf("sale"), field = RuleField.TITLE, enabled = true, observeOnly = false)
    @Test fun literalMatchingNormalizesUnicodeAndCaseWithoutRegex() {
        assertTrue(matchNotificationRule(rule, item, emptySet()).matches)
        assertFalse(matchNotificationRule(rule.copy(terms = listOf("s.*e")), item, emptySet()).matches)
    }
    @Test fun fieldsAnyAllAndExceptionsAgree() {
        val both = rule.copy(field = RuleField.BOTH, terms = listOf("sale", "delivery"), matchAll = true)
        assertTrue(matchNotificationRule(both, item, emptySet()).matches)
        assertFalse(matchNotificationRule(both.copy(field = RuleField.TITLE), item, emptySet()).matches)
        assertTrue(matchNotificationRule(both.copy(field = RuleField.TITLE, matchAll = false), item, emptySet()).matches)
        assertFalse(matchNotificationRule(both.copy(exceptions = listOf("tomorrow")), item, emptySet()).matches)
    }
    @Test fun incompleteAndProtectedContentCannotMatch() {
        assertFalse(matchNotificationRule(rule, item.copy(previewAvailable = false), emptySet()).matches)
        assertFalse(matchNotificationRule(rule, item.copy(contentComplete = false), emptySet()).matches)
        assertFalse(matchNotificationRule(rule, item.copy(title = ""), emptySet()).matches)
        assertFalse(matchNotificationRule(rule, item.copy(protected = true), emptySet()).matches)
        assertFalse(matchNotificationRule(rule, item, setOf(item.appId)).matches)
        assertFalse(matchNotificationRule(rule, item.copy(summary = true), emptySet()).matches)
    }
    @Test fun unavailablePreviewsAreExcludedAndSavedSearchIsCaseInsensitive() {
        assertFalse(hasNotificationPreview("", ""))
        assertFalse(hasNotificationPreview("Mail", "Sensitive notification content hidden."))
        assertFalse(hasNotificationPreview("Preview unavailable", ""))
        assertFalse(hasNotificationPreview("Mail", "Contenu masqué", "Contenu masqué"))
        assertTrue(hasNotificationPreview("Code", "Your visible code is 123456"))
        val copy = SavedNotification("saved", "fixture.mail", 0, "Delivery", "Tomorrow", 1, 2)
        val hidden = copy.copy(id = "redacted", text = "Content hidden")
        assertEquals(listOf(copy), savedNotificationsMatching(listOf(copy, hidden), ""))
        assertEquals(listOf(copy), savedNotificationsMatching(listOf(copy), " TOMORROW "))
        assertEquals(listOf(copy), savedNotificationsMatching(listOf(copy), "MAIL"))
        assertTrue(savedNotificationsMatching(listOf(copy), "other").isEmpty())
    }
    @Test fun precedencePauseAndObservationAreExplicit() {
        val dismiss = rule.copy(id = "second", action = RuleAction.DISMISS)
        val config = NotificationConfiguration(rules = listOf(rule, dismiss))
        assertEquals(rule, firstNotificationRule(item, config))
        assertNull(firstNotificationRule(item, config.copy(paused = true)))
        assertEquals(dismiss, firstNotificationRule(item, config.copy(rules = listOf(rule.copy(observeOnly = true), dismiss))))
    }
    @Test fun profileAndChannelBoundariesAndLimitsAreRespected() {
        assertFalse(matchNotificationRule(rule.copy(appId = "1:app"), item, emptySet()).matches)
        assertFalse(matchNotificationRule(rule.copy(channelId = "offers"), item, emptySet()).matches)
        assertFalse(validNotificationRule(rule.copy(terms = listOf(""))))
        assertFalse(validNotificationRule(rule.copy(terms = List(9) { "term" })))
    }
    @Test fun openedCopiesAppearOnlyAfterSourceRemovalAndDisappearOnManualDismiss() {
        val copy = SavedNotification(savedNotificationId(item), item.app, item.profile, item.title, item.text, 1, 2, true)
        assertTrue(retainedInboxCopies(listOf(copy), listOf(item), true).isEmpty())
        assertEquals(listOf(copy), retainedInboxCopies(listOf(copy), emptyList(), true))
        assertTrue(retainedInboxCopies(listOf(copy.copy(keepInInbox = false)), emptyList(), true).isEmpty())
        assertTrue(retainedInboxCopies(listOf(copy), emptyList(), false).isEmpty())
    }
    @Test fun savedTabExcludesLiveCopiesUntilTheirSourceDisappears() {
        val copy = SavedNotification(savedNotificationId(item), item.app, item.profile, item.title, item.text, 1, 2)
        assertTrue(savedNotificationsMatching(listOf(copy), "", listOf(item)).isEmpty())
        assertTrue(savedNotificationsMatching(listOf(copy), "sale", listOf(item.copy(revision = 2))).isEmpty())
        assertEquals(listOf(copy), savedNotificationsMatching(listOf(copy), "", emptyList()))
        // A later post or another profile is a different notification, even with the same key.
        assertEquals(listOf(copy), savedNotificationsMatching(listOf(copy), "", listOf(item.copy(postedAt = 3))))
        assertEquals(listOf(copy), savedNotificationsMatching(listOf(copy), "", listOf(item.copy(profile = 1))))
    }
}
