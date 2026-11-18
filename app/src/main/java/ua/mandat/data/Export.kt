package ua.mandat.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Turns Mandat's records into open formats other apps understand: vCard for contacts, iCalendar
 * for events, CSV for tables, Markdown for notes and dossiers. Text only, built in memory;
 * nothing is written to the phone here.
 */
object Export {

    // ── vCard 3.0 ──

    private fun vEsc(s: String) = s.replace("\r\n", "\n").replace('\r', '\n').replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    /** Lines longer than 75 bytes are folded, as the format asks. */
    private fun fold(line: String): String {
        val out = StringBuilder()
        var count = 0
        var i = 0
        while (i < line.length) {
            // One whole character at a time, so an emoji (two chars) is never split
            val cp = line.codePointAt(i)
            val n = Character.charCount(cp)
            val len = when { cp < 0x80 -> 1; cp < 0x800 -> 2; cp < 0x10000 -> 3; else -> 4 }
            if (count + len > 74) { out.append("\r\n "); count = 1 }
            out.appendCodePoint(cp)
            count += len
            i += n
        }
        return out.toString()
    }

    data class VContact(val name: String, val phones: List<String>, val emails: List<String>, val org: String, val title: String,
                        val note: String, val categories: List<String>)

    fun vcard(list: List<VContact>): String = buildString {
        for (c in list) {
            append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            append(fold("FN:" + vEsc(c.name))).append("\r\n")
            append(fold("N:" + vEsc(c.name) + ";;;;")).append("\r\n")
            c.phones.forEach { append(fold("TEL;TYPE=CELL:" + vEsc(it))).append("\r\n") }
            c.emails.forEach { append(fold("EMAIL:" + vEsc(it))).append("\r\n") }
            if (c.org.isNotBlank()) append(fold("ORG:" + vEsc(c.org))).append("\r\n")
            if (c.title.isNotBlank()) append(fold("TITLE:" + vEsc(c.title))).append("\r\n")
            if (c.note.isNotBlank()) append(fold("NOTE:" + vEsc(c.note))).append("\r\n")
            if (c.categories.isNotEmpty()) append(fold("CATEGORIES:" + c.categories.joinToString(",") { vEsc(it) })).append("\r\n")
            append("END:VCARD\r\n")
        }
    }

    fun personToV(p: Person, categories: List<String>) = VContact(
        p.name, phonesIn(p.contact), emailsIn(p.contact), p.organization, p.jobTitle, p.notes, categories,
    )

    fun contactToV(c: Contact) = VContact(
        c.name, c.phones.lines().filter { it.isNotBlank() }, c.emails.lines().filter { it.isNotBlank() }, c.organization, c.jobTitle,
        c.notes, listOfNotNull(c.label.ifBlank { null }),
    )

