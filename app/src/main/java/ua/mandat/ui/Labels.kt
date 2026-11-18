package ua.mandat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ua.mandat.R
import ua.mandat.data.Attention
import ua.mandat.data.MatterStatus
import ua.mandat.data.MatterType
import ua.mandat.data.Mode

/**
 * The owner's mode (chosen at the start, changed in Options). Read while drawing, so every name
 * follows a change at once.
 */
object AppMode {
    var mode by androidx.compose.runtime.mutableStateOf(Mode.RADA)

    /**
     * The simple view ([ua.mandat.data.SETTING_SIMPLE]): the ways into media, alliances, the influence
     * map, the team, communities, statistics and the retrospective are not shown, and a few rarely
     * needed fields of a person, a promise and a delegation are left out. Nothing stored changes.
     */
    var simple by androidx.compose.runtime.mutableStateOf(false)
}

fun Mode.label(): Int = when (this) {
    Mode.RADA -> R.string.mode_rada
    Mode.COUNCIL -> R.string.mode_council
    Mode.ASSISTANT -> R.string.mode_assistant
    Mode.OFFICIAL -> R.string.mode_official
    Mode.ACTIVIST -> R.string.mode_activist
    Mode.CITIZEN -> R.string.mode_citizen
}

/**
 * A text written for elected work and its plain twin for an official or a citizen, so that in
 * their modes nothing speaks of a district, a term of office or voters. Read while drawing.
 */
fun forMode(elected: Int, plain: Int): Int = if (AppMode.mode.elected) elected else plain

/**
 * The words of direction ("promised to me", "I promise"): an assistant speaks for the deputy's
 * office, so for them it is "to us" and "we promise".
 */
fun dirLabel(id: Int): Int = if (AppMode.mode != Mode.ASSISTANT) id else when (id) {
    R.string.kind_promise_in -> R.string.kind_promise_in_assistant
    R.string.kind_delegation_in -> R.string.kind_delegation_in_assistant
    R.string.dir_promise_out -> R.string.dir_promise_out_assistant
    R.string.dir_delegation_out -> R.string.dir_delegation_out_assistant
    R.string.chip_to_me -> R.string.chip_to_us
    else -> id
}

/** A word of its own for an official, an activist or a citizen where one exists; [other] for the rest. */
fun byMode(other: Int, official: Int? = null, activist: Int? = null, citizen: Int? = null): Int = when (AppMode.mode) {
    Mode.OFFICIAL -> official ?: other
    Mode.ACTIVIST -> activist ?: other
    Mode.CITIZEN -> citizen ?: other
    else -> other
}

/** Types that do not fit the chosen mode are not offered for new matters (old ones keep theirs). */
fun matterTypesForMode(): List<MatterType> {
    // A grant, a public action and a dispute belong to civic work and to a citizen's own affairs (a dispute also to an office)
    val civic = setOf(MatterType.GRANT, MatterType.ACTION, MatterType.DISPUTE)
    return when (AppMode.mode) {
        Mode.COUNCIL -> MatterType.entries.filter { it != MatterType.INTERNATIONAL && it !in civic }
        Mode.RADA, Mode.ASSISTANT -> MatterType.entries.filter { it !in civic }
        Mode.OFFICIAL -> MatterType.entries.filter { it != MatterType.GRANT && it != MatterType.ACTION }
        Mode.CITIZEN -> MatterType.entries.filter { it != MatterType.INTERNATIONAL && it != MatterType.GRANT }
        Mode.ACTIVIST -> MatterType.entries
    }
}

