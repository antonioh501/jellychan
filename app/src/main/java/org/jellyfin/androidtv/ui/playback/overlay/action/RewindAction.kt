package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import androidx.core.content.ContextCompat
import org.jellyfin.androidtv.R
import androidx.leanback.widget.PlaybackControlsRow
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

class RewindAction(context: Context) : PlaybackControlsRow.RewindAction(context), AndroidAction {
	init {
		// JellyChan: rounded web-style icon
		val d = ContextCompat.getDrawable(context, R.drawable.jc_ic_osd_rewind)
		setDrawables(Array(actionCount) { d })
		icon = d
	}

	override fun onActionClicked(videoPlayerAdapter: VideoPlayerAdapter) =
		videoPlayerAdapter.rewind()
}
