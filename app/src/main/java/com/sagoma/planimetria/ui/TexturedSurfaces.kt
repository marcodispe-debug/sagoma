package com.sagoma.planimetria.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.filament.Box
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.SurfaceOrientation
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.gltfio.MaterialProvider
import com.sagoma.planimetria.assets.AssetPaths
import com.sagoma.planimetria.assets.AssetStore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Superfici con un materiale fotografico (pavimenti, rivestimenti) disegnate da Filament con le tre mappe
 * del materiale: colore, rilievo (normali) e ruvidità. Le texture si caricano una volta sola e si
 * riusano; la geometria si ricostruisce a ogni cambio della scena (è poca).
 *
 * Le tre foto si leggono e si decodificano in un thread a parte: intanto la superficie si vede con la miniatura del materiale
 * (piccola, si decodifica subito), poi arriva il materiale vero e si chiama [onChange] per ridisegnare.
 */
internal class TexturedSurfaces(
    private val engine: Engine,
    private val scene: Scene,
    private val provider: MaterialProvider,
    private val assets: AssetStore,
    private val onChange: () -> Unit,
) {
    private class Mat(val instance: MaterialInstance, val textures: List<Texture>)
    private class Surface(val entity: Int, val vb: VertexBuffer, val ib: IndexBuffer)

    /** Materiali veri, pronti (null = non disponibile). */
    private val mats = HashMap<String, Mat?>()
    /** Materiali provvisori (la miniatura) per quelli che si stanno ancora decodificando. */
    private val drafts = HashMap<String, Mat?>()
    /** Materiali in decodifica. */
    private val pending = HashSet<String>()
    /** Materiali che non si sono potuti leggere: si riprova alla prossima [update] (il file può arrivare dal server). */
    private val failed = HashSet<String>()
    /** Le ultime superfici chieste: si ricostruiscono quando arriva un materiale vero. */
    private var last: Map<String, FloatArray> = emptyMap()
    private var destroyed = false
    private val main = Handler(Looper.getMainLooper())
    private val decoder: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "sagoma-materiali").apply { priority = Thread.NORM_PRIORITY - 1 } }
    }
    private val surfaces = mutableListOf<Surface>()
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT).apply { anisotropy = 4f }
    /** Texture bianca 1×1 per le mappe che il materiale non usa (occlusione, emissione…). */
    private val white: Texture by lazy {
        Texture.Builder().width(1).height(1).levels(1).format(Texture.InternalFormat.RGBA8).build(engine).also {
            val b = ByteBuffer.allocateDirect(4).put(byteArrayOf(-1, -1, -1, -1)).also { it.flip() }
            it.setImage(engine, 0, Texture.PixelBufferDescriptor(b, Texture.Format.RGBA, Texture.Type.UBYTE))
        }
    }

    /** Ci sono materiali mancanti: una nuova [update] li riprova anche se le superfici sono le stesse. */
    val hasMissing: Boolean get() = failed.isNotEmpty()

    /** Sostituisce le superfici disegnate con quelle della scena (8 float per vertice, in cm). */
    fun update(textured: Map<String, FloatArray>) {
        failed.clear()
        rebuild(textured)
    }

    private fun rebuild(textured: Map<String, FloatArray>) {
        clear()
        last = textured
        for ((id, data) in textured) {
            val n = data.size / 8
            if (n < 3) continue
            val mat = mats[id] ?: run {
                if (id !in failed && pending.add(id)) {
                    if (id !in drafts) drafts[id] = draft(id)
                    requestLoad(id)
                }
                drafts[id]
            } ?: continue
            surfaces += build(data, n, mat)
        }
    }

    /** Legge e decodifica le tre foto in [decoder]; le texture si creano poi sul thread principale (con il motore). */
    private fun requestLoad(id: String) {
        decoder.execute {
            val maps = runCatching { listOf(decode(id, "color"), decode(id, "normal"), decode(id, "orm")) }
                .onFailure { Log.w("Sagoma", "materiale $id non disponibile", it) }.getOrNull()
            main.post {
                if (destroyed) { maps?.forEach(Bitmap::recycle); return@post }
                pending.remove(id)
                val mat = maps?.let { load(id, it) }
                if (mat == null) {
                    // Il file non c'è ancora (catalogo scaricato a richiesta): resta la miniatura, si riprova alla prossima scena.
                    failed += id
                    return@post
                }
                mats[id] = mat
                // Le superfici si rifanno con il materiale vero, poi si butta quello provvisorio.
                val d = drafts.remove(id)
                rebuild(last)
                d?.let(::destroyMat)
                onChange()
            }
        }
    }

    private fun decode(id: String, suffix: String): Bitmap {
        val bytes = assets.peek(AssetPaths.materialMap(id, suffix)) ?: error("${AssetPaths.materialMap(id, suffix)} non trovato")
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: error("${AssetPaths.materialMap(id, suffix)} non si legge")
    }

    /**
     * Materiale provvisorio: solo il colore, dalla miniatura del materiale (che l'app ha sempre con sé). Se manca, null: la
     * superficie aspetta il materiale vero.
     */
    private fun draft(id: String): Mat? = runCatching {
        val bytes = assets.peek(AssetPaths.materialThumbnail(id)) ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: return null
        val color = texture(bmp, srgb = true)
        val key = MaterialProvider.MaterialKey().apply { hasBaseColorTexture = true; baseColorUV = 0 }
        val mi = provider.createMaterialInstance(key, IntArray(8), "sagoma_${id}_miniatura", null)!!
        neutral(mi)
        mi.setParameter("baseColorMap", color, sampler); mi.setParameter("baseColorIndex", 0)
        Mat(mi, listOf(color))
    }.onFailure { Log.w("Sagoma", "miniatura di $id non disponibile", it) }.getOrNull()

    private fun build(data: FloatArray, n: Int, mat: Mat): Surface {
        fun floats(count: Int) = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val pos = floats(n * 3)
        val nor = floats(n * 3)
        val uv = floats(n * 2)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until n) {
            val k = i * 8
            // cm → metri, come il resto della scena.
            val x = data[k] * 0.01f; val y = data[k + 1] * 0.01f; val z = data[k + 2] * 0.01f
            pos.put(x); pos.put(y); pos.put(z)
            nor.put(data[k + 3]); nor.put(data[k + 4]); nor.put(data[k + 5])
            uv.put(data[k + 6]); uv.put(data[k + 7])
            minX = minOf(minX, x); minY = minOf(minY, y); minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x); maxY = maxOf(maxY, y); maxZ = maxOf(maxZ, z)
        }
        pos.flip(); nor.flip(); uv.flip()
        val idx = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        for (i in 0 until n) idx.put(i)
        idx.flip()
        // Orientamento della superficie (normale + tangente), serve alla mappa del rilievo.
        val so = SurfaceOrientation.Builder().vertexCount(n).normals(nor).uvs(uv).positions(pos).triangleCount(n / 3).triangles_uint32(idx).build()
        val quats = floats(n * 4)
        so.getQuatsAsFloat(quats)
        so.destroy()
        quats.rewind(); nor.rewind(); uv.rewind(); pos.rewind(); idx.rewind()
        val colors = floats(n * 4)
        for (i in 0 until n * 4) colors.put(1f)
        colors.flip()

        val vb = VertexBuffer.Builder()
            .bufferCount(4)
            .vertexCount(n)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .attribute(VertexBuffer.VertexAttribute.UV1, 2, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 3, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .build(engine)
        vb.setBufferAt(engine, 0, pos)
        vb.setBufferAt(engine, 1, quats)
        vb.setBufferAt(engine, 2, uv)
        vb.setBufferAt(engine, 3, colors)
        val ib = IndexBuffer.Builder().indexCount(n).bufferType(IndexBuffer.Builder.IndexType.UINT).build(engine)
        ib.setBuffer(engine, idx)
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2, (maxX - minX) / 2 + 0.01f, (maxY - minY) / 2 + 0.01f, (maxZ - minZ) / 2 + 0.01f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .material(0, mat.instance)
            .castShadows(false)
            .receiveShadows(true)
            .build(engine, entity)
        scene.addEntity(entity)
        return Surface(entity, vb, ib)
    }

    /** Texture con le mipmap da una foto già decodificata (la foto si libera quando il motore l'ha copiata). */
    private fun texture(bmp: Bitmap, srgb: Boolean): Texture {
        val t = Texture.Builder()
            .width(bmp.width).height(bmp.height).levels(0xff)
            .format(if (srgb) Texture.InternalFormat.SRGB8_A8 else Texture.InternalFormat.RGBA8)
            .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
            .build(engine)
        TextureHelper.setBitmap(engine, t, 0, bmp)
        t.generateMipmaps(engine)
        return t
    }

    /** Materiale del motore con le tre mappe di `materials/<id>_*.jpg` (colore, rilievo, ruvidità), già decodificate. */
    private fun load(id: String, maps: List<Bitmap>): Mat? = runCatching {
        val color = texture(maps[0], srgb = true)
        val normal = texture(maps[1], srgb = false)
        val orm = texture(maps[2], srgb = false)
        val key = MaterialProvider.MaterialKey().apply {
            hasBaseColorTexture = true; baseColorUV = 0
            hasNormalTexture = true; normalUV = 0
            hasMetallicRoughnessTexture = true; metallicRoughnessUV = 0
        }
        val mi = provider.createMaterialInstance(key, IntArray(8), "sagoma_$id", null)!!
        neutral(mi)
        mi.setParameter("normalMap", normal, sampler); mi.setParameter("normalIndex", 0)
        mi.setParameter("metallicRoughnessMap", orm, sampler); mi.setParameter("metallicRoughnessIndex", 0)
        mi.setParameter("baseColorMap", color, sampler); mi.setParameter("baseColorIndex", 0)
        Mat(mi, listOf(color, normal, orm))
    }.onFailure { Log.w("Sagoma", "materiale $id non disponibile", it) }.getOrNull()

    /** Valori neutri per tutto ciò che il materiale non usa (le mappe vere si mettono dopo). */
    private fun neutral(mi: MaterialInstance) {
        for (p in mi.material.parameters) {
            when {
                p.type.name == "INT" && p.name.endsWith("Index") -> mi.setParameter(p.name, -1)
                p.type == Material.Parameter.Type.MAT3 && p.name.endsWith("UvMatrix") ->
                    mi.setParameter(p.name, MaterialInstance.FloatElement.MAT3, floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), 0, 1)
                p.type == Material.Parameter.Type.SAMPLER_2D -> mi.setParameter(p.name, white, sampler)
            }
        }
        fun has(name: String) = mi.material.hasParameter(name)
        mi.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, 1f, 1f, 1f, 1f)
        if (has("metallicFactor")) mi.setParameter("metallicFactor", 0f)
        if (has("roughnessFactor")) mi.setParameter("roughnessFactor", 1f)
        if (has("normalScale")) mi.setParameter("normalScale", 1f)
        if (has("emissiveFactor")) mi.setParameter("emissiveFactor", 0f, 0f, 0f)
    }

    private fun destroyMat(m: Mat) {
        engine.destroyMaterialInstance(m.instance)
        m.textures.forEach(engine::destroyTexture)
    }

    fun clear() {
        for (s in surfaces) {
            scene.removeEntity(s.entity)
            engine.destroyEntity(s.entity)
            engine.destroyVertexBuffer(s.vb)
            engine.destroyIndexBuffer(s.ib)
            EntityManager.get().destroy(s.entity)
        }
        surfaces.clear()
    }

    fun destroy() {
        clear()
        destroyed = true
        // Quelle in coda non partono; quella in corso finisce a vuoto (vedi requestLoad).
        decoder.shutdownNow()
        for (m in mats.values + drafts.values) if (m != null) destroyMat(m)
        mats.clear()
        drafts.clear()
        pending.clear()
        engine.destroyTexture(white)
    }
}
