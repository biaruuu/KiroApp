package com.biaruuu.kiro.ui.components

import android.graphics.BitmapFactory
import android.graphics.drawable.Animatable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.biaruuu.kiro.data.Design
import com.biaruuu.kiro.data.Designs
import com.biaruuu.kiro.ui.auth.Avatar
import java.nio.ByteBuffer

private val frameCache = java.util.concurrent.ConcurrentHashMap<String, Apng.Frames>()

/** Decodes an image off the main thread. Animated PNGs share their decoded frames between every place they are shown. */
private fun load(context: android.content.Context, design: Design): Drawable? = try {
    val bytes: ByteArray = if (design.asset != null) {
        context.assets.open(design.asset).use { it.readBytes() }
    } else {
        Base64.decode(design.data!!, Base64.DEFAULT)
    }
    val key = design.asset ?: (design.id + ":" + design.data.hashCode())
    val frames = frameCache[key] ?: if (Apng.isApng(bytes)) Apng.decode(bytes)?.also { frameCache[key] = it } else null
    if (frames != null) {
        ApngDrawable(frames)
    } else if (Build.VERSION.SDK_INT >= 28) {
        // GIF and animated WebP: Android plays these itself.
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))).also {
            if (it is AnimatedImageDrawable) it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        }
    } else {
        BitmapDrawable(context.resources, BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }
} catch (_: Throwable) {
    null
}

/** An image that plays when it is animated (animated PNG, GIF, WebP) and sits still otherwise. */
@Composable
fun AnimatedAsset(design: Design, modifier: Modifier = Modifier, crop: Boolean = false) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null, design.id, design.asset, design.data) {
        value = withContext(Dispatchers.Default) { load(context, design) }
    }
    DisposableEffect(drawable) {
        onDispose { (drawable as? Animatable)?.stop() }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER } },
        update = {
            it.setImageDrawable(drawable)
            (drawable as? Animatable)?.let { a -> if (!a.isRunning) a.start() }
        },
        modifier = modifier,
    )
}

/** The profile picture with the chosen animated decoration drawn around it (20% larger than the picture). */
@Composable
fun DecoratedAvatar(avatar: String, name: String, size: Dp, decorationId: String?, modifier: Modifier = Modifier) {
    val remote by Designs.remote.collectAsState()
    val design = remember(decorationId, remote) { Designs.find(decorationId) }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Avatar(avatar, name, size)
        if (design != null) AnimatedAsset(design, Modifier.requiredSize(size * 1.2f))
    }
}
