package com.biaruuu.kiro.ui.auth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.ProfileRepository
import com.biaruuu.kiro.ui.components.PageTopBar
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val Muted = Color(0xFF9A9A9F)
private val ErrorRed = Color(0xFFFF6B6B)

/**
 * Sits between sign-in and the app: a brand-new account has to pick a username (then optionally a
 * bio and a photo) before it gets in. Accounts that already have one go straight through.
 */
@Composable
fun ProfileGate(uid: String, fallbackName: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val profile by ProfileRepository.profile.collectAsState()
    val known = remember(uid) { ProfileRepository.knownDone(context, uid) }
    var timedOut by remember(uid) { mutableStateOf(false) }
    LaunchedEffect(uid) {
        delay(6000)
        timedOut = true
    }
    when {
        profile.username.isNotBlank() || known -> content()
        profile.loaded -> OnboardingFlow(fallbackName)
        timedOut -> content() // offline and nothing cached: don't trap the user
        else -> Box(Modifier.fillMaxSize().background(KiroBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
    }
}

// ---------------------------------------------------------------- Onboarding

@Composable
private fun OnboardingFlow(suggestion: String) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var username by rememberSaveable {
        mutableStateOf(suggestion.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(ProfileRepository.MAX_USERNAME))
    }
    var bio by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = step > 0) { step -= 1 }
    val pick = rememberAvatarFlow { avatar = it }

    val nameOk = ProfileRepository.validUsername(username)

    fun finish() {
        if (busy) return
        busy = true
        error = null
        ProfileRepository.save(username = username, bio = bio, avatar = avatar.ifEmpty { null }) { err ->
            busy = false
            error = err
        }
        // Once the profile is saved the gate lets the user into the app by itself.
    }

    CenteredPage {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            PageTopBar(
                title = when (step) { 0 -> "Username"; 1 -> "Bio"; else -> "Profile photo" },
                onBack = if (step > 0) ({ step -= 1 }) else null,
            )
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)) {
                Text("${step + 1} / 3", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    when (step) {
                        0 -> "Enter your username"
                        1 -> "Tell us about yourself"
                        else -> "Upload your profile photo"
                    },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (step) {
                        0 -> "This is how you appear in Kiro. You can change it any time in Account Setting."
                        1 -> "Add a short bio. You can skip this and add it later."
                        else -> "Pick a photo and crop it to fit. You can skip this and add it later."
                    },
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(24.dp))
                when (step) {
                    0 -> {
                        Text("Username", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        UnderlineField(
                            value = username,
                            onChange = { if (it.length <= ProfileRepository.MAX_USERNAME) username = it.trim() },
                            hint = "Enter username",
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next,
                            onImeAction = { if (nameOk) step = 1 },
                        )
                        Spacer(Modifier.height(16.dp))
                        CheckBox("3-20 characters: letters, numbers, _ or .", nameOk)
                    }
                    1 -> {
                        Text("Bio", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        UnderlineField(
                            value = bio,
                            onChange = { if (it.length <= ProfileRepository.MAX_BIO) bio = it },
                            hint = "Write something about you",
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Default,
                            onImeAction = {},
                            singleLine = false,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${bio.length} / ${ProfileRepository.MAX_BIO}",
                            color = Muted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            AvatarEditable(avatar, username, 132.dp, onClick = pick)
                        }
                        error?.let {
                            Spacer(Modifier.height(20.dp))
                            Text(it, color = ErrorRed, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            if (step > 0) {
                Text(
                    "Skip",
                    color = Muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { if (step == 1) { bio = ""; step = 2 } else { avatar = ""; finish() } }
                        .padding(vertical = 12.dp),
                )
            }
            PrimaryButton(
                label = if (step < 2) "Next" else "Done",
                enabled = if (step == 0) nameOk else true,
                busy = busy,
                onClick = { if (step < 2) step += 1 else finish() },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Avatar

/** The profile picture, or the first letter of the name on purple when there is none. */
@Composable
fun Avatar(avatar: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(avatar) { decodeAvatar(avatar) }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color(0xFF2A2A2E)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                (name.firstOrNull() ?: 'K').uppercase(),
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/** [Avatar] with a small camera badge; tapping opens the photo picker. */
@Composable
fun AvatarEditable(avatar: String, name: String, size: Dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clickable(onClick = onClick)) {
        Avatar(avatar, name, size)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.3f)
                .clip(CircleShape)
                .background(Color(0xFF2E2E30)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.fluent_camera_filled),
                contentDescription = "Change photo",
                tint = Color.White,
                modifier = Modifier.size(size * 0.16f),
            )
        }
    }
}

private fun decodeAvatar(b64: String): ImageBitmap? {
    if (b64.isEmpty()) return null
    return try {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/**
 * Returns a function that opens the photo picker, then the crop screen; [onCropped] receives the
 * finished picture as base64 JPEG.
 */
@Composable
fun rememberAvatarFlow(onCropped: (String) -> Unit): () -> Unit {
    var picked by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked = uri
    }
    picked?.let { uri ->
        AvatarCropDialog(
            uri = uri,
            onCancel = { picked = null },
            onCropped = {
                picked = null
                onCropped(it)
            },
        )
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

private suspend fun loadBitmap(context: android.content.Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@withContext null
        val orientation = try {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) raw
        else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
    } catch (_: Exception) {
        null
    }
}

/** Full-screen crop: drag and pinch the photo under a circle, then confirm. */
@Composable
private fun AvatarCropDialog(uri: Uri, onCancel: () -> Unit, onCropped: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var busy by remember { mutableStateOf(false) }
    val margin = with(LocalDensity.current) { 24.dp.toPx() }

    LaunchedEffect(uri) {
        val b = loadBitmap(context, uri)
        bitmap = b
        failed = b == null
    }

    val bmp = bitmap
    // Side of the square the circle is cut from, and the scale at which the photo just covers it.
    val crop = min(area.width, area.height) - 2 * margin
    val baseScale = if (bmp != null && crop > 0f) crop / min(bmp.width, bmp.height) else 1f

    fun clampOffset(o: Offset, z: Float): Offset {
        if (bmp == null) return Offset.Zero
        val s = baseScale * z
        val maxX = max(0f, (bmp.width * s - crop) / 2f)
        val maxY = max(0f, (bmp.height * s - crop) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            PageTopBar("Move and scale", onCancel)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { area = it }
                    .pointerInput(bmp, crop) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val z = (zoom * gestureZoom).coerceIn(1f, 6f)
                            zoom = z
                            offset = clampOffset(offset + pan, z)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null && crop > 0f) {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    Canvas(Modifier.fillMaxSize()) {
                        val s = baseScale * zoom
                        val w = bmp.width * s
                        val h = bmp.height * s
                        val left = size.width / 2f + offset.x - w / 2f
                        val top = size.height / 2f + offset.y - h / 2f
                        drawImage(
                            image = image,
                            srcSize = IntSize(bmp.width, bmp.height),
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        )
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dim = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                            addOval(androidx.compose.ui.geometry.Rect(center, crop / 2f))
                        }
                        drawPath(dim, Color.Black.copy(alpha = 0.65f))
                        drawCircle(Color.White, radius = crop / 2f, center = center, style = Stroke(width = 1.5.dp.toPx()))
                    }
                } else if (failed) {
                    Text("Couldn't open this photo.", color = Muted, fontSize = 14.sp)
                } else {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
            PrimaryButton(
                label = "Choose",
                enabled = bmp != null,
                busy = busy,
                onClick = {
                    val b = bmp ?: return@PrimaryButton
                    busy = true
                    val s = baseScale * zoom
                    val o = offset
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            // Where the circle's square sits on the original photo.
                            val left = area.width / 2f + o.x - b.width * s / 2f
                            val top = area.height / 2f + o.y - b.height * s / 2f
                            val cropLeft = area.width / 2f - crop / 2f
                            val cropTop = area.height / 2f - crop / 2f
                            val x = ((cropLeft - left) / s).roundToInt().coerceIn(0, b.width - 1)
                            val y = ((cropTop - top) / s).roundToInt().coerceIn(0, b.height - 1)
                            val side = max(1, min((crop / s).roundToInt(), min(b.width - x, b.height - y)))
                            val square = Bitmap.createBitmap(b, x, y, side, side)
                            val out = Bitmap.createScaledBitmap(square, 256, 256, true)
                            val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                            Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                        }
                        busy = false
                        onCropped(result)
                    }
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
            )
        }
    }
}
