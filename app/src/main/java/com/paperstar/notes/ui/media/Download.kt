package com.paperstar.notes.ui.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Something saved inside the app that can be downloaded to the phone. */
data class Downloadable(val file: File, val name: String, val mime: String)

private const val FOLDER = "Paper Star"

/** Copies [d] into the phone's Downloads/Paper Star folder (Android 10+, no permission needed). */
private suspend fun saveToDownloads(ctx: Context, d: Downloadable): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < 29) return@withContext false
    val resolver = ctx.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, d.name)
        put(MediaStore.Downloads.MIME_TYPE, d.mime.ifBlank { "application/octet-stream" })
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    var uri: Uri? = null
    try {
        uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        resolver.openOutputStream(uri)?.use { out -> d.file.inputStream().use { it.copyTo(out) } } ?: error("no stream")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        true
    } catch (e: Exception) {
        Log.w("Download", "Couldn't save ${d.name}", e)
        uri?.let { resolver.delete(it, null, null) }
        false
    }
}

private suspend fun copyTo(ctx: Context, d: Downloadable, target: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        ctx.contentResolver.openOutputStream(target, "wt")?.use { out -> d.file.inputStream().use { it.copyTo(out) } } != null
    } catch (e: Exception) { false }
}

/**
 * Returns a function that downloads a file to the phone. On Android 10 and newer it goes
 * straight to Downloads/Paper Star; on older phones Android's "save as" screen opens instead.
 */
@Composable
fun rememberDownloader(): (Downloadable) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf<Downloadable?>(null) }
    fun done(ok: Boolean, d: Downloadable, where: String) = Toast.makeText(
        ctx, if (ok) "Saved “${d.name}” to $where" else "“${d.name}” couldn't be saved.", Toast.LENGTH_SHORT,
    ).show()
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val d = waiting ?: return@rememberLauncherForActivityResult
        waiting = null
        if (uri != null) scope.launch { done(copyTo(ctx, d, uri), d, "your phone") }
    }
    return remember {
        { d: Downloadable ->
            if (Build.VERSION.SDK_INT >= 29) scope.launch { done(saveToDownloads(ctx, d), d, "Downloads › $FOLDER") }
            else { waiting = d; saveAs.launch(d.name) }
        }
    }
}

/** A file name that's safe on any phone or computer, like "Cell structure 2.jpg". */
fun safeFileName(base: String, ext: String): String {
    val clean = base.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").replace(Regex("\\s+"), " ").trim().take(80)
    return (clean.ifEmpty { "Paper Star" }) + ext
}
