package org.jellyfin.androidtv.ui.preference.screen

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.seerr.SeerrAccount
import org.jellyfin.androidtv.data.seerr.SeerrRepository
import org.jellyfin.androidtv.data.seerr.SeerrState
import org.jellyfin.androidtv.ui.preference.dsl.OptionsCategory
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.info
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.ui.seerr.SeerrConnectActivity
import org.koin.android.ext.android.inject

class SeerrPreferencesScreen : OptionsFragment() {
	private val seerrRepository: SeerrRepository by inject()

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				seerrRepository.state.collect { rebuild() }
			}
		}
	}

	override val screen by optionsScreen {
		setTitle(R.string.jc_seerr)

		val state = seerrRepository.state.value

		category {
			info {
				setTitle(R.string.jc_seerr_status)
				content = describe(state)
			}

			when (state) {
				SeerrState.NotConfigured -> connectAction(R.string.jc_seerr_connect)
				is SeerrState.Connecting -> Unit
				is SeerrState.Connected -> {
					connectAction(R.string.jc_seerr_change)
					disconnectAction()
				}

				is SeerrState.SessionExpired -> {
					connectAction(R.string.jc_seerr_sign_in_again)
					disconnectAction()
				}

				is SeerrState.Unavailable -> {
					action {
						setTitle(R.string.jc_seerr_retry)
						onActivate = { lifecycleScope.launch { seerrRepository.refresh() } }
					}
					connectAction(R.string.jc_seerr_change)
					disconnectAction()
				}
			}
		}
	}

	private fun describe(state: SeerrState) = when (state) {
		SeerrState.NotConfigured -> getString(R.string.jc_seerr_status_not_configured)
		is SeerrState.Connecting -> getString(R.string.jc_seerr_status_connecting)
		is SeerrState.Connected -> getString(
			R.string.jc_seerr_status_connected,
			state.account.host,
			state.user.name.orEmpty(),
			state.version.orEmpty(),
		)

		is SeerrState.SessionExpired -> getString(R.string.jc_seerr_status_expired)
		is SeerrState.Unavailable -> getString(R.string.jc_seerr_status_unavailable, state.account.host)
	}

	private val SeerrAccount.host
		get() = url.toHttpUrlOrNull()?.let { "${it.host}:${it.port}" } ?: url

	private fun OptionsCategory.connectAction(title: Int) = action {
		setTitle(title)
		onActivate = { startActivity(Intent(requireContext(), SeerrConnectActivity::class.java)) }
	}

	private fun OptionsCategory.disconnectAction() = action {
		setTitle(R.string.jc_seerr_disconnect)
		onActivate = { lifecycleScope.launch { seerrRepository.disconnect() } }
	}
}
