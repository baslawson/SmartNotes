package io.github.baslawson.smartnotes

import android.content.SharedPreferences

/**
 * Where the app was when it was last used, so it opens there again: the screen (a tab, or a note or task), the Notes
 * search and tag filter, and how far each list was scrolled. Kept on the phone only (left out of backups).
 */
class AppState(private val prefs: SharedPreferences?) {
    /** "notes", "tasks", "shopping", "note/5" or "todo/7" (see [placeOf]). */
    var place: String
        get() = prefs?.getString(KEY_PLACE, null) ?: NOTES
        set(value) { if (value != place) prefs?.edit()?.putString(KEY_PLACE, value)?.apply() }

    var notesQuery: String
        get() = prefs?.getString(KEY_QUERY, null).orEmpty()
        set(value) { prefs?.edit()?.putString(KEY_QUERY, value)?.apply() }

    var notesTag: String?
        get() = prefs?.getString(KEY_TAG, null)
        set(value) { prefs?.edit()?.putString(KEY_TAG, value)?.apply() }

    /** The first item shown and how far into it, for the list [key] ("notes", "tasks", "shopping:3"). */
    fun scroll(key: String): Pair<Int, Int> =
        (prefs?.getInt("$KEY_SCROLL$key", 0) ?: 0) to (prefs?.getInt("$KEY_SCROLL$key:offset", 0) ?: 0)

    fun setScroll(key: String, index: Int, offset: Int) {
        prefs?.edit()?.putInt("$KEY_SCROLL$key", index)?.putInt("$KEY_SCROLL$key:offset", offset)?.apply()
    }

    /** One-time tips: a tip once dismissed ("Got it") isn't shown again. Kept through a restore. */
    fun tipSeen(tip: String): Boolean = prefs?.getBoolean("$KEY_TIP$tip", false) ?: false

    fun setTipSeen(tip: String) {
        prefs?.edit()?.putBoolean("$KEY_TIP$tip", true)?.apply()
    }

    /** After a restore the remembered note, task and scroll positions may point at other things: start afresh. */
    fun forgetPlaces() {
        val p = prefs ?: return
        p.edit().apply {
            remove(KEY_PLACE)
            p.all.keys.filter { it.startsWith(KEY_SCROLL) }.forEach { remove(it) }
        }.apply()
    }

    companion object {
        const val NOTES = "notes"
        const val TASKS = "tasks"
        const val SHOPPING = "shopping"
        private const val KEY_PLACE = "place"
        private const val KEY_QUERY = "notes_query"
        private const val KEY_TAG = "notes_tag"
        private const val KEY_SCROLL = "scroll:"
        private const val KEY_TIP = "tip:"

        /**
         * The place to remember for a screen ([route] is its navigation pattern, [id] its note or task): a tab, or the
         * note or task itself; null for screens not reopened (Settings, the recycle bin, a note not saved yet).
         */
        fun placeOf(route: String?, id: Long): String? = when (route?.substringBefore('?')?.substringBefore('/')) {
            NOTES -> NOTES
            TASKS -> TASKS
            SHOPPING -> SHOPPING
            "note" -> if (id > 0) "note/$id" else null
            "todo" -> if (id > 0) "todo/$id" else null
            else -> null
        }

        /** The screens to open, in order, to get back to [place]: a task opens over Tasks, so Back goes there. */
        fun routesFor(place: String): List<String> {
            val id = place.substringAfter('/', "").toLongOrNull()
            return when {
                place == TASKS -> listOf(TASKS)
                place == SHOPPING -> listOf(SHOPPING)
                place.startsWith("note/") && id != null && id > 0 -> listOf("note/$id?title=")
                place.startsWith("todo/") && id != null && id > 0 -> listOf(TASKS, "todo/$id")
                else -> emptyList()
            }
        }
    }
}
