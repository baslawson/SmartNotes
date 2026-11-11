package io.github.baslawson.smartnotes.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything a backup holds. Reminders aren't in it: they are set again from the tasks and lists. */
data class BackupData(
    val createdAt: Long,
    val appVersion: String,
    val notes: List<Note>,
    val todos: List<Todo>,
    val lists: List<ShoppingList>,
    val items: List<ShoppingItem>,
    val history: List<ShoppingHistory>,
    val settings: Map<String, Any?>,
    /** The aisles chosen for items; null: a file from before aisles (a restore keeps the ones there are). */
    val aisles: List<ItemAisle>? = null,
    /** Staples; null: a file from before staples (a restore keeps the ones there are). */
    val staples: List<Staple>? = null,
) {
    /** "42 notes, 7 tasks, 3 shopping lists": what is in use (not in the recycle bin). */
    fun summary(): String {
        fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
        return listOf(
            count(notes.count { it.deletedAt == null }, "note", "notes"),
            count(todos.count { it.deletedAt == null }, "task", "tasks"),
            count(lists.count { it.deletedAt == null }, "shopping list", "shopping lists"),
        ).joinToString(", ")
    }
}

class BackupException(message: String) : Exception(message)

/**
 * A backup file: readable JSON, `{"format": "smartnotes-backup", "version": 1, ...}`. Newer versions of the format
 * can add fields; a file from a newer format version than this app knows is refused, not half-read.
 */
object BackupFormat {
    const val FORMAT = "smartnotes-backup"
    const val VERSION = 1
    const val FILE_PREFIX = "SmartNotes-backup-"
    const val AUTO_PREFIX = "SmartNotes-auto-"

