package ua.mandat.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Smart input without AI: reads a short note ("Тарасу таблицю підписів до п'ятниці") and
 * suggests what it is, for whom, by when and for which matter. Only a suggestion: the owner
 * always confirms. Plain rules, so it works offline, instantly and predictably.
 */
enum class NoteKind { DELEGATION, PROMISE, DEADLINE, MATTER, NOTE }

data class Suggestion(
    val kind: NoteKind,
    val personId: Long? = null,
    val due: Long? = null,
    val matterId: Long? = null,
    val matterType: MatterType? = null,
    /** A promise or a delegation that reads as made to the owner ("мені пообіцяли", "сусід пообіцяв"), not by the owner. */
    val incoming: Boolean = false,
)

private val NOT_WORD = Regex("[^\\p{L}\\p{N}'.]+")

/** Lower case, apostrophes unified, letters and digits only. */
internal fun words(text: String): List<String> =
    text.lowercase().replace('’', '\'').replace('ʼ', '\'').replace('`', '\'')
        .split(NOT_WORD).map { it.trim('.', '\'') }.filter { it.isNotEmpty() }

/**
 * A rough stem for Ukrainian words: drops a typical ending so that "Тарас", "Тарасу",
 * "Тарасові" and "Тарасом" meet. Short words stay as they are.
 */
internal fun stem(word: String): String {
    val w = word.lowercase()
    if (w.length <= 4) return w
    val endings = listOf("ові", "еві", "ому", "ою", "ею", "ам", "ям", "ах", "ях", "ів", "ей", "ом", "ем", "у", "ю", "а", "я", "і", "ї", "и", "е", "о", "ь", "й")
    for (e in endings) if (w.endsWith(e) && w.length - e.length >= 3) return w.dropLast(e.length)
    return w
}

private val WEEKDAYS = mapOf(
    "понеділ" to DayOfWeek.MONDAY, "вівтор" to DayOfWeek.TUESDAY, "серед" to DayOfWeek.WEDNESDAY,
    "четвер" to DayOfWeek.THURSDAY, "п'ятниц" to DayOfWeek.FRIDAY, "субот" to DayOfWeek.SATURDAY, "неділ" to DayOfWeek.SUNDAY,
)

private val MONTHS = listOf("січ", "лют", "берез", "квіт", "трав", "черв", "лип", "серп", "верес", "жовт", "листопад", "груд")

/** The first date the note mentions, as a calendar day, or null. Past dates move to next year. */
fun parseDue(text: String, today: Long): Long? {
    val t = LocalDate.ofEpochDay(today)
    val ws = words(text)
    for ((i, w) in ws.withIndex()) {
        when {
            w == "сьогодні" -> return today
            w == "завтра" -> return today + 1
            w == "післязавтра" -> return today + 2
        }
        WEEKDAYS.entries.firstOrNull { w.startsWith(it.key) }?.let { (_, dow) ->
            var d = t.plusDays(1)
            while (d.dayOfWeek != dow) d = d.plusDays(1)
            // "наступного понеділка", "на наступному тижні в середу": a week later
            val next = ws.getOrNull(i - 1)?.startsWith("наступн") == true
            return (if (next && d.toEpochDay() - today < 7) d.plusWeeks(1) else d).toEpochDay()
        }
        // "через 3 дні", "через 2 тижні", "через тиждень"
        if (w == "через") {
            val a = ws.getOrNull(i + 1)
            val n = a?.toIntOrNull()
            val unit = if (n != null) ws.getOrNull(i + 2) else a
            val count = (n ?: 1).toLong()
            if (unit != null) {
                when {
                    unit.startsWith("дн") || unit.startsWith("ден") -> if (n != null) return today + count
                    unit.startsWith("тиж") -> return today + 7 * count
                    unit.startsWith("місяц") -> return t.plusMonths(count).toEpochDay()
                }
            }
        }
        if (w.startsWith("тиж") && ws.getOrNull(i - 1)?.startsWith("наступн") == true) {
            return t.with(DayOfWeek.MONDAY).plusWeeks(1).toEpochDay()
        }
        if (w == "кінця" || w == "кінці") {
            val next = ws.getOrNull(i + 1).orEmpty()
            when {
                next.startsWith("тиж") -> return t.with(DayOfWeek.FRIDAY).let { if (it.toEpochDay() < today) it.plusWeeks(1) else it }.toEpochDay()
                next.startsWith("місяц") -> return t.withDayOfMonth(t.lengthOfMonth()).toEpochDay()
            }
        }
        // 25.11 or 25.11.2026
        Regex("^(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{2,4}))?$").find(w)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            return dateOf(d, mo, y, t)
        }
        // "25 листопада"
        w.toIntOrNull()?.let { d ->
            val next = ws.getOrNull(i + 1) ?: return@let
            val mo = MONTHS.indexOfFirst { next.startsWith(it) }
            if (mo >= 0) return dateOf(d, mo + 1, null, t)
        }
    }
    return null
}

