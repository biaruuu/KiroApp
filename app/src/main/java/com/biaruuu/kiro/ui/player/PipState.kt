package com.biaruuu.kiro.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow

/** Picture-in-picture: lets the player shrink into a small floating window while you use other apps. */
object PipState {
    /** True while the phone shows the player in the small window. */
    val inPip = MutableStateFlow(false)

    /** Set by the player: leaving the app should open the small window (a video is playing). */
    @Volatile var canEnter = false

    fun supported(activity: Activity?): Boolean =
        activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Opens the small window; returns false when this phone or setting does not allow it. */
    fun enter(activity: Activity?): Boolean {
        if (!supported(activity)) return false
        return try {
            inPip.value = true // set first: the activity pauses while entering and must not stop the video
            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            val ok = activity!!.enterPictureInPictureMode(params)
            if (!ok) inPip.value = false
            ok
        } catch (_: Exception) {
            inPip.value = false
            false
        }
    }
}
