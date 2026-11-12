package ua.mandat.backup

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The backup file, written and read in parts, so memory never depends on its size.
 *
 * Like the one-piece file before it ([BackupCrypto]), it has no readable header or marker: to
 * anyone without the password it looks like random bytes and does not reveal that it comes from
 * Mandat. Which of the two layouts a file has is found by trying, with the password.
 *
 * Layout: salt (16) | streaming ciphertext of a gzip stream.
 * The password goes through Argon2id with the same fixed settings as before ([BackupCrypto.DEFAULT]).
 * That key feeds Google Tink's AES-256-GCM-HKDF streaming encryption (1 MiB segments): Tink takes
 * a fresh key for each file from its own random salt and authenticates every segment, their order
 * and the end of the file, so a cut, reordered or changed file never reads as whole. The salt and
 * the purpose are bound in as associated data. Tink's stream begins with one byte that is always
 * the same (the length of its header, 40); it is left out of the file and put back when reading.
 * Compressed before encryption: only the size of the file says anything, as with any file.
 */
object BackupStream {
    const val SALT_BYTES = 16
    private const val KEY_BYTES = 32
    private const val SEGMENT_BYTES = 1 shl 20
    /** Tink's first byte: the length of its own header (this byte, its salt, the nonce prefix). */
    private const val TINK_FIRST = 1 + KEY_BYTES + 7
    private val LABEL = "mandat-backup-v2".toByteArray(Charsets.US_ASCII)

    /** The file, at most: far above any real copy, a bound on what a crafted one can make the phone read. */
    const val MAX_FILE_BYTES = 1L shl 30
    /** Unpacked, at most; the same when writing, so a copy that was made can always be read. */
    const val MAX_PLAIN_BYTES = 512L shl 20

    /** There is more data than a copy can hold ([MAX_PLAIN_BYTES]). */
    class TooBig : Exception()

    /** The key for one file, held only while it is written or until a restore is confirmed or dropped. */
    class Key internal constructor(internal val salt: ByteArray, internal val ikm: ByteArray) {
        internal val aead by lazy { AesGcmHkdfStreaming(ikm, "HmacSha256", KEY_BYTES, SEGMENT_BYTES, 0) }
        /** Our copy is cleared; Tink keeps its own until the memory is reused, which the JVM cannot prevent. */
        fun wipe() = ikm.fill(0)
    }

    fun newKey(password: CharArray, params: BackupCrypto.KdfParams = BackupCrypto.DEFAULT): Key {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return Key(salt, BackupCrypto.deriveKey(password, salt, params))
    }

    /** The key a file's salt and a password give. Whether it is right shows when the first part opens. */
    fun keyFor(salt: ByteArray, password: CharArray, params: BackupCrypto.KdfParams = BackupCrypto.DEFAULT): Key {
        if (salt.size != SALT_BYTES) throw BackupCrypto.NotABackup()
        return Key(salt.copyOf(), BackupCrypto.deriveKey(password, salt, params))
    }

    /** A key made from the password earlier (automatic copies keep it inside the encrypted database). */
    fun storedKey(salt: ByteArray, key: ByteArray): Key {
        require(salt.size == SALT_BYTES && key.size == KEY_BYTES)
        return Key(salt.copyOf(), key.copyOf())
    }

    /** Reads exactly the salt from [input]; a shorter file is not ours. */
    fun readSalt(input: InputStream): ByteArray {
        val salt = ByteArray(SALT_BYTES)
        var got = 0
        while (got < salt.size) {
            val n = input.read(salt, got, salt.size - got)
            if (n < 0) throw BackupCrypto.NotABackup()
            got += n
        }
        return salt
    }

    private fun aad(key: Key) = key.salt + LABEL

    /**
     * Writes the salt to [out]; what is then written to the result is compressed and encrypted.
     * Closing it finishes the file. More than [MAX_PLAIN_BYTES] is refused with [TooBig].
     */
    fun writer(out: OutputStream, key: Key): OutputStream {
        out.write(key.salt)
        return Counting(GZIPOutputStream(key.aead.newEncryptingStream(DropFirst(out), aad(key)), 64 * 1024))
    }

    /**
     * [input] stands right after the salt, which must be [key]'s. Reading gives the data back;
     * a wrong password or any change in the file shows as [BackupCrypto.WrongPasswordOrDamaged].
     */
    fun reader(input: InputStream, key: Key): InputStream {
        val file = Bounded(Source(input), MAX_FILE_BYTES - SALT_BYTES)
        val whole = SequenceInputStream(ByteArrayInputStream(byteArrayOf(TINK_FIRST.toByte())), file)
        val decrypted = Failing(key.aead.newDecryptingStream(whole, aad(key)))
        // (gzip reads its own header at once, so a wrong password shows right here)
        val unpacked = try {
            GZIPInputStream(decrypted, 64 * 1024)
        } catch (e: IOException) {
            throw BackupCrypto.WrongPasswordOrDamaged()
        }
        return Failing(Bounded(unpacked, MAX_PLAIN_BYTES))
    }

    /** Leaves out the first byte written, which must be [TINK_FIRST]. */
    private class DropFirst(out: OutputStream) : FilterOutputStream(out) {
        private var dropped = false
        override fun write(b: Int) {
            if (dropped) out.write(b) else drop(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len <= 0) return
            if (dropped) {
                out.write(b, off, len)
            } else {
                drop(b[off].toInt())
                out.write(b, off + 1, len - 1)
            }
        }
        private fun drop(b: Int) {
            // Anything else would make a file that cannot be read back: better no file at all
            check(b and 0xff == TINK_FIRST)
            dropped = true
        }
    }

    /** Refuses to write past [MAX_PLAIN_BYTES]. */
    private class Counting(out: OutputStream) : FilterOutputStream(out) {
        private var count = 0L
        override fun write(b: Int) {
            if (++count > MAX_PLAIN_BYTES) throw TooBig()
            out.write(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            count += len
            if (count > MAX_PLAIN_BYTES) throw TooBig()
            out.write(b, off, len)
        }
    }

    /** Refuses to read past [limit] bytes: a file that size is not ours. */
    private class Bounded(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0 && ++count > limit) throw BackupCrypto.NotABackup() }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also {
            if (it > 0) {
                count += it
                if (count > limit) throw BackupCrypto.NotABackup()
            }
        }
        override fun skip(n: Long): Long = throw IOException("No skipping")
    }

    /**
     * The file as the phone gives it: a failure to read it is said as such (unchecked, so neither
     * Tink nor gzip turns it into "wrong password or damaged").
     */
    private class Source(input: InputStream) : FilterInputStream(input) {
        override fun read(): Int = try { super.read() } catch (e: IOException) { throw BackupCrypto.FileUnreadable() }
        override fun read(b: ByteArray, off: Int, len: Int): Int = try { super.read(b, off, len) } catch (e: IOException) { throw BackupCrypto.FileUnreadable() }
    }

    /** Tink and gzip say "wrong" as an IOException; the screen says it in one way, never with details. */
    private class Failing(input: InputStream) : FilterInputStream(input) {
        override fun read(): Int = guard { super.read() }
        override fun read(b: ByteArray, off: Int, len: Int): Int = guard { super.read(b, off, len) }
        private inline fun guard(block: () -> Int): Int = try {
            block()
        } catch (e: IOException) {
            throw BackupCrypto.WrongPasswordOrDamaged()
        }
    }
}