private fun dateOf(day: Int, month: Int, year: Int?, today: LocalDate): Long? {
    if (month !in 1..12 || day !in 1..31) return null
    return try {
        var d = LocalDate.of(year ?: today.year, month, day)
        if (year == null && d.isBefore(today)) d = d.plusYears(1)
        d.toEpochDay()
    } catch (e: Exception) {
        null
    }
}

/** The person the note names (in any grammatical case), if exactly one fits best. */
fun findPerson(text: String, people: List<Person>): Long? {
    val stems = words(text).filter { it.length >= 3 }.map(::stem).toSet()
    if (stems.isEmpty()) return null
    val scored = people.map { p ->
        val parts = words(p.name).filter { it.length >= 3 }.map(::stem)
        p to parts.count { it in stems }
    }.filter { it.second > 0 }
    val best = scored.maxOfOrNull { it.second } ?: return null
    val top = scored.filter { it.second == best }
    // Two people share the name that was written: better not to guess
    return top.singleOrNull()?.first?.id
}

private val DELEGATION_WORDS = listOf("доруч", "попрос", "нехай", "хай", "передат", "підготуват")
private val PROMISE_WORDS = listOf("пообіця", "обіця", "обіцянк")
private val DEADLINE_WORDS = listOf("строк", "дедлайн", "термін", "крайн")

private val TYPE_WORDS = listOf(
    MatterType.BILL to listOf("законопроєкт", "закон", "правк", "читанн"),
    MatterType.INQUIRY to listOf("запит", "звернен"),
    MatterType.INVESTIGATION to listOf("тск", "слідч", "розслідуван"),
    MatterType.WORKING_GROUP to listOf("робоч"),
    MatterType.INTERNATIONAL to listOf("відрядж", "візит", "делегац", "парє", "міжнарод"),
    MatterType.COURT to listOf("суд", "позов", "адвокат"),
    MatterType.PUBLIC_COMMS to listOf("ефір", "допис", "інтерв'ю", "інтерв", "соцмереж", "брифінг", "відео"),
    MatterType.CAMPAIGN to listOf("кампані", "реформ", "адвокаці"),
    MatterType.DISTRICT to listOf("округ", "громад", "староста", "прийом"),
)

/** The active matter the note is about: a long distinctive word or two words in common. */
fun findMatter(text: String, matters: List<MatterTitle>): Long? {
    val stems = words(text).filter { it.length >= 4 }.map(::stem).toSet()
    val scored = matters.map { m ->
        val parts = words(m.title).filter { it.length >= 4 }.map(::stem).toSet()
        val common = parts intersect stems
        m to (common.size * 2 + common.count { it.length >= 6 })
    }.filter { it.second >= 3 }
    val best = scored.maxOfOrNull { it.second } ?: return null
    return scored.filter { it.second == best }.singleOrNull()?.first?.id
}

