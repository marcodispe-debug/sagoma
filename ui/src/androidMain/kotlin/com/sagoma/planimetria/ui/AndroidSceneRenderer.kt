package com.sagoma.planimetria.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.sagoma.planimetria.geometry.Mat4
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3

/** Renderer Android: disegna su una View (GLSurfaceView, SurfaceView di Filament) messa nell'interfaccia. */
abstract class AndroidSceneRenderer : SceneRenderer {
    /** Crea la superficie su cui disegnare (una sola per renderer). */
    abstract fun createView(context: Context): View

    @Composable
    override fun Surface(modifier: Modifier) {
        AndroidView(factory = { ctx -> createView(ctx) }, modifier = modifier)
    }
}

/** Renderer OpenGL ES 2 di sempre ([PlanRenderer3D]). */
class GlSceneRenderer : AndroidSceneRenderer() {
    private val renderer = PlanRenderer3D()
    private var surface: GLSurfaceView? = null

    override fun createView(context: Context): View = GLSurfaceView(context).apply {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(PlanRenderer3D.ConfigChooser())
        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        surface = this
    }

    override fun setScene(scene: Scene3D) {
        renderer.setScene(scene)
        surface?.requestRender()
    }

    override fun setCamera(eye: Vec3, center: Vec3, up: Vec3, fovY: Double, near: Double, walking: Boolean) {
        renderer.view = Mat4.lookAt(eye, center, up)
        renderer.projection = { aspect -> Mat4.perspective(fovY, aspect.toDouble(), near, 100_000.0) }
        surface?.requestRender()
    }

    override fun onPause() { surface?.onPause() }
    override fun onResume() { surface?.onResume() }
    override fun release() { surface = null }
}
