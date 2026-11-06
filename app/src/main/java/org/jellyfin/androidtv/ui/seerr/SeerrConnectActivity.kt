package org.jellyfin.androidtv.ui.seerr

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.seerr.SeerrAccount
import org.jellyfin.androidtv.data.seerr.SeerrLoginMethod
import org.jellyfin.androidtv.data.seerr.SeerrLoginResult
import org.jellyfin.androidtv.data.seerr.SeerrRepository
import org.jellyfin.androidtv.data.seerr.SeerrState
import org.jellyfin.androidtv.databinding.JcActivitySeerrConnectBinding
import org.jellyfin.androidtv.util.applyTheme
import org.koin.android.ext.android.inject

/**
 * Form to connect to a Seerr server or to sign in again.
 */
class SeerrConnectActivity : FragmentActivity() {
	private val seerrRepository: SeerrRepository by inject()
	private lateinit var binding: JcActivitySeerrConnectBinding
	private var busy = false

	private val selectedMethod
		get() = when (binding.jcSeerrMethod.checkedRadioButtonId) {
			R.id.jc_seerr_method_local -> SeerrLoginMethod.LOCAL
			else -> SeerrLoginMethod.SERVER_ACCOUNT
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		applyTheme()
		super.onCreate(savedInstanceState)

		binding = JcActivitySeerrConnectBinding.inflate(layoutInflater)
		setContentView(binding.root)

		binding.jcSeerrMethod.setOnCheckedChangeListener { _, _ -> updateUsernameLabel() }
		binding.jcSeerrConfirm.setOnClickListener { submit() }
		binding.jcSeerrPassword.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				submit()
				true
			} else false
		}

		savedAccount()?.let(::fillIn)
		if (binding.jcSeerrAddress.text.isBlank()) binding.jcSeerrAddress.requestFocus()
		else binding.jcSeerrUsername.requestFocus()
	}

	private fun savedAccount(): SeerrAccount? = when (val state = seerrRepository.state.value) {
		SeerrState.NotConfigured -> null
		is SeerrState.Connecting -> state.account
		is SeerrState.Connected -> state.account
		is SeerrState.SessionExpired -> state.account
		is SeerrState.Unavailable -> state.account
	}

	private fun fillIn(account: SeerrAccount) {
		binding.jcSeerrAddress.setText(account.url)
		binding.jcSeerrMethod.check(
			when (account.method) {
				SeerrLoginMethod.SERVER_ACCOUNT -> R.id.jc_seerr_method_server
				SeerrLoginMethod.LOCAL -> R.id.jc_seerr_method_local
			}
		)
		updateUsernameLabel()
	}

	private fun updateUsernameLabel() {
		binding.jcSeerrUsernameLabel.setText(
			when (selectedMethod) {
				SeerrLoginMethod.SERVER_ACCOUNT -> R.string.input_username
				SeerrLoginMethod.LOCAL -> R.string.jc_seerr_email
			}
		)
	}

	private fun submit() {
		if (busy) return

		val address = binding.jcSeerrAddress.text.toString()
		val username = binding.jcSeerrUsername.text.toString().trim()
		val password = binding.jcSeerrPassword.text.toString()

		if (address.isBlank() || username.isEmpty() || password.isEmpty()) {
			showMessage(R.string.jc_seerr_error_fields_empty)
			return
		}

		busy = true
		showMessage(R.string.jc_seerr_status_connecting)

		lifecycleScope.launch {
			val result = seerrRepository.login(address, selectedMethod, username, password)
			busy = false

			when (result) {
				SeerrLoginResult.SUCCESS -> finish()
				SeerrLoginResult.INVALID_ADDRESS -> showMessage(R.string.jc_seerr_error_address)
				SeerrLoginResult.UNREACHABLE -> showMessage(R.string.jc_seerr_error_unreachable)
				SeerrLoginResult.NOT_SEERR -> showMessage(R.string.jc_seerr_error_not_seerr)
				SeerrLoginResult.METHOD_DISABLED -> showMessage(R.string.jc_seerr_error_method_disabled)
				SeerrLoginResult.INVALID_CREDENTIALS -> {
					binding.jcSeerrPassword.text.clear()
					binding.jcSeerrPassword.requestFocus()
					showMessage(R.string.jc_seerr_error_credentials)
				}
			}
		}
	}

	private fun showMessage(@StringRes message: Int) {
		binding.jcSeerrMessage.setText(message)
	}
}