    fun write(data: BackupData): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("createdAt", data.createdAt)
        put("appVersion", data.appVersion)
        put("notes", JSONArray(data.notes.map { n ->
            JSONObject().put("id", n.id).put("title", n.title).put("body", n.body).put("pinned", n.pinned)
                .put("createdAt", n.createdAt).put("updatedAt", n.updatedAt).putOpt("deletedAt", n.deletedAt).put("position", n.position)
        }))
        put("todos", JSONArray(data.todos.map { t ->
            JSONObject().put("id", t.id).put("title", t.title).put("details", t.details).put("done", t.done)
                .put("doneAt", t.doneAt).putOpt("dueDate", t.dueDate).putOpt("dueTime", t.dueTime).put("remind", t.remind)
                .put("sound", t.sound).put("repeat", t.repeat).put("repeatEvery", t.repeatEvery).put("repeatDay", t.repeatDay)
                .put("priority", t.priority).put("createdAt", t.createdAt).put("updatedAt", t.updatedAt)
                .putOpt("deletedAt", t.deletedAt).put("position", t.position)
        }))
        put("shoppingLists", JSONArray(data.lists.map { l ->
            JSONObject().put("id", l.id).put("name", l.name).putOpt("date", l.date).putOpt("remindAt", l.remindAt)
                .put("sound", l.sound).put("createdAt", l.createdAt).putOpt("deletedAt", l.deletedAt).put("position", l.position)
        }))
        put("shoppingItems", JSONArray(data.items.map { i ->
            JSONObject().put("id", i.id).put("listId", i.listId).put("name", i.name).put("key", i.key)
                .put("quantity", i.quantity).put("checked", i.checked).put("addedAt", i.addedAt).put("checkedAt", i.checkedAt)
                .put("position", i.position)
        }))
        put("shoppingHistory", JSONArray(data.history.map { h ->
            JSONObject().put("key", h.key).put("name", h.name).put("useCount", h.useCount).put("lastUsed", h.lastUsed)
        }))
        data.aisles?.let { a -> put("aisles", JSONArray(a.map { JSONObject().put("key", it.key).put("aisle", it.aisle) })) }
        data.staples?.let { st ->
            put("staples", JSONArray(st.map { JSONObject().put("key", it.key).put("name", it.name).put("quantity", it.quantity).put("addedAt", it.addedAt) }))
        }
        put("settings", JSONObject(data.settings.filterValues { it != null }))
    }.toString(2)

    /** Reads a backup file; throws [BackupException] (with a message for the user) when it isn't one this app can use. */
    fun read(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            // Cut short (a copy that didn't finish, say) is damaged, not "something else".
            val ours = text.trimStart().startsWith("{") && listOf(FORMAT, "\"shoppingLists\"", "\"todos\"").any { it in text }
            throw BackupException(if (ours) DAMAGED else "This file isn't a SmartNotes backup.")
        }
        if (root.optString("format") != FORMAT) throw BackupException("This file isn't a SmartNotes backup.")
        val version = root.optInt("version", 0)
        if (version > VERSION) throw BackupException("This backup was made by a newer SmartNotes. Update the app first.")
        if (version < 1) throw BackupException(DAMAGED)
        try {
            // Every part must be there (an empty list is fine): a missing one would otherwise restore as "nothing".
            val notes = root.objects("notes").map { o ->
                Note(o.getLong("id"), o.text("title"), o.text("body"), o.optBoolean("pinned"),
                    o.optLong("createdAt"), o.optLong("updatedAt"), o.longOrNull("deletedAt"), o.optLong("position"))
            }
            val todos = root.objects("todos").map { o ->
                Todo(
                    id = o.getLong("id"), title = o.text("title"), details = o.optString("details"),
                    done = o.optBoolean("done"), doneAt = o.optLong("doneAt"), dueDate = o.stringOrNull("dueDate"),
                    dueTime = o.stringOrNull("dueTime"), remind = o.optBoolean("remind"), sound = o.optString("sound", "DEFAULT"),
                    repeat = o.optString("repeat", "NONE"), repeatEvery = o.optInt("repeatEvery", 1), repeatDay = o.optInt("repeatDay"),
                    priority = o.optInt("priority", 1), createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
                    deletedAt = o.longOrNull("deletedAt"), position = o.optLong("position"),
                )
            }
            val lists = root.objects("shoppingLists").map { o ->
                ShoppingList(o.getLong("id"), o.text("name"), o.stringOrNull("date"), o.stringOrNull("remindAt"),
                    o.optString("sound", "DEFAULT"), o.optLong("createdAt"), o.longOrNull("deletedAt"), o.optLong("position"))
            }
            val listIds = lists.map { it.id }.toSet() + 1L
            val items = root.objects("shoppingItems").map { o ->
                ShoppingItem(o.getLong("id"), o.optLong("listId", 1), o.text("name"), o.text("key"),
                    o.optString("quantity"), o.optBoolean("checked"), o.optLong("addedAt"), o.optLong("checkedAt"), o.optLong("position"))
            }
            if (items.any { it.listId !in listIds }) throw BackupException(DAMAGED)
            val history = root.objects("shoppingHistory").map { o ->
                ShoppingHistory(o.text("key"), o.text("name"), o.optInt("useCount", 1), o.optLong("lastUsed"))
            }
            // Added in 0.8.0 (same format version): older files have neither, and keep what the app has on restore.
            val aisles = if (root.has("aisles")) root.objects("aisles").map { o -> ItemAisle(o.text("key"), o.text("aisle")) } else null
            val staples = if (root.has("staples")) root.objects("staples").map { o ->
                Staple(o.text("key"), o.text("name"), o.optString("quantity"), o.optLong("addedAt"))
            } else null
            if (aisles != null && aisles.map { it.key }.toSet().size != aisles.size) throw BackupException(DAMAGED)
            if (staples != null && staples.map { it.key }.toSet().size != staples.size) throw BackupException(DAMAGED)
            val settings = root.optJSONObject("settings")?.let { s -> s.keys().asSequence().associateWith { s.opt(it) } }.orEmpty()
            requireUnique(notes.map { it.id }, todos.map { it.id }, lists.map { it.id }, items.map { it.id })
            if (history.map { it.key }.toSet().size != history.size) throw BackupException(DAMAGED)
            return BackupData(root.optLong("createdAt"), root.optString("appVersion"), notes, todos, lists, items, history, settings, aisles, staples)
        } catch (e: JSONException) {
            throw BackupException(DAMAGED)
        }
    }

    private const val DAMAGED = "This backup file is damaged."

    /**
     * Automatic backups to delete so only the newest [keep] stay (names carry their time, so they sort by it). Backups
     * made by hand ([FILE_PREFIX]) and other files in the folder are never touched.
     */
    fun toPrune(names: List<String>, keep: Int = 5): List<String> =
        names.filter { it.startsWith(AUTO_PREFIX) && it.endsWith(".json") }.sortedDescending().drop(keep)

    /** Ids are positive and appear once each. */
    private fun requireUnique(vararg ids: List<Long>) {
        if (ids.any { list -> list.size != list.toSet().size || list.any { it <= 0 } }) throw BackupException(DAMAGED)
    }

    /** A required list of objects (throws if it is missing or not a list). */
    private fun JSONObject.objects(name: String): List<JSONObject> =
        getJSONArray(name).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    /** A required string: null, a number or anything else that isn't text means the file is damaged. */
    private fun JSONObject.text(name: String): String = opt(name) as? String ?: throw BackupException(DAMAGED)

    private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else optString(name)
    private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else optLong(name)
}
