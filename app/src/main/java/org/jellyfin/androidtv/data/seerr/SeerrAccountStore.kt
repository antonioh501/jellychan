package org.jellyfin.androidtv.data.seerr

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID

/**
 * Saved Seerr connections, one for each user of this app.
 */
interface SeerrAccountStore {
	fun get(userId: UUID): SeerrAccount?
	fun put(userId: UUID, account: SeerrAccount)
	fun remove(userId: UUID)
}

/**
 * Keeps the accounts in private app storage. The file is not listed in the backup rules, so
 * sessions never leave the device.
 */
class SharedPreferencesSeerrAccountStore(context: Context) : SeerrAccountStore {
	private val preferences = context.getSharedPreferences("seerr_accounts", Context.MODE_PRIVATE)

	override fun get(userId: UUID): SeerrAccount? {
		val value = preferences.getString(userId.toString(), null) ?: return null

		return try {
			Json.decodeFromString<SeerrAccount>(value)
		} catch (err: SerializationException) {
			Timber.w(err, "Ignoring unreadable Seerr account")
			null
		}
	}

	override fun put(userId: UUID, account: SeerrAccount) = preferences.edit {
		putString(userId.toString(), Json.encodeToString(account))
	}

	override fun remove(userId: UUID) = preferences.edit {
		remove(userId.toString())
	}
}
