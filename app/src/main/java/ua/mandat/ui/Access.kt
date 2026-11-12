package ua.mandat.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ua.mandat.R

// Small pieces that make the screens usable with a screen reader (TalkBack) as well as by sight.

/**
 * A whole row as one checkbox: a tap anywhere on it toggles, and a screen reader says the row's
 * text together with "ticked" or "not ticked" (a bare checkbox beside a text is read without the text).
 */
fun Modifier.checkRow(checked: Boolean, onChange: (Boolean) -> Unit): Modifier =
    toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)

/** The tick of a [checkRow]: only drawn, the row is the control. It takes the same room as a checkbox of its own. */
@Composable
fun CheckMark(checked: Boolean) = Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))

/** A sign that only repeats what the text beside it says (an arrow at the end of a row): a screen reader passes over it. */
fun Modifier.decorative(): Modifier = clearAndSetSemantics { }

/** A name for a control that shows only a sign or a tick. */
fun Modifier.named(name: String): Modifier = semantics { contentDescription = name }

/** A title that changes when an arrow beside it is pressed (a month, a week): a screen reader says the new one by itself. */
fun Modifier.announced(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }

/** The "earlier" and "later" arrows of a period, named for a screen reader. */
@Composable
fun StepButton(back: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.named(stringResource(if (back) R.string.step_back else R.string.step_forward))) {
        Text(if (back) "‹" else "›", style = MaterialTheme.typography.titleLarge, modifier = Modifier.decorative())
    }
}
