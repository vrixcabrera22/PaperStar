package com.paperstar.notes.ui.note

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.paperstar.notes.data.Note
import com.paperstar.notes.data.ReminderTimes
import com.paperstar.notes.data.Repository
import com.paperstar.notes.ui.PsIcon
import com.paperstar.notes.ui.PsIcons
import com.paperstar.notes.ui.theme.Paper
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/** The "Remind me" chip on a note (#27): quick choices, or any date and time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderChip(repo: Repository, note: Note) {
    val c = Paper.colors
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableLongStateOf(0L) }
    var pickTime by remember { mutableStateOf(false) }
    var pendingAt by remember { mutableStateOf<Long?>(null) }

    fun save(at: Long) {
        if (at <= System.currentTimeMillis()) {
            Toast.makeText(ctx, "Pick a time that hasn't passed yet.", Toast.LENGTH_SHORT).show(); return
        }
        repo.updateNote(note.id) { it.copy(remindAt = at) }
        Toast.makeText(ctx, "I'll remind you ${ReminderTimes.label(at, System.currentTimeMillis()).replaceFirstChar { it.lowercase() }}", Toast.LENGTH_SHORT).show()
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pendingAt?.let { save(it) }
        pendingAt = null
        if (!ok) Toast.makeText(ctx, "Notifications are off for Paper Star, so the reminder can't pop up. You can turn them on in Settings.", Toast.LENGTH_LONG).show()
    }
    /** Asks for notification permission the first time (Android 13+), then saves. */
    fun set(at: Long) {
        val need = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (need) { pendingAt = at; askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) } else save(at)
    }

    val at = note.remindAt
    val now = rememberNow()
    val past = at != null && at <= now
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(999.dp))
                .background(if (at != null && !past) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                .border(1.dp, if (at != null && !past) c.accentSoft else c.line, RoundedCornerShape(999.dp))
                .clickable(role = Role.Button, onClickLabel = "Set a reminder") { menu = true }
                .padding(start = 10.dp, end = if (at != null) 4.dp else 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (at != null && !past) c.accent else c.muted
            PsIcon(PsIcons.Bell, null, tint, Modifier.size(14.dp))
            Text(
                when { at == null -> " Remind me"; past -> " Reminded ${ReminderTimes.label(at, now).replaceFirstChar { it.lowercase() }}"; else -> " ${ReminderTimes.label(at, now)}" },
                style = MaterialTheme.typography.labelMedium, color = tint,
            )
            if (at != null) Box(
                Modifier.padding(start = 4.dp).size(22.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Remove reminder") { repo.updateNote(note.id) { it.copy(remindAt = null) } },
                contentAlignment = Alignment.Center,
            ) { PsIcon(PsIcons.Close, "Remove reminder", tint, Modifier.size(12.dp)) }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            ReminderTimes.presets(now).forEach { p ->
                DropdownMenuItem(text = { Text(p.label) }, onClick = { menu = false; set(p.at) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Pick date & time…") }, onClick = { menu = false; pickDate = true })
        }
    }

    if (pickDate) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (at ?: now).let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate().isBefore(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(enabled = state.selectedDateMillis != null, onClick = {
                    pickedDate = state.selectedDateMillis ?: return@TextButton
                    pickDate = false; pickTime = true
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val cal = Calendar.getInstance().apply { at?.let { timeInMillis = it } ?: run { set(Calendar.HOUR_OF_DAY, 19); set(Calendar.MINUTE, 0) } }
        val state = rememberTimePickerState(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("What time?") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = { pickTime = false; set(ReminderTimes.combine(pickedDate, state.hour, state.minute)) }) { Text("Remind me") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
        )
    }
}

/** A small reminder line for note cards. */
@Composable
fun ReminderLine(at: Long) {
    val c = Paper.colors
    val now = rememberNow()
    val past = at <= now
    Row(verticalAlignment = Alignment.CenterVertically) {
        PsIcon(PsIcons.Bell, null, if (past) c.muted else c.accent, Modifier.size(13.dp))
        Text(" " + ReminderTimes.label(at, now).let { if (past) "Reminded " + it.replaceFirstChar { ch -> ch.lowercase() } else it },
            style = MaterialTheme.typography.labelMedium, color = if (past) c.muted else c.accent)
    }
}

/** The current time, updated every 30 seconds so "Today" and "Reminded" stay right while the screen is open. */
@Composable
private fun rememberNow(): Long {
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { kotlinx.coroutines.delay(30_000); value = System.currentTimeMillis() }
    }
    return now
}
