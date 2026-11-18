package org.jellyfin.androidtv.ui.presentation

import android.view.View
import androidx.core.view.isVisible
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

open class CustomListRowPresenter @JvmOverloads constructor(
	private val topPadding: Int? = null
) : ListRowPresenter() {
	init {
		headerPresenter = CustomRowHeaderPresenter()
	}

	// JellyChan: optional extra start padding (person page rows aligned with the text column)
	var startPadding: Int? = null

	// JellyChan: moves the row title right by this many pixels, to line it up with the card thumbnails (the cards keep
	// some space around the thumbnail for their selection border)
	var headerInset: Int = 0

	override fun isUsingDefaultShadow() = false

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit

	override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(holder, item)

		val view = holder.view?.parent as? View ?: return
		if (topPadding != null) view.setPadding(view.paddingLeft, topPadding, view.paddingRight, view.paddingBottom)
		startPadding?.let { view.setPaddingRelative(it, view.paddingTop, view.paddingEnd, view.paddingBottom) }

		holder.headerViewHolder.view.translationX = headerInset.toFloat()

		// Hide header view when the item doesn't have one
		holder.headerViewHolder.view.isVisible = !(item is ListRow && item.headerItem == null)
	}
}
