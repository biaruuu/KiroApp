package com.biaruuu.kiro.enhancer;

import android.annotation.SuppressLint;
import android.opengl.GLES20;

import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Size;
import androidx.media3.effect.BaseGlShaderProgram;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * GPU line sharpener based on Anime4K by bloc97 (MIT licence, see licenses/ANIME4K-LICENSE).
 *
 * Tier 1: a single cheap luma sharpen. Tier 2: Anime4K "Deblur DoG" in three passes. Tier 3: tier 2
 * plus the "Darken" line pass at quarter resolution. Passes use only what OpenGL ES 2.0 offers.
 * Frames that arrive while the tier is 0 are copied unchanged.
 */
@SuppressLint("UnsafeOptInUsageError")
final class Anime4kProgram extends BaseGlShaderProgram {

    private static final String VERT =
            "attribute vec4 a_pos;\n"
          + "varying vec2 v_uv;\n"
          + "void main(){ gl_Position = a_pos; v_uv = a_pos.xy * 0.5 + 0.5; }\n";

    private static final String HEAD =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
          + "varying vec2 v_uv;\n"
          + "uniform sampler2D u_a;\n"
          + "uniform sampler2D u_b;\n"
          + "uniform vec2 u_px;\n"
          + "float luma(vec3 c){ return dot(vec3(0.299,0.587,0.114), c); }\n";

    private static final String COPY = HEAD
          + "void main(){ gl_FragColor = vec4(texture2D(u_a, v_uv).rgb, 1.0); }\n";

    /** Tier 1: unsharp on a cross of four neighbours, clamped to the local range. */
    private static final String LIGHT = HEAD
          + "void main(){\n"
          + "  vec4 c = texture2D(u_a, v_uv);\n"
          + "  float l = luma(c.rgb);\n"
          + "  float a = luma(texture2D(u_a, v_uv + vec2(u_px.x,0.0)).rgb);\n"
          + "  float b = luma(texture2D(u_a, v_uv - vec2(u_px.x,0.0)).rgb);\n"
          + "  float d = luma(texture2D(u_a, v_uv + vec2(0.0,u_px.y)).rgb);\n"
          + "  float e = luma(texture2D(u_a, v_uv - vec2(0.0,u_px.y)).rgb);\n"
          + "  float mn = min(min(a,b), min(min(d,e), l));\n"
          + "  float mx = max(max(a,b), max(max(d,e), l));\n"
          + "  float blur = (a+b+d+e) * 0.25;\n"
          + "  float t = clamp(l + (l - blur) * 0.8, mn, mx) - l;\n"
          + "  gl_FragColor = vec4(clamp(c.rgb + t, 0.0, 1.0), 1.0);\n"
          + "}\n";

    /** Deblur DoG kernel X: (gauss7, min3, max3) of luma along x. */
    private static final String DOG_X = HEAD
          + "float L(float o){ return luma(texture2D(u_a, v_uv + vec2(o*u_px.x, 0.0)).rgb); }\n"
          + "void main(){\n"
          + "  float m2=L(-2.0), m1=L(-1.0), c=L(0.0), p1=L(1.0), p2=L(2.0);\n"
          + "  float g = (m2+p2)*0.06136 + (m1+p1)*0.24477 + c*0.38774;\n"
          + "  gl_FragColor = vec4(g, min(min(m1,c),p1), max(max(m1,c),p1), 1.0);\n"
          + "}\n";

    /** Deblur DoG kernel Y on the output of kernel X. */
    private static final String DOG_Y = HEAD
          + "vec3 T(float o){ return texture2D(u_a, v_uv + vec2(0.0, o*u_px.y)).rgb; }\n"
          + "void main(){\n"
          + "  vec3 m2=T(-2.0), m1=T(-1.0), c=T(0.0), p1=T(1.0), p2=T(2.0);\n"
          + "  float g = (m2.x+p2.x)*0.06136 + (m1.x+p1.x)*0.24477 + c.x*0.38774;\n"
          + "  float mn = min(min(m1.y,c.y),p1.y);\n"
          + "  float mx = max(max(m1.z,c.z),p1.z);\n"
          + "  gl_FragColor = vec4(g, mn, mx, 1.0);\n"
          + "}\n";

    /** Deblur DoG apply: u_a is the original frame, u_b the kernel result. */
    private static final String DOG_APPLY = HEAD
          + "void main(){\n"
          + "  vec4 src = texture2D(u_a, v_uv);\n"
          + "  vec3 k = texture2D(u_b, v_uv).rgb;\n"
          + "  float l = luma(src.rgb);\n"
          + "  float c = (l - k.x) * 0.6;\n"
          + "  float range = 0.1 - 0.004;\n"
          + "  float ct = abs(c);\n"
          + "  if (ct > 0.004) {\n"
          + "    ct = (ct - 0.004) / range;\n"
          + "    ct = pow(ct, 0.6);\n"
          + "    ct = ct * range + 0.004;\n"
          + "    ct = ct * sign(c);\n"
          + "  } else { ct = c; }\n"
          + "  float cc = clamp(ct + l, k.y, k.z) - l;\n"
          + "  gl_FragColor = vec4(clamp(src.rgb + cc, 0.0, 1.0), 1.0);\n"
          + "}\n";

