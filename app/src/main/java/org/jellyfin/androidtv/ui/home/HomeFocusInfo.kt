package org.jellyfin.androidtv.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.browsing.composable.inforow.JcDetailsInfoRow
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject

/** JellyChan: what the top of the home shows while a row has the focus. */
sealed interface HomeFocus {
	data class Item(val item: BaseItemDto) : HomeFocus
	data class Genre(val name: String, val sample: BaseItemDto?) : HomeFocus
}

private val TextShadow = Shadow(color = Color(0xCC000000), blurRadius = 8f)

private const val TICKS_PER_MINUTE = 600_000_000L

/** "1h 50m" or "51m". */
internal fun formatMinutes(ticks: Long?): String? {
	val minutes = ticks?.let { it / TICKS_PER_MINUTE }?.takeIf { it > 0 } ?: return null
	return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/**
 * JellyChan: info about the focused card (or genre) at the top left of the home, over its backdrop. It sits right
 * above the selected row.
 */
@Composable
fun HomeFocusInfo(
	focus: HomeFocus?,
	modifier: Modifier = Modifier,
) {
	Box(
		contentAlignment = Alignment.BottomStart,
		modifier = modifier
			.fillMaxWidth()
			.height(HomeFocusLine - 50.dp)
			.padding(start = HomeContentStart),
	) {
		AnimatedContent(
			targetState = focus,
			transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
			contentAlignment = Alignment.BottomStart,
			label = "HomeFocusInfo",
		) { target ->
			when (target) {
				is HomeFocus.Item -> ItemInfo(target.item)
				is HomeFocus.Genre -> Text(
					text = target.name,
					style = JellyfinTheme.typography.default.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, shadow = TextShadow),
					modifier = Modifier
						.widthIn(max = HomeInfoWidth)
						.padding(bottom = 40.dp),
				)

				null -> Box(Modifier)
			}
		}
	}
}

@Composable
private fun ItemInfo(item: BaseItemDto) {
	val imageHelper = koinInject<ImageHelper>()
	val logoUrl = remember(item.id) { imageHelper.getLogoImageUrl(item, 800) }
	val logoPainter = rememberAsyncImagePainter(logoUrl)
	val logoState by logoPainter.state.collectAsState()
	val isEpisode = item.type == BaseItemKind.EPISODE
	val title = if (isEpisode) item.seriesName ?: item.name else item.name

	Column(
		verticalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier
			.width(HomeInfoWidth)
			.padding(bottom = 4.dp),
	) {
		if (logoUrl != null && logoState !is AsyncImagePainter.State.Error) {
			Image(
				painter = logoPainter,
				contentDescription = title,
				contentScale = ContentScale.Fit,
				alignment = Alignment.CenterStart,
				modifier = Modifier
					.heightIn(max = 96.dp)
					.widthIn(max = HomeInfoWidth)
					.padding(bottom = 4.dp),
			)
		} else {
			Text(
				text = title.orEmpty(),
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				style = JellyfinTheme.typography.default.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, shadow = TextShadow),
			)
		}

		val position = item.userData?.playbackPositionTicks?.takeIf { it > 0 }
		val left = position?.let { formatMinutes((item.runTimeTicks ?: 0) - it) }
		val episode = if (isEpisode) {
			listOfNotNull(
				item.parentIndexNumber?.let { "S$it" },
				item.indexNumber?.let { "E$it" },
			).joinToString(" ").let { number -> listOf(number, item.name.orEmpty()).filter { it.isNotBlank() }.joinToString(" ") }
		} else null
		val line = listOfNotNull(left?.let { stringResource(R.string.jc_home_time_left, it) }, episode).joinToString(" · ")

		if (line.isNotBlank()) {
			Text(
				text = line,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				style = JellyfinTheme.typography.default.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, shadow = TextShadow),
			)
		} else {
			Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) {
				JcDetailsInfoRow(item = item, runtime = formatMinutes(item.runTimeTicks), endTime = null)
			}
		}

		item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
			Text(
				text = overview,
				maxLines = 4,
				overflow = TextOverflow.Ellipsis,
				style = JellyfinTheme.typography.default.copy(fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFFD1D5DB), shadow = TextShadow),
			)
		}
	}
}
