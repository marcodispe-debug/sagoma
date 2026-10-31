package com.sagoma.planimetria.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * La scritta va a capo a metà di una parola («Progett-o»): succede quando una parola da sola è più larga dello spazio.
 * Chi la mostra può rimpicciolire un po' il carattere finché la parola ci sta.
 */
internal fun TextLayoutResult.breaksWord(): Boolean {
    val s = layoutInput.text.text
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end in 1 until s.length && s[end - 1].isLetter() && s[end].isLetter()) return true
    }
    return false
}

/**
 * Scritta su una riga, nello stile del posto (pulsante, etichetta di un campo): se non ci sta si rimpicciolisce un po', e
 * solo alla fine mette i puntini. Con `maxLines = 1` e basta andava a capo e il resto spariva («🧱» senza «Elementos»,
 * «Profondeu-r» nell'etichetta di un campo stretto).
 */
@Composable
internal fun FitText(text: String) {
    val style = LocalTextStyle.current
    var scale by remember(text) { mutableStateOf(1f) }
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * scale),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (it.hasVisualOverflow && scale > 0.7f) scale -= 0.05f },
    )
}

/**
 * Nome sotto la miniatura di una scheda stretta (arredi, materiali, modelli): se una parola da sola non ci sta (in tedesco
 * «Kleiderständer», in inglese «herringbone») il carattere si rimpicciolisce un po' invece di spezzarla a metà. L'altezza
 * delle righe resta quella dello stile: le schede restano alte uguali. Un nome troppo lungo finisce coi puntini, come prima.
 */
@Composable
internal fun CardName(text: String, maxLines: Int, modifier: Modifier = Modifier, minLines: Int = 1) {
    val style = MaterialTheme.typography.labelSmall
    var scale by remember(text) { mutableStateOf(1f) }
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * scale),
        textAlign = TextAlign.Center,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onTextLayout = { if (it.breaksWord() && scale > CARD_NAME_MIN_SCALE) scale -= 0.05f },
    )
}

/** Fin dove rimpicciolisce [CardName]: 11 sp × 0,8 ≈ 9 sp, ancora leggibile sul telefono. */
internal const val CARD_NAME_MIN_SCALE = 0.8f
