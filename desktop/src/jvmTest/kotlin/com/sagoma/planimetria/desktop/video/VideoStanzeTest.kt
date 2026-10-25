package com.sagoma.planimetria.desktop.video

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.Rect
import com.sagoma.planimetria.access.Account
import com.sagoma.planimetria.access.Profile
import com.sagoma.planimetria.desktop.DesktopPlatform
import com.sagoma.planimetria.desktop.freshViewModels
import androidx.lifecycle.ViewModelStore
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.geometry.exterior
import com.sagoma.planimetria.geometry.interior
import com.sagoma.planimetria.geometry.Polygon
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.Vec2
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.persistence.FileLanguageStore
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.persistence.LanguageChoice
import com.sagoma.planimetria.ui.PickedFile
import com.sagoma.planimetria.ui.Platform
import com.sagoma.planimetria.ui.SagomaApp
import java.io.File
import java.nio.file.Files
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.test.Test

/**
 * Video tutorial «I modi per creare una stanza» registrato dall'app vera sul computer.
 * Si lancia a mano: `VIDEO=1 ./gradlew :desktop:jvmTest --tests '*VideoStanzeTest*'`; il video esce in desktop/build/video/.
 */
@OptIn(ExperimentalComposeUiApi::class)
class VideoStanzeTest {
    /** Solo la piantina di prova, per guardarla: `PIANTINA=1 ./gradlew :desktop:jvmTest --tests '*VideoStanzeTest*'`. */
    @Test
    fun piantina() {
        if (System.getenv("PIANTINA") == null) return
        File("build/video").mkdirs()
        File("build/video/piantina.png").writeBytes(PiantinaDiProva.png())
    }

