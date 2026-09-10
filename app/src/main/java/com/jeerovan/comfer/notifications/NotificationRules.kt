package com.jeerovan.comfer.notifications

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Serializable
enum class RuleField { TITLE, BODY, BOTH }
@Serializable
enum class RuleAction { DISMISS }
@Serializable
data class NotificationRule(
    val id: String,
    val name: String,
    val appId: String? = null,
    val channelId: String? = null,
    val field: RuleField = RuleField.BOTH,
    val terms: List<String> = emptyList(),
    val exceptions: List<String> = emptyList(),
    val matchAll: Boolean = false,
    val action: RuleAction = RuleAction.DISMISS,
    val enabled: Boolean = false,
    val observeOnly: Boolean = true,
)

internal fun validNotificationRule(rule: NotificationRule): Boolean =
    rule.id.length in 1..100 && rule.name.length in 1..80 && rule.terms.size in 1..8 && rule.exceptions.size <= 8 &&
        (rule.terms + rule.exceptions).all { it.isNotBlank() && it.length <= 100 }

internal data class RuleMatch(val matches: Boolean, val reason: String)
private fun normalizeRuleText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

/** Shared by preview and live evaluation. Missing or truncated content is never a destructive match. */
internal fun matchNotificationRule(rule: NotificationRule, item: NotificationItem, protectedApps: Set<String>): RuleMatch {
    if (!validNotificationRule(rule)) return RuleMatch(false, "Invalid rule")
    if (!canManageNotification(item, protectedApps)) return RuleMatch(false, "Protected or non-clearable notification")
    if (rule.appId != null && rule.appId != item.appId) return RuleMatch(false, "Different app or profile")
    if (rule.channelId != null && rule.channelId != item.channelId) return RuleMatch(false, "Different notification channel")
    val fields = when (rule.field) {
        RuleField.TITLE -> listOf(item.title)
        RuleField.BODY -> listOf(item.text)
        RuleField.BOTH -> listOf(item.title, item.text)
    }
    if (!item.previewAvailable || !item.contentComplete || fields.any { it.isBlank() }) return RuleMatch(false, "Insufficient notification content")
    val text = fields.map(::normalizeRuleText)
    if (rule.exceptions.any { term -> text.any { normalizeRuleText(term) in it } }) return RuleMatch(false, "Exception text matched")
    val matches = rule.terms.map { term -> text.any { normalizeRuleText(term) in it } }
    val matched = if (rule.matchAll) matches.all { it } else matches.any { it }
    return RuleMatch(matched, if (matched) "${if (rule.matchAll) "All" else "Any"} required text matched" else "Required text did not match")
}

internal fun firstNotificationRule(item: NotificationItem, config: NotificationConfiguration): NotificationRule? =
    if (config.paused) null else config.rules.firstOrNull { it.enabled && !it.observeOnly && matchNotificationRule(it, item, config.protectedApps).matches }

internal data class RuleOutcome(val time: Long, val ruleId: String, val outcome: String)
internal object NotificationRuleActivity {
    private val mutable = MutableStateFlow<List<RuleOutcome>>(emptyList())
    val state = mutable.asStateFlow()
    @Synchronized fun record(ruleId: String, outcome: String) {
        mutable.value = (listOf(RuleOutcome(System.currentTimeMillis(), ruleId, outcome)) + mutable.value).take(50)
    }
}
