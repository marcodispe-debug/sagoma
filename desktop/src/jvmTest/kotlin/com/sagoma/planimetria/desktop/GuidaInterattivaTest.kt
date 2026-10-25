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
import com.sagoma.planimetria.editor.GuideTarget
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

/**
 * La guida interattiva dall'app vera, al primo avvio sul computer: un "utente" fa tutte e 7 le lezioni facendo solo quello che
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

    /** Il ViewModel del progetto con la guida in corso (dopo la lezione 6 la guida passa al secondo progetto di prova). */
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

    private fun type(n: SemanticsNode, text: String) {
        fun find(x: SemanticsNode): SemanticsNode? = x.takeIf { it.config.contains(SemanticsActions.SetText) } ?: x.children.firstNotNullOfOrNull(::find)
        val field = find(n) ?: error("il campo indicato non accetta testo")
        field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(text))
        frames(3)
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
                        same = 0
                    } else if (++same > 40) error("la guida è ferma al passo $step: ${nodes().flatMap(::texts).distinct()}")
                    if (!step.action) {
                        when (step) {
                            CourseStep.Welcome -> click(center(button(T.editor.inizia)))
                            CourseStep.ElementsDone -> click(center(button(T.corso.apriProgettoNuovo)))
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
                            hint.expect != null -> type(active, hint.expect!!)
                            hint.target == GuideTarget.Tip -> click(center(button(T.editor.hoCapito)))
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
                        // Una stanza da portare sulla piantina: l'angolo interno in alto a sinistra su quello disegnato.
                        goal.size >= 3 -> {
                            val room = vm.state.value.plan.rooms.minBy { com.sagoma.planimetria.geometry.Polygon.labelPoint(it.points).distanceTo(start) }
                            val corner = room.points[0] + Vec2(room.wallThickness / 2, room.wallThickness / 2)
                            drag(screen(start), screen(start + (goal[0] - corner)))
                        }
                        else -> drag(screen(start), screen(goal[0]))
                    }
                }
                assertTrue(done, "la guida non è arrivata alla fine; passi visti: $steps")
            }
            // Tutti i passi, nessuno saltato, e la casa di prova come la piantina della guida.
            assertEquals(CourseStep.entries.toList(), steps.distinct())
            assertTrue(TipStore.COURSE_DONE in tips.seen(), "finita, la guida non riparte al prossimo avvio")
            assertEquals(2, repo.list().count { it.name.startsWith(T.corso.progettoProva) }, "due progetti di prova, nessun progetto dell'utente toccato")
            val first = repo.planStore(repo.list().first { it.name == T.corso.progettoProva }.id).load()!!.floor.plan
            assertEquals(
                listOf(RoomType.Studio, RoomType.Camera, RoomType.Soggiorno, RoomType.Bagno, RoomType.Corridoio, RoomType.Balcone),
                first.rooms.map { it.type },
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
