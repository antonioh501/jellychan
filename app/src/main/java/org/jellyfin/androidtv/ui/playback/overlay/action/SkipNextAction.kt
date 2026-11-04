package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import androidx.core.content.ContextCompat
import org.jellyfin.androidtv.R
import androidx.leanback.widget.PlaybackControlsRow
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

class SkipNextAction(context: Context) : PlaybackControlsRow.SkipNextAction(context),
	AndroidAction {
	init {
		// JellyChan: rounded web-style icon
		icon = ContextCompat.getDrawable(context, R.drawable.jc_ic_osd_next)
	}

	override fun onActionClicked(videoPlayerAdapter: VideoPlayerAdapter) =
		videoPlayerAdapter.next()
}
