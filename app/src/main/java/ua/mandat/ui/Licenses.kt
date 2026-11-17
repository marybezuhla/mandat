package ua.mandat.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.mandat.R

/** A licence shown in full, as it asks of anyone who passes the software on. */
class LicenceText(val title: String, val paragraphs: List<String>)

/**
 * The licence texts that travel with Mandat: its own, and those of the libraries that ask for
 * their text to be shown. Bouncy Castle carries its text inside the library, so what is shown
 * always belongs to the version that is really in the app.
 */
fun licenceTexts(context: Context): List<LicenceText> {
    fun asset(name: String) = context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
    return listOf(
        "SQLCipher" to asset("sqlcipher.txt"),
        "Bouncy Castle" to org.bouncycastle.LICENSE.licenseText,
        "Protocol Buffers" to asset("protobuf.txt"),
        "Apache License 2.0" to asset("apache-2.0.txt"),
        "GNU General Public License 3.0" to asset("gpl-3.0.txt"),
    ).map { (title, text) -> LicenceText(title, paragraphsOf(text)) }
}

/** A long text as paragraphs (split at empty lines), so only what is on the screen is laid out. */
fun paragraphsOf(text: String): List<String> =
    text.replace("\r\n", "\n").split(Regex("\n[ \t]*\n")).map { it.trim('\n') }.filter { it.isNotBlank() }

/**
 * A paragraph for a narrow screen: the file's own line breaks are dropped, so that the screen
 * wraps the text itself. A line that begins a list item ("*") still begins a line.
 */
fun flowed(paragraph: String): String =
    paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
        .fold(StringBuilder()) { out, line ->
            if (out.isNotEmpty()) out.append(if (line.startsWith("*")) "\n" else " ")
            out.append(line)
        }.toString()

/** The libraries and materials in Mandat, each with its licence, and the texts those licences ask to show. */
@Composable
fun LicensesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val texts by produceState(emptyList<LicenceText>()) {
        value = withContext(Dispatchers.IO) { runCatching { licenceTexts(context) }.getOrDefault(emptyList()) }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.licenses_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyMedium) }
                item { Text(stringResource(R.string.licenses_list), style = MaterialTheme.typography.bodyMedium) }
                texts.forEach { licence ->
                    item { Text(licence.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() }) }
                    items(licence.paragraphs) { Text(flowed(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.ok)) } },
    )
}