    // ── iCalendar ──

    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val dateOnly = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val localTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun ics(events: List<Event>, kindName: (String) -> String, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): String = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//notes//EN\r\nCALSCALE:GREGORIAN\r\n")
        // The zone the repeating timed events are written in, described once
        if ('/' in zone.id && events.any { Repeat.of(it.repeat) != null && it.startMin != null }) try { append(vtimezone(zone)) } catch (x: Exception) { }
        for (e in events) {
            append("BEGIN:VEVENT\r\n")
            // A random identifier: record numbers and creation times would show gaps and history
            append("UID:").append(java.util.UUID.randomUUID()).append("@local\r\n")
            append("DTSTAMP:").append(Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).format(utc)).append("\r\n")
            val repeat = Repeat.of(e.repeat)
            // A repeating event keeps its hour through summer and winter time only when written in its own time zone
            val inZone = repeat != null && e.startMin != null && '/' in zone.id
            fun stamp(local: LocalDateTime) = if (inZone) local.format(localTime) else local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(utc)
            val tz = if (inZone) ";TZID=" + zone.id else ""
            if (e.startMin == null) {
                append("DTSTART;VALUE=DATE:").append(LocalDate.ofEpochDay(e.day).format(dateOnly)).append("\r\n")
                append("DTEND;VALUE=DATE:").append(LocalDate.ofEpochDay(e.lastDay + 1).format(dateOnly)).append("\r\n")
            } else {
                val start = LocalDate.ofEpochDay(e.day).atStartOfDay().plusMinutes(e.startMin.toLong())
                val end = LocalDate.ofEpochDay(e.lastDay).atStartOfDay().plusMinutes((e.endMin ?: (e.startMin + 60)).toLong())
                append("DTSTART").append(tz).append(":").append(stamp(start)).append("\r\n")
                append("DTEND").append(tz).append(":").append(stamp(maxOf(end, start.plusMinutes(15)))).append("\r\n")
            }
            if (repeat != null) {
                append(fold("RRULE:" + rrule(e, zone))).append("\r\n")
                val skipped = Repeats.skipped(e).filter { it >= e.day }.sorted()
                if (skipped.isNotEmpty()) append(fold(
                    if (e.startMin == null) "EXDATE;VALUE=DATE:" + skipped.joinToString(",") { LocalDate.ofEpochDay(it).format(dateOnly) }
                    else "EXDATE" + tz + ":" + skipped.joinToString(",") { stamp(LocalDate.ofEpochDay(it).atStartOfDay().plusMinutes(e.startMin.toLong())) },
                )).append("\r\n")
            }
            append(fold("SUMMARY:" + vEsc(e.title))).append("\r\n")
            if (e.place.isNotBlank()) append(fold("LOCATION:" + vEsc(e.place))).append("\r\n")
            val desc = listOf(e.notes, e.outcome.ifBlank { "" }.let { if (it.isEmpty()) "" else "✓ $it" }).filter { it.isNotBlank() }.joinToString("\n")
            if (desc.isNotBlank()) append(fold("DESCRIPTION:" + vEsc(desc))).append("\r\n")
            append(fold("CATEGORIES:" + vEsc(kindName(e.kind)))).append("\r\n")
            append("END:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }

    /**
     * The rule of repeating of [e] as calendars write it (the part after "RRULE:"), or null for an
     * event that happens once. In a month without that day the last day is taken, as Mandat itself
     * counts; the end is written as calendars expect it for a whole-day and for a timed event.
     */
    /** Weekdays as calendars write them, Monday first. */
    private val ICS_DAYS = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

    /**
     * The time zone itself, for a calendar that does not know it by name (older Outlook): its
     * standard and summer offsets and the yearly rule of the change, from the phone's own rules.
     */
    internal fun vtimezone(zone: ZoneId): String = buildString {
        val rules = zone.rules
        append("BEGIN:VTIMEZONE\r\nTZID:").append(zone.id).append("\r\n")
        val changes = rules.transitionRules
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        fun offset(o: ZoneOffset): String {
            val sec = o.totalSeconds
            val sign = if (sec < 0) "-" else "+"
            val a = kotlin.math.abs(sec)
            return sign + String.format(java.util.Locale.ROOT, "%02d%02d", a / 3600, a % 3600 / 60)
        }
        if (changes.isEmpty()) {
            // (a zone without a yearly rule: its standard offset, not whatever holds on the day of export)
            val o = rules.getStandardOffset(Instant.now())
            append("BEGIN:STANDARD\r\nDTSTART:19700101T000000\r\nTZOFFSETFROM:").append(offset(o)).append("\r\nTZOFFSETTO:").append(offset(o)).append("\r\nEND:STANDARD\r\n")
        } else for (r in changes) {
            val t = r.createTransition(1970)
            val part = if (t.offsetAfter.totalSeconds > r.standardOffset.totalSeconds) "DAYLIGHT" else "STANDARD"
            val day = r.dayOfWeek?.let { d ->
                val name = ICS_DAYS[d.value - 1]
                when {
                    // "The last Sunday" or "the first Sunday on or after the 25th" (the same in a month of 31 days)
                    r.dayOfMonthIndicator < 0 -> ";BYDAY=-1$name"
                    else -> ";BYMONTHDAY=" + (r.dayOfMonthIndicator..minOf(r.dayOfMonthIndicator + 6, 31)).joinToString(",") + ";BYDAY=$name"
                }
            } ?: ";BYMONTHDAY=" + r.dayOfMonthIndicator
            append("BEGIN:").append(part).append("\r\n")
            append("DTSTART:").append(t.dateTimeBefore.format(fmt)).append("\r\n")
            append("TZOFFSETFROM:").append(offset(t.offsetBefore)).append("\r\n")
            append("TZOFFSETTO:").append(offset(t.offsetAfter)).append("\r\n")
            append("RRULE:FREQ=YEARLY;BYMONTH=").append(r.month.value).append(day).append("\r\n")
            append("END:").append(part).append("\r\n")
        }
        append("END:VTIMEZONE\r\n")
    }

    fun rrule(e: Event, zone: ZoneId = ZoneId.systemDefault()): String? {
        val repeat = Repeat.of(e.repeat) ?: return null
        val first = LocalDate.ofEpochDay(e.day)
        val rule = StringBuilder("FREQ=").append(when (repeat) {
            Repeat.DAILY -> "DAILY"
            Repeat.WEEKLY -> "WEEKLY"
            Repeat.BIWEEKLY -> "WEEKLY;INTERVAL=2"
            Repeat.MONTHLY -> "MONTHLY" + when {
                first.dayOfMonth == 31 -> ";BYMONTHDAY=-1"
                first.dayOfMonth > 28 -> ";BYMONTHDAY=" + (28..first.dayOfMonth).joinToString(",") + ";BYSETPOS=-1"
                else -> ""
            }
            Repeat.YEARLY -> "YEARLY" + if (first.monthValue == 2 && first.dayOfMonth == 29) ";BYMONTH=2;BYMONTHDAY=28,29;BYSETPOS=-1" else ""
            Repeat.DAYS -> "WEEKLY;BYDAY=" + Repeat.daysOf(e.repeat).ifEmpty { setOf(first.dayOfWeek.value) }.joinToString(",") { ICS_DAYS[it - 1] }
            // (a fifth weekday is taken as the last one, as Mandat counts it)
            Repeat.MONTHLY_NTH -> "MONTHLY;BYDAY=" + ((first.dayOfMonth - 1) / 7 + 1).let { if (it == 5) -1 else it } + ICS_DAYS[first.dayOfWeek.value - 1]
            Repeat.MONTHLY_LAST -> "MONTHLY;BYDAY=-1" + ICS_DAYS[first.dayOfWeek.value - 1]
        })
        e.repeatUntil?.let { u ->
            rule.append(";UNTIL=").append(if (e.startMin == null) LocalDate.ofEpochDay(u).format(dateOnly)
                else LocalDate.ofEpochDay(u).atTime(23, 59, 59).atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(utc))
        }
        return rule.toString()
    }

    // ── CSV ──

    private val plainNumber = Regex("-?\\d+(\\.\\d+)?")

    /**
     * One CSV field: quoted when needed; a leading =,+,-,@ is neutralised so spreadsheets never run
     * it as a formula. A plain number such as -12.00 stays a number.
     */
    fun csvField(s: String): String {
        val safe = if (s.isNotEmpty() && s[0] in "=+-@\t\r" && !plainNumber.matches(s)) "'$s" else s
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** A table with a header row; UTF-8 with a byte-order mark so spreadsheet apps read Cyrillic right. */
    fun csv(header: List<String>, rows: List<List<String>>): String = buildString {
        append('﻿')
        append(header.joinToString(",") { csvField(it) }).append("\r\n")
        rows.forEach { r -> append(r.joinToString(",") { csvField(it) }).append("\r\n") }
    }

    fun day(d: Long?): String = d?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()
    fun moment(ms: Long?): String = ms?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).withNano(0).toString().replace('T', ' ') }.orEmpty()
    fun money(minor: Long): String = (if (minor < 0) "-" else "") + (kotlin.math.abs(minor) / 100).toString() + "." + "%02d".format(kotlin.math.abs(minor) % 100)

