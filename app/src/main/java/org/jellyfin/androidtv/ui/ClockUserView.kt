package org.jellyfin.androidtv.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.databinding.ClockUserBugBinding
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.ClockBehavior
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.getActivity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockUserView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
	defStyleRes: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr, defStyleRes), KoinComponent {
	private val binding: ClockUserBugBinding = ClockUserBugBinding.inflate(LayoutInflater.from(context), this, true)
	private val userPreferences by inject<UserPreferences>()
	private val userRepository by inject<UserRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val imageHelper by inject<ImageHelper>()
	private val mediaManager by inject<org.jellyfin.androidtv.ui.playback.MediaManager>()
	private val sessionRepository by inject<org.jellyfin.androidtv.auth.repository.SessionRepository>()

	var isVideoPlayer = false
		set(value) {
			field = value
			updateClockVisibility()
		}

	val homeButton get() = binding.home

	init {
		updateClockVisibility()

		val currentUser = userRepository.currentUser.value

		binding.clockUserImage.load(
			url = currentUser?.let(imageHelper::getPrimaryImageUrl),
			placeholder = ContextCompat.getDrawable(context, R.drawable.ic_user)
		)

		binding.clockUserImage.isVisible = currentUser != null
		// JellyChan: placeholder silhouette follows the button text color (dark on the light focus circle)
		if (currentUser?.primaryImageTag == null) {
			binding.clockUserImage.imageTintList = ContextCompat.getColorStateList(context, R.drawable.button_icon_tint)
		}

		binding.home.setOnClickListener {
			navigationRepository.reset(Destinations.home, clearHistory = true)
		}

		// JellyChan: the user avatar works like the one on Home (switch user)
		binding.clockUserImage.setOnClickListener {
			if (isVideoPlayer) return@setOnClickListener
			val activity = context.getActivity() ?: return@setOnClickListener
			mediaManager.clearAudioQueue()
			sessionRepository.destroyCurrentSession()
			activity.startActivity(org.jellyfin.androidtv.ui.navigation.ActivityDestinations.startup(activity))
			activity.finishAfterTransition()
		}
	}

	private fun updateClockVisibility() {
		val showClock = userPreferences[UserPreferences.clockBehavior]

		binding.clock.isVisible = when (showClock) {
			ClockBehavior.ALWAYS -> true
			ClockBehavior.NEVER -> false
			ClockBehavior.IN_VIDEO -> isVideoPlayer
			ClockBehavior.IN_MENUS -> !isVideoPlayer
		}

		binding.home.isVisible = !isVideoPlayer
		binding.clockUserImage.isFocusable = !isVideoPlayer
		// JellyChan: no avatar in the player, only the clock
		binding.clockUserImage.isVisible = !isVideoPlayer && userRepository.currentUser.value != null
	}
}
