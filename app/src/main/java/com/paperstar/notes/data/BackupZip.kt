package com.paperstar.notes.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The backup file format (#36): a zip with the notes as JSON plus every saved picture,
 * attached file and page background. Plain Java, so it can be tested without a phone.
 */
object BackupZip {
    const val DATA = "paperstar.json"
    /** Folders inside the app's files folder that hold note files. */
    val FOLDERS = listOf(Imports.PICTURES, Imports.FILES, Imports.PAGE_BGS)

    /** Writes [json] and the note files under [filesDir] to [out]. Returns how many files were included. */
    fun write(out: OutputStream, json: String, filesDir: File): Int {
        var count = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DATA)); zip.write(json.encodeToByteArray()); zip.closeEntry()
            for (folder in FOLDERS) {
                File(filesDir, folder).listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("$folder/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        }
        return count
    }

    /**
     * Unpacks a backup into [dest] (which should be empty) and returns its JSON.
     * Only the notes file and files in the known folders are accepted, so a bad zip
     * can't write anywhere else. Throws if there's no notes file.
     */
    fun read(input: InputStream, dest: File): String {
        var json: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name
                when {
                    name == DATA -> json = zip.readBytes().decodeToString()
                    isSafeFile(name) -> {
                        val out = File(dest, name)
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                    else -> Unit // ignore anything unexpected
                }
            }
        }
        return json ?: throw IllegalArgumentException("This isn't a Paper Star backup.")
    }

    /** "pictures/abc.jpg" is fine; "../x", "pictures/../../x", "other/x" and nested paths are not. */
    fun isSafeFile(name: String): Boolean {
        val parts = name.split('/')
        if (parts.size != 2) return false
        val (folder, file) = parts
        return folder in FOLDERS && file.isNotEmpty() && file != "." && file != ".." && '\\' !in file
    }
}