/** The stored type is the same in every mode; only the name shown follows the mode. */
fun MatterType.label(): Int = when (this) {
    MatterType.CAMPAIGN -> R.string.type_CAMPAIGN
    MatterType.BILL -> when (AppMode.mode) {
        Mode.RADA -> R.string.type_BILL
        Mode.ASSISTANT -> R.string.type_BILL_assistant
        Mode.COUNCIL -> R.string.type_BILL_council
        Mode.OFFICIAL -> R.string.type_BILL_official
        Mode.CITIZEN, Mode.ACTIVIST -> R.string.type_BILL_citizen
    }
    MatterType.INVESTIGATION -> when (AppMode.mode) {
        Mode.RADA -> R.string.type_INVESTIGATION
        Mode.ASSISTANT -> R.string.type_INVESTIGATION_assistant
        Mode.COUNCIL -> R.string.type_INVESTIGATION_council
        Mode.OFFICIAL -> R.string.type_INVESTIGATION_official
        Mode.CITIZEN, Mode.ACTIVIST -> R.string.type_INVESTIGATION_citizen
    }
    MatterType.COURT -> R.string.type_COURT
    MatterType.DISTRICT -> when (AppMode.mode) {
        Mode.RADA, Mode.COUNCIL, Mode.ASSISTANT -> R.string.type_DISTRICT
        Mode.OFFICIAL, Mode.CITIZEN, Mode.ACTIVIST -> R.string.type_DISTRICT_community
    }
    MatterType.INTERNATIONAL -> R.string.type_INTERNATIONAL
    MatterType.WORKING_GROUP -> R.string.type_WORKING_GROUP
    MatterType.INQUIRY -> when (AppMode.mode) {
        Mode.RADA, Mode.COUNCIL, Mode.ASSISTANT -> R.string.type_INQUIRY
        Mode.OFFICIAL -> R.string.type_INQUIRY_official
        Mode.CITIZEN, Mode.ACTIVIST -> R.string.type_INQUIRY_citizen
    }
    MatterType.PUBLIC_COMMS -> R.string.type_PUBLIC_COMMS
    MatterType.AID -> R.string.type_AID
    MatterType.GRANT -> R.string.type_GRANT
    MatterType.ACTION -> R.string.type_ACTION
    MatterType.DISPUTE -> R.string.type_DISPUTE
    MatterType.OTHER -> R.string.type_OTHER
}

fun Attention.label(): Int = when (this) {
    Attention.FOCUS -> R.string.attention_FOCUS
    Attention.BACKGROUND -> R.string.attention_BACKGROUND
    Attention.SLEEPING -> R.string.attention_SLEEPING
}

fun Attention.hint(): Int = when (this) {
    Attention.FOCUS -> R.string.attention_hint_FOCUS
    Attention.BACKGROUND -> R.string.attention_hint_BACKGROUND
    Attention.SLEEPING -> R.string.attention_hint_SLEEPING
}

fun MatterStatus.label(): Int = when (this) {
    MatterStatus.ACTIVE -> R.string.status_ACTIVE
    MatterStatus.DONE -> R.string.status_DONE
    MatterStatus.DROPPED -> R.string.status_DROPPED
}

/**
 * A body (or a line of work) and the positions one can hold in it. A role is built
 * as "position — body", optionally with a name or topic. Only generic names are in the code;
 * the owner's actual choices live in the encrypted database.
 */
data class RoleBody(val name: Int, val positions: List<Int>)

/** The bodies of the chosen mode. */
fun roleBodies(): List<RoleBody> = when (AppMode.mode) {
    Mode.RADA -> ROLE_BODIES
    Mode.COUNCIL -> COUNCIL_BODIES
    Mode.ASSISTANT -> ASSISTANT_BODIES
    Mode.OFFICIAL -> OFFICIAL_BODIES
    Mode.ACTIVIST -> ACTIVIST_BODIES
    Mode.CITIZEN -> CITIZEN_BODIES
}

/** Posts in an office: the same in a ministry, an administration, a local body or an institution. */
private val OFFICE_POSITIONS = listOf(
    R.string.pos_head, R.string.pos_deputy_head, R.string.office_pos_unit_head, R.string.office_pos_specialist, R.string.office_pos_adviser,
)

/**
 * For a deputy's assistant: whom they assist (the deputy's name goes into the role's own name)
 * and the deputy's reception office. The work itself is the deputy's, so everything else is as in
 * the deputy's modes.
 */
val ASSISTANT_BODIES = listOf(
    RoleBody(R.string.assistant_body_mp, listOf(R.string.assistant_pos_consultant, R.string.assistant_pos_voluntary)),
    RoleBody(R.string.assistant_body_councillor, listOf(R.string.assistant_pos_consultant)),
    RoleBody(R.string.assistant_body_reception, listOf(R.string.pos_head, R.string.assistant_pos_worker)),
    RoleBody(R.string.mode_body_working_group, listOf(R.string.pos_head, R.string.pos_member)),
)

/** An official: generic names only. */
val OFFICIAL_BODIES = listOf(
    RoleBody(R.string.office_body_ministry, OFFICE_POSITIONS),
    RoleBody(R.string.office_body_administration, OFFICE_POSITIONS),
    RoleBody(R.string.office_body_local, OFFICE_POSITIONS),
    RoleBody(R.string.office_body_institution, OFFICE_POSITIONS),
    RoleBody(R.string.office_body_commission, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.pos_secretary, R.string.pos_member)),
    RoleBody(R.string.mode_body_working_group, listOf(R.string.pos_head, R.string.pos_member)),
    RoleBody(R.string.body_other_body, listOf(R.string.pos_representative, R.string.pos_member)),
)

