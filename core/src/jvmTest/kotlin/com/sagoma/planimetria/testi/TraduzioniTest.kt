package com.sagoma.planimetria.testi

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.model.Dimension
import com.sagoma.planimetria.model.ProPalette
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.LanguageChoice
import com.sagoma.planimetria.testi.en.Inglese
import com.sagoma.planimetria.testi.es.Spagnolo
import com.sagoma.planimetria.testi.fr.Francese
import java.lang.reflect.Modifier
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Traduzioni: ogni testo dell'italiano ha la sua traduzione in ogni lingua, e la scelta della lingua segue quella dell'utente o del sistema. */
class TraduzioniTest {
    @AfterTest
    fun italiano() {
        T.lingua = Italiano
    }

    private val aree = Lingua::class.java.declaredMethods
        .filter { it.name.startsWith("get") && it.parameterCount == 0 && Modifier.isPublic(it.modifiers) && !it.returnType.isPrimitive }

    private val traduzioni = Lingue.tutte - Italiano

    @Test
    fun ogniAreaHaLaSuaTraduzione() {
        for (lingua in traduzioni) for (getter in aree) {
            val italiano = getter.invoke(Italiano)
            val tradotta = getter.invoke(lingua)
            assertTrue(italiano !== tradotta, "${lingua.codice} ${getter.name}: usa ancora l'italiano")
            for (altra in traduzioni - lingua) {
                assertTrue(getter.invoke(altra) !== tradotta, "${lingua.codice} ${getter.name}: usa i testi di ${altra.codice}")
            }
        }
    }

    @Test
    fun ogniTestoETradotto() {
        val mancanti = mutableListOf<String>()
        for (lingua in traduzioni) for (getter in aree) {
            val base = getter.returnType
            val tradotta = getter.invoke(lingua).javaClass
            for (m in base.declaredMethods) {
                if (!Modifier.isPublic(m.modifiers) || Modifier.isStatic(m.modifiers) || m.isSynthetic) continue
                val ok = runCatching { tradotta.getDeclaredMethod(m.name, *m.parameterTypes) }.isSuccess
                if (!ok) mancanti += "${lingua.codice} ${base.simpleName}.${m.name}"
            }
        }
        assertTrue(mancanti.isEmpty(), "testi senza traduzione: $mancanti")
    }

    @Test
    fun testiInInglese() {
        T.lingua = Inglese
        assertEquals("Bedroom", RoomType.Camera.label)
        assertEquals("Room", RoomType.Altro.defaultName)
        assertEquals("Welcome", Tip.Welcome.title)
        assertEquals("Sides changed: 0 of 2", T.tutorial.latiCambiati(0))
        assertEquals("Pure white", ProPalette.nameOf(0xFFFAFAF8))
        assertTrue(Tip.entries.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertTrue(TutorialStep.entries.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertEquals("20.50", formatDecimal(20.5, 2))
        assertEquals("12.5", Dimension.formatLength(12.5))
        assertEquals(Italiano.nomi.tavolozza.keys, Inglese.nomi.tavolozza.keys, "la tavolozza ha gli stessi colori")
    }

    @Test
    fun testiInFrancese() {
        T.lingua = Francese
        assertEquals("FRA", Francese.sigla)
        assertEquals("Chambre", RoomType.Camera.label)
        assertEquals("Pièce", RoomType.Altro.defaultName)
        assertTrue(Tip.entries.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertTrue(TutorialStep.entries.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertEquals("20,50", formatDecimal(20.5, 2))
        assertEquals(Italiano.nomi.tavolozza.keys, Francese.nomi.tavolozza.keys, "la tavolozza ha gli stessi colori")
    }

    @Test
    fun testiInSpagnolo() {
        T.lingua = Spagnolo
        assertEquals("ESP", Spagnolo.sigla)
        assertEquals("Dormitorio", RoomType.Camera.label)
        assertEquals("Habitación", RoomType.Altro.defaultName)
        assertTrue(Tip.entries.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertTrue(TutorialStep.entries.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertEquals("20,50", formatDecimal(20.5, 2))
        assertEquals(Italiano.nomi.tavolozza.keys, Spagnolo.nomi.tavolozza.keys, "la tavolozza ha gli stessi colori")
    }

    @Test
    fun ogniLinguaHaLaSuaSigla() {
        assertEquals(listOf("ITA", "ENG", "FRA", "ESP"), Lingue.tutte.map { it.sigla })
    }

    @Test
    fun sceltaDellaLingua() {
        assertSame(Italiano, Lingue.scegli(LanguageChoice.Italian, "en-US"))
        assertSame(Inglese, Lingue.scegli(LanguageChoice.English, "it-IT"))
        assertSame(Italiano, Lingue.scegli(LanguageChoice.System, "it-IT"))
        assertSame(Italiano, Lingue.scegli(LanguageChoice.System, "it"))
        assertSame(Inglese, Lingue.scegli(LanguageChoice.System, "en-GB"))
        assertSame(Francese, Lingue.scegli(LanguageChoice.French, "it-IT"))
        assertSame(Spagnolo, Lingue.scegli(LanguageChoice.Spanish, "it-IT"))
        assertSame(Francese, Lingue.scegli(LanguageChoice.System, "fr-FR"))
        assertSame(Francese, Lingue.scegli(LanguageChoice.System, "fr-CA"))
        assertSame(Spagnolo, Lingue.scegli(LanguageChoice.System, "es"))
        assertSame(Spagnolo, Lingue.scegli(LanguageChoice.System, "es-MX"))
        assertSame(Inglese, Lingue.scegli(LanguageChoice.System, "de"))
    }
}