    /** Darken step 1: quarter resolution luma. */
    private static final String DARK_Q1 = HEAD
          + "void main(){\n"
          + "  vec2 o = u_px;\n"
          + "  float l = luma(texture2D(u_a, v_uv + vec2(-o.x,-o.y)).rgb)\n"
          + "          + luma(texture2D(u_a, v_uv + vec2( o.x,-o.y)).rgb)\n"
          + "          + luma(texture2D(u_a, v_uv + vec2(-o.x, o.y)).rgb)\n"
          + "          + luma(texture2D(u_a, v_uv + vec2( o.x, o.y)).rgb);\n"
          + "  gl_FragColor = vec4(l * 0.25, 0.0, 0.0, 1.0);\n"
          + "}\n";

    /** Darken step 2: how much darker than its blurred surroundings each point is, times 4. */
    private static final String DARK_Q2 = HEAD
          + "float Q(vec2 o){ return texture2D(u_a, v_uv + o * u_px).x; }\n"
          + "void main(){\n"
          + "  float w0 = 0.1065, w1 = 0.787;\n"
          + "  float g = 0.0;\n"
          + "  g += (Q(vec2(-1.0,-1.0)) + Q(vec2(1.0,-1.0)) + Q(vec2(-1.0,1.0)) + Q(vec2(1.0,1.0))) * w0 * w0;\n"
          + "  g += (Q(vec2(0.0,-1.0)) + Q(vec2(0.0,1.0)) + Q(vec2(-1.0,0.0)) + Q(vec2(1.0,0.0))) * w0 * w1;\n"
          + "  g += Q(vec2(0.0,0.0)) * w1 * w1;\n"
          + "  float c = Q(vec2(0.0,0.0));\n"
          + "  gl_FragColor = vec4(clamp(max(g - c, 0.0) * 4.0, 0.0, 1.0), 0.0, 0.0, 1.0);\n"
          + "}\n";

    /** Darken apply: u_a is the sharpened frame, u_b the line mask. */
    private static final String DARK_APPLY = HEAD
          + "void main(){\n"
          + "  vec4 src = texture2D(u_a, v_uv);\n"
          + "  float m = texture2D(u_b, v_uv).x;\n"
          + "  gl_FragColor = vec4(clamp(src.rgb - m * 0.375, 0.0, 1.0), 1.0);\n"
          + "}\n";

    private static final class Prog {
        int id, aPos, uA, uB, uPx;
    }

    private static final class Target {
        int tex, fbo, w, h;
    }

    private final Anime4kState state;
    private final FloatBuffer quad;

    private Prog copy, light, dogX, dogY, dogApply, darkQ1, darkQ2, darkApply;
    private boolean built;
    private Target a, b, q1, q2;
    private int w, h;

    Anime4kProgram(Anime4kState state, boolean useHdr) throws VideoFrameProcessingException {
        super(useHdr, 1);
        this.state = state;
        float[] v = {-1f, -1f, 0f, 1f, 1f, -1f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 0f, 1f};
        quad = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(v).position(0);
    }

    @Override
    public Size configure(int inputWidth, int inputHeight) {
        if (inputWidth != w || inputHeight != h) {
            freeTargets();
            w = inputWidth;
            h = inputHeight;
        }
        return new Size(inputWidth, inputHeight);
    }

