package org.jellyfin.androidtv.ui.background

import android.graphics.drawable.ColorDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.koin.compose.koinInject

@Composable
private fun AppThemeBackground() {
	val context = LocalContext.current
	val themeBackground = remember(context.theme) {
		val attributes = context.theme.obtainStyledAttributes(intArrayOf(R.attr.defaultBackground))
		val drawable = attributes.getDrawable(0)
		attributes.recycle()

		if (drawable is ColorDrawable) drawable.toBitmap(1, 1).asImageBitmap()
		else drawable?.toBitmap()?.asImageBitmap()
	}

	if (themeBackground != null) {
		Image(
			bitmap = themeBackground,
			contentDescription = null,
			alignment = Alignment.Center,
			contentScale = ContentScale.Crop,
			modifier = Modifier.fillMaxSize()
		)
	} else {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black)
		)
	}
}

@Composable
fun AppBackground() {
	val backgroundService = koinInject<BackgroundService>()
	val currentBackground by backgroundService.currentBackground.collectAsState()
	val blurBackground by backgroundService.blurBackground.collectAsState()
	val enabled by backgroundService.enabled.collectAsState()
	val spotlight by backgroundService.spotlight.collectAsState()
	val trailerUrl by backgroundService.trailerUrl.collectAsState()

	if (enabled) Box(modifier = Modifier.fillMaxSize()) {
		AnimatedContent(
			targetState = currentBackground,
			transitionSpec = {
				val duration = (BackgroundService.TRANSITION_DURATION.inWholeMilliseconds / 2).toInt()
				fadeIn(tween(durationMillis = duration)) togetherWith fadeOut(snap(delayMillis = duration))
			},
			label = "BackgroundTransition",
		) { background ->
			if (background != null) {
				Box(modifier = Modifier.fillMaxSize()) {
					Image(
						bitmap = background,
						contentDescription = null,
						alignment = Alignment.Center,
						contentScale = ContentScale.Crop,
						modifier = Modifier
							.fillMaxSize()
							.then(if (blurBackground && !spotlight) Modifier.blur(10.dp) else Modifier)
					)
					// JellyChan: backdrop fades into the navy base. In spotlight mode (details header)
					// the image stays clear at the top, like the JellyChan item page.
					val overlay by animateFloatAsState(if (spotlight) 0f else 1f, tween(400), label = "BackdropDim")
					SpotlightGradient()
					Box(
						modifier = Modifier
							.fillMaxSize()
							.graphicsLayer { alpha = overlay }
							.background(
								Brush.verticalGradient(
									0.0f to Color(0xBF111827),
									0.4f to Color(0xE0111827),
									0.7f to Color(0xF5111827),
									1.0f to Color(0xFF111827),
								)
							)
					)
					Box(
						modifier = Modifier
							.fillMaxSize()
							.graphicsLayer { alpha = overlay }
							.background(
								Brush.horizontalGradient(
									0.0f to Color(0x99111827),
									0.6f to Color(0x00111827),
								)
							)
					)
				}
			} else {
				AppThemeBackground()
			}
		}

		// JellyChan: muted trailer over the backdrop (home carousel), outside AnimatedContent
		// so backdrop rotation does not restart it
		val url = trailerUrl
		if (spotlight && url != null) {
			TrailerLayer(url, backgroundService)
			SpotlightGradient()
		}
	}
}

@Composable
private fun SpotlightGradient() = Box(
	modifier = Modifier
		.fillMaxSize()
		.background(
			Brush.verticalGradient(
				0.0f to Color(0xE6111827),
				0.1f to Color(0xB3111827),
				0.24f to Color(0x00111827),
				0.34f to Color(0x0A111827),
				0.52f to Color(0x99111827),
				0.66f to Color(0xE0111827),
				0.8f to Color(0xF7111827),
				1.0f to Color(0xFF111827),
			)
		)
)


@Composable
private fun TrailerLayer(url: String, backgroundService: BackgroundService) {
	val context = LocalContext.current
	var visible by remember(url) { mutableStateOf(false) }
	val player = remember {
		androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
			volume = 0f
		}
	}

	DisposableEffect(url) {
		val listener = object : androidx.media3.common.Player.Listener {
			override fun onRenderedFirstFrame() {
				visible = true
			}

			override fun onPlaybackStateChanged(playbackState: Int) {
				if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
					visible = false
					backgroundService.trailerEnded.tryEmit(url)
				}
			}

			override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
				timber.log.Timber.w(error, "JellyChan trailer failed")
				visible = false
				backgroundService.trailerEnded.tryEmit(url)
			}
		}
		player.addListener(listener)
		player.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
		player.prepare()
		player.playWhenReady = true

		onDispose {
			player.removeListener(listener)
			player.stop()
		}
	}

	DisposableEffect(Unit) {
		onDispose { player.release() }
	}

	val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(800), label = "TrailerAlpha")
	androidx.compose.ui.viewinterop.AndroidView(
		factory = { ctx ->
			android.view.TextureView(ctx).also { player.setVideoTextureView(it) }
		},
		modifier = Modifier
			.fillMaxSize()
			.graphicsLayer { this.alpha = alpha },
	)
}
