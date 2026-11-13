package com.paperstar.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paperstar.notes.ui.AppRoot
import com.paperstar.notes.ui.theme.PaperStarTheme

class MainActivity : ComponentActivity() {
    private val repo get() = (application as PaperStarApp).repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PaperStarTheme { AppRoot(repo) } }
    }

    override fun onStop() {
        super.onStop()
        repo.flush()
    }
}
