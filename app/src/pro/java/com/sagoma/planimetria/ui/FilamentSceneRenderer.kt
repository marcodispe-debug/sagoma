package com.sagoma.planimetria.ui

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import android.view.View as AndroidView
import com.sagoma.planimetria.assets.AssetPaths
import com.sagoma.planimetria.assets.AssetStore
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Texture
import android.util.Log
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.sagoma.planimetria.geometry.Glb
import com.sagoma.planimetria.geometry.Rgba
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import java.nio.ByteBuffer
import kotlin.math.pow

/**
 * Versione pro: la scena disegnata con Filament (Google), con materiali fisici, sole con ombre morbide,
 * luce ambiente, occlusione ambientale negli angoli (SSAO), antialiasing e resa dei colori naturale.
 * La scena arriva come glTF in memoria ([Glb]); si ridisegna solo quando cambia qualcosa.
 * Modelli, texture e luci ambiente li dà `assets` (da dove arrivino, qui non importa).
 */
class FilamentSceneRenderer(private val assets: AssetStore) : AndroidSceneRenderer() {

    companion object {
        private var loaded = false
        fun init() {
            if (loaded) return
            Filament.init()
            Gltfio.init()
            loaded = true
        }
        private fun lin(c: Float) = c.toDouble().pow(2.2).toFloat()
        private val SKY = Rgba(0.93f, 0.95f, 0.97f)
        private val GROUND = Rgba(0.80f, 0.82f, 0.78f)
        /** Materiale del prato attorno alla casa. */
        private const val GRASS = "grass005"
    }

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: View
    private val camera: Camera
    private val cameraEntity: Int
    private val sun: Int
    private val fill: Int
    private var indirect: IndirectLight
    private var skybox: Skybox
    /** Luci ambiente lette da assets/env/<nome>.ibl: riflessi (cubo), armoniche sferiche, cielo. */
    private class Env(val reflections: Texture, val sh: FloatArray, val sky: Skybox?)
    private val envs = HashMap<String, Env?>()
    private var envName = ""
    private var ambientBoost = 1f
    private val grading: ColorGrading
    private val materials: UbershaderProvider
    private val assetLoader: AssetLoader
    private val resourceLoader: ResourceLoader
    private val texturedSurfaces: TexturedSurfaces
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var displayHelper: DisplayHelper? = null
    private var swapChain: SwapChain? = null
    private var surfaceView: SurfaceView? = null

    private var asset: FilamentAsset? = null
    /** File dei modelli degli arredi, letti una volta sola (null = modello mancante). */
    private val modelFiles = HashMap<String, ByteBuffer?>()
    /** Arredi in scena, nello stesso ordine della scena: si riusano se il modello non cambia. */
    private val furniture = mutableListOf<Pair<String, FilamentAsset>>()
    private var pendingScene: Scene3D? = null
    private var dirty = true
    private var running = false
    private var width = 1
    private var height = 1
    private var fovY = 60.0
    private var near = 5.0
    private var walking = false

