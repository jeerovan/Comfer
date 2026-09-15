@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.jeerovan.comfer.journals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jeerovan.comfer.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Material pickers inherit the same palette as Journal, including dynamic/light/dark colors. */
@Composable
internal fun JournalDateTimeDialog(value: Long, zone: String, includeTime: Boolean,
    onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val compact = LocalConfiguration.current.screenHeightDp < 600
    val initial = remember(value, zone) { Instant.ofEpochMilli(value).atZone(ZoneId.of(zone)) }
    var selectedDay by rememberSaveable(value, zone) { mutableStateOf<Long?>(null) }
    if (selectedDay == null) {
        // Material's date-picker millis represent UTC calendar dates, not local instants.
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = minOf(1900, initial.year)..maxOf(2100, initial.year),
            initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker)
        DatePickerDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("journal-date-dialog"),
            confirmButton = { IconButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    if (includeTime) selectedDay = date.toEpochDay()
                    else onConfirm(date.atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli())
                }
            }) { Icon(Icons.Outlined.Check, stringResource(R.string.journal_save)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.journal_cancel)) } }) {
            DatePicker(picker, showModeToggle = !compact)
        }
    } else {
        val time = rememberTimePickerState(initial.hour, initial.minute,
            android.text.format.DateFormat.is24HourFormat(LocalContext.current))
        val date = LocalDate.ofEpochDay(selectedDay!!)
        AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("journal-time-dialog"),
            title = { Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) },
            text = {
                if (compact) TimeInput(time) else TimePicker(time)
            },
            confirmButton = { IconButton(onClick = {
                onConfirm(date.atTime(time.hour, time.minute).atZone(ZoneId.of(zone)).toInstant().toEpochMilli())
            }) { Icon(Icons.Outlined.Check, stringResource(R.string.journal_save)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.journal_cancel)) } })
    }
}
