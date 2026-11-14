package com.paperstar.notes.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.paperstar.notes.MainActivity
import com.paperstar.notes.PaperStarApp
import com.paperstar.notes.R
import com.paperstar.notes.data.AppData
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Note reminders (#27). Each note has at most one reminder time. An alarm fires a notification
 * at that time; tapping it opens the note.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    const val EXTRA_NOTE = "paperstar.noteId"
    private const val EXTRA_AT = "paperstar.at"

    /** A note to open, set when a reminder notification is tapped. */
    val openNote = MutableStateFlow<String?>(null)

    private fun alarmIntent(ctx: Context, noteId: String, at: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, noteId.hashCode(),
            Intent(ctx, ReminderReceiver::class.java).putExtra(EXTRA_NOTE, noteId).putExtra(EXTRA_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Makes the alarms match the notes: every future reminder gets an alarm (setting it again just
     * replaces it), and alarms for reminders that were removed are cancelled.
     */
    fun sync(ctx: Context, data: AppData, previous: Set<String>): Set<String> {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val want = data.notes.mapNotNull { n -> n.remindAt?.takeIf { it > now }?.let { n.id to it } }.toMap()
        for ((id, at) in want) {
            val pi = alarmIntent(ctx, id, at)
            // Exact when Android allows it; otherwise Android may deliver it a few minutes late.
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        for (id in previous - want.keys) am.cancel(alarmIntent(ctx, id, 0))
        return want.keys
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Note reminders you set in Paper Star"
            })
        }
    }

    internal fun show(ctx: Context, noteId: String) {
        val app = ctx.applicationContext as PaperStarApp
        val data = app.repo.data.value
        val note = data.note(noteId) ?: return
        val secret = data.folder(note.folderId)?.secret == true
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, noteId.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_NOTE, noteId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val jar = data.folder(note.folderId)?.name.orEmpty()
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            // The Secret jar never shows what's inside on the lock screen or in the shade.
            .setContentTitle(if (secret) "Paper Star reminder" else note.title.ifBlank { "Untitled note" })
            .setContentText(if (secret) "A note in your Secret jar" else note.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "Reminder from $jar")
            .setVisibility(if (secret) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(noteId.hashCode(), n) } catch (_: SecurityException) { /* notifications are off */ }
    }

    /** Called from the alarm: only notify if the note still wants a reminder at this time. */
    internal fun fire(ctx: Context, noteId: String, at: Long) {
        val data = (ctx.applicationContext as PaperStarApp).repo.data.value
        val want = data.note(noteId)?.remindAt ?: return
        if (kotlin.math.abs(want - at) > 60_000) return
        show(ctx, noteId)
    }

    internal fun atOf(intent: Intent) = intent.getLongExtra(EXTRA_AT, 0)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Reminders.EXTRA_NOTE) ?: return
        Reminders.fire(context, id, Reminders.atOf(intent))
    }
}

/** Alarms are cleared when the phone restarts or the time zone changes; set them again. */
class ReminderRestore : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PaperStarApp
        Reminders.sync(context, app.repo.data.value, emptySet())
    }
}
