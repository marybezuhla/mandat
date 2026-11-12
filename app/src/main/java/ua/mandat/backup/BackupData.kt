package ua.mandat.backup

import android.content.ContentValues
import android.database.Cursor
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import ua.mandat.data.MandatDb

/**
 * Turns all of Mandat's tables into a copy and back. Nothing unencrypted is ever written to the
 * phone's storage.
 *
 * A backup is written and read row by row ([BackupLines]), so its size is not held in memory.
 * The older form, one JSON document kept in memory, is still made for the "everything" part of an
 * export and still read from backups made before 0.3.0.
 */
object BackupData {

    private const val FORMAT = 1

    // Internal bookkeeping tables that are never backed up or restored
    private val SKIP = setOf("room_master_table", "android_metadata", "sqlite_sequence")

    /** What a copy holds; [digest] is the SHA-256 of a row-by-row copy's contents (null for a one-document one). */
    class Summary(val createdAt: Long, val schema: Int, val rows: Int, val digest: ByteArray? = null) {
        /** True when [other] describes the very same contents. */
        fun same(other: Summary): Boolean = createdAt == other.createdAt && schema == other.schema && rows == other.rows &&
            digest != null && other.digest != null && digest.contentEquals(other.digest)
    }

    /** Something in the backup does not fit this version of Mandat. */
    class Incompatible : Exception()

    /** The backup was made by a newer Mandat. */
    class FromNewer : Exception()

    /** The tables of this version of Mandat, each with its columns and their declared types. */
    class Layout(val tables: Map<String, Map<String, String>>)

    fun layout(db: MandatDb): Layout = layout(db.openHelper.readableDatabase)

    private fun layout(sql: SupportSQLiteDatabase) = Layout(tableNames(sql).associateWith { columnTypes(sql, it) })

    /**
     * Checks a row-by-row copy against [layout] as it is read: only tables and columns that really
     * exist in this version, and every value of the right kind. [accept] gets each row ready to insert.
     */
    open class Checker(private val layout: Layout) : BackupLines.Sink {
        private var table = ""
        private var columns = emptyList<String>()
        private var types = emptyList<String>()
        /** Copies from before schema 16 have no post for roles. */
        var rolesHavePost = false
            private set

        override fun table(name: String, columns: List<String>) {
            val known = layout.tables[name] ?: throw Incompatible()
            this.types = columns.map { known[it] ?: throw Incompatible() }
            this.columns = columns
            table = name
            if (name == "roles") rolesHavePost = "post" in columns
        }

        override fun row(values: JSONArray) {
            if (values.length() != columns.size) throw Incompatible()
            val row = ContentValues()
            for (i in columns.indices) put(row, columns[i], checkCell(columns[i], types[i], values.get(i)))
            accept(table, row)
        }

        protected open fun accept(table: String, row: ContentValues) {}
    }

