package org.jellyfin.androidtv.ui.itemdetail

import androidx.core.view.isVisible
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.data.model.InfoItem
import org.jellyfin.androidtv.ui.TextUnderButton
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

class MyDetailsOverviewRow @JvmOverloads constructor(
	val item: BaseItemDto,
	var imageDrawable: String? = null,
	var summary: String? = null,
	var infoItem1: InfoItem? = null,
	var infoItem2: InfoItem? = null,
	var infoItem3: InfoItem? = null,
	var selectedMediaSourceIndex: Int = 0,
	var hasLogo: Boolean = false,
	var episodeThumbUrl: String? = null,
) : Row() {
	private val _actions = mutableListOf<TextUnderButton>()
	val actions get() = _actions.toList()
	val visibleActions get() = _actions.count { it.isVisible }

	fun clearActions() = _actions.clear()
	fun addAction(button: TextUnderButton) = _actions.add(button)

	fun moveActionBefore(button: TextUnderButton, anchor: TextUnderButton) {
		_actions.remove(button)
		_actions.add(_actions.indexOf(anchor), button)
	}
}

/**
 * JellyChan: episodes, movies and series share the layout with original title, single metadata line,
 * pill main action and icon-only secondary actions.
 */
fun usesJcDetailsLayout(item: BaseItemDto) = when (item.type) {
	BaseItemKind.EPISODE, BaseItemKind.MOVIE, BaseItemKind.SERIES -> true
	else -> false
}
