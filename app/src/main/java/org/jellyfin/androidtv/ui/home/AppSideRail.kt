package org.jellyfin.androidtv.ui.home

import kotlin.math.roundToInt
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter
import org.jellyfin.androidtv.ui.presentation.CARD_FOCUS_INSET
import org.jellyfin.androidtv.R
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import org.jellyfin.androidtv.ui.browsing.BrowseFolderFragment
import org.jellyfin.androidtv.ui.browsing.BrowseGridFragment
import org.jellyfin.androidtv.ui.browsing.EnhancedBrowseFragment
import org.jellyfin.androidtv.ui.itemdetail.FullDetailsFragment
import org.jellyfin.androidtv.ui.livetv.LiveTvGuideFragment
import org.jellyfin.androidtv.ui.playback.AudioNowPlayingFragment
import org.jellyfin.androidtv.ui.itemdetail.ItemListFragment
import org.jellyfin.androidtv.ui.itemdetail.MusicFavoritesListFragment
import org.jellyfin.androidtv.ui.search.SearchFragment
import org.jellyfin.androidtv.ui.seerr.SeerrDetailFragment
import org.jellyfin.androidtv.ui.seerr.SeerrDiscoverFragment
import org.jellyfin.androidtv.ui.seerr.SeerrRequestsFragment

/** JellyChan: start of the screens shown next to the app rail (just past its icons). */
val AppRailContentInset = 64.dp

/** JellyChan: start of the content of the screens next to the app rail, inside them: the same line as the home. */
val AppContentStart = HomeContentStart - AppRailContentInset

/** JellyChan: leanback rows of a screen next to the app rail start on the content line (cards and titles). */
fun CustomListRowPresenter.alignToAppRail(context: Context) {
	val density = context.resources.displayMetrics.density
	val browsePadding = context.resources.getDimension(R.dimen.lb_browse_padding_start)
	startPadding = ((AppContentStart - CARD_FOCUS_INSET).value * density - browsePadding).roundToInt()
	headerInset = (CARD_FOCUS_INSET.value * density).roundToInt()
}

/** JellyChan: the screens next to the app rail fade out on their left up to here (the start of their content). */
val AppRailFadeEnd = 96.dp

/** JellyChan: screens that fade out on the left: all but the library grid, which never scrolls sideways. */
fun Fragment.appRailFade(): Boolean = appRailSection() != null && this !is BrowseGridFragment

/** JellyChan: band at the top of the Compose screens, where the top bar was, with the app clock on its right. */
val AppScreenTopInset = 48.dp

/** JellyChan: the app clock, top right of the screen. */
val AppClockTop = 22.dp
val AppClockEnd = 48.dp

/** JellyChan: screens with the app clock: the ones with the app rail, the home and a few full screen menus. */
fun Fragment.showsAppClock(): Boolean = appRailSection() != null || this is HomeFragment ||
	this is LiveTvGuideFragment || this is AudioNowPlayingFragment

/**
 * JellyChan: section highlighted in the app rail for a screen, or null when the screen has no rail (the home has its
 * own, players and full screen pages have none).
 */
fun Fragment.appRailSection(): RailSection? = when (this) {
	is SearchFragment -> RailSection.Search
	is SeerrDiscoverFragment, is SeerrDetailFragment, is SeerrRequestsFragment -> RailSection.Discover
	is BrowseGridFragment, is EnhancedBrowseFragment, is BrowseFolderFragment,
	is FullDetailsFragment, is ItemListFragment, is MusicFavoritesListFragment -> RailSection.None
	else -> null
}
