package com.jeerovan.comfer.notifications

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.R

/** The same app controls for live notifications and saved copies; no platform handles are needed. */
@Composable
internal fun NotificationAppActions(
    appName: String, protected: Boolean, canCreateRule: Boolean, busy: Boolean,
    onCreateRule: () -> Unit, onToggleProtection: () -> Unit, onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("notification-app-actions"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(appName, style = MaterialTheme.typography.titleMedium)
        TextButton(enabled = !busy && canCreateRule, onClick = onCreateRule) { Text("Create content rule") }
        TextButton(enabled = !busy, onClick = onToggleProtection) {
            Text(stringResource(if (protected) R.string.notification_unprotect else R.string.notification_protect))
        }
        Text("App protection also excludes this app from History and removes its saved copies.", style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy, onClick = onOpenSettings) { Text(stringResource(R.string.notification_sound_settings)) }
    }
}

internal fun notificationAppSettingsDestinations(app: String, channel: String?): List<Intent> = buildList {
    if (Build.VERSION.SDK_INT >= 26) {
        if (channel != null) add(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app).putExtra(Settings.EXTRA_CHANNEL_ID, channel))
        add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app))
    }
    add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$app")))
    add(Intent(Settings.ACTION_SETTINGS))
}
