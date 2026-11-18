package org.jellyfin.androidtv.ui.background

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.drawWithContent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntOffset
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
	val framed by backgroundService.framed.collectAsState()
	val carousel by backgroundService.carousel.collectAsState()

	if (enabled) Box(modifier = Modifier.fillMaxSize()) {
		AnimatedContent(
			targetState = currentBackground,
			transitionSpec = {
				val duration = (BackgroundService.TRANSITION_DURATION.inWholeMilliseconds / 2).toInt()
				fadeIn(tween(durationMillis = duration)) togetherWith fadeOut(snap(delayMillis = duration))
			},
			label = "BackgroundTransition",
		) { background ->
			if (background != null && framed) {
				FramedBackdrop(background)
			} else if (background != null) {
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
					if (carousel) CarouselGradient() else SpotlightGradient()
					// JellyChan: navy veil at the top and on the left, so the texts and the clock read on light images
					BackdropVeil(left = if (carousel) CarouselLeftVeil else SpotlightLeftVeil, tint = carousel)
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
		if (spotlight && !framed && url != null) {
			TrailerLayer(url, backgroundService)
			SpotlightGradient()
			BackdropVeil(left = SpotlightLeftVeil, tint = false)
		} else if (framed && url != null) {
			// JellyChan: home rows, the trailer of the selected card in the backdrop frame, with the same vignette
			TrailerLayer(url, backgroundService, framed = true)
		}

		// JellyChan: home rows, the top of the framed backdrop (and of its trailer) is veiled under the clock
		if (framed && currentBackground != null) BackdropVeil(left = null, tint = false)
	}
}

/** JellyChan: the veil darkens the top band of every backdrop, where the clock is. */
private val TopVeil = Brush.verticalGradient(
	0.0f to Color(0xB3111827),
	0.08f to Color(0x80111827),
	0.2f to Color(0x26111827),
	0.3f to Color(0x00111827),
)

/** Home carousel: the left half, under the logo, the texts and the rail icons. */
private val CarouselLeftVeil = Brush.horizontalGradient(
	0.0f to Color(0xD9111827),
	0.3f to Color(0x8C111827),
	0.55f to Color(0x26111827),
	0.7f to Color(0x00111827),
)

/** Details page: lighter, its own gradient already darkens the bottom. */
private val SpotlightLeftVeil = Brush.horizontalGradient(
	0.0f to Color(0x99111827),
	0.35f to Color(0x33111827),
	0.55f to Color(0x00111827),
)

/** A light navy tint all over the carousel image, so the brightest spots never win over the texts. */
private val VeilTint = Color(0x1F111827)

@Composable
private fun BackdropVeil(left: Brush?, tint: Boolean) = Box(
	modifier = Modifier
		.fillMaxSize()
		.drawBehind {
			if (tint) drawRect(VeilTint)
			drawRect(TopVeil)
			if (left != null) drawRect(left)
		}
)

private val Navy = Color(0xFF111827)
private val NavyLight = Color(0xFF1B2436)

/** Navy base behind the home backdrop, a little lighter at the top. */
private val FramedBase = Brush.verticalGradient(
	0.0f to NavyLight,
	0.55f to Navy,
	1.0f to Navy,
)

/**
 * JellyChan: home rows. The backdrop fills the top right of the screen and fades out with a rounded vignette to the left and to the
 * bottom, over a fixed navy base (a little lighter at the top left) that stays the same from one card to the next.
 */
@Composable
private fun FramedBackdrop(background: ImageBitmap) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(FramedBase)
	) {
		Image(
			bitmap = background,
			contentDescription = null,
			alignment = Alignment.TopEnd,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.align(Alignment.TopEnd)
				.fillMaxWidth(FRAMED_WIDTH)
				.fillMaxHeight(FRAMED_HEIGHT)
				.framedVignette()
		)
	}
}

/** Vignette of the home backdrop: the top and right edges stay sharp, the left and the bottom melt into the navy base. */
private fun Modifier.framedVignette() = this
	.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
	.drawWithContent {
		drawContent()
		drawImage(
			image = VignetteMask,
			dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
			blendMode = BlendMode.DstIn,
		)
	}

/** Exponent of the vignette shape: 2 is an ellipse, higher values get closer to a rectangle with a rounded corner. */
private const val VIGNETTE_SHAPE = 3.5f

/** Where the vignette starts fading, as a fraction of the distance from the top right corner to the far edges. */
private const val VIGNETTE_START = 0.42f

