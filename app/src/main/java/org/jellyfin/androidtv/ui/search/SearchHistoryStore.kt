package org.jellyfin.androidtv.ui.search

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/**
 * JellyChan: recent search queries, stored locally per user (never sent to the server).
 */
class SearchHistoryStore(context: Context) {
	companion object {
		private const val PREFERENCES_NAME = "jc_search_history"
		private const val MAX_ENTRIES = 8
		private const val SEPARATOR = '\n'
	}

	private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

	fun get(userId: UUID?): List<String> {
		if (userId == null) return emptyList()
		return preferences.getString(userId.toString(), null)
			?.split(SEPARATOR)
			?.filter { it.isNotBlank() }
			.orEmpty()
	}

	fun add(userId: UUID?, query: String): List<String> {
		val trimmed = query.trim().replace(SEPARATOR, ' ')
		if (userId == null || trimmed.isBlank()) return get(userId)

		val updated = (listOf(trimmed) + get(userId).filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX_ENTRIES)
		preferences.edit { putString(userId.toString(), updated.joinToString(SEPARATOR.toString())) }
		return updated
	}

	fun clear(userId: UUID?) {
		if (userId == null) return
		preferences.edit { remove(userId.toString()) }
	}
}
