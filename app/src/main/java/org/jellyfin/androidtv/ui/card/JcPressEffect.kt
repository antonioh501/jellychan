package org.jellyfin.androidtv.ui.card

import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * JellyChan: "press" feedback for focusable cards. The target shrinks slightly while OK is held and returns
 * to its normal size on release, even when the key is released before the shrink has finished.
 *
 * The target must be a child of the focusable card: the card itself is scaled by the leanback focus zoom,
 * so animating a child keeps the two effects independent.
 */
class JcPressEffect(private val target: View) {
	private var pressed = false
	private var shrinking = false

	fun setPressed(value: Boolean) {
		if (pressed == value) return
		pressed = value

		if (value) shrink()
		else if (!shrinking) release()
	}

	/** Restore the target immediately, for views that are detached or recycled while pressed. */
	fun reset() {
		target.animate().cancel()
		pressed = false
		shrinking = false
		target.scaleX = 1f
		target.scaleY = 1f
	}

	private fun shrink() {
		shrinking = true
		target.animate()
			.scaleX(PRESSED_SCALE)
			.scaleY(PRESSED_SCALE)
			.setDuration(SHRINK_DURATION_MS)
			.setInterpolator(interpolator)
			.withEndAction {
				shrinking = false
				if (!pressed) release()
			}
	}

	private fun release() {
		target.animate()
			.scaleX(1f)
			.scaleY(1f)
			.setDuration(RELEASE_DURATION_MS)
			.setInterpolator(interpolator)
	}

	private companion object {
		const val PRESSED_SCALE = 0.95f
		const val SHRINK_DURATION_MS = 80L
		const val RELEASE_DURATION_MS = 120L

		val interpolator = DecelerateInterpolator()
	}
}
