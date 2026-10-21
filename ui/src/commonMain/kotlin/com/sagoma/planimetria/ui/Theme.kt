package com.sagoma.planimetria.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.persistence.ThemeChoice
import com.sagoma.planimetria.testi.T

/** Colori dell'app chiara: il verde petrolio di Sagoma. */
internal val SagomaLightColors: ColorScheme = lightColorScheme(primary = Color(0xFF2F5D62))

/**
 * Colori dell'app scura: lo stesso verde petrolio schiarito, per leggersi sullo sfondo scuro. La pianta resta un foglio
 * chiaro (vedi [PlanBackgroundColor]) come le tavole che si stampano: cambiano menu, schede e finestre.
 */
internal val SagomaDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8CCDD3),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF1E4B50),
    onPrimaryContainer = Color(0xFFB8ECF1),
    inversePrimary = Color(0xFF2F5D62),
    secondary = Color(0xFFB1CBCD),
    onSecondary = Color(0xFF1C3436),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCDE7E9),
    // Grigi neutri appena verdastri (quelli di base di Material tendono al viola).
    background = Color(0xFF151819),
    surface = Color(0xFF151819),
    surfaceVariant = Color(0xFF3B4446),
    onSurfaceVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color(0xFF101314),
    surfaceContainerLow = Color(0xFF1B1F20),
    surfaceContainer = Color(0xFF1F2324),
    surfaceContainerHigh = Color(0xFF292E2F),
    surfaceContainerHighest = Color(0xFF343939),
    outline = Color(0xFF899294),
    outlineVariant = Color(0xFF3F4849),
)

/** Tema scelto e come cambiarlo, per il menu: lo fornisce [SagomaApp]. */
class ThemeSetting(val choice: ThemeChoice, val change: (ThemeChoice) -> Unit)

val LocalThemeSetting = staticCompositionLocalOf { ThemeSetting(ThemeChoice.Light) {} }

/** Scelta del tema: chiaro, scuro o come il sistema. Cambia subito; la scelta resta per i prossimi avvii. */
@Composable
fun ThemeDialog(onDismiss: () -> Unit) {
    val setting = LocalThemeSetting.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T.vari.tema) },
        text = {
            Column {
                for (c in ThemeChoice.entries) {
                    Row(
                        Modifier.fillMaxWidth().clickable { setting.change(c) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c == setting.choice, onClick = { setting.change(c) })
                        Text(c.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(T.comuni.chiudi) } },
    )
}