    @Override
    public void drawFrame(int inputTexId, long presentationTimeUs) throws VideoFrameProcessingException {
        int[] fb = new int[1];
        int[] vp = new int[4];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, fb, 0);
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0);
        try {
            ensureBuilt();
            int tier = state.failed ? 0 : state.tier;
            if (tier <= 0 || light == null) {
                draw(copy, inputTexId, 0, fb[0], vp[2], vp[3], vp);
                return;
            }
            if (tier == 1) {
                draw(light, inputTexId, 0, fb[0], vp[2], vp[3], vp);
                return;
            }
            ensureTargets(tier >= 3);
            // Deblur DoG: x, y, then apply. a and b are full size.
            drawInto(dogX, inputTexId, 0, a, w, h);
            drawInto(dogY, a.tex, 0, b, w, h);
            if (tier == 2) {
                draw(dogApply, inputTexId, b.tex, fb[0], vp[2], vp[3], vp);
                return;
            }
            // The first target is free again, so it holds the sharpened frame for the darken step.
            drawInto(dogApply, inputTexId, b.tex, a, w, h);
            drawInto(darkQ1, a.tex, 0, q1, q1.w, q1.h);
            drawInto(darkQ2, q1.tex, 0, q2, q2.w, q2.h);
            draw(darkApply, a.tex, q2.tex, fb[0], vp[2], vp[3], vp);
        } catch (RuntimeException e) {
            state.failed = true;
            throw new VideoFrameProcessingException(e);
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0]);
            GLES20.glViewport(vp[0], vp[1], vp[2], vp[3]);
        }
    }

    @Override
    public void release() throws VideoFrameProcessingException {
        super.release();
        freeTargets();
        Prog[] all = {copy, light, dogX, dogY, dogApply, darkQ1, darkQ2, darkApply};
        for (Prog p : all) if (p != null) GLES20.glDeleteProgram(p.id);
        copy = light = dogX = dogY = dogApply = darkQ1 = darkQ2 = darkApply = null;
    }

    // ---- drawing helpers ----

    private void drawInto(Prog p, int texA, int texB, Target t, int tw, int th) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, t.fbo);
        GLES20.glViewport(0, 0, tw, th);
        run(p, texA, texB, srcW(texA), srcH(texA));
    }

    private void draw(Prog p, int texA, int texB, int fbo, int vw, int vh, int[] vp) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glViewport(vp[0], vp[1], vw, vh);
        run(p, texA, texB, srcW(texA), srcH(texA));
    }

    /** Pixel size of the texture being read, to step exactly one texel. */
    private int srcW(int tex) {
        if (q1 != null && tex == q1.tex) return q1.w;
        return w;
    }

    private int srcH(int tex) {
        if (q1 != null && tex == q1.tex) return q1.h;
        return h;
    }

    private void run(Prog p, int texA, int texB, int sw, int sh) {
        // Darken step 1 reads the full frame but steps in quarter-resolution texels (1 px = 2 source px).
        GLES20.glUseProgram(p.id);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texA);
        GLES20.glUniform1i(p.uA, 0);
        if (p.uB >= 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texB);
            GLES20.glUniform1i(p.uB, 1);
        }
        if (p.uPx >= 0) {
            boolean q1pass = p == darkQ1;
            float f = q1pass ? 2f : 1f;
            GLES20.glUniform2f(p.uPx, f / sw, f / sh);
        }
        quad.position(0);
        GLES20.glEnableVertexAttribArray(p.aPos);
        GLES20.glVertexAttribPointer(p.aPos, 4, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(p.aPos);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    // ---- setup ----

    private void ensureBuilt() {
        if (built) return;
        built = true;
        copy = build(COPY);
        light = build(LIGHT);
        dogX = build(DOG_X);
        dogY = build(DOG_Y);
        dogApply = build(DOG_APPLY);
        darkQ1 = build(DARK_Q1);
        darkQ2 = build(DARK_Q2);
        darkApply = build(DARK_APPLY);
        if (copy == null) {
            // Even the plain copy failed: nothing can be drawn, let the player drop the effect.
            state.failed = true;
            throw new IllegalStateException("copy shader failed");
        }
        if (light == null || dogX == null || dogY == null || dogApply == null
                || darkQ1 == null || darkQ2 == null || darkApply == null) {
            state.failed = true;
        }
    }

    private void ensureTargets(boolean withDarken) {
        if (a == null) {
            a = target(w, h);
            b = target(w, h);
        }
        if (withDarken && q1 == null) {
            q1 = target(Math.max(1, w / 4), Math.max(1, h / 4));
            q2 = target(q1.w, q1.h);
        }
    }

    private void freeTargets() {
        for (Target t : new Target[] {a, b, q1, q2}) {
            if (t == null) continue;
            GLES20.glDeleteFramebuffers(1, new int[] {t.fbo}, 0);
            GLES20.glDeleteTextures(1, new int[] {t.tex}, 0);
        }
        a = b = q1 = q2 = null;
    }

    private static Target target(int tw, int th) {
        Target t = new Target();
        t.w = tw;
        t.h = th;
        int[] id = new int[1];
        GLES20.glGenTextures(1, id, 0);
        t.tex = id[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.tex);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, tw, th, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glGenFramebuffers(1, id, 0);
        t.fbo = id[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, t.fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, t.tex, 0);
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("framebuffer incomplete");
        }
        return t;
    }

    /** Compiles and links one program; null when this GPU refuses it. */
    private static Prog build(String frag) {
        int v = compile(GLES20.GL_VERTEX_SHADER, VERT);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, frag);
        if (v == 0 || f == 0) {
            if (v != 0) GLES20.glDeleteShader(v);
            if (f != 0) GLES20.glDeleteShader(f);
            return null;
        }
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            GLES20.glDeleteProgram(p);
            return null;
        }
        Prog r = new Prog();
        r.id = p;
        r.aPos = GLES20.glGetAttribLocation(p, "a_pos");
        r.uA = GLES20.glGetUniformLocation(p, "u_a");
        r.uB = GLES20.glGetUniformLocation(p, "u_b");
        r.uPx = GLES20.glGetUniformLocation(p, "u_px");
        return r;
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            GLES20.glDeleteShader(s);
            return 0;
        }
        return s;
    }
}
