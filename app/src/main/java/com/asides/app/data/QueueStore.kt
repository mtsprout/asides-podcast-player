package com.asides.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The ordering rules of the play queue (first = plays next), kept free of Android so they can be tested. */
object QueueOps {
    /** [added] go to the top in the order given; any of them already queued move up rather than repeat. */
    fun addToTop(queue: List<Long>, added: List<Long>): List<Long> {
        val top = added.distinct()
        return top + queue.filter { it !in top }
    }

    /** Moves the entry at [from] to position [to]; out-of-range requests leave the queue alone. */
    fun move(queue: List<Long>, from: Int, to: Int): List<Long> {
        if (from !in queue.indices || to !in queue.indices || from == to) return queue
        return queue.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun encode(queue: List<Long>) = queue.joinToString(",")

    fun decode(text: String?): List<Long> =
        text.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
}

/**
 * The play queue: episode ids in the order they will play, kept in its own small preferences file (not
 * the database, so there is nothing to migrate). Episodes from podcasts you don't follow are saved as
 * ordinary unsubscribed rows before they are queued, so every id here has an episode behind it.
 */
class QueueStore(private val prefs: SharedPreferences) {
    private val state = MutableStateFlow(QueueOps.decode(prefs.getString(KEY, null)))
    val ids: StateFlow<List<Long>> = state

    @Synchronized private fun update(change: (List<Long>) -> List<Long>) {
        val next = change(state.value)
        if (next == state.value) return
        state.value = next
        prefs.edit().putString(KEY, QueueOps.encode(next)).commit()
    }

    fun addToTop(added: List<Long>) = update { QueueOps.addToTop(it, added) }
    fun remove(id: Long) = update { q -> q.filter { it != id } }
    fun move(from: Int, to: Int) = update { QueueOps.move(it, from, to) }
    fun clear() = update { emptyList() }

    private companion object {
        const val KEY = "ids"
    }
}
