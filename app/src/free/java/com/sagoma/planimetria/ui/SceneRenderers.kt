package com.sagoma.planimetria.ui

import android.content.Context
import com.sagoma.planimetria.assets.AssetStore

/** Versione free: il renderer OpenGL semplice. */
object SceneRenderers {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, assets: AssetStore): SceneRenderer = GlSceneRenderer()
}
