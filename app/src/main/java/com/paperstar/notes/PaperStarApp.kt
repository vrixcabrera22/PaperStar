package com.paperstar.notes

import android.app.Application
import com.paperstar.notes.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class PaperStarApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repo: Repository by lazy { Repository(filesDir, appScope) }
}
