package com.sagoma.planimetria.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Scelta di un modello: schede scorrevoli in orizzontale, ognuna con l'anteprima disegnata e il nome.
 * `preview` disegna l'anteprima del modello nel suo riquadro.
 */
@Composable
fun <T> ModelPicker(
    label: String,
    options: List<T>,
    selected: T,
    name: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    preview: DrawScope.(T) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        ArrowLazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, end = 8.dp),
        ) {
            itemsIndexed(options) { _, option ->
                val isSelected = option == selected
                Surface(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.width(100.dp),
                ) {
                    Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(width = 88.dp, height = 60.dp).background(PreviewBackground, RoundedCornerShape(8.dp))) {
                            Canvas(Modifier.size(width = 88.dp, height = 60.dp)) { preview(option) }
                        }
                        Text(
                            name(option),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 4.dp).height(30.dp),
                        )
                    }
                }
            }
        }
    }
}

private val PreviewBackground = Color(0xFFF6F5F2)

/** Scelta di un colore: pallini, con il nome di quello scelto. */
@Composable
fun ColorPicker(label: String, colors: List<Pair<String, Long>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text("$label: ${colors.getOrNull(selected)?.first.orEmpty()}", style = MaterialTheme.typography.labelMedium)
        ArrowLazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 2.dp, end = 8.dp)) {
            itemsIndexed(colors) { i, (_, argb) ->
                val isSelected = i == selected
                Box(
                    Modifier
                        .size(34.dp)
                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                        .padding(if (isSelected) 5.dp else 2.dp)
                        .background(Color(argb), CircleShape)
                        .clickable { onSelect(i) },
                )
            }
        }
    }
}
