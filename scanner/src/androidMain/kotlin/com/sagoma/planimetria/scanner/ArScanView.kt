package com.sagoma.planimetria.scanner

import android.app.Activity
import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.sagoma.planimetria.geometry.Triangulation
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.scan.ArPoint
import com.sagoma.planimetria.scan.ScanDraft
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

/**
 * Vista della scansione: fotocamera, pavimento rilevato (piani orizzontali di ARCore) e perimetro toccato, disegnati con
 * OpenGL ES 2. Non decide niente sulla stanza: gli angoli toccati arrivano a [listener] come punti AR in metri
 * ([ArPoint]) e la bozza ([draft]) la possiede chi usa la vista, che qui viene solo disegnata.
 *
 * Uso: crearla, impostare [listener] e [draft], chiamare [start] quando l'attività riprende e [stop] quando va in pausa.
 * I metodi di [Listener] sono chiamati sul thread principale.
 */
class ArScanView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {

    /** Stato della scansione, per i messaggi a schermo. */
    data class Status(
        /** La telecamera sta seguendo l'ambiente (senza, i punti toccati non sarebbero affidabili). */
        val tracking: Boolean = false,
        /** Perché il tracciamento si è perso, in italiano (null se non si sa o va bene). */
        val trackingHint: String? = null,
        /** C'è almeno un pezzo di pavimento rilevato abbastanza grande. */
        val floorDetected: Boolean = false,
    )

    /** Posizione sullo schermo (pixel della vista). */
    data class ScreenPoint(val x: Float, val y: Float)

    interface Listener {
        fun onStatus(status: Status)

        /** Angolo toccato sul pavimento. */
        fun onFloorPoint(point: ArPoint)

        /** Tocco non valido (non sul pavimento rilevato, tracciamento perso…), con il motivo da mostrare. */
        fun onTapRejected(reason: String)

        /**
         * Dove cadono sullo schermo gli angoli e i punti medi dei lati della bozza (stesso ordine di [ScanDraft.points] e
         * [ScanDraft.edgeLengthsCm]); null se il punto è dietro la telecamera. Per scrivere le distanze sopra la scena.
         */
        fun onOverlay(corners: List<ScreenPoint?>, edgeMidpoints: List<ScreenPoint?>)
    }

    /** Come è andato [start]. */
    sealed interface StartResult {
        data object Started : StartResult

        /** Si sta installando o aggiornando ARCore: [start] va richiamato al ritorno nell'app. */
        data object InstallRequested : StartResult

        data class Failed(val message: String) : StartResult
    }

    @Volatile
    var listener: Listener? = null

    /** Perimetro da disegnare; si può cambiare da qualsiasi thread. */
    @Volatile
    var draft: ScanDraft = ScanDraft()

    @Volatile
    private var session: Session? = null
    private var installRequested = false
    @Volatile
    private var cameraTextureAttached = false

    private val taps = ConcurrentLinkedQueue<FloatArray>()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    // Disegno.
    private var cameraTexture = 0
    private var backgroundProgram = 0
    private var solidProgram = 0
    private var viewportChanged = true
    private var viewportWidth = 1
    private var viewportHeight = 1
    private val quadCoords = floatBuffer(floatArrayOf(-1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f))
    private val quadTexCoords = floatBuffer(FloatArray(8))
    private var scratch: FloatBuffer = floatBuffer(FloatArray(4096))
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private var lastStatus: Status? = null

    init {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
        setWillNotDraw(false)
        keepScreenOn = true
    }

    // ---------- Sessione ----------

