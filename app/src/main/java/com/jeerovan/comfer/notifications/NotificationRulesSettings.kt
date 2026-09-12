@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.jeerovan.comfer.notifications

import com.jeerovan.comfer.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID
import java.text.DateFormat
import java.util.Date

@Composable
internal fun NotificationRulesSettings(onEdit: (String?) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val config by NotificationPreferences.state.collectAsState()
    val activity by NotificationRuleActivity.state.collectAsState()
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf(false) }
    var deletingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ui_content_based_rules), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.notification_rules_help), style = MaterialTheme.typography.bodySmall)
        if (config.paused) Text(stringResource(R.string.notification_rules_paused))
        if (failure) Text(stringResource(R.string.ui_could_not_save_rule_changes), color = MaterialTheme.colorScheme.error)
        Button(enabled = config.rules.size < 20, onClick = { onEdit(null) }) { Text(stringResource(R.string.ui_create_content_rule)) }
        Text(stringResource(R.string.notification_rules_gestures), style = MaterialTheme.typography.bodySmall)
        for ((index, rule) in config.rules.withIndex()) key(rule.id) {
            NotificationSwipeContainer(enabled = deletingRuleId == null, onDismiss = {
                deletingRuleId = rule.id
                false // Keep the rule and restore its card until deletion is confirmed.
            }) {
                OutlinedCard(Modifier.fillMaxWidth().testTag("rule-card:${rule.id}")
                    .combinedClickable(onClick = {}, onLongClickLabel = resources.getString(R.string.ui_edit_and_preview), onLongClick = { onEdit(rule.id) })
                    .semantics { customActions = listOf(CustomAccessibilityAction(resources.getString(R.string.ui_delete_rule_action)) { deletingRuleId = rule.id; true }) }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(rule.appId?.let { appLabel(context, it.substringAfter(':')) } ?: resources.getString(R.string.notification_all),
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("rule-enabled:${rule.id}")
                                .semantics { contentDescription = resources.getString(R.string.notification_enable_named_rule, rule.name) }
                                .toggleable(value = rule.enabled, role = Role.Switch, onValueChange = { enabled -> scope.launch {
                                    failure = !NotificationPreferences.update { it.copy(rules = it.rules.map { r -> if (r.id == rule.id) r.copy(enabled = enabled) else r }) }
                                } }), contentAlignment = Alignment.Center) {
                                Switch(checked = rule.enabled, onCheckedChange = null, modifier = Modifier.scale(.7f))
                            }
                        }
                        Text(rule.name, style = MaterialTheme.typography.bodyMedium)
                        Text(if (rule.observeOnly) resources.getString(R.string.ui_test_only) else resources.getString(R.string.ui_automatically_dismiss))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (index > 0) TextButton(onClick = { scope.launch {
                                failure = !NotificationPreferences.update { c ->
                                    val list = c.rules.toMutableList()
                                    val at = list.indexOfFirst { it.id == rule.id }
                                    if (at > 0) java.util.Collections.swap(list, at, at - 1)
                                    c.copy(rules = list)
                                }
                            } }) { Text(stringResource(R.string.ui_move_up)) }
                        }
                    }
                }
            }
        }
        if (activity.isNotEmpty()) {
            Text(stringResource(R.string.ui_recent_rule_activity), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.notification_rule_activity_help), style = MaterialTheme.typography.bodySmall)
            activity.take(10).forEach { event ->
                Text(stringResource(R.string.notification_rule_activity_entry, DateFormat.getTimeInstance().format(Date(event.time)), config.rules.firstOrNull { it.id == event.ruleId }?.name ?: resources.getString(R.string.ui_deleted_rule), stringResource(ruleResultLabel(event.outcome))), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    deletingRuleId?.let { id ->
        AlertDialog(onDismissRequest = { deletingRuleId = null },
            title = { Text(stringResource(R.string.ui_delete_rule)) },
            text = { Text(stringResource(R.string.notification_delete_named_rule, config.rules.firstOrNull { it.id == id }?.name ?: resources.getString(R.string.ui_this_rule))) },
            confirmButton = { TextButton(onClick = {
                deletingRuleId = null
                scope.launch { failure = !NotificationPreferences.update { it.copy(rules = it.rules.filterNot { rule -> rule.id == id }) } }
            }) { Text(stringResource(R.string.ui_delete)) } },
            dismissButton = { TextButton(onClick = { deletingRuleId = null }) { Text(stringResource(R.string.notification_cancel)) } })
    }

}

@Composable
internal fun NotificationRuleEditor(ruleId: String?, source: NotificationItem? = null, savedSource: SavedNotification? = null, onSaved: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val config by NotificationPreferences.state.collectAsState()
    val snapshot by com.jeerovan.comfer.MyNotificationListenerService.snapshot.collectAsState()
    val original = config.rules.firstOrNull { it.id == ruleId }
    val scope = rememberCoroutineScope()
    val savedField = if (savedSource?.title.isNullOrBlank()) RuleField.BODY else RuleField.TITLE
    fun phrase(text: String) = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(100).orEmpty()
    var name by rememberSaveable(ruleId) { mutableStateOf(original?.name ?: resources.getString(R.string.ui_new_rule)) }
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
        Text(stringResource(R.string.ui_match_notification_content), style = MaterialTheme.typography.titleMedium)
        if (savedSource != null) {
            Text(stringResource(R.string.ui_saved_notification_reference), style = MaterialTheme.typography.titleSmall)
            Text(savedSource.title)
            Text(savedSource.text)
            Text(stringResource(R.string.notification_saved_rule_help), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (savedSource.title.isNotBlank()) TextButton(onClick = { field = RuleField.TITLE; terms = phrase(savedSource.title) }) { Text(stringResource(R.string.ui_use_title)) }
                if (savedSource.text.isNotBlank()) TextButton(onClick = { field = RuleField.BODY; terms = phrase(savedSource.text) }) { Text(stringResource(R.string.ui_use_message)) }
            }
        }
        OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.ui_rule_name)) }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.ui_app_and_profile))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = app == null, onClick = { app = null; channel = null }, label = { Text(stringResource(R.string.notification_all)) })
            for (appId in (snapshot.items.map { it.appId } + listOfNotNull(app)).distinct()) FilterChip(selected = app == appId,
                onClick = { app = appId; channel = null }, label = { Text(appLabel(context, appId.substringAfter(':'))) })
        }
        if (app != null) {
            Text(stringResource(R.string.ui_notification_channel))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = channel == null, onClick = { channel = null }, label = { Text(stringResource(R.string.ui_all_channels)) })
                for (value in (snapshot.items.filter { it.appId == app }.mapNotNull { it.channelId } + listOfNotNull(channel)).distinct()) FilterChip(selected = channel == value, onClick = { channel = value }, label = { Text(value) })
            }
        }
        Text(stringResource(R.string.ui_search_in))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in RuleField.entries) FilterChip(selected = field == value, onClick = { field = value }, label = { Text(when (value) { RuleField.TITLE -> resources.getString(R.string.ui_title); RuleField.BODY -> resources.getString(R.string.ui_message); RuleField.BOTH -> resources.getString(R.string.ui_title_and_message) }) })
        }
        OutlinedTextField(terms, { terms = it.take(808) }, label = { Text(stringResource(R.string.ui_contains_text_one_phrase_per_line)) }, supportingText = { Text(stringResource(R.string.notification_rule_phrases_help)) }, modifier = Modifier.fillMaxWidth())
        NotificationSettingToggle(stringResource(R.string.ui_require_all_phrases_off_means_any), all) { all = it }
        OutlinedTextField(exceptions, { exceptions = it.take(808) }, label = { Text(stringResource(R.string.ui_except_when_containing_optional)) }, supportingText = { Text(stringResource(R.string.notification_rule_exceptions_help)) }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.ui_action_automatically_dismiss))
        NotificationSettingToggle(stringResource(R.string.ui_test_only_record_matches_without_acting), observe) { observe = it }
        Text(stringResource(R.string.notification_rule_dismiss_limits), style = MaterialTheme.typography.bodySmall)
        Button(enabled = validNotificationRule(draft), onClick = { previewed = draft }) { Text(stringResource(R.string.ui_preview_current_notifications)) }
        if (previewed == draft) {
            savedSource?.let { copy ->
                val example = NotificationItem("saved-preview:${copy.id}", copy.app, copy.profile, copy.title, copy.text, copy.postedAt,
                    group = null, summary = false, clearable = true, protected = false)
                val match = matchNotificationRule(draft, example, config.protectedApps)
                Text(stringResource(R.string.notification_saved_rule_preview, stringResource(if (match.matches) R.string.notification_rule_matches else R.string.notification_rule_skipped), stringResource(ruleResultLabel(match.reason))), modifier = Modifier.testTag("saved-rule-preview"))
            }
            val results = notificationChildren(snapshot.items).map { it to matchNotificationRule(draft, it, config.protectedApps) }
            Text(stringResource(R.string.notification_rule_preview_count, results.count { it.second.matches }, results.size))
            results.take(30).forEach { (item, match) -> Text(stringResource(R.string.notification_rule_preview_item, appLabel(context, item.app), item.title.take(120), stringResource(if (match.matches) R.string.notification_rule_matches else R.string.notification_rule_skipped), stringResource(ruleResultLabel(match.reason))), style = MaterialTheme.typography.bodySmall) }
        }
        if (failure) Text(stringResource(R.string.ui_could_not_save_this_rule))
        Button(enabled = validNotificationRule(draft) && previewed == draft, onClick = {
            if (action == RuleAction.DISMISS && !observe) confirmDismiss = true else save()
        }) { Text(if (observe) resources.getString(R.string.ui_save_in_test_only_mode) else resources.getString(R.string.ui_save_and_enable_rule)) }
    }
    if (confirmDismiss) AlertDialog(onDismissRequest = { confirmDismiss = false }, title = { Text(stringResource(R.string.ui_enable_automatic_dismissal)) }, text = {
        Text(stringResource(R.string.notification_rule_dismiss_consent))
    }, confirmButton = { TextButton(onClick = { confirmDismiss = false; save() }) { Text(stringResource(R.string.ui_enable_auto_dismiss)) } }, dismissButton = { TextButton(onClick = { confirmDismiss = false }) { Text(stringResource(R.string.notification_cancel)) } })
}
