package com.sagoma.planimetria.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.persistence.LanguageChoice
import com.sagoma.planimetria.testi.Lingue
import com.sagoma.planimetria.testi.T

/** Lingua scelta e come cambiarla, per il menu: lo fornisce [SagomaApp]. */
class LanguageSetting(val choice: LanguageChoice, val change: (LanguageChoice) -> Unit)

val LocalLanguageSetting = staticCompositionLocalOf { LanguageSetting(LanguageChoice.Italian) {} }

/** Scelta della lingua: italiano, inglese o come il sistema. Cambia subito; la scelta resta per i prossimi avvii. */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val setting = LocalLanguageSetting.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.vari.lingua) },
        text = {
            Column {
                for (c in LanguageChoice.entries) {
                    Row(
                        Modifier.fillMaxWidth().clickable { setting.change(c) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c == setting.choice, onClick = { setting.change(c) })
                        if (c != LanguageChoice.System) {
                            LanguageFlag(Lingue.scegli(c, "").codice)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(c.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(T.comuni.chiudi) } },
    )
}

/**
 * Pulsante della lingua accanto a "❔ Guide": bandiera e sigla della lingua in uso (🇮🇹 ITA, 🇬🇧 ENG);
 * toccandolo si sceglie un'altra lingua o "come il sistema". Con `flagOnly` (telefono in verticale) solo la bandiera.
 */
@Composable
fun LanguageButton(modifier: Modifier = Modifier, flagOnly: Boolean = false) {
    val setting = LocalLanguageSetting.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = if (flagOnly) 4.dp else 10.dp)) {
            LanguageFlag(T.lingua.codice)
            if (!flagOnly) {
                Spacer(Modifier.width(6.dp))
                Text(T.lingua.sigla, maxLines = 1)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (c in LanguageChoice.entries) {
                val lingua = Lingue.scegli(c, "")
                DropdownMenuItem(
                    text = { Text((if (c == setting.choice) "✓ " else "") + if (c == LanguageChoice.System) c.label else "${lingua.sigla} · ${c.label}") },
                    leadingIcon = if (c == LanguageChoice.System) null else ({ LanguageFlag(lingua.codice) }),
                    onClick = { open = false; setting.change(c) },
                )
            }
        }
    }
}

/**
 * Bandiera disegnata (le bandiere emoji non si vedono su Windows): tricolore per l'italiano, Union Jack per l'inglese.
 */
@Composable
fun LanguageFlag(codice: String, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 21.dp, height = 14.dp)) {
        val w = size.width
        val h = size.height
        clipRect {
            when (codice) {
                "en" -> {
                    drawRect(Color(0xFF012169))
                    val diag = h * 0.2f
                    drawLine(Color.White, Offset(0f, 0f), Offset(w, h), diag)
                    drawLine(Color.White, Offset(0f, h), Offset(w, 0f), diag)
                    drawLine(Color(0xFFC8102E), Offset(0f, 0f), Offset(w, h), diag / 3)
                    drawLine(Color(0xFFC8102E), Offset(0f, h), Offset(w, 0f), diag / 3)
                    val cross = h / 3
                    drawRect(Color.White, Offset((w - cross) / 2, 0f), Size(cross, h))
                    drawRect(Color.White, Offset(0f, (h - cross) / 2), Size(w, cross))
                    val red = h * 0.2f
                    drawRect(Color(0xFFC8102E), Offset((w - red) / 2, 0f), Size(red, h))
                    drawRect(Color(0xFFC8102E), Offset(0f, (h - red) / 2), Size(w, red))
                }
                else -> {
                    drawRect(Color(0xFF009246), Offset.Zero, Size(w / 3, h))
                    drawRect(Color.White, Offset(w / 3, 0f), Size(w / 3, h))
                    drawRect(Color(0xFFCE2B37), Offset(2 * w / 3, 0f), Size(w / 3, h))
                }
            }
        }
        // Bordo sottile: il bianco della bandiera resta visibile anche su fondo chiaro.
        drawRect(Color(0x40000000), style = Stroke(1f))
    }
}
