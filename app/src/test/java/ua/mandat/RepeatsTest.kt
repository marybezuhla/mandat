package ua.mandat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.mandat.data.Event
import ua.mandat.data.EventKind
import ua.mandat.data.EventPerson
import ua.mandat.data.Export
import ua.mandat.data.HiddenNames
import ua.mandat.data.Hiding
import ua.mandat.data.IcsReader
import ua.mandat.data.IdRef
import ua.mandat.data.Repeat
import ua.mandat.data.Repeats
import ua.mandat.data.cleanEvent
import ua.mandat.data.reminderDays
import java.time.LocalDate
import java.time.ZoneId

/** Events that come again and again: their times, what a damaged record does, and the calendar file. */
class RepeatsTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    private fun event(day: Long, repeat: Repeat? = null, until: Long? = null, skip: String = "", endDay: Long? = null, start: Int? = null, remind: Boolean = false) =
        Event(id = 7, title = "Вигадана подія", day = day, endDay = endDay, startMin = start, endMin = start?.plus(60), kind = EventKind.MEETING.name,
            createdAt = 0, updatedAt = 0, repeat = repeat?.name, repeatUntil = until, skipDays = skip, remind = remind)

    @Test fun anEventThatHappensOnceHasOneTime() {
        val e = event(100)
        assertEquals(listOf(100L), Repeats.starts(e, 90, 110))
        assertEquals(emptyList<Long>(), Repeats.starts(e, 101, 110))
        assertEquals(listOf(e), Repeats.expand(listOf(e), 90, 110))
        assertEquals(100L, Repeats.next(e, 100))
        assertNull(Repeats.next(e, 101))
        assertEquals(e, Repeats.near(e, 500))
    }

    @Test fun weeklyAndEveryTwoWeeks() {
        val monday = day(2026, 1, 5)
        assertEquals(listOf(monday, monday + 7, monday + 14), Repeats.starts(event(monday, Repeat.WEEKLY), monday, monday + 20))
        assertEquals(listOf(monday, monday + 14), Repeats.starts(event(monday, Repeat.BIWEEKLY), monday, monday + 20))
        // Far from the first time: found without walking there, and never before the first time
        assertEquals(listOf(monday + 7 * 500), Repeats.starts(event(monday, Repeat.WEEKLY), monday + 7 * 500 - 3, monday + 7 * 500 + 3))
        assertEquals(emptyList<Long>(), Repeats.starts(event(monday, Repeat.WEEKLY), monday - 30, monday - 1))
        assertEquals(listOf(monday, monday + 1, monday + 2), Repeats.starts(event(monday, Repeat.DAILY), monday - 5, monday + 2))
    }

    @Test fun untilAndSkippedDays() {
        val first = day(2026, 3, 2)
        val e = event(first, Repeat.WEEKLY, until = first + 21, skip = "${first + 7}, x,${first + 700}")
        assertEquals(listOf(first, first + 14, first + 21), Repeats.starts(e, first - 10, first + 100))
        assertEquals(first + 14, Repeats.next(e, first + 1))
        assertNull(Repeats.next(e, first + 22))
        // Over: shown at its last time
        assertEquals(first + 21, Repeats.near(e, first + 300).day)
        // Coming: shown at its coming time
        assertEquals(first + 14, Repeats.near(e, first + 8).day)
        assertEquals(setOf(first + 7, first + 14, first + 700), Repeats.skipped(Repeats.skipping(e, first + 14)))
    }

    @Test fun aMonthWithoutThatDayTakesItsLastDay() {
        val e = event(day(2026, 1, 31), Repeat.MONTHLY)
        assertEquals(
            listOf(day(2026, 1, 31), day(2026, 2, 28), day(2026, 3, 31), day(2026, 4, 30)),
            Repeats.starts(e, day(2026, 1, 1), day(2026, 4, 30)),
        )
        val leap = event(day(2024, 2, 29), Repeat.YEARLY)
        assertEquals(listOf(day(2025, 2, 28), day(2026, 2, 28), day(2028, 2, 29)), Repeats.starts(leap, day(2025, 1, 1), day(2028, 12, 31)).filter { it != day(2027, 2, 28) })
        // Far ahead in months and years
        assertEquals(listOf(day(2031, 5, 31)), Repeats.starts(e, day(2031, 5, 15), day(2031, 6, 1)))
    }

    @Test fun aTimeThatLastsSeveralDaysTouchesTheDaysItCovers() {
        val first = day(2026, 6, 1)
        val e = event(first, Repeat.MONTHLY, endDay = first + 2)
        // The window starts on the last day of the July time
        assertEquals(listOf(day(2026, 7, 1)), Repeats.starts(e, day(2026, 7, 3), day(2026, 7, 20)))
        val july = Repeats.expand(listOf(e), day(2026, 7, 3), day(2026, 7, 20)).single()
        assertEquals(day(2026, 7, 1), july.day)
        assertEquals(day(2026, 7, 3), july.endDay)
        assertEquals(7L, july.id)
        assertTrue(july.covers(day(2026, 7, 2)))
    }

    @Test fun eachTimeKeepsOneOffEventsAndCountsEveryTimeOfARepeatingOne() {
        val old = event(10)
        val weekly = event(1000, Repeat.WEEKLY).copy(id = 8)
        val all = Repeats.eachTime(listOf(old, weekly), 1000, 1020)
        assertEquals(listOf(10L, 1000L, 1007L, 1014L), all.map { it.day })
    }

    @Test fun aDamagedRecordNeverMakesAnEndlessList() {
        val e = event(0, Repeat.DAILY)
        assertEquals(Repeats.MAX_TIMES, Repeats.starts(e, 0, Long.MAX_VALUE).size)
        // Impossible days are simply no times
        assertEquals(emptyList<Long>(), Repeats.starts(event(Long.MAX_VALUE - 5, Repeat.MONTHLY), 0, Long.MAX_VALUE))
        assertEquals(emptyList<Long>(), Repeats.starts(event(100, Repeat.WEEKLY).copy(repeat = "EVERY_SECOND"), 200, 300))
    }

    @Test fun cleaningKeepsARepeatOnlyWhereItMakesSense() {
        val first = day(2026, 1, 5)
        // Longer than the gap between two times: saved without repeating
        assertNull(cleanEvent(event(first, Repeat.WEEKLY, endDay = first + 7))!!.repeat)
        assertEquals("WEEKLY", cleanEvent(event(first, Repeat.WEEKLY, endDay = first + 6))!!.repeat)
        // The end cannot come before the first time; skipped days only among its own days; none without a repeat
        assertNull(cleanEvent(event(first, Repeat.WEEKLY, until = first - 1))!!.repeatUntil)
        assertEquals("${first + 7}", cleanEvent(event(first, Repeat.WEEKLY, until = first + 30, skip = "${first - 7},${first + 7},${first + 70},abc"))!!.skipDays)
        val once = cleanEvent(event(first, until = first + 30, skip = "${first + 7}"))!!
        assertNull(once.repeatUntil)
        assertEquals("", once.skipDays)
        assertNull(cleanEvent(event(first).copy(repeat = "SOMETIMES"))!!.repeat)
    }

    @Test fun remindersComeForTheComingTimes() {
        val today = day(2026, 5, 10)
        val weekly = event(today - 70, Repeat.WEEKLY, remind = true)
        val days = reminderDays(emptyList(), emptyList(), emptyList(), today, listOf(weekly))
        assertEquals(today, days.first())
        assertTrue(days.all { it >= today && (it - weekly.day) % 7 == 0L })
        assertEquals(13, days.size) // today and twelve more weeks within ninety days
        // Over before today: nothing
        assertEquals(emptyList<Long>(), reminderDays(emptyList(), emptyList(), emptyList(), today, listOf(event(today - 70, Repeat.WEEKLY, until = today - 1, remind = true))))
    }

    @Test fun theCalendarFileKeepsTheRepeat() {
        val kyiv = ZoneId.of("Europe/Kyiv")
        val first = day(2026, 1, 31)
        val monthly = event(first, Repeat.MONTHLY, until = day(2026, 12, 31), skip = "${day(2026, 2, 28)}", start = 600)
        val weekly = event(day(2026, 3, 2), Repeat.BIWEEKLY).copy(title = "Цілий день")
        val once = event(day(2026, 3, 3), start = 540).copy(title = "Один раз")
        val text = Export.ics(listOf(monthly, weekly, once), { it }, kyiv, now = 0)
        assertTrue(text.contains("DTSTART;TZID=Europe/Kyiv:20260131T100000"))
        assertTrue(text.contains("RRULE:FREQ=MONTHLY;BYMONTHDAY=28,29,30,31;BYSETPOS=-1;UNTIL=20261231T215959Z"))
        assertTrue(text.contains("EXDATE;TZID=Europe/Kyiv:20260228T100000"))
        assertTrue(text.contains("RRULE:FREQ=WEEKLY;INTERVAL=2\r\n"))
        // An event that happens once is written as before, in universal time
        assertTrue(text.contains("DTSTART:20260303T070000Z"))
        val back = IcsReader.read(text, kyiv)
        assertEquals(3, back.size)
        assertEquals(listOf("MONTHLY", "BIWEEKLY", null), back.map { it.repeat })
        assertEquals(first, back[0].day)
        assertEquals(600, back[0].startMin)
        assertEquals(day(2026, 12, 31), back[0].repeatUntil)
        assertEquals("${day(2026, 2, 28)}", back[0].skipDays)
        assertNull(back[1].repeatUntil)
    }

    @Test fun aRuleMandatCannotKeepIsTakenOnce() {
        val zone = ZoneId.of("Europe/Kyiv")
        val monday = day(2026, 1, 5)
        fun rule(r: String?) = IcsReader.repeatOf(r, monday, zone)?.first
        assertEquals(Repeat.WEEKLY, rule("FREQ=WEEKLY;BYDAY=MO"))
        assertEquals(Repeat.WEEKLY, rule("FREQ=WEEKLY;WKST=SU"))
        assertEquals(Repeat.BIWEEKLY, rule("FREQ=WEEKLY;INTERVAL=2"))
        assertEquals(Repeat.YEARLY, rule("FREQ=YEARLY"))
        assertEquals(Repeat.MONTHLY, rule("FREQ=MONTHLY;BYMONTHDAY=5"))
        assertNull(rule("FREQ=WEEKLY;BYDAY=MO,WE,FR"))
        assertNull(rule("FREQ=WEEKLY;BYDAY=TU"))
        assertNull(rule("FREQ=WEEKLY;COUNT=10"))
        assertNull(rule("FREQ=WEEKLY;INTERVAL=3"))
        assertNull(rule("FREQ=MONTHLY;BYDAY=2TU"))
        assertNull(rule("FREQ=MONTHLY;BYMONTHDAY=15"))
        assertNull(rule("FREQ=HOURLY"))
        assertNull(rule(""))
        assertNull(rule(null))
        assertEquals(day(2026, 6, 30), IcsReader.repeatOf("FREQ=DAILY;UNTIL=20260630", monday, zone)?.second)
    }

    @Test fun aSensitivePersonHidesTheirEventsAndIsFoundByTheFullName() {
        // Marked people are hidden also when no matter is, and with the people known only through hidden matters
        assertEquals(setOf(20L), Hiding.people(emptySet(), listOf(IdRef(20, null)), marked = setOf(20L)))
        assertEquals(setOf(10L, 20L), Hiding.people(setOf(1L), listOf(IdRef(10, 1), IdRef(11, 2), IdRef(20, 2)), marked = setOf(20L)))
        assertEquals(setOf(5L), Hiding.events(setOf(20L), listOf(EventPerson(5, 20), EventPerson(6, 21))))
        assertEquals(emptySet<Long>(), Hiding.events(emptySet(), listOf(EventPerson(5, 20))))
        val names = HiddenNames(listOf("Вигадана Особа"))
        assertTrue(names.named("зустріч із Вигаданою Особою"))
        assertFalse(names.named("зустріч з Особою"))
    }
}
