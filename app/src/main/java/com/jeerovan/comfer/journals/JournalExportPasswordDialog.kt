package com.jeerovan.comfer.journals

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.jeerovan.comfer.R

@Composable
internal fun JournalExportPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit, includeNotes: Boolean = false) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (includeNotes) "Encrypted backup" else stringResource(R.string.journal_export_password)) },
        text = { Column {
            Text(if (includeNotes) "Include protected content in this encrypted backup. Keep this password: it is required to restore on another device and cannot be reset." else stringResource(R.string.journal_password_explanation))
            OutlinedTextField(password, { password = it; error = 0 },
                label = { Text(stringResource(R.string.journal_password)) },
                isError = error == R.string.journal_password_too_short,
                visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(confirmation, { confirmation = it; error = 0 },
                label = { Text(stringResource(R.string.journal_password_confirm)) },
                isError = error == R.string.journal_password_mismatch,
                visualTransformation = PasswordVisualTransformation())
            if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            error = when {
                password.length < JournalBackup.MIN_PASSWORD_LENGTH -> R.string.journal_password_too_short
                password != confirmation -> R.string.journal_password_mismatch
                else -> 0
            }
            if (error == 0) onConfirm(password)
        }) { Text(stringResource(R.string.title_backup)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
