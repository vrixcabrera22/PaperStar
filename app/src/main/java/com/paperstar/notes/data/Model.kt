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
    /** Bold, italic, highlight and colour ranges in [body]. */
    val marks: List<Mark> = emptyList(),
    val checklist: List<CheckItem> = emptyList(),
    /** Stored lowercase, without the leading '#'. */
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    /** Handwritten pages, in order. */
    val pages: List<Page> = emptyList(),
    val pictures: List<Picture> = emptyList(),
    val files: List<Attachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A picture copied into the app. [path] is relative to the app's files folder. */
@Serializable
data class Picture(
    val id: String = newId(),
    val path: String,
    val width: Int,
    val height: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Any attached file (PDF, Word, slides...), copied into the app. [path] is relative to the files folder. */
@Serializable
data class Attachment(
    val id: String = newId(),
    val name: String,
    val mime: String,
    val size: Long,
    val path: String,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class FileKind(val label: String) { PDF("PDF"), DOC("DOC"), SLIDES("PPT"), SHEET("XLS"), IMAGE("IMG"), TEXT("TXT"), OTHER("FILE") }

fun kindOf(name: String, mime: String): FileKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        mime == "application/pdf" || ext == "pdf" -> FileKind.PDF
        ext in setOf("doc", "docx", "odt", "rtf") || mime.contains("wordprocessing") || mime == "application/msword" -> FileKind.DOC
        ext in setOf("ppt", "pptx", "odp", "key") || mime.contains("presentation") || mime.contains("powerpoint") -> FileKind.SLIDES
        ext in setOf("xls", "xlsx", "ods", "csv") || mime.contains("spreadsheet") || mime.contains("excel") -> FileKind.SHEET
        mime.startsWith("image/") -> FileKind.IMAGE
        mime.startsWith("text/") || ext in setOf("txt", "md") -> FileKind.TEXT
        else -> FileKind.OTHER
    }
}

fun sizeText(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
}

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
