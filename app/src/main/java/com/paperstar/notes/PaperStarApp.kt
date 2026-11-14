package com.paperstar.notes

import android.app.Application
import com.paperstar.notes.data.Repository
import com.paperstar.notes.backup.Backup
import com.paperstar.notes.ocr.Recognizer
import com.paperstar.notes.reminders.Reminders
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class PaperStarApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repo: Repository by lazy { Repository(filesDir, appScope) }

    override fun onCreate() {
        super.onCreate()
        Recognizer(this, repo, appScope).start()
        Reminders.ensureChannel(this)
        Backup.load(this)
        // Keep alarms in step with the notes whenever a reminder is added, changed or removed.
        appScope.launch {
            var scheduled = emptySet<String>()
            repo.data.map { d -> d.notes.mapNotNull { n -> n.remindAt?.let { n.id to it } } }.distinctUntilChanged().collect {
                scheduled = Reminders.sync(this@PaperStarApp, repo.data.value, scheduled)
            }
        }
    }
}
