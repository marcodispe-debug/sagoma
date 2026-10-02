package com.sagoma.planimetria.desktop

import com.sagoma.planimetria.ui.GltfModel
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.EXTTextureFilterAnisotropic
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL30.*
import kotlin.math.max

/**
 * Risorse sulla scheda grafica (solo dal thread OpenGL): texture decodificate con Skia, con mipmap e filtro
 * anisotropico, e modelli glTF degli arredi caricati una volta sola e riusati.
 */
internal class GlResources(private val readAsset: (String) -> ByteArray?) {
    private val textures = HashMap<String, Int>()
    private val models = HashMap<String, GpuModel?>()

    /** Texture da un'immagine codificata (JPEG/PNG), ridotta a `maxSide`; 0 se non si legge. */
    fun texture(key: String, bytes: () -> ByteArray?, maxSide: Int = 2048): Int = textures.getOrPut(key) {
        val data = bytes() ?: return@getOrPut 0
        runCatching { upload(Image.makeFromEncoded(data), maxSide) }.getOrElse { 0 }
    }

    /** Texture di un materiale fotografico (`kind`: color, normal, orm). */
    fun material(id: String, kind: String): Int = texture("mat:$id:$kind", { readAsset("materials/${id}_$kind.jpg") })

    private fun upload(src: Image, maxSide: Int): Int {
        val k = minOf(1f, maxSide.toFloat() / max(src.width, src.height))
        val w = (src.width * k).toInt().coerceAtLeast(1)
        val h = (src.height * k).toInt().coerceAtLeast(1)
        val img = if (k < 1f) {
            val s = Surface.makeRasterN32Premul(w, h)
            s.canvas.drawImageRect(src, org.jetbrains.skia.Rect.makeWH(src.width.toFloat(), src.height.toFloat()), org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()), SamplingMode.LINEAR, null, true)
            s.makeImageSnapshot()
        } else src
        val bmp = Bitmap()
        bmp.allocPixels(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL))
        img.readPixels(bmp)
        val px = bmp.readPixels() ?: return 0
        val buf = BufferUtils.createByteBuffer(px.size).put(px).flip()
        val t = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, t)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf)
        glGenerateMipmap(GL_TEXTURE_2D)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            glTexParameterf(GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, 8f)
        }
        return t
    }

    /** Parte di un arredo sulla GPU: vertici (posizione, normale, uv) e indici, con il suo materiale. */
    class GpuPart(val vbo: Int, val ibo: Int, val count: Int, val material: GltfModel.Material, val albedo: Int, val normal: Int, val orm: Int, val emissive: Int)
    class GpuModel(val parts: List<GpuPart>, val min: FloatArray, val max: FloatArray)

    /** Modello di un arredo (null se il file manca o non si legge). */
    fun model(name: String): GpuModel? = models.getOrPut(name) {
        val bytes = readAsset("furniture/$name.glb") ?: return@getOrPut null
        val m = runCatching { GltfModel.parse(bytes) }.getOrNull() ?: return@getOrPut null
        val parts = m.parts.map { p ->
            val n = p.positions.size / 3
            val data = FloatArray(n * 8)
            for (i in 0 until n) {
                for (k in 0..2) { data[i * 8 + k] = p.positions[i * 3 + k]; data[i * 8 + 3 + k] = p.normals[i * 3 + k] }
                data[i * 8 + 6] = p.uvs[i * 2]; data[i * 8 + 7] = p.uvs[i * 2 + 1]
            }
            val vbo = glGenBuffers()
            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW)
            val ibo = glGenBuffers()
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, p.indices, GL_STATIC_DRAW)
            fun tex(i: Int?) = if (i == null) 0 else texture("$name:$i", { m.images.getOrNull(i) }, maxSide = 1024)
            val mat = p.material
            GpuPart(vbo, ibo, p.indices.size, mat, tex(mat.baseColorImage), tex(mat.normalImage), tex(mat.metallicRoughnessImage), tex(mat.emissiveImage))
        }
        GpuModel(parts, m.min, m.max)
    }
}
