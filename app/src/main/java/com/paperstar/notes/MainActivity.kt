package com.paperstar.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paperstar.notes.ui.AppRoot
import com.paperstar.notes.ui.share.ShareInbox
import com.paperstar.notes.ui.theme.PaperStarTheme

class MainActivity : ComponentActivity() {
    private val repo get() = (application as PaperStarApp).repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) ShareInbox.accept(intent)
        setContent { PaperStarTheme { AppRoot(repo) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ShareInbox.accept(intent)
    }

    override fun onStop() {
        super.onStop()
        repo.flush()
    }
}
