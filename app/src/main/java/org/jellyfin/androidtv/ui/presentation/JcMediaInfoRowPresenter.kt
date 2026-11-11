package org.jellyfin.androidtv.ui.presentation

import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.databinding.JcViewMediaInfoCreditBinding
import org.jellyfin.androidtv.databinding.JcViewMediaInfoRowBinding
import org.jellyfin.androidtv.databinding.JcViewMediaInfoStreamBinding
import org.jellyfin.androidtv.ui.itemdetail.MyDetailsOverviewRow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind

private const val SUBTITLES_OFF = -1

/**
 * JellyChan: external ids, credits and media streams of a movie or episode. The selected audio and subtitle
 * tracks are kept on the overview row, next to the selected version.
 */
class JcMediaInfoRow(
	val overview: MyDetailsOverviewRow,
	private val lastAudioLanguage: () -> String?,
) : Row() {
	private val item get() = overview.item
	private val mediaSource get() = item.mediaSources?.getOrNull(overview.selectedMediaSourceIndex)
	private val streams get() = mediaSource?.mediaStreams ?: item.mediaStreams.orEmpty()

	val videoStream get() = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
	val audioStreams get() = streams.filter { it.type == MediaStreamType.AUDIO }
	val subtitleStreams get() = streams.filter { it.type == MediaStreamType.SUBTITLE }

	// The player keeps the language of the last played audio track, so that is what will play here
	val selectedAudioIndex
		get() = overview.selectedAudioIndex
			?: lastAudioLanguage()?.let { language -> audioStreams.firstOrNull { it.language == language }?.index }
			?: mediaSource?.defaultAudioStreamIndex
	val selectedSubtitleIndex
		get() = overview.selectedSubtitleIndex ?: mediaSource?.defaultSubtitleStreamIndex ?: SUBTITLES_OFF

	val credits: List<Pair<Int, String>>
		get() = listOf(
			R.string.jc_media_director to item.peopleNames(PersonKind.DIRECTOR),
			R.string.jc_media_writer to item.peopleNames(PersonKind.WRITER),
			R.string.jc_media_studio to item.studios.orEmpty().mapNotNull { it.name }.joinToString(", "),
			R.string.lbl_genres to item.genres.orEmpty().joinToString(", "),
		).filter { (_, value) -> value.isNotBlank() }

	val hasContent get() = credits.isNotEmpty() || streams.isNotEmpty()

	private fun BaseItemDto.peopleNames(kind: PersonKind) =
		people.orEmpty().filter { it.type == kind }.mapNotNull { it.name }.distinct().joinToString(", ")
}

