package org.jellyfin.androidtv.ui.presentation

import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import timber.log.Timber

class PositionableListRowPresenter : CustomListRowPresenter {
	private var viewHolder: ViewHolder? = null

	constructor() : super()
	constructor(padding: Int?) : super(padding)

	init {
		shadowEnabled = false
	}

	override fun isUsingDefaultShadow() = false

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit

	override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(holder, item)
		if (holder !is ViewHolder) return

		viewHolder = holder
		keepPadding(holder)
	}

	/**
	 * JellyChan: leanback gives the selected row a little more top padding, so the rows below move by a few pixels
	 * every time the selection changes. When set, every row keeps the padding it has when not selected.
	 */
	var keepRowHeight = false

	override fun onRowViewSelected(holder: RowPresenter.ViewHolder, selected: Boolean) {
		super.onRowViewSelected(holder, selected)
		keepPadding(holder)
	}

	override fun onRowViewExpanded(holder: RowPresenter.ViewHolder, expanded: Boolean) {
		super.onRowViewExpanded(holder, expanded)
		keepPadding(holder)
	}

	private fun keepPadding(holder: RowPresenter.ViewHolder) {
		if (!keepRowHeight || holder !is ViewHolder) return
		val grid = holder.gridView ?: return
		if (!holder.isSelected) {
			grid.setTag(R.id.jc_row_padding_top, grid.paddingTop)
		} else {
			val top = grid.getTag(R.id.jc_row_padding_top) as? Int ?: return
			if (grid.paddingTop != top) grid.setPadding(grid.paddingLeft, top, grid.paddingRight, grid.paddingBottom)
		}
	}

	var position: Int
		get() = viewHolder?.gridView?.selectedPosition ?: -1
		set(value) {
			Timber.d("Setting position to $value")
			viewHolder?.gridView?.selectedPosition = value
		}
}
