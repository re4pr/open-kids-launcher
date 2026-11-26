package io.github.re4pr.kidslauncher.skin

import android.graphics.BitmapFactory
import android.graphics.Matrix as AndroidMatrix
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage

/**
 * Hardware-accelerated Live Video Wallpaper backdrop:
 * - Plays seamless looping H.264 `.mp4` theme worlds (`asset:///videos/<theme>.mp4`) via [ExoPlayer]
 *   with automatic center-crop scaling and lifecycle-aware pause/resume (0% battery when backgrounded).
 * - Keeps the high-resolution `.jpg` poster frame directly underneath so theme switches are instant
 *   with zero black flashes, and also supports custom `.gif`/`.webp`/`.mp4` URIs.
 */
@Composable
fun AnimatedSkyBackground(
    palette: ResolvedSkinPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val posterBitmap = remember(palette.sceneAssetPath) {
        palette.sceneAssetPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }

    val activeVideoUri: Uri? = remember(palette.customSceneUri, palette.videoAssetPath) {
        when {
            palette.customSceneUri.endsWith(".mp4", ignoreCase = true) ||
                palette.customSceneUri.endsWith(".webm", ignoreCase = true) ->
                Uri.parse(palette.customSceneUri)
            palette.customSceneUri.isNotBlank() -> null
            palette.videoAssetPath.isNotBlank() ->
                Uri.parse("asset:///${palette.videoAssetPath.trimStart('/')}")
            else -> null
        }
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
        }
    }

    LaunchedEffect(activeVideoUri) {
        if (activeVideoUri != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(activeVideoUri))
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (activeVideoUri != null) exoPlayer.play()
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.skyTop, palette.skyMid, palette.skyBottom)
                )
            )
    ) {
        // Instant high-resolution poster layer (prevents any flash while hardware decoder warms up)
        if (palette.customSceneUri.isNotBlank() && activeVideoUri == null) {
            AsyncImage(
                model = palette.customSceneUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (posterBitmap != null) {
            Image(
                bitmap = posterBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Hardware-decoded seamless looping H.264 MP4 Live Wallpaper!
        if (activeVideoUri != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).also { tv ->
                        exoPlayer.setVideoTextureView(tv)
                        val applyCenterCrop = { videoW: Int, videoH: Int ->
                            val viewW = tv.width
                            val viewH = tv.height
                            if (viewW > 0 && viewH > 0 && videoW > 0 && videoH > 0) {
                                val viewRatio = viewW.toFloat() / viewH.toFloat()
                                val videoRatio = videoW.toFloat() / videoH.toFloat()
                                val sx = if (videoRatio > viewRatio) videoRatio / viewRatio else 1f
                                val sy = if (viewRatio > videoRatio) viewRatio / videoRatio else 1f
                                val m = AndroidMatrix()
                                m.setScale(sx, sy, viewW / 2f, viewH / 2f)
                                tv.setTransform(m)
                            }
                        }
                        exoPlayer.addListener(object : Player.Listener {
                            override fun onVideoSizeChanged(videoSize: VideoSize) {
                                applyCenterCrop(videoSize.width, videoSize.height)
                            }
                        })
                        tv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            val vs = exoPlayer.videoSize
                            applyCenterCrop(vs.width, vs.height)
                        }
                    }
                },
            )
        }

        // Subtle top & bottom contrast scrim so white titles and glass shelves stay crystal-legible
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.16f),
                    0.34f to Color.Transparent,
                    1.0f to palette.skyTop.copy(alpha = 0.42f),
                )
            )
        }

        content()
    }
}
