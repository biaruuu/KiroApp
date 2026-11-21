@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.biaruuu.kiro.enhancer

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.os.Build
import android.os.PowerManager
import com.biaruuu.kiro.core.NativeCore

/** What this phone can do, measured once. The GPU name is cached because asking for it needs a GL context. */
class DeviceProfile(val ramMb: Int, val cores: Int, val sdk: Int, val renderer: String) {
    val gpu: Int get() = NativeCore.gpuScore(renderer)
    val ceiling: Int get() = NativeCore.enhancerCeiling(ramMb, cores, sdk, gpu)

    companion object {
        private const val PREFS = "kiro_enhancer"

        /** Reads the profile. Call off the main thread: the first run creates a tiny GL context. */
        fun load(context: Context): DeviceProfile {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val ram = (info.totalMem / (1024 * 1024)).toInt()
            val cores = Runtime.getRuntime().availableProcessors()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            var renderer = prefs.getString("gl_renderer", null)
            if (renderer == null) {
                renderer = queryRenderer()
                if (renderer.isNotEmpty()) prefs.edit().putString("gl_renderer", renderer).apply()
            }
            return DeviceProfile(ram, cores, Build.VERSION.SDK_INT, renderer)
        }

        fun batterySaver(context: Context): Boolean =
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

        /** 0 none ... 3 severe ... 6 shutdown. Unknown (old Android) counts as 0. */
        fun thermal(context: Context): Int {
            if (Build.VERSION.SDK_INT < 29) return 0
            return (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus ?: 0
        }

        private fun queryRenderer(): String {
            var display = EGL14.EGL_NO_DISPLAY
            var ctx = EGL14.EGL_NO_CONTEXT
            var surf = EGL14.EGL_NO_SURFACE
            try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                val v = IntArray(2)
                if (!EGL14.eglInitialize(display, v, 0, v, 1)) return ""
                val attribs = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE,
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val n = IntArray(1)
                if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, n, 0) || n[0] == 0) return ""
                ctx = EGL14.eglCreateContext(
                    display, configs[0], EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
                )
                surf = EGL14.eglCreatePbufferSurface(
                    display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
                )
                if (!EGL14.eglMakeCurrent(display, surf, surf, ctx)) return ""
                return android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER) ?: ""
            } catch (_: Throwable) {
                return ""
            } finally {
                try {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surf)
                    if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, ctx)
                    if (display != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
