package com.paperstar.notes.data

import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Keeps all jars and notes in memory and saves them to one JSON file in the app's
 * private storage. Writes are debounced and atomic, so a crash mid-save can't
 * corrupt the file.
 */
class Repository(dir: File, private val scope: CoroutineScope) {

    private val file = AtomicFile(File(dir, "paperstar.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _data = MutableStateFlow(load(dir))
    val data: StateFlow<AppData> = _data.asStateFlow()

    /** Jar id -> number of new stars that still need to drop in on Home. */
    private val _drops = MutableStateFlow<Map<String, Int>>(emptyMap())
    val drops: StateFlow<Map<String, Int>> = _drops.asStateFlow()

    private var saveJob: Job? = null

    private fun load(dir: File): AppData {
        val raw = File(dir, "paperstar.json")
        if (!raw.exists()) return Seed.data()
        return try {
            json.decodeFromString(AppData.serializer(), file.readFully().decodeToString())
        } catch (e: Exception) {
            // Keep the unreadable file instead of overwriting the user's notes.
            raw.copyTo(File(dir, "paperstar.broken-${System.currentTimeMillis()}.json"), overwrite = true)
            Seed.data()
        }
    }

    private fun persist() {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(300)
            writeNow(_data.value)
        }
    }

    /** Save right away (called when the app goes to the background). */
    fun flush() {
        saveJob?.cancel()
        val snapshot = _data.value
        scope.launch(Dispatchers.IO) { writeNow(snapshot) }
    }

    @Synchronized
    private fun writeNow(d: AppData) {
        val out = file.startWrite()
        try {
            out.write(json.encodeToString(AppData.serializer(), d).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    private fun edit(fn: (AppData) -> AppData) {
        _data.update(fn)
        persist()
    }

    // ---- jars ----
    fun addFolder(name: String, colorKey: Int): Folder {
        val f = Folder(name = name.trim().ifEmpty { "Untitled jar" }, colorKey = colorKey)
        edit { it.copy(folders = it.folders + f) }
        return f
    }

    fun updateFolder(id: String, fn: (Folder) -> Folder) =
        edit { d -> d.copy(folders = d.folders.map { if (it.id == id) fn(it) else it }) }

    fun deleteFolder(id: String) =
        edit { d -> d.copy(folders = d.folders.filterNot { it.id == id }, notes = d.notes.filterNot { it.folderId == id }) }

    // ---- notes ----
    fun addNote(folderId: String, title: String = ""): Note {
        val n = Note(folderId = folderId, title = title)
        edit { it.copy(notes = listOf(n) + it.notes) }
        _drops.update { it + (folderId to ((it[folderId] ?: 0) + 1)) }
        return n
    }

    fun updateNote(id: String, fn: (Note) -> Note) = edit { d ->
        d.copy(notes = d.notes.map { if (it.id == id) fn(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    /** Pinning shouldn't count as an edit, so it keeps updatedAt. */
    fun togglePin(id: String) = edit { d ->
        d.copy(notes = d.notes.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
    }

    fun deleteNote(id: String) = edit { d -> d.copy(notes = d.notes.filterNot { it.id == id }) }

    fun consumeDrops(folderId: String) = _drops.update { it - folderId }
}
