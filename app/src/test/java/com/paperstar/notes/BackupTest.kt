package com.paperstar.notes

import com.paperstar.notes.data.BackupZip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun roundTripKeepsNotesAndFiles() {
        val src = tmp.newFolder("src")
        File(src, "pictures").mkdirs(); File(src, "pictures/a.jpg").writeText("picture")
        File(src, "attachments").mkdirs(); File(src, "attachments/b.pdf").writeText("pdf")
        File(src, "page-backgrounds").mkdirs(); File(src, "page-backgrounds/c.jpg").writeText("bg")
        File(src, "cache.tmp").writeText("not a note file")

        val bytes = ByteArrayOutputStream()
        assertEquals(3, BackupZip.write(bytes, """{"notes":[]}""", src))

        val dest = tmp.newFolder("dest")
        val json = BackupZip.read(ByteArrayInputStream(bytes.toByteArray()), dest)
        assertEquals("""{"notes":[]}""", json)
        assertEquals("picture", File(dest, "pictures/a.jpg").readText())
        assertEquals("pdf", File(dest, "attachments/b.pdf").readText())
        assertEquals("bg", File(dest, "page-backgrounds/c.jpg").readText())
        assertFalse(File(dest, "cache.tmp").exists())
    }

    @Test fun badPathsAreIgnored() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            fun put(name: String) { z.putNextEntry(ZipEntry(name)); z.write("x".encodeToByteArray()); z.closeEntry() }
            put(BackupZip.DATA); put("../evil.txt"); put("pictures/../../evil.txt"); put("other/x.txt")
        }
        val dest = tmp.newFolder("dest")
        BackupZip.read(ByteArrayInputStream(bytes.toByteArray()), dest)
        assertTrue(dest.walk().filter { it.isFile }.toList().isEmpty())
        assertFalse(File(dest.parentFile, "evil.txt").exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun zipWithoutNotesIsRejected() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z -> z.putNextEntry(ZipEntry("pictures/a.jpg")); z.closeEntry() }
        BackupZip.read(ByteArrayInputStream(bytes.toByteArray()), tmp.newFolder("dest"))
    }

    @Test fun safeNames() {
        assertTrue(BackupZip.isSafeFile("pictures/abc.jpg"))
        assertFalse(BackupZip.isSafeFile("pictures/.."))
        assertFalse(BackupZip.isSafeFile("pictures/a/b.jpg"))
        assertFalse(BackupZip.isSafeFile("/pictures/a.jpg"))
        assertFalse(BackupZip.isSafeFile("pictures/a\\..\\b"))
    }
}
