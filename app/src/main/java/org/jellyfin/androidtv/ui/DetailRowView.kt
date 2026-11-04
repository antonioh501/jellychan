package org.jellyfin.androidtv.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnFocusChangeListener
import android.widget.FrameLayout
import android.view.ViewTreeObserver
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.databinding.ViewRowDetailsBinding
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DetailRowView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
	defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes), KoinComponent {
	val binding = ViewRowDetailsBinding.inflate(LayoutInflater.from(context), this, true)

	/**
	 * Keeps track of the last selected button and reselect it when navigating back to the buttons row.
	 */
	private val buttonsHierarchyChangeListener = object : OnHierarchyChangeListener {

		private var lastFocusedButton: View? = null

		private val focusChangeListener = OnFocusChangeListener { view, hasFocus ->
			// Restore last focused button when navigating back to button row
			if (hasFocus && lastFocusedButton != null) {
				lastFocusedButton?.requestFocus()
				lastFocusedButton = null
			}
			view.post {
				// Store last focused button when navigating away from button row
				if (binding.fdButtonRow.focusedChild == null) {
					lastFocusedButton = view
				}
			}
		}

		override fun onChildViewAdded(parent: View?, child: View?) {
			child?.onFocusChangeListener = focusChangeListener
		}

		override fun onChildViewRemoved(parent: View?, child: View?) {
			child?.onFocusChangeListener = null
		}
	}

	init {
		binding.fdButtonRow.setOnHierarchyChangeListener(buttonsHierarchyChangeListener)
	}

	// JellyChan: clear "spotlight" backdrop while the focus is inside this header
	private val backgroundService by inject<BackgroundService>()
	private val globalFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
		if (newFocus == null) return@OnGlobalFocusChangeListener
		// Inside the header: clear backdrop. Rows below: dim it. Anything else (top icons): keep as is
		val grid = findParentGrid()
		when {
			newFocus.isDescendantOf(this) -> backgroundService.setSpotlight(true)
			grid != null && newFocus.isDescendantOf(grid) -> backgroundService.setSpotlight(false)
		}
	}

	private fun findParentGrid(): androidx.leanback.widget.VerticalGridView? {
		var v = parent
		while (v != null) {
			if (v is androidx.leanback.widget.VerticalGridView) return v
			v = v.parent
		}
		return null
	}

	private fun View.isDescendantOf(ancestor: View): Boolean {
		var v: View? = this
		while (v != null) {
			if (v === ancestor) return true
			v = v.parent as? View
		}
		return false
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusListener)
		if (hasFocus()) backgroundService.setSpotlight(true)
	}

	override fun onDetachedFromWindow() {
		viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusListener)
		backgroundService.setSpotlight(false)
		super.onDetachedFromWindow()
	}
}
