package org.jellyfin.androidtv.ui.settings.screen.seerr

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.seerr.SeerrAccount
import org.jellyfin.androidtv.data.seerr.SeerrRepository
import org.jellyfin.androidtv.data.seerr.SeerrState
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListMessage
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.seerr.SeerrConnectActivity
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

private val SeerrAccount.host
	get() = url.toHttpUrlOrNull()?.let { "${it.host}:${it.port}" } ?: url

/**
 * JellyChan: connection to Seerr (status, connect, change, sign in again, disconnect).
 */
@Composable
fun SettingsSeerrScreen() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val seerrRepository = koinInject<SeerrRepository>()
	val state by seerrRepository.state.collectAsState()

	val connect = { context.startActivity(Intent(context, SeerrConnectActivity::class.java)) }
	val disconnect = { scope.launch { seerrRepository.disconnect() }; Unit }

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.app_name_release).uppercase()) },
				headingContent = { Text(stringResource(R.string.jc_seerr)) },
			)
		}

		item {
			ListMessage {
				Text(
					when (val current = state) {
						SeerrState.NotConfigured -> stringResource(R.string.jc_seerr_status_not_configured)
						is SeerrState.Connecting -> stringResource(R.string.jc_seerr_status_connecting)
						is SeerrState.Connected -> stringResource(
							R.string.jc_seerr_status_connected,
							current.account.host,
							current.user.name.orEmpty(),
							current.version.orEmpty(),
						)

						is SeerrState.SessionExpired -> stringResource(R.string.jc_seerr_status_expired)
						is SeerrState.Unavailable -> stringResource(R.string.jc_seerr_status_unavailable, current.account.host)
					}
				)
			}
		}

		when (state) {
			SeerrState.NotConfigured -> item {
				ListButton(
					headingContent = { Text(stringResource(R.string.jc_seerr_connect)) },
					onClick = connect,
					modifier = Modifier.focusKey("seerr_connect", initialFocus = true),
				)
			}

			is SeerrState.Connecting -> Unit

			is SeerrState.Connected -> {
				item {
					ListButton(
						headingContent = { Text(stringResource(R.string.jc_seerr_change)) },
						onClick = connect,
						modifier = Modifier.focusKey("seerr_change", initialFocus = true),
					)
				}
				item {
					ListButton(
						headingContent = { Text(stringResource(R.string.jc_seerr_disconnect)) },
						onClick = { disconnect() },
						modifier = Modifier.focusKey("seerr_disconnect"),
					)
				}
			}

			is SeerrState.SessionExpired -> {
				item {
					ListButton(
						headingContent = { Text(stringResource(R.string.jc_seerr_sign_in_again)) },
						onClick = connect,
						modifier = Modifier.focusKey("seerr_sign_in_again", initialFocus = true),
					)
				}
				item {
					ListButton(
						headingContent = { Text(stringResource(R.string.jc_seerr_disconnect)) },
						onClick = { disconnect() },
						modifier = Modifier.focusKey("seerr_disconnect"),
					)
				}
			}

			is SeerrState.Unavailable -> {
				item {
					ListButton(
						headingContent = { Text(stringResource(R.string.jc_seerr_retry)) },
						onClick = { scope.launch { seerrRepository.refresh() } },
						modifier = Modifier.focusKey("seerr_retry", initialFocus = true),
					)
				}
				item {
					ListButton(
						headingContent = { Text(stringResource(R.string.jc_seerr_change)) },
						onClick = connect,
						modifier = Modifier.focusKey("seerr_change"),
					)
				}
				item {
					ListButton(
						headingContent = { Text(stringResource(R.string.jc_seerr_disconnect)) },
						onClick = { disconnect() },
						modifier = Modifier.focusKey("seerr_disconnect"),
					)
				}
			}
		}
	}
}
