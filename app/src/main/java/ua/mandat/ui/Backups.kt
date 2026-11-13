package ua.mandat.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import ua.mandat.backup.BackupCrypto
import ua.mandat.backup.BackupData
import ua.mandat.backup.BackupFile
import ua.mandat.backup.BackupStream
import java.io.File

/** What the owner sees after a backup or restore attempt. */
sealed interface BackupNotice {
    data class Saved(val rows: Int) : BackupNotice
    data object SaveFailed : BackupNotice
    data object WrongPassword : BackupNotice
    data object NotABackup : BackupNotice
    data object Incompatible : BackupNotice
    /** The file is an export (.mandatx), not a backup. */
    data object IsExport : BackupNotice
    /** The backup was made by a newer Mandat: update the app first. */
    data object FromNewer : BackupNotice
    data class Restored(val rows: Int) : BackupNotice
    data object RestoreFailed : BackupNotice
    /** Automatic copies are on, but the copy of the current data before replacing it failed: nothing was replaced. */
    data object RestoreNoSafetyCopy : BackupNotice
    /** The same, because the data is too big for an automatic copy: a copy by hand is needed first. */
    data object RestoreTooBigForSafety : BackupNotice
    data object AutoOn : BackupNotice
    data object AutoFailed : BackupNotice
    data object AutoOff : BackupNotice
    data object AutoSaved : BackupNotice
    data object AutoTooBig : BackupNotice
    /** The chosen file already holds something: Mandat writes a backup only into a new file. */
    data object ExistingFile : BackupNotice
    /** The data is larger than a backup file can hold. */
    data object TooBig : BackupNotice
}

/**
 * True when the chosen file is empty: a new file just made by the save dialog. A file that already
 * holds something (an older copy picked to "replace") is never written over, so a failure halfway
 * can never cost both the new copy and the old one.
 */
fun isNewDocument(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.openInputStream(uri)?.use { it.read() < 0 } ?: sizeIsZero(context, uri)
} catch (e: Exception) {
    // Some file apps cannot read a file just made: then its reported size decides
    sizeIsZero(context, uri)
}

private fun sizeIsZero(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
        ?.use { c -> c.moveToFirst() && !c.isNull(0) && c.getLong(0) == 0L } == true
} catch (e: Exception) {
    false
}

/**
 * A backup that opened with the password and was checked to its end, waiting for the owner's
 * "Replace". A copy written in parts waits as it came, encrypted, in [file] inside Mandat's own
 * storage, with its [key] in memory; a one-piece copy (made before 0.3.0) waits decrypted in
 * memory ([json]). Nothing unencrypted is written anywhere.
 */
class PendingRestore internal constructor(
    val summary: BackupData.Summary,
    internal val json: ByteArray? = null,
    internal val file: File? = null,
    internal val key: BackupStream.Key? = null,
) {
    internal fun drop() {
        json?.fill(0)
        key?.wipe()
        file?.delete()
    }
}