/**
 * Civic work: an organisation, a charity fund, a coalition, an initiative, a trade union, a party
 * branch, a newsroom. Generic names only; the organisation's own name goes into the role's name.
 */
val ACTIVIST_BODIES = listOf(
    RoleBody(R.string.act_body_ngo, listOf(R.string.pos_chair, R.string.act_pos_board_chair, R.string.act_pos_exec, R.string.pos_deputy, R.string.civic_pos_board, R.string.act_pos_manager, R.string.pos_member, R.string.civic_pos_volunteer)),
    RoleBody(R.string.act_body_fund, listOf(R.string.act_pos_director, R.string.act_pos_board_chair, R.string.act_pos_supervisory, R.string.act_pos_manager, R.string.civic_pos_coordinator, R.string.civic_pos_volunteer)),
    RoleBody(R.string.act_body_coalition, listOf(R.string.civic_pos_coordinator, R.string.pos_representative, R.string.civic_pos_participant)),
    RoleBody(R.string.civic_body_initiative, listOf(R.string.civic_pos_coordinator, R.string.civic_pos_participant)),
    RoleBody(R.string.civic_body_volunteer, listOf(R.string.civic_pos_coordinator, R.string.civic_pos_volunteer)),
    RoleBody(R.string.act_body_union, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.act_pos_committee, R.string.pos_member)),
    RoleBody(R.string.act_body_party, listOf(R.string.pos_head, R.string.pos_deputy_head, R.string.act_pos_council, R.string.pos_member)),
    RoleBody(R.string.act_body_media, listOf(R.string.act_pos_editor, R.string.act_pos_journalist, R.string.act_pos_author)),
    RoleBody(R.string.civic_body_public_council, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.pos_secretary, R.string.pos_member)),
    RoleBody(R.string.civic_body_condo, listOf(R.string.act_pos_board_chair, R.string.civic_pos_board, R.string.pos_member)),
    RoleBody(R.string.mode_body_working_group, listOf(R.string.pos_head, R.string.pos_member)),
    RoleBody(R.string.civic_body_community, listOf()),
    RoleBody(R.string.civic_body_personal, listOf()),
)

/** A citizen: organisations and groups people join by their own will, and their own affairs. */
val CITIZEN_BODIES = listOf(
    // What everyone has comes first: the first of these is offered to begin with
    RoleBody(R.string.civic_body_personal, listOf()),
    RoleBody(R.string.civic_body_family, listOf()),
    RoleBody(R.string.civic_body_condo, listOf(R.string.act_pos_board_chair, R.string.civic_pos_board, R.string.pos_member)),
    RoleBody(R.string.civic_body_community, listOf()),
    RoleBody(R.string.civic_body_ngo, listOf(R.string.pos_head, R.string.pos_deputy_head, R.string.civic_pos_board, R.string.pos_member, R.string.civic_pos_volunteer)),
    RoleBody(R.string.civic_body_initiative, listOf(R.string.civic_pos_coordinator, R.string.civic_pos_participant)),
    RoleBody(R.string.civic_body_volunteer, listOf(R.string.civic_pos_coordinator, R.string.civic_pos_volunteer)),
    RoleBody(R.string.civic_body_public_council, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.pos_secretary, R.string.pos_member)),
    RoleBody(R.string.mode_body_working_group, listOf(R.string.pos_head, R.string.pos_member)),
)

/** A local council: generic names only. */
val COUNCIL_BODIES = listOf(
    RoleBody(R.string.cbody_leadership, listOf(R.string.cpos_head, R.string.cpos_secretary, R.string.pos_deputy)),
    RoleBody(R.string.body_faction, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.pos_member)),
    RoleBody(R.string.body_independent, listOf()),
    RoleBody(R.string.cbody_commission, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.pos_secretary, R.string.pos_member)),
    RoleBody(R.string.cbody_control, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.pos_secretary, R.string.pos_member)),
    RoleBody(R.string.cbody_executive, listOf(R.string.pos_member)),
    RoleBody(R.string.cbody_presidium, listOf(R.string.pos_member)),
    RoleBody(R.string.body_working_group, listOf(R.string.pos_head, R.string.pos_member)),
    RoleBody(R.string.cbody_district, listOf()),
    RoleBody(R.string.body_other_body, listOf(R.string.pos_representative, R.string.pos_member)),
)

