package com.jeerovan.comfer.notifications

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun NotificationRingDot(checked: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(14.dp)) {
        if (checked) drawCircle(color)
        else drawCircle(color, style = Stroke(width = 1.4.dp.toPx()))
    }
}

@Composable
fun NotificationSettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).testTag("notification-setting-indicator"), contentAlignment = Alignment.Center) {
            NotificationRingDot(checked)
        }
        Text(label, Modifier.weight(1f))
    }
}
