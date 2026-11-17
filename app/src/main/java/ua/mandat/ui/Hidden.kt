package ua.mandat.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import ua.mandat.data.Hiding
import ua.mandat.data.HiddenNames
import ua.mandat.data.MandatDb

/**
 * Everything kept off the screen while something is hidden (trip mode, lens), worked out once
 * and used by every screen: the matters, the people known only through them and the people marked
 * sensitive, the events those people take part in, the diary days linked to them or naming them,
 * the communities tied only to them, and the titles and names for recognising free text that
 * names them.
 */
class Hidden(
    val matters: Set<Long>,
    val people: Set<Long>,
    val reviews: Set<Long>,
    val communities: Set<Long>,
    val names: HiddenNames,
    /** When trip mode was turned on: what was written or changed since then is never hidden by its words alone. */
    val since: Long = Long.MAX_VALUE,
    /** Events a hidden person takes part in. */
    val events: Set<Long> = emptySet(),
    /** The names of the people trip mode hides, as written on their cards (for an export's own word check). */
    val peopleNames: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = matters.isEmpty() && people.isEmpty()

    /**
     * Recognises the names of the people trip mode hides. Unlike a matter's title, a person's name is
     * looked for in every record, also one that belongs to a matter that stays in view: the matter
     * stays, the record that names the person does not.
     */
    val personWords: HiddenNames = if (peopleNames.isEmpty()) HiddenNames.NONE else HiddenNames(peopleNames)

    /** True for an item tied to a hidden matter. */
    fun matter(id: Long?): Boolean = id != null && id in matters

    /** True for an item tied to a hidden matter, or not tied to one but naming it in its text. */
    fun item(matterId: Long?, vararg text: String): Boolean = matter(matterId) || (matterId == null && names.namedAny(*text)) || personWords.namedAny(*text)

    /** The same, for a record written or last changed at [at]: one written in trip mode stays in view. */
    fun itemAt(matterId: Long?, at: Long, vararg text: String): Boolean =
        matter(matterId) || (!fresh(at) && ((matterId == null && names.namedAny(*text)) || personWords.namedAny(*text)))

    /** True for a record tied to a hidden person, or (with [eventId]) an event such a person takes part in. */
    fun tied(personId: Long?, eventId: Long? = null): Boolean = (personId != null && personId in people) || (eventId != null && eventId in events)

    /** Free text written at [at] names a hidden matter (and was written before trip mode was turned on). */
    fun named(at: Long, text: String): Boolean = !fresh(at) && names.named(text)

    /** Written or changed since trip mode was turned on (a time in the future does not count). */
    private fun fresh(at: Long): Boolean = at >= since && at <= System.currentTimeMillis()

    companion object {
        val NONE = Hidden(emptySet(), emptySet(), emptySet(), emptySet(), HiddenNames.NONE)
    }
}

/**
 * Follows the database: what is hidden along with [matters] and the people marked sensitive
 * ([marked], trip mode only). Free text is checked for the titles of [byName] only (the matters
 * trip mode hides) and the names of [marked]: the lens leaves records without a matter in view.
 */
