package com.jeerovan.comfer

import com.jeerovan.comfer.notifications.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NotificationConfigurationMigrationTest {
    @Test fun oldVisibilityRulesAreDiscardedRatherThanConvertedToDismissal() {
        val config = decodeNotificationConfiguration("""{
            "hiddenUntil":{"0:mail":999999},
            "pinned":["0:mail"],"historyEnabled":true,
            "rules":[
                {"id":"implicit","name":"Old default","terms":["sale"],"enabled":true,"observeOnly":false},
                {"id":"explicit","name":"Old explicit","terms":["sale"],"action":"HIDE"},
                {"id":"keep","name":"Confirmed dismissal","terms":["sale"],"action":"DISMISS","enabled":true,"observeOnly":false}
            ],
            "quietSchedule":{"enabled":true,"deviceQuiet":false,"hiddenApps":["0:mail"]}
        }""")
        assertEquals(2, config.version)
        assertEquals(listOf("keep"), config.rules.map { it.id })
        assertFalse(config.quietSchedule.enabled)
        assertTrue(config.quietSchedule.deviceQuiet)
        assertTrue(config.historyEnabled)
        assertEquals(setOf("0:mail"), config.pinned)
    }

    @Test fun dndScheduleAndCurrentRulesSurviveReload() {
        val migrated = decodeNotificationConfiguration("""{"quietSchedule":{"enabled":true,"deviceQuiet":true,"hiddenApps":["0:mail"]}}""")
        assertTrue(migrated.quietSchedule.enabled)
        val current = migrated.copy(rules = listOf(NotificationRule("new", "New rule", terms = listOf("sale"))))
        val raw = Json { encodeDefaults = true }.encodeToString(current)
        assertFalse(raw.contains("hiddenUntil"))
        assertFalse(raw.contains("hiddenApps"))
        assertEquals(current, decodeNotificationConfiguration(raw))
    }
}
