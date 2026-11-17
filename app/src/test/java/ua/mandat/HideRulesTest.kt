package ua.mandat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.mandat.data.Deadline
import ua.mandat.data.Delegation
import ua.mandat.data.Event
import ua.mandat.data.HiddenNames
import ua.mandat.data.HideRules
import ua.mandat.data.Promise
import ua.mandat.ui.Hidden
import ua.mandat.ui.hides

/** The one rule for what trip mode hides: every kind of record, by every text field it has. */
class HideRulesTest {
    private val hidden = Hidden(setOf(7L), emptySet(), emptySet(), emptySet(), HiddenNames(listOf("Ремонт школи")))
    private val names = "щодо ремонту школи"

    private fun delegation(what: String, matterId: Long? = null, purpose: String = "", doneMeans: String = "", createdAt: Long = 0) =
        Delegation(what = what, personId = null, matterId = matterId, due = null, status = "OPEN", createdAt = createdAt, closedAt = null, purpose = purpose, doneMeans = doneMeans)

    private fun promise(what: String, toWhom: String = "", source: String = "", statusNote: String = "") =
        Promise(what = what, personId = null, toWhom = toWhom, isPublic = false, matterId = null, due = null, status = "OPEN", createdAt = 0, closedAt = null, source = source, statusNote = statusNote)

    private fun deadline(basis: String) =
        Deadline(title = "подати", day = 1, kind = "SOFT", basis = basis, matterId = null, remindBefore = 0, status = "OPEN", createdAt = 0, closedAt = null)

    @Test fun aRecordOfAHiddenMatterIsHiddenWhateverItsWords() {
        val d = delegation("подзвонити", matterId = 7)
        assertTrue(hidden.hides(HideRules.DELEGATION, d))
        assertFalse(hidden.hides(HideRules.DELEGATION, d.copy(matterId = 8)))
    }

    @Test fun aRecordWithoutAMatterIsHiddenByAnyOfItsTextFields() {
        // Each field alone is enough: these are the fields that were once checked in some places and not in others
        assertTrue(hidden.hides(HideRules.DELEGATION, delegation("подзвонити", doneMeans = names)))
        assertTrue(hidden.hides(HideRules.DELEGATION, delegation("подзвонити", purpose = names)))
        assertTrue(hidden.hides(HideRules.PROMISE, promise("зустрітися", source = names)))
        assertTrue(hidden.hides(HideRules.PROMISE, promise("зустрітися", statusNote = names)))
        assertTrue(hidden.hides(HideRules.PROMISE, promise("зустрітися", toWhom = names)))
        assertTrue(hidden.hides(HideRules.DEADLINE, deadline(names)))
        val event = Event(title = "нарада", day = 1, kind = "MEETING", createdAt = 0, updatedAt = 0)
        assertTrue(hidden.hides(HideRules.EVENT, event.copy(place = names)))
        assertTrue(hidden.hides(HideRules.EVENT, event.copy(outcome = names)))
        assertTrue(hidden.hides(HideRules.EVENT, event.copy(notes = names)))
        assertFalse(hidden.hides(HideRules.EVENT, event))
        // The field that one export once left unchecked
        val message = ua.mandat.data.KeyMessage(text = "теза", createdAt = 0, updatedAt = 0)
        assertTrue(hidden.hides(HideRules.MESSAGE, message.copy(audience = names)))
        assertTrue(hidden.hides(HideRules.MESSAGE, message.copy(proof = names)))
        assertFalse(hidden.hides(HideRules.MESSAGE, message))
    }

    @Test fun whatWasWrittenSinceTripModeBeganStaysInView() {
        val since = System.currentTimeMillis() - 60_000
        val trip = Hidden(setOf(7L), emptySet(), emptySet(), emptySet(), HiddenNames(listOf("Ремонт школи")), since)
        val before = delegation(names, createdAt = since - 1)
        val during = delegation(names, createdAt = since + 1)
        assertTrue(trip.hides(HideRules.DELEGATION, before))
        assertFalse(trip.hides(HideRules.DELEGATION, during))
        // Tied to the hidden matter itself: hidden whenever it was written
        assertTrue(trip.hides(HideRules.DELEGATION, during.copy(matterId = 7)))
    }

    @Test fun theTextOfARuleHoldsEveryField() {
        assertEquals("а б в", HideRules.DELEGATION.text(delegation("а", purpose = "б", doneMeans = "в")))
        assertEquals("а б в г", HideRules.PROMISE.text(promise("а", toWhom = "б", source = "в", statusNote = "г")))
    }

