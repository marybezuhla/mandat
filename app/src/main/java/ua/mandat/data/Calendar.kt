package ua.mandat.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Something in the owner's own calendar: a meeting, a session, a trip, a reception of citizens.
 * Kept only inside Mandat's encrypted database. Days are calendar days (no time zone), so a
 * trip abroad does not move the date; the time of day is minutes after midnight.
 */
@Entity(tableName = "events", indices = [Index("day")])
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** First day (days since 1970-01-01). */
    val day: Long,
    /** Last day for something that lasts several days (a trip); null for one day. */
    val endDay: Long? = null,
    /** Start in minutes after midnight; null means the whole day. */
    val startMin: Int? = null,
    val endMin: Int? = null,
    /** A key of [EventKind]. */
    val kind: String,
    val place: String = "",
    val matterId: Long? = null,
    val notes: String = "",
    /** What came out of it, written afterwards. */
    val outcome: String = "",
    /** Remind on the day (the reminder itself says nothing about what). */
    val remind: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** A key of [Repeat] for an event that comes again and again ([day] is then its first time); null for one that happens once. */
    val repeat: String? = null,
    /** The last day on which it may come again; null for no end. */
    val repeatUntil: Long? = null,
    /** The days on which it does not come after all, as numbers separated by commas. */
    @androidx.room.ColumnInfo(defaultValue = "") val skipDays: String = "",
) {
    val lastDay get() = endDay?.takeIf { it > day } ?: day
    fun covers(d: Long) = d in day..lastDay
    val repeats: Boolean get() = Repeat.of(repeat) != null
}

/** How often an event comes again. Keys are stored. */
enum class Repeat {
    DAILY, WEEKLY, BIWEEKLY, MONTHLY, YEARLY;

    /** The shortest gap between two times, in days: an event that repeats must be shorter than this. */
    val minGap: Long get() = when (this) { DAILY -> 1; WEEKLY -> 7; BIWEEKLY -> 14; MONTHLY -> 28; YEARLY -> 365 }

    companion object {
        fun of(key: String?) = key?.let { k -> entries.firstOrNull { it.name == k } }
    }
}

/**
 * The times of a repeating event. A repeating event is one record: its [Event.day] is the first
 * time, and each later time is worked out here, never stored. A month without that day of the
 * month (the 31st, 29 February) takes its last day instead.
 */
object Repeats {
    /** No list of times is ever longer than this (ten years of every day), whatever a damaged record says. */
    const val MAX_TIMES = 3_700

    fun skipped(e: Event): Set<Long> = e.skipDays.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

    /** The first day of the time number [n] (0 is the event's own day), or null past the supported dates. */
    private fun start(e: Event, kind: Repeat, n: Long): Long? = try {
        when (kind) {
            Repeat.DAILY -> e.day + n
            Repeat.WEEKLY -> e.day + 7 * n
            Repeat.BIWEEKLY -> e.day + 14 * n
            Repeat.MONTHLY -> LocalDate.ofEpochDay(e.day).plusMonths(n).toEpochDay()
            Repeat.YEARLY -> LocalDate.ofEpochDay(e.day).plusYears(n).toEpochDay()
        }
    } catch (x: java.time.DateTimeException) { null }

    /** The first days of the times of [e] that touch the days [from]..[to], in order. One day for an event that happens once. */
    fun starts(e: Event, from: Long, to: Long): List<Long> {
        val span = e.lastDay - e.day
        val kind = Repeat.of(e.repeat) ?: return if (e.day <= to && e.lastDay >= from) listOf(e.day) else emptyList()
        val last = minOf(to, e.repeatUntil ?: Long.MAX_VALUE, EVENT_MAX_DAY)
        if (last < e.day) return emptyList()
        val skip = skipped(e)
        // Jump close to the window instead of walking from the first time
        var n = when (kind) {
            Repeat.DAILY, Repeat.WEEKLY, Repeat.BIWEEKLY -> maxOf(0L, (from - span - e.day) / kind.minGap)
            Repeat.MONTHLY -> maxOf(0L, (from - span - e.day) / 31 - 1)
            Repeat.YEARLY -> maxOf(0L, (from - span - e.day) / 366 - 1)
        }
        val out = ArrayList<Long>()
        while (out.size < MAX_TIMES) {
            val s = start(e, kind, n++) ?: break
            if (s > last) break
            if (s + span >= from && s !in skip) out += s
        }
        return out
    }

