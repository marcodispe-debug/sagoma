package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.AnnotatedString
import com.sagoma.planimetria.editor.Course
import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.GuideField
import com.sagoma.planimetria.editor.GuideTarget
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.interior
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.testi.T
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.sagoma.planimetria.editor.GuidePlan
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.Opening
import com.sagoma.planimetria.model.OpeningKind
import com.sagoma.planimetria.model.Room
import com.sagoma.planimetria.persistence.FileLanguageStore
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.persistence.LanguageChoice
import com.sagoma.planimetria.ui.PickedFile
import com.sagoma.planimetria.ui.Platform
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Surface
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * Controllo dei testi (non è una prova da tenere: serve a trovare i problemi). Un "robot" apre l'app vera in ogni lingua,
 * al formato del computer e del telefono, e clicca i comandi uno per uno (fino a 3 clic di fila, solo i comandi comparsi
 * dopo l'ultimo clic). In ogni schermata guarda ogni scritta: tagliata coi puntini, che non ci sta in altezza, parola
 * spezzata a metà, o nascosta in parte dal riquadro. Risultati in build/controllo/ (elenco.tsv e foto con i riquadri rossi).
 *
 * Si attiva con -Dsagoma.controllo=true (altrimenti non fa niente); -Dsagoma.controllo.lingue=it,en e
 * -Dsagoma.controllo.formati=pc,tel per restringere.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ControlloTestiTest {
    private class Formato(val nome: String, val w: Int, val h: Int, val density: Float, val guida: Boolean)

    private val formati = listOf(
        Formato("pc", 1400, 900, 1f, true),
        Formato("tel", 1080, 2340, 2.625f, false),
        Formato("tel-orizz", 2340, 1080, 2.625f, false),
    )
    private val lingue = listOf(
        "it" to LanguageChoice.Italian, "en" to LanguageChoice.English,
        "fr" to LanguageChoice.French, "es" to LanguageChoice.Spanish, "de" to LanguageChoice.German,
    )

    private lateinit var scene: ImageComposeScene
    private var t = 0L

    private fun frames(n: Int) = repeat(n) {
        t += 40_000_000L
        scene.render(t).close()
        Thread.sleep(4)
    }

    private fun walk(root: SemanticsNode): List<SemanticsNode> {
        val all = mutableListOf<SemanticsNode>()
        fun go(n: SemanticsNode) { all += n; n.children.forEach(::go) }
        go(root)
        return all
    }

    private fun unmerged() = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
    private fun merged() = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }

    private fun texts(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()

    private fun key(n: SemanticsNode): String =
        (texts(n) + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" · ").replace('\n', ' ').trim()

    /** Comandi cliccabili visibili, ognuno col suo nome (più l'indice se il nome si ripete). */
    private fun clickables(): List<Pair<String, SemanticsNode>> {
        val seen = mutableMapOf<String, Int>()
        return merged().filter { n ->
            n.config.getOrNull(SemanticsActions.OnClick)?.action != null &&
                n.config.getOrNull(SemanticsProperties.Disabled) == null &&
                !n.boundsInWindow.isEmpty
        }.mapNotNull { n ->
            val k = key(n).ifEmpty { n.config.getOrNull(SemanticsProperties.TestTag)?.let { "#$it" } ?: return@mapNotNull null }
            val i = seen.merge(k, 1, Int::plus)!!
            (if (i == 1) k else "$k [$i]") to n
        }
    }

    private class Problema(val tipo: String, val testo: String, val box: Rect)

    private fun isLetter(c: Char) = c.isLetter()

    /** Le scritte con un problema nella schermata di adesso. */
    private fun problemi(): List<Problema> {
        val out = mutableListOf<Problema>()
        val all = unmerged()
        for (n in all) {
            if (n.config.contains(SemanticsActions.SetText)) continue
            val get = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: continue
            val list = mutableListOf<TextLayoutResult>()
            runCatching { get(list) }
            val r = list.firstOrNull() ?: continue
            val s = r.layoutInput.text.text
            if (s.isBlank()) continue
            val box = n.boundsInWindow
            if (box.isEmpty) continue
            if (System.getenv("SAGOMA_DEBUG") != null && s in System.getenv("SAGOMA_DEBUG")) println("DBG «$s» right=${(0 until r.lineCount).map { r.getLineRight(it) }} left=${(0 until r.lineCount).map { r.getLineLeft(it) }} align=${r.layoutInput.style.textAlign} size=${r.size} c=${r.layoutInput.constraints} mpW=${r.multiParagraph.width} mpH=${r.multiParagraph.height} ovW=${r.didOverflowWidth} ovH=${r.didOverflowHeight} soft=${r.layoutInput.softWrap} max=${r.layoutInput.maxLines} ovf=${r.layoutInput.overflow} node=${n.size} box=$box")
            val ellipsis = (0 until r.lineCount).any { r.isLineEllipsized(it) }
            val lastBottom = if (r.lineCount > 0) r.getLineBottom(r.lineCount - 1) else 0f
            val widest = (0 until r.lineCount).maxOfOrNull { r.getLineRight(it) - r.getLineLeft(it) } ?: 0f
            when {
                ellipsis -> out += Problema("puntini", s, box)
                r.multiParagraph.didExceedMaxLines -> out += Problema("righe in più nascoste", s, box)
                lastBottom > r.size.height + 2 -> out += Problema("non ci sta in altezza", s, box)
                widest > r.size.width + 2 -> out += Problema("non ci sta in larghezza", s, box)
            }
            for (line in 0 until r.lineCount - 1) {
                val end = r.getLineEnd(line, visibleEnd = false)
                if (end in 1 until s.length && isLetter(s[end - 1]) && isLetter(s[end])) {
                    out += Problema("parola spezzata («${s.substring(maxOf(0, end - 8), minOf(s.length, end + 8)).replace('\n', ' ')}»)", s, box)
                    break
                }
            }
            // Riquadro più stretto della scritta (e non dentro una lista che scorre): una parte non si vede.
            val inScroll = generateSequence(n.parent) { it.parent }.any {
                it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) || it.config.contains(SemanticsProperties.VerticalScrollAxisRange)
            }
            val window = Rect(0f, 0f, scene.let { _ -> curW.toFloat() }, curH.toFloat())
            val full = Rect(box.left, box.top, box.left + n.size.width, box.top + n.size.height)
            if (!inScroll && !ellipsis && full.overlaps(window) && (n.size.width - box.width > 3 || n.size.height - box.height > 3) &&
                box.left > 0 && box.top > 0 && box.right < curW && box.bottom < curH
            ) out += Problema("nascosta in parte dal riquadro", s, box)
        }
        return out
    }

    private var curW = 0
    private var curH = 0

    private fun foto(file: File, boxes: List<Rect>) {
        t += 40_000_000L
        val img = scene.render(t)
        val surface = Surface.makeRasterN32Premul(img.width, img.height)
        surface.canvas.drawImage(img, 0f, 0f)
        val paint = Paint().apply { color = 0xFFFF0000.toInt(); mode = PaintMode.STROKE; strokeWidth = 3f }
        for (b in boxes) surface.canvas.drawRect(org.jetbrains.skia.Rect.makeLTRB(b.left - 3, b.top - 3, b.right + 3, b.bottom + 3), paint)
        file.parentFile.mkdirs()
        file.writeBytes(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        img.close()
    }

    /** Piattaforma delle prove: niente finestre del sistema (apri/salva file), niente appunti. */
    private fun platformOf(dir: File, guida: Boolean, assets: File?): Platform {
        val desk = DesktopPlatform(projects = FileProjectRepository(dir), tips = FileTipStore(dir), language = FileLanguageStore(dir), assets = assets)
        return object : Platform by desk {
            override val interactiveGuide: Boolean get() = guida && desk.interactiveGuide
            override fun openFile(mimeTypes: List<String>, onResult: (PickedFile?) -> Unit) = onResult(null)
            override fun saveFile(suggestedName: String, mimeType: String, bytes: ByteArray, onDone: (Boolean) -> Unit) = onDone(false)
            override fun shareText(subject: String, text: String) {}
        }
    }

    @Test
    fun controllo() {
        if (System.getenv("SAGOMA_CONTROLLO") != "true") return
        val soloLingue = System.getenv("SAGOMA_LINGUE")?.split(',')
        val soloFormati = System.getenv("SAGOMA_FORMATI")?.split(',')
        val maxStati = System.getenv("SAGOMA_STATI")?.toInt() ?: 260
        val assets = File("../catalogo/src/main/assets").takeIf { it.isDirectory }
        val out = File("build/controllo")
        for (f in formati.filter { soloFormati == null || it.nome in soloFormati }) for ((codice, choice) in lingue.filter { soloLingue == null || it.first in soloLingue }) {
            val conf = "$codice-${f.nome}" + (System.getenv("SAGOMA_NOME")?.let { "-$it" } ?: "")
            val dirOut = File(out, conf).also { it.deleteRecursively(); it.mkdirs() }
            val elenco = File(dirOut, "elenco.tsv")
            elenco.writeText("percorso\ttipo\ttesto\tfoto\n")
            val visti = mutableSetOf<String>()
            val segnalati = mutableSetOf<String>()
            var foto = 0
            // Coda: percorsi di clic da rifare dall'inizio, con i comandi che c'erano prima dell'ultimo clic.
            val coda = ArrayDeque<Pair<List<String>, Set<String>>>()
            val inizi = System.getenv("SAGOMA_INIZI")?.split(";;")?.map { it.split(" › ").filter(String::isNotEmpty) } ?: listOf(emptyList())
            for (i in inizi) coda += i to emptySet()
            val profondita = System.getenv("SAGOMA_PROFONDITA")?.toInt() ?: 3
            val giro = File(dirOut, "percorsi.txt").also { it.writeText("") }
            var stati = 0
            while (coda.isNotEmpty() && stati < maxStati) {
                val (path, prima) = coda.removeFirst()
                val dir = Files.createTempDirectory("sagoma-controllo").toFile()
                try {
                    val repo = FileProjectRepository(dir)
                    val p = repo.create("Casa di prova")
                    var id = 0L
                    val rooms = GuidePlan.rooms.map { g ->
                        Room(++id, g.type.label, g.type, g.points).copy(
                            openings = listOf(Opening.default(1, OpeningKind.Door, 0, 120.0), Opening.default(2, OpeningKind.Window2, 1, 100.0)),
                        )
                    }
                    repo.planStore(p.id).save(Building.single(FloorPlan(rooms)))
                    repo.markOpened(p.id)
                    FileTipStore(dir).save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.COURSE_DONE)
                    FileLanguageStore(dir).save(choice)
                    val platform = platformOf(dir, f.guida, assets)
                    // Catalogo di arredi e materiali, come all'avvio dell'app (una volta sola: poi resta caricato).
                    if (System.getenv("SAGOMA_CATALOGO") == "true") com.sagoma.planimetria.ui.FurnitureAssets.init(platform)
                    curW = f.w; curH = f.h
                    ImageComposeScene(f.w, f.h, Density(f.density)).use { s ->
                        scene = s
                        var store: ViewModelStore? = null
                        scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                        frames(12)
                        var ok = true
                        for (step in path) {
                            val n = clickables().firstOrNull { it.first == step }?.second
                            if (n == null) { ok = false; break }
                            runCatching { n.config[SemanticsActions.OnClick].action!!.invoke() }.onFailure { ok = false }
                            frames(8)
                        }
                        if (!ok) return@use
                        stati++
                        giro.appendText(path.joinToString(" › ") + "\n")
                        val firma = unmerged().flatMap(::texts).sorted().joinToString("|")
                        if (!visti.add(firma)) return@use
                        // Con SAGOMA_FOTO=true una foto di ogni schermata (per vedere il "dopo" delle correzioni).
                        if (System.getenv("SAGOMA_FOTO") == "true") foto(File(dirOut, "schermata-%03d.png".format(stati)), problemi().map { it.box })
                        val probs = problemi().filter { segnalati.add(it.tipo + "|" + it.testo) }
                        if (probs.isNotEmpty()) {
                            val nome = "%03d.png".format(++foto)
                            foto(File(dirOut, nome), probs.map { it.box })
                            for (pr in probs) elenco.appendText("${path.joinToString(" › ")}\t${pr.tipo}\t${pr.testo.replace('\n', '⏎').replace('\t', ' ')}\t$nome\n")
                        }
                        if (path.size < profondita) {
                            val ora = clickables()
                            val nuovi = ora.map { it.first }.filter { it !in prima }
                            val tutti = ora.map { it.first }.toSet()
                            val salta = System.getenv("SAGOMA_SALTA")?.split(',').orEmpty()
                            for (k in nuovi) if (path.isNotEmpty() || salta.none { k.startsWith(it) }) coda += (path + k) to tutti
                        }
                    }
                } catch (e: Throwable) {
                    elenco.appendText("${path.joinToString(" › ")}\tERRORE\t${e.toString().replace('\n', ' ').take(300)}\t\n")
                } finally {
                    dir.deleteRecursively()
                }
            }
            println("CONTROLLO $conf: $stati stati, ${visti.size} schermate diverse, ${segnalati.size} problemi, coda rimasta ${coda.size}")
        }
    }

    // ---- Guida interattiva in ogni lingua: il robot di GuidaInterattivaTest, con il controllo delle scritte a ogni passo. ----
    private lateinit var store: ViewModelStore

    @Suppress("RestrictedApi")
    private fun vm(): EditorViewModel? = store.keys().mapNotNull { store[it] as? EditorViewModel }.firstOrNull { it.state.value.course != null }

    private fun nodes() = unmerged()
    private fun tag(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.TestTag)
    private fun center(n: SemanticsNode) = n.boundsInWindow.center
    private fun frames() = frames(1)

    private lateinit var guidaOut: File
    private lateinit var guidaElenco: File
    private val guidaSegnalati = mutableSetOf<String>()

    private fun controlla(name: String) {
        frames(2)
        val probs = problemi().filter { guidaSegnalati.add(it.tipo + "|" + it.testo) }
        if (probs.isEmpty()) return
        foto(File(guidaOut, "$name.png"), probs.map { it.box })
        for (pr in probs) guidaElenco.appendText("$name\t${pr.tipo}\t${pr.testo.replace('\n', '⏎').replace('\t', ' ')}\t$name.png\n")
    }

    private fun click(p: Offset) {
        scene.sendPointerEvent(PointerEventType.Move, p)
        frames()
        scene.sendPointerEvent(PointerEventType.Press, p)
        frames()
        scene.sendPointerEvent(PointerEventType.Release, p)
        frames(4)
    }

    private fun drag(a: Offset, b: Offset) {
        scene.sendPointerEvent(PointerEventType.Move, a)
        frames()
        scene.sendPointerEvent(PointerEventType.Press, a)
        frames()
        for (i in 1..16) {
            scene.sendPointerEvent(PointerEventType.Move, a + (b - a) * (i / 16f))
            frames()
        }
        scene.sendPointerEvent(PointerEventType.Release, b)
        frames(4)
    }

    /** Il pulsante con la scritta `label` (l'ultimo: il fumetto della guida sta sopra a tutto). */
    private fun button(label: String): SemanticsNode {
        val all = nodes().filter { n -> label in texts(n) }
        require(all.isNotEmpty()) { "non trovo «$label»" }
        val n = all.last()
        // La scritta è dentro il pulsante: si risale finché non si trova chi risponde al clic.
        var p: SemanticsNode? = n
        while (p != null && !p.config.contains(SemanticsActions.OnClick)) p = p.parent
        return p ?: n
    }

    /** Scrive nel campo; con `enter` anche Invio (i campi delle misure in Info applicano il valore solo così). */
    private fun type(n: SemanticsNode, text: String, enter: Boolean = false) {
        fun find(x: SemanticsNode): SemanticsNode? = x.takeIf { it.config.contains(SemanticsActions.SetText) } ?: x.children.firstNotNullOfOrNull(::find)
        val field = find(n) ?: error("il campo indicato non accetta testo")
        field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(text))
        frames(3)
        if (enter) {
            field.config[SemanticsActions.OnImeAction].action!!.invoke()
            frames(3)
        }
    }


    @Test
    fun guida() {
        if (System.getenv("SAGOMA_GUIDA") != "true") return
        val soloLingue = System.getenv("SAGOMA_LINGUE")?.split(',')
        for ((codice, choice) in lingue.filter { soloLingue == null || it.first in soloLingue }) {
            guidaOut = File("build/controllo/guida-$codice").also { it.deleteRecursively(); it.mkdirs() }
            guidaElenco = File(guidaOut, "elenco.tsv").also { it.writeText("passo\ttipo\ttesto\tfoto\n") }
            guidaSegnalati.clear()
            val dir = Files.createTempDirectory("sagoma-guida").toFile()
            try {
                val repo = FileProjectRepository(dir)
                val tips = FileTipStore(dir)
                FileLanguageStore(dir).save(choice)
                val platform = DesktopPlatform(projects = repo, tips = tips, language = FileLanguageStore(dir), assets = null)
                val steps = mutableListOf<CourseStep>()
                curW = 1400; curH = 900
                ImageComposeScene(1400, 900, Density(1f)).use { s ->
                    scene = s
                    scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                    frames(10)
                var last: CourseStep? = null
                var lastHint: String? = null
                var sub = 1
                var same = 0
                var done = false
                for (round in 0 until 600) {
                    frames(3)
                    val vm = vm() ?: break
                    val step = vm.state.value.course!!.step
                    if (step != last) {
                        steps += step
                        frames(4)
                        controlla("%02d-%s-%s".format(steps.size, step.lesson.name, step.name))
                        if (System.getenv("GUIDA_DEBUG") != null) println("DEBUG $step cam=${vm.state.value.camera} focus=${Course.focus(step, vm.state.value)} canvas=${nodes().firstOrNull { tag(it) == "pianta" }?.boundsInWindow}")
                        last = step
                        lastHint = Course.hints(step, vm.state.value).firstOrNull()?.text
                        sub = 1
                        same = 0
                    } else if (++same > 40) error("la guida è ferma al passo $step: ${nodes().flatMap(::texts).distinct()}")
                    // Dentro un passo, una foto per ogni indicazione nuova (il fumetto dice cosa fare dopo).
                    val shown = nodes().flatMap(::texts).let { t -> Course.hints(step, vm.state.value).firstOrNull { it.text in t }?.text }
                    if (shown != null && shown != lastHint) {
                        lastHint = shown
                        controlla("%02d-%s-%s-%d".format(steps.size, step.lesson.name, step.name, ++sub))
                    }
                    if (!step.action) {
                        when (step) {
                            CourseStep.Welcome -> click(center(button(T.editor.inizia)))
                            CourseStep.CheckDone -> click(center(button(T.corso.apriProgettoNuovo)))
                            CourseStep.Finished -> { click(center(button(T.comuni.fine))); done = true }
                            else -> click(center(button(T.corso.avanti)))
                        }
                        if (done) break
                        continue
                    }
                    val hints = Course.hints(step, vm.state.value)
                    val active = nodes().firstOrNull { tag(it)?.startsWith("guida:") == true }
                    if (active != null) {
                        val target = tag(active)!!.removePrefix("guida:")
                        val hint = hints.first { it.target.toString() == target }
                        when {
                            hint.expect != null -> type(active, hint.expect!!, enter = (hint.target as? GuideTarget.Field)?.field == GuideField.WallInfo)
                            else -> click(center(active))
                        }
                        continue
                    }
                    // Niente di cerchiato: il segno sulla pianta (dove cliccare, o da dove a dove trascinare).
                    val hint = hints.firstOrNull { it.target == null && it.marks.isNotEmpty() }
                    if (hint == null) {
                        frames(5)
                        continue
                    }
                    val canvas = nodes().first { tag(it) == "pianta" }.boundsInWindow
                    val cam = vm.state.value.camera
                    fun screen(p: Vec2) = Offset(canvas.left + cam.toScreenX(p.x), canvas.top + cam.toScreenY(p.y))
                    val start = hint.marks.first { !it.goal }.points.first()
                    val goal = hint.marks.firstOrNull { it.goal }?.points
                    when {
                        goal == null -> click(screen(start))
                        // Una stanza da portare al suo posto: l'angolo in alto a sinistra sul contorno tratteggiato, più o meno
                        // (qualche centimetro di scarto, come a mano: gli agganci la attaccano al centimetro).
                        goal.size >= 3 -> {
                            val room = vm.state.value.plan.rooms.minBy { Polygon.labelPoint(it.points).distanceTo(start) }
                            val g = Polygon.bounds(goal)
                            val r = Polygon.bounds(room.interior())
                            val near = if (step.lesson == Lesson.OverPlan) Vec2.Zero else Vec2(6.0, -5.0)
                            drag(screen(start), screen(start + Vec2(g.minX - r.minX, g.minY - r.minY) + near))
                        }
                        // Un muro portato a mano sulla piantina: mai esatto, qualche centimetro in più (le misure giuste poi in Info).
                        step == CourseStep.LivingWalls -> drag(screen(start), screen(goal[0] + (goal[0] - start).normalized() * 4.0))
                        else -> drag(screen(start), screen(goal[0]))
                    }
                }
                    println("GUIDA $codice: arrivata alla fine = $done, ${steps.size} passi, ${guidaSegnalati.size} problemi")
                }
            } catch (e: Throwable) {
                guidaElenco.appendText("-\tERRORE\t${e.toString().replace('\n', ' ').take(400)}\t\n")
            } finally {
                dir.deleteRecursively()
            }
        }
    }
}
