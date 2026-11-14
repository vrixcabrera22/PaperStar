package com.paperstar.notes.ui

import android.widget.Toast
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.paperstar.notes.data.Repository
import com.paperstar.notes.reminders.Reminders
import androidx.compose.runtime.LaunchedEffect
import com.paperstar.notes.ui.home.HomeScreen
import com.paperstar.notes.ui.jar.JarScreen
import com.paperstar.notes.ui.media.FileViewer
import com.paperstar.notes.ui.media.PictureViewer
import com.paperstar.notes.ui.note.NoteScreen
import com.paperstar.notes.ui.pen.PenScreen
import com.paperstar.notes.ui.share.ShareDialog
import com.paperstar.notes.ui.share.ShareInbox
import com.paperstar.notes.ui.share.applyShare
import com.paperstar.notes.ui.share.importShare
import com.paperstar.notes.ui.theme.Paper
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data class Jar(val id: String) : Screen
    data class Note(val id: String) : Screen
    data class Pen(val noteId: String, val page: Int) : Screen
    data class Picture(val noteId: String, val picId: String) : Screen
    data class File(val noteId: String, val fileId: String) : Screen
}

/** A tiny back stack: Home → Jar → Note → Pen / Picture / File. */
@Composable
fun AppRoot(repo: Repository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by repo.data.collectAsState()
    val drops by repo.drops.collectAsState()
    val share by ShareInbox.pending.collectAsState()
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val top = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.size > 1) { pop() }

    // A tapped reminder opens its note, with its jar behind it for Back.
    val remindNote by Reminders.openNote.collectAsState()
    LaunchedEffect(remindNote) {
        val n = remindNote?.let { data.note(it) } ?: return@LaunchedEffect
        Reminders.openNote.value = null
        stack.clear(); stack.add(Screen.Home); stack.add(Screen.Jar(n.folderId)); stack.add(Screen.Note(n.id))
    }

    Box(Modifier.fillMaxSize().background(Paper.colors.bg)) {
        AnimatedContent(top, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
            when (s) {
                Screen.Home -> HomeScreen(repo, data, drops, onOpenJar = { push(Screen.Jar(it)) }, onOpenNote = { push(Screen.Note(it)) })
                is Screen.Jar -> JarScreen(
                    repo, data, s.id, onBack = ::pop, onOpenNote = { push(Screen.Note(it)) },
                    // a pen note opens straight into the pen editor; Back returns to the note
                    onOpenPenNote = { push(Screen.Note(it)); push(Screen.Pen(it, 0)) },
                )
                is Screen.Note -> NoteScreen(
                    repo, data, s.id, onBack = ::pop,
                    onOpenPen = { page -> push(Screen.Pen(s.id, page)) },
                    onOpenPicture = { push(Screen.Picture(s.id, it)) },
                    onOpenFile = { push(Screen.File(s.id, it)) },
                )
                is Screen.Pen -> PenScreen(repo, data, s.noteId, s.page, onDone = ::pop)
                // "Write on it" swaps the viewer for the pen editor; Done then returns to the note
                is Screen.Picture -> PictureViewer(repo, data, s.noteId, s.picId, onBack = ::pop,
                    onWriteOn = { page -> pop(); push(Screen.Pen(s.noteId, page)) })
                is Screen.File -> FileViewer(repo, data, s.noteId, s.fileId, onBack = ::pop,
                    onWriteOn = { page -> pop(); push(Screen.Pen(s.noteId, page)) })
            }
        }
    }

    share?.let { payload ->
        ShareDialog(
            data, payload,
            onSave = { jar, existing ->
                ShareInbox.pending.value = null
                scope.launch {
                    // Copy first, so a share that can't be read doesn't leave an empty note behind.
                    val got = importShare(ctx, payload)
                    if (got.isEmpty) {
                        Toast.makeText(ctx, "Couldn't open what was shared. Try saving it to your phone first.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val noteId = existing ?: repo.addNote(
                        jar.id,
                        payload.subject ?: got.files.firstOrNull()?.name?.substringBeforeLast('.')
                            ?: ("Shared " + java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())),
                    ).id
                    applyShare(repo, noteId, got)
                    val msg = if (got.failed > 0) "Saved to ${jar.name}. ${got.failed} item(s) couldn't be opened." else "Saved to ${jar.name}"
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    stack.clear(); stack.add(Screen.Home); stack.add(Screen.Jar(jar.id)); stack.add(Screen.Note(noteId))
                }
            },
            onCancel = { ShareInbox.pending.value = null },
        )
    }
}
