package ua.mandat

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.mandat.backup.BackupCrypto
import ua.mandat.backup.BackupData
import ua.mandat.backup.BackupFile
import ua.mandat.backup.BackupLines
import ua.mandat.backup.BackupStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** The backup file written in parts: what was written reads back, and nothing else reads as a copy. */
class BackupStreamTest {
    // Small settings so the tests run fast; the app uses BackupCrypto.DEFAULT
    private val fast = BackupCrypto.KdfParams(memoryKiB = 8 * 1024, iterations = 1, parallelism = 1)
    private val pw = "тест-пароль-для-копії".toCharArray()

    private val head = """{"app":"mandat","kind":"backup","format":2,"schema":${ua.mandat.data.DB_VERSION},"created":5}"""
    private val table = """{"t":"settings","c":["key","value"]}"""
    private val copy = "$head\n$table\n[\"mode\",\"CITIZEN\"]\n[\"нотатка\",\"рядок\\nдругий\"]\n{\"end\":2}\n"

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun seal(data: ByteArray, key: BackupStream.Key = BackupStream.newKey(pw, fast)): ByteArray {
        val out = ByteArrayOutputStream()
        BackupStream.writer(out, key).use { it.write(data) }
        return out.toByteArray()
    }

    private fun seal(text: String) = seal(text.toByteArray(Charsets.UTF_8))

    private fun keyOf(file: ByteArray, password: CharArray = pw) = BackupStream.keyFor(file.copyOf(BackupStream.SALT_BYTES), password, fast)

    private fun unseal(file: ByteArray, password: CharArray = pw): ByteArray =
        BackupFile.open(ByteArrayInputStream(file), keyOf(file, password)).use { it.readBytes() }

    private class Rows : BackupLines.Sink {
        val got = ArrayList<String>()
        override fun table(name: String, columns: List<String>) { got += "$name:" + columns.joinToString(",") }
        override fun row(values: JSONArray) { got += (0 until values.length()).joinToString("|") { values.get(it).toString() } }
    }