val ROLE_BODIES = listOf(
    RoleBody(R.string.body_rada, listOf(R.string.pos_rada_chair, R.string.pos_rada_first_deputy, R.string.pos_rada_deputy)),
    RoleBody(R.string.body_faction, listOf(R.string.pos_chair, R.string.pos_first_deputy, R.string.pos_deputy, R.string.pos_member)),
    RoleBody(R.string.body_independent, listOf()),
    RoleBody(R.string.body_committee, listOf(R.string.pos_chair, R.string.pos_first_deputy, R.string.pos_deputy, R.string.pos_secretary, R.string.pos_sub_chair, R.string.pos_member)),
    RoleBody(R.string.body_tik, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.pos_secretary, R.string.pos_member)),
    RoleBody(R.string.body_tsk, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.pos_secretary, R.string.pos_member)),
    RoleBody(R.string.body_stik, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.pos_secretary, R.string.pos_member)),
    RoleBody(R.string.body_counting, listOf(R.string.pos_chair, R.string.pos_member)),
    RoleBody(R.string.body_delegation, listOf(R.string.pos_chair, R.string.pos_deputy, R.string.pos_member)),
    RoleBody(R.string.body_eu_committee, listOf(R.string.pos_cochair, R.string.pos_member)),
    RoleBody(R.string.body_assembly, listOf(R.string.pos_cochair, R.string.pos_member)),
    RoleBody(R.string.body_friendship, listOf(R.string.pos_head_cochair, R.string.pos_deputy_head, R.string.pos_secretary, R.string.pos_member)),
    RoleBody(R.string.body_interfaction, listOf(R.string.pos_cochair, R.string.pos_member)),
    RoleBody(R.string.body_working_group, listOf(R.string.pos_head, R.string.pos_member)),
    RoleBody(R.string.body_district, listOf()),
    RoleBody(R.string.body_region, listOf()),
    RoleBody(R.string.body_other_body, listOf(R.string.pos_representative, R.string.pos_member)),
)

fun ua.mandat.data.TeamRole.label(): Int = when (this) {
    // The stored keys are those of a deputy's office; what they are called follows who the owner is
    ua.mandat.data.TeamRole.ASSISTANT -> when (AppMode.mode) {
        Mode.RADA, Mode.ASSISTANT -> R.string.team_ASSISTANT
        // (a local councillor's assistants work without pay)
        Mode.COUNCIL -> R.string.team_ASSISTANT_council
        Mode.OFFICIAL -> R.string.team_ASSISTANT_office
        Mode.ACTIVIST -> R.string.team_ASSISTANT_civic
        Mode.CITIZEN -> R.string.team_ASSISTANT_plain
    }
    ua.mandat.data.TeamRole.VOLUNTEER_ASSISTANT -> if (AppMode.mode.elected) R.string.team_VOLUNTEER_ASSISTANT else if (AppMode.mode == Mode.OFFICIAL) R.string.team_VOLUNTEER_office else R.string.civic_pos_volunteer
    ua.mandat.data.TeamRole.OFFICE_HEAD -> if (AppMode.mode == Mode.OFFICIAL) R.string.team_OFFICE_HEAD_office else R.string.team_OFFICE_HEAD
    ua.mandat.data.TeamRole.INTERN -> R.string.team_INTERN
    ua.mandat.data.TeamRole.SECRETARIAT -> forMode(R.string.team_SECRETARIAT, R.string.team_SECRETARIAT_plain)
    ua.mandat.data.TeamRole.LAWYER -> R.string.team_LAWYER
    ua.mandat.data.TeamRole.ADVISOR -> R.string.team_ADVISOR
    ua.mandat.data.TeamRole.COMMS -> R.string.team_COMMS
    ua.mandat.data.TeamRole.ANALYST -> R.string.team_ANALYST
    ua.mandat.data.TeamRole.OTHER -> R.string.team_OTHER
}

