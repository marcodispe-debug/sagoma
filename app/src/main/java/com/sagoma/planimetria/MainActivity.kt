package com.sagoma.planimetria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sagoma.planimetria.persistence.FileProjectRepository
import com.sagoma.planimetria.persistence.FileTipStore
import com.sagoma.planimetria.ui.AndroidPlatform
import com.sagoma.planimetria.ui.FurnitureAssets
import com.sagoma.planimetria.ui.SagomaApp
import com.sagoma.planimetria.ui.SceneRenderers

/** App Android: prepara i servizi della piattaforma e mostra l'interfaccia comune ([SagomaApp]). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Edition.isPro = Flavor.isPro
        val platform = AndroidPlatform(
            activity = this,
            isPro = Flavor.isPro,
            projects = FileProjectRepository(filesDir),
            tips = FileTipStore(filesDir),
            sceneRenderer = SceneRenderers::create,
            remoteStore = (application as SagomaApplication).remoteStore,
        )
        FurnitureAssets.init(platform)
        enableEdgeToEdge()
        setContent { SagomaApp(platform) }
    }
}
