package com.jeerovan.comfer

import androidx.annotation.StringRes
import java.io.IOException

/** Keep technical exception messages out of translated user-facing errors. */
@StringRes
internal fun backupErrorLabel(error: Exception): Int = when {
    error is SecurityException -> R.string.backup_error_access
    error is InvalidBackupException && error.message in setOf(
        "This backup was created by a newer, unsupported version",
        "This backup format is no longer supported",
    ) -> R.string.backup_error_version
    error is InvalidBackupException -> R.string.backup_error_invalid
    error is IOException -> R.string.backup_error_storage
    else -> R.string.backup_error_retry
}