    /**
     * Replaces all current data with the row-by-row copy read from [plain], in one transaction:
     * all or nothing. [expected] is what the owner was shown before confirming; anything else
     * (the file changed meanwhile) replaces nothing.
     */
    fun restore(db: MandatDb, plain: java.io.InputStream, expected: Summary) {
        val sql = db.openHelper.writableDatabase
        val layout = layout(sql)
        db.runInTransaction(Runnable {
            clear(sql, layout.tables.keys)
            val checker = object : Checker(layout) {
                override fun accept(table: String, row: ContentValues) {
                    // This phone's automatic-backup settings stay as they are
                    if (table == "settings" && row.getAsString("key")?.startsWith(AutoBackup.PREFIX) == true) return
                    if (sql.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, row) < 0) throw Incompatible()
                }
            }
            if (!BackupLines.read(plain, checker).same(expected)) throw Incompatible()
            fixOlder(sql, layout.tables, checker.rolesHavePost)
        })
    }

    /** Empties every table, except this phone's automatic-backup settings. */
    private fun clear(sql: SupportSQLiteDatabase, tables: Collection<String>) {
        for (table in tables) {
            if (table == "settings") sql.execSQL("DELETE FROM `settings` WHERE substr(`key`, 1, ${AutoBackup.PREFIX.length}) <> '${AutoBackup.PREFIX}'")
            else sql.execSQL("DELETE FROM `$table`")
        }
    }

    /** What a copy from an older version lacks is filled in, as the migrations do. */
    private fun fixOlder(sql: SupportSQLiteDatabase, known: Map<String, Map<String, String>>, rolesHavePost: Boolean) {
        // Backups from before schema 8 have no "publicity" for promises: public ones get it here,
        // so the stored value always agrees with the older public/personal field
        if (known["promises"]?.containsKey("publicity") == true) {
            sql.execSQL("UPDATE `promises` SET `publicity` = 'PUBLIC' WHERE `isPublic` = 1 AND `publicity` <> 'PUBLIC'")
        }
        // Backups from before schema 16 have no post for roles: guessed from the name, as the migration does
        if (known["roles"]?.containsKey("post") == true && !rolesHavePost) {
            val found = ArrayList<Pair<Long, String>>()
            sql.query("SELECT id, name FROM roles WHERE post IS NULL").use { c ->
                while (c.moveToNext()) ua.mandat.data.RolePost.guess(c.getString(1))?.let { found.add(c.getLong(0) to it.name) }
            }
            found.forEach { (id, post) -> sql.execSQL("UPDATE roles SET post = ? WHERE id = ?", arrayOf<Any>(post, id)) }
        }
    }

    fun dump(db: MandatDb): ByteArray {
        val sql = db.openHelper.readableDatabase
        val tables = JSONObject()
        for (table in tableNames(sql)) {
            val columns = JSONArray()
            val rows = JSONArray()
            sql.query("SELECT * FROM `$table`").use { c ->
                for (i in 0 until c.columnCount) columns.put(c.getColumnName(i))
                val keyIndex = if (table == "settings") c.getColumnIndex("key") else -1
                while (c.moveToNext()) {
                    // The automatic-backup settings (folder and key) never go into a backup file
                    if (keyIndex >= 0 && c.getString(keyIndex)?.startsWith(AutoBackup.PREFIX) == true) continue
                    val row = JSONArray()
                    for (i in 0 until c.columnCount) row.put(cell(c, i))
                    rows.put(row)
                }
            }
            tables.put(table, JSONObject().put("columns", columns).put("rows", rows))
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("app", "mandat")
            .put("schema", sql.version)
            .put("created", System.currentTimeMillis())
            .put("tables", tables)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    /** Reads what a backup contains without changing anything. */
    fun summary(json: ByteArray): Summary {
        val root = parse(json)
        val tables = root.getJSONObject("tables")
        var rows = 0
        tables.keys().forEach { rows += tables.getJSONObject(it).getJSONArray("rows").length() }
        return Summary(root.getLong("created"), root.getInt("schema"), rows)
    }

    /** Replaces all current data with the backup, in one transaction: all or nothing. */
    fun restore(db: MandatDb, json: ByteArray) {
        val root = parse(json)
        val sql = db.openHelper.writableDatabase
        // Older backups are fine: their tables and columns still exist (newer tables stay empty).
        // A backup from a newer Mandat is refused.
        if (root.getInt("schema") > sql.version) throw Incompatible()
        val current = tableNames(sql).toSet()
        val tables = root.getJSONObject("tables")
        tables.keys().forEach { if (it !in current) throw Incompatible() }

        // Only columns that really exist in this version are accepted; anything else is refused
        val known = current.associateWith { columnTypes(sql, it) }
        tables.keys().forEach { table ->
            val columns = tables.getJSONObject(table).getJSONArray("columns")
            for (i in 0 until columns.length()) if (columns.getString(i) !in known.getValue(table).keys) throw Incompatible()
        }

        // Room's own transaction: all or nothing, and the screens see the new data afterwards
        db.runInTransaction(Runnable {
            clear(sql, current)
            tables.keys().forEach { table ->
                val t = tables.getJSONObject(table)
                val columns = t.getJSONArray("columns")
                val rows = t.getJSONArray("rows")
                for (r in 0 until rows.length()) {
                    val row = rows.getJSONArray(r)
                    if (row.length() != columns.length()) throw Incompatible()
                    val values = ContentValues()
                    for (i in 0 until columns.length()) {
                        val column = columns.getString(i)
                        // A value of the wrong kind refuses the file; a number outside what the screens
                        // can show is brought inside it, so a restored backup never makes Mandat fail
                        val value = checkCell(column, known.getValue(table).getValue(column), row.get(i))
                        put(values, column, value)
                    }
                    if (table == "settings" && values.getAsString("key")?.startsWith(AutoBackup.PREFIX) == true) continue
                    if (sql.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values) < 0) {
                        throw Incompatible()
                    }
                }
            }
            val rolesCols = tables.optJSONObject("roles")?.optJSONArray("columns")
            val hasPost = rolesCols != null && (0 until rolesCols.length()).any { rolesCols.getString(it) == "post" }
            fixOlder(sql, known, hasPost)
        })
    }

    private fun parse(json: ByteArray): JSONObject {
        val root = try {
            JSONObject(String(json, Charsets.UTF_8))
        } catch (e: Exception) {
            throw Incompatible()
        }
        if (root.optString("app") != "mandat" || root.optInt("format") != FORMAT) throw Incompatible()
        return root
    }

    /** Each column with its declared type (INTEGER, TEXT, REAL or BLOB). */
    private fun columnTypes(sql: SupportSQLiteDatabase, table: String): Map<String, String> {
        val types = HashMap<String, String>()
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIndex = c.getColumnIndexOrThrow("name")
            val typeIndex = c.getColumnIndexOrThrow("type")
            while (c.moveToNext()) types[c.getString(nameIndex)] = c.getString(typeIndex).uppercase(java.util.Locale.ROOT)
        }
        return types
    }

    /** Calendar days: about five centuries either way of 1970 (the screens show no others). */
    private val DAY_COLUMNS = setOf("day", "due", "endDay", "untilDay", "checkIn", "lastContact", "stanceSince")
    private const val MAX_DAY = 200_000L
    internal const val MAX_TEXT = 1_000_000

    /** Ranges the screens rely on. */
    private val RANGES = mapOf(
        "startMin" to 0L..1439L, "endMin" to 0L..1440L, "energy" to 1L..5L, "mood" to 1L..5L,
        "influence" to 1L..3L, "reach" to 1L..3L, "risk" to 1L..3L, "remindBefore" to 0L..60L,
        "votesNeeded" to 1L..10_000L, "population" to 1L..20_000_000L,
    )

    private fun checkCell(column: String, type: String, value: Any): Any {
        if (value == JSONObject.NULL) return value
        return when (type) {
            "INTEGER" -> {
                var v = when (value) {
                    is Int -> value.toLong()
                    is Long -> value
                    else -> throw Incompatible()
                }
                if (column in DAY_COLUMNS) v = v.coerceIn(-MAX_DAY, MAX_DAY)
                RANGES[column]?.let { v = v.coerceIn(it.first, it.last) }
                v
            }
            "REAL" -> if (value !is Number || (value is Double && !value.isFinite())) throw Incompatible() else value
            "TEXT" -> if (value !is String || value.length > MAX_TEXT) throw Incompatible() else value
            "BLOB" -> if (value !is JSONObject) throw Incompatible() else value
            else -> value
        }
    }

    internal fun tableNames(sql: SupportSQLiteDatabase): List<String> {
        val names = mutableListOf<String>()
        sql.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)
                if (name !in SKIP && !name.startsWith("sqlite_")) names += name
            }
        }
        return names
    }

    // Each cell keeps its type: null, number, text, or binary (marked as {"b64": …})
    internal fun cell(c: Cursor, i: Int): Any = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).also { if (!it.isFinite()) throw Incompatible() }
        Cursor.FIELD_TYPE_BLOB -> JSONObject().put("b64", Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP))
        else -> c.getString(i)
    }

    private fun put(values: ContentValues, column: String, value: Any) {
        when (value) {
            JSONObject.NULL -> values.putNull(column)
            is Int -> values.put(column, value.toLong())
            is Long -> values.put(column, value)
            is Double -> values.put(column, value)
            is String -> values.put(column, value)
            is JSONObject -> values.put(column, Base64.decode(value.getString("b64"), Base64.NO_WRAP))
            else -> throw Incompatible()
        }
    }
}