class JcMediaInfoRowPresenter(
	private val canSelectTracks: Boolean,
) : RowPresenter() {
	inner class ViewHolder(
		private val binding: JcViewMediaInfoRowBinding,
	) : RowPresenter.ViewHolder(binding.root) {
		private val inflater = LayoutInflater.from(binding.root.context)

		fun bind(row: JcMediaInfoRow) {
			bindCredits(row)
			val hasSelector = bindStreams(row)

			// Without selectors the row still needs a focus stop to scroll into view
			binding.root.isFocusable = !hasSelector
		}

		private fun bindCredits(row: JcMediaInfoRow) {
			binding.jcMediaCredits.removeAllViews()
			row.credits.forEach { (label, value) ->
				val credit = JcViewMediaInfoCreditBinding.inflate(inflater, binding.jcMediaCredits, true)
				credit.jcCreditLabel.setText(label)
				credit.jcCreditValue.text = value
			}
			binding.jcMediaCredits.isVisible = row.credits.isNotEmpty()
		}

		private fun bindStreams(row: JcMediaInfoRow): Boolean {
			val panel = binding.jcMediaStreams
			panel.removeAllViews()

			row.videoStream?.let { addStream(panel, R.drawable.jc_ic_media_video, it.label) }

			val audio = row.audioStreams
			val audioSelected = audio.firstOrNull { it.index == row.selectedAudioIndex } ?: audio.firstOrNull()
			val audioSelector = canSelectTracks && audio.size > 1
			if (audioSelected != null) {
				val options = audio.map { it.index to it.label }
				addStream(panel, R.drawable.jc_ic_media_audio, audioSelected.label, options.takeIf { audioSelector }, audioSelected.index) {
					row.overview.selectedAudioIndex = it
					bind(row)
				}
			}

			val subtitles = row.subtitleStreams
			val subtitleSelector = canSelectTracks && subtitles.isNotEmpty()
			if (subtitles.isNotEmpty()) {
				val off = binding.root.context.getString(R.string.lbl_none)
				val selected = subtitles.firstOrNull { it.index == row.selectedSubtitleIndex }
				val options = listOf(SUBTITLES_OFF to off) + subtitles.map { it.index to it.label }
				addStream(panel, R.drawable.jc_ic_media_subtitles, selected?.label ?: off, options.takeIf { subtitleSelector }, selected?.index ?: SUBTITLES_OFF) {
					row.overview.selectedSubtitleIndex = it
					bind(row)
				}
			}

			val hasStreams = panel.childCount > 0
			panel.isVisible = hasStreams
			binding.jcMediaPanelGap.isVisible = hasStreams && row.credits.isNotEmpty()
			return audioSelector || subtitleSelector
		}

		private fun addStream(
			panel: LinearLayout,
			@DrawableRes icon: Int,
			text: String,
			options: List<Pair<Int, String>>? = null,
			selected: Int? = null,
			onSelected: (Int) -> Unit = {},
		) {
			val stream = JcViewMediaInfoStreamBinding.inflate(inflater, panel, true)
			stream.jcStreamIcon.setImageResource(icon)
			stream.jcStreamText.text = text
			stream.jcStreamChevron.isVisible = options != null

			if (options == null) {
				stream.jcStreamBox.setPadding(0, 0, 0, 0)
				return
			}

			stream.jcStreamBox.setBackgroundResource(R.drawable.jc_media_select_back)
			stream.jcStreamBox.isFocusable = true
			stream.jcStreamBox.isClickable = true
			stream.jcStreamBox.setOnClickListener { anchor ->
				showOptions(anchor, options, selected, onSelected)
			}
		}

		private fun showOptions(
			anchor: View,
			options: List<Pair<Int, String>>,
			selected: Int?,
			onSelected: (Int) -> Unit,
		) = PopupMenu(anchor.context, anchor, Gravity.END).apply {
			options.forEachIndexed { position, (_, title) -> menu.add(Menu.NONE, position, position, title) }
			menu.setGroupCheckable(Menu.NONE, true, true)
			menu.findItem(options.indexOfFirst { (index, _) -> index == selected })?.isChecked = true
			setOnMenuItemClickListener { menuItem ->
				val streamRow = anchor.parent as View
				val streamPosition = binding.jcMediaStreams.indexOfChild(streamRow)
				onSelected(options[menuItem.itemId].first)
				// The panel was rebuilt: put the focus back on the same selector
				binding.jcMediaStreams.getChildAt(streamPosition)?.findViewById<View>(R.id.jcStreamBox)?.requestFocus()
				true
			}
			show()
		}
	}

	var viewHolder: ViewHolder? = null
		private set

	init {
		headerPresenter = null
		syncActivatePolicy = SYNC_ACTIVATED_CUSTOM
	}

	override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
		val binding = JcViewMediaInfoRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding).also { viewHolder = it }
	}

	override fun onBindRowViewHolder(viewHolder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(viewHolder, item)
		if (item is JcMediaInfoRow && viewHolder is ViewHolder) viewHolder.bind(item)
	}

	// The default effect wraps the row in a container that doesn't pass on the full width
	override fun isUsingDefaultSelectEffect() = false

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit
}

private val MediaStream.label: String
	get() = displayTitle?.takeIf { it.isNotBlank() }
		?: listOfNotNull(language, codec?.uppercase()).joinToString(" - ")
