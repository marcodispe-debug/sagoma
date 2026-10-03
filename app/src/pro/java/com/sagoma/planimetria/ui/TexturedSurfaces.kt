package com.sagoma.planimetria.ui

import android.graphics.BitmapFactory
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

/**
 * Superfici con un materiale fotografico (pavimenti, rivestimenti) disegnate da Filament con le tre mappe
 * del materiale: colore, rilievo (normali) e ruvidità. Le texture si caricano una volta sola e si
 * riusano; la geometria si ricostruisce a ogni cambio della scena (è poca).
 */
internal class TexturedSurfaces(
    private val engine: Engine,
    private val scene: Scene,
    private val provider: MaterialProvider,
    private val assets: AssetStore,
) {
    private class Mat(val instance: MaterialInstance, val textures: List<Texture>)
    private class Surface(val entity: Int, val vb: VertexBuffer, val ib: IndexBuffer)

    private val mats = HashMap<String, Mat?>()
    private val surfaces = mutableListOf<Surface>()
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT).apply { anisotropy = 4f }
    /** Texture bianca 1×1 per le mappe che il materiale non usa (occlusione, emissione…). */
    private val white: Texture by lazy {
        Texture.Builder().width(1).height(1).levels(1).format(Texture.InternalFormat.RGBA8).build(engine).also {
            val b = ByteBuffer.allocateDirect(4).put(byteArrayOf(-1, -1, -1, -1)).also { it.flip() }
            it.setImage(engine, 0, Texture.PixelBufferDescriptor(b, Texture.Format.RGBA, Texture.Type.UBYTE))
        }
    }

    /** Sostituisce le superfici disegnate con quelle della scena (8 float per vertice, in cm). */
    fun update(textured: Map<String, FloatArray>) {
        clear()
        for ((id, data) in textured) {
            val n = data.size / 8
            if (n < 3) continue
            val mat = mats.getOrPut(id) { load(id) } ?: continue
            surfaces += build(data, n, mat)
        }
    }

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

    /** Materiale del motore con le tre mappe di `materials/<id>_*.jpg`. */
    private fun load(id: String): Mat? = runCatching {
        fun texture(suffix: String, srgb: Boolean): Texture {
            val bytes = assets.peek(AssetPaths.materialMap(id, suffix)) ?: error("${AssetPaths.materialMap(id, suffix)} non trovato")
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 })!!
            val t = Texture.Builder()
                .width(bmp.width).height(bmp.height).levels(0xff)
                .format(if (srgb) Texture.InternalFormat.SRGB8_A8 else Texture.InternalFormat.RGBA8)
                .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
                .build(engine)
            TextureHelper.setBitmap(engine, t, 0, bmp)
            t.generateMipmaps(engine)
            return t
        }
        val color = texture("color", srgb = true)
        val normal = texture("normal", srgb = false)
        val orm = texture("orm", srgb = false)
        val key = MaterialProvider.MaterialKey().apply {
            hasBaseColorTexture = true; baseColorUV = 0
            hasNormalTexture = true; normalUV = 0
            hasMetallicRoughnessTexture = true; metallicRoughnessUV = 0
        }
        val mi = provider.createMaterialInstance(key, IntArray(8), "sagoma_$id", null)!!
        // Valori neutri per tutto ciò che il materiale non usa, poi le nostre mappe.
        for (p in mi.material.parameters) {
            when {
                p.type.name == "INT" && p.name.endsWith("Index") -> mi.setParameter(p.name, -1)
                p.type == Material.Parameter.Type.MAT3 && p.name.endsWith("UvMatrix") ->
                    mi.setParameter(p.name, MaterialInstance.FloatElement.MAT3, floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), 0, 1)
                p.type == Material.Parameter.Type.SAMPLER_2D -> mi.setParameter(p.name, white, sampler)
            }
        }
        fun has(name: String) = mi.material.hasParameter(name)
        mi.setParameter("baseColorMap", color, sampler); mi.setParameter("baseColorIndex", 0)
        mi.setParameter("normalMap", normal, sampler); mi.setParameter("normalIndex", 0)
        mi.setParameter("metallicRoughnessMap", orm, sampler); mi.setParameter("metallicRoughnessIndex", 0)
        mi.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, 1f, 1f, 1f, 1f)
        if (has("metallicFactor")) mi.setParameter("metallicFactor", 0f)
        if (has("roughnessFactor")) mi.setParameter("roughnessFactor", 1f)
        if (has("normalScale")) mi.setParameter("normalScale", 1f)
        if (has("emissiveFactor")) mi.setParameter("emissiveFactor", 0f, 0f, 0f)
        Mat(mi, listOf(color, normal, orm))
    }.onFailure { Log.w("Sagoma", "materiale $id non disponibile", it) }.getOrNull()

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
        for (m in mats.values) if (m != null) {
            engine.destroyMaterialInstance(m.instance)
            m.textures.forEach(engine::destroyTexture)
        }
        mats.clear()
        engine.destroyTexture(white)
    }
}