    private fun read(text: String, sink: BackupLines.Sink? = null) = BackupLines.read(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), sink)

    @Test fun roundTripRowByRow() {
        val file = seal(copy)
        val rows = Rows()
        val s = BackupFile.read(ByteArrayInputStream(file), keyOf(file), rows)
        assertEquals(5L, s.createdAt)
        assertEquals(2, s.rows)
        assertEquals(listOf("settings:key,value", "mode|CITIZEN", "нотатка|рядок\nдругий"), rows.got)
        // The same contents give the same fingerprint, whatever the file
        val again = seal(copy)
        assertTrue(s.same(BackupFile.read(ByteArrayInputStream(again), keyOf(again))))
        assertFalse(s.same(read(copy.replace("CITIZEN", "OFFICIAL"))))
    }

    @Test fun opensAFileMadeInTheRecoveryScriptsLayout() {
        // Made in Python from the layout scripts/decrypt_backup.py describes (not by Tink): both read the same file
        val file = hex(
            "000102030405060708090a0b0c0d0e0f6465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f80818283c8c9cacbcccdce" +
                "6cd2d233d019251272ef9022edfafc06187973eb9ba319cf7396cfab4c3acd46b8d9d7e2fda261fead21fdd1ae055532840dcc44e5d1" +
                "c63a43b01e36f62cec7538f5c0008cd7f1004158cf7e21cdef036f2c732307b4a19d3acfedd737c48a3d7c0ae48596e2bd926e999a19" +
                "1b2a5a6e19ef906e85c39477da17cf263fa1ba72b7f47d6f122d9e114978876e6dbb9a1c53a52f575cc8be962ef5ba1fade299909f6f" +
                "0f4fa9507155298de3a68ee7b7de0d09b882692adff1dcc19b61237e63281d360be6b6a797",
        )
        val rows = Rows()
        val key = BackupStream.keyFor(file.copyOf(16), "Тестовий пароль 2026".toCharArray(), fast)
        // (written for schema 16: read as it is, whatever today's schema)
        val plain = BackupFile.open(ByteArrayInputStream(file), key).use { String(it.readBytes(), Charsets.UTF_8) }
        assertTrue(plain.startsWith("""{"app":"mandat","kind":"backup","format":2,"schema":16,"created":5}"""))
        assertTrue(plain.endsWith("[\"нотатка\",\"Привіт, світе\"]\n{\"end\":2}\n"))
        assertEquals(2, read(plain, rows).rows)
        assertEquals(listOf("settings:key,value", "mode|CITIZEN", "нотатка|Привіт, світе"), rows.got)
    }

    @Test fun compressedAndWithoutAnyMarker() {
        val text = head + "\n" + table + "\n" + (1..3000).joinToString("") { "[\"k$it\",\"${"текст ".repeat(40)}\"]\n" } + "{\"end\":3000}\n"
        val file = seal(text)
        // Repeated words: compression makes the file far smaller than the text
        assertTrue(file.size < text.length / 10)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("mandat", ignoreCase = true))
        // No byte is the same in every file, not even the one Tink always writes first (left out)
        val files = (1..24).map { seal("x") }
        for (i in 0 until files.minOf { it.size }) assertTrue("byte $i", files.map { it[i] }.toSet().size > 1)
    }

    @Test fun wrongPasswordAnyChangeOrCutIsRefused() {
        val file = seal(copy)
        expect<BackupCrypto.WrongPasswordOrDamaged> { unseal(file, "інший-пароль-зовсім".toCharArray()) }
        for (i in file.indices step 7) {
            val bad = file.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            expect<BackupCrypto.WrongPasswordOrDamaged> { unseal(bad) }
        }
        // Cut short anywhere, or with something added: never taken as a whole copy
        for (cut in listOf(file.size - 1, file.size - 20, file.size / 2, 60)) {
            expect<BackupCrypto.WrongPasswordOrDamaged> { unseal(file.copyOf(cut)) }
        }
        expect<BackupCrypto.WrongPasswordOrDamaged> { unseal(file + 0) }
        expect<BackupCrypto.NotABackup> { unseal(file.copyOf(10)) }
        // A key made for another file's salt is not tried at all
        expect<BackupCrypto.WrongPasswordOrDamaged> { BackupFile.open(ByteArrayInputStream(file), BackupStream.newKey(pw, fast)) }
    }

    @Test fun partsCutSwappedOrRepeatedAreRefused() {
        // Random bytes do not compress: more than three 1 MiB parts
        val data = ByteArray(3_500_000).also { java.util.Random(7).nextBytes(it) }
        val file = seal(data)
        assertTrue(unseal(file).contentEquals(data))
        val seg = 1 shl 20
        // The first part holds the salt and Tink's header (less its first byte): parts 2 and 3 start here
        val p1 = seg + 15
        val p2 = 2 * seg + 15
        expect<BackupCrypto.WrongPasswordOrDamaged> { unseal(file.copyOf(p2)) }
        expect<BackupCrypto.WrongPasswordOrDamaged> { unseal(file.copyOf(p1)) }
        val swapped = file.copyOf().also { f ->
            val a = file.copyOfRange(p1, p1 + seg)
            val b = file.copyOfRange(p2, p2 + seg)
            b.copyInto(f, p1)
            a.copyInto(f, p2)
        }
        expect<BackupCrypto.WrongPasswordOrDamaged> { unseal(swapped) }
        val repeated = file.copyOf().also { f -> file.copyOfRange(p1, p1 + seg).copyInto(f, p2) }
        expect<BackupCrypto.WrongPasswordOrDamaged> { unseal(repeated) }
    }

    @Test fun theStoredKeyOfAutomaticCopiesMakesFilesThePasswordOpens() {
        // (as BackupCrypto.newAutoKey gives them, with the fast settings)
        val salt = ByteArray(BackupStream.SALT_BYTES) { (it * 3).toByte() }
        val key = BackupCrypto.deriveKey(pw, salt, fast)
        val file = seal(copy.toByteArray(Charsets.UTF_8), BackupStream.storedKey(salt, key))
        assertEquals(copy, String(unseal(file), Charsets.UTF_8))
        // Two files with one stored key share only the salt
        val other = seal(copy.toByteArray(Charsets.UTF_8), BackupStream.storedKey(salt, key))
        assertTrue(file.copyOf(16).contentEquals(other.copyOf(16)))
        assertFalse(file.copyOfRange(16, 60).contentEquals(other.copyOfRange(16, 60)))
    }

    @Test fun theSaltIsBoundIntoTheFile() {
        // The same key under another salt at the start of the file: refused, though the key itself is right
        val k = BackupCrypto.deriveKey(pw, ByteArray(BackupStream.SALT_BYTES), fast)
        val a = ByteArray(BackupStream.SALT_BYTES) { 1 }
        val b = ByteArray(BackupStream.SALT_BYTES) { 2 }
        val file = seal(copy.toByteArray(Charsets.UTF_8), BackupStream.storedKey(a, k))
        assertEquals(copy, String(BackupFile.open(ByteArrayInputStream(file), BackupStream.storedKey(a, k)).use { it.readBytes() }, Charsets.UTF_8))
        val moved = b + file.copyOfRange(BackupStream.SALT_BYTES, file.size)
        expect<BackupCrypto.WrongPasswordOrDamaged> { BackupFile.open(ByteArrayInputStream(moved), BackupStream.storedKey(b, k)).use { it.readBytes() } }
    }

    @Test fun anOlderOnePieceFileIsNotTakenForOneInParts() {
        val old = BackupCrypto.encrypt("{\"format\":1}".toByteArray(), pw, fast)
        expect<BackupCrypto.WrongPasswordOrDamaged> { unseal(old) }
        // ...and opens as before with the key the same password and salt give
        assertEquals("{\"format\":1}", String(BackupCrypto.decryptWithKey(old, BackupCrypto.deriveKey(pw, old.copyOf(16), fast)), Charsets.UTF_8))
    }

    @Test fun oddContentsAreRefused() {
        assertEquals(2, read(copy).rows)
        // No last line (the writing stopped halfway), a wrong count, something after the end
        expect<BackupData.Incompatible> { read("$head\n$table\n[\"a\",\"b\"]\n") }
        expect<BackupData.Incompatible> { read("$head\n$table\n[\"a\",\"b\"]\n{\"end\":2}\n") }
        expect<BackupData.Incompatible> { read("$head\n{\"end\":0}\n{\"end\":0}\n") }
        expect<BackupData.Incompatible> { read("") }
        expect<BackupData.Incompatible> { read("{\"end\":0}\n") }
        // A row before any table, a row of another width, the same table twice, a table without columns
        expect<BackupData.Incompatible> { read("$head\n[\"a\",\"b\"]\n{\"end\":1}\n") }
        expect<BackupData.Incompatible> { read("$head\n$table\n[\"a\"]\n{\"end\":1}\n") }
        expect<BackupData.Incompatible> { read("$head\n$table\n$table\n{\"end\":0}\n") }
        expect<BackupData.Incompatible> { read("$head\n{\"t\":\"settings\",\"c\":[]}\n{\"end\":0}\n") }
        expect<BackupData.Incompatible> { read("$head\n{\"t\":\"settings\",\"c\":[\"key\",\"key\"]}\n{\"end\":0}\n") }
        // Not ours, a line far too long, a newer format, a newer database
        expect<BackupData.Incompatible> { read(head.replace("mandat", "other") + "\n{\"end\":0}\n") }
        expect<BackupData.Incompatible> { read("$head\n$table\n[\"a\",\"" + "x".repeat(BackupLines.MAX_LINE_CHARS) + "\"]\n{\"end\":1}\n") }
        expect<BackupData.FromNewer> { read(head.replace("\"format\":2", "\"format\":3") + "\n") }
        expect<BackupData.FromNewer> { read(head.replace("\"schema\":${ua.mandat.data.DB_VERSION}", "\"schema\":${ua.mandat.data.DB_VERSION + 1}") + "\n{\"end\":0}\n") }
        // An empty copy is a copy
        assertEquals(0, read("$head\n{\"end\":0}\n").rows)
    }

    private inline fun <reified E : Throwable> expect(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            assertTrue("Expected ${E::class.simpleName}, got $e", e is E)
            return
        }
        throw AssertionError("Expected ${E::class.simpleName}")
    }
}
