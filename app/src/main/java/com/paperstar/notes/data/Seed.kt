package com.paperstar.notes.data

/** Example jars shown on first launch, so the app doesn't open empty. */
object Seed {
    fun data(now: Long = System.currentTimeMillis()): AppData {
        val day = 86_400_000L
        val bio = Folder(name = "Biology", colorKey = 2)
        val math = Folder(name = "General Math", colorKey = 1)
        val hist = Folder(name = "History", colorKey = 3)
        val me = Folder(name = "Personal", colorKey = 0)
        fun t(ago: Int) = now - ago * day
        val notes = listOf(
            Note(folderId = bio.id, title = "Cell structure", body = "Nucleus = control center.\nMitochondria = powerhouse of the cell!!\nPlant cells have a cell wall, animal cells don't.",
                tags = listOf("exam"), pinned = true, createdAt = t(0), updatedAt = t(0)),
            Note(folderId = bio.id, title = "Quiz on Friday", body = "Coverage: cell parts, plant vs animal cells, diffusion.",
                checklist = listOf(CheckItem(text = "Read p. 52–60", done = true), CheckItem(text = "Review the cell diagram"), CheckItem(text = "Answer the lab questions")),
                tags = listOf("exam", "quiz"), createdAt = t(1), updatedAt = t(1)),
            Note(folderId = math.id, title = "Quadratic formula", body = "x = (-b ± √(b² - 4ac)) / 2a\n\nex: x² + 5x + 6 = 0 → x = -2 or x = -3",
                tags = listOf("formula"), createdAt = t(0), updatedAt = t(0)),
            Note(folderId = math.id, title = "Assignment", body = "Exercises 3.1, numbers 1–15 (odd only).",
                checklist = listOf(CheckItem(text = "Numbers 1–7"), CheckItem(text = "Numbers 9–15")),
                tags = listOf("homework"), createdAt = t(2), updatedAt = t(2)),
            Note(folderId = hist.id, title = "Timeline: 1896–1898", body = "1896 – Cry of Pugad Lawin\n1898 – Declaration of Independence in Kawit",
                tags = listOf("exam"), createdAt = t(4), updatedAt = t(4)),
            Note(folderId = me.id, title = "Things to buy",
                checklist = listOf(CheckItem(text = "Index cards"), CheckItem(text = "Blue and red pens", done = true), CheckItem(text = "Highlighters")),
                createdAt = t(1), updatedAt = t(1)),
            Note(folderId = me.id, title = "Cafe study spots", body = "Quiet ones with outlets.", createdAt = t(6), updatedAt = t(6)),
        )
        return AppData(folders = listOf(bio, math, hist, me), notes = notes)
    }
}
