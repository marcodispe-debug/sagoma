package com.sagoma.planimetria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileLanguageStore
import com.sagoma.planimetria.persistence.FileThemeStore
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.reading.HttpPlanReader
import com.sagoma.planimetria.ui.AndroidPlatform
import com.sagoma.planimetria.ui.FurnitureAssets
import com.sagoma.planimetria.ui.SagomaApp
import com.sagoma.planimetria.ui.SceneRenderers

/** App Android: prepara i servizi della piattaforma e mostra l'interfaccia comune ([SagomaApp]). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SagomaApplication
        val platform = AndroidPlatform(
            activity = this,
            projects = FileProjectRepository(filesDir),
            tips = FileTipStore(filesDir),
            theme = FileThemeStore(filesDir),
            language = FileLanguageStore(filesDir),
            sceneRenderer = SceneRenderers::create,
            remoteStore = app.remoteStore,
            assetRequests = app.assetRequests,
            planReader = planReader(),
        )
        FurnitureAssets.init(platform)
        enableEdgeToEdge()
        setContent { SagomaApp(platform) }
    }

    /** "Leggi la piantina da sola", se è configurato l'indirizzo del server (proprietà Gradle `sagoma.lettura.url`). */
    private fun planReader(): HttpPlanReader? {
        val url = getString(R.string.lettura_url).trim()
        if (url.isEmpty()) return null
        return HttpPlanReader(url, HttpPlanReader.deviceId(filesDir), appKey = getString(R.string.lettura_chiave).trim())
    }
}
