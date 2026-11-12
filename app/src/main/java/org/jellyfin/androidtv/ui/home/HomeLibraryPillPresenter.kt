package org.jellyfin.androidtv.ui.home

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.util.getActivity

/**
 * JellyChan: My media on the home page as a row of pill buttons, like the Details button of the carousel, so the
 * page has room for larger Next up and Continue watching cards.
 */
class HomeLibraryPillPresenter : Presenter() {
	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.jc_home_library_pill, parent, false)
		view.setOnLongClickListener {
			view.context.getActivity()?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU)) ?: false
		}
		return ViewHolder(view)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		val view = viewHolder.view as TextView
		view.text = (item as? BaseRowItem)?.getCardName(view.context).orEmpty()
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) {
		(viewHolder.view as TextView).text = null
	}
}
