package com.sagoma.planimetria.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.sagoma.planimetria.model.Room
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Campo di testo che seleziona tutto il contenuto quando riceve il fuoco, pronto per essere
 * sovrascritto (§13). Usato in tutti i pannelli e le finestre.
 */
@Composable
fun SelectAllTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    suffix: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    var selectAllPending by remember { mutableStateOf(false) }
    if (tfv.text != value) tfv = tfv.copy(text = value, selection = TextRange(value.length))

    // La selezione va applicata dopo che il tocco ha posizionato il cursore, altrimenti verrebbe sovrascritta.
    LaunchedEffect(selectAllPending) {
        if (selectAllPending) {
            kotlinx.coroutines.delay(30)
            tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
            selectAllPending = false
        }
    }

    OutlinedTextField(
        value = tfv,
        onValueChange = {
            tfv = it
            if (it.text != value) onValueChange(it.text)
        },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = keyboardOptions ?: if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        keyboardActions = keyboardActions,
        modifier = modifier.onFocusChanged { if (it.isFocused) selectAllPending = true },
    )
}

/** Testo dell'avviso sulle misure interne: stesso testo in app ed esportazione. */
internal val INTERIOR_MEASURES_NOTE =
    "Aree, perimetri e lunghezze dei muri sono misure interne, al netto dello spessore dei muri."

/** Avviso all'utente: le misure mostrate e inserite sono riferite al lato interno dei muri. */
@Composable
fun InteriorMeasuresNotice(modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Text("ⓘ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(
                INTERIOR_MEASURES_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Converte il testo di un campo numerico (accetta la virgola) in un valore positivo, o null. */
fun parsePositive(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
