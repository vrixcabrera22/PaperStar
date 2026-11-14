package com.paperstar.notes.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.paperstar.notes.data.AppData
import com.paperstar.notes.data.BackupZip
import com.paperstar.notes.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backups (#36). The backup is one zip file in a place the user picks with Android's own file
 * picker, usually Google Drive. Android's Drive app does the uploading, so no Google sign-in or
 * cloud setup is needed in Paper Star. After the first backup, the same file is updated.
 */
object Backup {
    private const val PREFS = "backup"
    private const val DAY = 24 * 60 * 60 * 1000L
    const val FILE_NAME = "Paper Star backup.zip"
    private val lock = Mutex()

    data class Status(
        val target: Uri? = null,
        val targetName: String? = null,
        val lastAt: Long = 0,
        val auto: Boolean = true,
        val error: String? = null,
        val running: Boolean = false,
    )

    val status = MutableStateFlow(Status())

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        status.value = Status(
            target = p.getString("uri", null)?.let(Uri::parse),
            targetName = p.getString("name", null),
            lastAt = p.getLong("last", 0),
            auto = p.getBoolean("auto", true),
            error = p.getString("error", null),
        )
    }

    private fun save(ctx: Context, s: Status) {
        status.value = s
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("uri", s.target?.toString()).putString("name", s.targetName)
            .putLong("last", s.lastAt).putBoolean("auto", s.auto).putString("error", s.error).apply()
    }

    fun setAuto(ctx: Context, on: Boolean) = save(ctx, status.value.copy(auto = on))

    /** Remembers a file picked with "save as", keeping permission to write to it later. */
    fun setTarget(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) { Log.w("Backup", "No lasting permission for $uri", e) }
        save(ctx, status.value.copy(target = uri, targetName = displayName(ctx, uri), error = null))
    }

    private fun displayName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) { null }

    /** Writes a backup to the chosen file. Returns null when it worked, or a message saying what went wrong. */
    suspend fun backUpNow(ctx: Context, repo: Repository): String? = lock.withLock {
        val target = status.value.target ?: return "Choose where to save backups first."
        status.value = status.value.copy(running = true)
        val err = withContext(Dispatchers.IO) {
            try {
                val out = ctx.contentResolver.openOutputStream(target, "wt") ?: error("Couldn't open the backup file.")
                out.use { BackupZip.write(it, repo.exportJson(), ctx.filesDir) }
                null
            } catch (e: Exception) {
                Log.w("Backup", "Backup failed", e)
                "The backup couldn't be saved. If the file was moved or deleted, choose a new place."
            }
        }
        save(ctx, status.value.copy(running = false, error = err, lastAt = if (err == null) System.currentTimeMillis() else status.value.lastAt))
        err
    }

    /** Backs up when automatic backups are on and the last one is a day old. Called when the app goes to the background. */
    fun autoIfDue(ctx: Context, repo: Repository, scope: CoroutineScope) {
        val s = status.value
        if (!s.auto || s.target == null || s.running) return
        if (System.currentTimeMillis() - s.lastAt < DAY) return
        scope.launch { backUpNow(ctx, repo) }
    }

    /** A backup unpacked and checked, waiting for the user to confirm the restore. */
    class Pending(val data: AppData, val folder: File)

    /** Step 1 of a restore: unpack the chosen backup into a temporary folder and read its notes. */
    suspend fun prepareRestore(ctx: Context, repo: Repository, uri: Uri): Result<Pending> = withContext(Dispatchers.IO) {
        val tmp = File(ctx.filesDir, "restore-tmp")
        tmp.deleteRecursively(); tmp.mkdirs()
        try {
            val json = ctx.contentResolver.openInputStream(uri)?.use { BackupZip.read(it, tmp) } ?: error("Couldn't open the file.")
            Result.success(Pending(repo.parse(json), tmp))
        } catch (e: Exception) {
            tmp.deleteRecursively()
            Result.failure(IllegalArgumentException("This file isn't a Paper Star backup, or it's damaged."))
        }
    }

    /** Step 2: swap in the backup's files and notes. Everything currently in the app is replaced. */
    suspend fun applyRestore(ctx: Context, repo: Repository, p: Pending) = withContext(Dispatchers.IO) {
        lock.withLock {
            for (folder in BackupZip.FOLDERS) {
                val live = File(ctx.filesDir, folder)
                live.deleteRecursively()
                File(p.folder, folder).takeIf { it.exists() }?.renameTo(live)
            }
            p.folder.deleteRecursively()
            repo.replaceAll(p.data)
        }
    }

    fun cancelRestore(p: Pending) { p.folder.deleteRecursively() }
}
