package com.sagoma.planimetria.ui

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.sagoma.planimetria.geometry.Mat4
import com.sagoma.planimetria.geometry.Scene3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10

/**
 * Disegna la [Scene3D] con OpenGL ES 2: prima le superfici opache (con la luce), poi i vetri
 * trasparenti. Scena e telecamera arrivano dal thread dell'interfaccia; la GPU le legge al prossimo frame.
 */
class PlanRenderer3D : GLSurfaceView.Renderer {

    /** Scena da caricare sulla GPU al prossimo frame (null = invariata). */
    @Volatile private var pendingScene: Scene3D? = null
    /** Vista e proiezione della telecamera, calcolate dall'interfaccia. */
    @Volatile var view: FloatArray = Mat4.lookAt(com.sagoma.planimetria.geometry.Vec3(0.0, 1000.0, 1000.0), com.sagoma.planimetria.geometry.Vec3.Zero, com.sagoma.planimetria.geometry.Vec3.Up)
    @Volatile var projection: (Float) -> FloatArray = { aspect -> Mat4.perspective(60.0, aspect.toDouble(), 5.0, 100_000.0) }

    private var program = 0
    private var aPos = 0
    private var aNormal = 0
    private var aColor = 0
    private var uMvp = 0
    private val buffers = IntArray(2)
    private var opaqueCount = 0
    private var transparentCount = 0
    private var aspect = 1f

    fun setScene(scene: Scene3D) {
        pendingScene = scene
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = link(VERTEX, FRAGMENT)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        GLES20.glGenBuffers(2, buffers, 0)
        GLES20.glClearColor(0.93f, 0.95f, 0.97f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)
        // Il contesto è nuovo (anche dopo una pausa): la scena va ricaricata.
        opaqueCount = 0
        transparentCount = 0
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingScene?.let { upload(it); pendingScene = null }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, Mat4.multiply(projection(aspect), view), 0)

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        draw(buffers[0], opaqueCount)
        // Vetri: trasparenti, visibili dai due lati, senza nascondere ciò che sta dietro.
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        draw(buffers[1], transparentCount)
        GLES20.glDepthMask(true)
    }

    private fun upload(scene: Scene3D) {
        fun put(buffer: Int, data: FloatArray): Int {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
            val bb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(data)
            bb.position(0)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, bb, GLES20.GL_STATIC_DRAW)
            return data.size / Scene3D.FLOATS_PER_VERTEX
        }
        opaqueCount = put(buffers[0], scene.opaque)
        transparentCount = put(buffers[1], scene.transparent)
    }

    private fun draw(buffer: Int, count: Int) {
        if (count == 0) return
        val stride = Scene3D.FLOATS_PER_VERTEX * 4
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, 24)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    /** Antialiasing 4× se il dispositivo lo supporta, altrimenti la configurazione normale. */
    class ConfigChooser : GLSurfaceView.EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            fun pick(samples: Int): EGLConfig? {
                val attrs = intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                    EGL10.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0, EGL10.EGL_SAMPLES, samples,
                    EGL10.EGL_NONE,
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val num = IntArray(1)
                return if (egl.eglChooseConfig(display, attrs, configs, 1, num) && num[0] > 0) configs[0] else null
            }
            return pick(4) ?: pick(0) ?: error("Nessuna configurazione OpenGL disponibile")
        }
    }

    private companion object {
        const val VERTEX = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            varying vec4 vColor;
            varying vec3 vNormal;
            varying float vHeight;
            void main() {
                vColor = aColor;
                vNormal = aNormal;
                vHeight = aPos.y;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """
        // Luce principale dall'alto di lato più una luce di riempimento: le facce dei muri si distinguono.
        const val FRAGMENT = """
            precision mediump float;
            varying vec4 vColor;
            varying vec3 vNormal;
            varying float vHeight;
            void main() {
                float len = length(vNormal);
                if (len < 0.1) { gl_FragColor = vColor; return; }
                vec3 n = vNormal / len;
                float key = max(dot(n, normalize(vec3(0.45, 0.85, 0.30))), 0.0);
                float fill = max(dot(n, normalize(vec3(-0.5, 0.3, -0.6))), 0.0);
                float light = min(1.0, 0.76 + 0.26 * key + 0.10 * fill);
                // Sulle pareti un poco più scuro in basso: angoli e spigoli si leggono meglio.
                float wallShade = abs(n.y) < 0.5 ? mix(0.86, 1.0, clamp(vHeight / 240.0, 0.0, 1.0)) : 1.0;
                gl_FragColor = vec4(vColor.rgb * light * wallShade, vColor.a);
            }
        """
    }
}
