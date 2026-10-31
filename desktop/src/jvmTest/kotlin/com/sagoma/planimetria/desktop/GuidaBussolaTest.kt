package com.sagoma.planimetria.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.ViewModelStore
import com.sagoma.planimetria.editor.Course
import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.GuideTarget
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.model.Building
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
 * La lezione del sole dall'app vera: in un progetto dell'utente il primo tocco sulla bussola la fa partire; l'"utente" segue la
 * guida (gira la bussola, apre «Nord e città» e la chiude senza città, guarda il 3D, sposta l'ora e sceglie un giorno). La
 * seconda volta la bussola apre «Nord e città». Una foto per passo in desktop/build/guida-bussola/.
 */
@OptIn(ExperimentalComposeUiApi::class)
class GuidaBussolaTest {
    private lateinit var store: ViewModelStore
    private lateinit var scene: ImageComposeScene
    private var t = 0L
    private val out = File("build/guida-bussola")

    private fun frames(n: Int = 1) = repeat(n) {
        t += 40_000_000L
        scene.render(t).close()
        Thread.sleep(4)
    }

    @Suppress("RestrictedApi")
    private fun vm(): EditorViewModel = store.keys().mapNotNull { store[it] as? EditorViewModel }.first()

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

    private fun button(label: String): SemanticsNode {
        val n = nodes().last { label in texts(it) }
        var p: SemanticsNode? = n
        while (p != null && !p.config.contains(SemanticsActions.OnClick)) p = p.parent
        return p ?: n
    }

    private fun compass(): SemanticsNode = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(T.vista3D.bussola) == true }

    private fun shot(name: String) {
        t += 40_000_000L
        scene.render(t).use { File(out, "$name.png").writeBytes(it.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
    }

    @Test
    fun primoToccoSullaBussola() {
        val dir = Files.createTempDirectory("sagoma-bussola").toFile()
        out.mkdirs()
        out.listFiles()?.forEach { it.delete() }
        try {
            val repo = FileProjectRepository(dir)
            val tips = FileTipStore(dir)
            tips.save(setOf(TipStore.COURSE_DONE, TipStore.TUTORIAL_DONE))
            FileLanguageStore(dir).save(LanguageChoice.Italian)
            // Un progetto dell'utente, già disegnato (la casa della piantina della guida).
            val p = repo.create("Casa")
            repo.planStore(p.id).save(Building.single(Course.house(Lesson.OverPlan), autoLevel = true))
            repo.markOpened(p.id)
            val platform = DesktopPlatform(projects = repo, tips = tips, language = FileLanguageStore(dir), assets = null)
            val steps = mutableListOf<CourseStep>()
            ImageComposeScene(1400, 900, Density(1f)).use { s ->
                scene = s
                scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                frames(10)
                click(center(compass()))
                var sub = 1
                for (round in 0 until 200) {
                    frames(3)
                    val step = vm().state.value.course?.step ?: break
                    if (steps.lastOrNull() != step) {
                        steps += step
                        sub = 1
                        frames(4)
                        shot("%02d-%s".format(steps.size, step.name))
                    }
                    if (step == CourseStep.SunDone) {
                        click(center(button(T.comuni.fine)))
                        continue
                    }
                    val active = nodes().firstOrNull { tag(it)?.startsWith("guida:") == true }
                    if (active == null) {
                        // Il giorno: nessun valore imposto, si sceglie dall'elenco.
                        if (step == CourseStep.SunDay) click(center(button(T.vista3D.giugno21)))
                        continue
                    }
                    shot("%02d-%s-%d".format(steps.size, step.name, ++sub))
                    val target = tag(active)!!.removePrefix("guida:")
                    when {
                        step == CourseStep.North && target == GuideTarget.Compass.toString() -> {
                            val c = center(active)
                            drag(c + Offset(0f, -18f), c + Offset(22f, 0f))
                        }
                        target == GuideTarget.SunSlider.toString() -> {
                            fun find(x: SemanticsNode): SemanticsNode? = x.takeIf { it.config.contains(SemanticsActions.SetProgress) } ?: x.children.firstNotNullOfOrNull(::find)
                            find(active)!!.config[SemanticsActions.SetProgress].action!!.invoke(17f)
                            frames(3)
                        }
                        else -> click(center(active))
                    }
                }
                assertEquals(listOf(CourseStep.North, CourseStep.City, CourseStep.Open3d, CourseStep.SunHour, CourseStep.SunDay, CourseStep.SunDone), steps)
                assertTrue(TipStore.SUN_GUIDE_DONE in tips.seen())
                assertTrue(TipStore.COURSE_DONE in tips.seen())
                // Il nord girato resta nel progetto; nessuna città scelta, nessuna stanza toccata.
                val site = vm().state.value.building.site
                assertEquals(90.0, site.northDeg)
                assertEquals(null, site.place)
                assertEquals(5, vm().state.value.plan.rooms.size)
                // La seconda volta la bussola apre «Nord e città».
                click(center(button(T.editor.pianta2d)))
                frames(5)
                click(center(compass()))
                frames(5)
                assertEquals(null, vm().state.value.course)
                assertTrue(nodes().any { T.vista3D.spiegazioneNord in texts(it) }, "la finestra «Nord e città» è aperta")
                shot("%02d-seconda-volta".format(steps.size + 1))
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
