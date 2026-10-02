package com.sagoma.planimetria.ui

import android.content.Context

/** Versione free: il renderer OpenGL semplice. */
object SceneRenderers {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): SceneRenderer = GlSceneRenderer()
}
