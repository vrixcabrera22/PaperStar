package com.paperstar.notes.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Reminder times and labels (#27). Pure functions, so they can be tested without a phone. */
object ReminderTimes {
    private fun at(millis: Long, zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    private fun millis(t: LocalDateTime, zone: ZoneId): Long = t.atZone(zone).toInstant().toEpochMilli()

    /** "Today, 3:00 PM", "Tomorrow, 7:00 AM", "Fri, Nov 20, 3:00 PM", or with the year when it isn't this year. */
    fun label(whenMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val t = at(whenMs, zone)
        val today = at(nowMs, zone).toLocalDate()
        val time = t.format(DateTimeFormatter.ofPattern("h:mm a", locale))
        val day = when (t.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> t.format(DateTimeFormatter.ofPattern(if (t.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy", locale))
        }
        return "$day, $time"
    }

    /** A quick choice on the "Remind me" menu. */
    data class Preset(val label: String, val at: Long)

    /** Tonight at 8 (while it's still early enough), tomorrow at 7 in the morning, and the coming Saturday morning. */
    fun presets(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Preset> {
        val now = at(nowMs, zone)
        val today: LocalDate = now.toLocalDate()
        val out = mutableListOf<Preset>()
        val tonight = today.atTime(LocalTime.of(20, 0))
        if (now.plusMinutes(30) <= tonight) out += Preset("Tonight, 8:00 PM", millis(tonight, zone))
        out += Preset("Tomorrow morning, 7:00 AM", millis(today.plusDays(1).atTime(7, 0), zone))
        val sat = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        // On a Saturday, "Saturday" would mean today, so say "Next Saturday".
        val satName = if (today.dayOfWeek == DayOfWeek.SATURDAY) "Next Saturday" else "Saturday"
        if (sat != today.plusDays(1)) out += Preset("$satName, 9:00 AM", millis(sat.atTime(9, 0), zone))
        return out
    }

    /** Joins a date picked in the date picker (UTC midnight, as Material's picker gives it) with an hour and minute. */
    fun combine(dateUtcMs: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date = Instant.ofEpochMilli(dateUtcMs).atZone(ZoneId.of("UTC")).toLocalDate()
        return millis(date.atTime(hour, minute), zone)
    }
}
