package com.sagoma.planimetria.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.sagoma.planimetria.Edition
import com.sagoma.planimetria.ui.FurnitureAssets
import com.sagoma.planimetria.ui.SagomaApp
import kotlinx.browser.document
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val platform = WebPlatform()
    Edition.isPro = platform.isPro
    FurnitureAssets.init(platform)
    document.getElementById("caricamento")?.remove()
    ComposeViewport(document.body!!) {
        Box(Modifier.fillMaxSize()) {
            SagomaApp(platform)
            platform.message?.let { msg ->
                LaunchedEffect(msg) { delay(2500); platform.message = null }
                Snackbar(Modifier.align(Alignment.BottomCenter).padding(24.dp)) { Text(msg) }
            }
        }
    }
}
