package com.sagoma.planimetria.desktop.video

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import java.io.File
import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Registra un video tutorial dall'interfaccia vera di sagoma: muove il puntatore, clicca, scrive nei campi e
 * sopra ogni fotogramma disegna il riquadro arancione su cosa si sta usando, la spiegazione del passo e il puntatore.
 * I fotogrammi vanno a ffmpeg (MP4, 25 al secondo).
 */
@OptIn(ExperimentalComposeUiApi::class)
class Regista(private val scene: ImageComposeScene, private val width: Int, private val appHeight: Int, private val fps: Int = 25) {
    /** Altezza del video: l'app e, sotto, la fascia con la spiegazione (così non copre mai l'app). */
    private val height = appHeight + BAND
    private var ffmpeg: Process? = null
    private var pipe: OutputStream? = null
    private val surface = Surface.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
    private val frameNanos = 1_000_000_000L / fps
    private var t = 0L
    var frames = 0
        private set

    /** Inizia a registrare un video in `out` (MP4); finisce con [stop]. */
    fun start(out: File) {
        check(ffmpeg == null) { "un video è già in registrazione" }
        val p = ProcessBuilder(
            "ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", "${width}x$height", "-r", "$fps", "-i", "-",
            "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", out.absolutePath,
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start()
        ffmpeg = p
        pipe = p.outputStream.buffered(1 shl 20)
    }

    fun stop() {
        pipe?.close()
        check(ffmpeg?.waitFor() == 0) { "ffmpeg non è riuscito a creare il video" }
        ffmpeg = null; pipe = null
    }

    private fun emit() {
        val bytes = surface.makeImageSnapshot().use { img -> img.peekPixels()!!.buffer.bytes }
        pipe?.write(bytes)
        frames++
    }

    /** Dove sta il puntatore (coordinate della finestra). */
    var cursor = Offset(width * 0.55f, appHeight * 0.6f)
    /** Riquadro arancione su ciò che si sta usando (null = nessuno). */
    var highlight: Rect? = null
    /** Spiegazione del passo, in basso (o in alto se il riquadro è in basso). */
    var caption: String = ""
    /** Riga piccola sopra la spiegazione, per esempio "Quadrato · passo 2 di 6". */
    var stepLabel: String = ""
    /** Titolo in alto a sinistra per tutto il capitolo (es. "Modo 1 · Quadrato"). */
    var chapter: String = ""
    /** Piantina in piccolo a destra nella fascia (null = nessuna). */
    var plan: Image? = null
    /** La stanza su cui si sta lavorando, in pixel della [plan], colorata nella piantina piccola. */
    var planRoom: List<Pair<Float, Float>>? = null
    private var clickAge = 99f
    private var clickAt = Offset.Zero
    private var highlightAge = 0f
    private var lastHighlight: Rect? = null

    private val typeface: Typeface = FontMgr.default.matchFamilyStyle("DejaVu Sans", FontStyle.NORMAL) ?: FontMgr.default.legacyMakeTypeface("", FontStyle.NORMAL)!!
    private val bold: Typeface = FontMgr.default.matchFamilyStyle("DejaVu Sans", FontStyle.BOLD) ?: typeface

    // ---------- Fotogrammi ----------

    /** Un fotogramma: l'app che va avanti di 1/25 di secondo, con sopra riquadro, spiegazione e puntatore. */
    fun frame() {
        t += frameNanos
        val app = scene.render(t)
        val c = surface.canvas
        c.drawImage(app, 0f, 0f)
        app.close()
        if (highlight != lastHighlight) { highlightAge = 0f; lastHighlight = highlight }
        highlight?.let { drawHighlight(it) }
        drawCursor()
        drawBand()
        emit()
        clickAge += 1f / fps
        highlightAge += 1f / fps
        // Le animazioni dell'app usano il tempo passato a render, ma le attese (delay) usano l'orologio vero.
        Thread.sleep(4)
    }

    fun wait(seconds: Double) = repeat((seconds * fps).toInt().coerceAtLeast(1)) { frame() }

    /** Porta il puntatore su `target` con un movimento morbido. */
    fun moveTo(target: Offset, seconds: Double = 0.9) {
        val from = cursor
        val n = (seconds * fps).toInt().coerceAtLeast(1)
        for (i in 1..n) {
            val x = i.toFloat() / n
            val e = x * x * (3 - 2 * x)
            cursor = Offset(from.x + (target.x - from.x) * e, from.y + (target.y - from.y) * e)
            scene.sendPointerEvent(PointerEventType.Move, cursor)
            frame()
        }
    }

    /**
     * Porta il puntatore su un bersaglio che si può spostare mentre il puntatore si muove (la pianta che segue il mouse
     * durante «Muro per muro»): `target` si rilegge a ogni fotogramma, e alla fine si aspetta che stia fermo.
     */
    fun follow(target: () -> Offset, seconds: Double = 1.2) {
        val from = cursor
        val n = (seconds * fps).toInt().coerceAtLeast(1)
        for (i in 1..n) {
            val x = i.toFloat() / n
            val e = x * x * (3 - 2 * x)
            val to = target()
            cursor = Offset(from.x + (to.x - from.x) * e, from.y + (to.y - from.y) * e)
            scene.sendPointerEvent(PointerEventType.Move, cursor)
            frame()
        }
        repeat(40) {
            val to = target()
            if ((to - cursor).getDistance() < 0.3f) return
            cursor = to
            scene.sendPointerEvent(PointerEventType.Move, cursor)
            frame()
        }
    }

    /** Clic del tasto sinistro dove sta il puntatore, con l'onda del clic. */
    fun click() {
        scene.sendPointerEvent(PointerEventType.Press, cursor)
        clickAt = cursor; clickAge = 0f
        frame(); frame()
        scene.sendPointerEvent(PointerEventType.Release, cursor)
        wait(0.5)
    }

    /** Muove il puntatore al centro di `r`, lo evidenzia mentre ci arriva e clicca. */
    fun clickOn(r: Rect, explain: String? = null, readSeconds: Double = 2.5) {
        highlight = r
        explain?.let { caption = it }
        moveTo(Offset(r.left + r.width / 2, r.top + r.height / 2))
        wait(readSeconds)
        click()
        // Dopo il clic lo schermo cambia: il riquadro non deve restare su qualcos'altro.
        highlight = null
    }

    /** Rotellina del mouse dove sta il puntatore (`dy` > 0 = scorre in giù), a piccoli scatti. */
    fun scroll(dy: Float, steps: Int = 6) {
        repeat(steps) {
            scene.sendPointerEvent(PointerEventType.Scroll, cursor, scrollDelta = Offset(0f, dy / steps))
            frame()
        }
        wait(0.3)
    }

    /** Scrive `text` nel campo `node` una lettera alla volta (come se lo si scrivesse con la tastiera). */
    fun type(node: SemanticsNode, text: String) {
        val set = node.config.getOrNull(SemanticsActions.SetText)?.action ?: error("il campo non accetta testo")
        set(AnnotatedString(""))
        wait(0.2)
        for (i in 1..text.length) {
            set(AnnotatedString(text.substring(0, i)))
            wait(0.18)
        }
    }

    /** Fotogramma attuale come PNG (per controllare a occhio). */
    fun snapshot(file: File) {
        surface.makeImageSnapshot().use { file.writeBytes(it.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes) }
    }

    // ---------- Cartelli ----------

    /** Cartello con un'immagine grande (la piantina) sotto il titolo, e una spiegazione nella fascia. */
    fun imageCard(title: String, image: Image, explain: String, seconds: Double) {
        val n = (seconds * fps).toInt()
        for (i in 0 until n) {
            val c = surface.canvas
            c.clear(BRAND_DARK)
            val fade = min(1f, min(i, n - i).toFloat() / (fps * 0.4f))
            centerText(title, 70f, Font(bold, 40f), Paint().apply { color = withAlpha(WHITE, fade) })
            val maxW = width - 120f
            val maxH = appHeight - 130f
            val k = min(maxW / image.width, maxH / image.height)
            val w = image.width * k
            val h = image.height * k
            val x = (width - w) / 2
            val y = 105f
            c.drawRRect(RRect.makeXYWH(x - 10f, y - 10f, w + 20f, h + 20f, 14f), Paint().apply { color = withAlpha(WHITE, fade); isAntiAlias = true })
            c.drawImageRect(image, Rect(x, y, x + w, y + h), Paint().apply { alpha = (255 * fade).toInt() })
            val saved = Triple(caption, plan, highlight)
            caption = explain; plan = null; highlight = null
            drawBand()
            caption = saved.first; plan = saved.second; highlight = saved.third
            emit()
            Thread.sleep(4)
        }
    }

    /** Cartello a tutto schermo (titolo grande, sottotitolo) per `seconds`. */
    fun titleCard(title: String, subtitle: String, seconds: Double = 3.5) {
        val n = (seconds * fps).toInt()
        for (i in 0 until n) {
            val c = surface.canvas
            c.clear(BRAND_DARK)
            val fade = min(1f, min(i, n - i).toFloat() / (fps * 0.4f))
            val tp = Paint().apply { color = withAlpha(WHITE, fade) }
            centerText(title, height / 2f - 20f, Font(bold, 54f), tp)
            centerText(subtitle, height / 2f + 50f, Font(typeface, 28f), Paint().apply { color = withAlpha(ORANGE, fade) })
            // Barra arancione sotto il titolo.
            c.drawRect(Rect.makeXYWH(width / 2f - 60f, height / 2f + 5f, 120f, 5f), Paint().apply { color = withAlpha(ORANGE, fade) })
            emit()
            // L'app intanto resta ferma ma viva (le attese con l'orologio vero vanno avanti).
            Thread.sleep(4)
        }
    }

    // ---------- Disegno sopra l'app ----------

    private fun drawHighlight(r0: Rect) {
        val pad = 8f
        val r = Rect(r0.left - pad, r0.top - pad, r0.right + pad, r0.bottom + pad)
        val c = surface.canvas
        val appear = min(1f, highlightAge / 0.35f)
        // Tutto il resto un po' più scuro: l'occhio va dove c'è il riquadro.
        val shade = PathBuilder(PathFillMode.EVEN_ODD)
            .addRect(Rect.makeWH(width.toFloat(), appHeight.toFloat()))
            .addRRect(RRect.makeLTRB(r.left, r.top, r.right, r.bottom, 12f))
            .detach()
        c.drawPath(shade, Paint().apply { color = withAlpha(0xFF000000.toInt(), 0.38f * appear) })
        val pulse = (0.5f + 0.5f * sin(highlightAge * 2 * PI.toFloat() / 1.2f))
        val grow = 4f * pulse
        c.drawRRect(
            RRect.makeLTRB(r.left - grow, r.top - grow, r.right + grow, r.bottom + grow, 12f + grow),
            Paint().apply { color = withAlpha(ORANGE, appear * (0.35f + 0.25f * (1 - pulse))); mode = PaintMode.STROKE; strokeWidth = 10f; isAntiAlias = true },
        )
        c.drawRRect(
            RRect.makeLTRB(r.left, r.top, r.right, r.bottom, 12f),
            Paint().apply { color = withAlpha(ORANGE, appear); mode = PaintMode.STROKE; strokeWidth = 4f; isAntiAlias = true },
        )
    }

    /** Fascia sotto l'app: modo e passo in arancione, poi la spiegazione in bianco. */
    private fun drawBand() {
        val c = surface.canvas
        c.drawRect(Rect.makeXYWH(0f, appHeight.toFloat(), width.toFloat(), BAND.toFloat()), Paint().apply { color = BRAND_DARK })
        c.drawRect(Rect.makeXYWH(0f, appHeight.toFloat(), width.toFloat(), 4f), Paint().apply { color = ORANGE })
        val head = Font(bold, 21f)
        var x = 40f
        val top = appHeight + 40f
        if (chapter.isNotEmpty()) {
            c.drawString(chapter, x, top, head, Paint().apply { color = ORANGE; isAntiAlias = true })
            x += head.measureTextWidth(chapter) + 18f
        }
        if (stepLabel.isNotEmpty()) c.drawString(stepLabel, x, top, Font(typeface, 21f), Paint().apply { color = 0xFFB8C4D0.toInt(); isAntiAlias = true })
        var textW = width - 80f
        plan?.let { p ->
            val h = BAND - 34f
            val w = p.width * h / p.height
            val x = width - w - 28f
            val y = appHeight + 17f
            c.drawRRect(RRect.makeXYWH(x - 6f, y - 6f, w + 12f, h + 12f, 10f), Paint().apply { color = WHITE; isAntiAlias = true })
            c.drawImageRect(p, Rect(x, y, x + w, y + h))
            planRoom?.let { pts ->
                val k = w / p.width
                val path = PathBuilder(PathFillMode.WINDING).apply {
                    pts.forEachIndexed { i, (px, py) -> if (i == 0) moveTo(x + px * k, y + py * k) else lineTo(x + px * k, y + py * k) }
                    closePath()
                }.detach()
                c.drawPath(path, Paint().apply { color = withAlpha(ORANGE, 0.45f); isAntiAlias = true })
                c.drawPath(path, Paint().apply { color = ORANGE; mode = PaintMode.STROKE; strokeWidth = 3f; isAntiAlias = true })
            }
            textW -= w + 40f
        }
        val font = Font(typeface, 27f)
        var y = top + 44f
        for (line in wrap(caption, font, textW).take(3)) {
            c.drawString(line, 40f, y, font, Paint().apply { color = WHITE; isAntiAlias = true })
            y += 36f
        }
    }

    private fun drawCursor() {
        val c = surface.canvas
        if (clickAge < 0.6f) {
            val k = clickAge / 0.6f
            c.drawCircle(clickAt.x, clickAt.y, 10f + 34f * k, Paint().apply { color = withAlpha(ORANGE, 0.9f * (1 - k)); mode = PaintMode.STROKE; strokeWidth = 5f; isAntiAlias = true })
            c.drawCircle(clickAt.x, clickAt.y, 12f, Paint().apply { color = withAlpha(ORANGE, 0.5f * (1 - k)); isAntiAlias = true })
        }
        // Freccia del mouse (come quella di Windows), un po' più grande del normale per vederla bene nel video.
        val s = 1.5f
        val pts = listOf(0f to 0f, 0f to 17f, 4f to 13f, 7f to 20f, 10f to 19f, 7f to 12f, 12.5f to 12f)
        val path = PathBuilder(PathFillMode.WINDING).apply {
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(cursor.x + x * s, cursor.y + y * s) else lineTo(cursor.x + x * s, cursor.y + y * s) }
            closePath()
        }.detach()
        c.drawPath(path, Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true })
        c.drawPath(path, Paint().apply { color = 0xFF000000.toInt(); mode = PaintMode.STROKE; strokeWidth = 1.6f; isAntiAlias = true })
    }

    private fun centerText(s: String, baseline: Float, font: Font, paint: Paint) {
        paint.isAntiAlias = true
        val w = font.measureTextWidth(s)
        surface.canvas.drawString(s, (width - w) / 2f, baseline, font, paint)
    }

    private fun wrap(text: String, font: Font, maxW: Float): List<String> = text.split('\n').flatMap { para ->
        val out = mutableListOf<String>()
        var line = ""
        for (word in para.split(' ')) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (font.measureTextWidth(next) > maxW && line.isNotEmpty()) { out += line; line = word } else line = next
        }
        out += line
        out
    }

    // ---------- Trovare le cose sullo schermo ----------

    /** Tutti i nodi visibili (anche quelli delle finestre di dialogo), uniti come li legge un lettore di schermo. */
    fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { owner ->
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { out += n; n.children.forEach(::walk) }
        walk(owner.rootSemanticsNode)
        out
    }

    fun texts(n: SemanticsNode): List<String> =
        (n.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) +
            listOfNotNull(n.config.getOrNull(SemanticsProperties.EditableText)?.text) +
            (n.config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList())

    /** Il nodo cliccabile (o il più piccolo) che contiene il testo `label`. */
    fun node(label: String, exact: Boolean = false): SemanticsNode {
        val all = nodes().filter { n -> texts(n).any { if (exact) it == label else it.contains(label) } }
        require(all.isNotEmpty()) { "non trovo «$label» sullo schermo. Testi visibili: " + nodes().flatMap(::texts).distinct() }
        return all.firstOrNull { it.config.contains(SemanticsActions.OnClick) || it.config.contains(SemanticsActions.SetText) }
            ?: all.minBy { it.boundsInWindow.width * it.boundsInWindow.height }
    }

    fun rect(label: String, exact: Boolean = false): Rect = node(label, exact).boundsInWindow.let { Rect(it.left, it.top, it.right, it.bottom) }

    /** Il campo di testo con l'etichetta `label`. */
    fun field(label: String): SemanticsNode = nodes().first { n -> n.config.contains(SemanticsActions.SetText) && texts(n).any { it.contains(label) } }

    fun rectOf(n: SemanticsNode): Rect = n.boundsInWindow.let { Rect(it.left, it.top, it.right, it.bottom) }

    /** Elenco dei testi sullo schermo con la loro posizione (per scrivere le scene). */
    fun dump(): String = nodes().filter { texts(it).isNotEmpty() }.joinToString("\n") { n ->
        val b = n.boundsInWindow
        val flags = listOfNotNull("clic".takeIf { n.config.contains(SemanticsActions.OnClick) }, "testo".takeIf { n.config.contains(SemanticsActions.SetText) })
        "${texts(n)} @ ${b.left.toInt()},${b.top.toInt()} ${b.width.toInt()}x${b.height.toInt()} $flags"
    }

    companion object {
        /** Altezza della fascia della spiegazione sotto l'app. */
        const val BAND = 180
        val ORANGE = 0xFFFF8A00.toInt()
        val BRAND_DARK = 0xFF16222F.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
        fun withAlpha(color: Int, a: Float): Int = (((color ushr 24) * a.coerceIn(0f, 1f)).toInt() shl 24) or (color and 0xFFFFFF)
    }
}

private inline fun <R> Image.use(block: (Image) -> R): R = try { block(this) } finally { close() }