    // ── Markdown ──

    /** Escapes characters that would change the meaning of Markdown, including links, images and HTML. */
    fun md(s: String): String {
        val out = StringBuilder(s.length + 8)
        for (ch in s) {
            if (ch in MD_SPECIAL) out.append('\\')
            out.append(ch)
        }
        // A line that would start a heading underline, a list or a quote stays plain text
        return out.toString().lineSequence().joinToString("\n") { line ->
            val indent = line.takeWhile { it == ' ' }
            val l = line.substring(indent.length)
            val number = LIST_NUMBER.find(l)
            indent + when {
                l.isNotEmpty() && l[0] in "-+=~" -> "\\$l"
                // A backslash works only before punctuation: "1\." rather than "\1."
                number != null -> l.substring(0, number.range.last) + "\\" + l.substring(number.range.last)
                else -> l
            }
        }
    }

    private val LIST_NUMBER = Regex("^\\d+[.)]")

    private const val MD_SPECIAL = "\\*_#`<>[]!"
}

/**
 * Reads events from an .ics file: title, day and time (turned into this phone's time), place,
 * notes. A rule of repeating is kept when it is one Mandat can keep (every day, every week or
 * two, every month, every year; until a day; with days left out); with any other rule only the
 * first time is taken.
 */
object IcsReader {
    data class Item(
        val title: String, val day: Long, val endDay: Long?, val startMin: Int?, val endMin: Int?, val place: String, val notes: String,
        val repeat: String? = null, val repeatUntil: Long? = null, val skipDays: String = "",
    )

