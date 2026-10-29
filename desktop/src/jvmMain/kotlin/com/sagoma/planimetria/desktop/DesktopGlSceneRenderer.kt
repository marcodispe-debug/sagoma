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
import com.sagoma.planimetria.assets.MaterialResolution
import com.sagoma.planimetria.geometry.Mat4
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.SunAngles
import com.sagoma.planimetria.geometry.SunPath
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
import org.lwjgl.opengl.GL32.GL_SYNC_FLUSH_COMMANDS_BIT
import org.lwjgl.opengl.GL32.GL_SYNC_GPU_COMMANDS_COMPLETE
import org.lwjgl.opengl.GL32.glClientWaitSync
import org.lwjgl.opengl.GL32.glDeleteSync
import org.lwjgl.opengl.GL32.glFenceSync
import org.lwjgl.opengl.GL33.GL_TIME_ELAPSED
import org.lwjgl.opengl.GL33.glGetQueryObjecti64
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Vista 3D sul computer, fotorealistica: OpenGL (LWJGL) disegna fuori schermo con materiali fisici (PBR),
 * materiali fotografici, modelli 3D degli arredi, sole con ombre morbide secondo l'ora, cielo, luci della
 * casa e luce dalle finestre; l'immagine passa a Compose come una normale immagine (così pulsanti e gesti
 * della vista 3D stanno sopra). Si ridisegna solo quando cambiano scena, telecamera, ora o misure.
 * `readAsset` legge i file di arredi e materiali (null nella versione senza catalogo); `materials` è la definizione dei
 * materiali fotografici da usare quando c'è (altrimenti l'1K).
 */
