package io.github.baslawson.smartnotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private val data = BackupData(
        createdAt = 1_762_800_000_000, appVersion = "0.2.1",
        notes = listOf(
            Note(1, "Shopping ideas", "eggs\n#home \"quoted\"", pinned = true, createdAt = 5, updatedAt = 6, position = -3),
            Note(2, "Old", "binned", createdAt = 1, updatedAt = 2, deletedAt = 3),
        ),
        todos = listOf(
            Todo(3, "Pay rent", details = "by bank", dueDate = "2026-12-01", dueTime = "09:00", remind = true, sound = "ALARM",
                repeat = "MONTHLY", repeatEvery = 1, repeatDay = 31, priority = 2, createdAt = 7, updatedAt = 8, position = 4),
            Todo(4, "Done one", done = true, doneAt = 9),
        ),
        lists = listOf(ShoppingList(1, "Shopping", position = 2), ShoppingList(5, "", date = "2026-11-12", remindAt = "2026-11-12T10:00", deletedAt = 4)),
        items = listOf(ShoppingItem(10, 1, "Milk", "milk", "2", checked = true, addedAt = 1, checkedAt = 2, position = 7), ShoppingItem(11, 5, "Bread", "bread")),
        history = listOf(ShoppingHistory("milk", "Milk", 4, 99)),
        settings = mapOf("theme" to "DARK", "textScale" to 1.15, "keepFor" to "WEEK"),
    )

    @Test fun roundTripKeepsEverything() {
        val back = BackupFormat.read(BackupFormat.write(data))
        assertEquals(data.copy(settings = emptyMap()), back.copy(settings = emptyMap()))
        assertEquals("DARK", back.settings["theme"])
        assertEquals(1.15, (back.settings["textScale"] as Number).toDouble(), 0.0)
        assertEquals("1 note, 2 tasks, 1 shopping list", back.summary())
    }

    @Test fun aislesAndStaplesGoIntoTheBackup() {
        val full = data.copy(aisles = listOf(ItemAisle("milk", "SNACKS")), staples = listOf(Staple("milk", "Milk", "2x", 5)))
        assertEquals(full.copy(settings = emptyMap()), BackupFormat.read(BackupFormat.write(full)).copy(settings = emptyMap()))
        // Empty ones are kept as empty (a restore clears them), not as "from before".
        val none = data.copy(aisles = emptyList(), staples = emptyList())
        assertEquals(emptyList<Staple>(), BackupFormat.read(BackupFormat.write(none)).staples)
        // A file from before 0.8.0 has neither: null, so a restore keeps what the app has.
        val old = BackupFormat.read(BackupFormat.write(data))
        assertEquals(null, old.aisles)
        assertEquals(null, old.staples)
        // The same key twice is a damaged file.
        val twice = data.copy(staples = listOf(Staple("milk", "Milk"), Staple("milk", "milk")))
        assertThrows(BackupException::class.java) { BackupFormat.read(BackupFormat.write(twice)) }
    }

    @Test fun olderBackupsWithoutPositionsReadAsZero() {
        val old = org.json.JSONObject(BackupFormat.write(data)).apply {
            for (part in listOf("notes", "todos", "shoppingLists", "shoppingItems")) {
                val a = getJSONArray(part)
                for (i in 0 until a.length()) a.getJSONObject(i).remove("position")
            }
        }
        val back = BackupFormat.read(old.toString())
        assertEquals(listOf(0L, 0L), back.notes.map { it.position })
        assertEquals(listOf(0L, 0L), back.lists.map { it.position })
    }

    @Test fun refusesFilesThatArentBackups() {
        assertMessage("This file isn't a SmartNotes backup.") { BackupFormat.read("hello") }
        assertMessage("This file isn't a SmartNotes backup.") { BackupFormat.read("""{"format":"other","version":1}""") }
    }

    @Test fun refusesBackupsFromANewerApp() {
        val newer = BackupFormat.write(data).replace("\"version\": 1", "\"version\": 2")
        assertMessage("This backup was made by a newer SmartNotes. Update the app first.") { BackupFormat.read(newer) }
    }

    @Test fun refusesDamagedBackups() {
        assertMessage("This backup file is damaged.") { BackupFormat.read("""{"format":"smartnotes-backup","version":1,"notes":[{"title":"no id"}]}""") }
        assertMessage("This backup file is damaged.") { BackupFormat.read("""{"format":"smartnotes-backup","version":1,"notes":[{"id":1},{"id":1}]}""") }
        assertMessage("This backup file is damaged.") { BackupFormat.read("""{"format":"smartnotes-backup","version":0}""") }
    }

    @Test fun refusesBackupsWithPartsMissingOrWrong() {
        val full = org.json.JSONObject(BackupFormat.write(data))
        for (part in listOf("notes", "todos", "shoppingLists", "shoppingItems", "shoppingHistory")) {
            val missing = org.json.JSONObject(full.toString()).apply { remove(part) }
            assertMessage("This backup file is damaged.") { BackupFormat.read(missing.toString()) }
            val wrong = org.json.JSONObject(full.toString()).apply { put(part, "oops") }
            assertMessage("This backup file is damaged.") { BackupFormat.read(wrong.toString()) }
        }
        // Empty is fine.
        assertEquals(0, BackupFormat.read(BackupFormat.write(data.copy(notes = emptyList()))).notes.size)
    }

    @Test fun refusesItemsOfListsThatArentThereAndBadIds() {
        val lost = BackupFormat.write(data.copy(items = data.items + ShoppingItem(12, 77, "Lost", "lost")))
        assertMessage("This backup file is damaged.") { BackupFormat.read(lost) }
        assertMessage("This backup file is damaged.") { BackupFormat.read(BackupFormat.write(data.copy(notes = listOf(Note(0, "x", "y"))))) }
        val nullBody = org.json.JSONObject(BackupFormat.write(data)).apply {
            getJSONArray("notes").getJSONObject(1).put("body", org.json.JSONObject.NULL)
        }.toString()
        assertMessage("This backup file is damaged.") { BackupFormat.read(nullBody) }
    }

    @Test fun aCutShortBackupIsDamaged() {
        val text = BackupFormat.write(data)
        assertMessage("This backup file is damaged.") { BackupFormat.read(text.take(text.length / 2)) }
    }

    @Test fun pruneKeepsTheNewestFiveAutomaticBackupsOnly() {
        val auto = (1..7).map { "SmartNotes-auto-2026-11-0$it-120000.json" }
        val names = auto + "SmartNotes-backup-2026-01-01-120000.json" + "notes.txt" + "SmartNotes-auto-x.txt"
        assertEquals(listOf("SmartNotes-auto-2026-11-02-120000.json", "SmartNotes-auto-2026-11-01-120000.json"), BackupFormat.toPrune(names))
        assertTrue(BackupFormat.toPrune(auto.take(5)).isEmpty())
    }

    private fun assertMessage(message: String, block: () -> Unit) {
        assertEquals(message, assertThrows(BackupException::class.java, block).message)
    }
}
