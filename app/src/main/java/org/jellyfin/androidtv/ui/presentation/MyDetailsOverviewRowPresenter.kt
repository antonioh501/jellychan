package org.jellyfin.androidtv.ui.presentation

import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.ui.DetailRowView
import org.jellyfin.androidtv.ui.browsing.composable.inforow.BaseItemInfoRowView
import org.jellyfin.androidtv.ui.browsing.composable.inforow.JcDetailsInfoRowView
import org.jellyfin.androidtv.ui.itemdetail.MyDetailsOverviewRow
import org.jellyfin.androidtv.ui.itemdetail.usesJcDetailsLayout
import org.jellyfin.androidtv.util.InfoLayoutHelper
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.sdk.model.api.BaseItemKind

class MyDetailsOverviewRowPresenter(
	private val markdownRenderer: MarkdownRenderer,
) : RowPresenter() {
	class ViewHolder(
		private val detailRowView: DetailRowView,
		private val markdownRenderer: MarkdownRenderer,
	) : RowPresenter.ViewHolder(detailRowView) {
		private val binding get() = detailRowView.binding

		fun setItem(row: MyDetailsOverviewRow) {
			// JellyChan: dedicated layout for people (cast & crew)
			val isPerson = row.item.type == BaseItemKind.PERSON
			binding.fdHeaderRoot.isVisible = !isPerson
			binding.jcPersonRoot.isVisible = isPerson
			if (isPerson) {
				setPerson(row)
				return
			}

			setTitle(row.item.name)

			val jcLayout = usesJcDetailsLayout(row.item)
			val originalTitle = row.item.originalTitle
				?.takeIf { jcLayout && it.isNotBlank() && !it.trim().equals(row.item.name?.trim(), ignoreCase = true) }
			binding.jcOriginalTitle.text = originalTitle
			binding.jcOriginalTitle.isVisible = originalTitle != null

			if (jcLayout) {
				setDetailsInfoRow(row)
			} else {
				detailsInfoRow?.isVisible = false
				InfoLayoutHelper.addInfoRow(view.context, row.item, row.item.mediaSources?.getOrNull(row.selectedMediaSourceIndex), binding.fdMainInfoRow, false)
				binding.fdMainInfoRow.children.filterIsInstance<BaseItemInfoRowView>().forEach {
					it.isVisible = true
					it.includeSeasonEpisode = row.item.type != BaseItemKind.EPISODE
				}
			}
			binding.fdGenreRow.text = row.item.genres?.joinToString(" / ")

			binding.infoTitle1.text = row.infoItem1?.label
			binding.infoValue1.text = row.infoItem1?.value

			binding.infoTitle2.text = row.infoItem2?.label
			binding.infoValue2.text = row.infoItem2?.value

			binding.infoTitle3.text = row.infoItem3?.label
			binding.infoValue3.text = row.infoItem3?.value

			// JellyChan spotlight header: centered clear logo, title only as fallback.
			// Episodes: thumbnail on top, smaller series logo, then "S1:E1 — Title"
			val isEpisode = row.item.type == BaseItemKind.EPISODE
			val density = view.resources.displayMetrics.density
			val thumb = row.episodeThumbUrl
			binding.fdEpisodeThumb.isVisible = isEpisode && thumb != null
			if (isEpisode && thumb != null) {
				binding.fdEpisodeThumb.clipToOutline = true
				binding.fdEpisodeThumb.load(thumb, null, null, 1.0, 0)
			}
			binding.mainImage.maxHeight = ((if (isEpisode) 48 else 120) * density).toInt()
			binding.mainImage.maxWidth = ((if (isEpisode) 220 else 420) * density).toInt()

			// JellyChan: episodes use a more compact stack so the thumbnail sits lower on screen
			fun View.setBottomMargin(dp: Int) {
				(layoutParams as? ViewGroup.MarginLayoutParams)?.let {
					it.bottomMargin = (dp * density).toInt()
					layoutParams = it
				}
			}
			binding.fdEpisodeThumb.setBottomMargin(if (isEpisode) 8 else 16)
			// JellyChan: smaller episode thumbnail and a lower stack, keeping some air under the toolbar
			binding.fdEpisodeThumb.layoutParams = binding.fdEpisodeThumb.layoutParams.apply {
				width = ((if (isEpisode) 256 else 320) * density).toInt()
				height = ((if (isEpisode) 144 else 180) * density).toInt()
			}
			binding.fdHeaderRoot.setPadding(
				binding.fdHeaderRoot.paddingLeft,
				binding.fdHeaderRoot.paddingTop,
				binding.fdHeaderRoot.paddingRight,
				((if (isEpisode) 0 else 8) * density).toInt(),
			)
			binding.mainImage.setBottomMargin(if (isEpisode) 4 else 14)
			binding.fdGenreRow.isVisible = !isEpisode && row.item.type != BaseItemKind.PERSON
			binding.fdButtonRow.setBottomMargin(if (isEpisode) 8 else 14)
			binding.fdMainInfoRow.setBottomMargin(if (isEpisode) 16 else 4)
			binding.fdSummaryText.textSize = if (isEpisode) 14f else 15f

			if (row.hasLogo) {
				binding.mainImage.isVisible = true
				binding.mainImage.load(row.imageDrawable, null, null, 1.0, 0)
				binding.fdTitle.isVisible = isEpisode
				binding.fdTitle.textSize = if (isEpisode) 18f else 22f
			} else {
				binding.mainImage.isVisible = false
				binding.fdTitle.isVisible = true
				binding.fdTitle.textSize = if (isEpisode) 22f else 44f
			}
			if (isEpisode) {
				val season = row.item.parentIndexNumber
				val episode = row.item.indexNumber
				val prefix = if (season != null && episode != null) "S$season:E$episode — " else ""
				binding.fdTitle.text = prefix + row.item.name.orEmpty()
				binding.fdSummaryText.maxLines = 2
			}

			extraInfo = if (jcLayout) emptyList() else listOfNotNull(
				row.infoItem2?.value?.takeIf { it.isNotBlank() },
				row.infoItem3?.let { item -> item.value?.takeIf { it.isNotBlank() }?.let { "${item.label} $it" } },
			)
			endsLabel = row.infoItem3?.label
			updateExtraInfo()

			setSummary(row.summary)

			if (row.item.type == BaseItemKind.PERSON) {
				binding.fdSummaryText.maxLines = 9
				binding.fdGenreRow.isVisible = false
			}

			val isEpisodeRow = row.item.type == BaseItemKind.EPISODE
			binding.fdButtonRow.removeAllViews()
			for (button in row.actions) {
				val parent = button.parent
				if (parent is ViewGroup) parent.removeView(button)

				if (jcLayout) {
					// Icon-only secondary actions, highlighted only when focused
					button.binding.imageButton.setBackgroundResource(org.jellyfin.androidtv.R.drawable.jc_icon_button_back)
					button.binding.imageButton.setPadding((10 * density).toInt())
					button.binding.imageButton.layoutParams = (button.binding.imageButton.layoutParams as ViewGroup.MarginLayoutParams).apply {
						width = (52 * density).toInt()
						height = (44 * density).toInt()
						topMargin = (2 * density).toInt()
					}
					button.binding.label.layoutParams = button.binding.label.layoutParams.apply {
						width = (68 * density).toInt()
					}
					button.binding.label.setPadding(0, 0, 0, 0)
				} else {
					// JellyChan: slightly smaller action buttons on episodes
					button.binding.imageButton.layoutParams = button.binding.imageButton.layoutParams.apply {
						width = ((if (isEpisodeRow) 60 else 70) * view.resources.displayMetrics.density).toInt()
						height = ((if (isEpisodeRow) 34 else 40) * view.resources.displayMetrics.density).toInt()
					}
				}
				binding.fdButtonRow.addView(button)
			}
		}

		private val detailsInfoRow: JcDetailsInfoRowView?
			get() = binding.fdMainInfoRow.children.filterIsInstance<JcDetailsInfoRowView>().firstOrNull()

		private fun setDetailsInfoRow(row: MyDetailsOverviewRow) {
			binding.fdMainInfoRow.children.filterIsInstance<BaseItemInfoRowView>().forEach { it.isVisible = false }

			val infoRow = detailsInfoRow ?: JcDetailsInfoRowView(view.context).also { binding.fdMainInfoRow.addView(it) }
			infoRow.isVisible = true
			infoRow.item = row.item
			infoRow.runtime = row.infoItem2?.value
			infoRow.endTime = row.infoItem3?.value
		}

		private fun setPerson(row: MyDetailsOverviewRow) {
			val item = row.item
			val context = view.context
			// The photo is drawn fixed by the fragment (jcPersonFixedPhoto); keep its space here
			binding.jcPersonPhoto.visibility = View.INVISIBLE
			binding.jcPersonName.text = item.name
			binding.jcPersonOverview.text = item.overview?.let { markdownRenderer.toMarkdownSpanned(it) }
			binding.jcPersonOverview.isVisible = !item.overview.isNullOrBlank()

			val formatter = java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy")
			val born = item.premiereDate
			val died = item.endDate
			binding.jcPersonBorn.isVisible = born != null
			if (born != null) {
				val end = died ?: java.time.LocalDateTime.now()
				val age = java.time.temporal.ChronoUnit.YEARS.between(born, end).toInt()
				binding.jcPersonBorn.text = if (died != null)
					context.getString(org.jellyfin.androidtv.R.string.jc_person_born_died, formatter.format(born), formatter.format(died), age)
				else
					context.getString(org.jellyfin.androidtv.R.string.jc_person_born, formatter.format(born), age)
			}
			val place = item.productionLocations?.firstOrNull()
			binding.jcPersonBirthplace.isVisible = place != null
			if (place != null) binding.jcPersonBirthplace.text = context.getString(org.jellyfin.androidtv.R.string.jc_person_birthplace, place)

			binding.jcPersonButtons.removeAllViews()
			binding.fdButtonRow.removeAllViews()
			for (button in row.actions) {
				val parent = button.parent
				if (parent is ViewGroup) parent.removeView(button)
				// Icon-only buttons, highlighted only when focused (like the web)
				button.binding.imageButton.setBackgroundResource(org.jellyfin.androidtv.R.drawable.jc_icon_button_back)
				button.binding.imageButton.layoutParams = button.binding.imageButton.layoutParams.apply {
					width = (48 * view.resources.displayMetrics.density).toInt()
					height = (36 * view.resources.displayMetrics.density).toInt()
				}
				binding.jcPersonButtons.addView(button)
			}
		}

		fun setTitle(title: String?) {
			binding.fdTitle.text = title
		}

		fun setSummary(summary: String?) {
			binding.fdSummaryText.text = summary?.let { markdownRenderer.toMarkdownSpanned(it) }
		}

		fun setInfoValue3(text: String?) {
			binding.infoValue3.text = text
			detailsInfoRow?.endTime = text
			if (extraInfo.size == 2 && text != null) {
				extraInfo = listOf(extraInfo[0], "$endsLabel $text")
				updateExtraInfo()
			}
		}

		private var extraInfo: List<String> = emptyList()
		private var endsLabel: String? = null

		// JellyChan: runtime and "ends at" shown inline after the metadata badges
		private fun updateExtraInfo() {
			val row = binding.fdMainInfoRow
			var extra = row.findViewWithTag<android.widget.TextView>("jc_extra")
			if (extra == null) {
				extra = android.widget.TextView(row.context).apply {
					tag = "jc_extra"
					setTextColor(android.graphics.Color.parseColor("#D1D5DB"))
					textSize = 16f
					typeface = androidx.core.content.res.ResourcesCompat.getFont(row.context, org.jellyfin.androidtv.R.font.inter_medium)
					gravity = android.view.Gravity.CENTER_VERTICAL
					setPadding((12 * resources.displayMetrics.density).toInt(), 0, 0, 0)
				}
				row.addView(extra, android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
			}
			extra.text = extraInfo.joinToString("   ")
			extra.isVisible = extraInfo.isNotEmpty()
		}
	}

	var viewHolder: ViewHolder? = null
		private set

	init {
		syncActivatePolicy = SYNC_ACTIVATED_CUSTOM
	}

	override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
		val view = DetailRowView(parent.context)
		viewHolder = ViewHolder(view, markdownRenderer)
		return viewHolder!!
	}

	override fun onBindRowViewHolder(viewHolder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(viewHolder, item)
		if (item !is MyDetailsOverviewRow) return
		if (viewHolder !is ViewHolder) return

		viewHolder.setItem(item)
	}

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit
}