fun ua.mandat.data.PersonCategory.label(): Int = when (this) {
    // (a colleague only for another deputy; for an assistant, as for an official or a citizen, simply a deputy)
    ua.mandat.data.PersonCategory.COLLEAGUE_MP -> if (AppMode.mode.deputy) R.string.cat_COLLEAGUE_MP else R.string.cat_COLLEAGUE_MP_plain
    ua.mandat.data.PersonCategory.COLLEAGUE -> R.string.cat_COLLEAGUE
    ua.mandat.data.PersonCategory.OFFICIAL -> R.string.cat_OFFICIAL
    ua.mandat.data.PersonCategory.EXPERT -> R.string.cat_EXPERT
    ua.mandat.data.PersonCategory.JOURNALIST -> R.string.cat_JOURNALIST
    ua.mandat.data.PersonCategory.NGO -> R.string.cat_NGO
    // (for a citizen the same keys name the people of a home life; for civic work, its partners)
    ua.mandat.data.PersonCategory.BUSINESS -> byMode(R.string.cat_BUSINESS, citizen = R.string.cat_BUSINESS_home)
    ua.mandat.data.PersonCategory.INTERNATIONAL -> byMode(R.string.cat_INTERNATIONAL, activist = R.string.cat_INTERNATIONAL_civic, citizen = R.string.cat_FAMILY)
    ua.mandat.data.PersonCategory.VOTER -> byMode(forMode(R.string.cat_VOTER, R.string.cat_VOTER_plain), citizen = R.string.cat_NEIGHBOUR)
    ua.mandat.data.PersonCategory.OTHER -> R.string.cat_OTHER
}

fun ua.mandat.data.Stance.label(): Int = when (this) {
    // (an office speaks of support and objection, not of allies and opponents)
    ua.mandat.data.Stance.ALLY -> byMode(R.string.stance_ALLY, official = R.string.stance_ALLY_office)
    ua.mandat.data.Stance.UNDECIDED -> R.string.stance_UNDECIDED
    ua.mandat.data.Stance.OPPONENT -> byMode(R.string.stance_OPPONENT, official = R.string.stance_OPPONENT_office)
    ua.mandat.data.Stance.NEUTRAL -> R.string.stance_NEUTRAL
}

fun ua.mandat.data.TaskStatus.delegationLabel(): Int = when (this) {
    ua.mandat.data.TaskStatus.OPEN -> R.string.d_OPEN
    ua.mandat.data.TaskStatus.DONE -> R.string.d_DONE
    ua.mandat.data.TaskStatus.FAILED, ua.mandat.data.TaskStatus.CANCELLED -> R.string.d_CANCELLED
}

fun ua.mandat.data.TaskStatus.promiseLabel(): Int = when (this) {
    ua.mandat.data.TaskStatus.OPEN -> R.string.p_OPEN
    ua.mandat.data.TaskStatus.DONE -> R.string.p_DONE
    ua.mandat.data.TaskStatus.FAILED -> R.string.p_FAILED
    ua.mandat.data.TaskStatus.CANCELLED -> R.string.p_CANCELLED
}

/** Statuses offered for delegations and deadlines (no "did not work out"). */
val TASK_STATUSES = listOf(ua.mandat.data.TaskStatus.OPEN, ua.mandat.data.TaskStatus.DONE, ua.mandat.data.TaskStatus.CANCELLED)

fun ua.mandat.data.Freedom.label(): Int = when (this) {
    ua.mandat.data.Freedom.TELL -> R.string.freedom_TELL
    ua.mandat.data.Freedom.OPTIONS -> R.string.freedom_OPTIONS
    ua.mandat.data.Freedom.DECIDE_INFORM -> R.string.freedom_DECIDE_INFORM
    ua.mandat.data.Freedom.FULL -> R.string.freedom_FULL
}

fun ua.mandat.data.Freedom.hint(): Int = when (this) {
    ua.mandat.data.Freedom.TELL -> R.string.freedom_TELL_hint
    ua.mandat.data.Freedom.OPTIONS -> R.string.freedom_OPTIONS_hint
    ua.mandat.data.Freedom.DECIDE_INFORM -> R.string.freedom_DECIDE_INFORM_hint
    ua.mandat.data.Freedom.FULL -> R.string.freedom_FULL_hint
}

fun ua.mandat.data.Raci.label(): Int = when (this) {
    ua.mandat.data.Raci.LEAD -> R.string.raci_LEAD
    ua.mandat.data.Raci.APPROVE -> R.string.raci_APPROVE
    ua.mandat.data.Raci.CONSULT -> R.string.raci_CONSULT
    ua.mandat.data.Raci.INFORM -> R.string.raci_INFORM
}

fun influenceLabel(level: Int?): Int = when (level) {
    1 -> R.string.influence_1
    2 -> R.string.influence_2
    3 -> R.string.influence_3
    else -> R.string.not_set
}