    /** [e] as it is on the time that starts on [start]: the same record, moved to that day. */
    fun at(e: Event, start: Long): Event = if (start == e.day) e else e.copy(day = start, endDay = e.endDay?.let { it + (start - e.day) })

    /**
     * [events] with every time of a repeating one that touches [from]..[to] as an entry of its own
     * (the same id, its own days); earlier days first, whole-day ones before timed ones.
     */
    fun expand(events: List<Event>, from: Long, to: Long): List<Event> =
        events.flatMap { e -> starts(e, from, to).map { at(e, it) } }.sortedWith(compareBy({ it.day }, { it.startMin != null }, { it.startMin }))

    /**
     * For counting time, visits and meetings: one-off events as they are, and each time of a
     * repeating one from [from] to [to] as an entry of its own.
     */
    fun eachTime(events: List<Event>, from: Long, to: Long): List<Event> =
        events.flatMap { e -> if (!e.repeats) listOf(e) else starts(e, from, to).map { at(e, it) } }

    /** [e] at the time that matters on [today]: its coming time, or else its last one. A one-off event as it is. */
    fun near(e: Event, today: Long): Event {
        if (!e.repeats) return e
        next(e, today)?.let { return at(e, it) }
        val end = minOf(today, e.repeatUntil ?: today)
        return starts(e, end - 400, end).lastOrNull()?.let { at(e, it) } ?: e
    }

    /** [e] with its time that starts on [day] left out. */
    fun skipping(e: Event, day: Long): Event = e.copy(skipDays = (skipped(e) + day).sorted().joinToString(","))

    /** The first time of [e] that has not ended before [from], or null when there is none. */
    fun next(e: Event, from: Long): Long? {
        if (!e.repeats) return e.day.takeIf { e.lastDay >= from }
        // Far enough ahead for any rhythm, also when many times in a row are skipped
        return starts(e, from, from + 366L * 5).firstOrNull()
    }
}

/** Who takes part in an event. */
@Entity(tableName = "event_people", primaryKeys = ["eventId", "personId"], indices = [Index("personId")])
data class EventPerson(val eventId: Long, val personId: Long)