class Backups(private val context: Context, private val session: Session) {

    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<BackupNotice?>(null)
    var pending by mutableStateOf<PendingRestore?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // A copy left on its way out or in when Mandat was stopped (encrypted, inside Mandat's own storage).
        // At the first start of the process nothing can be on its way, so everything goes; later only what is old
        val first = firstInProcess.compareAndSet(true, false)
        // (an automatic copy that waits to be sent stays: it is sent after the next unlock)
        scope.launch {
            try {
                if (first) BackupFile.clear(context, olderThanMs = 0, keepWaiting = true) else BackupFile.clear(context, keepWaiting = true)
            } catch (e: Exception) {
            }
        }
    }

    /** Automatic backups: on or off, and when the last one worked or failed. */
    var auto by mutableStateOf<ua.mandat.backup.AutoBackup.Status?>(null)
        private set

    fun refreshAuto() {
        scope.launch {
            val st = try { session.withRealDb { ua.mandat.backup.AutoBackup.status(it) } } catch (e: Exception) { null }
            withContext(Dispatchers.Main) { auto = st }
        }
    }

    /** Turns automatic backups on: folder chosen, password typed once; the first copy is checked with it. */
    fun enableAuto(folder: Uri, password: String) = run(onCrash = { BackupNotice.AutoFailed }) {
        val auto = ua.mandat.backup.AutoBackup
        val db = (session.state as? LockState.Open)?.db ?: return@run BackupNotice.AutoFailed
        val prepared = session.withRealDb {
            auto.setUp(context, it, folder, password)
            auto.prepareIfDue(context, it, force = true)
        }
        if (prepared == null) {
            val tooBig = session.withRealDb { auto.status(it).failReason == auto.REASON_TOO_BIG } == true
            session.withRealDb { auto.disable(context, it) }
            refreshAuto()
            return@run if (tooBig) BackupNotice.AutoTooBig else BackupNotice.AutoFailed
        }
        // Written without the database, then checked with the password the owner typed
        val wait = waitMs(prepared)
        val job = session.startAutoWrite(prepared)
        val result = withTimeoutOrNull(wait) { job.await() }
        val ok = result?.ok == true && result.uri != null && auto.opensWith(context, result.uri, password)
        if (ok) {
            session.reconcileAuto(db)
        } else {
            // Off first, so nothing counts the discarded file as a good copy
            val off = session.withRealDb { auto.disable(context, it); true } == true
            // (locked meanwhile, so it could not be turned off: then the copy stays and is recorded at the next unlock)
            if (off) {
                if (result != null) result.uri?.let { auto.discard(context, it) }
                // Still writing after a minute: remove the file once it is finished
                else scope.launch { job.await().uri?.let { auto.discard(context, it) } }
            }
        }
        refreshAuto()
        if (ok) BackupNotice.AutoOn else BackupNotice.AutoFailed
    }

    fun disableAuto() = run(onCrash = { BackupNotice.AutoOff }) {
        session.withRealDb { ua.mandat.backup.AutoBackup.disable(context, it) }
        refreshAuto()
        BackupNotice.AutoOff
    }

    /** Makes an automatic copy right now, without waiting a day. */
    fun autoNow() = run(onCrash = { BackupNotice.AutoFailed }) {
        val auto = ua.mandat.backup.AutoBackup
        val db = (session.state as? LockState.Open)?.db ?: return@run BackupNotice.AutoFailed
        // An earlier copy still waiting to be recorded comes first
        session.reconcileAuto(db)
        val prepared = session.withRealDb { auto.prepareIfDue(context, it, force = true) }
        if (prepared == null) {
            refreshAuto()
            val tooBig = session.withRealDb { auto.status(it).failReason == auto.REASON_TOO_BIG } == true
            return@run if (tooBig) BackupNotice.AutoTooBig else BackupNotice.AutoFailed
        }
        val wait = waitMs(prepared)
        withTimeoutOrNull(wait) { session.startAutoWrite(prepared).await() }
        val ok = session.reconcileAuto(db) == true
        refreshAuto()
        if (ok) BackupNotice.AutoSaved else BackupNotice.AutoFailed
    }

    /** The EFF long word list (7776 words) for suggested passwords. */
    val words: List<String> by lazy {
        context.assets.open("eff_large_wordlist.txt").bufferedReader().readLines().filter { it.isNotBlank() }
    }

    /** Writes an encrypted copy of everything to [uri], then reads it back to check it. */
    fun save(uri: Uri, password: String) {
        // Only a new, empty file is written; a file Mandat started writing into is removed after a failure
        var touched = false
        var temp: File? = null
        // (once the file is known to be the new empty one, any failure removes it: no empty "backup" stays behind)
        var ours = false
        run(onCrash = { temp?.delete(); if (ours || touched) deleteQuietly(uri); BackupNotice.SaveFailed }) {
            if (!isNewDocument(context, uri)) return@run BackupNotice.ExistingFile
            ours = true
            val chars = password.toCharArray()
            val key = try { BackupStream.newKey(chars) } finally { chars.fill(' ') }
            try {
                val file = BackupFile.temp(context, "out").also { temp = it }
                // First written, already encrypted, inside Mandat's own storage: the database never waits for a slow folder
                val written = try {
                    session.withRealDb { BackupFile.write(it, key, file) }
                } catch (e: BackupStream.TooBig) {
                    // Said plainly before anything is sent, rather than a copy that could not be opened again
                    deleteQuietly(uri)
                    return@run BackupNotice.TooBig
                } catch (e: Exception) {
                    null
                }
                if (written == null) {
                    deleteQuietly(uri)
                    return@run BackupNotice.SaveFailed
                }
                context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                    touched = true
                    file.inputStream().use { BackupFile.copy(it, out) }
                }
                // Check what actually landed on the storage, not what we meant to write: it must open
                // with this password, be whole to its last row, and hold exactly what was written
                val back = context.contentResolver.openInputStream(uri)!!.use { BackupFile.read(it, key) }
                if (back.same(written)) {
                    // Remembered for the reminder to make a copy (only the time, on this phone)
                    try {
                        session.withRealDb { it.settings().put(ua.mandat.data.Setting(ua.mandat.backup.AutoBackup.KEY_MANUAL, System.currentTimeMillis().toString())) }
                    } catch (e: Exception) {
                        // The copy is written and checked: failing to note the time must not remove it
                    }
                    BackupNotice.Saved(written.rows)
                } else {
                    deleteQuietly(uri)
                    BackupNotice.SaveFailed
                }
            } catch (e: Exception) {
                // Neither a half-written file nor an empty one stays behind looking like a backup
                deleteQuietly(uri)
                BackupNotice.SaveFailed
            } finally {
                key.wipe()
                temp?.delete()
            }
        }
    }

    /**
     * Opens a backup and shows what is in it; nothing is changed yet. The file is first taken,
     * as it is (encrypted), into Mandat's own storage, then read to its end: every part, every
     * row and every value is checked before the owner is asked anything.
     * [startedAt]: the lock generation read on the main thread when the owner chose the file.
     */
    fun open(uri: Uri, password: String, startedAt: Int) {
        var temp: File? = null
        // (a failure of Mandat itself says nothing about the file: it is not called "not a backup")
        run(onCrash = { temp?.delete(); BackupNotice.RestoreFailed }) {
            var key: BackupStream.Key? = null
            var kept = false
            try {
                val file = BackupFile.temp(context, "in").also { temp = it }
                try {
                    fetch(uri, file)
                } catch (e: BackupCrypto.NotABackup) {
                    throw e
                } catch (e: Exception) {
                    // The folder or the phone's storage failed: nothing is known about the file itself
                    return@run BackupNotice.RestoreFailed
                }
                if (file.length() < BackupCrypto.MIN_FILE_BYTES) return@run BackupNotice.NotABackup
                val layout = session.withRealDb { BackupData.layout(it) } ?: return@run BackupNotice.RestoreFailed
                val salt = file.inputStream().use { BackupStream.readSalt(it) }
                val chars = password.toCharArray()
                val k = try { BackupStream.keyFor(salt, chars) } finally { chars.fill(' ') }
                key = k
                val summary = try {
                    file.inputStream().use { BackupFile.read(it, k, BackupData.Checker(layout)) }
                } catch (e: BackupCrypto.WrongPasswordOrDamaged) {
                    // Not a copy written in parts. One piece, then: an export, or a backup made before 0.3.0
                    return@run openOnePiece(file, k, startedAt)
                }
                withContext(Dispatchers.Main) {
                    // Locked meanwhile: drop it instead of keeping the key in memory
                    if (startedAt == session.generationNow()) {
                        pending = PendingRestore(summary, file = file, key = k)
                        kept = true
                    }
                }
                null
            } catch (e: BackupCrypto.WrongPasswordOrDamaged) {
                BackupNotice.WrongPassword
            } catch (e: BackupCrypto.NotABackup) {
                BackupNotice.NotABackup
            } catch (e: BackupData.FromNewer) {
                // Said before asking to replace anything: a newer copy needs a newer Mandat
                BackupNotice.FromNewer
            } catch (e: BackupData.Incompatible) {
                BackupNotice.Incompatible
            } catch (e: Exception) {
                BackupNotice.RestoreFailed
            } finally {
                if (!kept) {
                    key?.wipe()
                    temp?.delete()
                }
            }
        }
    }

    /**
     * A one-piece file (it has the same salt at its start, so the key is the same): an export is
     * said to be one; a backup made before 0.3.0 waits, decrypted in memory, for the owner's "Replace".
     */
    private suspend fun openOnePiece(file: File, key: BackupStream.Key, startedAt: Int): BackupNotice? {
        if (file.length() > BackupCrypto.MAX_FILE_BYTES) return BackupNotice.WrongPassword
        val json = BackupCrypto.decryptWithKey(file.readBytes(), key.ikm)
        if (BackupCrypto.startsWithExportMark(json)) {
            json.fill(0)
            return BackupNotice.IsExport
        }
        val summary = try {
            BackupData.summary(json)
        } catch (e: Exception) {
            json.fill(0)
            return BackupNotice.Incompatible
        }
        if (summary.schema > ua.mandat.data.DB_VERSION) {
            json.fill(0)
            return BackupNotice.FromNewer
        }
        withContext(Dispatchers.Main) {
            if (startedAt == session.generationNow()) pending = PendingRestore(summary, json = json) else json.fill(0)
        }
        return null
    }

    /** Takes the chosen file into [to], refusing anything larger than a backup can be. */
    private fun fetch(uri: Uri, to: File) {
        // Check the size first when the file app tells it, before reading anything
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                if (it.statSize > BackupStream.MAX_FILE_BYTES) throw BackupCrypto.NotABackup()
            }
        } catch (e: BackupCrypto.NotABackup) {
            throw e
        } catch (e: Exception) {
            // Size unknown; the limit still applies while reading
        }
        context.contentResolver.openInputStream(uri)!!.use { input -> to.outputStream().use { BackupFile.copy(input, it) } }
    }

    /** The owner confirmed: replace all current data with the pending backup. */
    fun confirmRestore() {
        val restore = pending ?: return
        pending = null
        run(onCrash = { restore.drop(); BackupNotice.RestoreFailed }) {
            try {
                // With automatic copies on, the current data is copied first (encrypted, to that folder),
                // so replacing it can be undone; if that copy fails, nothing is replaced
                val auto = ua.mandat.backup.AutoBackup
                val db = (session.state as? LockState.Open)?.db ?: return@run BackupNotice.RestoreFailed
                if (session.withRealDb { auto.status(it).enabled } == true) {
                    session.reconcileAuto(db)
                    val tried = System.currentTimeMillis()
                    val prepared = session.withRealDb { auto.prepareIfDue(context, it, force = true) }
                    // "Too big" only if this very attempt found it so, not an old failure
                    if (prepared == null && session.withRealDb { st -> auto.status(st).let { it.failReason == auto.REASON_TOO_BIG && (it.lastFail ?: 0L) >= tried } } == true) {
                        refreshAuto()
                        return@run BackupNotice.RestoreTooBigForSafety
                    }
                    val written = if (prepared == null) null else withTimeoutOrNull(waitMs(prepared)) { session.startAutoWrite(prepared).await() }
                    val ok = written?.ok == true && written.uri != null && session.reconcileAuto(db) == true
                    // Kept a month even if more copies are made today (another restore, "copy now")
                    if (ok) session.withRealDb { auto.keepAsSafety(it, written!!.uri!!, prepared!!.createdAt) }
                    refreshAuto()
                    if (!ok) return@run BackupNotice.RestoreNoSafetyCopy
                }
                session.withRealDb { db ->
                    val json = restore.json
                    val file = restore.file
                    val key = restore.key
                    if (json != null) {
                        BackupData.restore(db, json)
                    } else if (file != null && key != null) {
                        // Read again from Mandat's own storage; it replaces anything only if it is still what was shown
                        file.inputStream().use { input -> BackupFile.open(input, key).use { BackupData.restore(db, it, restore.summary) } }
                    } else {
                        throw IllegalStateException()
                    }
                    noteRestoredCopy(db, restore.summary.createdAt)
                }
                    ?.let { BackupNotice.Restored(restore.summary.rows) }
                    ?: BackupNotice.RestoreFailed
            } catch (e: BackupData.Incompatible) {
                BackupNotice.Incompatible
            } catch (e: Exception) {
                BackupNotice.RestoreFailed
            } finally {
                restore.drop()
            }
        }
    }

    /**
     * The file just restored is a copy of this data, made at [createdAt]: noted as the last copy
     * made by hand, so the first tab does not say there is none (on a new phone nothing else is known).
     */
    private suspend fun noteRestoredCopy(db: ua.mandat.data.MandatDb, createdAt: Long) {
        try {
            val s = db.settings()
            // (a time from the file is never trusted beyond now)
            val made = minOf(createdAt, System.currentTimeMillis())
            val known = s.get(ua.mandat.backup.AutoBackup.KEY_MANUAL)?.value?.toLongOrNull() ?: 0L
            if (made > known) s.put(ua.mandat.data.Setting(ua.mandat.backup.AutoBackup.KEY_MANUAL, made.toString()))
        } catch (e: Exception) {
            // Only the reminder depends on it; the restore itself is done
        }
    }

    fun cancelRestore() {
        pending?.drop()
        pending = null
    }

    private fun run(onCrash: () -> BackupNotice, work: suspend () -> BackupNotice?) {
        if (busy) return
        busy = true
        scope.launch {
            // Also catches out-of-memory and other errors, so the app never crashes here
            val result = try {
                work()
            } catch (e: Throwable) {
                onCrash()
            }
            withContext(Dispatchers.Main) {
                busy = false
                if (result != null) notice = result
            }
        }
    }

    /** The picker made a new file, but Mandat locked before it could be written: the empty file is removed and the owner told. */
    fun savePickedTooLate(uri: Uri) {
        scope.launch {
            val empty = isNewDocument(context, uri)
            if (empty) deleteQuietly(uri)
            withContext(Dispatchers.Main) { if (notice == null) notice = BackupNotice.SaveFailed }
        }
    }

    private companion object {
        val firstInProcess = java.util.concurrent.atomic.AtomicBoolean(true)

        /** How long to wait for a copy to reach the folder: a minute, and more for a large one (read before sending starts). */
        fun waitMs(p: ua.mandat.backup.AutoBackup.Prepared): Long =
            (60_000L + p.file.length() / (1024 * 1024) * 2_000L).coerceAtMost(10 * 60_000L)
    }

    private fun deleteQuietly(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            // The file app may not allow it; nothing else to do
        }
    }
}
