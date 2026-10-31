package com.sagoma.planimetria.ui

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.sagoma.planimetria.model.FurnitureCatalog
import com.sagoma.planimetria.model.MaterialCatalog
import com.sagoma.planimetria.testi.Italiano
import com.sagoma.planimetria.testi.Lingue
import com.sagoma.planimetria.testi.T
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Nomi sotto le miniature di arredi (88 dp, due righe) e materiali (72 dp, tre righe): in tedesco tante parole lunghe
 * («Waschbeckenunterschrank») andavano a capo a metà. Ora il nome rimpicciolisce un po' ([CardName]) e le parole
 * troppo lunghe anche così sono scritte diverse («Waschbecken-Unterschrank»), in ogni lingua.
 */
class CardNameTest {
    // Lo stile labelSmall di Material 3, con cui si scrivono i nomi nelle schede.
    private val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
    private val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)

    @AfterTest
    fun italiano() {
        T.lingua = Italiano
    }

    /** Come fa [CardName]: rimpicciolisce a passi finché nessuna parola va a capo a metà, fino a [CARD_NAME_MIN_SCALE]. */
    private fun spezzaParole(nome: String, larghezza: Int, righe: Int): Boolean {
        var scale = 1f
        while (true) {
            val breaks = measurer.measure(
                nome, style.copy(fontSize = style.fontSize * scale), overflow = TextOverflow.Ellipsis, maxLines = righe,
                constraints = Constraints(maxWidth = larghezza),
            ).breaksWord()
            if (!breaks || scale <= CARD_NAME_MIN_SCALE) return breaks
            scale -= 0.05f
        }
    }

    @Test
    fun parolaLungaRimpicciolisceInvoceDiSpezzarsi() {
        val breaks = measurer.measure("Waschbeckenunterschrank", style, constraints = Constraints(maxWidth = 88)).breaksWord()
        assertTrue(breaks)
        assertFalse(spezzaParole("Waschbecken-Unterschrank", 88, 2))
        assertFalse(spezzaParole("Divano", 88, 2))
    }

    @Test
    fun nomiDelCatalogoInteriInOgniLingua() {
        // Il catalogo vero sta nel modulo :catalogo; senza (copia parziale del progetto) non c'è nulla da provare.
        val arredi = File("../catalogo/src/main/assets/furniture/catalog.json").takeIf { it.isFile } ?: return
        val materiali = File("../catalogo/src/main/assets/materials/materials.json").takeIf { it.isFile } ?: return
        FurnitureCatalog.load(arredi.readText())
        MaterialCatalog.load(materiali.readText())
        FurnitureCatalog.addRugs(MaterialCatalog.items)
        val spezzati = mutableListOf<String>()
        for (lingua in Lingue.tutte) {
            T.lingua = lingua
            // Un po' di margine: il carattere del sistema cambia da un computer all'altro.
            for (i in FurnitureCatalog.items) if (spezzaParole(i.nome, 92, 2)) spezzati += "${lingua.codice}: ${i.nome}"
            for (m in MaterialCatalog.items) if (spezzaParole(m.nome, 76, 3)) spezzati += "${lingua.codice}: ${m.nome}"
        }
        assertEquals(emptyList(), spezzati)
    }
}
