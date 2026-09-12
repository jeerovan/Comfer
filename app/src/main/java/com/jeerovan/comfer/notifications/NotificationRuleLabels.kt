package com.jeerovan.comfer.notifications

import androidx.annotation.StringRes
import com.jeerovan.comfer.R

/** Translate diagnostic outcomes at the UI boundary; rule evaluation stays locale-independent. */
@StringRes
internal fun ruleResultLabel(result: String): Int = when (result) {
    "Invalid rule" -> R.string.notification_rule_reason_invalid
    "Protected or non-clearable notification" -> R.string.notification_rule_reason_protected
    "Different app or profile" -> R.string.notification_rule_reason_app
    "Different notification channel" -> R.string.notification_rule_reason_channel
    "Insufficient notification content" -> R.string.notification_rule_reason_incomplete
    "Exception text matched" -> R.string.notification_rule_reason_exception
    "All required text matched" -> R.string.notification_rule_reason_all
    "Any required text matched" -> R.string.notification_rule_reason_any
    "Required text did not match" -> R.string.notification_rule_reason_none
    "Test only: matched; no action" -> R.string.notification_rule_outcome_test
    "Skipped: notification changed" -> R.string.notification_rule_outcome_changed
    "Dismissal requested" -> R.string.notification_rule_outcome_requested
    "Dismissal unavailable" -> R.string.notification_rule_outcome_unavailable
    else -> R.string.notification_rule_outcome_unknown
}
