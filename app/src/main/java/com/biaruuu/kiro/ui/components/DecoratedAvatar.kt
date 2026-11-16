package com.biaruuu.kiro.ui.components

import android.graphics.BitmapFactory
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

private fun decode(context: android.content.Context, asset: String?, data: String?): Drawable? = try {
    val bytes = data?.let { Base64.decode(it, Base64.DEFAULT) }
    if (Build.VERSION.SDK_INT >= 28) {
        val source = if (asset != null) ImageDecoder.createSource(context.assets, asset) else ImageDecoder.createSource(ByteBuffer.wrap(bytes!!))
        ImageDecoder.decodeDrawable(source)
    } else {
        val bmp = if (asset != null) context.assets.open(asset).use { BitmapFactory.decodeStream(it) } else BitmapFactory.decodeByteArray(bytes, 0, bytes!!.size)
        BitmapDrawable(context.resources, bmp)
    }
} catch (_: Throwable) {
    null
}

/** An image that plays when it is animated (animated PNG, GIF, WebP) and sits still otherwise. */
@Composable
fun AnimatedAsset(design: Design, modifier: Modifier = Modifier, crop: Boolean = false) {
    val context = LocalContext.current
    val drawable = remember(design.id, design.asset, design.data) { decode(context, design.asset, design.data) }
    DisposableEffect(drawable) {
        if (Build.VERSION.SDK_INT >= 28 && drawable is AnimatedImageDrawable) {
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            drawable.start()
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= 28 && drawable is AnimatedImageDrawable) drawable.stop()
        }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER } },
        update = { it.setImageDrawable(drawable) },
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
