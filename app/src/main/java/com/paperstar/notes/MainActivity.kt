package com.paperstar.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paperstar.notes.backup.Backup
import com.paperstar.notes.reminders.Reminders
import com.paperstar.notes.ui.AppRoot
import com.paperstar.notes.ui.share.ShareInbox
import com.paperstar.notes.ui.theme.PaperStarTheme

class MainActivity : ComponentActivity() {
    private val repo get() = (application as PaperStarApp).repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) { ShareInbox.accept(intent); acceptReminder(intent) }
        setContent { PaperStarTheme { AppRoot(repo) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ShareInbox.accept(intent)
        acceptReminder(intent)
    }

    /** A tapped reminder notification opens its note. */
    private fun acceptReminder(intent: Intent?) {
        intent?.getStringExtra(Reminders.EXTRA_NOTE)?.let { Reminders.openNote.value = it }
    }

    override fun onStop() {
        super.onStop()
        repo.flush()
        // Daily automatic backup, when it's on and a backup place has been chosen.
        val app = application as PaperStarApp
        Backup.autoIfDue(this, repo, app.appScope)
    }
}
