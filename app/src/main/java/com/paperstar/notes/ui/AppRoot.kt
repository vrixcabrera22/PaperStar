package com.paperstar.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.paperstar.notes.data.Repository
import com.paperstar.notes.ui.home.HomeScreen
import com.paperstar.notes.ui.jar.JarScreen
import com.paperstar.notes.ui.note.NoteScreen
import com.paperstar.notes.ui.pen.PenScreen
import com.paperstar.notes.ui.theme.Paper

sealed interface Screen {
    data object Home : Screen
    data class Jar(val id: String) : Screen
    data class Note(val id: String) : Screen
    data class Pen(val noteId: String, val page: Int) : Screen
}

/** A tiny back stack: Home → Jar → Note. */
@Composable
fun AppRoot(repo: Repository) {
    val data by repo.data.collectAsState()
    val drops by repo.drops.collectAsState()
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val top = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.size > 1) { pop() }

    Box(Modifier.fillMaxSize().background(Paper.colors.bg)) {
        AnimatedContent(top, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
            when (s) {
                Screen.Home -> HomeScreen(repo, data, drops, onOpenJar = { push(Screen.Jar(it)) }, onOpenNote = { push(Screen.Note(it)) })
                is Screen.Jar -> JarScreen(
                    repo, data, s.id, onBack = ::pop, onOpenNote = { push(Screen.Note(it)) },
                    // a pen note opens straight into the pen editor; Back returns to the note
                    onOpenPenNote = { push(Screen.Note(it)); push(Screen.Pen(it, 0)) },
                )
                is Screen.Note -> NoteScreen(repo, data, s.id, onBack = ::pop, onOpenPen = { page -> push(Screen.Pen(s.id, page)) })
                is Screen.Pen -> PenScreen(repo, data, s.noteId, s.page, onDone = ::pop)
            }
        }
    }
}