enum class EventKind {
    MEETING, SESSION, COMMITTEE, TRIP, RECEPTION, CALL, MEDIA, OTHER;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.name == key } ?: OTHER
    }
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events")
    fun observeAll(): Flow<List<Event>>

    /** The records behind [observeRange]: a repeating event once, whenever it could touch the days. */
    @Query(
        "SELECT * FROM events WHERE day <= :to AND (COALESCE(endDay, day) >= :from OR " +
            "(repeat IS NOT NULL AND (repeatUntil IS NULL OR repeatUntil + COALESCE(endDay, day) - day >= :from))) " +
            "ORDER BY day, startMin IS NOT NULL, startMin",
    )
    fun observeRangeRows(from: Long, to: Long): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE matterId = :matterId ORDER BY day DESC")
    fun observeForMatter(matterId: Long): Flow<List<Event>>

    @Query("SELECT e.* FROM events e JOIN event_people p ON p.eventId = e.id WHERE p.personId = :personId ORDER BY e.day DESC")
    fun observeForPerson(personId: Long): Flow<List<Event>>

    /** Events marked for a reminder that are not over by [from]; a repeating one as its record (see [Repeats.next]). */
    @Query("SELECT * FROM events WHERE remind = 1 AND (COALESCE(endDay, day) >= :from OR (repeat IS NOT NULL AND (repeatUntil IS NULL OR repeatUntil >= :from)))")
    suspend fun withReminder(from: Long): List<Event>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: Long): Event?

    @Insert
    suspend fun insert(e: Event): Long

    @Update
    suspend fun update(e: Event)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("SELECT * FROM event_people")
    fun observeLinks(): Flow<List<EventPerson>>

    @Query("SELECT personId FROM event_people WHERE eventId = :eventId")
    suspend fun peopleOf(eventId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: EventPerson)

    @Query("DELETE FROM event_people WHERE eventId = :eventId")
    suspend fun unlinkAll(eventId: Long)

    @Query("DELETE FROM event_people WHERE personId = :personId")
    suspend fun unlinkPerson(personId: Long)

    @Query("UPDATE events SET matterId = NULL WHERE matterId = :matterId")
    suspend fun clearMatter(matterId: Long)

    @Transaction
    suspend fun delete(id: Long) {
        unlinkAll(id)
        deleteRow(id)
    }

    @Query("SELECT id FROM people WHERE id IN (:ids)")
    suspend fun existingPeople(ids: Collection<Long>): List<Long>

    /** Saves the people of an event: exactly the given ones that still exist (one may have been deleted meanwhile). */
    @Transaction
    suspend fun setPeople(eventId: Long, people: Collection<Long>) {
        unlinkAll(eventId)
        if (people.isEmpty()) return
        existingPeople(people).forEach { link(EventPerson(eventId, it)) }
    }
}

/**
 * Events that touch the days from..to (a trip that started earlier is included). Each time of a
 * repeating event is an entry of its own, with the record's id and that time's days: to change
 * the record, read it again by its id.
 */
fun EventDao.observeRange(from: Long, to: Long): Flow<List<Event>> = observeRangeRows(from, to).map { Repeats.expand(it, from, to) }

/** 1900-01-01 and 2200-01-01 as calendar days: an event outside them (a broken file) is not saved. */
const val EVENT_MIN_DAY = -25_567L
const val EVENT_MAX_DAY = 84_006L
/** How many skipped days of a repeating event are kept (the latest ones). */
const val MAX_SKIP_DAYS = 500
/** The longest event in days; a longer one keeps only its first day, so no screen walks thousands of years. */
const val EVENT_MAX_SPAN = 366L

/** Trims an event before saving; null when there is no title or the day is impossible. The end never comes before the start. */
fun cleanEvent(e: Event): Event? {
    val title = e.title.trim().take(500)
    if (title.isEmpty()) return null
    if (e.day !in EVENT_MIN_DAY..EVENT_MAX_DAY) return null
    val start = e.startMin?.coerceIn(0, 24 * 60 - 1)
    val end = if (start == null) null else e.endMin?.coerceIn(0, 24 * 60)
    val endDay = e.endDay?.takeIf { it > e.day && it - e.day <= EVENT_MAX_SPAN }
    // Something that repeats must end before it comes again, and its end cannot come before its start
    val repeat = Repeat.of(e.repeat)?.takeIf { (endDay ?: e.day) - e.day < it.minGap }
    val until = if (repeat == null) null else e.repeatUntil?.takeIf { it >= e.day }?.coerceAtMost(EVENT_MAX_DAY)
    val skip = if (repeat == null) "" else Repeats.skipped(e).filter { it >= e.day && (until == null || it <= until) }.sorted().takeLast(MAX_SKIP_DAYS).joinToString(",")
    return e.copy(
        repeat = repeat?.name, repeatUntil = until, skipDays = skip,
        title = title, place = e.place.trim().take(500), notes = e.notes.trim().take(MAX_NOTE_LENGTH),
        outcome = e.outcome.trim().take(MAX_NOTE_LENGTH), startMin = start,
        // On one day the end must be after the start
        endMin = if (end != null && start != null && endDay == null && end <= start) null else end,
        endDay = endDay,
    )
}
