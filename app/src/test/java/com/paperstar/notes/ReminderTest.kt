package com.paperstar.notes

import com.paperstar.notes.data.ReminderTimes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class ReminderTest {
    private val zone = ZoneId.of("Asia/Manila")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    // Saturday, Nov 14 2026, 4:05 PM
    private val now = ms(2026, 11, 14, 16, 5)

    @Test fun labels() {
        fun label(t: Long) = ReminderTimes.label(t, now, zone, Locale.US)
        assertEquals("Today, 8:00 PM", label(ms(2026, 11, 14, 20)))
        assertEquals("Tomorrow, 7:30 AM", label(ms(2026, 11, 15, 7, 30)))
        assertEquals("Yesterday, 9:00 AM", label(ms(2026, 11, 13, 9)))
        assertEquals("Fri, Nov 20, 3:00 PM", label(ms(2026, 11, 20, 15)))
        assertEquals("Mon, Jan 4, 2027, 8:00 AM", label(ms(2027, 1, 4, 8)))
    }

    @Test fun presetsInTheAfternoon() {
        val p = ReminderTimes.presets(now, zone)
        assertEquals(listOf("Tonight, 8:00 PM", "Tomorrow morning, 7:00 AM", "Next Saturday, 9:00 AM"), p.map { it.label })
        assertEquals(ms(2026, 11, 14, 20), p[0].at)
        assertEquals(ms(2026, 11, 15, 7), p[1].at)
        assertEquals(ms(2026, 11, 21, 9), p[2].at) // next Saturday, not today
    }

    @Test fun midweekSaysSaturday() {
        val wed = ms(2026, 11, 18, 10)
        assertEquals("Saturday, 9:00 AM", ReminderTimes.presets(wed, zone).last().label)
        assertEquals(ms(2026, 11, 21, 9), ReminderTimes.presets(wed, zone).last().at)
    }

    @Test fun noTonightWhenItIsLate() {
        val late = ms(2026, 11, 13, 19, 45) // Friday evening
        // Saturday is tomorrow, so it isn't offered twice
        assertEquals(listOf("Tomorrow morning, 7:00 AM"), ReminderTimes.presets(late, zone).map { it.label })
    }

    @Test fun combinesPickedDateWithTime() {
        val utcMidnight = LocalDateTime.of(2026, 11, 20, 0, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(ms(2026, 11, 20, 15, 30), ReminderTimes.combine(utcMidnight, 15, 30, zone))
    }
}