fun ua.mandat.data.Publicity.label(): Int = when (this) {
    ua.mandat.data.Publicity.PUBLIC -> R.string.publicity_public
    ua.mandat.data.Publicity.CIRCLE -> R.string.publicity_circle
    ua.mandat.data.Publicity.PERSONAL -> R.string.publicity_personal
    ua.mandat.data.Publicity.SELF -> R.string.publicity_self
}

fun ua.mandat.data.PromiseOrigin.label(): Int = when (this) {
    ua.mandat.data.PromiseOrigin.CHOSEN -> R.string.origin_chosen
    ua.mandat.data.PromiseOrigin.FORCED -> R.string.origin_forced
    ua.mandat.data.PromiseOrigin.ATTRIBUTED -> R.string.origin_attributed
    ua.mandat.data.PromiseOrigin.DUG_UP -> R.string.origin_dug_up
}

fun ua.mandat.data.PromiseOrigin.hint(): Int = when (this) {
    ua.mandat.data.PromiseOrigin.CHOSEN -> R.string.origin_chosen_hint
    ua.mandat.data.PromiseOrigin.FORCED -> R.string.origin_forced_hint
    ua.mandat.data.PromiseOrigin.ATTRIBUTED -> R.string.origin_attributed_hint
    ua.mandat.data.PromiseOrigin.DUG_UP -> R.string.origin_dug_up_hint
}

fun riskLabel(level: Int): Int = when (level) {
    1 -> R.string.risk_low
    2 -> R.string.risk_mid
    else -> R.string.risk_high
}

fun ua.mandat.data.InfoKind.label(): Int = when (this) {
    ua.mandat.data.InfoKind.DISCUSSION -> R.string.info_discussion
    ua.mandat.data.InfoKind.CRITIQUE -> byMode(R.string.info_critique, official = R.string.info_critique_office, citizen = R.string.info_critique_office)
    ua.mandat.data.InfoKind.ATTACK -> byMode(R.string.info_attack, official = R.string.info_attack_office)
    ua.mandat.data.InfoKind.CORRECTION -> R.string.info_correction
}

fun ua.mandat.data.AttackStage.label(): Int = when (this) {
    ua.mandat.data.AttackStage.SPOTTED -> R.string.stage_spotted
    ua.mandat.data.AttackStage.ASSESSED -> R.string.stage_assessed
    ua.mandat.data.AttackStage.DECIDED -> R.string.stage_decided
    ua.mandat.data.AttackStage.ACTED -> R.string.stage_acted
    ua.mandat.data.AttackStage.RESULT -> R.string.stage_result
}

fun ua.mandat.data.AttackDecision.label(): Int = when (this) {
    ua.mandat.data.AttackDecision.IGNORE -> R.string.decision_ignore
    ua.mandat.data.AttackDecision.ANSWER -> R.string.decision_answer
    ua.mandat.data.AttackDecision.REFUTE -> R.string.decision_refute
    ua.mandat.data.AttackDecision.ALLIES -> R.string.decision_allies
    ua.mandat.data.AttackDecision.LEGAL -> R.string.decision_legal
}

fun ua.mandat.data.IntegrityCheck.label(): Int = when (this) {
    ua.mandat.data.IntegrityCheck.SOURCE -> R.string.check_source
    ua.mandat.data.IntegrityCheck.ACTIONS -> R.string.check_actions
    ua.mandat.data.IntegrityCheck.FACTS -> R.string.check_facts
    ua.mandat.data.IntegrityCheck.SIGN -> R.string.check_sign
    ua.mandat.data.IntegrityCheck.PROPORTION -> R.string.check_proportion
}

fun ua.mandat.data.AllianceStatus.label(): Int = when (this) {
    ua.mandat.data.AllianceStatus.ACTIVE -> R.string.alliance_active
    ua.mandat.data.AllianceStatus.ENDED -> R.string.alliance_ended
    ua.mandat.data.AllianceStatus.BROKEN -> forMode(R.string.alliance_broken, R.string.alliance_broken_plain)
}

fun ua.mandat.data.RolePost.label(): Int = when (this) {
    ua.mandat.data.RolePost.LEAD -> R.string.post_LEAD
    ua.mandat.data.RolePost.DEPUTY -> R.string.post_DEPUTY
    ua.mandat.data.RolePost.SECRETARY -> R.string.post_SECRETARY
    ua.mandat.data.RolePost.MEMBER -> R.string.post_MEMBER
    ua.mandat.data.RolePost.OTHER -> R.string.post_OTHER
}
