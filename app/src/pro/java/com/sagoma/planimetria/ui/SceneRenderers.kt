package com.sagoma.planimetria.ui

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Versione pro: Filament, se il telefono ha OpenGL ES 3 (quasi tutti); altrimenti, o se Filament non
 * parte, il renderer semplice.
 */
object SceneRenderers {
    fun create(context: Context): SceneRenderer {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.deviceConfigurationInfo.reqGlEsVersion < 0x30000) return GlSceneRenderer()
        return try {
            FilamentSceneRenderer()
        } catch (t: Throwable) {
            Log.w("Sagoma", "Filament non disponibile, uso il renderer semplice", t)
            GlSceneRenderer()
        }
    }
}
