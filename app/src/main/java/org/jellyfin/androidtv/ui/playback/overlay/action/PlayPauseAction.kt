package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import androidx.core.content.ContextCompat
import org.jellyfin.androidtv.R
import androidx.leanback.widget.PlaybackControlsRow
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

class PlayPauseAction(context: Context) : PlaybackControlsRow.PlayPauseAction(context),
	AndroidAction {
	init {
		// JellyChan: rounded web-style icons
		val icons = arrayOfNulls<android.graphics.drawable.Drawable>(actionCount)
		icons[INDEX_PLAY] = ContextCompat.getDrawable(context, R.drawable.jc_ic_osd_play)
		icons[INDEX_PAUSE] = ContextCompat.getDrawable(context, R.drawable.jc_ic_osd_pause)
		setDrawables(icons)
		icon = icons[index]
	}

	override fun onActionClicked(videoPlayerAdapter: VideoPlayerAdapter) =
		when (this.index) {
			INDEX_PLAY -> {
				videoPlayerAdapter.play()
				this.index = INDEX_PAUSE
			}

			INDEX_PAUSE -> {
				videoPlayerAdapter.pause()
				this.index = INDEX_PLAY
			}

			else -> {}
		}

}