    init {
        init()
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        cameraEntity = EntityManager.get().create()
        camera = engine.createCamera(cameraEntity)
        // Esposizione fissa: 1 unità di luce = bianco. Le intensità qui sotto sono tarate su questo.
        camera.setExposure(1f)
        view.scene = scene
        view.camera = camera

        // Cielo chiaro come sfondo (il colore dei renderer è lineare).
        skybox = Skybox.Builder().color(lin(SKY.r), lin(SKY.g), lin(SKY.b), 1f).build(engine)
        scene.skybox = skybox
        // Luce ambiente uniforme, un poco azzurra come il cielo.
        indirect = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.50f, 0.52f, 0.56f))
            .intensity(1f)
            .build(engine)
        scene.indirectLight = indirect
        // Sole dall'alto di lato (come la luce del renderer semplice), con ombre morbide.
        sun = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 0.96f, 0.90f)
            .intensity(2.4f)
            .direction(-0.45f, -0.85f, -0.30f)
            .castShadows(true)
            .shadowOptions(
                LightManager.ShadowOptions().apply {
                    mapSize = 2048
                    shadowCascades = 3
                    constantBias = 0.002f
                    normalBias = 0.6f
                    stable = false
                },
            )
            .build(engine, sun)
        scene.addEntity(sun)
        // Luce di riempimento dal lato opposto, senza ombre: le facce in ombra non diventano piatte.
        fill = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.92f, 0.95f, 1f)
            .intensity(0.5f)
            .direction(0.5f, -0.3f, 0.6f)
            .castShadows(false)
            .build(engine, fill)
        scene.addEntity(fill)

        grading = ColorGrading.Builder().toneMapper(ToneMapper.PBRNeutralToneMapper()).build(engine)
        view.colorGrading = grading
        view.antiAliasing = View.AntiAliasing.FXAA
        view.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply {
            enabled = true
            sampleCount = 4
        }
        view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply {
            enabled = true
            radius = 0.35f
            intensity = 1.1f
            power = 1.2f
            quality = View.QualityLevel.MEDIUM
        }
        view.setShadowType(View.ShadowType.PCF)

        materials = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materials, EntityManager.get())
        resourceLoader = ResourceLoader(engine)
        texturedSurfaces = TexturedSurfaces(engine, scene, materials, assets)
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            pendingScene?.let { load(it); pendingScene = null }
            val chain = swapChain ?: return
            if (!dirty || !uiHelper.isReadyToRender) return
            if (renderer.beginFrame(chain, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
                dirty = false
            }
        }
    }

    override fun createView(context: Context): AndroidView {
        val sv = SurfaceView(context)
        surfaceView = sv
        displayHelper = DisplayHelper(context)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                displayHelper?.attach(renderer, sv.display)
                dirty = true
            }

            override fun onDetachedFromSurface() {
                displayHelper?.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                this@FilamentSceneRenderer.width = width.coerceAtLeast(1)
                this@FilamentSceneRenderer.height = height.coerceAtLeast(1)
                view.viewport = Viewport(0, 0, width, height)
                updateProjection()
                dirty = true
            }
        }
        uiHelper.attachTo(sv)
        onResume()
        return sv
    }

    override fun setScene(scene: Scene3D) {
        pendingScene = scene
        dirty = true
    }

    override fun setCamera(eye: Vec3, center: Vec3, up: Vec3, fovY: Double, near: Double, walking: Boolean) {
        val s = 0.01 // cm → m
        camera.lookAt(eye.x * s, eye.y * s, eye.z * s, center.x * s, center.y * s, center.z * s, up.x, up.y, up.z)
        this.fovY = fovY
        this.near = near
        if (walking != this.walking) {
            this.walking = walking
            applyLighting()
        }
        updateProjection()
        dirty = true
    }

    // ---------- Luce del giorno e luci della casa ----------

    private var hour = 11f
    private var lampsMode = "auto"
    /** Luci create dalla scena (lampade e finestre), da togliere quando la scena cambia. */
    private val lampEntities = mutableListOf<Int>()
    private val windowEntities = mutableListOf<Int>()
    private var nightSky: Skybox? = null
    private var sceneLights: List<Scene3D.SceneLight> = emptyList()
    private var sceneWindows: List<Scene3D.SceneWindow> = emptyList()

    override fun setDaylight(hour: Float, lamps: String) {
        if (hour == this.hour && lamps == lampsMode) return
        this.hour = hour
        this.lampsMode = lamps
        applyLighting()
    }

    /**
     * Posizione del sole all'ora scelta (nord in alto nella pianta, sole a est alle 6, a sud a mezzogiorno,
     * a ovest alle 18), altezza fino a 55°. Ne seguono colore e forza del sole, del cielo e della luce che
     * entra dalle finestre; le lampade si accendono quando il sole è basso (o sempre / mai, a scelta).
     */
    private fun sunElevation(): Double = 55.0 * kotlin.math.sin(Math.PI * (hour - 6.0) / 12.0)

    private fun applyLighting() {
        val el = sunElevation()
        val az = Math.toRadians(90.0 + (hour - 6.0) * 15.0)
        val elr = Math.toRadians(el.coerceAtLeast(2.0))
        // Direzione in cui viaggia la luce (dal sole verso la casa).
        val sx = (kotlin.math.sin(az) * kotlin.math.cos(elr)).toFloat()
        val sy = kotlin.math.sin(elr).toFloat()
        val sz = (-kotlin.math.cos(az) * kotlin.math.cos(elr)).toFloat()
        // Quanto giorno c'è: 0 di notte, 1 con il sole alto; morbido attorno all'alba e al tramonto.
        val day = ((el + 6.0) / 30.0).coerceIn(0.0, 1.0).toFloat()
        val low = (1.0 - (el / 35.0).coerceIn(0.0, 1.0)).toFloat() // 1 = sole basso, luce calda
        val lm = engine.lightManager
        val s = lm.getInstance(sun)
        lm.setDirection(s, -sx, -sy, -sz)
        lm.setColor(s, 1f, 0.97f - 0.33f * low, 0.92f - 0.52f * low)
        lm.setIntensity(s, if (el <= 0) 0f else 2.6f * day)
        lm.setShadowCaster(s, true)
        lm.setIntensity(lm.getInstance(fill), 0f)

        // Cielo e luce ambiente: foto del giorno o del tramonto, sempre più scure verso la notte.
        val envName = if (el > 14) "giorno" else "tramonto"
        val env = envs.getOrPut(envName) { loadEnv(envName) }
        // Dentro casa la luce del cielo entra solo dalle finestre: la luce ambiente diffusa si riduce
        // molto e la sostituiscono le luci delle finestre.
        // Di notte resta solo un filo di luce lunare: senza lampade accese non si vede quasi niente.
        val ambient = (0.004f + 0.996f * day) * (if (walking) 0.45f else 1f)
        if (env != null) {
            val old = indirect
            indirect = IndirectLight.Builder().reflections(env.reflections).irradiance(3, env.sh).intensity(ambient).build(engine)
            scene.indirectLight = indirect
            engine.destroyIndirectLight(old)
            this.envName = envName
        } else indirect.intensity = ambient
        if (day < 0.15f) {
            if (nightSky == null) nightSky = Skybox.Builder().color(0.012f, 0.018f, 0.04f, 1f).build(engine)
            scene.skybox = nightSky
        } else scene.skybox = env?.sky ?: skybox

        lampsOnNow = lampsMode == "on" || (lampsMode == "auto" && el < 8)
        updateLamps(lampsOn = lampsOnNow)
        updateWindows(day, low)
        dirty = true
    }

    /** Fattore tra i lumen veri e l'esposizione della scena (tarato perché una stanza di notte si veda bene). */
    private val LUMEN_SCALE = 0.05f

    /** Le lampade della casa sono accese (calcolato da [applyLighting]). */
    private var lampsOnNow = false

    /**
     * Cambiano solo le luci (faretto orientato con la maniglia 3D): senza ricostruire la scena né l'illuminazione del giorno.
     * Se le luci sono le stesse di prima per numero e tipo (con o senza direzione) si aggiornano sul posto direzione e posizione
     * delle entità Filament già esistenti (`FOCUSED_SPOT` per chi ha una direzione); altrimenti si ricreano.
     */
    override fun setLights(lights: List<Scene3D.SceneLight>): Boolean {
        val old = sceneLights
        sceneLights = lights
        if (!lampsOnNow) return true // lampade spente: niente da aggiornare, le luci nuove valgono alla prossima accensione
        val lm = engine.lightManager
        val sameShape = lampEntities.size == lights.size && old.size == lights.size &&
            lights.indices.all { (old[it].direction == null) == (lights[it].direction == null) }
        if (sameShape) {
            for ((i, l) in lights.withIndex()) {
                val inst = lm.getInstance(lampEntities[i])
                val p = l.position
                lm.setPosition(inst, (p.x * 0.01).toFloat(), (p.y * 0.01).toFloat(), (p.z * 0.01).toFloat())
                l.direction?.let { lm.setDirection(inst, it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }
            }
        } else updateLamps(lampsOn = true)
        dirty = true
        return true
    }

    private fun updateLamps(lampsOn: Boolean) {
        val lm = engine.lightManager
        for (e in lampEntities) { scene.removeEntity(e); lm.destroy(e); EntityManager.get().destroy(e) }
        lampEntities.clear()
        if (!lampsOn) return
        for (l in sceneLights) {
            val e = EntityManager.get().create()
            val (r, g, b) = if (l.warm) Triple(1f, 0.82f, 0.62f) else Triple(0.95f, 0.97f, 1f)
            val p = l.position
            val dir = l.direction
            val builder = if (dir != null) LightManager.Builder(LightManager.Type.FOCUSED_SPOT)
                .direction(dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
                .spotLightCone(0.35f, 0.8f)
            else LightManager.Builder(LightManager.Type.POINT)
            builder.color(r, g, b)
                .intensity((l.lumens * LUMEN_SCALE).toFloat())
                .position((p.x * 0.01).toFloat(), (p.y * 0.01).toFloat(), (p.z * 0.01).toFloat())
                .falloff(7f)
                .castShadows(false)
                .build(engine, e)
            scene.addEntity(e)
            lampEntities += e
        }
    }

    /**
     * Luce del cielo che entra da ogni finestra: una luce morbida appena dentro, rivolta verso la stanza e un
     * po' in basso, forte quanto la finestra è grande e quanto è chiaro fuori (niente di notte).
     */
    private fun updateWindows(day: Float, low: Float) {
        val lm = engine.lightManager
        for (e in windowEntities) { scene.removeEntity(e); lm.destroy(e); EntityManager.get().destroy(e) }
        windowEntities.clear()
        if (day <= 0.02f) return
        for (w in sceneWindows) {
            val area = (w.width * w.height / 10000.0).toFloat()
            val e = EntityManager.get().create()
            val c = w.center + w.inward * 25.0
            val d = (w.inward + Vec3(0.0, -0.45, 0.0)).normalized()
            LightManager.Builder(LightManager.Type.SPOT)
                .color(0.9f + 0.1f * low, 0.94f, 1f - 0.2f * low)
                .intensity(1500f * area * day * LUMEN_SCALE)
                .position((c.x * 0.01).toFloat(), (c.y * 0.01).toFloat(), (c.z * 0.01).toFloat())
                .direction(d.x.toFloat(), d.y.toFloat(), d.z.toFloat())
                .spotLightCone(0.9f, 1.45f)
                .falloff(9f)
                .castShadows(false)
                .build(engine, e)
            scene.addEntity(e)
            windowEntities += e
        }
    }

    /**
     * Luce ambiente fotografata: riflessi e luce diffusa dalla foto a 360°, cielo visibile (solo fuori),
     * e un sole coerente con la foto (a mezzogiorno alto e bianco, al tramonto basso e arancio).
     */

    /** Legge assets/env/<name>.ibl (preparato con tools/furniture/PackEnv.java). */
    private fun loadEnv(name: String): Env? = runCatching {
        val bytes = assets.peek(AssetPaths.environment(name)) ?: error("${AssetPaths.environment(name)} non trovato")
        val bb = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        check(bb.get().toInt() == 'S'.code && bb.get().toInt() == 'I'.code && bb.get().toInt() == 'B'.code && bb.get().toInt() == 'L'.code)
        bb.int // versione
        val size = bb.int
        val levels = bb.int
        val sh = FloatArray(27) { bb.float }
        val tex = Texture.Builder()
            .width(size).height(size).levels(levels)
            .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
            .format(Texture.InternalFormat.R11F_G11F_B10F)
            .build(engine)
        // Si usa il cubo nitido (livello 0: 6 facce RGB half float); i livelli sempre più ruvidi dei
        // riflessi li calcola Filament sulla scheda grafica.
        val face = size * size * 6
        val data = ByteBuffer.allocateDirect(face * 6).order(java.nio.ByteOrder.nativeOrder())
        data.put(bytes, bb.position(), face * 6)
        data.flip()
        tex.generatePrefilterMipmap(
            engine,
            Texture.PixelBufferDescriptor(data, Texture.Format.RGB, Texture.Type.HALF),
            IntArray(6) { it * face },
            Texture.PrefilterOptions(),
        )
        // All'aperto il cielo della foto fa da sfondo; dentro casa resta il cielo chiaro uniforme.
        val sky = if (name == "interno") null else Skybox.Builder().environment(tex).build(engine)
        Env(tex, sh, sky)
    }.onFailure { Log.w("Sagoma", "luce $name non disponibile", it) }.getOrNull()

    private fun updateProjection() {
        camera.setProjection(fovY, width.toDouble() / height, near * 0.01, 1000.0, Camera.Fov.VERTICAL)
    }

    /** Sostituisce la scena disegnata con quella nuova. */
    private fun load(s: Scene3D) {
        val bytes = Glb.fromScene(s, ground = GROUND)
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes)
        buffer.flip()
        val next = assetLoader.createAsset(buffer) ?: return
        resourceLoader.loadResources(next)
        next.releaseSourceData()
        val rm = engine.renderableManager
        for (e in next.renderableEntities) {
            val i = rm.getInstance(e)
            val name = next.getName(e)
            // I vetri e le lampade non fanno ombra; tutto il resto sì, e riceve le ombre.
            rm.setCastShadows(i, name == "lit" || name == "lit_down")
            rm.setReceiveShadows(i, name != "unlit")
        }
        asset?.let {
            scene.removeEntities(it.entities)
            assetLoader.destroyAsset(it)
        }
        scene.addEntities(next.entities)
        asset = next
        loadFurniture(s.furniture)
        texturedSurfaces.update(s.textured + (GRASS to ground(s)))
        sceneLights = s.lights
        sceneWindows = s.windows
        applyLighting()
    }

    /**
     * Prato attorno alla casa: un quadrato di 80 m più largo della pianta, appena sopra il terreno
     * colorato (che resta per il renderer semplice), con l'erba ripetuta ogni 2 m.
     */
    private fun ground(s: Scene3D): FloatArray {
        var minY = 0f
        var k = 1
        while (k < s.opaque.size) { minY = minOf(minY, s.opaque[k]); k += Scene3D.FLOATS_PER_VERTEX }
        val b = s.bounds ?: return FloatArray(0)
        val m = 4000.0
        val x0 = (b.minX - m).toFloat(); val x1 = (b.maxX + m).toFloat()
        val z0 = (b.minY - m).toFloat(); val z1 = (b.maxY + m).toFloat()
        val y = minY - 0.3f
        val size = 200f
        fun v(x: Float, z: Float) = floatArrayOf(x, y, z, 0f, 1f, 0f, x / size, z / size)
        // Due triangoli rivolti verso l'alto.
        return v(x0, z0) + v(x0, z1) + v(x1, z1) + v(x0, z0) + v(x1, z1) + v(x1, z0)
    }

    private fun modelFile(name: String): ByteBuffer? = modelFiles.getOrPut(name) {
        assets.peek(AssetPaths.furnitureModel(name))?.let { bytes ->
            ByteBuffer.allocateDirect(bytes.size).put(bytes).also { it.flip() }
        }
    }

    /**
     * Arredi: ogni modello si adatta alle misure scelte (larghezza, profondità e altezza), con la base
     * appoggiata alla quota giusta e ruotato come in pianta. Quelli già caricati si riusano (per modello);
     * un modello che non c'è (o non si legge) si salta senza toccare gli altri.
     */
    private fun loadFurniture(items: List<Scene3D.PlacedFurniture>) {
        val pool = furniture.toMutableList()
        furniture.clear()
        for (p in items) {
            val reuse = pool.indexOfFirst { it.first == p.model }
            val a = if (reuse >= 0) pool.removeAt(reuse).second else createFurniture(p.model) ?: continue
            place(a, p)
            furniture += p.model to a
        }
        // Quelli rimasti non servono più.
        for ((_, a) in pool) destroyFurniture(a)
    }

    private fun createFurniture(model: String): FilamentAsset? {
        val buffer = modelFile(model) ?: return null
        val a = runCatching { assetLoader.createAsset(buffer.duplicate()) }.getOrNull() ?: return null
        resourceLoader.loadResources(a)
        a.releaseSourceData()
        val rm = engine.renderableManager
        for (e in a.renderableEntities) {
            val ri = rm.getInstance(e)
            rm.setCastShadows(ri, true)
            rm.setReceiveShadows(ri, true)
        }
        scene.addEntities(a.entities)
        return a
    }

    private fun destroyFurniture(a: FilamentAsset) {
        scene.removeEntities(a.entities)
        assetLoader.destroyAsset(a)
    }

    private fun removeFurniture(i: Int) = destroyFurniture(furniture.removeAt(i).second)

    /** Scala, rotazione e posizione del modello; il mobile selezionato si illumina di azzurro. */
    private fun place(a: FilamentAsset, p: Scene3D.PlacedFurniture) {
        val box = a.boundingBox
        val c = box.center
        val h = box.halfExtent
        // Specchiato: la larghezza del modello si ribalta (sinistra ↔ destra guardandolo dal davanti).
        val sx = (p.width * 0.01 / (2 * h[0].coerceAtLeast(1e-4f))).toFloat() * (if (p.mirrored) -1f else 1f)
        val sy = (p.height * 0.01 / (2 * h[1].coerceAtLeast(1e-4f))).toFloat()
        val sz = (p.depth * 0.01 / (2 * h[2].coerceAtLeast(1e-4f))).toFloat()
        // Rotazione attorno alla verticale: in pianta i gradi girano in senso orario (y verso il basso).
        val phi = Math.toRadians(-p.rotation)
        val cos = kotlin.math.cos(phi).toFloat()
        val sin = kotlin.math.sin(phi).toFloat()
        // Base del modello (centro in basso del suo ingombro) nell'origine, poi scala, rotazione e posto.
        val t0x = -c[0]
        val t0y = -c[1] + h[1]
        val t0z = -c[2]
        val m = floatArrayOf(
            cos * sx, 0f, -sin * sx, 0f,
            0f, sy, 0f, 0f,
            sin * sz, 0f, cos * sz, 0f,
            (p.base.x * 0.01).toFloat() + cos * sx * t0x + sin * sz * t0z,
            (p.base.y * 0.01).toFloat() + sy * t0y,
            (p.base.z * 0.01).toFloat() - sin * sx * t0x + cos * sz * t0z,
            1f,
        )
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(a.root), m)
        val glow = if (p.selected) floatArrayOf(0.02f, 0.09f, 0.24f) else floatArrayOf(0f, 0f, 0f)
        for (mi in a.instance.materialInstances) {
            if (mi.material.hasParameter("emissiveFactor")) mi.setParameter("emissiveFactor", glow[0], glow[1], glow[2])
        }
    }

    override fun onPause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onResume() {
        if (running) return
        running = true
        dirty = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun release() {
        onPause()
        uiHelper.detach()
        asset?.let {
            scene.removeEntities(it.entities)
            assetLoader.destroyAsset(it)
        }
        asset = null
        while (furniture.isNotEmpty()) removeFurniture(furniture.lastIndex)
        texturedSurfaces.destroy()
        resourceLoader.destroy()
        assetLoader.destroy()
        materials.destroyMaterials()
        materials.destroy()
        engine.destroyEntity(sun)
        engine.destroyEntity(fill)
        for (e in lampEntities + windowEntities) { engine.lightManager.destroy(e); EntityManager.get().destroy(e) }
        nightSky?.let(engine::destroySkybox)
        engine.destroyIndirectLight(indirect)
        engine.destroySkybox(skybox)
        for (env in envs.values) if (env != null) {
            env.sky?.let(engine::destroySkybox)
            engine.destroyTexture(env.reflections)
        }
        engine.destroyColorGrading(grading)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        EntityManager.get().destroy(sun)
        EntityManager.get().destroy(fill)
        engine.destroy()
        surfaceView = null
    }
}
