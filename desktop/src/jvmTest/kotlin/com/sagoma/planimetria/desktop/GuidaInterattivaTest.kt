package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.sagoma.planimetria.editor.Course
import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.GuideField
import com.sagoma.planimetria.editor.GuidePlan
import com.sagoma.planimetria.editor.GuideTarget
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.geometry.interior
import com.sagoma.planimetria.geometry.interiorLengths
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.persistence.FileLanguageStore
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.persistence.LanguageChoice
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.ui.SagomaApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.roundToInt

/**
 * La guida interattiva dall'app vera, al primo avvio sul computer: un "utente" fa tutte le lezioni facendo solo quello che
 * la guida indica (il comando cerchiato in arancione, il campo con la misura da scrivere, il segno sulla pianta), senza mai
 * "Salta passo". Una foto per passo in desktop/build/guida/.
 */
@OptIn(ExperimentalComposeUiApi::class)
class GuidaInterattivaTest {
    private lateinit var store: ViewModelStore
    private lateinit var scene: ImageComposeScene
    private var t = 0L
    private val out = File("build/guida")

    private fun frames(n: Int = 1) = repeat(n) {
        t += 40_000_000L
        scene.render(t).close()
        Thread.sleep(4)
    }

    /** Il ViewModel del progetto con la guida in corso (prima dell'ultima lezione la guida passa al secondo progetto di prova). */
    @Suppress("RestrictedApi")
    private fun vm(): EditorViewModel? = store.keys().mapNotNull { store[it] as? EditorViewModel }.firstOrNull { it.state.value.course != null }

    private fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { owner ->
        val all = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { all += n; n.children.forEach(::walk) }
        walk(owner.unmergedRootSemanticsNode)
        all
    }

    private fun texts(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    private fun tag(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.TestTag)

    private fun center(n: SemanticsNode) = n.boundsInWindow.center

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

    private fun shot(name: String) {
        t += 40_000_000L
        scene.render(t).use { File(out, "$name.png").writeBytes(it.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
    }

    @Test
    fun tutteLeLezioniSoloSeguendoLaGuida() {
        val dir = Files.createTempDirectory("sagoma-guida").toFile()
        out.mkdirs()
        out.listFiles()?.forEach { it.delete() }
        try {
            val repo = FileProjectRepository(dir)
            val tips = FileTipStore(dir)
            FileLanguageStore(dir).save(LanguageChoice.Italian)
            val platform = DesktopPlatform(projects = repo, tips = tips, language = FileLanguageStore(dir), assets = null)
            val steps = mutableListOf<CourseStep>()
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
                        shot("%02d-%s-%s".format(steps.size, step.lesson.name, step.name))
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
                        shot("%02d-%s-%s-%d".format(steps.size, step.lesson.name, step.name, ++sub))
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
                assertTrue(done, "la guida non è arrivata alla fine; passi visti: $steps")
            }
            // Tutti i passi, nessuno saltato, e la casa di prova come la piantina della guida: ogni stanza al suo posto.
            assertEquals(CourseStep.entries.filter { it.lesson.inCourse }, steps.distinct())
            assertTrue(TipStore.COURSE_DONE in tips.seen(), "finita, la guida non riparte al prossimo avvio")
            assertEquals(2, repo.list().count { it.name.startsWith(T.corso.progettoProva) }, "due progetti di prova, nessun progetto dell'utente toccato")
            val first = repo.planStore(repo.list().first { it.name == T.corso.progettoProva }.id).load()!!.floor.plan
            assertEquals(
                listOf(RoomType.Studio, RoomType.Soggiorno, RoomType.Camera, RoomType.Bagno, RoomType.Corridoio, RoomType.Balcone),
                first.rooms.map { it.type },
            )
            val offset = first.rooms.first().interior().minBy { it.x + it.y } - GuidePlan.studio.points[0]
            for (r in GuidePlan.rooms) {
                val inner = first.rooms.single { it.type == r.type }.interior()
                assertTrue(r.points.all { p -> inner.any { it.distanceTo(p + offset) < 3.0 } }, "${r.type} non è dove sta sulla piantina")
            }
            // Il tramezzo della cabina armadio: al suo posto a mano (qualche centimetro), lunghezza e spessore esatti da Info.
            val wall = first.freeWalls.single()
            val t = GuidePlan.tramezzo
            assertTrue(wall.start.distanceTo(t.start + offset) < 15.0 && wall.end.distanceTo(t.end + offset) < 15.0, "il tramezzo non è al suo posto: $wall")
            assertEquals(t.length, wall.length, 0.5)
            assertEquals(t.thickness, wall.thickness, 0.01)
            // Sopra la piantina: camera, bagno e soggiorno a L con le misure esatte scritte in Info.
            val second = repo.planStore(repo.list().first { it.name == T.corso.progettoProvaPlanimetria }.id).load()!!.floor.plan
            assertEquals(listOf(RoomType.Camera, RoomType.Bagno, RoomType.Soggiorno), second.rooms.map { it.type })
            val living = second.rooms.last()
            assertEquals(
                GuidePlan.soggiorno.points.indices.map { GuidePlan.side(GuidePlan.soggiorno, it) }.sorted(),
                living.interiorLengths().map { it.roundToInt() }.sorted(),
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
