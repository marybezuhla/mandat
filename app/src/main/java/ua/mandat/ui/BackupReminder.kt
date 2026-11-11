package ua.mandat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ua.mandat.R
import ua.mandat.backup.AutoBackup
import ua.mandat.data.MandatDb

/** Why a copy is worth making now. */
enum class BackupDue { NEVER, OLD, AUTO_STOPPED, NEAR_LIMIT, TOO_BIG }

/** Days after the last copy when the reminder comes. */
const val BACKUP_REMIND_DAYS = 14

/** A copy this large (three quarters of the most a copy can hold) brings a warning, while there is still time to tidy up. */
const val BACKUP_WARN_BYTES = ua.mandat.backup.BackupCrypto.MAX_PLAIN_BYTES * 3 / 4

/**
 * Works out whether to remind. First of all: the data ([size] bytes at the last attempt) no longer
 * fits into a copy. Then: never copied (while there is something to lose), the newest copy older
 * than [BACKUP_REMIND_DAYS], or automatic copies failing since the last good one. Last, with the
 * copies themselves in order: the data is close to the most a copy can hold.
 */
fun backupDue(hasData: Boolean, manual: Long?, autoOk: Long?, autoFail: Long?, autoOn: Boolean, now: Long, size: Long? = null): BackupDue? {
    // No copy can be made at all until there is less: the other reminders would only mislead
    if (size != null && size > ua.mandat.backup.BackupCrypto.MAX_PLAIN_BYTES) return BackupDue.TOO_BIG
    val nearLimit = size != null && size > BACKUP_WARN_BYTES
    // (so much data without a single matter is possible: texts, people, the diary)
    if (!hasData) return if (nearLimit) BackupDue.NEAR_LIMIT else null
    if (autoOn && autoFail != null && autoFail > (autoOk ?: 0L)) return BackupDue.AUTO_STOPPED
    val last = listOfNotNull(manual, if (autoOn) autoOk else null).maxOrNull() ?: return BackupDue.NEVER
    if (now - last > BACKUP_REMIND_DAYS * 86_400_000L) return BackupDue.OLD
    return if (nearLimit) BackupDue.NEAR_LIMIT else null
}

/** A small card on the first tab when a backup is due; it leads to the settings. */
@Composable
fun BackupReminder(db: MandatDb, onOpen: () -> Unit) {
    val flow = remember(db) {
        val s = db.settings()
        combine(
            // When the last copy was made by hand and how large the last copy was, as one pair (combine takes five flows)
            s.observe(AutoBackup.KEY_MANUAL).combine(s.observe(AutoBackup.KEY_SIZE)) { manual, size -> manual to size },
            s.observe(AutoBackup.KEY_LAST_OK), s.observe(AutoBackup.KEY_LAST_FAIL),
            s.observe(AutoBackup.KEY_FOLDER), db.matters().observeAllTitles().map { it.isNotEmpty() }.distinctUntilChanged(),
        ) { (manual, size), ok, fail, folder, hasData ->
            backupDue(
                hasData, manual?.value?.toLongOrNull(), ok?.value?.toLongOrNull(), fail?.value?.toLongOrNull(), folder != null,
                System.currentTimeMillis(), size?.value?.toLongOrNull(),
            )
        }
    }
    val due by flow.collectAsStateWithLifecycle(null)
    // "Later" hides it until Mandat is opened again
    var later by rememberSaveable { mutableStateOf(false) }
    val why = due ?: return
    if (later) return
    MCard(Modifier.padding(horizontal = Space.screen, vertical = 4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(
                    when (why) {
                        BackupDue.NEVER -> R.string.backup_due_never
                        BackupDue.OLD -> R.string.backup_due_old
                        BackupDue.AUTO_STOPPED -> R.string.backup_due_auto
                        BackupDue.NEAR_LIMIT -> R.string.backup_due_near_limit
                        BackupDue.TOO_BIG -> R.string.backup_due_too_big
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { later = true }) { Text(stringResource(R.string.backup_due_later)) }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.backup_due_open)) }
            }
        }
    }
}
