package com.paperstar.notes.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(12)

/** A jar. In code and storage it is still called a folder. */
@Serializable
data class Folder(
    val id: String = newId(),
    val name: String,
    /** Index into the lid colour list (0..4). */
    val colorKey: Int = 0,
    val secret: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class CheckItem(
    val id: String = newId(),
    val text: String = "",
    val done: Boolean = false,
)

@Serializable
data class Note(
    val id: String = newId(),
    val folderId: String,
    val title: String = "",
    val body: String = "",
    val checklist: List<CheckItem> = emptyList(),
    /** Stored lowercase, without the leading '#'. */
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class AppData(
    val version: Int = 1,
    val folders: List<Folder> = emptyList(),
    val notes: List<Note> = emptyList(),
) {
    fun notesIn(folderId: String): List<Note> = notes.filter { it.folderId == folderId }
    fun folder(id: String): Folder? = folders.firstOrNull { it.id == id }
    fun note(id: String): Note? = notes.firstOrNull { it.id == id }
}

/** Pinned first, then most recently edited. */
fun List<Note>.sortedForJar(): List<Note> =
    sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

fun normalizeTag(raw: String): String =
    raw.trim().removePrefix("#").trim().lowercase().replace(Regex("\\s+"), "-").take(30)
