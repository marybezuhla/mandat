package ua.mandat.backup

import org.json.JSONArray
import org.json.JSONObject
import ua.mandat.data.DB_VERSION
import ua.mandat.data.MandatDb
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * The contents of a backup as lines of text, one row of a table per line, so that a copy is
 * written and read row by row and never held whole in memory.
 *
 *     {"app":"mandat","kind":"backup","format":2,"schema":16,"created":1700000000000}
 *     {"t":"matters","c":["id","title", …]}        a table and its columns
 *     [1,"Назва", …]                                one row of that table, in the same order
 *     …
 *     {"end":123}                                   how many rows there were; nothing follows
 *
 * A value keeps its kind: null, number, text, or binary as {"b64": …}. The last line is what
 * tells a whole copy from one whose writing stopped halfway.
 */
object BackupLines {
    private const val FORMAT = 2

    /** A longer line is not ours (a row of the longest texts Mandat accepts is far shorter). */
    const val MAX_LINE_CHARS = 8_000_000

    /** Gets each table and row as it is read; may refuse any of them. */
    interface Sink {
        fun table(name: String, columns: List<String>)
        fun row(values: JSONArray)
    }

    /**
     * Writes every table of [db] to [out] as one consistent snapshot. [out] gets plain text: the
     * caller encrypts it ([BackupStream.writer]). Nothing that could not be read back is written.
     */
    fun write(db: MandatDb, out: OutputStream, now: Long = System.currentTimeMillis()): BackupData.Summary {
        val digest = MessageDigest.getInstance("SHA-256")
        val w = DigestOutputStream(out, digest).bufferedWriter(Charsets.UTF_8)
        var rows = 0
        var schema = 0
        fun line(text: String) {
            if (text.length > MAX_LINE_CHARS) throw BackupData.Incompatible()
            w.write(text)
            w.write("\n")
        }
        // One transaction: no change made meanwhile can leave the copy half old and half new
        db.runInTransaction(Runnable {
            val sql = db.openHelper.writableDatabase
            schema = sql.version
            line(JSONObject().put("app", "mandat").put("kind", "backup").put("format", FORMAT).put("schema", schema).put("created", now).toString())
            for (table in BackupData.tableNames(sql)) {
                sql.query("SELECT * FROM `$table`").use { c ->
                    val columns = JSONArray()
                    for (i in 0 until c.columnCount) columns.put(c.getColumnName(i))
                    line(JSONObject().put("t", table).put("c", columns).toString())
                    val keyIndex = if (table == "settings") c.getColumnIndex("key") else -1
                    while (c.moveToNext()) {
                        // The automatic-backup settings (folder and key) never go into a backup file
                        if (keyIndex >= 0 && c.getString(keyIndex)?.startsWith(AutoBackup.PREFIX) == true) continue
                        val row = JSONArray()
                        for (i in 0 until c.columnCount) {
                            val value = BackupData.cell(c, i)
                            if (value is String && value.length > BackupData.MAX_TEXT) throw BackupData.Incompatible()
                            row.put(value)
                        }
                        line(row.toString())
                        rows++
                    }
                }
            }
            line(JSONObject().put("end", rows).toString())
        })
        w.flush()
        return BackupData.Summary(now, schema, rows, digest.digest())
    }

    /**
     * Reads a whole copy from [plain] (already decrypted, see [BackupStream.reader]) to its very
     * end, giving [sink] each table and row. Anything that is not a complete copy for this
     * version is refused: [BackupData.Incompatible], or [BackupData.FromNewer] when a newer
     * Mandat made it.
     */
    fun read(plain: InputStream, sink: Sink?): BackupData.Summary {
        val digest = MessageDigest.getInstance("SHA-256")
        val lines = Lines(InputStreamReader(DigestInputStream(plain, digest), Charsets.UTF_8))
        val head = obj(lines.next() ?: throw BackupData.Incompatible())
        if (head.optString("app") != "mandat" || head.optString("kind") != "backup") throw BackupData.Incompatible()
        val format = head.optInt("format", -1)
        if (format > FORMAT) throw BackupData.FromNewer()
        if (format != FORMAT) throw BackupData.Incompatible()
        val schema = head.optInt("schema", -1)
        val created = head.optLong("created", -1)
        if (schema < 1 || created < 0) throw BackupData.Incompatible()
        if (schema > DB_VERSION) throw BackupData.FromNewer()

        var rows = 0
        var width = -1
        val seen = HashSet<String>()
        while (true) {
            // Ended without the last line: the writing stopped halfway
            val line = lines.next() ?: throw BackupData.Incompatible()
            if (line.startsWith("[")) {
                val row = try { JSONArray(line) } catch (e: Exception) { throw BackupData.Incompatible() }
                if (width < 0 || row.length() != width) throw BackupData.Incompatible()
                sink?.row(row)
                rows++
                continue
            }
            val o = obj(line)
            if (o.has("end")) {
                if (o.optInt("end", -1) != rows) throw BackupData.Incompatible()
                break
            }
            val name = o.optString("t")
            val c = o.optJSONArray("c") ?: throw BackupData.Incompatible()
            val columns = (0 until c.length()).map { c.opt(it) as? String ?: throw BackupData.Incompatible() }
            if (name.isEmpty() || !seen.add(name) || columns.isEmpty() || columns.toSet().size != columns.size) throw BackupData.Incompatible()
            width = columns.size
            sink?.table(name, columns)
        }
        if (lines.next() != null) throw BackupData.Incompatible()
        return BackupData.Summary(created, schema, rows, digest.digest())
    }

    private fun obj(line: String): JSONObject = try {
        JSONObject(line)
    } catch (e: Exception) {
        throw BackupData.Incompatible()
    }

    /** Lines ending in "\n", none longer than [MAX_LINE_CHARS]: a crafted file cannot fill the memory with one line. */
    private class Lines(private val reader: Reader) {
        private val buffer = CharArray(64 * 1024)
        private var pos = 0
        private var len = 0

        /** The next line, or null at the end. */
        fun next(): String? {
            val line = StringBuilder()
            var any = false
            while (true) {
                if (pos == len) {
                    val n = reader.read(buffer)
                    if (n < 0) return if (any) line.toString() else null
                    pos = 0
                    len = n
                    continue
                }
                any = true
                val start = pos
                while (pos < len && buffer[pos] != '\n') pos++
                line.append(buffer, start, pos - start)
                if (line.length > MAX_LINE_CHARS) throw BackupData.Incompatible()
                if (pos < len) {
                    pos++
                    return line.toString()
                }
            }
        }
    }
}
