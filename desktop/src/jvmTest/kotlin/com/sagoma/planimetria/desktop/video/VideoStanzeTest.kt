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
            val tips = FileTipStore(dir).also { s -> s.save(Tip.entries.map { it.name }.toSet() + TipStore.TUTORIAL_DONE + TipStore.TRACING_GUIDE_DONE) }
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
                // Ricalco e lettura della piantina: si rifanno quando Marco dice come spiegarli. Il muro singolo va nei mini tutorial.
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
        r.clickOn(r.rect("Camera da letto", exact = true), "Si apre la finestra «Misure della stanza». In «Tipo di stanza» clicca su che stanza è, per esempio «Camera da letto»: sagoma le dà il nome e il colore giusti.", readSeconds = 5.0)
        r.wait(1.0)
        r.stepLabel = "Passo 4 di $tot"
        val lato = r.field("Lato")
        r.clickOn(r.rectOf(lato), "Clicca nel campo «Lato» e scrivi quanto è lungo un lato, in centimetri. È la misura interna, da muro a muro, quella che prendi col metro.", readSeconds = 5.0)
        r.type(lato, "350")
        r.wait(1.5)
        r.stepLabel = "Passo 5 di $tot"
        r.highlight = r.rectOf(r.field("Altezza soffitto"))
        r.caption = "L'altezza del soffitto è già scritta: 270 cm. Cambiala solo se la tua stanza è più alta o più bassa. Serve per la vista 3D."
        r.moveTo(r.highlight!!.let { Offset(it.right - 60f, it.top + it.height / 2) })
        r.wait(5.0)
        r.stepLabel = "Passo 6 di $tot"
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca su «Crea stanza».", readSeconds = 2.5)
        r.highlight = null
        r.caption = "Fatto! La camera da letto quadrata di 3,50 × 3,50 m è sulla pianta, con le misure scritte sui muri."
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
        r.clickOn(r.rect("Soggiorno", exact = true), "Scegli il tipo: per esempio «Soggiorno».", readSeconds = 3.0)
        r.wait(0.8)
        r.stepLabel = "Passo 5 di $tot"
        val larg = r.field("Larghezza")
        r.clickOn(r.rectOf(larg), "Nel campo «Larghezza» scrivi il lato orizzontale della stanza (da sinistra a destra sulla pianta), in centimetri.", readSeconds = 4.5)
        r.type(larg, "520")
        r.wait(1.0)
        r.stepLabel = "Passo 6 di $tot"
        val prof = r.field("Profondità")
        r.clickOn(r.rectOf(prof), "Nel campo «Profondità» scrivi l'altro lato (dall'alto in basso sulla pianta).", readSeconds = 4.0)
        r.type(prof, "410")
        r.wait(1.0)
        r.stepLabel = "Passo 7 di $tot"
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca su «Crea stanza».", readSeconds = 2.0)
        r.highlight = null
        r.caption = "Ecco il soggiorno di 5,20 × 4,10 m accanto alla camera. Poi potrai spostarlo trascinandolo dove vuoi."
        r.moveTo(Offset(1100f, 560f))
        r.wait(5.0)
    }

    /** Modo 3: forma a L. */
    private fun formaL(r: Regista) {
        r.titleCard("Modo 3 · Forma a L", "Per una stanza con un angolo che rientra")
        r.chapter = "Modo 3 · Forma a L"
        val tot = 9
        r.highlight = null
        r.caption = "Molte cucine e molti soggiorni sono a forma di L. Vediamo come si fa."
        r.wait(2.5)
        nuovaStanza(r, tot)
        r.stepLabel = "Passo 3 di $tot"
        r.clickOn(r.rect("A L", exact = true), "Clicca su «A L».", readSeconds = 2.5)
        r.wait(0.8)
        r.stepLabel = "Passo 4 di $tot"
        r.highlight = r.rect("Dove sporge la stanza?").let { t -> Rect(t.left, t.top, r.rect("In basso a sinistra").right, r.rect("In basso a sinistra").bottom) }
        r.caption = "sagoma chiede da che parte sporge il pezzo in più della L. Guarda la tua stanza dall'alto, come su una piantina, e scegli l'angolo giusto."
        r.wait(5.5)
        r.clickOn(r.rect("In alto a destra"), "Qui scegliamo «In alto a destra».", readSeconds = 2.5)
        r.wait(0.8)
        r.stepLabel = "Passo 5 di $tot"
        r.clickOn(r.rect("Cucina", exact = true), "Scegli il tipo: «Cucina».", readSeconds = 2.5)
        r.wait(0.6)
        r.stepLabel = "Passo 6 di $tot"
        val lp = r.field("Larghezza principale")
        r.clickOn(r.rectOf(lp), "La L è fatta di due pezzi. Il pezzo principale è il rettangolo più grande: in «Larghezza principale» scrivi quanto è largo.", readSeconds = 5.0)
        r.type(lp, "450")
        val pp = r.field("Profondità principale")
        r.clickOn(r.rectOf(pp), "In «Profondità principale» scrivi quanto è profondo il pezzo principale.", readSeconds = 3.5)
        r.type(pp, "300")
        r.wait(0.8)
        r.stepLabel = "Passo 7 di $tot"
        val lpr = r.field("Larghezza protuberanza")
        r.clickOn(r.rectOf(lpr), "La «protuberanza» è il pezzo che sporge. In «Larghezza protuberanza» scrivi quanto è largo: deve essere meno della larghezza principale.", readSeconds = 5.5)
        r.type(lpr, "200")
        r.stepLabel = "Passo 8 di $tot"
        val ppr = r.field("Profondità protuberanza")
        r.clickOn(r.rectOf(ppr), "In «Profondità protuberanza» scrivi di quanto sporge.", readSeconds = 3.5)
        r.type(ppr, "180")
        r.wait(0.8)
        r.stepLabel = "Passo 9 di $tot"
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca su «Crea stanza».", readSeconds = 2.0)
        r.highlight = null
        r.caption = "Ecco la cucina a L: 4,50 × 3,00 m con il pezzo di 2,00 × 1,80 m che sporge in alto a destra."
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
        val lati = PiantinaDiProva.latiBagno.mapIndexed { i, l -> "Lato ${"ABCD"[i]}${"ABCD"[(i + 1) % 4]}" to l.toString() }
        for ((i, l) in lati.withIndex()) {
            val f = r.field(l.first)
            val spiega = if (i == 0) "Clicca in «Lato AB» e scrivi la misura da A a B, in centimetri." else "Ora «${l.first}»."
            r.clickOn(r.rectOf(f), spiega, readSeconds = if (i == 0) 3.5 else 1.5)
            r.type(f, l.second)
            r.wait(0.4)
        }
        r.stepLabel = "Passo 7 di $tot"
        val diag = r.field("Diagonale AC")
        r.clickOn(r.rectOf(diag), "In «Diagonale AC» scrivi la distanza in diagonale da A all'angolo opposto C. È questa misura che dice a sagoma se gli angoli sono storti.", readSeconds = 5.5)
        r.type(diag, PiantinaDiProva.diagonaleBagno.toString())
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
            Triple("→ destra", "350", "Primo muro: clicca «→ destra», scrivi 350 in «Lunghezza cm» e clicca «Aggiungi»."),
            Triple("↓ giù", "150", "Secondo muro: «↓ giù», 150 cm, «Aggiungi»."),
            Triple("← sinistra", "100", "Il muro rientra: «← sinistra», 100 cm, «Aggiungi»."),
            Triple("↓ giù", "100", "Ancora «↓ giù», 100 cm."),
            Triple("← sinistra", "250", "E «← sinistra», 250 cm: siamo tornati sotto il primo angolo."),
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
        r.clickOn(r.rect("Studio", exact = true), "Nella finestra «Nuova stanza» scegli il tipo, per esempio «Studio». Soffitto e spessore dei muri sono già scritti.", readSeconds = 5.0)
        r.wait(0.6)
        r.clickOn(r.rect("Crea stanza", exact = true), "Clicca «Crea stanza».", readSeconds = 2.0)
        r.highlight = null
        r.caption = "Ecco lo studio, con la forma che hai disegnato muro per muro e le misure interne che hai scritto."
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