    @Test
    fun video() {
        if (System.getenv("VIDEO") == null) return
        val dir = Files.createTempDirectory("sagoma-video").toFile()
        val out = File("build/video").apply { mkdirs() }
        try {
            val repo = FileProjectRepository(dir)
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.TRACING_GUIDE_DONE + TipStore.COURSE_DONE) }
            FileLanguageStore(dir).save(LanguageChoice.Italian)
            val desktop = DesktopPlatform(
                account = Account(profile = Profile.Professional), projects = repo, tips = tips, language = FileLanguageStore(dir), assets = null,
                planReader = PiantinaDiProva.lettore,
            )
            // Al posto della finestra per scegliere il file: la piantina inventata per il video.
            val platform = object : Platform by desktop {
                override fun openFile(mimeTypes: List<String>, onResult: (PickedFile?) -> Unit) = onResult(PickedFile("piantina.png", PiantinaDiProva.png()))
            }
            File(out, "piantina.png").writeBytes(PiantinaDiProva.png())
            ImageComposeScene(W, H, Density(1f)).use { scene ->
                scene.setContent { freshViewModels(onStore = { store = it }) { SagomaApp(platform) } }
                val r = Regista(scene, W, H)
                fun shot(name: String) { File(out, "dump-$name.txt").writeText(r.dump()); r.snapshot(File(out, "shot-$name.png")) }
                r.wait(0.5)
                // VIDEO_SOLO=6: solo il capitolo sopra la planimetria (per provarlo senza rifare tutti gli altri).
                val solo = System.getenv("VIDEO_SOLO")
                if (solo == null) {
                    r.start(File(out, "1-quadrato.mp4"))
                    r.titleCard("Come creare una stanza", "Video tutorial di sagoma per computer")
                    quadrato(r)
                    r.stop()
                    shot("1")
                    r.start(File(out, "2-rettangolo.mp4"))
                    rettangolo(r)
                    r.stop()
                    shot("2")
                    r.start(File(out, "3-forma-a-l.mp4"))
                    formaL(r)
                    r.stop()
                    shot("3")
                    r.start(File(out, "4-da-rilievo.mp4"))
                    rilievo(r)
                    r.stop()
                    shot("4")
                    r.start(File(out, "5-muro-per-muro.mp4"))
                    muroPerMuro(r)
                    r.stop()
                    shot("5")
                }
                if (solo == null || solo == "6") {
                    r.start(File(out, "6-sopra-la-planimetria.mp4"))
                    sopraLaPlanimetria(r)
                    r.stop()
                    shot("6")
                }
                // Secondo video: porte, finestre, balconi, impianti e scale sull'appartamento di prova già disegnato.
                if (solo == null || solo == "7") {
                    preparaAppartamento(r, tips)
                    r.start(File(out, "7-porte-finestre-e-altro.mp4"))
                    elementi(r)
                    r.stop()
                    shot("7")
                }
                // Capitoli vecchi fuori dalla serie: muro singolo (andrà nei mini tutorial), ricalco angolo per angolo, lettura automatica.
                if (System.getenv("VIDEO_RICALCO") != null) {
                    r.start(File(out, "6-muro-singolo.mp4"))
                    muroSingolo(r)
                    r.stop()
                    r.start(File(out, "7-ricalca.mp4"))
                    ricalca(r)
                    r.stop()
                    r.start(File(out, "8-leggi-la-piantina.mp4"))
                    leggi(r)
                    r.stop()
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Modo 1: Quadrato. */
    private fun quadrato(r: Regista) {
        r.titleCard("Modo 1 · Quadrato", "Per una stanza con i quattro lati uguali")
        r.chapter = "Modo 1 · Quadrato"
        val tot = 6
        r.stepLabel = "Passo 1 di $tot"
        r.highlight = r.rect("Scegli la forma della stanza").let { t -> Rect(t.left, t.top, r.rect("Ricalca una piantina").right, r.rect("Ricalca una piantina").bottom) }
        r.caption = "Quando apri un progetto nuovo, sagoma ti chiede subito che forma ha la prima stanza. Ci sono più modi: in questi video li vediamo uno alla volta."
        r.wait(6.0)
        r.stepLabel = "Passo 2 di $tot"
        r.clickOn(r.rect("Quadrato", exact = true), "Clicca su «Quadrato»: è la forma giusta per una stanza con i quattro lati uguali.", readSeconds = 3.5)
        r.wait(0.8)
        r.stepLabel = "Passo 3 di $tot"
        r.clickOn(r.rect("Studio", exact = true), "Si apre la finestra «Misure della stanza». In «Tipo di stanza» clicca su che stanza è, per esempio «Studio»: sagoma le dà il nome e il colore giusti.", readSeconds = 5.0)
        r.wait(1.0)
        r.stepLabel = "Passo 4 di $tot"
        val lato = r.field("Lato")
        r.clickOn(r.rectOf(lato), "Clicca nel campo «Lato» e scrivi quanto è lungo un lato, in centimetri. È la misura interna, da muro a muro, quella che prendi col metro.", readSeconds = 5.0)
        r.type(lato, "330")
        r.wait(1.5)
        r.stepLabel = "Passo 5 di $tot"
        r.highlight = r.rectOf(r.field("Altezza soffitto"))
        r.caption = "L'altezza del soffitto è già scritta: 270 cm. Cambiala solo se la tua stanza è più alta o più bassa. Serve per la vista 3D."
        r.moveTo(r.highlight!!.let { Offset(it.right - 60f, it.top + it.height / 2) })
        r.wait(5.0)
        r.stepLabel = "Passo 6 di $tot"
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca su «Crea stanza».", readSeconds = 2.5)
        r.highlight = null
        r.caption = "Fatto! Lo studio quadrato di 3,30 × 3,30 m è sulla pianta, con le misure scritte sui muri."
        r.moveTo(Offset(1050f, 600f))
        r.wait(5.0)
    }

    /** Apre di nuovo la scelta della forma per una stanza in più: Elementi architettonici → Stanza. */
    private fun nuovaStanza(r: Regista, tot: Int) {
        r.stepLabel = "Passo 1 di $tot"
        r.clickOn(r.rect("Elementi architettonici"), "Per aggiungere un'altra stanza clicca su «Elementi architettonici», in basso a sinistra.", readSeconds = 3.5)
        r.wait(0.6)
        r.stepLabel = "Passo 2 di $tot"
        r.clickOn(r.rect("▭ Stanza"), "Nel menu che si apre clicca su «Stanza»: torna la finestra con le forme.", readSeconds = 3.0)
        r.wait(0.8)
    }

    /** Modo 2: Rettangolo. */
    private fun rettangolo(r: Regista) {
        r.titleCard("Modo 2 · Rettangolo", "Per una stanza con due lati lunghi e due corti")
        r.chapter = "Modo 2 · Rettangolo"
        val tot = 7
        r.highlight = null
        r.caption = "Ora aggiungiamo una seconda stanza, rettangolare."
        r.wait(2.5)
        nuovaStanza(r, tot)
        r.stepLabel = "Passo 3 di $tot"
        r.clickOn(r.rect("Rettangolo", exact = true), "Clicca su «Rettangolo».", readSeconds = 2.5)
        r.wait(0.8)
        r.stepLabel = "Passo 4 di $tot"
        r.clickOn(r.rect("Camera da letto", exact = true), "Scegli il tipo: per esempio «Camera da letto».", readSeconds = 3.0)
        r.wait(0.8)
        r.stepLabel = "Passo 5 di $tot"
        val larg = r.field("Larghezza")
        r.clickOn(r.rectOf(larg), "Nel campo «Larghezza» scrivi il lato orizzontale della stanza (da sinistra a destra sulla pianta), in centimetri.", readSeconds = 4.5)
        r.type(larg, "400")
        r.wait(1.0)
        r.stepLabel = "Passo 6 di $tot"
        val prof = r.field("Profondità")
        r.clickOn(r.rectOf(prof), "Nel campo «Profondità» scrivi l'altro lato (dall'alto in basso sulla pianta).", readSeconds = 4.0)
        r.type(prof, "330")
        r.wait(1.0)
        r.stepLabel = "Passo 7 di $tot"
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca su «Crea stanza».", readSeconds = 2.0)
        r.highlight = null
        r.caption = "Ecco la camera di 4,00 × 3,30 m accanto allo studio. Poi potrai spostarla trascinandola dove vuoi."
        r.moveTo(Offset(1100f, 560f))
        r.wait(5.0)
    }

    /** Modo 3: forma a L. */
    private fun formaL(r: Regista) {
        r.titleCard("Modo 3 · Forma a L", "Per una stanza con un angolo che rientra")
        r.chapter = "Modo 3 · Forma a L"
        val tot = 9
        r.highlight = null
        r.caption = "Molti soggiorni e molte cucine sono a forma di L. Vediamo come si fa."
        r.wait(2.5)
        nuovaStanza(r, tot)
        r.stepLabel = "Passo 3 di $tot"
        r.clickOn(r.rect("A L", exact = true), "Clicca su «A L».", readSeconds = 2.5)
        r.wait(0.8)
        r.stepLabel = "Passo 4 di $tot"
        r.highlight = r.rect("Dove sporge la stanza?").let { t -> Rect(t.left, t.top, r.rect("In basso a sinistra").right, r.rect("In basso a sinistra").bottom) }
        r.caption = "sagoma chiede da che parte sporge il pezzo in più della L. Guarda la tua stanza dall'alto, come su una piantina, e scegli l'angolo giusto."
        r.wait(5.5)
        r.clickOn(r.rect("In basso a destra"), "Qui scegliamo «In basso a destra».", readSeconds = 2.5)
        r.wait(0.8)
        r.stepLabel = "Passo 5 di $tot"
        r.clickOn(r.rect("Soggiorno", exact = true), "Scegli il tipo: «Soggiorno».", readSeconds = 2.5)
        r.wait(0.6)
        r.stepLabel = "Passo 6 di $tot"
        val lp = r.field("Larghezza principale")
        r.clickOn(r.rectOf(lp), "La L è fatta di due pezzi. Il pezzo principale è il rettangolo più grande: in «Larghezza principale» scrivi quanto è largo.", readSeconds = 5.0)
        r.type(lp, "640")
        val pp = r.field("Profondità principale")
        r.clickOn(r.rectOf(pp), "In «Profondità principale» scrivi quanto è profondo il pezzo principale.", readSeconds = 3.5)
        r.type(pp, "270")
        r.wait(0.8)
        r.stepLabel = "Passo 7 di $tot"
        val lpr = r.field("Larghezza protuberanza")
        r.clickOn(r.rectOf(lpr), "La «protuberanza» è il pezzo che sporge. In «Larghezza protuberanza» scrivi quanto è largo: deve essere meno della larghezza principale.", readSeconds = 5.5)
        r.type(lpr, "300")
        r.stepLabel = "Passo 8 di $tot"
        val ppr = r.field("Profondità protuberanza")
        r.clickOn(r.rectOf(ppr), "In «Profondità protuberanza» scrivi di quanto sporge.", readSeconds = 3.5)
        r.type(ppr, "190")
        r.wait(0.8)
        r.stepLabel = "Passo 9 di $tot"
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca su «Crea stanza».", readSeconds = 2.0)
        r.highlight = null
        r.caption = "Ecco il soggiorno a L: 6,40 × 2,70 m con il pezzo di 3,00 × 1,90 m che sporge in basso a destra."
        r.moveTo(Offset(1100f, 560f))
        r.wait(5.0)
    }

    /** Modo 4: Da rilievo (lati e diagonali). */
    private fun rilievo(r: Regista) {
        r.titleCard("Modo 4 · Da rilievo", "Per le stanze fuori squadra: lati e diagonali presi col metro")
        r.chapter = "Modo 4 · Da rilievo"
        val tot = 9
        r.highlight = null
        r.caption = "Se gli angoli della stanza non sono proprio a 90°, oppure la stanza ha 5 o più lati, misura lati e diagonali: sagoma ricostruisce la forma vera."
        r.wait(5.0)
        nuovaStanza(r, tot)
        r.stepLabel = "Passo 3 di $tot"
        r.clickOn(r.rect("Da rilievo"), "Clicca su «Da rilievo».", readSeconds = 2.5)
        r.wait(0.8)
        r.stepLabel = "Passo 4 di $tot"
        r.clickOn(r.rect("Bagno", exact = true), "Si apre «Stanza da rilievo». Scegli il tipo: «Bagno».", readSeconds = 3.0)
        r.wait(0.5)
        r.stepLabel = "Passo 5 di $tot"
        r.highlight = r.rect("Numero di lati").let { t -> Rect(t.left, t.top - 10f, r.rect("+", exact = true).right, t.bottom + 10f) }
        r.caption = "In «Numero di lati» con − e + scegli quanti muri ha la stanza. Il nostro bagno ne ha 4, come già scritto."
        r.moveTo(r.highlight!!.let { Offset(it.right - 30f, it.top + it.height / 2) })
        r.wait(5.0)
        r.stepLabel = "Passo 6 di $tot"
        r.highlight = r.rect("Misura i lati sul filo interno")
        r.caption = "Chiama A un angolo della stanza, poi B, C e D girando in senso orario. Misura i lati col metro sul muro, da angolo ad angolo, all'interno."
        r.moveTo(r.highlight!!.let { Offset(it.right - 40f, it.bottom - 10f) })
        r.wait(7.0)
        val lati = PiantinaDiProva.latiStorta.mapIndexed { i, l -> "Lato ${"ABCD"[i]}${"ABCD"[(i + 1) % 4]}" to l.toString() }
        for ((i, l) in lati.withIndex()) {
            val f = r.field(l.first)
            val spiega = if (i == 0) "Clicca in «Lato AB» e scrivi la misura da A a B, in centimetri." else "Ora «${l.first}»."
            r.clickOn(r.rectOf(f), spiega, readSeconds = if (i == 0) 3.5 else 1.5)
            r.type(f, l.second)
            r.wait(0.4)
        }
        r.stepLabel = "Passo 7 di $tot"
        r.highlight = r.rect("Quale diagonale hai misurato?").let { t -> Rect(t.left, t.top - 6f, r.rect("BD", exact = true).right + 8f, r.rect("BD", exact = true).bottom + 4f) }
        r.caption = "Poi la diagonale: da A all'angolo opposto C (AC), oppure da B a D (BD). Basta una delle due: scegli quella che hai misurato. Qui abbiamo AC, già scelta."
        r.moveTo(r.rect("BD", exact = true).let { Offset(it.right + 30f, it.bottom + 10f) })
        r.wait(6.5)
        val diag = r.field("Diagonale AC")
        r.clickOn(r.rectOf(diag), "In «Diagonale AC» scrivi la distanza in diagonale da A a C. È questa misura che dice a sagoma se gli angoli sono storti.", readSeconds = 5.0)
        r.type(diag, PiantinaDiProva.diagonaleStorta.toString())
        r.wait(1.0)
        r.stepLabel = "Passo 8 di $tot"
        r.highlight = null
        r.caption = "Con la rotellina del mouse scorri in giù: c'è l'anteprima della forma, lo spessore dei muri e l'altezza del soffitto."
        r.moveTo(Offset(700f, 600f))
        r.scroll(600f)
        r.highlight = r.rect("Anteprima").let { t -> Rect(t.left, t.top, t.left + 512f, r.rectOf(r.field("Spessore dei muri")).top - 8f) }
        r.caption = "L'anteprima mostra la forma vera del bagno: un po' fuori squadra, proprio come è nella realtà."
        r.wait(5.0)
        r.highlight = r.rectOf(r.field("Spessore dei muri"))
        r.caption = "Spessore dei muri (15 cm) e altezza del soffitto (270 cm) sono già scritti: cambiali solo se i tuoi sono diversi."
        r.wait(4.5)
        r.stepLabel = "Passo 9 di $tot"
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca su «Crea stanza».", readSeconds = 2.0)
        r.highlight = null
        r.caption = "Ecco il bagno, con la sua forma vera ricostruita dalle misure."
        r.moveTo(Offset(1100f, 560f))
        r.wait(5.0)
    }

    /** Modo 5: Muro per muro. */
    private fun muroPerMuro(r: Regista) {
        r.titleCard("Modo 5 · Muro per muro", "Per qualsiasi forma: disegni i muri uno dopo l'altro")
        r.chapter = "Modo 5 · Muro per muro"
        val tot = 9
        r.highlight = null
        r.caption = "Con «Muro per muro» disegni la stanza come in un programma da architetto: un muro alla volta, scrivendo le misure interne, come negli altri modi."
        r.wait(4.5)
        nuovaStanza(r, tot)
        r.stepLabel = "Passo 3 di $tot"
        r.clickOn(r.rect("Muro per muro"), "Clicca su «Muro per muro».", readSeconds = 2.5)
        r.wait(0.8)
        r.stepLabel = "Passo 4 di $tot"
        r.highlight = r.rect("✏ Muro per muro").let { t -> Rect(t.left, t.top, r.rect("Agganci").right, r.rect("Chiudi stanza").bottom) }
        r.caption = "In alto compare il pannello «Muro per muro»: ti dice cosa fare a ogni passo. Con «Ultimo» togli l'ultimo angolo, con «Esci» smetti di disegnare."
        r.moveTo(Offset(700f, 260f))
        r.wait(6.5)
        r.stepLabel = "Passo 5 di $tot"
        r.clickOn(r.rect("−", exact = true), "Serve un po' di spazio libero sulla pianta: clicca due volte su «−», in alto a destra, per rimpicciolirla.", readSeconds = 4.0)
        r.click()
        r.wait(1.2)
        r.stepLabel = "Passo 6 di $tot"
        // Primo angolo sotto la camera, sul filo interno del suo muro a sinistra: lo studio ha il muro in linea con quello della camera.
        val start = {
            val camera = vm().state.value.plan.rooms.first { it.type == RoomType.Camera }
            pianta(Vec2(camera.interior().minOf { it.x }, camera.exterior().maxOf { it.y }))
        }
        fun box(p: Offset) = Rect(p.x - 26f, p.y - 26f, p.x + 26f, p.y + 26f)
        r.highlight = box(start())
        r.caption = "Clicca sulla pianta dove inizia il primo muro: è il primo angolo della stanza, all'interno. Qui lo mettiamo sotto la camera: l'angolo si aggancia da solo in linea con il suo muro."
        r.follow(start, 1.4)
        r.highlight = box(start())
        r.wait(4.0)
        r.follow(start, 0.2)
        r.click()
        r.highlight = null
        r.wait(1.0)
        r.stepLabel = "Passo 7 di $tot"
        r.highlight = r.rect("Verso:").let { t -> Rect(t.left, r.rect("→ destra").top, r.rect("↑ su").right, r.rect("Aggiungi", exact = true).bottom) }
        r.caption = "Per ogni muro scegli il verso con le frecce (destra, giù, sinistra, su), scrivi la lunghezza interna in centimetri e clicca «Aggiungi»."
        r.moveTo(r.highlight!!.let { Offset(it.right - 40f, it.bottom + 30f) })
        r.wait(6.0)
        val muri = listOf(
            Triple("→ destra", "130", "Primo muro: clicca «→ destra», scrivi 130 in «Lunghezza cm» e clicca «Aggiungi»."),
            Triple("↓ giù", "270", "Secondo muro: «↓ giù», 270 cm, «Aggiungi»."),
            Triple("← sinistra", "440", "Poi «← sinistra», 440 cm."),
            Triple("↑ su", "140", "Il muro risale: «↑ su», 140 cm."),
            Triple("→ destra", "310", "E «→ destra», 310 cm: siamo tornati sotto il primo angolo."),
        )
        for ((i, m) in muri.withIndex()) {
            r.caption = m.third
            r.clickOn(r.rect(m.first), readSeconds = if (i == 0) 3.0 else 1.6)
            val f = r.field("Lunghezza cm")
            r.clickOn(r.rectOf(f), readSeconds = 0.6)
            r.type(f, m.second)
            r.clickOn(r.rect("Aggiungi", exact = true), readSeconds = 0.8)
            r.wait(1.0)
        }
        r.stepLabel = "Passo 8 di $tot"
        r.clickOn(r.rect("Chiudi stanza", exact = true), "Manca solo l'ultimo muro, che torna al primo angolo: clicca «Chiudi stanza» e sagoma lo disegna da solo.", readSeconds = 4.5)
        r.wait(1.0)
        r.stepLabel = "Passo 9 di $tot"
        r.clickOn(r.rect("Corridoio", exact = true), "Nella finestra «Nuova stanza» scegli il tipo: per l'ingresso va bene «Corridoio». Soffitto e spessore dei muri sono già scritti.", readSeconds = 5.0)
        r.wait(0.6)
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca «Crea stanza».", readSeconds = 2.0)
        r.highlight = null
        r.caption = "Ecco l'ingresso, con la forma che hai disegnato muro per muro e le misure interne che hai scritto."
        r.moveTo(Offset(1100f, 560f))
        r.wait(5.0)
    }

    /** Modo 6: Muro singolo (un tramezzo). */
    private fun muroSingolo(r: Regista) {
        r.titleCard("Modo 6 · Muro singolo", "Un tramezzo da solo, dentro una stanza o tra due stanze")
        r.chapter = "Modo 6 · Muro singolo"
        val tot = 7
        r.highlight = null
        r.caption = "A volte serve un muro da solo, che non chiude una stanza: per esempio un tramezzo che divide il soggiorno. Si fa così."
        r.wait(4.5)
        nuovaStanza(r, tot)
        r.stepLabel = "Passo 3 di $tot"
        r.clickOn(r.rect("Muro singolo"), "In fondo alla finestra c'è «Muro singolo» (compare solo quando c'è già almeno una stanza). Cliccalo.", readSeconds = 4.0)
        r.wait(1.0)
        r.stepLabel = "Passo 4 di $tot"
        r.highlight = Rect(318f, 500f, 348f, 655f)
        r.caption = "Il muro compare nella stanza selezionata, qui lo studio, già scelto (blu). I pallini alle due estremità si trascinano per allungarlo, accorciarlo o girarlo."
        r.moveTo(Offset(420f, 580f))
        r.wait(6.0)
        r.stepLabel = "Passo 5 di $tot"
        r.clickOn(r.rect("Info", exact = true), "Per le misure esatte clicca «Info», in basso a destra.", readSeconds = 3.0)
        r.wait(1.0)
        r.stepLabel = "Passo 6 di $tot"
        r.highlight = r.rectOf(r.field("Lunghezza")).let { t -> Rect(t.left, t.top - 6f, r.rectOf(r.field("Altezza")).right, t.bottom) }
        r.caption = "Nella scheda del muro scrivi lunghezza, spessore e altezza. Più sopra scegli se il muro c'è già, è da demolire o è nuovo (per le pratiche edilizie)."
        r.moveTo(Offset(1150f, 620f))
        r.wait(6.0)
        val sp = r.field("Spessore")
        r.clickOn(r.rectOf(sp), "Per esempio un tramezzo in cartongesso è sottile: in «Spessore» scriviamo 8.", readSeconds = 3.5)
        r.type(sp, "8")
        r.wait(1.5)
        r.stepLabel = "Passo 7 di $tot"
        r.clickOn(r.rect("▼", exact = true), "Clicca «▼» per chiudere la scheda.", readSeconds = 2.5)
        r.caption = "Fatto: lo studio ora è diviso da un tramezzo. Anche ai muri singoli puoi aggiungere porte e finestre."
        r.moveTo(Offset(900f, 450f))
        r.wait(5.0)
    }

    /** Apre un progetto nuovo dall'elenco dei progetti: ⋮ → Progetti → Nuovo progetto. */
    private fun nuovoProgetto(r: Regista, nome: String, tot: Int) {
        r.stepLabel = "Passo 1 di $tot"
        r.clickOn(r.rect("⋮", exact = true), "Questo modo si usa per la prima stanza di un progetto nuovo. Clicca «⋮», in alto a destra.", readSeconds = 4.0)
        r.wait(0.6)
        r.clickOn(r.rect("Progetti", exact = false), "Clicca «Progetti».", readSeconds = 2.0)
        r.wait(0.8)
        r.stepLabel = "Passo 2 di $tot"
        r.clickOn(r.rect("Nuovo progetto"), "Nell'elenco dei progetti clicca «+ Nuovo progetto».", readSeconds = 2.5)
        r.wait(0.6)
        val n = r.field("Nome del progetto")
        r.clickOn(r.rectOf(n), "Scrivi il nome del progetto…", readSeconds = 1.5)
        r.type(n, nome)
        r.wait(0.5)
        r.clickOn(r.rect("Salva", exact = true), "…e clicca «Salva». Il progetto nuovo si apre con la scelta della prima stanza.", readSeconds = 2.5)
        r.wait(1.0)
    }

    /** Modo 6: carichi la tua planimetria, la metti in scala e costruisci le stanze sopra, spostando muri e angoli. */
    private fun sopraLaPlanimetria(r: Regista) {
        r.titleCard("Modo 6 · Sopra la tua planimetria", "Carichi la piantina che hai e costruisci le stanze sopra")
        r.chapter = "Modo 6 · Sopra la tua planimetria"
        r.highlight = null
        r.caption = "Hai già una planimetria, del catasto, del progetto o anche solo una foto? Caricala in sagoma e costruisci le stanze sopra, facendole combaciare con quelle disegnate."
        r.wait(6.5)
        // «Ricalca una piantina» c'è solo nella scelta della prima stanza di un progetto: serve un progetto nuovo.
        val nuovo = r.nodes().none { n -> r.texts(n).any { it.contains("Ricalca una piantina") } }
        val base = if (nuovo) 2 else 0
        val tot = base + 14
        fun passo(k: Int) { r.stepLabel = "Passo ${base + k} di $tot" }
        if (nuovo) nuovoProgetto(r, "Casa mia", tot)
        passo(1)
        r.clickOn(r.rect("Ricalca una piantina"), "Nella scelta della prima stanza clicca «Ricalca una piantina».", readSeconds = 3.0)
        r.wait(1.0)
        passo(2)
        r.clickOn(r.rect("Foto / immagine"), "Clicca «Foto / immagine» e scegli sul computer il file della planimetria: una foto, una scansione o un'immagine. Per un PDF c'è il pulsante «PDF».", readSeconds = 5.0)
        r.wait(1.5)
        passo(3)
        r.highlight = r.rect("Taratura: tocca")
        r.caption = "La planimetria ora è sotto il disegno, ma sagoma non sa ancora quanto è grande: va messa in scala con una misura scritta sopra. Usiamo il muro in alto del soggiorno: 640 cm."
        r.moveTo(Offset(700f, 420f))
        r.wait(7.0)
        passo(4)
        val a = { sfondo(PiantinaDiProva.px(0f), PiantinaDiProva.py(0f)) }
        val b = { sfondo(PiantinaDiProva.px(640f), PiantinaDiProva.py(0f)) }
        fun box(p: Offset, k: Float = 22f) = Rect(p.x - k, p.y - k, p.x + k, p.y + k)
        r.highlight = box(a())
        r.caption = "Clicca dove comincia la misura: l'angolo interno in alto a sinistra del soggiorno."
        r.follow(a, 1.4)
        r.wait(3.0)
        r.click()
        r.wait(0.6)
        r.highlight = box(b())
        r.caption = "Poi clicca dove finisce: l'angolo interno in alto a destra del soggiorno."
        r.follow(b, 1.6)
        r.wait(2.5)
        r.click()
        r.highlight = null
        r.wait(1.0)
        passo(5)
        val cm = r.field("cm")
        r.clickOn(r.rectOf(cm), "Nella finestra «Misura reale» scrivi la misura vera tra i due punti, quella scritta sulla piantina: 640.", readSeconds = 4.5)
        r.type(cm, "640")
        r.wait(0.6)
        r.clickOn(r.rect("Tara", exact = true), "Clicca «Tara»: adesso la planimetria è in scala, ogni centimetro è giusto.", readSeconds = 3.0)
        r.wait(1.5)
        passo(6)
        r.highlight = r.rect("Piantina ·").let { t -> Rect(t.left - 8f, t.top - 16f, r.rect("Chiudi", exact = true).right, r.rect("Disegno ·").bottom + 40f) }
        r.caption = "A destra c'è il pannello «Piantina e disegno»: qui rendi la piantina più chiara o più scura, oppure la nascondi. Per ora chiudilo con «Chiudi»."
        r.moveTo(Offset(1150f, 520f))
        r.wait(6.0)
        r.clickOn(r.rect("Chiudi", exact = true), readSeconds = 1.5)
        r.wait(0.8)
        passo(7)
        r.highlight = r.rect("Esci", exact = true)
        r.caption = "Parte «Muro per muro» sopra la piantina: potresti cliccare gli angoli delle stanze uno a uno. Qui invece usiamo le forme che conosci già e le aggiustiamo sulla piantina. Clicca «Esci»."
        r.moveTo(r.highlight!!.let { Offset(it.left + it.width / 2, it.top + it.height / 2) })
        r.wait(6.5)
        r.click()
        r.highlight = null
        r.wait(1.0)
        passo(8)
        r.clickOn(r.rect("Quadrato", exact = true), "Torna la scelta della forma. Cominciamo dalla camera, che è un rettangolo: partiamo da un quadrato, per vedere come si allunga. Clicca «Quadrato».", readSeconds = 5.0)
        r.wait(0.6)
        r.clickOn(r.rect("Camera da letto", exact = true), "Scegli «Camera da letto»…", readSeconds = 2.0)
        val lato = r.field("Lato")
        r.clickOn(r.rectOf(lato), "…e scrivi il lato corto della camera, letto sulla piantina: 330.", readSeconds = 3.0)
        r.type(lato, "330")
        r.wait(0.5)
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca «Crea stanza».", readSeconds = 2.0)
        r.wait(1.5)
        passo(9)
        r.highlight = null
        r.caption = "La camera c'è, ma non ancora al suo posto sulla piantina. Per vedere anche la piantina rimpicciolisci: gira la rotellina del mouse verso di te."
        r.wait(2.5)
        stanza(RoomType.Camera).exterior().let { e ->
            inquadra(r, Vec2(e.minOf { it.x }, e.minOf { it.y }), suPiantina(1080f, 640f))
        }
        r.wait(1.0)
        prova(r, "q1")
        // La camera va spostata finché il suo filo interno in alto a sinistra coincide con quello della camera sulla piantina.
        val dove = riquadro(suPiantina(650f, 0f), suPiantina(1050f, 330f))
        r.highlight = dove
        r.caption = "Ecco dove va: sopra la camera disegnata sulla piantina."
        r.moveTo(Offset(dove.left + dove.width / 2, dove.top + dove.height / 2))
        r.wait(3.5)
        val camera = stanza(RoomType.Camera)
        val etichetta = Polygon.labelPoint(camera.points)
        val sposta = suPiantina(650f, 0f) - stanzaInterno(camera).first
        val arrivo = pianta(etichetta + sposta)
        r.highlight = riquadro(stanzaInterno(camera).first, stanzaInterno(camera).second)
        r.caption = "Per spostare una stanza premi sul suo nome, tieni premuto il tasto del mouse e trascinala."
        r.moveTo(pianta(etichetta))
        r.wait(3.5)
        r.highlight = dove
        r.caption = "Lasciala quando l'angolo in alto a sinistra è sopra quello della camera disegnata."
        r.drag({ arrivo }, seconds = 2.4)
        r.highlight = null
        r.wait(1.5)
        prova(r, "q2")
        passo(10)
        val quadrata = stanza(RoomType.Camera)
        // Muro 1 (a destra): si prende un po' sopra la metà, lontano dalla misura scritta sul muro.
        val presa = quadrata.points[1] + (quadrata.points[2] - quadrata.points[1]) * 0.3
        val allunga = quadrata.interior().minOf { it.x } + 400.0 - quadrata.interior().maxOf { it.x }
        val muroDestro = pianta(presa)
        r.highlight = Rect(muroDestro.x - 14f, pianta(quadrata.points[1]).y, muroDestro.x + 14f, pianta(quadrata.points[2]).y)
        r.caption = "La camera sulla piantina è più larga: 400. Basta spostare il muro di destra. Porta il puntatore sul muro…"
        r.moveTo(muroDestro)
        r.wait(4.0)
        r.highlight = dove
        r.caption = "…tieni premuto e trascinalo verso destra fino al muro della piantina. Mentre lo sposti la misura cambia: lasciala a 400."
        r.drag({ pianta(presa + Vec2(allunga, 0.0)) }, seconds = 3.0)
        r.highlight = null
        r.caption = "Fatto: il quadrato è diventato un rettangolo di 4,00 × 3,30 m, proprio come la camera della piantina."
        r.moveTo(Offset(900f, 640f))
        r.wait(4.5)
        prova(r, "q4")
        if (System.getenv("PROVA_FINO") == "q4") return
        passo(11)
        r.clickOn(r.rect("Elementi architettonici"), "Adesso il bagno, che è storto: anche qui partiamo da un quadrato. «Elementi architettonici»…", readSeconds = 3.5)
        r.wait(0.6)
        r.clickOn(r.rect("▭ Stanza"), "…«Stanza»…", readSeconds = 1.2)
        r.wait(0.8)
        r.clickOn(r.rect("Quadrato", exact = true), "…«Quadrato»…", readSeconds = 1.2)
        r.wait(0.6)
        r.clickOn(r.rect("Bagno", exact = true), "…«Bagno»…", readSeconds = 1.2)
        val lato3 = r.field("Lato")
        r.clickOn(r.rectOf(lato3), "…lato 260, il muro in alto…", readSeconds = 2.0)
        r.type(lato3, "260")
        r.wait(0.5)
        r.clickOn(r.rect("Crea stanza", exact = true), "…e «Crea stanza».", readSeconds = 1.5)
        r.wait(1.5)
        prova(r, "q5")
        passo(12)
        stanza(RoomType.Bagno).exterior().let { e ->
            inquadra(r, Vec2(minOf(e.minOf { it.x }, suPiantina(640f, 0f).x), e.minOf { it.y }), Vec2(e.maxOf { it.x }, suPiantina(1080f, 640f).y))
        }
        val bagno = stanza(RoomType.Bagno)
        val etichettaB = Polygon.labelPoint(bagno.points)
        val spostaB = suPiantina(790f, 345f) - stanzaInterno(bagno).first
        val arrivoB = pianta(etichettaB + spostaB)
        r.highlight = riquadro(suPiantina(790f, 345f), suPiantina(1050f, 615f))
        r.caption = "Il bagno va sotto la camera: trascinalo dal suo nome sopra il bagno della piantina, con l'angolo in alto a sinistra al suo posto."
        r.moveTo(pianta(etichettaB))
        r.wait(4.0)
        r.drag({ arrivoB }, seconds = 2.4)
        r.highlight = null
        r.wait(1.0)
        prova(r, "q6")
        passo(13)
        // Vista sul bagno: si trascina un punto vuoto della pianta (sposta la vista) e si ingrandisce con la rotellina.
        val vuoto = pianta(suPiantina(300f, 400f))
        val centroBagno = { pianta(Polygon.labelPoint(stanza(RoomType.Bagno).points)) }
        r.caption = "Gli angoli del bagno non sono a 90°: avviciniamoci. Per spostare la vista premi in un punto vuoto della pianta e trascina."
        r.moveTo(vuoto)
        r.wait(3.0)
        val sposta2 = Offset(700f, 430f) - centroBagno()
        r.drag({ vuoto + sposta2 }, seconds = 1.6)
        r.caption = "Poi ingrandisci: porta il puntatore sul bagno e gira la rotellina in avanti."
        r.moveTo(centroBagno() + Offset(0f, 70f))
        r.wait(2.5)
        r.scroll(-4f, steps = 4)
        r.wait(1.0)
        prova(r, "q7")
        passo(14)
        // Angoli C e D sopra quelli della piantina: si trascina il pallino (mezzeria) dove il filo interno combacia.
        val giusto = Polygon.inset(PiantinaDiProva.storta.punti.map { (x, y) -> suPiantina(x, y) }, -stanza(RoomType.Bagno).wallThickness / 2)
        val spiega = mapOf(
            2 to "Ora sposta gli angoli. Premi sul pallino blu dell'angolo in basso a destra e trascinalo finché l'angolo combacia con quello della piantina.",
            3 to "Poi l'angolo in basso a sinistra: qui basta un po' più giù. Le misure dei muri si aggiornano mentre lo sposti.",
        )
        for ((i, testo) in spiega) {
            val da = pianta(stanza(RoomType.Bagno).points[i])
            val a2 = giusto[i]
            r.highlight = box(da, 18f)
            r.caption = testo
            r.moveTo(da)
            r.wait(3.5)
            r.highlight = box(pianta(a2), 18f)
            r.drag({ pianta(a2) }, seconds = 2.0)
            r.highlight = null
            r.wait(1.2)
            if (System.getenv("PROVA") != null) File("build/video/angolo-$i.txt").writeText("voluto ${a2}\nottenuto ${stanza(RoomType.Bagno).points[i]}\ninterno ${stanza(RoomType.Bagno).interior()}\nscala ${vm().state.value.camera.scale}")
        }
        prova(r, "q8")
        r.caption = "Ecco il bagno storto, uguale a quello della piantina."
        r.moveTo(Offset(700f, 600f))
        r.wait(3.5)
        r.clickOn(r.rect("Adatta", exact = true), "Per rivedere tutto il disegno clicca «Adatta».", readSeconds = 2.5)
        r.wait(1.0)
        r.caption = "Fai lo stesso con le altre stanze: una forma per partire, poi muri e angoli al loro posto, sopra la tua planimetria."
        r.moveTo(Offset(900f, 640f))
        r.wait(6.0)
    }

    /**
     * Fuori dal video: un progetto nuovo con l'appartamento di prova già disegnato sopra la piantina (con «Leggi la piantina da
     * sola» e il lettore finto, senza terrazza e balcone, che si mettono nel video). Le mini guide degli elementi del video
     * tornano da vedere, così compaiono come la prima volta.
     */
    private fun preparaAppartamento(r: Regista, tips: TipStore) {
        tips.save(tips.seen() - listOf(Tip.Opening, Tip.Fixture, Tip.Stair, Tip.Outdoor).map { it.name }.toSet())
        PiantinaDiProva.conEsterni = false
        if (r.nodes().any { n -> r.texts(n).any { it.contains("Ricalca una piantina") } }) r.clickOn(r.rect("Progetti", exact = false), readSeconds = 0.1)
        else {
            r.clickOn(r.rect("⋮", exact = true), readSeconds = 0.1)
            r.wait(0.3)
            r.clickOn(r.rect("Progetti", exact = false), readSeconds = 0.1)
        }
        r.wait(0.5)
        r.clickOn(r.rect("Nuovo progetto"), readSeconds = 0.1)
        r.wait(0.3)
        r.type(r.field("Nome del progetto"), "Appartamento di prova")
        r.clickOn(r.rect("Salva", exact = true), readSeconds = 0.1)
        r.wait(0.8)
        r.clickOn(r.rect("Ricalca una piantina"), readSeconds = 0.1)
        r.wait(0.5)
        r.clickOn(r.rect("Leggi la piantina da sola"), readSeconds = 0.1)
        // Il lettore finto risponde dopo qualche secondo vero: si aspetta che le stanze siano sulla pianta.
        repeat(80) { if (vm().state.value.plan.rooms.size < PiantinaDiProva.stanze.size) r.wait(0.25) }
        check(vm().state.value.plan.rooms.size == PiantinaDiProva.stanze.size) { "la lettura finta non è arrivata" }
        PiantinaDiProva.conEsterni = true
        r.wait(0.5)
        runCatching { r.clickOn(r.rect("Chiudi", exact = true), readSeconds = 0.1) }
        r.wait(0.5)
        // Si vede tutta la piantina, anche dove andranno balcone e terrazza.
        inquadra(r, suPiantina(-40f, -180f), suPiantina(1060f, 890f))
        r.wait(0.5)
        prova(r, "e0")
        r.cursor = Offset(900f, 600f)
    }

    /** Porte, finestre, balconi e terrazze, impianti e scale: come si mettono; il resto si cambia da Info. */
    private fun elementi(r: Regista) {
        r.titleCard("Porte, finestre e altri elementi", "Porte, finestre, balconi, impianti e scale")
        r.chapter = "Porte, finestre e altri elementi"
        val tot = 9
        fun passo(k: Int) { r.stepLabel = "Passo $k di $tot" }
        fun box(p: Offset, k: Float = 22f) = Rect(p.x - k, p.y - k, p.x + k, p.y + k)
        r.highlight = null
        r.stepLabel = ""
        r.caption = "Partiamo dall'appartamento di prova già disegnato. Porte, finestre e tutto il resto si aggiungono dal pulsante «Elementi architettonici»."
        r.moveTo(Offset(700f, 500f))
        r.wait(6.0)
        fun menu(vararg voci: Pair<String, String>) {
            r.clickOn(r.rect("Elementi architettonici"), voci.first().second, readSeconds = 3.0)
            r.wait(0.5)
            for ((voce, spiega) in voci.drop(1)) {
                // Le voci con un sottomenu finiscono con «  ▸».
                val dove = runCatching { r.rect(voce, exact = true) }.getOrElse { r.rect("$voce  ▸", exact = true) }
                r.clickOn(dove, spiega, readSeconds = 2.2)
                r.wait(0.5)
            }
        }
        /** La mini guida che si apre la prima volta: si legge e si chiude con «Ho capito». */
        fun miniGuida(spiega: String) {
            r.wait(0.8)
            val card = r.rect("💡", exact = false).let { t -> Rect(t.left - 60f, t.top - 16f, r.rect("Ho capito", exact = true).right + 8f, r.rect("Ho capito", exact = true).bottom + 4f) }
            r.highlight = card
            r.caption = spiega
            r.moveTo(Offset(card.right - 60f, card.bottom + 40f))
            r.wait(6.5)
            r.clickOn(r.rect("Ho capito", exact = true), "Quando hai letto, clicca «Ho capito»: non si apre più.", readSeconds = 2.5)
            r.wait(0.6)
        }

        // 1. Porta tra ingresso e soggiorno.
        passo(1)
        menu(
            "Elementi architettonici" to "Cominciamo da una porta tra l'ingresso e il soggiorno. Clicca «Elementi architettonici»…",
            "🚪 Porte" to "…poi «Porte»…",
            "Porta ad 1 anta" to "…e scegli il tipo, per esempio «Porta ad 1 anta».",
        )
        val porta = pianta(suPiantina(495f, 465f))
        r.highlight = box(porta, 26f)
        r.caption = "Ora clicca sul muro, nel punto dove va la porta."
        r.moveTo(porta)
        r.wait(3.0)
        r.click()
        r.highlight = null
        prova(r, "e1")
        miniGuida("La prima volta che metti una porta si apre una mini guida che spiega cosa puoi fare: spostarla trascinandola lungo il muro, cambiare misure e verso di apertura da «Info».")
        // 2. Finestra sul muro in alto del soggiorno.
        passo(2)
        menu(
            "Elementi architettonici" to "Una finestra si mette allo stesso modo: «Elementi architettonici»…",
            "🪟 Finestre" to "…«Finestre»…",
            "Finestra a 2 ante" to "…«Finestra a 2 ante».",
        )
        val finestra = pianta(suPiantina(320f, -15f))
        r.highlight = box(finestra, 26f)
        r.caption = "Clicca sul muro esterno del soggiorno, dove va la finestra."
        r.moveTo(finestra)
        r.wait(2.5)
        r.click()
        r.highlight = null
        r.wait(1.5)
        prova(r, "e2")
        // 3. Balcone sopra la camera, con la porta-finestra.
        passo(3)
        menu(
            "Elementi architettonici" to "Ora il balcone, sopra la camera. «Elementi architettonici»…",
            "🌿 Balconi e terrazze" to "…«Balconi e terrazze»…",
            "Balcone" to "…«Balcone».",
        )
        prova(r, "e3")
        r.clickOn(r.rect("Rettangolo", exact = true), "Si sceglie la forma come per le stanze: il balcone è un rettangolo.", readSeconds = 3.0)
        r.wait(0.5)
        val larg = r.field("Larghezza")
        r.clickOn(r.rectOf(larg), "Scrivi le misure della piantina: larghezza 300…", readSeconds = 2.0)
        r.type(larg, "300")
        val prof = r.field("Profondità")
        r.clickOn(r.rectOf(prof), "…e profondità 140.", readSeconds = 1.5)
        r.type(prof, "140")
        r.wait(0.4)
        r.clickOn(r.rect("Crea", exact = true), "Clicca «Crea».", readSeconds = 2.0)
        r.wait(1.5)
        prova(r, "e4")
        miniGuida("Anche il balcone ha la sua mini guida: al posto dei muri c'è il parapetto, e da «Info» scegli ringhiera, muretto o vetro.")
        passo(4)
        val balcone = stanza(RoomType.Balcone)
        val etB = Polygon.labelPoint(balcone.points)
        val arrivoB = pianta(etB + (suPiantina(700f, -170f) - stanzaInterno(balcone).first))
        r.highlight = riquadro(suPiantina(700f, -170f), suPiantina(1000f, -30f))
        r.caption = "Trascina il balcone dal suo nome fino al suo posto sulla piantina, sopra la camera."
        r.moveTo(pianta(etB))
        r.wait(3.0)
        r.drag({ arrivoB }, seconds = 2.2)
        r.highlight = null
        r.wait(1.0)
        prova(r, "e5")
        menu(
            "Elementi architettonici" to "Per uscire sul balcone serve una porta-finestra: «Elementi architettonici»…",
            "🚪 Porte-finestre" to "…«Porte-finestre»…",
            "Balcone a 2 ante" to "…«Balcone a 2 ante».",
        )
        val pf = pianta(suPiantina(850f, -15f))
        r.highlight = box(pf, 26f)
        r.caption = "Clicca sul muro tra la camera e il balcone."
        r.moveTo(pf)
        r.wait(2.5)
        r.click()
        r.highlight = null
        r.wait(1.5)
        // 4. Terrazza a sud, sotto studio e ingresso.
        passo(5)
        menu(
            "Elementi architettonici" to "La terrazza si fa come il balcone: «Elementi architettonici»…",
            "🌿 Balconi e terrazze" to "…«Balconi e terrazze»…",
            "Terrazza" to "…«Terrazza».",
        )
        r.clickOn(r.rect("Rettangolo", exact = true), "«Rettangolo»…", readSeconds = 1.5)
        r.wait(0.5)
        val lt = r.field("Larghezza")
        r.clickOn(r.rectOf(lt), "…larghezza 640…", readSeconds = 1.2)
        r.type(lt, "640")
        val pt = r.field("Profondità")
        r.clickOn(r.rectOf(pt), "…profondità 240…", readSeconds = 1.2)
        r.type(pt, "240")
        r.clickOn(r.rect("Crea", exact = true), "…«Crea».", readSeconds = 1.5)
        r.wait(1.5)
        val terrazza = stanza(RoomType.Terrazza)
        val etT = Polygon.labelPoint(terrazza.points)
        val arrivoT = pianta(etT + (suPiantina(-20f, 640f) - stanzaInterno(terrazza).first))
        r.highlight = riquadro(suPiantina(-20f, 640f), suPiantina(620f, 880f))
        r.caption = "Trascinala al suo posto, a sud, sotto lo studio e l'ingresso."
        r.moveTo(pianta(etT))
        r.wait(3.0)
        r.drag({ arrivoT }, seconds = 2.2)
        r.highlight = null
        r.wait(1.5)
        prova(r, "e6")
        // 5. Impianti: calorifero sul muro, lampadario nella stanza.
        passo(6)
        menu(
            "Elementi architettonici" to "Gli impianti: termosifoni, prese, interruttori, punti acqua e luci. «Elementi architettonici»…",
            "🔌 Impianti" to "…«Impianti»…",
            "Calorifero" to "…«Calorifero».",
        )
        val cal = pianta(suPiantina(-15f, 520f))
        r.highlight = box(cal, 26f)
        r.caption = "Termosifoni, prese, interruttori e punti acqua si mettono cliccando sul muro: qui il muro esterno dello studio."
        r.moveTo(cal)
        r.wait(3.5)
        r.click()
        r.highlight = null
        prova(r, "e7")
        miniGuida("Mini guida degli impianti: quelli a muro scorrono lungo il muro, e da «Info» imposti altezza da terra e misure.")
        passo(7)
        menu(
            "Elementi architettonici" to "Le luci invece vanno dentro la stanza. «Elementi architettonici»…",
            "🔌 Impianti" to "…«Impianti»…",
            "Luci" to "…«Luci»…",
            "Lampadario" to "…per esempio «Lampadario».",
        )
        val luce = pianta(suPiantina(330f, 110f))
        r.highlight = box(luce, 26f)
        r.caption = "Clicca nella stanza, dove va la luce: qui nel soggiorno."
        r.moveTo(luce)
        r.wait(2.5)
        r.click()
        r.highlight = null
        r.wait(1.5)
        prova(r, "e8")
        // 6. Scala nello studio.
        passo(8)
        val studio = pianta(suPiantina(120f, 540f))
        r.clickOn(box(studio, 30f), "Ultima, la scala. Prima clicca nella stanza dove va, per esempio lo studio…", readSeconds = 3.0)
        r.wait(0.6)
        menu(
            "Elementi architettonici" to "…poi «Elementi architettonici»…",
            "🪜 Scale" to "…«Scale»…",
            "A chiocciola" to "…e scegli la forma, per esempio «A chiocciola».",
        )
        r.wait(1.0)
        prova(r, "e9")
        miniGuida("Quando metti una scala si apre la sua mini guida, che ti spiega tutto: come spostarla e accostarla a un muro, e cosa cambi da «Info» (tipo, larghezza, gradini, verso).")
        // 7. Info.
        passo(9)
        r.clickOn(r.rect("Info", exact = true), "La scala è selezionata. Per tutte le altre modifiche clicca «Info»: vale per ogni elemento, porte, finestre, balconi, impianti e scale.", readSeconds = 5.0)
        r.wait(1.2)
        prova(r, "e10")
        r.highlight = null
        r.caption = "Nella scheda «Info» cambi misure, tipo e tutto il resto dell'elemento selezionato. Per chiuderla clicca «▼»."
        r.moveTo(Offset(1000f, 600f))
        r.wait(6.0)
        r.clickOn(r.rect("▼", exact = true), readSeconds = 1.5)
        r.caption = "Ecco l'appartamento con porte, finestre, balcone, terrazza, impianti e scala. Per ogni elemento: lo scegli da «Elementi architettonici», lo metti sulla pianta, e il resto da «Info»."
        r.moveTo(Offset(900f, 640f))
        r.wait(7.0)
    }

    private fun stanza(type: RoomType) = vm().state.value.plan.rooms.last { it.type == type }

    /** Angoli in alto a sinistra e in basso a destra del filo interno di una stanza (cm). */
    private fun stanzaInterno(room: com.sagoma.planimetria.model.Room): Pair<Vec2, Vec2> =
        room.interior().let { p -> Vec2(p.minOf { it.x }, p.minOf { it.y }) to Vec2(p.maxOf { it.x }, p.maxOf { it.y }) }

    /**
     * Rimpicciolisce con la rotellina finché il rettangolo della pianta tra `a` e `b` (cm) si vede tutto, lontano dai
     * pulsanti: il puntatore va prima nel punto da cui la rotellina porta il rettangolo al centro della vista.
     */
    private fun inquadra(r: Regista, a: Vec2, b: Vec2) {
        val vista = Rect(60f, 120f, 1180f, 800f)
        val p0 = pianta(a)
        val p1 = pianta(b)
        if (p0.x >= vista.left && p1.x <= vista.right && p0.y >= vista.top && p1.y <= vista.bottom) return
        val serve = maxOf((p1.x - p0.x) / vista.width, (p1.y - p0.y) / vista.height)
        var n = maxOf(1, ceil(ln(serve.toDouble()) / ln(1.1)).toInt())
        val centro = Offset((p0.x + p1.x) / 2, (p0.y + p1.y) / 2)
        val meta = Offset(vista.left + vista.width / 2, vista.top + vista.height / 2)
        while (true) {
            // Dopo n scatti tutto si avvicina al puntatore P di un fattore f: P + (centro − P)·f = meta.
            val f = 1.1.pow(-n).toFloat()
            val p = (meta - centro * f) / (1 - f)
            if (p.x in 20f..1180f && p.y in 100f..820f) {
                r.moveTo(p)
                r.wait(1.0)
                r.scroll(n.toFloat(), steps = n)
                return
            }
            n++
        }
    }

    /** Riquadro sullo schermo tra due punti della pianta (cm). */
    private fun riquadro(a: Vec2, b: Vec2): Rect = pianta(a).let { p -> pianta(b).let { q -> Rect(p.x, p.y, q.x, q.y) } }

    /** Il punto (`x`, `y`) della piantina di prova (cm sul filo interno, come in [PiantinaDiProva]) nella pianta di sagoma. */
    private fun suPiantina(x: Float, y: Float): Vec2 {
        val u = vm().state.value.plan.underlay ?: error("manca la piantina di sfondo")
        return Vec2(u.origin.x + PiantinaDiProva.px(x) * u.cmPerPx, u.origin.y + PiantinaDiProva.py(y) * u.cmPerPx)
    }

    /** Fotografia e testi dello schermo per controllare un punto del capitolo (solo con PROVA). */
    private fun prova(r: Regista, nome: String) {
        if (System.getenv("PROVA") == null) return
        val s = vm().state.value
        File("build/video/dump-$nome.txt").writeText(r.dump() + "\ncamera ${s.camera}\nunderlay ${s.plan.underlay}\nrooms ${s.plan.rooms.map { it.name to it.points }}\nwallDraw ${s.wallDraw}\ncreation ${s.creation}")
        r.snapshot(File("build/video/shot-$nome.png"))
    }

    /** Modo 7: Ricalca una piantina. */
    private fun ricalca(r: Regista) {
        r.titleCard("Modo 7 · Ricalca una piantina", "Hai la piantina del catasto o del progetto? Ripassala con il mouse")
        r.chapter = "Modo 7 · Ricalca una piantina"
        val tot = 9
        r.highlight = null
        r.caption = "Se hai una piantina (foto, scansione o PDF), la metti sotto il disegno in scala e ripassi i muri. Vediamo come."
        r.wait(4.5)
        nuovoProgetto(r, "Appartamento", tot)
        File("build/video/dump-k1.txt").writeText(r.dump()); r.snapshot(File("build/video/shot-k1.png"))
        r.stepLabel = "Passo 3 di $tot"
        r.clickOn(r.rect("Ricalca una piantina"), "Clicca «Ricalca una piantina».", readSeconds = 2.5)
        r.wait(1.0)
        File("build/video/dump-k2.txt").writeText(r.dump()); r.snapshot(File("build/video/shot-k2.png"))
        r.stepLabel = "Passo 4 di $tot"
        r.clickOn(r.rect("Foto / immagine"), "Clicca «Foto / immagine» e scegli sul computer il file della piantina (per un PDF c'è il pulsante «PDF»).", readSeconds = 4.5)
        r.wait(1.5)
        r.stepLabel = "Passo 5 di $tot"
        r.highlight = r.rect("Taratura: tocca")
        r.caption = "La piantina è sotto il disegno, ma sagoma non sa ancora quanto è grande: va «tarata» con una misura che conosci. Qui usiamo il lato lungo del soggiorno: 6,00 m."
        r.moveTo(Offset(400f, 300f))
        r.wait(7.0)
        r.stepLabel = "Passo 6 di $tot"
        val a = { sfondo(120f, 140f) }
        val b = { sfondo(720f, 140f) }
        fun box(p: Offset) = Rect(p.x - 22f, p.y - 22f, p.x + 22f, p.y + 22f)
        r.highlight = box(a())
        r.caption = "Clicca l'inizio della misura: l'angolo interno in alto a sinistra del soggiorno (punto A)."
        r.follow(a, 1.4)
        r.wait(3.0)
        r.click()
        r.wait(0.8)
        r.highlight = box(b())
        r.caption = "Poi clicca la fine della misura: l'angolo interno in alto a destra (punto B)."
        r.follow(b, 1.6)
        r.wait(2.5)
        r.click()
        r.highlight = null
        r.wait(1.0)
        r.stepLabel = "Passo 7 di $tot"
        val cm = r.field("cm")
        r.clickOn(r.rectOf(cm), "Si apre «Misura reale»: scrivi quanto è lunga davvero la distanza tra A e B, in centimetri. Qui 600.", readSeconds = 4.5)
        r.type(cm, "600")
        r.wait(0.6)
        r.clickOn(r.rect("Tara", exact = true), "Clicca «Tara»: ora la piantina è in scala.", readSeconds = 2.5)
        r.wait(1.5)
        r.stepLabel = "Passo 8 di $tot"
        r.highlight = r.rect("Clicca sugli angoli della piantina").let { t -> Rect(t.left, t.top - 24f, t.right, t.bottom) }
        r.caption = "Subito dopo parte «Muro per muro» sopra la piantina: adesso basta cliccare gli angoli di ogni stanza."
        r.moveTo(Offset(900f, 450f))
        r.wait(5.0)
        r.clickOn(r.rect("−", exact = true), "Prima clicca «−» per vedere tutta la piantina.", readSeconds = 2.5)
        r.wait(1.2)
        File("build/video/dump-k5.txt").writeText(r.dump() + "\n" + vm().state.value.camera + "\n" + vm().state.value.plan.underlay); r.snapshot(File("build/video/shot-k5.png"))
        if (System.getenv("STOP_K") != null) return
        r.stepLabel = "Passo 9 di $tot"
        // Angoli interni della cucina sulla piantina (pixel dell'immagine), poi sullo schermo secondo la vista del momento.
        val angoli = listOf(120f to 600f, 470f to 600f, 470f to 840f, 120f to 840f)
        val spiega = listOf(
            "Ricalchiamo la cucina. Clicca il primo angolo, all'interno dei muri: in alto a sinistra.",
            "Clicca l'angolo dopo, in alto a destra. Mentre disegni la pianta si sposta per seguirti: è normale.",
            "Poi l'angolo in basso a destra…",
            "…e quello in basso a sinistra.",
        )
        fun box(p: Offset, k: Float = 20f) = Rect(p.x - k, p.y - k, p.x + k, p.y + k)
        for ((i, a) in angoli.withIndex()) {
            val target = { sfondo(a.first, a.second) }
            r.caption = spiega[i]
            r.highlight = box(target())
            r.follow(target)
            r.highlight = box(target())
            r.wait(if (i == 0) 3.5 else 2.0)
            r.follow(target, 0.2)
            r.click()
            r.wait(0.5)
        }
        val primo = { sfondo(angoli[0].first, angoli[0].second) }
        r.caption = "Per chiudere la stanza clicca di nuovo il primo angolo (quello cerchiato), oppure «Chiudi stanza»."
        r.highlight = box(primo(), 24f)
        r.follow(primo)
        r.highlight = box(primo(), 24f)
        r.wait(3.0)
        r.follow(primo, 0.2)
        r.click()
        r.highlight = null
        r.wait(1.0)
        r.clickOn(r.rect("Cucina", exact = true), "Nella finestra «Nuova stanza» scegli «Cucina».", readSeconds = 3.0)
        r.wait(0.5)
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca «Crea stanza».", readSeconds = 2.0)
        r.caption = "Ecco la cucina, ricalcata con le misure giuste. Fai lo stesso per le altre stanze: si agganciano da sole ai muri già fatti."
        r.moveTo(Offset(900f, 480f))
        r.wait(6.0)
        File("build/video/dump-k6.txt").writeText(r.dump()); r.snapshot(File("build/video/shot-k6.png"))
    }

    private lateinit var store: ViewModelStore

    /** Il ViewModel del progetto aperto (cambia aprendo un altro progetto). */
    @Suppress("RestrictedApi")
    private fun vm(): EditorViewModel = store.keys().mapNotNull { store[it] as? EditorViewModel }.single()

    /** Modo 8: ✨ Leggi la piantina da sola. */
    private fun leggi(r: Regista) {
        r.titleCard("Modo 8 · Leggi la piantina da sola", "sagoma guarda la piantina e disegna lei tutte le stanze")
        r.chapter = "Modo 8 · ✨ Leggi la piantina da sola"
        val tot = 6
        r.highlight = null
        r.caption = "Il modo più veloce: carichi la piantina e sagoma disegna da sola stanze, porte e finestre. Serve internet."
        r.wait(4.5)
        nuovoProgetto(r, "Appartamento letto", tot)
        r.stepLabel = "Passo 3 di $tot"
        r.clickOn(r.rect("Ricalca una piantina"), "Clicca «Ricalca una piantina».", readSeconds = 2.5)
        r.wait(1.0)
        r.stepLabel = "Passo 4 di $tot"
        r.clickOn(r.rect("Leggi la piantina da sola"), "Clicca «✨ Leggi la piantina da sola» e scegli il file della piantina sul computer (foto, scansione o PDF).", readSeconds = 4.5)
        r.stepLabel = "Passo 5 di $tot"
        r.caption = "sagoma sta leggendo la piantina: può volerci anche un minuto. Aspetta senza chiudere la finestra."
        r.wait(5.0)
        File("build/video/dump-l1.txt").writeText(r.dump()); r.snapshot(File("build/video/shot-l1.png"))
        r.stepLabel = "Passo 6 di $tot"
        r.caption = "Fatto! Tutte le stanze sono disegnate sopra la piantina, con porte e finestre. Controlla sempre le misure e correggi dove serve."
        r.moveTo(Offset(900f, 480f))
        r.wait(6.0)
        File("build/video/dump-l2.txt").writeText(r.dump()); r.snapshot(File("build/video/shot-l2.png"))
    }

    /** Dove si vede ora sullo schermo il punto `p` della pianta (cm). */
    private fun pianta(p: Vec2): Offset {
        val c = vm().state.value.camera
        return Offset(c.toScreenX(p.x) + CANVAS_X, c.toScreenY(p.y) + CANVAS_Y)
    }

    /** Dove si vede ora sullo schermo il pixel (`x`, `y`) della piantina di sfondo. */
    private fun sfondo(x: Float, y: Float): Offset {
        val s = vm().state.value
        val u = s.plan.underlay ?: error("manca la piantina di sfondo")
        val c = s.camera
        return Offset(c.toScreenX(u.origin.x + x * u.cmPerPx) + CANVAS_X, c.toScreenY(u.origin.y + y * u.cmPerPx) + CANVAS_Y)
    }

    private companion object {
        /** Dove comincia la pianta nella finestra (sotto la barra in alto). */
        const val CANVAS_X = 0.5f
        const val CANVAS_Y = 73f
        const val W = 1400
        const val H = 900
    }
}
