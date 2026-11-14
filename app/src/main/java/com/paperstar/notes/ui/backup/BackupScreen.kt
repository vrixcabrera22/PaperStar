package com.paperstar.notes.ui.backup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.paperstar.notes.backup.Backup
import com.paperstar.notes.data.ReminderTimes
import com.paperstar.notes.data.Repository
import com.paperstar.notes.ui.PsIcon
import com.paperstar.notes.ui.PsIcons
import com.paperstar.notes.ui.theme.Paper
import kotlinx.coroutines.launch

/** Back up to Google Drive (or anywhere) and restore (#36). */
@Composable
fun BackupScreen(repo: Repository, onBack: () -> Unit) {
    val c = Paper.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Backup.status.collectAsState()
    var pending by remember { mutableStateOf<Backup.Pending?>(null) }
    var working by remember { mutableStateOf<String?>(null) }

    fun backUp() = scope.launch {
        val err = Backup.backUpNow(ctx, repo)
        Toast.makeText(ctx, err ?: "Backed up", if (err == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }
    val chooseTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) { Backup.setTarget(ctx, uri); backUp() }
    }
    val chooseRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            working = "Opening the backup…"
            val r = Backup.prepareRestore(ctx, repo, uri)
            working = null
            r.onSuccess { pending = it }.onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PsIcon(PsIcons.Back, null, c.accent, Modifier.size(20.dp))
            Text("Jars", color = c.accent, style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Backup", style = MaterialTheme.typography.headlineMedium, color = c.ink)
            Text("Keep a copy of every jar, note, picture and file in your Google Drive, so nothing is lost if your phone is lost or reset.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted)

            // status card
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PsIcon(PsIcons.Cloud, null, if (s.lastAt > 0 && s.error == null) c.accent else c.muted, Modifier.size(22.dp))
                    Text(
                        when {
                            s.running -> "  Backing up…"
                            s.lastAt > 0 -> "  Last backup: ${ReminderTimes.label(s.lastAt, System.currentTimeMillis()).replaceFirstChar { it.lowercase() }}"
                            else -> "  Not backed up yet"
                        },
                        style = MaterialTheme.typography.titleMedium, color = c.ink,
                    )
                }
                s.targetName?.let { Text("Saved as “$it”", style = MaterialTheme.typography.bodySmall, color = c.muted) }
                s.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.danger) }
            }

            BigButton(if (s.target == null) "Back up to Google Drive" else "Back up now", primary = true, enabled = !s.running) {
                if (s.target == null) chooseTarget.launch(Backup.FILE_NAME) else backUp()
            }
            if (s.target == null) Text(
                "On the next screen, tap ☰ and choose Google Drive (or another place), then tap Save. Paper Star keeps using that file.",
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Switch) { Backup.setAuto(ctx, !s.auto) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Back up automatically", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text("Once a day, when you leave the app", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Switch(s.auto, { Backup.setAuto(ctx, it) }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
            }

            if (s.target != null) BigButton("Save backups somewhere else", enabled = !s.running) { chooseTarget.launch(Backup.FILE_NAME) }
            BigButton("Restore from a backup…", enabled = !s.running) { chooseRestore.launch(arrayOf("application/zip", "application/octet-stream")) }
            Text("The backup isn't locked with a password, so keep it in your own Drive. Notes in the Secret jar are included.",
                style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }

    working?.let { msg ->
        AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp)); Text(msg, modifier = Modifier.padding(start = 16.dp))
            }
        })
    }
    pending?.let { p ->
        val jars = p.data.folders.size; val notes = p.data.notes.size
        AlertDialog(
            onDismissRequest = { Backup.cancelRestore(p); pending = null },
            title = { Text("Restore this backup?") },
            text = { Text("It has $notes note${if (notes == 1) "" else "s"} in $jars jar${if (jars == 1) "" else "s"}. Everything in Paper Star now will be replaced by the backup. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        working = "Restoring…"
                        Backup.applyRestore(ctx, repo, p)
                        working = null
                        Toast.makeText(ctx, "Restored", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }) { Text("Replace everything", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { Backup.cancelRestore(p); pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BigButton(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Paper.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (primary) (if (enabled) c.accent else c.line) else c.surface)
            .border(1.dp, if (primary) androidx.compose.ui.graphics.Color.Transparent else c.line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) { Text(label, style = MaterialTheme.typography.titleMedium, color = if (primary) c.accentInk else c.accent) }
}