/**
 * JellyChan: alpha mask of the home backdrop, opaque at the top right corner. The fade follows a superellipse (a
 * "squircle", less oval than an ellipse) and a smoothstep, so light images show no line. Built once, scaled to the image.
 */
private val VignetteMask: ImageBitmap by lazy {
	val side = 512
	val pixels = IntArray(side * side)
	for (y in 0 until side) {
		val v = y.toFloat() / (side - 1)
		for (x in 0 until side) {
			val u = (side - 1 - x).toFloat() / (side - 1)
			val distance = (u.pow(VIGNETTE_SHAPE) + v.pow(VIGNETTE_SHAPE)).pow(1f / VIGNETTE_SHAPE)
			val t = ((distance - VIGNETTE_START) / (1f - VIGNETTE_START)).coerceIn(0f, 1f)
			val alpha = 1f - t * t * (3f - 2f * t)
			pixels[y * side + x] = (alpha * 255).roundToInt() shl 24
		}
	}
	Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The backdrop takes the right 76% of the width (from 230dp) and the top 78% of the height. */
private const val FRAMED_WIDTH = 0.76f
private const val FRAMED_HEIGHT = 0.78f

/**
 * JellyChan: home carousel. The backdrop stays clean; only the bottom fades into the navy base, from a little above the
 * first row (it peeks in at about 83% of the height).
 */
@Composable
private fun CarouselGradient() = Box(
	modifier = Modifier
		.fillMaxSize()
		.background(
			Brush.verticalGradient(
				0.0f to Color(0x00111827),
				0.66f to Color(0x00111827),
				0.76f to Color(0x99111827),
				0.84f to Color(0xEB111827),
				1.0f to Color(0xFF111827),
			)
		)
)

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


/** JellyChan: trailer sound, low under the home. */
private const val TRAILER_VOLUME = 0.25f

@Composable
private fun TrailerLayer(url: String, backgroundService: BackgroundService, framed: Boolean = false) {
	val context = LocalContext.current
	var visible by remember(url) { mutableStateOf(false) }
	val player = remember {
		// JellyChan: an audio track the device cannot play is dropped instead of keeping the player stuck
		val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
			setParameters(
				buildUponParameters()
					.setExceedRendererCapabilitiesIfNecessary(false)
					.setExceedAudioConstraintsIfNecessary(false)
			)
		}
		androidx.media3.exoplayer.ExoPlayer.Builder(context)
			.setTrackSelector(trackSelector)
			.build()
			.apply {
				volume = 0f
				// Low sound, and it gives way to (and never plays over) any other playback
				setAudioAttributes(
					androidx.media3.common.AudioAttributes.Builder()
						.setUsage(androidx.media3.common.C.USAGE_MEDIA)
						.setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
						.build(),
					true,
				)
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

	// JellyChan: in the frame the vignette comes first and the video fades in under it, so its edges never show
	val maskAlpha by animateFloatAsState(if (framed && visible) 1f else 0f, tween(200), label = "TrailerMask")
	val videoVisible = visible && (!framed || maskAlpha == 1f)
	val alpha by animateFloatAsState(if (videoVisible) 1f else 0f, tween(800), label = "TrailerAlpha")
	// The volume follows the fade (a snapshot flow: the animation does not recompose this function)
	LaunchedEffect(player) {
		snapshotFlow { alpha }.collect { player.volume = TRAILER_VOLUME * it }
	}
	androidx.compose.ui.viewinterop.AndroidView(
		factory = { ctx ->
			android.view.TextureView(ctx).also { player.setVideoTextureView(it) }
		},
		modifier = Modifier
			.fillMaxSize()
			.then(
				if (framed) Modifier.wrapContentSize(Alignment.TopEnd).fillMaxWidth(FRAMED_WIDTH).fillMaxHeight(FRAMED_HEIGHT)
				else Modifier
			)
			.graphicsLayer { this.alpha = alpha },
	)

	// JellyChan: the video surface does not draw inside a masked layer, so the vignette of the frame is drawn over it:
	// the navy base, see-through where the frame is clear
	if (framed) Box(
		modifier = Modifier
			.fillMaxSize()
			.graphicsLayer {
				this.alpha = maskAlpha
				compositingStrategy = CompositingStrategy.Offscreen
			}
			.drawBehind {
				drawRect(FramedBase)
				val width = size.width * FRAMED_WIDTH
				drawImage(
					image = VignetteMask,
					dstOffset = IntOffset((size.width - width).roundToInt(), 0),
					dstSize = IntSize(width.roundToInt(), (size.height * FRAMED_HEIGHT).roundToInt()),
					blendMode = BlendMode.DstOut,
				)
			}
	)
}
