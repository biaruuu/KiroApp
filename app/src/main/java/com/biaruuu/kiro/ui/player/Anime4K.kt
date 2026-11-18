@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.biaruuu.kiro.ui.player

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Anime line sharpener and 4K upscaler.
 *
 * Runs Anime4K (bloc97, MIT) mpv "user shaders" on the GPU as a Media3 video effect, the same idea
 * NuvioMobile-Enhanced uses. The parser, planner and GL driver here are Kiro's own code.
 */
internal object Anime4K {
    /** Short status for the player menu, e.g. "720p to 1440p"; null when nothing is running. */
    val status = MutableStateFlow<String?>(null)

    private const val MAX_UPSCALE_INPUT_HEIGHT = 1080

    @UnstableApi
    fun effects(context: Context, sharpen: Boolean, upscale: Boolean): List<Effect> {
        if (!sharpen && !upscale) return emptyList()
        return try {
            val assets = context.applicationContext.assets
            fun load(name: String) = ShaderParser.parse(assets.open("anime4k/$name").bufferedReader().use { it.readText() })
            val restore = if (sharpen) load("Anime4K_Restore_CNN_S.glsl") else emptyList()
            val up = if (upscale) load("Anime4K_Upscale_CNN_x2_S.glsl") else emptyList()
            val sharp = if (sharpen) load("Kiro_Line_Sharpen.glsl") else emptyList()
            listOf(Anime4KEffect(restore, up, sharp))
        } catch (e: Exception) {
            Log.w(TAG, "Could not load Anime4K shaders", e)
            emptyList()
        }
    }

    internal const val TAG = "Anime4K"
    internal fun maxUpscaleInput() = MAX_UPSCALE_INPUT_HEIGHT
}

internal class ShaderPass(
    val desc: String,
    val hook: String,
    val binds: List<String>,
    val save: String?,
    val width: String?,
    val height: String?,
    val body: String,
)

/** Reads the mpv user-shader text format (//!DESC, //!HOOK, //!BIND, //!SAVE, //!WIDTH, //!HEIGHT). */
internal object ShaderParser {
    fun parse(text: String): List<ShaderPass> {
        val passes = mutableListOf<ShaderPass>()
        var desc = ""; var hook = "MAIN"; var save: String? = null
        var w: String? = null; var h: String? = null
        var binds = mutableListOf<String>()
        val body = StringBuilder()
        var open = false
        fun flush() {
            if (open) passes += ShaderPass(desc, hook, binds, save, w, h, body.toString())
            desc = ""; hook = "MAIN"; save = null; w = null; h = null
            binds = mutableListOf(); body.setLength(0)
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("//!")) {
                val key = line.substring(3).substringBefore(' ')
                val value = line.substring(3).substringAfter(' ', "").trim()
                if (key == "DESC") { flush(); open = true; desc = value; continue }
                when (key) {
                    "HOOK" -> hook = value
                    "BIND" -> binds += value
                    "SAVE" -> save = value
                    "WIDTH" -> w = value
                    "HEIGHT" -> h = value
                    // WHEN (resolution conditions) and COMPONENTS are decided by Kiro itself.
                }
            } else if (open) {
                body.append(raw).append('\n')
            }
        }
        flush()
        return passes
    }

    /** Reverse-polish size expression such as "conv2d_last_tf.w 2 *". */
    fun eval(expr: String?, sizes: Map<String, Pair<Int, Int>>, fallback: Int, wide: Boolean): Int {
        if (expr == null) return fallback
        val stack = ArrayDeque<Double>()
        for (t in expr.trim().split(Regex("\\s+"))) {
            when (t) {
                "+", "-", "*", "/" -> {
                    val b = stack.removeLast(); val a = stack.removeLast()
                    stack.addLast(when (t) { "+" -> a + b; "-" -> a - b; "*" -> a * b; else -> a / b })
                }
                else -> {
                    val num = t.toDoubleOrNull()
                    if (num != null) stack.addLast(num) else {
                        val name = t.substringBeforeLast('.')
                        val s = sizes[if (name == "HOOKED") "MAIN" else name] ?: error("unknown size $t")
                        stack.addLast((if (t.endsWith(".w")) s.first else s.second).toDouble())
                    }
                }
            }
        }
        return stack.last().toInt().coerceAtLeast(1)
    }
}

@UnstableApi
private class Anime4KEffect(
    private val restore: List<ShaderPass>,
    private val upscale: List<ShaderPass>,
    private val sharpen: List<ShaderPass>,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Anime4KProgram(restore, upscale, sharpen, useHdr)
}

private class Planned(val pass: ShaderPass, val target: String, val w: Int, val h: Int)

