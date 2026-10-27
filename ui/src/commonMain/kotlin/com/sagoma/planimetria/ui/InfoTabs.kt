package com.sagoma.planimetria.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Una scheda della tendina Info: `id` fisso (per ricordarla), `label` nella lingua scelta. */
internal class InfoTab(val id: String, val label: String, val content: @Composable ColumnScope.() -> Unit)

/** Ultima scheda aperta per ogni tipo di oggetto (muro, finestra, scala…): resta passando da un oggetto all'altro. */
private val lastTabs = mutableStateMapOf<String, String>()

/**
 * Tendina Info divisa in schede, come quella della stanza: in alto la parte fissa (comandi e misure principali), poi le
 * schede e sotto il contenuto della scheda scelta, che scorre nello spazio rimasto. Con una sola scheda niente linguette.
 */
@Composable
internal fun TabbedInfo(kind: String, modifier: Modifier, header: @Composable ColumnScope.() -> Unit, tabs: List<InfoTab>) {
    val tab = tabs.firstOrNull { it.id == lastTabs[kind] } ?: tabs.first()
    Column(modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            header()
            if (tabs.size > 1) InfoTabRow(tabs.map { it.label }, tabs.indexOf(tab)) { lastTabs[kind] = tabs[it].id }
        }
        val scroll = key(kind, tab.id) { rememberScrollState() }
        Box(Modifier.weight(1f, fill = false).padding(top = 8.dp)) {
            Column(Modifier.verticalScroll(scroll).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tab.content(this)
            }
            ScrollBar(scroll, Modifier.align(Alignment.TopEnd).offset(x = 6.dp))
        }
    }
}

/**
 * Linguette delle schede, a larghezza fissa: tutte visibili anche sul telefono (le parole lunghe vanno su due righe).
 * `tabModifier` serve alla guida interattiva per indicare una linguetta.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun InfoTabRow(labels: List<String>, selected: Int, tabModifier: (Int) -> Modifier = { Modifier }, onSelect: (Int) -> Unit) {
    androidx.compose.material3.SecondaryTabRow(selectedTabIndex = selected, containerColor = Color.Transparent) {
        // Margini stretti: con quelli standard le parole si spezzano a metà nella tendina.
        labels.forEachIndexed { i, label ->
            androidx.compose.material3.Tab(selected = i == selected, onClick = { onSelect(i) }, modifier = tabModifier(i)) {
                // Se una parola non ci sta (pannello stretto col telefono in orizzontale: «Progett-o») il carattere si rimpicciolisce.
                val style = MaterialTheme.typography.labelLarge
                var scale by remember(label) { mutableStateOf(1f) }
                Text(
                    label,
                    style = style.copy(fontSize = style.fontSize * scale),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    onTextLayout = { if ((it.breaksWord() || it.hasVisualOverflow) && scale > 0.7f) scale -= 0.05f },
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
                )
            }
        }
    }
}
