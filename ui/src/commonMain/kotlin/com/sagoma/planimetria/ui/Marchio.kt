package com.sagoma.planimetria.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * Il marchio di Sagoma: la scritta con la «o» disegnata come una stanza vista dall'alto, aperta da una porta (l'arco
 * terracotta). L'icona è la stessa «o» da sola. I tracciati vengono dai file del marchio (lettere Inter Display SemiBold
 * già convertite in tracciati): non si ritoccano a mano, si rigenerano insieme all'icona Android
 * (`app/src/main/res/drawable/ic_launcher_foreground.xml`), a quella della pagina web (`favicon.svg`) e a quelle del PC.
 */
object Marchio {
    val Inchiostro = Color(0xFF16202A)
    val Terracotta = Color(0xFFC8553D)

    // Scritta, in unità del carattere: lettere piene, muri della stanza e arco della porta tracciati.
    private const val LARGHEZZA = 7265f
    private const val ALTEZZA = 1946f
    private const val LETTERE = "M590 1538C943 1538 1164 1362 1164 1084C1164 865 1025 736 716 667L565 632C371 587 294 531 294 431C294 309 411 224 576 224C750 224 864 319 875 476H1126C1117 180 910 0 577 0C248 0 29 178 29 444C29 653 166 784 464 852L624 888C823 934 901 995 901 1100C901 1231 778 1315 592 1315C385 1315 261 1208 259 1028H0C0 1347 221 1538 590 1538ZM1626 1531C1798 1531 1887 1459 1937 1365H1941V1514H2189V794C2189 574 2026 437 1751 437C1475 437 1303 576 1291 786H1534C1541 697 1623 633 1746 633C1867 633 1939 697 1939 787V795C1939 867 1872 870 1676 892C1458 915 1261 974 1261 1214C1261 1425 1416 1531 1626 1531ZM1688 1344C1578 1344 1507 1295 1507 1212C1507 1116 1599 1077 1707 1061C1811 1045 1909 1029 1940 1009V1123C1940 1246 1853 1344 1688 1344ZM2846 1946C3132 1946 3345 1814 3345 1487V458H3096V615H3095C3025 494 2910 437 2773 437C2502 437 2318 660 2318 981C2318 1299 2499 1521 2775 1521C2914 1521 3026 1465 3094 1341H3095V1502C3095 1664 3004 1751 2846 1751C2728 1751 2641 1703 2617 1612H2370C2398 1817 2567 1946 2846 1946ZM2834 1313C2678 1313 2573 1194 2573 978C2573 763 2678 643 2834 643C2999 643 3107 775 3107 978C3107 1181 2999 1313 2834 1313ZM4646 1514H4898V884C4898 733 4995 651 5109 651C5221 651 5301 726 5301 841V1514H5546V866C5546 740 5625 651 5753 651C5861 651 5949 713 5949 853V1514H6202V817C6202 569 6048 437 5848 437C5694 437 5568 515 5512 637C5468 516 5351 437 5208 437C5075 437 4954 505 4890 641V458H4646ZM6702 1531C6874 1531 6963 1459 7013 1365H7017V1514H7265V794C7265 574 7102 437 6827 437C6551 437 6379 576 6367 786H6610C6617 697 6699 633 6822 633C6943 633 7015 697 7015 787V795C7015 867 6948 870 6752 892C6534 915 6337 974 6337 1214C6337 1425 6492 1531 6702 1531ZM6764 1344C6654 1344 6583 1295 6583 1212C6583 1116 6675 1077 6783 1061C6887 1045 6985 1029 7016 1009V1123C7016 1246 6929 1344 6764 1344Z"
    private const val STANZA = "M4028,557H3869A271,271 0 0 0 3598,828V1144A271,271 0 0 0 3869,1415H4122A271,271 0 0 0 4393,1144V923"
    private const val PORTA = "M4028,557A366,366 0 0 1 4393,923"
    private const val MURO = 247f
    private const val ARCO = 89f

    // Icona, su un quadrato di 1024.
    private const val ICONA_FONDO = "M225,0H799A225,225 0 0 1 1024,225V799A225,225 0 0 1 799,1024H225A225,225 0 0 1 0,799V225A225,225 0 0 1 225,0Z"
    private const val ICONA_STANZA = "M528.1,294.6H447.8A137.4,137.4 0 0 0 310.4,432.1V591.9A137.4,137.4 0 0 0 447.8,729.4H576.2A137.4,137.4 0 0 0 713.6,591.9V480.1"
    private const val ICONA_PORTA = "M528.1,294.6A185.5,185.5 0 0 1 713.6,480.1"
    private const val ICONA_MURO = 125.3f
    private const val ICONA_ARCO = 40.1f

    /** Larghezza della scritta diviso altezza. */
    const val PROPORZIONE = LARGHEZZA / ALTEZZA

    /** La scritta «Sagoma» con le lettere del colore [lettere]; l'arco della porta resta terracotta. */
    fun scritta(lettere: Color): ImageVector =
        ImageVector.Builder("Sagoma", (LARGHEZZA / 50f).dp, (ALTEZZA / 50f).dp, LARGHEZZA, ALTEZZA)
            .addPath(addPathNodes(LETTERE), fill = SolidColor(lettere))
            .addPath(addPathNodes(STANZA), stroke = SolidColor(lettere), strokeLineWidth = MURO)
            .addPath(addPathNodes(PORTA), stroke = SolidColor(Terracotta), strokeLineWidth = ARCO, strokeLineCap = StrokeCap.Round)
            .build()

    /** L'icona: la «o» bianca su un quadrato inchiostro con gli angoli arrotondati (finestra e barra delle applicazioni del PC). */
    val icona: ImageVector by lazy {
        ImageVector.Builder("Sagoma", 64.dp, 64.dp, 1024f, 1024f)
            .addPath(addPathNodes(ICONA_FONDO), fill = SolidColor(Inchiostro))
            .addPath(addPathNodes(ICONA_STANZA), stroke = SolidColor(Color.White), strokeLineWidth = ICONA_MURO)
            .addPath(addPathNodes(ICONA_PORTA), stroke = SolidColor(Terracotta), strokeLineWidth = ICONA_ARCO, strokeLineCap = StrokeCap.Round)
            .build()
    }
}

/** La scritta del marchio nelle sue proporzioni: basta darle l'altezza. Le lettere seguono il tema chiaro o scuro. */
@Composable
fun ScrittaSagoma(modifier: Modifier = Modifier, lettere: Color = MaterialTheme.colorScheme.onBackground) {
    val disegno = remember(lettere) { Marchio.scritta(lettere) }
    Image(rememberVectorPainter(disegno), contentDescription = "Sagoma", modifier = modifier.aspectRatio(Marchio.PROPORZIONE))
}
