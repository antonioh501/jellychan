package org.jellyfin.androidtv.ui.browsing.composable.inforow

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.SeriesStatus
import org.koin.compose.koinInject

@Composable
private fun InfoRowText(text: String?) {
	if (text.isNullOrBlank()) return

	InfoRowItem(contentDescription = null) {
		Text(text)
	}
}

@Composable
private fun seriesYears(item: BaseItemDto): String? {
	val present = stringResource(R.string.jc_present)
	val start = item.productionYear ?: item.premiereDate?.year
	val end = item.endDate?.year
	val status = item.status?.let(SeriesStatus::fromNameOrNull)

	return when {
		start == null -> null
		status == SeriesStatus.CONTINUING -> "$start - $present"
		end != null && end != start -> "$start - $end"
		else -> start.toString()
	}
}

/**
 * JellyChan: metadata line of the episode, movie and series pages. Empty fields are skipped and the items are
 * only separated by spacing, so a missing value never leaves a separator behind.
 *
 * @param ratingsOnly Show only the parental, community and critic ratings (home carousel).
 */
@Composable
fun JcDetailsInfoRow(
	item: BaseItemDto,
	runtime: String?,
	endTime: String?,
	ratingsOnly: Boolean = false,
) {
	val userPreferences = koinInject<UserPreferences>()
	val showRatings = userPreferences[UserPreferences.defaultRatingType] != RatingType.RATING_HIDDEN
	val communityRating = item.communityRating?.takeIf { showRatings && it > 0f }
	val criticRating = item.criticRating?.takeIf { showRatings }
	val officialRating = item.officialRating?.takeUnless { it.isBlank() }
	val endsLabel = stringResource(R.string.lbl_ends)
	val endsAt = endTime?.takeUnless { it.isBlank() }?.let { "$endsLabel $it" }

	Row(
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		when {
			ratingsOnly -> {
				officialRating?.let { InfoRowParentalRating(it) }
				communityRating?.let { InfoRowCommunityRating(it / 10f) }
				criticRating?.let { InfoRowCriticRating(it / 100f) }
			}

			item.type == BaseItemKind.EPISODE -> {
				InfoRowDate(item)
				InfoRowText(runtime)
				communityRating?.let { InfoRowCommunityRating(it / 10f) }
				InfoRowText(endsAt)
			}

			item.type == BaseItemKind.SERIES -> {
				InfoRowText(seriesYears(item))
				officialRating?.let { InfoRowParentalRating(it) }
				communityRating?.let { InfoRowCommunityRating(it / 10f) }
			}

			else -> {
				InfoRowText((item.productionYear ?: item.premiereDate?.year)?.toString())
				InfoRowText(runtime)
				officialRating?.let { InfoRowParentalRating(it) }
				communityRating?.let { InfoRowCommunityRating(it / 10f) }
				criticRating?.let { InfoRowCriticRating(it / 100f) }
				InfoRowText(endsAt)
			}
		}
	}
}

/**
 * Exposes the [JcDetailsInfoRow] composable as Android view.
 */
class JcDetailsInfoRowView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {
	private val _item = MutableStateFlow<BaseItemDto?>(null)
	private val _runtime = MutableStateFlow<String?>(null)
	private val _endTime = MutableStateFlow<String?>(null)
	private val _ratingsOnly = MutableStateFlow(false)

	var item: BaseItemDto?
		get() = _item.value
		set(value) {
			_item.value = value
		}

	var runtime: String?
		get() = _runtime.value
		set(value) {
			_runtime.value = value
		}

	var endTime: String?
		get() = _endTime.value
		set(value) {
			_endTime.value = value
		}

	var ratingsOnly: Boolean
		get() = _ratingsOnly.value
		set(value) {
			_ratingsOnly.value = value
		}

	init {
		isFocusable = false
		descendantFocusability = FOCUS_BLOCK_DESCENDANTS
	}

	@Composable
	override fun Content() {
		val item by _item.collectAsState()
		val runtime by _runtime.collectAsState()
		val endTime by _endTime.collectAsState()
		val ratingsOnly by _ratingsOnly.collectAsState()

		item?.let { JcDetailsInfoRow(it, runtime, endTime, ratingsOnly) }
	}
}