    const val MAX_EVENTS = 5_000

    fun read(text: String, zone: ZoneId = ZoneId.systemDefault()): List<Item> {
        val out = ArrayList<Item>()
        // Whose event each item is, and the days on which a repeating event was moved or changed on
        // its own (those come as events of their own, so the repeating one must not come then too)
        val ids = ArrayList<String?>()
        val changed = HashMap<String, MutableSet<Long>>()
        var cur: MutableMap<String, Pair<String, String>>? = null
        for (l in unfold(text)) {
            if (out.size >= MAX_EVENTS) break
            when {
                l.equals("BEGIN:VEVENT", true) -> cur = HashMap()
                l.equals("END:VEVENT", true) -> {
                    cur?.let { p ->
                        val uid = p["UID"]?.second?.trim()?.takeIf { it.isNotEmpty() }
                        toItem(p, zone)?.let {
                            out.add(it); ids.add(uid)
                            // Only a changed time that was itself taken: one that could not be read leaves the day in the series
                            val day = p["RECURRENCE-ID"]?.let { (h, v) -> when_(h, v, zone)?.first }
                            if (uid != null && day != null) changed.getOrPut(uid) { HashSet() }.add(day)
                        }
                    }
                    cur = null
                }
                cur != null && ':' in l -> {
                    val head = l.substringBefore(':')
                    val name = head.substringBefore(';').uppercase()
                    val value = l.substringAfter(':')
                    val had = cur[name]
                    if (had == null) cur[name] = head to value
                    // Days left out may come one per line: all of them count
                    // (up to the longest line taken; past that nothing is copied again)
                    else if (name == "EXDATE" && had.second.length < MAX_LINE) cur[name] = had.first to (had.second + "," + value).take(MAX_LINE)
                }
            }
        }
        return out.mapIndexed { i, item ->
            val more = ids[i]?.let(changed::get)
            if (item.repeat == null || more.isNullOrEmpty()) item
            else item.copy(skipDays = (item.skipDays.split(',').mapNotNull { it.toLongOrNull() } + more).distinct().sorted().takeLast(MAX_SKIP_DAYS).joinToString(","))
        }
    }

    /** Longest logical line kept; anything beyond is dropped, so a hostile file cannot grow one line forever. */
    private const val MAX_LINE = 100_000