@UnstableApi
private class Anime4KProgram(
    private val restore: List<ShaderPass>,
    private val upscale: List<ShaderPass>,
    private val sharpen: List<ShaderPass>,
    private val useHdr: Boolean,
) : BaseGlShaderProgram(false, 3) {

    private class Compiled(val program: Int, val pos: Int, val binds: List<String>) {
        val raw = IntArray(binds.size); val size = IntArray(binds.size); val pt = IntArray(binds.size)
    }
    private class Target(val tex: Int, val fbo: Int, val w: Int, val h: Int)

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
    }
    private var plan = emptyList<Planned>()
    private val compiled = mutableMapOf<ShaderPass, Compiled>()
    private val targets = mutableListOf<Target?>()
    private var copy: Compiled? = null
    private var inW = 0
    private var inH = 0
    private var outW = 0
    private var outH = 0
    private var broken = false
    private var glReady = true

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        releaseTargets()
        inW = inputWidth; inH = inputHeight; outW = inputWidth; outH = inputHeight
        plan = emptyList()
        var label: String? = null
        glReady = glOk()
        if (!broken && !useHdr && glReady) {
            try {
                val chain = mutableListOf<ShaderPass>()
                chain += restore
                val doUp = upscale.isNotEmpty() && inputHeight <= Anime4K.maxUpscaleInput()
                if (doUp) chain += upscale
                chain += sharpen
                val sizes = mutableMapOf("MAIN" to (inputWidth to inputHeight))
                val planned = mutableListOf<Planned>()
                for (p in chain) {
                    val target = p.save ?: p.hook
                    val base = sizes[if (p.hook == "HOOKED") "MAIN" else p.hook] ?: sizes.getValue("MAIN")
                    val w = ShaderParser.eval(p.width, sizes, base.first, true)
                    val h = ShaderParser.eval(p.height, sizes, base.second, false)
                    sizes[target] = w to h
                    planned += Planned(p, target, w, h)
                }
                val last = planned.lastOrNull()
                if (last != null && last.target == "MAIN") {
                    for (pl in planned) if (pl.pass !in compiled) compiled[pl.pass] = compileFor(pl)
                    planned.forEachIndexed { i, pl ->
                        targets += if (i == planned.lastIndex) null else {
                            val tex = GlUtil.createTexture(pl.w, pl.h, true)
                            Target(tex, GlUtil.createFboForTexture(tex), pl.w, pl.h)
                        }
                    }
                    plan = planned
                    outW = last.w; outH = last.h
                    label = buildString {
                        if (restore.isNotEmpty() || sharpen.isNotEmpty()) append("Sharpener")
                        if (doUp) { if (isNotEmpty()) append(" + "); append("${minOf(inputWidth, inputHeight)}p to ${minOf(outW, outH)}p") }
                    }
                }
            } catch (e: Exception) {
                Log.w(Anime4K.TAG, "Anime4K setup failed, passing frames through", e)
                broken = true
                releaseTargets()
                plan = emptyList(); outW = inputWidth; outH = inputHeight; label = null
            }
        }
        Anime4K.status.value = label ?: when {
            useHdr -> "!HDR video"
            broken -> "!shader error"
            !glReady -> "!needs OpenGL ES 3"
            else -> null
        }
        if (copy == null) {
            copy = try {
                build(COPY_VS, COPY_FS, listOf("MAIN"))
            } catch (e: Exception) {
                throw VideoFrameProcessingException(e)
            }
        }
        return Size(outW, outH)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val outFbo = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, outFbo, 0)
            GLES20.glDisable(GLES20.GL_BLEND)
            if (plan.isEmpty()) {
                GlUtil.focusFramebufferUsingCurrentContext(outFbo[0], outW, outH)
                draw(checkNotNull(copy), listOf(Triple(inputTexId, inW, inH)))
                return
            }
            val tex = mutableMapOf("MAIN" to Triple(inputTexId, inW, inH))
            plan.forEachIndexed { i, pl ->
                val t = targets[i]
                if (t == null) GlUtil.focusFramebufferUsingCurrentContext(outFbo[0], pl.w, pl.h)
                else GlUtil.focusFramebufferUsingCurrentContext(t.fbo, pl.w, pl.h)
                draw(compiled.getValue(pl.pass), pl.pass.binds.map { tex.getValue(it) })
                if (t != null) tex[pl.target] = Triple(t.tex, t.w, t.h)
            }
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        Anime4K.status.value = null
        super.release()
        try {
            releaseTargets()
            compiled.values.forEach { GLES20.glDeleteProgram(it.program) }
            compiled.clear()
            copy?.let { GLES20.glDeleteProgram(it.program) }
            copy = null
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private fun releaseTargets() {
        targets.filterNotNull().forEach { GlUtil.deleteFbo(it.fbo); GlUtil.deleteTexture(it.tex) }
        targets.clear()
    }

    private fun draw(c: Compiled, inputs: List<Triple<Int, Int, Int>>) {
        GLES20.glUseProgram(c.program)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        inputs.forEachIndexed { unit, (id, w, h) ->
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glUniform1i(c.raw[unit], unit)
            GLES20.glUniform2f(c.size[unit], w.toFloat(), h.toFloat())
            GLES20.glUniform2f(c.pt[unit], 1f / w, 1f / h)
        }
        quad.position(0)
        GLES20.glEnableVertexAttribArray(c.pos)
        GLES20.glVertexAttribPointer(c.pos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(c.pos)
    }

    private fun compileFor(pl: Planned) = build(PASS_VS, fragmentFor(pl), pl.pass.binds)

    private fun fragmentFor(pl: Planned): String = buildString {
        append("#version 300 es\nprecision highp float;\nprecision highp int;\n")
        append("in vec2 vTex;\nout vec4 kFrag;\nvec2 kPos;\n")
        val names = pl.pass.binds.toMutableList()
        for (n in names) {
            append("uniform sampler2D ${n}_raw;\nuniform vec2 ${n}_size;\nuniform vec2 ${n}_pt;\n")
            append("#define ${n}_pos kPos\n#define ${n}_mul 1.0\n")
            append("#define ${n}_tex(p) texture(${n}_raw, vec2((p).x, 1.0 - (p).y))\n")
            append("#define ${n}_texOff(o) ${n}_tex(${n}_pos + ${n}_pt * vec2(o))\n")
        }
        if ("MAIN" in names) {
            append("#define HOOKED_raw MAIN_raw\n#define HOOKED_size MAIN_size\n#define HOOKED_pt MAIN_pt\n")
            append("#define HOOKED_pos MAIN_pos\n#define HOOKED_mul MAIN_mul\n")
            append("#define HOOKED_tex(p) MAIN_tex(p)\n#define HOOKED_texOff(o) MAIN_texOff(o)\n")
        }
        append(pl.pass.body)
        append("\nvoid main() {\n    kPos = vec2(vTex.x, 1.0 - vTex.y);\n")
        append(if (pl.target == "MAIN") "    kFrag = vec4(hook().rgb, 1.0);\n" else "    kFrag = hook();\n")
        append("}\n")
    }

    private fun build(vs: String, fs: String, binds: List<String>): Compiled {
        val v = shader(GLES20.GL_VERTEX_SHADER, vs)
        val f = try { shader(GLES20.GL_FRAGMENT_SHADER, fs) } catch (e: Exception) { GLES20.glDeleteShader(v); throw e }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(v); GLES20.glDeleteShader(f)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(p); GLES20.glDeleteProgram(p)
            throw IllegalStateException("link failed: $log")
        }
        val c = Compiled(p, GLES20.glGetAttribLocation(p, "aPos"), binds)
        binds.forEachIndexed { i, n ->
            c.raw[i] = GLES20.glGetUniformLocation(p, "${n}_raw")
            c.size[i] = GLES20.glGetUniformLocation(p, "${n}_size")
            c.pt[i] = GLES20.glGetUniformLocation(p, "${n}_pt")
        }
        return c
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(s); GLES20.glDeleteShader(s)
            throw IllegalStateException("compile failed: $log")
        }
        return s
    }

    /** Needs OpenGL ES 3.0+ with float render targets. */
    private fun glOk(): Boolean {
        val v = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
        val m = Regex("OpenGL ES (\\d+)\\.(\\d+)").find(v) ?: return false
        val major = m.groupValues[1].toInt(); val minor = m.groupValues[2].toInt()
        if (major < 3) return false
        if (major > 3 || minor >= 2) return true
        val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
        return "GL_EXT_color_buffer_half_float" in ext || "GL_EXT_color_buffer_float" in ext
    }

    private companion object {
        const val PASS_VS = "#version 300 es\nin vec2 aPos;\nout vec2 vTex;\n" +
            "void main() { vTex = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n"
        const val COPY_VS = "#version 300 es\nin vec2 aPos;\nout vec2 vTex;\n" +
            "void main() { vTex = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n"
        const val COPY_FS = "#version 300 es\nprecision highp float;\nin vec2 vTex;\nout vec4 kFrag;\n" +
            "uniform sampler2D MAIN_raw;\nuniform vec2 MAIN_size;\nuniform vec2 MAIN_pt;\n" +
            "void main() { kFrag = texture(MAIN_raw, vTex); }\n"
    }
}
