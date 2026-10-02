package com.sagoma.planimetria.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.sagoma.planimetria.geometry.Mat4
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.geometry.toRadians
import com.sagoma.planimetria.ui.Daylight
import com.sagoma.planimetria.ui.LitShaders
import com.sagoma.planimetria.ui.PbrShaders
import com.sagoma.planimetria.ui.SceneRenderer
import com.sagoma.planimetria.ui.SimpleShaders
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Vista 3D sul computer, fotorealistica: OpenGL (LWJGL) disegna fuori schermo con materiali fisici (PBR),
 * materiali fotografici, modelli 3D degli arredi, sole con ombre morbide secondo l'ora, cielo, luci della
 * casa e luce dalle finestre; l'immagine passa a Compose come una normale immagine (così pulsanti e gesti
 * della vista 3D stanno sopra). Si ridisegna solo quando cambiano scena, telecamera, ora o misure.
 * `readAsset` legge i file di arredi e materiali (null nella versione senza catalogo).
 */
class DesktopGlSceneRenderer(private val readAsset: (String) -> ByteArray? = { null }) : SceneRenderer {
    /** Tutto l'OpenGL gira su questo thread (il contesto è legato a lui). */
    private val gl = Executors.newSingleThreadExecutor { Thread(it, "Sagoma 3D").apply { isDaemon = true } }
    private val scheduled = AtomicBoolean(false)
    @Volatile private var pendingScene: Scene3D? = null
    @Volatile private var scene: Scene3D? = null
    @Volatile private var size = IntSize.Zero
    @Volatile private var released = false
    @Volatile private var hour = 11f
    @Volatile private var lamps = "auto"
    @Volatile private var walking = false
    @Volatile private var eye = Vec3.Zero
    private val camera = SimpleShaders.CameraState()
    /** Occlusione ambientale (spegnibile per le prove di confronto). */
    @Volatile internal var ambientOcclusion = true

    private var image by mutableStateOf<ImageBitmap?>(null)
    private var skyColor by mutableStateOf(Color(0.80f, 0.88f, 0.96f))

    // Stato OpenGL (solo sul thread "Sagoma 3D").
    private var context: WglContext? = null
    private var program = 0
    private var depthProgram = 0
    private lateinit var res: GlResources
    private var opaqueVbo = 0
    private var opaqueCount = 0
    private var transparentVbo = 0
    private var transparentCount = 0
    /** Superfici con materiale fotografico: materiale → (buffer, vertici). */
    private val textured = HashMap<String, Pair<Int, Int>>()
    private var fboMsaa = 0
    private var fboResolve = 0
    private var colorMsaa = 0
    private var depthMsaa = 0
    private var colorResolve = 0
    private var fboSize = IntSize.Zero
    private var pixels: ByteBuffer? = null
    private var shadowFbo = 0
    private var shadowTex = 0
    private var depthFbo = 0
    private var depthTex = 0
    private var aoProgram = 0
    private var blurProgram = 0
    private var aoRawFbo = 0
    private var aoRawTex = 0
    private var aoDepthRb = 0
    private var aoFbo = 0
    private var aoTex = 0
    private var screenVbo = 0
    private var whiteTex = 0
    /** Soffitti (anche non disegnati): mappa vista dall'alto per sapere cosa è dentro casa, e ombre del sole. */
    private var roofVbo = 0
    private var roofCount = 0
    private var roofFbo = 0
    private var roofTex = 0

