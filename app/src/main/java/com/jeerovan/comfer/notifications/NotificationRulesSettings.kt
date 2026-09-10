@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.jeerovan.comfer.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID
import java.text.DateFormat
import java.util.Date

@Composable
internal fun NotificationRulesSettings(onEdit: (String?) -> Unit) {
    val context = LocalContext.current
    val config by NotificationPreferences.state.collectAsState()
    val activity by NotificationRuleActivity.state.collectAsState()
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf(false) }
    var deletingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Content-based rules", style = MaterialTheme.typography.titleMedium)
        Text("Literal text matching only; regex is deferred. The first matching active rule wins, in the order below. Test-only rules record matches without changing notifications.", style = MaterialTheme.typography.bodySmall)
        if (config.paused) Text("Rules are paused. Resume in Notification settings to process future notifications; existing notifications will not be automatically dismissed.")
        if (failure) Text("Could not save rule changes.", color = MaterialTheme.colorScheme.error)
        Button(enabled = config.rules.size < 20, onClick = { onEdit(null) }) { Text("Create content rule") }
        Text("Long press a rule to edit and preview. Swipe to delete.", style = MaterialTheme.typography.bodySmall)
        for ((index, rule) in config.rules.withIndex()) key(rule.id) {
            NotificationSwipeContainer(enabled = deletingRuleId == null, onDismiss = {
                deletingRuleId = rule.id
                false // Keep the rule and restore its card until deletion is confirmed.
            }) {
                OutlinedCard(Modifier.fillMaxWidth().testTag("rule-card:${rule.id}")
                    .combinedClickable(onClick = {}, onLongClickLabel = "Edit and preview", onLongClick = { onEdit(rule.id) })
                    .semantics { customActions = listOf(CustomAccessibilityAction("Delete rule") { deletingRuleId = rule.id; true }) }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(rule.appId?.let { appLabel(context, it.substringAfter(':')) } ?: "All apps",
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("rule-enabled:${rule.id}")
                                .semantics { contentDescription = "Enable ${rule.name}" }
                                .toggleable(value = rule.enabled, role = Role.Switch, onValueChange = { enabled -> scope.launch {
                                    failure = !NotificationPreferences.update { it.copy(rules = it.rules.map { r -> if (r.id == rule.id) r.copy(enabled = enabled) else r }) }
                                } }), contentAlignment = Alignment.Center) {
                                Switch(checked = rule.enabled, onCheckedChange = null, modifier = Modifier.scale(.7f))
                            }
                        }
                        Text(rule.name, style = MaterialTheme.typography.bodyMedium)
                        Text(if (rule.observeOnly) "Test only" else "Automatically dismiss")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (index > 0) TextButton(onClick = { scope.launch {
                                failure = !NotificationPreferences.update { c ->
                                    val list = c.rules.toMutableList()
                                    val at = list.indexOfFirst { it.id == rule.id }
                                    if (at > 0) java.util.Collections.swap(list, at, at - 1)
                                    c.copy(rules = list)
                                }
                            } }) { Text("Move up") }
                        }
                    }
                }
            }
        }
        if (activity.isNotEmpty()) {
            Text("Recent rule activity", style = MaterialTheme.typography.titleSmall)
            Text("Last 50 outcomes, kept in memory without notification text.", style = MaterialTheme.typography.bodySmall)
            activity.take(10).forEach { event ->
                Text("${DateFormat.getTimeInstance().format(Date(event.time))} · ${config.rules.firstOrNull { it.id == event.ruleId }?.name ?: "Deleted rule"}: ${event.outcome}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    deletingRuleId?.let { id ->
        AlertDialog(onDismissRequest = { deletingRuleId = null },
            title = { Text("Delete rule?") },
            text = { Text("Delete ${config.rules.firstOrNull { it.id == id }?.name ?: "this rule"}? This does not dismiss existing notifications.") },
            confirmButton = { TextButton(onClick = {
                deletingRuleId = null
                scope.launch { failure = !NotificationPreferences.update { it.copy(rules = it.rules.filterNot { rule -> rule.id == id }) } }
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingRuleId = null }) { Text("Cancel") } })
    }

}

@Composable
internal fun NotificationRuleEditor(ruleId: String?, source: NotificationItem? = null, savedSource: SavedNotification? = null, onSaved: () -> Unit) {
    val context = LocalContext.current
    val config by NotificationPreferences.state.collectAsState()
    val snapshot by com.jeerovan.comfer.MyNotificationListenerService.snapshot.collectAsState()
    val original = config.rules.firstOrNull { it.id == ruleId }
    val scope = rememberCoroutineScope()
    val savedField = if (savedSource?.title.isNullOrBlank()) RuleField.BODY else RuleField.TITLE
    fun phrase(text: String) = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(100).orEmpty()
    var name by rememberSaveable(ruleId) { mutableStateOf(original?.name ?: "New rule") }
    val id = rememberSaveable(ruleId) { ruleId ?: UUID.randomUUID().toString() }
    var app by rememberSaveable(ruleId) { mutableStateOf(original?.appId ?: source?.appId ?: savedSource?.appId) }
    var channel by rememberSaveable(ruleId) { mutableStateOf(original?.channelId ?: source?.channelId) }
    var field by rememberSaveable(ruleId) { mutableStateOf(original?.field ?: if (savedSource != null) savedField else RuleField.BOTH) }
    var terms by rememberSaveable(ruleId) { mutableStateOf(original?.terms?.joinToString("\n") ?: savedSource?.let { phrase(if (savedField == RuleField.TITLE) it.title else it.text) }.orEmpty()) }
    var exceptions by rememberSaveable(ruleId) { mutableStateOf(original?.exceptions?.joinToString("\n") ?: "") }
    var all by rememberSaveable(ruleId) { mutableStateOf(original?.matchAll ?: false) }
    val action = RuleAction.DISMISS
    var observe by rememberSaveable(ruleId) { mutableStateOf(original?.observeOnly ?: true) }
    var previewed by remember { mutableStateOf<NotificationRule?>(null) }
    var confirmDismiss by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    fun lines(value: String) = value.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val draft = NotificationRule(id, name.trim(), app, channel, field, lines(terms), lines(exceptions), all, action, enabled = true, observeOnly = observe)
    fun save() { scope.launch {
        val saved = NotificationPreferences.update { c -> c.copy(rules = if (c.rules.any { it.id == id }) c.rules.map { if (it.id == id) draft else it } else (c.rules + draft).take(20)) }
        failure = !saved
        if (saved) onSaved()
    } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Match notification content", style = MaterialTheme.typography.titleMedium)
        if (savedSource != null) {
            Text("Saved notification reference", style = MaterialTheme.typography.titleSmall)
            Text(savedSource.title)
            Text(savedSource.text)
            Text("Choose a phrase to match future notifications. Preview uses only the saved text; the original channel and Android actions are unavailable.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (savedSource.title.isNotBlank()) TextButton(onClick = { field = RuleField.TITLE; terms = phrase(savedSource.title) }) { Text("Use title") }
                if (savedSource.text.isNotBlank()) TextButton(onClick = { field = RuleField.BODY; terms = phrase(savedSource.text) }) { Text("Use message") }
            }
        }
        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Rule name") }, modifier = Modifier.fillMaxWidth())
        Text("App and profile")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = app == null, onClick = { app = null; channel = null }, label = { Text("All apps") })
            for (appId in (snapshot.items.map { it.appId } + listOfNotNull(app)).distinct()) FilterChip(selected = app == appId,
                onClick = { app = appId; channel = null }, label = { Text(appLabel(context, appId.substringAfter(':'))) })
        }
        if (app != null) {
            Text("Notification channel")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = channel == null, onClick = { channel = null }, label = { Text("All channels") })
                for (value in (snapshot.items.filter { it.appId == app }.mapNotNull { it.channelId } + listOfNotNull(channel)).distinct()) FilterChip(selected = channel == value, onClick = { channel = value }, label = { Text(value) })
            }
        }
        Text("Search in")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in RuleField.entries) FilterChip(selected = field == value, onClick = { field = value }, label = { Text(when (value) { RuleField.TITLE -> "Title"; RuleField.BODY -> "Message"; RuleField.BOTH -> "Title and message" }) })
        }
        OutlinedTextField(terms, { terms = it.take(808) }, label = { Text("Contains text — one phrase per line") }, supportingText = { Text("1–8 phrases, up to 100 characters each. Case-insensitive literal matching.") }, modifier = Modifier.fillMaxWidth())
        NotificationSettingToggle("Require ALL phrases (off means ANY)", all) { all = it }
        OutlinedTextField(exceptions, { exceptions = it.take(808) }, label = { Text("Except when containing — optional") }, supportingText = { Text("One phrase per line. Any exception prevents a match.") }, modifier = Modifier.fillMaxWidth())
        Text("Action: automatically dismiss")
        NotificationSettingToggle("Test only — record matches without acting", observe) { observe = it }
        Text("Protected, incomplete and unavailable content is skipped. Auto-dismiss removes future matching Android notifications; it cannot guarantee silence or Undo.", style = MaterialTheme.typography.bodySmall)
        Button(enabled = validNotificationRule(draft), onClick = { previewed = draft }) { Text("Preview current notifications") }
        if (previewed == draft) {
            savedSource?.let { copy ->
                val example = NotificationItem("saved-preview:${copy.id}", copy.app, copy.profile, copy.title, copy.text, copy.postedAt,
                    group = null, summary = false, clearable = true, protected = false)
                val match = matchNotificationRule(draft, example, config.protectedApps)
                Text("Saved reference: ${if (match.matches) "Matches" else "Skipped"} — ${match.reason}", modifier = Modifier.testTag("saved-rule-preview"))
            }
            val results = notificationChildren(snapshot.items).map { it to matchNotificationRule(draft, it, config.protectedApps) }
            Text("${results.count { it.second.matches }} of ${results.size} current notifications match. Preview performs no actions.")
            results.take(30).forEach { (item, match) -> Text("${appLabel(context, item.app)} · ${item.title.take(120)}: ${if (match.matches) "Matches" else "Skipped"} — ${match.reason}", style = MaterialTheme.typography.bodySmall) }
        }
        if (failure) Text("Could not save this rule.")
        Button(enabled = validNotificationRule(draft) && previewed == draft, onClick = {
            if (action == RuleAction.DISMISS && !observe) confirmDismiss = true else save()
        }) { Text(if (observe) "Save in test-only mode" else "Save and enable rule") }
    }
    if (confirmDismiss) AlertDialog(onDismissRequest = { confirmDismiss = false }, title = { Text("Enable automatic dismissal?") }, text = {
        Text("This rule will dismiss future matching Android notifications. Existing notifications will not be dismissed by saving or resuming the rule. Undo is not available. History only retains eligible copies if separately enabled.")
    }, confirmButton = { TextButton(onClick = { confirmDismiss = false; save() }) { Text("Enable auto-dismiss") } }, dismissButton = { TextButton(onClick = { confirmDismiss = false }) { Text("Cancel") } })
}