/** "To me", "to us": the one promised or asked is the owner. */
private val TO_OWNER = setOf("мені", "мене", "нам", "нас")
private val BY_OWNER = setOf("я", "ми")
/** A past form the owner could use of themselves too ("пообіцяв", "пообіцяла"): it speaks of someone else only when that someone stands right before it. */
private val THEY_DID = setOf("пообіцяв", "пообіцяла", "пообіцяли", "обіцяв", "обіцяла", "обіцяли", "доручив", "доручила", "доручили", "попросив", "попросила", "попросили")
private val DONE_TO = setOf("доручено", "обіцяно", "пообіцяно")
/** Words before the verb that are not the one who did it: when, how, a joining word. */
private val NOT_WHO = setOf(
    "вчора", "учора", "позавчора", "сьогодні", "щойно", "вже", "уже", "тоді", "знову", "також", "теж", "ще", "не", "і", "й", "та", "а", "що", "як", "коли",
    "зранку", "вранці", "ввечері", "увечері", "нещодавно", "днями", "раніше", "потім", "давно", "тут", "там", "публічно", "особисто", "сам", "сама", "самі",
    "понеділок", "вівторок", "четвер", "січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня", "листопада", "грудня",
)
/** Looks like "to someone" or "someone" as an object ("Тарасу", "Олегові", "сусідку"), never like the one who acts. */
private fun toSomeone(w: String) = w.endsWith("ові") || w.endsWith("еві") || w.endsWith("єві") || w.endsWith("у") || w.endsWith("ю")

/**
 * The note reads as something promised or handed to the owner: it says "to me", or "it was handed"
 * (but not "handed to Taras"), or someone else stands right before the verb ("сусід пообіцяв",
 * "сусідка пообіцяла") and "I" is not there. "Вчора пообіцяв виборцям" and "Тарасу доручив" are
 * the owner's own. Only a guess: the dialog opens with the direction chosen and the owner can
 * change it with one tap.
 */
internal fun toOwner(ws: List<String>): Boolean {
    if (ws.any { it in BY_OWNER }) return false
    if (ws.any { it in TO_OWNER }) return true
    val done = ws.indexOfFirst { it in DONE_TO }
    if (done >= 0) return !(ws[done] == "доручено" && ws.getOrNull(done + 1)?.let(::toSomeone) == true)
    val verb = ws.indexOfFirst { it in THEY_DID }
    if (verb <= 0) return false
    val who = ws[verb - 1]
    if (who in NOT_WHO || who.any(Char::isDigit) || toSomeone(who)) return false
    // "Марії пообіцяв": to her, by the owner; "сусіди пообіцяли": they did
    return ws[verb].endsWith("ли") || !(who.endsWith("і") || who.endsWith("ї"))
}

/** Reads the note and suggests what it most likely is. */
fun suggest(text: String, people: List<Person>, matters: List<MatterTitle>, today: Long): Suggestion {
    val ws = words(text)
    if (ws.isEmpty()) return Suggestion(NoteKind.NOTE)
    fun has(list: List<String>) = ws.any { w -> list.any { w.startsWith(it) } }
    val personId = findPerson(text, people)
    val due = parseDue(text, today)
    val matterId = findMatter(text, matters)
    val person = people.firstOrNull { it.id == personId }
    val kind = when {
        has(PROMISE_WORDS) -> NoteKind.PROMISE
        has(DELEGATION_WORDS) -> NoteKind.DELEGATION
        // A note that starts with a team member's name is a task for them
        person?.teamRole != null && stem(ws.first()) in words(person.name).map(::stem) -> NoteKind.DELEGATION
        person?.teamRole != null && due != null -> NoteKind.DELEGATION
        has(DEADLINE_WORDS) && due != null -> NoteKind.DEADLINE
        due != null && personId == null -> NoteKind.DEADLINE
        TYPE_WORDS.any { (_, list) -> has(list) } && matterId == null -> NoteKind.MATTER
        else -> NoteKind.NOTE
    }
    val type = if (kind == NoteKind.MATTER) TYPE_WORDS.firstOrNull { (_, list) -> has(list) }?.first else null
    return Suggestion(kind, personId, due, matterId, type, incoming = (kind == NoteKind.PROMISE || kind == NoteKind.DELEGATION) && toOwner(ws))
}