    /**
     * Crea (la prima volta) e avvia la sessione AR, poi la vista. Da chiamare quando l'attività è in primo piano e il permesso
     * della fotocamera è già stato concesso.
     */
    fun start(activity: Activity): StartResult {
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return StartResult.InstallRequested
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                }
                val s = Session(activity)
                val config = Config(s).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    depthMode = Config.DepthMode.DISABLED
                }
                s.configure(config)
                session = s
                cameraTextureAttached = false
            } catch (e: UnavailableUserDeclinedInstallationException) {
                return StartResult.Failed("Senza ARCore non posso usare la realtà aumentata. Riprova e accetta l'installazione.")
            } catch (e: UnavailableDeviceNotCompatibleException) {
                return StartResult.Failed("Questo telefono non è compatibile con la realtà aumentata (ARCore).")
            } catch (e: UnavailableArcoreNotInstalledException) {
                return StartResult.Failed("ARCore (Servizi Google Play per la RA) non è installato.")
            } catch (e: UnavailableApkTooOldException) {
                return StartResult.Failed("ARCore è troppo vecchio: aggiorna \"Servizi Google Play per la RA\" dal Play Store.")
            } catch (e: UnavailableSdkTooOldException) {
                return StartResult.Failed("Questa versione di Sagoma è troppo vecchia per ARCore: aggiorna l'app.")
            } catch (e: Exception) {
                Log.e(TAG, "Sessione AR non creata", e)
                return StartResult.Failed("Non riesco ad avviare la realtà aumentata (${e.javaClass.simpleName}).")
            }
        }
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            session = null
            return StartResult.Failed("La fotocamera è in uso da un'altra app: chiudila e riprova.")
        } catch (e: Exception) {
            Log.e(TAG, "Sessione AR non ripresa", e)
            session = null
            return StartResult.Failed("Non riesco ad avviare la realtà aumentata (${e.javaClass.simpleName}).")
        }
        onResume()
        return StartResult.Started
    }

    /** Ferma vista e sessione (prima la vista, così il thread di disegno non usa una sessione in pausa). */
    fun stop() {
        onPause()
        session?.pause()
    }

    /** Libera la sessione: dopo, la vista non si usa più. */
    fun release() {
        session?.close()
        session = null
    }

    // ---------- Tocco ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; downTime = event.eventTime }
            MotionEvent.ACTION_UP -> {
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                val still = abs(event.x - downX) <= slop && abs(event.y - downY) <= slop
                if (still && event.eventTime - downTime < 600) taps.offer(floatArrayOf(event.x, event.y))
            }
        }
        return true
    }

    // ---------- Disegno ----------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        cameraTexture = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        backgroundProgram = link(BACKGROUND_VERTEX, BACKGROUND_FRAGMENT)
        solidProgram = link(SOLID_VERTEX, SOLID_FRAGMENT)
        cameraTextureAttached = false
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        try {
            if (!cameraTextureAttached) {
                s.setCameraTextureName(cameraTexture)
                cameraTextureAttached = true
            }
            if (viewportChanged) {
                s.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
                viewportChanged = false
            }
            val frame = s.update()
            val camera = frame.camera
            drawBackground(frame)
            val tracking = camera.trackingState == TrackingState.TRACKING
            val planes = if (tracking) floorPlanes(s) else emptyList()
            val floorY = planes.minOfOrNull { it.centerPose.ty() }
            val status = Status(
                tracking = tracking,
                trackingHint = if (tracking) null else hint(camera),
                floorDetected = planes.any { it.extentX * it.extentZ >= MIN_FLOOR_AREA_M2 },
            )
            if (status != lastStatus) {
                lastStatus = status
                post { listener?.onStatus(status) }
            }
            handleTaps(frame, tracking, floorY)
            if (!tracking) return
            camera.getProjectionMatrix(projectionMatrix, 0, NEAR, FAR)
            camera.getViewMatrix(viewMatrix, 0)
            Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            for (p in planes) drawPlane(p)
            drawDraft(draft, floorY)
            publishOverlay(draft)
        } catch (e: CameraNotAvailableException) {
            Log.w(TAG, "Fotocamera non disponibile", e)
        } catch (e: Throwable) {
            Log.e(TAG, "Errore nel disegno della scansione", e)
        }
    }

    private fun displayRotation(): Int {
        @Suppress("DEPRECATION")
        return (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }

    private fun drawBackground(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            quadCoords.rewind()
            quadTexCoords.rewind()
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED, quadTexCoords,
            )
        }
        if (frame.timestamp == 0L) return // ancora nessuna immagine
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(backgroundProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(backgroundProgram, "u_Texture"), 0)
        val pos = GLES20.glGetAttribLocation(backgroundProgram, "a_Position")
        val tex = GLES20.glGetAttribLocation(backgroundProgram, "a_TexCoord")
        quadCoords.rewind()
        quadTexCoords.rewind()
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
        GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glEnableVertexAttribArray(tex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)
        GLES20.glDisableVertexAttribArray(tex)
        GLES20.glDepthMask(true)
    }

    /** Piani orizzontali rivolti verso l'alto, seguiti, non assorbiti da un altro: candidati a pavimento (o tavoli, ripiani). */
    private fun floorPlanes(s: Session): List<Plane> = s.getAllTrackables(Plane::class.java).filter {
        it.trackingState == TrackingState.TRACKING && it.subsumedBy == null && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING
    }

    private fun hint(camera: Camera): String? = when (camera.trackingFailureReason) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "C'è poca luce: accendi le luci della stanza."
        TrackingFailureReason.EXCESSIVE_MOTION -> "Muovi il telefono più lentamente."
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Inquadra superfici con più dettagli (non solo pareti bianche)."
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Fotocamera non disponibile."
        else -> null
    }

    // ---------- Tocchi sul pavimento ----------

    private fun handleTaps(frame: Frame, tracking: Boolean, floorY: Float?) {
        while (true) {
            val tap = taps.poll() ?: return
            if (!tracking) { reject("Aspetta che la fotocamera si orienti, poi tocca di nuovo."); continue }
            val hit = frame.hitTest(tap[0], tap[1]).firstOrNull {
                val t = it.trackable
                t is Plane && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING && t.isPoseInPolygon(it.hitPose)
            }
            if (hit == null) { reject("Tocca il pavimento già rilevato (zona colorata)."); continue }
            val pose = hit.hitPose
            // Un tavolo o un ripiano è un piano orizzontale come il pavimento: si accetta solo ciò che sta alla quota del pavimento.
            if (floorY != null && pose.ty() > floorY + MAX_ABOVE_FLOOR_M) { reject("Quel punto non è sul pavimento: tocca a terra."); continue }
            val p = ArPoint(pose.tx().toDouble(), pose.ty().toDouble(), pose.tz().toDouble())
            post { listener?.onFloorPoint(p) }
        }
    }

    private fun reject(reason: String) {
        post { listener?.onTapRejected(reason) }
    }

    // ---------- Disegno di piani e perimetro ----------

    private fun drawPlane(plane: Plane) {
        val poly = plane.polygon
        val n = poly.limit() / 2
        if (n < 3) return
        val pose = plane.centerPose
        val c = pose.translation
        val v = FloatArray(n * 9)
        // Vertici del poligono: x, z nel sistema del piano → mondo.
        val pts = Array(n) { i ->
            FloatArray(3).also { pose.transformPoint(floatArrayOf(poly.get(2 * i), 0f, poly.get(2 * i + 1)), 0, it, 0) }
        }
        var k = 0
        for (i in 0 until n) {
            val a = pts[i]
            val b = pts[(i + 1) % n]
            v[k++] = c[0]; v[k++] = c[1] + LIFT; v[k++] = c[2]
            v[k++] = a[0]; v[k++] = a[1] + LIFT; v[k++] = a[2]
            v[k++] = b[0]; v[k++] = b[1] + LIFT; v[k++] = b[2]
        }
        drawTriangles(v, k / 3, PLANE_COLOR)
    }

    private fun drawDraft(d: ScanDraft, floorY: Float?) {
        val pts = d.points
        if (pts.isEmpty()) return
        val y = (floorY ?: pts.first().y.toFloat()) + 2 * LIFT
        val fill = ArrayList<Float>()
        val lines = ArrayList<Float>()
        val marks = ArrayList<Float>()
        if (d.closed && pts.size >= 3) {
            val flat = pts.map { Vec2(it.x, it.z) }
            for ((a, b, c) in Triangulation.triangulate(flat)) {
                for (i in intArrayOf(a, b, c)) { fill.add(pts[i].x.toFloat()); fill.add(y); fill.add(pts[i].z.toFloat()) }
            }
        }
        val n = pts.size
        val edges = if (d.closed) n else n - 1
        for (i in 0 until edges) {
            val a = pts[i]
            val b = pts[(i + 1) % n]
            addStrip(lines, a.x.toFloat(), a.z.toFloat(), b.x.toFloat(), b.z.toFloat(), y, LINE_HALF_WIDTH_M)
        }
        for (p in pts) addSquare(marks, p.x.toFloat(), p.z.toFloat(), y + LIFT, MARK_HALF_SIZE_M)
        if (fill.isNotEmpty()) drawTriangles(fill.toFloatArray(), fill.size / 3, FILL_COLOR)
        if (lines.isNotEmpty()) drawTriangles(lines.toFloatArray(), lines.size / 3, LINE_COLOR)
        drawTriangles(marks.toFloatArray(), marks.size / 3, MARK_COLOR)
    }

    /** Striscia sottile sul pavimento tra due punti (x, z), larga 2 × `half` metri. */
    private fun addStrip(out: MutableList<Float>, x0: Float, z0: Float, x1: Float, z1: Float, y: Float, half: Float) {
        val dx = x1 - x0
        val dz = z1 - z0
        val len = kotlin.math.sqrt(dx * dx + dz * dz)
        if (len < 1e-4f) return
        val nx = -dz / len * half
        val nz = dx / len * half
        val q = floatArrayOf(x0 + nx, z0 + nz, x0 - nx, z0 - nz, x1 - nx, z1 - nz, x1 + nx, z1 + nz)
        for (i in intArrayOf(0, 1, 2, 0, 2, 3)) { out.add(q[2 * i]); out.add(y); out.add(q[2 * i + 1]) }
    }

    private fun addSquare(out: MutableList<Float>, x: Float, z: Float, y: Float, half: Float) {
        val q = floatArrayOf(x - half, z - half, x + half, z - half, x + half, z + half, x - half, z + half)
        for (i in intArrayOf(0, 1, 2, 0, 2, 3)) { out.add(q[2 * i]); out.add(y); out.add(q[2 * i + 1]) }
    }

    private fun drawTriangles(vertices: FloatArray, count: Int, color: FloatArray) {
        if (count <= 0) return
        if (scratch.capacity() < vertices.size) scratch = floatBuffer(FloatArray(vertices.size * 2))
        scratch.clear()
        scratch.put(vertices, 0, count * 3)
        scratch.rewind()
        GLES20.glUseProgram(solidProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(solidProgram, "u_Mvp"), 1, false, viewProjection, 0)
        GLES20.glUniform4fv(GLES20.glGetUniformLocation(solidProgram, "u_Color"), 1, color, 0)
        val pos = GLES20.glGetAttribLocation(solidProgram, "a_Position")
        GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 0, scratch)
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(pos)
    }

    /** Posizione sullo schermo (pixel) di un punto del mondo, o null se sta dietro la telecamera. */
    private fun project(x: Float, y: Float, z: Float): ScreenPoint? {
        val m = viewProjection
        val cw = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (cw <= 1e-4f) return null
        val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
        return ScreenPoint((cx / cw + 1f) / 2f * viewportWidth, (1f - cy / cw) / 2f * viewportHeight)
    }

    private fun publishOverlay(d: ScanDraft) {
        val pts = d.points
        if (pts.isEmpty()) { post { listener?.onOverlay(emptyList(), emptyList()) }; return }
        val corners = pts.map { project(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }
        val n = pts.size
        val edges = if (d.closed) n else n - 1
        val mids = (0 until edges).map {
            val a = pts[it]
            val b = pts[(it + 1) % n]
            project(((a.x + b.x) / 2).toFloat(), ((a.y + b.y) / 2).toFloat(), ((a.z + b.z) / 2).toFloat())
        }
        post { listener?.onOverlay(corners, mids) }
    }

    // ---------- OpenGL ----------

    private fun link(vertex: String, fragment: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "Programma OpenGL non collegato: ${GLES20.glGetProgramInfoLog(program)}" }
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "Shader OpenGL non compilato: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); rewind() }

    private companion object {
        const val TAG = "ArScanView"
        const val NEAR = 0.05f
        const val FAR = 100f
        /** Un piano è "pavimento" se è abbastanza esteso (m²). */
        const val MIN_FLOOR_AREA_M2 = 0.4f
        /** Un punto toccato più in alto del pavimento di tanto (m) è su un mobile, non a terra. */
        const val MAX_ABOVE_FLOOR_M = 0.12f
        /** Sollevamento dei disegni dal pavimento (m), per non farli sparire dentro il piano. */
        const val LIFT = 0.004f
        const val LINE_HALF_WIDTH_M = 0.012f
        const val MARK_HALF_SIZE_M = 0.035f
        val PLANE_COLOR = floatArrayOf(0.10f, 0.75f, 0.45f, 0.28f)
        val FILL_COLOR = floatArrayOf(0.25f, 0.55f, 1.00f, 0.25f)
        val LINE_COLOR = floatArrayOf(1.00f, 1.00f, 1.00f, 0.95f)
        val MARK_COLOR = floatArrayOf(1.00f, 0.65f, 0.05f, 1.00f)

        const val BACKGROUND_VERTEX = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """
        const val BACKGROUND_FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """
        const val SOLID_VERTEX = """
            uniform mat4 u_Mvp;
            attribute vec3 a_Position;
            void main() {
                gl_Position = u_Mvp * vec4(a_Position, 1.0);
            }
        """
        const val SOLID_FRAGMENT = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """
    }
}
