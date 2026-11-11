package io.github.baslawson.smartnotes.data

import io.github.baslawson.smartnotes.AppLog
import io.github.baslawson.smartnotes.parse.Shopping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

class ShoppingRepository(private val dao: ShoppingDao, private val clock: Clock = Clock.systemDefaultZone()) {
    private val lock = Mutex()

    fun observeItems(): Flow<List<ShoppingItem>> = dao.observeItems()
    fun observeHistory(): Flow<List<ShoppingHistory>> = dao.observeHistory()

    /**
     * Adds "2x milk" as milk, quantity 2x. An item already on the list isn't added twice: it is unticked and,
     * when a quantity was typed, gets the new quantity.
     */
    suspend fun add(input: String) = lock.withLock {
        val parsed = Shopping.parse(input) ?: return@withLock
        val key = Shopping.key(parsed.name)
        val now = clock.millis()
        val existing = dao.findByKey(key)
        if (existing == null) {
            dao.insert(ShoppingItem(name = parsed.name, key = key, quantity = parsed.quantity, addedAt = now))
        } else {
            dao.update(
                existing.copy(
                    quantity = parsed.quantity.ifEmpty { existing.quantity },
                    checked = false,
                    addedAt = if (existing.checked) now else existing.addedAt,
                )
            )
        }
        AppLog.i("Shopping", (if (existing == null) "Added " else "Re-added ") + parsed.name + parsed.quantity.let { if (it.isEmpty()) "" else " ($it)" })
        val past = dao.history(key)
        dao.upsertHistory(ShoppingHistory(key, parsed.name, (past?.useCount ?: 0) + 1, now))
    }

    suspend fun setChecked(item: ShoppingItem, checked: Boolean) = lock.withLock {
        dao.update(item.copy(checked = checked, checkedAt = if (checked) clock.millis() else 0))
    }

    suspend fun delete(id: Long) = lock.withLock { dao.delete(id) }
    suspend fun clearChecked() = lock.withLock {
        dao.clearChecked()
        AppLog.i("Shopping", "Cleared ticked items")
    }
    suspend fun forget(key: String) = lock.withLock { dao.forget(key) }
}
