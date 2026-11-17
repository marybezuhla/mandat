package ua.mandat.backup

import android.content.Context
import ua.mandat.data.MandatDb
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * A whole backup file: the rows of [BackupLines] inside the encryption of [BackupStream].
 *
 * A copy is first written, already encrypted, to a file inside Mandat's own storage and only then
 * sent to the folder the owner chose; a copy to restore is first taken in the same way. So the
 * database never waits for a slow folder, and what was checked is exactly what is restored. These
 * files are encrypted like the backup itself and are removed as soon as they have served.
 */
object BackupFile {
    private const val DIR = "copies"
    private const val STALE_MS = 60L * 60 * 1000
    private const val WAITING = "waiting-"

    private fun dir(context: Context) = File(context.cacheDir, DIR).apply { mkdirs() }

    /** A new empty file for a copy on its way out or in. */
    fun temp(context: Context, kind: String): File = File.createTempFile("$kind-", ".bin", dir(context))

    /**
     * An automatic copy waiting to be sent to the folder under [name]. Unlike [temp] files it may
     * outlive the process: if Mandat is stopped before it is sent, it is sent after the next unlock.
     */
    fun waiting(context: Context, name: String): File = File(dir(context), WAITING + name)

    /**
     * Removes what an earlier run left behind (Mandat was stopped halfway): files older than
     * [olderThanMs], so that a copy being sent out right now is never taken from under it.
     * With 0, everything goes. [keepWaiting] leaves automatic copies that still wait to be sent
     * (false when all data is erased or automatic copies are turned off).
     */
    fun clear(context: Context, olderThanMs: Long = STALE_MS, keepWaiting: Boolean = false) {
        val before = System.currentTimeMillis() - olderThanMs
        File(context.cacheDir, DIR).listFiles()?.forEach {
            if (keepWaiting && it.name.startsWith(WAITING)) return@forEach
            if (olderThanMs <= 0 || it.lastModified() < before) it.delete()
        }
    }

    /** Removes automatic copies that wait to be sent (none is expected any more). */
    fun clearWaiting(context: Context) {
        File(context.cacheDir, DIR).listFiles()?.forEach { if (it.name.startsWith(WAITING)) it.delete() }
    }

    /** Writes an encrypted copy of everything in [db] to [file]; [BackupStream.TooBig] if it cannot hold it. */
    fun write(db: MandatDb, key: BackupStream.Key, file: File): BackupData.Summary =
        file.outputStream().use { out -> BackupStream.writer(out, key).use { BackupLines.write(db, it) } }

    /** [input] is a copy from its first byte: gives its contents, decrypted. The salt in it must be [key]'s. */
    fun open(input: InputStream, key: BackupStream.Key): InputStream {
        if (!BackupStream.readSalt(input).contentEquals(key.salt)) throw BackupCrypto.WrongPasswordOrDamaged()
        return BackupStream.reader(input, key)
    }

    /** Reads a whole copy to its end, checking every part and every line; [sink] sees each table and row. */
    fun read(input: InputStream, key: BackupStream.Key, sink: BackupLines.Sink? = null): BackupData.Summary =
        open(input, key).use { BackupLines.read(it, sink) }

    /** Copies at most [limit] bytes ([BackupCrypto.NotABackup] beyond that); returns the SHA-256 of what went through. */
    fun copy(from: InputStream, to: OutputStream?, limit: Long = BackupStream.MAX_FILE_BYTES): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = from.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw BackupCrypto.NotABackup()
            digest.update(buffer, 0, n)
            to?.write(buffer, 0, n)
        }
        return digest.digest()
    }
}