fun observeHidden(db: MandatDb, matters: Set<Long>, byName: Set<Long> = matters, since: Long = Long.MAX_VALUE, marked: Set<Long> = emptySet()): Flow<Hidden> {
    if (matters.isEmpty() && marked.isEmpty()) return flowOf(Hidden.NONE)
    val h = db.hiding()
    val sources: List<Flow<List<Any>>> = listOf(
        db.matters().observeAllTitles(), h.personMatter(), db.reviews().observeAll(), db.reviews().observeLinks(),
        db.communities().observeLinks(), h.eventMatter(), h.promiseMatter(),
        db.events().observeLinks(), h.promisePerson(), if (marked.isEmpty()) flowOf(emptyList<ua.mandat.data.Person>()) else db.people().observeAll(),
    )
    return combine(sources) { arr ->
        @Suppress("UNCHECKED_CAST") val titles = arr[0] as List<ua.mandat.data.MatterTitle>
        @Suppress("UNCHECKED_CAST") val personMatter = arr[1] as List<ua.mandat.data.IdRef>
        @Suppress("UNCHECKED_CAST") val reviews = arr[2] as List<ua.mandat.data.Review>
        @Suppress("UNCHECKED_CAST") val reviewLinks = arr[3] as List<ua.mandat.data.ReviewLink>
        @Suppress("UNCHECKED_CAST") val commLinks = arr[4] as List<ua.mandat.data.CommunityLink>
        @Suppress("UNCHECKED_CAST") val eventMatter = (arr[5] as List<ua.mandat.data.IdRef>).associate { it.a to it.b }
        @Suppress("UNCHECKED_CAST") val promiseMatter = (arr[6] as List<ua.mandat.data.IdRef>).associate { it.a to it.b }
        @Suppress("UNCHECKED_CAST") val eventPeople = arr[7] as List<ua.mandat.data.EventPerson>
        @Suppress("UNCHECKED_CAST") val promisePerson = (arr[8] as List<ua.mandat.data.IdRef>).associate { it.a to it.b }
        @Suppress("UNCHECKED_CAST") val peopleNames = (arr[9] as List<ua.mandat.data.Person>).filter { it.id in marked }.map { it.name }
        // A matter whose own title names a hidden person is hidden with that person (trip mode)
        val byPerson = if (peopleNames.isEmpty()) emptySet() else HiddenNames(peopleNames).let { w -> titles.filter { w.named(it.title) }.mapTo(HashSet()) { it.id } }
        @Suppress("NAME_SHADOWING") val matters = matters + byPerson
        val names = HiddenNames(titles.filter { it.id in byName || it.id in byPerson }.map { it.title } + peopleNames)
        val people = Hiding.people(matters, personMatter, marked)
        val events = Hiding.events(people, eventPeople)
        Hidden(
            matters = matters,
            people = people,
            reviews = Hiding.reviews(matters, people, names, reviews, reviewLinks, since),
            communities = Hiding.communities(matters, people, commLinks, eventMatter, promiseMatter, events, promisePerson),
            names = names,
            since = since,
            events = events,
            peopleNames = peopleNames,
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default) // worked out away from the screen thread, also with years of records
}

/** Search rows that may be shown now: nothing hidden and nothing tied to it or naming it. */
fun visibleRows(rows: List<ua.mandat.data.SearchRow>, h: Hidden): List<ua.mandat.data.SearchRow> =
    if (h.isEmpty) rows else rows.filter { r ->
        when (r.kind) {
            "person" -> r.id !in h.people && !h.named(r.at, r.haystack)
            "event" -> r.id !in h.events && !h.itemAt(r.matterId, r.at, r.haystack)
            "review" -> r.id !in h.reviews
            "community" -> r.id !in h.communities && !h.named(r.at, r.haystack)
            "contact" -> !h.named(r.at, r.haystack)
            else -> !h.tied(r.personId) && !h.itemAt(r.matterId, r.at, r.haystack)
        }
    }

/** The words of a person's card that could name a matter. */
fun personText(p: ua.mandat.data.Person): String = p.name + " " + p.organization + " " + p.jobTitle + " " + p.contact + " " + p.notes

/** Hidden now: marked sensitive (in trip mode), known only through hidden matters, or the card names something hidden (written before trip mode). */
fun Hidden.person(p: ua.mandat.data.Person): Boolean = p.id in people || named(p.updatedAt, personText(p))

/** The words of a community's card that could name a matter. */
fun communityText(c: ua.mandat.data.Community): String = c.name + " " + c.head + " " + c.notes

/** Hidden now: tied only to hidden things, or its card names a hidden matter. */
fun Hidden.community(c: ua.mandat.data.Community): Boolean = c.id in communities || named(c.updatedAt, communityText(c))

/** Leaves out people known only through hidden matters, or whose card names one. */
fun Flow<List<ua.mandat.data.Person>>.hidingPeople(session: Session?): Flow<List<ua.mandat.data.Person>> =
    if (session == null) this
    else combine(session.hidden) { list, h -> if (h.isEmpty) list else list.filter { !h.person(it) } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)

/** Leaves out diary days tied to hidden matters or naming them. */
fun Flow<List<ua.mandat.data.Review>>.hidingReviews(session: Session?): Flow<List<ua.mandat.data.Review>> =
    if (session == null) this
    else combine(session.hidden) { list, h -> if (h.reviews.isEmpty()) list else list.filter { it.id !in h.reviews } }

/** Leaves out communities tied only to hidden things, or whose card names a hidden matter. */
fun Flow<List<ua.mandat.data.Community>>.hidingCommunities(session: Session?): Flow<List<ua.mandat.data.Community>> =
    if (session == null) this
    else combine(session.hidden) { list, h -> if (h.isEmpty) list else list.filter { !h.community(it) } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)

/** Hidden now, by the one rule for this kind of record ([ua.mandat.data.HideRules]). */
fun <T> Hidden.hides(rule: ua.mandat.data.HideRule<T>, item: T): Boolean =
    tied(rule, item) || itemAt(rule.matterOf(item), rule.at(item), rule.text(item))

/** Tied to a hidden person: handed to them, promised to them, about them, or an event they take part in. */
fun <T> Hidden.tied(rule: ua.mandat.data.HideRule<T>, item: T): Boolean = tied(rule.personOf(item), rule.eventOf(item))

/** Leaves out records tied to a hidden matter, and free text (not tied to a matter) that names one. */
fun <T> Flow<List<T>>.hiding(session: Session?, rule: ua.mandat.data.HideRule<T>): Flow<List<T>> =
    if (session == null) this
    else combine(session.hidden) { list, h -> if (h.isEmpty) list else list.filter { !h.hides(rule, it) } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default) // matching titles in many texts, away from the screen thread

/** Leaves out the people of a matter who are hidden now (a person marked sensitive may be in a matter that stays in view). */
fun Flow<List<ua.mandat.data.MatterPersonRow>>.hidingRows(session: Session?): Flow<List<ua.mandat.data.MatterPersonRow>> =
    if (session == null) this
    else combine(session.hidden) { list, h -> if (h.people.isEmpty()) list else list.filter { it.personId !in h.people && !h.personWords.named(it.name) } }

/** Leaves out who-is-in-which-matter links of hidden matters and of hidden people. */
fun Flow<List<ua.mandat.data.MatterPerson>>.hidingLinks(session: Session?): Flow<List<ua.mandat.data.MatterPerson>> =
    if (session == null) this
    else combine(session.hidden) { list, h -> if (h.isEmpty) list else list.filter { it.matterId !in h.matters && it.personId !in h.people } }

/** Only what the owner promised (not what was promised to the owner): for counts, charts and reports about the owner's own word. */
@JvmName("ownPromises")
fun Flow<List<ua.mandat.data.Promise>>.own(): Flow<List<ua.mandat.data.Promise>> = map { l -> l.filter { !it.incoming } }

/** Only what the owner handed out (not what was handed to the owner). */
@JvmName("ownDelegations")
fun Flow<List<ua.mandat.data.Delegation>>.own(): Flow<List<ua.mandat.data.Delegation>> = map { l -> l.filter { !it.incoming } }

/**
 * For counting time, meetings and visits: each time of a repeating event as an entry of its own
 * (the record's id, that time's days), from about six years back to two months ahead. One-off
 * events stay as they are.
 */
fun Flow<List<ua.mandat.data.Event>>.eachTime(): Flow<List<ua.mandat.data.Event>> =
    map { l -> Dates.today().let { t -> ua.mandat.data.Repeats.eachTime(l, t - 2_200, t + 60) } }