    /** Joins continuation lines (starting with a space or tab) in one pass, in linear time. */
    internal fun unfold(text: String): Sequence<String> = sequence {
        val cur = StringBuilder()
        var has = false
        for (raw in text.lineSequence()) {
            val l = raw.removeSuffix("\r")
            if ((l.startsWith(" ") || l.startsWith("\t")) && has) {
                if (cur.length < MAX_LINE) cur.append(l, 1, minOf(l.length, 1 + MAX_LINE - cur.length))
            } else {
                if (has) yield(cur.toString())
                cur.setLength(0)
                cur.append(l, 0, minOf(l.length, MAX_LINE))
                has = true
            }
        }
        if (has) yield(cur.toString())
    }

    private fun unescape(s: String) = VCard.unescape(s).take(MAX_NOTE_LENGTH)

    /** A date or date-time with its parameters: the calendar day and minutes after midnight (null for a whole day). */
    private fun when_(head: String, value: String, zone: ZoneId): Pair<Long, Int?>? = try {
        val v = value.trim()
        if (v.length == 8 || head.contains("VALUE=DATE", true) && !v.contains('T')) {
            LocalDate.parse(v.take(8), DateTimeFormatter.BASIC_ISO_DATE).toEpochDay() to null
        } else {
            val local = LocalDateTime.parse(v.take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            val tz = Regex("TZID=([^;:]+)").find(head)?.groupValues?.get(1)?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            val at = when {
                v.endsWith("Z") -> local.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime()
                tz != null -> local.atZone(tz).withZoneSameInstant(zone).toLocalDateTime()
                else -> local
            }
            at.toLocalDate().toEpochDay() to (at.hour * 60 + at.minute)
        }
    } catch (e: Exception) {
        null
    }

    private fun toItem(p: Map<String, Pair<String, String>>, zone: ZoneId): Item? {
        val (sh, sv) = p["DTSTART"] ?: return null
        val start = when_(sh, sv, zone) ?: return null
        val end = p["DTEND"]?.let { (h, v) -> when_(h, v, zone) }
        val title = unescape(p["SUMMARY"]?.second.orEmpty()).trim().take(500).ifEmpty { "—" }
        val (endDay, endMin) = when {
            end == null -> null to null
            start.second == null -> (end.first - 1).takeIf { it > start.first } to null // DTEND of a date is exclusive
            else -> end.first.takeIf { it > start.first } to end.second
        }
        // Days beyond reason (a broken or hostile file) are not taken: no event, or only its first day
        if (start.first !in EVENT_MIN_DAY..EVENT_MAX_DAY) return null
        val span = endDay?.takeIf { it - start.first <= EVENT_MAX_SPAN }
        val rule = repeatOf(p["RRULE"]?.second, start.first, zone)
        val skip = if (rule == null) "" else p["EXDATE"]?.let { (h, v) -> v.split(',').take(MAX_SKIP_DAYS).mapNotNull { when_(h, it, zone)?.first } }.orEmpty().joinToString(",")
        return Item(title, start.first, span, start.second, if (start.second == null || span == null && endDay != null) null else endMin,
            unescape(p["LOCATION"]?.second.orEmpty()).take(500), unescape(p["DESCRIPTION"]?.second.orEmpty()),
            repeat = rule?.let { keyOf(p["RRULE"]?.second, start.first, it.first) }, repeatUntil = rule?.second, skipDays = skip)
    }

    /** The key to keep for a rule [repeatOf] took as [kind]: with its weekdays for [Repeat.DAYS]. */
    private fun keyOf(rule: String?, firstDay: Long, kind: Repeat): String? =
        if (kind != Repeat.DAYS) kind.name
        else Repeat.key(kind, byDays(rule).orEmpty().mapNotNull { (n, d) -> d.takeIf { n == null } }.toSet(), firstDay)

    /** BYDAY of a rule as (number or null, weekday 1..7) pairs; null when there is none or it cannot be read. */
    private fun byDays(rule: String?): List<Pair<Int?, Int>>? {
        val v = rule.orEmpty().split(';').firstOrNull { it.trim().uppercase().startsWith("BYDAY=") }?.substringAfter('=') ?: return null
        return v.split(',').map { x ->
            val t = x.trim().uppercase()
            val day = ICS_NAMES.indexOf(t.takeLast(2)).takeIf { it >= 0 } ?: return null
            val n = t.dropLast(2).takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: return null }
            n to day + 1
        }
    }
    private val ICS_NAMES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

