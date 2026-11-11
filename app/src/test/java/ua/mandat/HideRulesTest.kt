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
}
