package com.paperstar.notes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Copies pictures and files from other apps into Paper Star's private storage. */
object Imports {
    const val PICTURES = "pictures"
    const val FILES = "attachments"
    private const val MAX_SIDE = 2000

    /** Decodes, shrinks to at most 2000 px on the long side, and saves as JPEG. */
    suspend fun picture(ctx: Context, uri: Uri): Picture? = withContext(Dispatchers.IO) {
        try {
            val bmp = decode(ctx, uri) ?: return@withContext null
            val id = newId()
            val rel = "$PICTURES/$id.jpg"
            val out = File(ctx.filesDir, rel).apply { parentFile?.mkdirs() }
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            Picture(id = id, path = rel, width = bmp.width, height = bmp.height).also { bmp.recycle() }
        } catch (e: Exception) {
            null
        }
    }

    private fun decode(ctx: Context, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder also applies the photo's rotation.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                val s = info.size
                val scale = MAX_SIDE.toFloat() / max(s.width, s.height)
                if (scale < 1f) d.setTargetSize((s.width * scale).roundToInt(), (s.height * scale).roundToInt())
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }

    const val PAGE_BGS = "page-backgrounds"

    /** Saves a picture to write on (#11) and returns where it went and the page height to use. */
    suspend fun saveBackground(ctx: Context, bmp: Bitmap): PageBackground? = withContext(Dispatchers.IO) {
        try {
            val rel = "$PAGE_BGS/${newId()}.jpg"
            val out = File(ctx.filesDir, rel).apply { parentFile?.mkdirs() }
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            PageBackground(rel, pageHeightFor(bmp.width, bmp.height))
        } catch (e: Exception) {
            null
        }
    }

    /** Copies a saved picture so it can be written on without changing the original. */
    suspend fun backgroundFromPicture(ctx: Context, src: File): PageBackground? = withContext(Dispatchers.IO) {
        try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(src.path, o)
            if (o.outWidth <= 0) return@withContext null
            val rel = "$PAGE_BGS/${newId()}.jpg"
            src.copyTo(File(ctx.filesDir, rel).apply { parentFile?.mkdirs() })
            PageBackground(rel, pageHeightFor(o.outWidth, o.outHeight))
        } catch (e: Exception) {
            null
        }
    }

    /** Copies any file as-is, keeping its name and type. */
    suspend fun file(ctx: Context, uri: Uri, fallbackName: String = "File"): Attachment? = withContext(Dispatchers.IO) {
        try {
            val cr = ctx.contentResolver
            var name: String? = null
            var size = -1L
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (ni >= 0) name = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE); if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val ext = (name?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
                ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin").lowercase()
            val display = name ?: "$fallbackName.$ext"
            val id = newId()
            val rel = "$FILES/$id.$ext"
            val out = File(ctx.filesDir, rel).apply { parentFile?.mkdirs() }
            val copied = cr.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return@withContext null
            Attachment(id = id, name = display, mime = mime, size = if (size >= 0) size else copied, path = rel)
        } catch (e: Exception) {
            null
        }
    }

    /** A temporary file for the camera to write into. */
    fun cameraTarget(ctx: Context): Uri {
        val f = File(ctx.cacheDir, "camera/${newId()}.jpg").apply { parentFile?.mkdirs() }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    fun clearCameraTemp(ctx: Context) {
        File(ctx.cacheDir, "camera").listFiles()?.forEach { it.delete() }
    }

    /** A link other apps can open (read-only) for one of our saved files. */
    fun shareUri(ctx: Context, relativePath: String): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(ctx.filesDir, relativePath))

    /**
     * Which of the picked items are photos in the phone's gallery (so they can be removed from it).
     * Items picked from Drive, Downloads etc. aren't included. Needs Android 11+.
     */
    fun galleryItems(ctx: Context, picked: List<Uri>): List<Uri> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        return picked.mapNotNull { u ->
            try {
                if (u.authority == MediaStore.AUTHORITY) u else MediaStore.getMediaUri(ctx, u)
            } catch (e: Exception) {
                null
            }
        }
    }
}