    /**
     * A rule of repeating that Mandat can keep, with its last day if it has one; null for any other
     * (a number of times, every third week, the second Tuesday of every other month): then the
     * event is taken once.
     */
    internal fun repeatOf(rule: String?, firstDay: Long, zone: ZoneId): Pair<Repeat, Long?>? = try {
        val parts = rule.orEmpty().split(';').mapNotNull { x -> x.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim().uppercase() to it[1].trim() } }.toMap()
        val every = parts["INTERVAL"]?.toIntOrNull() ?: 1
        val first = LocalDate.ofEpochDay(firstDay)
        val known = setOf("FREQ", "INTERVAL", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "BYSETPOS", "WKST")
        val freq = parts["FREQ"]?.uppercase()
        val byDay = parts["BYDAY"]
        val monthDays = parts["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
        // "The last day of the month" in the two ways it is written, when the event itself begins on such a day
        val lastOfMonth = (parts["BYSETPOS"] == "-1" && monthDays != null && monthDays.isNotEmpty() && monthDays == (28..first.dayOfMonth).toList()) ||
            (parts["BYSETPOS"] == null && monthDays == listOf(-1) && first.dayOfMonth == 31)
        // Calendars leave out a month or a year that has no such day; Mandat would take its last day instead.
        // So the 29th to the 31st is kept only when the rule itself says "the last day"
        val needsLast = (freq == "MONTHLY" && byDay == null && first.dayOfMonth > 28) || (freq == "YEARLY" && first.monthValue == 2 && first.dayOfMonth == 29)
        val repeat = when {
            parts.keys.any { it !in known } -> null
            // Weekdays: the event's own must be among them (several of them every week, or a numbered one every month)
            byDay != null && byDays(rule)?.any { it.second == first.dayOfWeek.value } != true -> null
            parts["BYSETPOS"] != null && !lastOfMonth -> null
            monthDays != null && !lastOfMonth && monthDays != listOf(first.dayOfMonth) -> null
            needsLast && !lastOfMonth -> null
            // "Only in such a month" belongs to a yearly rule, and must be the event's own month
            parts["BYMONTH"] != null && (freq != "YEARLY" || parts["BYMONTH"] != first.monthValue.toString()) -> null
            else -> when (freq) {
                // "Every working day" is written as daily on some weekdays
                "DAILY" -> if (byDay != null) byDays(rule)?.takeIf { d -> every == 1 && monthDays == null && d.all { it.first == null } }?.let { Repeat.DAYS }
                    else if (monthDays != null) null else when (every) { 1 -> Repeat.DAILY; 7 -> Repeat.WEEKLY; 14 -> Repeat.BIWEEKLY; else -> null }
                "WEEKLY" -> if (monthDays != null) null else {
                    val days = byDays(rule)
                    when {
                        days != null && days.any { it.first != null } -> null
                        days != null && days.map { it.second }.toSet().size > 1 -> if (every == 1) Repeat.DAYS else null
                        else -> when (every) { 1 -> Repeat.WEEKLY; 2 -> Repeat.BIWEEKLY; else -> null }
                    }
                }
                "MONTHLY" -> if (byDay != null) {
                    // "The second Tuesday" or "the last Tuesday": one numbered weekday, the event's own place in its month
                    val days = byDays(rule).orEmpty()
                    val place = (first.dayOfMonth - 1) / 7 + 1
                    val last = first.plusWeeks(1).monthValue != first.monthValue
                    val n = days.singleOrNull()?.first
                    when {
                        every != 1 || monthDays != null || parts["BYSETPOS"] != null || n == null -> null
                        n == -1 && last -> Repeat.MONTHLY_LAST
                        n == place && n in 1..4 -> Repeat.MONTHLY_NTH
                        else -> null
                    }
                } else when (every) { 1 -> Repeat.MONTHLY; 12 -> if (needsLast) null else Repeat.YEARLY; else -> null }
                "YEARLY" -> if (byDay != null || every != 1) null else Repeat.YEARLY
                else -> null
            }
        }
        repeat?.let { r ->
            val until = parts["UNTIL"]?.let { u ->
                if (u.length >= 15 && u.endsWith("Z")) LocalDateTime.parse(u.take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate().toEpochDay()
                else LocalDate.parse(u.take(8), DateTimeFormatter.BASIC_ISO_DATE).toEpochDay()
            }
            r to until?.takeIf { it in EVENT_MIN_DAY..EVENT_MAX_DAY }
        }
    } catch (e: Exception) {
        null
    }
}

/** Reads a CSV of contacts (comma or semicolon), recognising common header names in Ukrainian and English. */
object ContactCsv {
    fun rows(text: String): List<List<String>> {
        val t = text.removePrefix("﻿")
        val firstLine = t.lineSequence().firstOrNull().orEmpty()
        val sep = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < t.length) {
            val ch = t[i]
            when {
                quoted && ch == '"' && i + 1 < t.length && t[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == sep -> { if (row.size < MAX_COLUMNS) row += cell.toString(); cell.setLength(0) }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < t.length && t[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    if (row.any { it.isNotBlank() }) out += row
                    row = ArrayList()
                    if (out.size > VCard.MAX_CONTACTS) break
                }
                else -> if (cell.length < 5_000) cell.append(ch)
            }
            i++
        }
        row += cell.toString()
        if (row.any { it.isNotBlank() }) out += row
        return out
    }

