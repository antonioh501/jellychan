package org.jellyfin.androidtv.ui.search

import android.content.Context
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * JellyChan: the search results are Compose rows now; this keeps the launch and backdrop behavior of the former
 * leanback rows.
 */
class SearchFragmentDelegate(
	private val context: Context,
	private val backgroundService: BackgroundService,
	private val itemLauncher: ItemLauncher,
) {
	fun launch(item: BaseItemDto, row: Collection<BaseItemDto>) {
		// The launcher behaves differently for search rows (a song plays alone, photos open the photo player)
		val adapter = ItemRowAdapter(
			context,
			row.toList(),
			CardPresenter(),
			MutableObjectAdapter<Row>(CustomListRowPresenter()),
			QueryType.Search,
		)
		itemLauncher.launch(BaseItemDtoBaseRowItem(item), adapter, context)
	}

	fun onFocused(item: BaseItemDto?) {
		if (item != null) backgroundService.setBackground(item)
		else backgroundService.clearBackgrounds()
	}
}