    @Test fun whatIsTiedToASensitivePersonIsHiddenAlsoInAMatterThatStaysInView() {
        // Person 5 is marked sensitive and takes part in event 9; no matter is hidden at all
        val trip = Hidden(emptySet(), setOf(5L), emptySet(), emptySet(), HiddenNames(listOf("Вигадана Особа")), events = setOf(9L))
        assertFalse(trip.isEmpty)
        assertTrue(trip.hides(HideRules.DELEGATION, delegation("подзвонити", matterId = 8).copy(personId = 5)))
        assertFalse(trip.hides(HideRules.DELEGATION, delegation("подзвонити", matterId = 8).copy(personId = 6)))
        assertTrue(trip.hides(HideRules.PROMISE, promise("зустрітися").copy(personId = 5, incoming = true)))
        val event = Event(id = 9, title = "нарада", day = 1, kind = "MEETING", matterId = 8, createdAt = 0, updatedAt = 0)
        assertTrue(trip.hides(HideRules.EVENT, event))
        assertFalse(trip.hides(HideRules.EVENT, event.copy(id = 10)))
        // A note without a matter that names the person in full is hidden; the surname alone is not enough
        assertTrue(trip.hides(HideRules.DELEGATION, delegation("передати Вигаданій Особі")))
        assertFalse(trip.hides(HideRules.DELEGATION, delegation("передати Особі")))
        // Search follows the same rule
        val rows = listOf(
            ua.mandat.data.SearchRow("delegation", 1, "a", "", "a", 8, personId = 5),
            ua.mandat.data.SearchRow("delegation", 2, "b", "", "b", 8, personId = 6),
            ua.mandat.data.SearchRow("event", 9, "c", "", "c", 8),
            ua.mandat.data.SearchRow("event", 10, "d", "", "d", 8),
            ua.mandat.data.SearchRow("person", 5, "e", "", "e", null),
        )
        assertEquals(listOf(2L, 10L), ua.mandat.ui.visibleRows(rows, trip).map { it.id })
    }

    @Test fun theReportLeavesOutUntickedMattersAndCountsVisitsByCommunity() {
        fun matter(id: Long, title: String, sensitive: Boolean = false) = ua.mandat.data.Matter(id = id, title = title, type = "OTHER", roleId = null, attention = "FOCUS",
            nextStep = "", goal = "", description = "", status = "ACTIVE", createdAt = 0, updatedAt = 0, sensitive = sensitive)
        val heads = ua.mandat.ui.ReportHeads("T", "DONE", "KEPT", "MEET", "AID", "WORK", "receptions", "visits", "collected", "note", "uah")
        val span = ua.mandat.data.Span(0, 100)
        val day = 24L * 60 * 60 * 1000
        fun kept(what: String, matterId: Long?, incoming: Boolean = false) = Promise(what = what, personId = null, toWhom = "", isPublic = !incoming, matterId = matterId, due = null,
            status = "DONE", createdAt = 0, closedAt = 10 * day, incoming = incoming)
        fun event(id: Long, kind: String, matterId: Long? = null) = Event(id = id, title = "x", day = 5, kind = kind, matterId = matterId, createdAt = 0, updatedAt = 0)
        val matters = listOf(matter(1, "Перша"), matter(2, "Друга"), matter(3, "Третя", sensitive = true))
        val promises = listOf(kept("обіцянка першої", 1), kept("обіцянка другої", 2), kept("обіцяли мені", 1, incoming = true))
        val events = listOf(event(1, "MEETING"), event(2, "MEETING"), event(3, "CALL"), event(4, "TRIP"), event(5, "RECEPTION"), event(6, "MEETING", matterId = 2))
        val text = ua.mandat.ui.voterReport(heads, span, matters, finished = setOf(1L), promises = promises, events = events, aid = emptyList(),
            skip = setOf(2L), communityEvents = setOf(1L, 3L, 5L, 6L))
        assertTrue(text.contains("- Перша"))
        assertFalse(text.contains("Друга"))
        assertFalse(text.contains("Третя"))
        assertTrue(text.contains("обіцянка першої"))
        assertFalse(text.contains("обіцянка другої"))
        // What was promised to the owner is never among the owner's kept promises
        assertFalse(text.contains("обіцяли мені"))
        // Visits: the meeting in a community and the trip; not the call, not the reception (counted apart), not the unticked matter's event
        assertTrue(text.contains("- visits: 2"))
        assertTrue(text.contains("- receptions: 1"))
    }
}