    /** More columns than any real contact table has; the rest of a row is ignored. */
    const val MAX_COLUMNS = 200

    private val NAME = listOf("name", "full name", "ім'я", "імʼя", "піб", "прізвище та ім'я", "назва")
    private val PHONE = listOf("phone", "mobile", "tel", "телефон", "мобільний", "номер")
    private val EMAIL = listOf("email", "e-mail", "mail", "пошта", "електронна пошта")
    private val ORG = listOf("organization", "organisation", "company", "організація", "компанія", "установа")
    private val TITLE = listOf("title", "job title", "position", "посада")
    private val NOTE = listOf("note", "notes", "примітка", "нотатки", "коментар")
    private val GROUP = listOf("group", "label", "category", "група", "категорія", "список")

    fun contacts(text: String, now: Long = System.currentTimeMillis()): List<Contact> {
        val all = rows(text)
        if (all.size < 2) return emptyList()
        val header = all.first().map { it.trim().lowercase() }
        fun col(names: List<String>) = header.indexOfFirst { h -> names.any { h == it || h.startsWith("$it ") } }
        val iName = col(NAME).takeIf { it >= 0 } ?: 0
        val iPhones = header.indices.filter { h -> PHONE.any { header[h] == it || header[h].startsWith("$it ") || header[h].startsWith(it) } }
        val iEmails = header.indices.filter { h -> EMAIL.any { header[h] == it || header[h].startsWith(it) } }
        val iOrg = col(ORG); val iTitle = col(TITLE); val iNote = col(NOTE); val iGroup = col(GROUP)
        fun at(r: List<String>, i: Int) = if (i in r.indices) r[i].trim() else ""
        return all.drop(1).mapNotNull { r ->
            cleanContact(
                Contact(
                    name = at(r, iName), phones = iPhones.map { at(r, it) }.filter { it.isNotEmpty() }.joinToString("\n"),
                    emails = iEmails.map { at(r, it) }.filter { it.isNotEmpty() }.joinToString("\n"),
                    organization = at(r, iOrg), jobTitle = at(r, iTitle), notes = at(r, iNote), label = at(r, iGroup),
                    sortKey = "", createdAt = now,
                ),
            )
        }.take(VCard.MAX_CONTACTS)
    }
}

/** Splits a text file into Inbox notes at blank lines (at most [max]). */
fun notesFromText(text: String, max: Int = 500): List<String> =
    text.removePrefix("﻿").replace("\r\n", "\n").split(Regex("\n\\s*\n")).mapNotNull { cleanInboxText(it) }.take(max)