class DesktopGlSceneRenderer(
    private val readAsset: (String) -> ByteArray? = { null },
    private val materials: MaterialResolution = MaterialResolution.K1,
) : SceneRenderer {
    /** Tutto l'OpenGL gira su questo thread (il contesto è legato a lui). */
    private val gl = Executors.newSingleThreadExecutor { Thread(it, "Sagoma 3D").apply { isDaemon = true } }
    /** Un disegno è chiesto e non ancora cominciato ([queued]). */
    private val scheduled = AtomicBoolean(false)
    @Volatile private var pendingScene: Scene3D? = null
    @Volatile private var scene: Scene3D? = null
    @Volatile private var size = IntSize.Zero
    @Volatile private var released = false
    @Volatile private var hour = 11f
    @Volatile private var lamps = "auto"
    @Volatile private var lampBrightness = 1f
    @Volatile private var sunAt: SunAngles = SunPath.simple(11f)
    @Volatile private var walking = false
    @Volatile private var eye = Vec3.Zero
    private val camera = SimpleShaders.CameraState()
    /** Occlusione ambientale (spegnibile per le prove di confronto). */
    @Volatile internal var ambientOcclusion = true

    private var image by mutableStateOf<ImageBitmap?>(null)
    private var skyColor by mutableStateOf(Color(0.80f, 0.88f, 0.96f))

    // Stato OpenGL (solo sul thread "Sagoma 3D").
    private var context: GlContext? = null
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
    private var skyProgram = 0
    private var bloomProgram = 0
    private var finishProgram = 0
    /** Alone delle luci forti: immagine rimpicciolita a un quarto, sfumata due volte (andata e ritorno tra A e B). */
    private var bloomFboA = 0
    private var bloomTexA = 0
    private var bloomFboB = 0
    private var bloomTexB = 0
    private var aoRawFbo = 0
    private var aoRawTex = 0
    private var aoDepthRb = 0
    private var aoFbo = 0
    private var aoTex = 0
    private var aoTexSize = IntSize(1, 1)
    private var screenVbo = 0
    private var whiteTex = 0
    /** Soffitti (anche non disegnati): mappa vista dall'alto per sapere cosa è dentro casa, e ombre del sole. */
    private var roofVbo = 0
    private var roofCount = 0
    private var roofFbo = 0
    private var roofTex = 0
    private val uniforms = HashMap<String, Int>()

    /**
     * Pixel letti senza fermarsi ad aspettare la scheda grafica: copia l'immagine in uno dei due buffer mentre si prepara la successiva
     * (vedi [readPixels]). Per ogni buffer: l'attesa della copia, le misure e il colore del cielo dell'immagine.
     */
    private var asyncRead = false
    private val pbo = IntArray(2)
    private val pboFence = LongArray(2)
    private val pboSize = arrayOfNulls<IntSize>(2)
    private val pboSky = arrayOfNulls<FloatArray>(2)

    /**
     * Muovendosi (un'immagine subito dopo l'altra) l'immagine si fa più piccola quanto serve per restare sotto [FRAME_MS], e la vista la
     * allarga: più immagini al secondo, un po' meno nitide. Appena ci si ferma torna piena e nitida (vedi [sharpen]).
     */
    internal var motionScale = 1f
    /** Foto in corso (solo sul thread "Sagoma 3D"): misure, definizione dei materiali e immagine letta (vedi [renderPhoto]). */
    private var photoSize: IntSize? = null
    private var photoQuality: MaterialResolution? = null
    private var photoResult: Bitmap? = null
    private var lastFrameEnd = 0L
    @Volatile private var frames = 0L
    private val sharpen = Executors.newSingleThreadScheduledExecutor { Thread(it, "Sagoma 3D nitido").apply { isDaemon = true } }

    /** Tempi della scheda grafica per passaggio (SAGOMA_TEMPI_3D=1): medie in ms di [timings], scritte ogni 60 immagini. */
    private val timing = System.getenv("SAGOMA_TEMPI_3D") != null || System.getProperty("sagoma.tempi3d") != null
    private val gpuTimes = LinkedHashMap<String, Double>()
    private var gpuFrames = 0
    private val queries = ArrayList<Pair<String, Int>>()
    private val freeQueries = ArrayList<Int>()

    init {
        gl.execute {
            // Contesto OpenGL con le funzioni di Windows (niente glfw.dll, che Smart App Control può bloccare); su Linux con X11.
            context = GlContext.create()
            res = GlResources(readAsset)
            program = link(PbrShaders.vertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.fragment(SimpleShaders.DESKTOP_HEADER))
            depthProgram = link(PbrShaders.depthVertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.depthFragment(SimpleShaders.DESKTOP_HEADER))
            aoProgram = link(PbrShaders.vertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.aoFragment(SimpleShaders.DESKTOP_HEADER))
            blurProgram = link(PbrShaders.screenVertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.blurFragment(SimpleShaders.DESKTOP_HEADER))
            skyProgram = link(PbrShaders.skyVertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.skyFragment(SimpleShaders.DESKTOP_HEADER))
            bloomProgram = link(PbrShaders.screenVertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.bloomFragment(SimpleShaders.DESKTOP_HEADER))
            finishProgram = link(PbrShaders.screenVertex(SimpleShaders.DESKTOP_HEADER), PbrShaders.finishFragment(SimpleShaders.DESKTOP_HEADER))
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
        if (scheduled.compareAndSet(false, true)) {
            gl.execute {
                scheduled.set(false)
                runCatching { renderFrame() }.onFailure { it.printStackTrace() }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Disegno

    /** Come disegnare un'immagine: da ferma o in movimento lo decide il renderer; le prove possono chiederlo. */
    internal enum class Mode { AUTO, STILL, MOVING }

    private fun renderFrame(mode: Mode = Mode.AUTO) {
        if (released || context?.isOpen != true) return
        val full = photoSize ?: size
        if (full.width <= 0 || full.height <= 0) return
        ensureFramebuffer(full)
        // Scheda grafica senza memoria per un'immagine così grande (succede solo con le foto): niente disegno.
        glBindFramebuffer(GL_FRAMEBUFFER, fboMsaa)
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return
        pendingScene?.let { res.retryMissing(); upload(it); pendingScene = null }
        val start = System.nanoTime()
        val moving = mode == Mode.MOVING || mode == Mode.AUTO && start - lastFrameEnd < MOTION_GAP_NS
        // Si disegna nell'angolo in basso a sinistra delle texture (grandi quanto la vista) e si legge solo quella parte.
        val s = if (moving && motionScale < 1f) {
            IntSize(maxOf(1, (full.width * motionScale).roundToInt()), maxOf(1, (full.height * motionScale).roundToInt()))
        } else full
        val d = Daylight(hour, lamps, walking, sunAt)
        val lightMvp = sunMatrix(d)
        roofMvp = roofMatrix()

        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDisable(GL_BLEND)
        glDisable(GL_CULL_FACE)
        glUseProgram(depthProgram)
        val modelLoc = glGetUniformLocation(depthProgram, "uModel")
        // Mappa dei soffitti e ombre del sole dipendono dalla scena, dal sole e dagli arredi arrivati, non dalla telecamera: si
        // rifanno solo quando cambia uno di questi. Muovendosi (camminata, WASD, mouse) non si ridisegna ogni volta tutta la casa
        // dall'alto e dal sole (4096 × 4096): più immagini al secondo.
        val furnitureReady = scene?.furniture?.count { res.model(it.model) != null } ?: 0
        if (uploads != depthMapsUploads || furnitureReady != depthMapsFurniture ||
            !lightMvp.contentEquals(depthMapsSun) || !roofMvp.contentEquals(depthMapsRoof)
        ) {
            depthMapsUploads = uploads
            depthMapsFurniture = furnitureReady
            depthMapsSun = lightMvp
            depthMapsRoof = roofMvp
            gpu("mappe soffitti e ombre") {
                // 0. Soffitti visti dall'alto: ciò che sta sotto è dentro casa (anche se il tetto non si disegna).
                glBindFramebuffer(GL_FRAMEBUFFER, roofFbo)
                glViewport(0, 0, ROOF_SIZE, ROOF_SIZE)
                glClear(GL_DEPTH_BUFFER_BIT)
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
            }
        }

        // Y capovolta: OpenGL legge le righe dal basso, l'immagine le vuole dall'alto.
        val aspect = full.width.toFloat() / full.height
        val viewProj = camera.mvp(aspect)
        for (i in 1 until 16 step 4) viewProj[i] = -viewProj[i]

        // 2. Profondità vista dalla telecamera, per l'occlusione ambientale (angoli, sotto i mobili).
        gpu("profondità") {
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
        }

        // 3. Occlusione ambientale: calcolata sulla scena, poi sfumata rispettando i bordi.
        if (ambientOcclusion) gpu("occlusione ambientale") { renderAmbientOcclusion(viewProj, s, full) }

        // 4. Scena vista dalla telecamera.
        val sky = d.sky
        gpu("scena") {
            glBindFramebuffer(GL_FRAMEBUFFER, fboMsaa)
            glViewport(0, 0, s.width, s.height)
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
            // L'occlusione è a metà misure: un pixel della scena è mezzo pixel della sua texture.
            glUniform2f(u("uViewport"), aoTexSize.width * 2f, aoTexSize.height * 2f)
            glUniform1f(u("uUseAo"), if (ambientOcclusion) 1f else 0f)

            // Pieno: scena con i colori dei vertici, superfici fotografiche, arredi.
            setMaterial(useVertexColor = true, roughness = 0.85f, metallic = 0f)
            glUniformMatrix4fv(u("uModel"), false, IDENTITY)
            drawArrays(program, opaqueVbo, opaqueCount, Format.SCENE)
            // Nella foto i materiali più definiti stanno sulla scheda grafica solo mentre si disegna il loro materiale.
            val quality = photoQuality ?: materials
            for ((id, buf) in textured) {
                setMaterial(albedo = res.material(id, "color", quality), normal = res.material(id, "normal", quality), orm = res.material(id, "orm", quality), ormHasAo = true, roughness = 1f, metallic = 1f, macro = id == GRASS)
                drawArrays(program, buf.first, buf.second, Format.TEXTURED)
                if (quality != materials) res.dropMaterial(id, quality)
            }
            forEachFurniturePart(opaqueOnly = true) { part, m ->
                setFurnitureMaterial(part)
                glUniformMatrix4fv(u("uModel"), false, m)
                drawElements(program, part)
            }
        }
        // Cielo dopo il pieno, solo dove non c'è niente davanti: dentro casa quasi niente da calcolare.
        gpu("cielo") { drawSky(d, aspect) }
        gpu("vetri") {
            // Trasparenti: vetri della scena e parti trasparenti degli arredi, senza nascondere ciò che sta dietro. I vetri danno il
            // colore già moltiplicato per la loro copertura (vedi uGlass nello shader).
            glUseProgram(program)
            glEnable(GL_BLEND)
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            glDepthMask(false)
            glUniform1f(u("uUseAo"), 0f)
            setMaterial(useVertexColor = true, roughness = 0.08f, metallic = 0f)
            glUniform1f(u("uGlass"), 1f)
            glUniformMatrix4fv(u("uModel"), false, IDENTITY)
            drawArrays(program, transparentVbo, transparentCount, Format.SCENE)
            glUniform1f(u("uGlass"), 0f)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            forEachFurniturePart(opaqueOnly = false, blendOnly = true) { part, m ->
                setFurnitureMaterial(part)
                glUniformMatrix4fv(u("uModel"), false, m)
                drawElements(program, part)
            }
            glDepthMask(true)
            glDisable(GL_BLEND)
            glFrontFace(GL_CCW)
        }

        // 5. Antialiasing, alone delle luci e copia dei pixel per Compose.
        gpu("antialiasing e alone") {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, fboMsaa)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fboResolve)
            glBlitFramebuffer(0, 0, s.width, s.height, 0, 0, s.width, s.height, GL_COLOR_BUFFER_BIT, GL_NEAREST)
            finish(s, full)
        }
        readPixels(s, sky)
        frames++
        if (timing) collectTimings()
        lastFrameEnd = System.nanoTime()
        if (moving) {
            // Immagine troppo lenta: più piccola; avanza tempo: più grande (a piccoli passi, senza scatti di nitidezza).
            val k = sqrt(FRAME_MS / ((lastFrameEnd - start) / 1e6)).toFloat()
            if (k < 0.95f || k > 1.1f) motionScale = (motionScale * k.coerceIn(0.8f, 1.1f)).coerceIn(MIN_SCALE, 1f)
        }
        if (s != full && mode == Mode.AUTO && !released) {
            // Ci si è fermati se dopo questa non ne arrivano altre: allora un'immagine piena e nitida.
            val n = frames
            runCatching { sharpen.schedule({ if (frames == n) request() }, SHARP_DELAY_MS, TimeUnit.MILLISECONDS) }
        }
    }

    /**
     * Pixel dell'immagine (`s`, nell'angolo in basso a sinistra di [fboResolve]) a Compose. Con [asyncRead] la scheda grafica li copia da
     * sola in un buffer, e intanto si consegna l'immagine di prima e si prepara la prossima: niente attese. L'ultima (nessuna in coda)
     * si consegna subito.
     */
    private fun readPixels(s: IntSize, sky: FloatArray) {
        glBindFramebuffer(GL_FRAMEBUFFER, fboResolve)
        if (photoSize != null) {
            // Foto: letta subito e tenuta per chi l'ha chiesta, lo schermo resta con l'immagine di prima.
            val buf = pixels!!
            buf.clear()
            glReadPixels(0, 0, s.width, s.height, GL_RGBA, GL_UNSIGNED_BYTE, buf)
            photoResult = bitmapOf(s, MemoryUtil.memAddress(buf))
            return
        }
        if (!asyncRead) {
            val buf = pixels!!
            buf.clear()
            glReadPixels(0, 0, s.width, s.height, GL_RGBA, GL_UNSIGNED_BYTE, buf)
            show(s, MemoryUtil.memAddress(buf), sky)
            return
        }
        val slot = (frames % 2).toInt()
        gpu("lettura pixel") {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo[slot])
            glReadPixels(0, 0, s.width, s.height, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
        }
        pboFence[slot] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        pboSize[slot] = s
        pboSky[slot] = sky
        glFlush()
        val other = 1 - slot
        if (scheduled.get()) {
            publish(other)
        } else {
            discard(other)
            publish(slot)
        }
    }

    /** Consegna l'immagine del buffer `i`, aspettando che la scheda grafica abbia finito di copiarla. */
    private fun publish(i: Int) {
        val fence = pboFence[i]
        if (fence == 0L) return
        glClientWaitSync(fence, GL_SYNC_FLUSH_COMMANDS_BIT, 5_000_000_000L)
        glDeleteSync(fence)
        pboFence[i] = 0L
        val s = pboSize[i]!!
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo[i])
        val mapped = glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0L, s.width.toLong() * s.height * 4, GL_MAP_READ_BIT)
        if (mapped != null) {
            show(s, MemoryUtil.memAddress(mapped), pboSky[i]!!)
            glUnmapBuffer(GL_PIXEL_PACK_BUFFER)
        }
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
    }

    private fun discard(i: Int) {
        if (pboFence[i] != 0L) glDeleteSync(pboFence[i])
        pboFence[i] = 0L
    }

    /** Immagine per Compose dai pixel RGBA (`s`) all'indirizzo `pixels`. */
    private fun show(s: IntSize, pixels: Long, sky: FloatArray) {
        val bitmap = bitmapOf(s, pixels)
        bitmap.setImmutable()
        image = bitmap.asComposeImageBitmap()
        skyColor = Color(sky[0], sky[1], sky[2])
    }

    /** Bitmap dai pixel RGBA (`s`) all'indirizzo `pixels`, copiati direttamente nella sua memoria. */
    private fun bitmapOf(s: IntSize, pixels: Long): Bitmap {
        val bytes = s.width * s.height * 4
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(s.width, s.height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
        val dst = bitmap.peekPixels()
        if (dst != null && bitmap.rowBytes == s.width * 4) {
            MemoryUtil.memCopy(pixels, dst.addr, bytes.toLong())
            dst.close()
            bitmap.notifyPixelsChanged()
        } else {
            bitmap.installPixels(ByteArray(bytes).also { MemoryUtil.memByteBuffer(pixels, bytes).get(it) })
        }
        return bitmap
    }

    /** Misura il tempo della scheda grafica per `block` (solo con [timing]). */
    private inline fun gpu(name: String, block: () -> Unit) {
        if (!timing) return block()
        val q = freeQueries.removeLastOrNull() ?: glGenQueries()
        glBeginQuery(GL_TIME_ELAPSED, q)
        block()
        glEndQuery(GL_TIME_ELAPSED)
        queries += name to q
    }

    private fun collectTimings() {
        for ((name, q) in queries) {
            gpuTimes[name] = (gpuTimes[name] ?: 0.0) + glGetQueryObjecti64(q, GL_QUERY_RESULT) / 1e6
            freeQueries += q
        }
        queries.clear()
        if (++gpuFrames == 60) System.err.println("Sagoma 3D, scheda grafica (ms per immagine): " + takeTimings())
    }

    private fun takeTimings(): Map<String, Double> {
        val n = gpuFrames.coerceAtLeast(1)
        val m = gpuTimes.mapValues { it.value / n }
        gpuTimes.clear()
        gpuFrames = 0
        return m
    }

    /** Medie dei tempi della scheda grafica per passaggio, in ms (con SAGOMA_TEMPI_3D=1), poi si ricomincia. */
    internal fun timings(): Map<String, Double> = gl.submit<Map<String, Double>> { takeTimings() }.get()

    private fun u(name: String) = uniforms.getOrPut(name) { glGetUniformLocation(program, name) }

    /**
     * Occlusione ambientale: la scena disegnata con lo shader SSAO a metà misure (è sfumata, non si nota, e costa un quarto), poi sfumata
     * in [aoTex].
     */
    private fun renderAmbientOcclusion(viewProj: FloatArray, s: IntSize, full: IntSize) {
        fun ua(name: String) = glGetUniformLocation(aoProgram, name)
        val a = IntSize((s.width + 1) / 2, (s.height + 1) / 2)
        glBindFramebuffer(GL_FRAMEBUFFER, aoRawFbo)
        glViewport(0, 0, a.width, a.height)
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
        // La profondità occupa solo la parte `s` della sua texture.
        glUniform2f(ua("uUvScale"), s.width.toFloat() / full.width, s.height.toFloat() / full.height)
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
        glUniform2f(ub("uViewport"), aoTexSize.width.toFloat(), aoTexSize.height.toFloat())
        glUniform2f(ub("uUvMax"), (a.width - 0.5f) / aoTexSize.width, (a.height - 0.5f) / aoTexSize.height)
        glUniform1f(ub("uNear"), camera.near.toFloat())
        glUniform1f(ub("uFar"), 100_000f)
        glBindBuffer(GL_ARRAY_BUFFER, screenVbo)
        val aPos = glGetAttribLocation(blurProgram, "aPos")
        glEnableVertexAttribArray(aPos)
        glVertexAttribPointer(aPos, 2, GL_FLOAT, false, 8, 0L)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        glEnable(GL_DEPTH_TEST)
    }

    /**
     * Come in una foto: alone attorno alle luci forti (finestre di giorno viste da dentro, sole che entra, lampade) e angoli appena
     * più scuri, sull'immagine finita in [fboResolve].
     */
    private fun finish(s: IntSize, full: IntSize) {
        val q = IntSize(maxOf(1, s.width / 4), maxOf(1, s.height / 4))
        val qt = IntSize(maxOf(1, full.width / 4), maxOf(1, full.height / 4))
        if (q != qt) {
            // Immagine più piccola delle texture: il resto nero, così la sfumatura sui bordi non prende pezzi delle immagini di prima.
            glClearColor(0f, 0f, 0f, 1f)
            for (fb in intArrayOf(bloomFboA, bloomFboB)) {
                glBindFramebuffer(GL_FRAMEBUFFER, fb)
                glClear(GL_COLOR_BUFFER_BIT)
            }
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fboResolve)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, bloomFboA)
        glBlitFramebuffer(0, 0, s.width, s.height, 0, 0, q.width, q.height, GL_COLOR_BUFFER_BIT, GL_LINEAR)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glViewport(0, 0, q.width, q.height)
        glUseProgram(bloomProgram)
        fun ub(name: String) = glGetUniformLocation(bloomProgram, name)
        glUniform1i(ub("uSrc"), 0)
        glUniform2f(ub("uViewport"), qt.width.toFloat(), qt.height.toFloat())
        glActiveTexture(GL_TEXTURE0)
        for ((target, source, horizontal) in listOf(Triple(bloomFboB, bloomTexA, true), Triple(bloomFboA, bloomTexB, false))) {
            glBindFramebuffer(GL_FRAMEBUFFER, target)
            glBindTexture(GL_TEXTURE_2D, source)
            glUniform2f(ub("uDir"), if (horizontal) 1f / qt.width else 0f, if (horizontal) 0f else 1f / qt.height)
            glUniform1f(ub("uThreshold"), if (horizontal) 1f else 0f)
            screenTriangle(bloomProgram)
        }
        glBindFramebuffer(GL_FRAMEBUFFER, fboResolve)
        glViewport(0, 0, s.width, s.height)
        glUseProgram(finishProgram)
        glBindTexture(GL_TEXTURE_2D, bloomTexA)
        glUniform1i(glGetUniformLocation(finishProgram, "uBloom"), 0)
        glUniform2f(glGetUniformLocation(finishProgram, "uViewport"), s.width.toFloat(), s.height.toFloat())
        glUniform2f(glGetUniformLocation(finishProgram, "uBloomScale"), q.width.toFloat() / (s.width * qt.width), q.height.toFloat() / (s.height * qt.height))
        glUniform1f(glGetUniformLocation(finishProgram, "uBloomStrength"), 0.3f)
        glEnable(GL_BLEND)
        glBlendFunc(GL_ONE, GL_SRC_ALPHA)
        screenTriangle(finishProgram)
        glDisable(GL_BLEND)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
    }

    /** Triangolo che copre tutto lo schermo, con il programma `prog` (attributo aPos). */
    private fun screenTriangle(prog: Int) {
        glBindBuffer(GL_ARRAY_BUFFER, screenVbo)
        val aPos = glGetAttribLocation(prog, "aPos")
        glEnableVertexAttribArray(aPos)
        glVertexAttribPointer(aPos, 2, GL_FLOAT, false, 8, 0L)
        glDrawArrays(GL_TRIANGLES, 0, 3)
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
        val lights = LitShaders.Lights(scene, d, eye, windowsAlways = true, brightness = lampBrightness)
        // Di notte l'occhio si abitua un poco: più esposizione solo se c'è qualche lampada accesa (così le
        // stanze illuminate si vedono bene); senza luci resta tutto buio.
        val exposure = 1.0f + (if (lights.lampCount > 0) 1.4f else 0.3f) * (1f - d.day)
        glUniform1f(u("uExposure"), exposure)
        // Dentro casa (sotto i soffitti, anche nella vista dall'alto dove il tetto non si vede): il cielo entra
        // solo dalle finestre, la luce diffusa è quella che rimbalza su pavimento, pareti e soffitto, quasi
        // bianca e un poco calda. Di notte la danno solo le lampade, stanza per stanza.
        var kIn = skyK * 0.55f
        var exposureIn = exposure
        // Il cielo che si vede da una finestra: metà cielo, metà terreno e case intorno, un po' meno attraverso il vetro.
        val winK = skyK * 0.62f
        if (walking) {
            // Camminando, di giorno, come in una foto d'interni: dentro casa arriva molta meno luce che fuori (quella delle finestre, più
            // forte vicino a loro, e quella del sole che entra), e l'esposizione si regola sulla stanza in cui si è, come l'esposimetro
            // di una macchina fotografica: luce del cielo dalle finestre, metà di quella del sole (una stanza al sole resta più luminosa)
            // e le lampade. Quando sono le lampade a fare quasi tutta la luce (di notte, o accese di giorno) resta come prima, così
            // la loro luminosità si regola sempre con il cursore.
            val room = 2 * PI * winK * lights.meterWindows + 0.5 * d.sun[1] * sunK * 0.32 * lights.meterSun + lights.meterLamps +
                PI * kIn * INDOOR_AMBIENT
            val metered = if (lights.meterIndoor) (EXPOSURE_TARGET / room.toFloat()).coerceIn(1f, MAX_BOOST) else OUTSIDE_BOOST
            val boost = 1f + (metered - 1f) * d.day
            kIn *= maxOf(INDOOR_AMBIENT, 1f / (boost * boost))
            exposureIn = exposure * boost
        }
        glUniform1f(u("uExposureIn"), exposureIn)
        glUniform1f(u("uEyeInside"), if (lights.meterIndoor) 1f else 0f)
        glUniform1f(u("uGlass"), 0f)
        glUniform3f(u("uInSky"), 1.0f * kIn, 0.975f * kIn, 0.94f * kIn)
        glUniform3f(u("uInGround"), 0.95f * kIn * 0.75f, 0.86f * kIn * 0.75f, 0.74f * kIn * 0.75f)
        glActiveTexture(GL_TEXTURE7)
        glBindTexture(GL_TEXTURE_2D, roofTex)
        glUniform1i(u("uRoofMap"), 7)
        glUniformMatrix4fv(u("uRoofMvp"), false, roofMvp)
        glUniform1f(u("uRoofTexel"), 1f / ROOF_SIZE)
        glUniform1f(u("uRoofDepthCm"), (ROOF_FAR - 1.0).toFloat())
        glUniform1i(u("uAlbedoTex"), 0)
        glUniform1i(u("uNormalTex"), 1)
        glUniform1i(u("uOrmTex"), 2)
        glUniform1i(u("uEmissiveTex"), 3)
        glUniform1i(u("uShadowMap"), 4)
        glUniform1f(u("uShadowTexel"), 1f / SHADOW_SIZE)
        // Ombre in centimetri veri, per ogni grandezza della casa (vedi il vertex shader). Margine di profondità di 3 mm: il sole non
        // passa negli ultimi centimetri di una parete sotto il soffitto (con il margine di prima, 5-15 cm: una "striscia" chiara).
        val r = sunRadius()
        glUniform1f(u("uShadowTexelCm"), (2 * r / SHADOW_SIZE).toFloat())
        glUniform1f(u("uShadowBias"), (0.3 / (r * 4 - 1.0)).toFloat())
        // Bordo morbido pieno quando ciò che fa ombra è ad almeno 20 cm, più netto quando è più vicino.
        glUniform1f(u("uShadowSoft"), ((r * 4 - 1.0) / 20.0).toFloat())
        glUniform1i(u("uLampCount"), lights.lampCount)
        glUniform4fv(u("uLamp"), lights.lamps)
        glUniform3fv(u("uLampColor"), lights.lampColors)
        glUniform4fv(u("uLampBox"), lights.lampBoxes)
        glUniform4fv(u("uLampAxis"), lights.lampAxes)
        glUniform1fv(u("uLampSame"), lights.lampSame)
        // Finestre: rettangoli di cielo che illuminano la loro stanza.
        glUniform1i(u("uWinCount"), lights.skyWindowCount)
        glUniform4fv(u("uWin"), lights.skyWindows)
        glUniform4fv(u("uWinAxis"), lights.skyWindowAxes)
        glUniform4fv(u("uWinRoom"), lights.skyRooms)
        glUniform4fv(u("uWinBox"), lights.skyBoxes)
        glUniform3f(u("uWinSky"), d.windowColor[0] * winK, d.windowColor[1] * winK, d.windowColor[2] * winK)
        // Sole che entra dalle finestre e rimbalza dalla macchia chiara sul pavimento (di solito legno o cotto: luce calda).
        glUniform4fv(u("uSunPatch"), lights.sunPatches)
        glUniform4fv(u("uSunPatchN"), lights.sunPatchNormals)
        glUniform4fv(u("uSunGate"), lights.sunGates)
        glUniform3f(u("uBounce"), d.sun[0] * sunK * 0.40f, d.sun[1] * sunK * 0.32f, d.sun[2] * sunK * 0.24f)
    }

    /**
     * Cielo dove non c'è altro davanti (si disegna dopo il pieno, dietro a tutto): azzurro più pieno in alto, velato all'orizzonte, il
     * bagliore del sole, nuvole leggere e in fondo alberi e colline lontane (vedi [PbrShaders.skyFragment]). Colori già pronti per lo
     * schermo (sRGB). `aspect` = larghezza / altezza della vista.
     */
    private fun drawSky(d: Daylight, aspect: Float) {
        glDepthMask(false)
        glUseProgram(skyProgram)
        fun us(name: String) = glGetUniformLocation(skyProgram, name)
        // Direzioni della telecamera dalla sua matrice (righe: destra, alto, indietro), allungate fino ai bordi dell'immagine.
        val v = camera.view
        val ty = tan(Math.toRadians(camera.fovY / 2)).toFloat()
        val tx = ty * aspect
        glUniform3f(us("uRight"), v[0] * tx, v[4] * tx, v[8] * tx)
        glUniform3f(us("uUp"), v[1] * ty, v[5] * ty, v[9] * ty)
        glUniform3f(us("uForward"), -v[2], -v[6], -v[10])
        val h = d.sky
        glUniform3f(us("uHorizon"), h[0], h[1], h[2])
        // In alto il cielo è più azzurro e più scuro; di notte blu scurissimo, al tramonto un po' meno saturo.
        val day = d.day
        val zenith = floatArrayOf(0.36f, 0.56f, 0.88f).mapIndexed { i, c ->
            val dayC = c + (floatArrayOf(0.42f, 0.50f, 0.72f)[i] - c) * d.low
            floatArrayOf(0.01f, 0.018f, 0.05f)[i] + (dayC - floatArrayOf(0.01f, 0.018f, 0.05f)[i]) * day
        }
        glUniform3f(us("uZenith"), zenith[0], zenith[1], zenith[2])
        glUniform3f(us("uToSun"), d.toSun.x.toFloat(), d.toSun.y.toFloat(), d.toSun.z.toFloat())
        val glow = if (d.elevation > -3) day else 0f
        glUniform3f(us("uSunGlow"), glow, glow * (0.92f - 0.30f * d.low), glow * (0.80f - 0.45f * d.low))
        val k = 0.10f + 0.90f * day
        glUniform3f(us("uTrees"), 0.17f * k, 0.29f * k, 0.15f * k)
        // Come il prato in lontananza (che finisce 40 m oltre la casa), un po' velato.
        glUniform3f(us("uFarGround"), 0.68f * k, 0.75f * k, 0.53f * k)
        glUniform1f(us("uClouds"), day)
        screenTriangle(skyProgram)
        glDepthMask(true)
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

    /** Mezzo lato (cm) del quadrato visto dal sole per le ombre: tutta la casa e un po' di prato attorno. */
    private fun sunRadius(): Double = (scene?.bounds?.let { hypot(it.width, it.height) / 2 } ?: 1000.0) + 600.0

    /** Proiezione ortogonale dal sole che contiene tutta la casa. */
    private fun sunMatrix(d: Daylight): FloatArray {
        val b = scene?.bounds
        val cx = b?.let { (it.minX + it.maxX) / 2 } ?: 0.0
        val cz = b?.let { (it.minY + it.maxY) / 2 } ?: 0.0
        val r = sunRadius()
        val center = Vec3(cx, 150.0, cz)
        val from = center + d.toSun * (r * 2)
        val up = if (abs(d.toSun.y) > 0.95) Vec3(0.0, 0.0, 1.0) else Vec3.Up
        return Mat4.multiply(ortho(r, 1.0, r * 4), Mat4.lookAt(from, center, up))
    }

    private var roofMvp = IDENTITY

    /** Scene caricate sulla scheda grafica (cambia a ogni [upload]). */
    private var uploads = 0
    /** Con cosa sono state disegnate l'ultima volta mappa dei soffitti e ombre: se non cambia niente restano quelle. */
    private var depthMapsUploads = -1
    private var depthMapsFurniture = -1
    private var depthMapsSun: FloatArray? = null
    private var depthMapsRoof: FloatArray? = null

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
        val half = IntSize((s.width + 1) / 2, (s.height + 1) / 2)
        colorTarget(half, withDepth = true).let { (fb, t) -> aoRawFbo = fb; aoRawTex = t }
        colorTarget(half, withDepth = false).let { (fb, t) -> aoFbo = fb; aoTex = t }
        aoTexSize = half
        glBindTexture(GL_TEXTURE_2D, aoTex)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        if (bloomFboA != 0) {
            glDeleteFramebuffers(bloomFboA); glDeleteFramebuffers(bloomFboB)
            glDeleteTextures(bloomTexA); glDeleteTextures(bloomTexB)
        }
        val q = IntSize(maxOf(1, s.width / 4), maxOf(1, s.height / 4))
        colorTarget(q, withDepth = false).let { (fb, t) -> bloomFboA = fb; bloomTexA = t }
        colorTarget(q, withDepth = false).let { (fb, t) -> bloomFboB = fb; bloomTexB = t }
        for (t in listOf(bloomTexA, bloomTexB)) {
            glBindTexture(GL_TEXTURE_2D, t)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        }
        // Due buffer per leggere i pixel senza aspettare (vedi [readPixels]); le immagini in attesa con le misure di prima si lasciano.
        asyncRead = GL.getCapabilities().OpenGL32
        if (asyncRead) {
            for (i in 0..1) {
                discard(i)
                if (pbo[i] == 0) pbo[i] = glGenBuffers()
                glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo[i])
                glBufferData(GL_PIXEL_PACK_BUFFER, s.width.toLong() * s.height * 4, GL_STREAM_READ)
            }
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
        }
        fboSize = s
    }

    /** Scena sulla GPU: pieno con il prato attorno, trasparenze, superfici fotografiche. */
    private fun upload(s: Scene3D) {
        uploads++
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

    /** Ultima immagine consegnata a Compose, e attesa che il renderer abbia finito quello che ha in coda (per le prove). */
    internal val shown: ImageBitmap? get() = image
    internal fun idle() { gl.submit {}.get() }

    /** Disegna subito con le misure date e restituisce l'immagine (per le prove): nitida, o come muovendosi con [Mode.MOVING]. */
    internal fun renderNow(width: Int, height: Int, mode: Mode = Mode.STILL): ImageBitmap? {
        size = IntSize(width, height)
        gl.submit { renderFrame(mode) }.get()
        return image
    }

    override val canRenderPhoto: Boolean get() = true

    /**
     * Foto della vista: la stessa scena e la stessa telecamera, disegnate fuori schermo con il lato lungo di `longSide` pixel
     * (al massimo quello che la scheda grafica regge) e i materiali 4K. Lo schermo poi torna alle sue misure.
     */
    override fun renderPhoto(longSide: Int, onDone: (ImageBitmap?) -> Unit) {
        if (released) return onDone(null)
        gl.execute {
            val photo = runCatching {
                val view = size
                if (view.width <= 0 || view.height <= 0 || context?.isOpen != true) return@runCatching null
                val max = minOf(glGetInteger(GL_MAX_RENDERBUFFER_SIZE), glGetInteger(GL_MAX_TEXTURE_SIZE))
                val k = minOf(longSide, max).toDouble() / maxOf(view.width, view.height)
                photoSize = IntSize((view.width * k).roundToInt().coerceAtLeast(1), (view.height * k).roundToInt().coerceAtLeast(1))
                photoQuality = MaterialResolution.K4
                photoResult = null
                // Si riprova quello che mancava: i 4K appena arrivati dal server.
                res.retryMissing()
                renderFrame(Mode.STILL)
                photoResult
            }.onFailure { it.printStackTrace() }.getOrNull()
            photoSize = null
            photoQuality = null
            photoResult = null
            onDone(photo?.let { it.setImmutable(); it.asComposeImageBitmap() })
            // Lo schermo torna alle sue misure (i framebuffer si rifanno al prossimo disegno).
            request()
        }
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

    override fun setDaylight(hour: Float, lamps: String, sun: SunAngles) {
        if (hour == this.hour && lamps == this.lamps && sun == sunAt) return
        this.hour = hour
        this.lamps = lamps
        this.sunAt = sun
        request()
    }

    override val queued: Boolean get() = scheduled.get()

    override fun setLampBrightness(k: Float) {
        if (k == lampBrightness) return
        lampBrightness = k
        request()
    }

    override fun release() {
        released = true
        sharpen.shutdownNow()
        gl.execute {
            context?.close()
            context = null
        }
        gl.shutdown()
    }

    private companion object {
        /**
         * Camminando di giorno: luce diffusa che resta uguale in tutta la casa (quella che arriva da altre stanze, da porte e corridoi),
         * rispetto a prima; luce media di una stanza ben esposta; quanto si può alzare l'esposizione dentro casa (una stanza senza
         * finestre resta un po' più scura); dentro casa guardando da fuori (si vede più scuro, come dal giardino).
         */
        const val INDOOR_AMBIENT = 0.06f
        const val EXPOSURE_TARGET = 1.8f
        const val MAX_BOOST = 24f
        const val OUTSIDE_BOOST = 3f
        /** Immagini a meno di 50 ms una dall'altra: ci si sta muovendo. */
        const val MOTION_GAP_NS = 50_000_000L
        /** Tempo per immagine a cui si mira muovendosi (circa 50 al secondo), e misure minime dell'immagine (metà). */
        const val FRAME_MS = 20.0
        const val MIN_SCALE = 0.5f
        /** Dopo l'ultima immagine in movimento, quella nitida. */
        const val SHARP_DELAY_MS = 150L
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
