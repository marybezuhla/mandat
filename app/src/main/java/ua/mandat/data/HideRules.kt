package ua.mandat.data

/**
 * How one kind of record is checked against what is hidden: the matter it belongs to, when it was
 * written or last changed, and all of its free text that could name a matter.
 */
class HideRule<T>(val matterOf: (T) -> Long?, val at: (T) -> Long, val text: (T) -> String)

/**
 * The one place that says, for every kind of record, what decides whether it is hidden. The
 * screens, reminders, exports, packets and what the AI is told take their fields from here.
 *
 * Not from here, and to be kept in step by hand: the search rows (their text is built in SQL, see
 * SearchDao), sheets still being typed (a draft is not a record yet), and people, communities and
 * diary days, which have links of their own ([Hiding], personText, communityText).
 */
object HideRules {
    val DEADLINE = HideRule<Deadline>({ it.matterId }, { it.createdAt }) { it.title + " " + it.basis }
    val DELEGATION = HideRule<Delegation>({ it.matterId }, { it.createdAt }) { it.what + " " + it.purpose + " " + it.doneMeans }
    val PROMISE = HideRule<Promise>({ it.matterId }, { it.createdAt }) { it.what + " " + it.toWhom + " " + it.source + " " + it.statusNote }
    val EVENT = HideRule<Event>({ it.matterId }, { it.updatedAt }) { it.title + " " + it.notes + " " + it.place + " " + it.outcome }
    val INFO = HideRule<InfoItem>({ it.matterId }, { it.updatedAt }) { it.title + " " + it.notes + " " + it.source + " " + it.result }
    val ALLIANCE = HideRule<Alliance>({ it.matterId }, { it.updatedAt }) { it.withWhom + " " + it.about + " " + it.notes + " " + it.gives + " " + it.gets }
    val MESSAGE = HideRule<KeyMessage>({ it.matterId }, { it.updatedAt }) { it.text + " " + it.proof + " " + it.audience }
    val MONEY = HideRule<MoneyEntry>({ it.matterId }, { it.updatedAt }) { it.note + " " + it.toWhom + " " + it.category }
    val AID = HideRule<AidRecord>({ it.matterId }, { it.createdAt }) { it.what + " " + it.party }
    val TEXT = HideRule<TextDoc>({ it.matterId }, { it.updatedAt }) { it.title + " " + it.body }
    val INBOX = HideRule<InboxItem>({ null }, { it.createdAt }) { it.text }
    val CONTACT = HideRule<Contact>({ null }, { it.createdAt }) { it.name + " " + it.organization + " " + it.jobTitle + " " + it.notes + " " + it.label }
}