    init {
        gl.execute {
            // Contesto OpenGL con le funzioni di Windows (niente glfw.dll, che Smart App Control può bloccare).
            context = WglContext()
            res = GlResources(readAsset)
            program = link(PbrShaders.vertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.fragment(SimpleShaders.DESKTOP_HEADER))
            depthProgram = link(PbrShaders.depthVertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.depthFragment(SimpleShaders.DESKTOP_HEADER))
            aoProgram = link(PbrShaders.vertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.aoFragment(SimpleShaders.DESKTOP_HEADER))
            blurProgram = link(PbrShaders.screenVertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.blurFragment(SimpleShaders.DESKTOP_HEADER))
            screenVbo = glGenBuffers()
            glBindBuffer(GL_ARRAY_BUFFER, screenVbo)
            glBufferData(GL_ARRAY_BUFFER, floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f), GL_STATIC_DRAW)
            opaqueVbo = glGenBuffers()
            transparentVbo = glGenBuffers()
            createShadowMap()
            roofVbo = glGenBuffers()
            roofTex = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, roofTex)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, ROOF_SIZE, ROOF_SIZE, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0L)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            roofFbo = glGenFramebuffers()
            glBindFramebuffer(GL_FRAMEBUFFER, roofFbo)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, roofTex, 0)
            glDrawBuffer(GL_NONE)
            glReadBuffer(GL_NONE)
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            whiteTex = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, whiteTex)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(4).put(byteArrayOf(-1, -1, -1, -1)).flip())
        }
    }

    /** Mappa delle ombre del sole: la profondità della scena vista dal sole. */
    private fun createShadowMap() {
        shadowTex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, shadowTex)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SHADOW_SIZE, SHADOW_SIZE, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0L)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        shadowFbo = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowTex, 0)
        glDrawBuffer(GL_NONE)
        glReadBuffer(GL_NONE)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    /** Chiede un nuovo disegno (più richieste ravvicinate diventano un solo disegno). */
    private fun request() {
        if (released || size.width <= 0 || size.height <= 0) return
        if (scheduled.compareAndSet(false, true)) gl.execute {
            scheduled.set(false)
            runCatching { renderFrame() }.onFailure { it.printStackTrace() }
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Disegno

    private fun renderFrame() {
        if (released || context?.isOpen != true) return
        val s = size
        if (s.width <= 0 || s.height <= 0) return
        ensureFramebuffer(s)
        pendingScene?.let { upload(it); pendingScene = null }
        val d = Daylight(hour, lamps, walking)
        val lightMvp = sunMatrix(d)
        roofMvp = roofMatrix()

        // 0. Soffitti visti dall'alto: ciò che sta sotto è dentro casa (anche se il tetto non si disegna).
        glBindFramebuffer(GL_FRAMEBUFFER, roofFbo)
        glViewport(0, 0, ROOF_SIZE, ROOF_SIZE)
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDisable(GL_BLEND)
        glDisable(GL_CULL_FACE)
        glClear(GL_DEPTH_BUFFER_BIT)
        glUseProgram(depthProgram)
        val modelLoc = glGetUniformLocation(depthProgram, "uModel")
        glUniformMatrix4fv(modelLoc, false, IDENTITY)
        glUniformMatrix4fv(glGetUniformLocation(depthProgram, "uLightMvp"), false, roofMvp)
        drawArrays(depthProgram, roofVbo, roofCount, Format.SCENE)

        // 1. Ombre: profondità dal sole (solo il pieno: il sole passa dai vetri e dalle parti trasparenti).
        // Anche i soffitti fanno ombra: dentro casa il sole entra solo da finestre e porte.
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo)
        glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE)
        glClear(GL_DEPTH_BUFFER_BIT)
        glUniformMatrix4fv(glGetUniformLocation(depthProgram, "uLightMvp"), false, lightMvp)
        drawArrays(depthProgram, roofVbo, roofCount, Format.SCENE)
        drawArrays(depthProgram, opaqueVbo, opaqueCount, Format.SCENE)
        for ((vbo, n) in textured.values) drawArrays(depthProgram, vbo, n, Format.TEXTURED)
        forEachFurniturePart(opaqueOnly = true) { part, m ->
            glUniformMatrix4fv(modelLoc, false, m)
            drawElements(depthProgram, part)
        }

        // Y capovolta: OpenGL legge le righe dal basso, l'immagine le vuole dall'alto.
        val viewProj = camera.mvp(s.width.toFloat() / s.height)
        for (i in 1 until 16 step 4) viewProj[i] = -viewProj[i]

        // 2. Profondità vista dalla telecamera, per l'occlusione ambientale (angoli, sotto i mobili).
        glBindFramebuffer(GL_FRAMEBUFFER, depthFbo)
        glViewport(0, 0, s.width, s.height)
        glClear(GL_DEPTH_BUFFER_BIT)
        glUniformMatrix4fv(glGetUniformLocation(depthProgram, "uLightMvp"), false, viewProj)
        glUniformMatrix4fv(modelLoc, false, IDENTITY)
        drawArrays(depthProgram, opaqueVbo, opaqueCount, Format.SCENE)
        forEachFurniturePart(opaqueOnly = true) { part, m ->
            glUniformMatrix4fv(modelLoc, false, m)
            drawElements(depthProgram, part)
        }

        // 3. Occlusione ambientale: calcolata sulla scena, poi sfumata rispettando i bordi.
        if (ambientOcclusion) renderAmbientOcclusion(viewProj, s)

        // 4. Scena vista dalla telecamera.
        glBindFramebuffer(GL_FRAMEBUFFER, fboMsaa)
        glViewport(0, 0, s.width, s.height)
        val sky = d.sky
        glClearColor(sky[0], sky[1], sky[2], 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glUseProgram(program)
        // L'immagine capovolta inverte il verso dei triangoli: "davanti" diventa orario.
        glFrontFace(GL_CW)
        setLighting(d, viewProj, lightMvp)
        glActiveTexture(GL_TEXTURE4)
        glBindTexture(GL_TEXTURE_2D, shadowTex)
        glActiveTexture(GL_TEXTURE6)
        glBindTexture(GL_TEXTURE_2D, aoTex)
        glUniform1i(u("uAoTex"), 6)
        glUniform2f(u("uViewport"), s.width.toFloat(), s.height.toFloat())
        glUniform1f(u("uUseAo"), if (ambientOcclusion) 1f else 0f)

        // Pieno: scena con i colori dei vertici, superfici fotografiche, arredi.
        setMaterial(useVertexColor = true, roughness = 0.85f, metallic = 0f)
        glUniformMatrix4fv(u("uModel"), false, IDENTITY)
        drawArrays(program, opaqueVbo, opaqueCount, Format.SCENE)
        for ((id, buf) in textured) {
            setMaterial(albedo = res.material(id, "color"), normal = res.material(id, "normal"), orm = res.material(id, "orm"), ormHasAo = true, roughness = 1f, metallic = 1f, macro = id == GRASS)
            drawArrays(program, buf.first, buf.second, Format.TEXTURED)
        }
        forEachFurniturePart(opaqueOnly = true) { part, m ->
            setFurnitureMaterial(part)
            glUniformMatrix4fv(u("uModel"), false, m)
            drawElements(program, part)
        }
        // Trasparenti: vetri della scena e parti trasparenti degli arredi, senza nascondere ciò che sta dietro.
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDepthMask(false)
        glUniform1f(u("uUseAo"), 0f)
        setMaterial(useVertexColor = true, roughness = 0.08f, metallic = 0f)
        glUniformMatrix4fv(u("uModel"), false, IDENTITY)
        drawArrays(program, transparentVbo, transparentCount, Format.SCENE)
        forEachFurniturePart(opaqueOnly = false, blendOnly = true) { part, m ->
            setFurnitureMaterial(part)
            glUniformMatrix4fv(u("uModel"), false, m)
            drawElements(program, part)
        }
        glDepthMask(true)
        glDisable(GL_BLEND)
        glFrontFace(GL_CCW)

        // 4. Antialiasing e copia dei pixel per Compose.
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fboMsaa)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fboResolve)
        glBlitFramebuffer(0, 0, s.width, s.height, 0, 0, s.width, s.height, GL_COLOR_BUFFER_BIT, GL_NEAREST)
        glBindFramebuffer(GL_FRAMEBUFFER, fboResolve)
        val buf = pixels!!
        buf.clear()
        glReadPixels(0, 0, s.width, s.height, GL_RGBA, GL_UNSIGNED_BYTE, buf)
        val bytes = ByteArray(s.width * s.height * 4)
        buf.get(bytes)
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(s.width, s.height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
        bitmap.installPixels(bytes)
        bitmap.setImmutable()
        image = bitmap.asComposeImageBitmap()
        skyColor = Color(sky[0], sky[1], sky[2])
    }

    private fun u(name: String) = glGetUniformLocation(program, name)

    /** Occlusione ambientale: la scena disegnata con lo shader SSAO, poi sfumata in [aoTex]. */
    private fun renderAmbientOcclusion(viewProj: FloatArray, s: IntSize) {
        fun ua(name: String) = glGetUniformLocation(aoProgram, name)
        glBindFramebuffer(GL_FRAMEBUFFER, aoRawFbo)
        glViewport(0, 0, s.width, s.height)
        glClearColor(1f, 1f, 1f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glEnable(GL_DEPTH_TEST)
        glFrontFace(GL_CW)
        glUseProgram(aoProgram)
        glUniformMatrix4fv(ua("uViewProj"), false, viewProj)
        glUniformMatrix4fv(ua("uViewProjAo"), false, viewProj)
        glUniformMatrix4fv(ua("uLightMvp"), false, IDENTITY)
        glUniform1f(ua("uNear"), camera.near.toFloat())
        glUniform1f(ua("uFar"), 100_000f)
        glActiveTexture(GL_TEXTURE5)
        glBindTexture(GL_TEXTURE_2D, depthTex)
        glUniform1i(ua("uDepthTex"), 5)
        val modelLoc = ua("uModel")
        val doubleLoc = ua("uDoubleSided")
        glUniformMatrix4fv(modelLoc, false, IDENTITY)
        glUniform1f(doubleLoc, 0f)
        drawArrays(aoProgram, opaqueVbo, opaqueCount, Format.SCENE)
        for ((vbo, n) in textured.values) drawArrays(aoProgram, vbo, n, Format.TEXTURED)
        glUniform1f(doubleLoc, 1f)
        forEachFurniturePart(opaqueOnly = true) { part, m ->
            glUniformMatrix4fv(modelLoc, false, m)
            drawElements(aoProgram, part)
        }
        glFrontFace(GL_CCW)
        // Sfumatura su tutto lo schermo.
        glBindFramebuffer(GL_FRAMEBUFFER, aoFbo)
        glDisable(GL_DEPTH_TEST)
        glUseProgram(blurProgram)
        fun ub(name: String) = glGetUniformLocation(blurProgram, name)
        glActiveTexture(GL_TEXTURE6)
        glBindTexture(GL_TEXTURE_2D, aoRawTex)
        glUniform1i(ub("uAoRaw"), 6)
        glUniform1i(ub("uDepthTex"), 5)
        glUniform2f(ub("uViewport"), s.width.toFloat(), s.height.toFloat())
        glUniform1f(ub("uNear"), camera.near.toFloat())
        glUniform1f(ub("uFar"), 100_000f)
        glBindBuffer(GL_ARRAY_BUFFER, screenVbo)
        val aPos = glGetAttribLocation(blurProgram, "aPos")
        glEnableVertexAttribArray(aPos)
        glVertexAttribPointer(aPos, 2, GL_FLOAT, false, 8, 0L)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        glEnable(GL_DEPTH_TEST)
    }

    /** Texture di colore delle misure della vista (per l'occlusione ambientale), con il suo framebuffer. */
    private fun colorTarget(s: IntSize, withDepth: Boolean): Pair<Int, Int> {
        val t = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, t)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, s.width, s.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        val fb = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fb)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t, 0)
        if (withDepth) {
            val rb = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, rb)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, s.width, s.height)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, rb)
            aoDepthRb = rb
        }
        return fb to t
    }

    private fun toLinear(c: Float) = c.toDouble().pow(2.2).toFloat()

    /** Luce del giorno, cielo, lampade e finestre per lo shader PBR. */
    private fun setLighting(d: Daylight, viewProj: FloatArray, lightMvp: FloatArray) {
        glUniformMatrix4fv(u("uViewProj"), false, viewProj)
        glUniformMatrix4fv(u("uLightMvp"), false, lightMvp)
        glUniform3f(u("uEye"), eye.x.toFloat(), eye.y.toFloat(), eye.z.toFloat())
        glUniform3f(u("uToSun"), d.toSun.x.toFloat(), d.toSun.y.toFloat(), d.toSun.z.toFloat())
        val sunK = 6.0f
        glUniform3f(u("uSun"), d.sun[0] * sunK, d.sun[1] * sunK, d.sun[2] * sunK)
        // Cielo e terreno: luce diffusa. Di notte resta solo un filo di luce lunare: senza lampade accese non
        // si vede quasi niente.
        val skyK = 0.006f + 0.594f * d.day
        // Fuori: cielo e prato (che rimanda luce verde solo in parte: si mescola con il grigio del terreno).
        val sky = d.sky.map { toLinear(it) * skyK / maxOf(toLinear(d.sky.max()), 0.05f) }
        val g = LitShaders.grass
        val ground = floatArrayOf(toLinear(g[0]), toLinear(g[1]), toLinear(g[2])).map { (it * 0.45f + 0.12f) * skyK * 0.45f }
        glUniform3f(u("uSky"), sky[0], sky[1], sky[2])
        glUniform3f(u("uGround"), ground[0], ground[1], ground[2])
        // Dentro casa (sotto i soffitti, anche nella vista dall'alto dove il tetto non si vede): il cielo entra
        // solo dalle finestre, la luce diffusa è quella che rimbalza su pavimento, pareti e soffitto, quasi
        // bianca e un poco calda. Di notte la danno solo le lampade, stanza per stanza.
        val kIn = skyK * 0.55f
        glUniform3f(u("uInSky"), 1.0f * kIn, 0.975f * kIn, 0.94f * kIn)
        glUniform3f(u("uInGround"), 0.95f * kIn * 0.75f, 0.86f * kIn * 0.75f, 0.74f * kIn * 0.75f)
        glActiveTexture(GL_TEXTURE7)
        glBindTexture(GL_TEXTURE_2D, roofTex)
        glUniform1i(u("uRoofMap"), 7)
        glUniformMatrix4fv(u("uRoofMvp"), false, roofMvp)
        glUniform1f(u("uRoofTexel"), 1f / ROOF_SIZE)
        val lights = LitShaders.Lights(scene, d, eye, windowsAlways = true)
        // Di notte l'occhio si abitua un poco: più esposizione solo se c'è qualche lampada accesa (così le
        // stanze illuminate si vedono bene); senza luci resta tutto buio.
        glUniform1f(u("uExposure"), 1.0f + (if (lights.lampCount > 0) 1.4f else 0.3f) * (1f - d.day))
        glUniform1i(u("uAlbedoTex"), 0)
        glUniform1i(u("uNormalTex"), 1)
        glUniform1i(u("uOrmTex"), 2)
        glUniform1i(u("uEmissiveTex"), 3)
        glUniform1i(u("uShadowMap"), 4)
        glUniform1f(u("uShadowTexel"), 1f / SHADOW_SIZE)
        glUniform1i(u("uLampCount"), lights.lampCount)
        glUniform4fv(u("uLamp"), lights.lamps)
        glUniform3fv(u("uLampColor"), lights.lampColors)
        glUniform4fv(u("uLampBox"), lights.lampBoxes)
        glUniform1i(u("uWinCount"), lights.windowCount)
        glUniform4fv(u("uWin"), lights.windows)
        glUniform4fv(u("uWinDir"), lights.windowDirs)
        glUniform3f(u("uWinColor"), d.windowColor[0], d.windowColor[1], d.windowColor[2])
    }

    private fun bindTex(unit: Int, tex: Int) {
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, if (tex != 0) tex else whiteTex)
    }

    private fun setMaterial(
        useVertexColor: Boolean = false, albedo: Int = 0, normal: Int = 0, orm: Int = 0, ormHasAo: Boolean = false, emissive: Int = 0,
        baseColor: FloatArray = ONE4, roughness: Float, metallic: Float, emissiveColor: FloatArray = ZERO3,
        mask: Boolean = false, doubleSided: Boolean = false, macro: Boolean = false,
    ) {
        bindTex(0, albedo); bindTex(1, normal); bindTex(2, orm); bindTex(3, emissive)
        glUniform1f(u("uMacro"), if (macro) 1f else 0f)
        glUniform1f(u("uHasAlbedo"), if (albedo != 0) 1f else 0f)
        glUniform1f(u("uHasNormal"), if (normal != 0) 1f else 0f)
        glUniform1f(u("uHasOrm"), if (orm != 0) 1f else 0f)
        glUniform1f(u("uOrmHasAo"), if (ormHasAo) 1f else 0f)
        glUniform1f(u("uHasEmissiveTex"), if (emissive != 0) 1f else 0f)
        glUniform4f(u("uBaseColor"), baseColor[0], baseColor[1], baseColor[2], baseColor[3])
        glUniform1f(u("uRoughness"), roughness)
        glUniform1f(u("uMetallic"), metallic)
        glUniform3f(u("uEmissive"), emissiveColor[0], emissiveColor[1], emissiveColor[2])
        glUniform1f(u("uMask"), if (mask) 1f else 0f)
        glUniform1f(u("uDoubleSided"), if (doubleSided) 1f else 0f)
        glUniform1f(u("uUseVertexColor"), if (useVertexColor) 1f else 0f)
    }

    private var selectedGlow = false

    private fun setFurnitureMaterial(p: GlResources.GpuPart) {
        val m = p.material
        // Il colore di base di glTF è già lineare. Emissione: quella del modello (spesso nulla) più l'azzurro della selezione.
        val glow = if (selectedGlow) floatArrayOf(0.02f, 0.09f, 0.24f) else ZERO3
        val em = floatArrayOf(m.emissive[0] + glow[0], m.emissive[1] + glow[1], m.emissive[2] + glow[2])
        setMaterial(
            albedo = p.albedo, normal = p.normal, orm = p.orm, ormHasAo = false, emissive = if (m.emissive.any { it > 0f }) p.emissive else 0,
            baseColor = m.baseColor, roughness = m.roughness, metallic = m.metallic, emissiveColor = em, mask = m.mask, doubleSided = true,
        )
    }

    /** Ogni parte di ogni arredo della scena con la sua matrice di posizionamento (in cm). */
    private fun forEachFurniturePart(opaqueOnly: Boolean, blendOnly: Boolean = false, block: (GlResources.GpuPart, FloatArray) -> Unit) {
        val sc = scene ?: return
        val front = glGetInteger(GL_FRONT_FACE)
        for (f in sc.furniture) {
            val model = res.model(f.model) ?: continue
            val m = placement(model, f)
            selectedGlow = f.selected
            // Un arredo specchiato inverte il verso dei triangoli: il davanti va scambiato.
            glFrontFace(if (f.mirrored == (front == GL_CW)) GL_CCW else GL_CW)
            for (p in model.parts) {
                if (opaqueOnly && p.material.blend) continue
                if (blendOnly && !p.material.blend) continue
                block(p, m)
            }
        }
        glFrontFace(front)
        selectedGlow = false
    }

    /**
     * Matrice che porta il modello nell'ingombro dell'arredo (larghezza, profondità, altezza), ruotato e con la
     * base a terra (o all'altezza da terra), come fa il renderer Filament.
     */
    private fun placement(model: GlResources.GpuModel, f: Scene3D.PlacedFurniture): FloatArray {
        val mn = model.min; val mx = model.max
        // Specchiato: la larghezza del modello si ribalta (sinistra ↔ destra guardandolo dal davanti).
        val sx = (f.width / (mx[0] - mn[0]).coerceAtLeast(1e-4f)).toFloat() * (if (f.mirrored) -1f else 1f)
        val sy = (f.height / (mx[1] - mn[1]).coerceAtLeast(1e-4f)).toFloat()
        val sz = (f.depth / (mx[2] - mn[2]).coerceAtLeast(1e-4f)).toFloat()
        val phi = toRadians(-f.rotation)
        val c = cos(phi).toFloat()
        val s = sin(phi).toFloat()
        val t0x = -(mn[0] + mx[0]) / 2
        val t0y = -mn[1]
        val t0z = -(mn[2] + mx[2]) / 2
        return floatArrayOf(
            c * sx, 0f, -s * sx, 0f,
            0f, sy, 0f, 0f,
            s * sz, 0f, c * sz, 0f,
            f.base.x.toFloat() + c * sx * t0x + s * sz * t0z,
            f.base.y.toFloat() + sy * t0y,
            f.base.z.toFloat() - s * sx * t0x + c * sz * t0z,
            1f,
        )
    }

    private enum class Format { SCENE, TEXTURED }

    /** Collega gli attributi: scena = posizione, normale, colore; texture = posizione, normale, uv. */
    private fun attributes(prog: Int, format: Format) {
        val aPos = glGetAttribLocation(prog, "aPos")
        val aNormal = glGetAttribLocation(prog, "aNormal")
        val aColor = glGetAttribLocation(prog, "aColor")
        val aUv = glGetAttribLocation(prog, "aUv")
        val stride = if (format == Format.SCENE) Scene3D.FLOATS_PER_VERTEX * 4 else 8 * 4
        glEnableVertexAttribArray(aPos)
        glVertexAttribPointer(aPos, 3, GL_FLOAT, false, stride, 0L)
        if (aNormal >= 0) { glEnableVertexAttribArray(aNormal); glVertexAttribPointer(aNormal, 3, GL_FLOAT, false, stride, 12L) }
        if (format == Format.SCENE) {
            if (aColor >= 0) { glEnableVertexAttribArray(aColor); glVertexAttribPointer(aColor, 4, GL_FLOAT, false, stride, 24L) }
            if (aUv >= 0) { glDisableVertexAttribArray(aUv); glVertexAttrib2f(aUv, 0f, 0f) }
        } else {
            if (aUv >= 0) { glEnableVertexAttribArray(aUv); glVertexAttribPointer(aUv, 2, GL_FLOAT, false, stride, 24L) }
            if (aColor >= 0) { glDisableVertexAttribArray(aColor); glVertexAttrib4f(aColor, 1f, 1f, 1f, 1f) }
        }
    }

    private fun drawArrays(prog: Int, vbo: Int, count: Int, format: Format) {
        if (count == 0) return
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        attributes(prog, format)
        glDrawArrays(GL_TRIANGLES, 0, count)
    }

    private fun drawElements(prog: Int, p: GlResources.GpuPart) {
        glBindBuffer(GL_ARRAY_BUFFER, p.vbo)
        attributes(prog, Format.TEXTURED)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, p.ibo)
        glDrawElements(GL_TRIANGLES, p.count, GL_UNSIGNED_INT, 0L)
    }

    /** Proiezione ortogonale dal sole che contiene tutta la casa. */
    private fun sunMatrix(d: Daylight): FloatArray {
        val b = scene?.bounds
        val cx = b?.let { (it.minX + it.maxX) / 2 } ?: 0.0
        val cz = b?.let { (it.minY + it.maxY) / 2 } ?: 0.0
        val r = (b?.let { hypot(it.width, it.height) / 2 } ?: 1000.0) + 600.0
        val center = Vec3(cx, 150.0, cz)
        val from = center + d.toSun * (r * 2)
        val up = if (abs(d.toSun.y) > 0.95) Vec3(0.0, 0.0, 1.0) else Vec3.Up
        return Mat4.multiply(ortho(r, 1.0, r * 4), Mat4.lookAt(from, center, up))
    }

    private var roofMvp = IDENTITY

    /** Vista dall'alto, dritta verso il basso, di tutta la casa (per la mappa dei soffitti). */
    private fun roofMatrix(): FloatArray {
        val b = scene?.bounds
        val cx = b?.let { (it.minX + it.maxX) / 2 } ?: 0.0
        val cz = b?.let { (it.minY + it.maxY) / 2 } ?: 0.0
        val r = (b?.let { maxOf(it.width, it.height) / 2 } ?: 1000.0) + 200.0
        val center = Vec3(cx, 0.0, cz)
        return Mat4.multiply(ortho(r, 1.0, ROOF_FAR), Mat4.lookAt(center + Vec3(0.0, ROOF_FAR / 2, 0.0), center, Vec3(0.0, 0.0, -1.0)))
    }

    private fun ortho(r: Double, near: Double, far: Double): FloatArray {
        val m = FloatArray(16)
        m[0] = (1 / r).toFloat()
        m[5] = (1 / r).toFloat()
        m[10] = (-2 / (far - near)).toFloat()
        m[14] = (-(far + near) / (far - near)).toFloat()
        m[15] = 1f
        return m
    }

    /** Framebuffer fuori schermo (4 campioni per l'antialiasing) delle misure della vista. */
    private fun ensureFramebuffer(s: IntSize) {
        if (s == fboSize) return
        if (fboMsaa != 0) {
            glDeleteFramebuffers(fboMsaa); glDeleteFramebuffers(fboResolve)
            glDeleteRenderbuffers(colorMsaa); glDeleteRenderbuffers(depthMsaa); glDeleteRenderbuffers(colorResolve)
        }
        fboMsaa = glGenFramebuffers()
        colorMsaa = glGenRenderbuffers()
        depthMsaa = glGenRenderbuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fboMsaa)
        glBindRenderbuffer(GL_RENDERBUFFER, colorMsaa)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, 4, GL_RGBA8, s.width, s.height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorMsaa)
        glBindRenderbuffer(GL_RENDERBUFFER, depthMsaa)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, 4, GL_DEPTH_COMPONENT24, s.width, s.height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthMsaa)
        fboResolve = glGenFramebuffers()
        colorResolve = glGenRenderbuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fboResolve)
        glBindRenderbuffer(GL_RENDERBUFFER, colorResolve)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, s.width, s.height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorResolve)
        pixels = BufferUtils.createByteBuffer(s.width * s.height * 4)
        // Profondità della vista (per l'occlusione ambientale), senza multicampionamento.
        if (depthFbo != 0) { glDeleteFramebuffers(depthFbo); glDeleteTextures(depthTex) }
        depthTex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, depthTex)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, s.width, s.height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0L)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        depthFbo = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, depthFbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTex, 0)
        glDrawBuffer(GL_NONE)
        glReadBuffer(GL_NONE)
        // Occlusione ambientale: grezza (con la sua profondità) e sfumata.
        if (aoFbo != 0) {
            glDeleteFramebuffers(aoRawFbo); glDeleteFramebuffers(aoFbo)
            glDeleteTextures(aoRawTex); glDeleteTextures(aoTex); glDeleteRenderbuffers(aoDepthRb)
        }
        colorTarget(s, withDepth = true).let { (fb, t) -> aoRawFbo = fb; aoRawTex = t }
        colorTarget(s, withDepth = false).let { (fb, t) -> aoFbo = fb; aoTex = t }
        fboSize = s
    }

    /** Scena sulla GPU: pieno con il prato attorno, trasparenze, superfici fotografiche. */
    private fun upload(s: Scene3D) {
        val opaque = s.opaque + LitShaders.ground(s)
        glBindBuffer(GL_ARRAY_BUFFER, opaqueVbo)
        glBufferData(GL_ARRAY_BUFFER, opaque, GL_STATIC_DRAW)
        opaqueCount = opaque.size / Scene3D.FLOATS_PER_VERTEX
        glBindBuffer(GL_ARRAY_BUFFER, transparentVbo)
        glBufferData(GL_ARRAY_BUFFER, s.transparent, GL_STATIC_DRAW)
        transparentCount = s.transparent.size / Scene3D.FLOATS_PER_VERTEX
        glBindBuffer(GL_ARRAY_BUFFER, roofVbo)
        glBufferData(GL_ARRAY_BUFFER, s.roofs, GL_STATIC_DRAW)
        roofCount = s.roofs.size / Scene3D.FLOATS_PER_VERTEX
        for ((vbo, _) in textured.values) glDeleteBuffers(vbo)
        textured.clear()
        // Prato fotografico attorno alla casa (come su Android), sopra al prato colorato.
        val grass = com.sagoma.planimetria.model.MaterialCatalog.item(GRASS)
        val all = if (grass != null && s.bounds != null) s.textured + (GRASS to groundTextured(s, grass.size)) else s.textured
        for ((id, data) in all) {
            val vbo = glGenBuffers()
            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW)
            textured[id] = vbo to data.size / 8
        }
    }

    /** Prato con coordinate della foto (una ripetizione ogni `size` cm), poco sopra il prato colorato. */
    private fun groundTextured(s: Scene3D, size: Double): FloatArray {
        val plain = LitShaders.ground(s)
        val n = plain.size / Scene3D.FLOATS_PER_VERTEX
        val out = FloatArray(n * 8)
        for (i in 0 until n) {
            val o = i * Scene3D.FLOATS_PER_VERTEX
            val x = plain[o]; val z = plain[o + 2]
            out[i * 8] = x; out[i * 8 + 1] = plain[o + 1] + 0.2f; out[i * 8 + 2] = z
            out[i * 8 + 3] = 0f; out[i * 8 + 4] = 1f; out[i * 8 + 5] = 0f
            out[i * 8 + 6] = (x / size).toFloat(); out[i * 8 + 7] = (z / size).toFloat()
        }
        return out
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val sh = glCreateShader(type)
            glShaderSource(sh, src)
            glCompileShader(sh)
            if (glGetShaderi(sh, GL_COMPILE_STATUS) == GL_FALSE) System.err.println("Shader: " + glGetShaderInfoLog(sh))
            return sh
        }
        val p = glCreateProgram()
        glAttachShader(p, compile(GL_VERTEX_SHADER, vs))
        glAttachShader(p, compile(GL_FRAGMENT_SHADER, fs))
        glLinkProgram(p)
        if (glGetProgrami(p, GL_LINK_STATUS) == GL_FALSE) System.err.println("Programma: " + glGetProgramInfoLog(p))
        return p
    }

    /** Disegna subito con le misure date e restituisce l'immagine (per le prove). */
    internal fun renderNow(width: Int, height: Int): ImageBitmap? {
        size = IntSize(width, height)
        gl.submit { renderFrame() }.get()
        return image
    }

    @Composable
    override fun Surface(modifier: Modifier) {
        Box(modifier.background(skyColor).onSizeChanged { size = it; request() }) {
            image?.let { Image(it, contentDescription = "Vista 3D", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        }
    }

    override fun setScene(scene: Scene3D) {
        this.scene = scene
        pendingScene = scene
        request()
    }

    override fun setCamera(eye: Vec3, center: Vec3, up: Vec3, fovY: Double, near: Double, walking: Boolean) {
        camera.set(eye, center, up, fovY, near)
        this.eye = eye
        this.walking = walking
        request()
    }

    override fun setDaylight(hour: Float, lamps: String) {
        if (hour == this.hour && lamps == this.lamps) return
        this.hour = hour
        this.lamps = lamps
        request()
    }

    override fun release() {
        released = true
        gl.execute {
            context?.close()
            context = null
        }
        gl.shutdown()
    }

    private companion object {
        const val SHADOW_SIZE = 4096
        const val ROOF_SIZE = 2048
        /** Profondità della vista dall'alto dei soffitti (cm): da 50 m sopra fino a 50 m sotto. */
        const val ROOF_FAR = 10_000.0
        /** Materiale del prato (Poly Haven, CC0), lo stesso della versione Android. */
        const val GRASS = "grass005"
        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val ONE4 = floatArrayOf(1f, 1f, 1f, 1f)
        val ZERO3 = floatArrayOf(0f, 0f, 0f)
    }
}
