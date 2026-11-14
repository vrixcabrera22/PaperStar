package com.paperstar.notes.data

import kotlinx.serialization.Serializable

enum class RecKind(val label: String) { PAGE("Handwritten page"), PICTURE("Picture"), PDF("PDF") }

/**
 * Text read from one page, picture or PDF in a note (#24). [sig] says which version of the
 * item was read, so it is read again only after it changes.
 */
@Serializable
data class Recognized(val id: String, val kind: RecKind, val sig: String, val text: String)

/** Something in a note whose text still needs reading. */
data class RecTask(val noteId: String, val id: String, val kind: RecKind, val sig: String, val path: String? = null, val page: Page? = null)

/** Changes whenever the page's ink or picture changes. */
fun pageSig(p: Page): String = "${p.strokes.size}:${p.strokes.sumOf { it.count }}:${p.strokes.lastOrNull()?.id}:${p.bg}"

/** Everything not read yet, or changed since it was read. Blank pages and the Secret jar are skipped. */
fun pendingRecognition(data: AppData): List<RecTask> {
    val secret = data.folders.filter { it.secret }.map { it.id }.toSet()
    val out = mutableListOf<RecTask>()
    for (n in data.notes) {
        if (n.folderId in secret) continue
        val done = n.recognized.associateBy { it.id }
        fun need(id: String, sig: String) = done[id]?.sig != sig
        for (p in n.pages) {
            if (p.strokes.isEmpty() && p.bg == null) continue
            val sig = pageSig(p)
            if (need(p.id, sig)) out += RecTask(n.id, p.id, RecKind.PAGE, sig, page = p)
        }
        for (pic in n.pictures) if (need(pic.id, "1")) out += RecTask(n.id, pic.id, RecKind.PICTURE, "1", path = pic.path)
        for (f in n.files) if (kindOf(f.name, f.mime) == FileKind.PDF && need(f.id, "1")) out += RecTask(n.id, f.id, RecKind.PDF, "1", path = f.path)
    }
    return out
}

/** Keeps only results for items the note still has. */
fun pruneRecognized(n: Note): List<Recognized> {
    val ids = (n.pages.map { it.id } + n.pictures.map { it.id } + n.files.map { it.id }).toSet()
    return n.recognized.filter { it.id in ids }
}
