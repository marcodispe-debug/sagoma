package com.sagoma.planimetria.desktop

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLX
import org.lwjgl.opengl.GLX13
import org.lwjgl.system.linux.X11

/**
 * Contesto OpenGL su Linux (X11), su una superficie nascosta (pbuffer): come [WglContext] si disegna sempre fuori
 * schermo. Serve anche alle prove della vista 3D fuori da Windows (per esempio nel cloud, con Xvfb e la scheda
 * grafica "software" di Mesa). Va creato, usato e chiuso sempre dallo stesso thread.
 */
internal class GlxContext : GlContext {
    private var display = 0L
    private var pbuffer = 0L
    private var context = 0L

    init {
        display = X11.XOpenDisplay(null as CharSequence?)
        check(display != 0L) { "Schermo X11 non disponibile (DISPLAY)" }
        val configs = GLX13.glXChooseFBConfig(display, X11.XDefaultScreen(display), intArrayOf(
            GLX13.GLX_DRAWABLE_TYPE, GLX13.GLX_PBUFFER_BIT,
            GLX13.GLX_RENDER_TYPE, GLX13.GLX_RGBA_BIT,
            GLX.GLX_RED_SIZE, 8, GLX.GLX_GREEN_SIZE, 8, GLX.GLX_BLUE_SIZE, 8, GLX.GLX_ALPHA_SIZE, 8,
            GLX.GLX_DEPTH_SIZE, 24,
            0,
        ))
        check(configs != null && configs.remaining() > 0) { "Formato OpenGL non disponibile" }
        val config = configs.get(0)
        X11.XFree(configs)
        pbuffer = GLX13.glXCreatePbuffer(display, config, intArrayOf(GLX13.GLX_PBUFFER_WIDTH, 16, GLX13.GLX_PBUFFER_HEIGHT, 16, 0))
        context = GLX13.glXCreateNewContext(display, config, GLX13.GLX_RGBA_TYPE, 0L, true)
        check(context != 0L) { "OpenGL non disponibile" }
        check(GLX13.glXMakeContextCurrent(display, pbuffer, pbuffer, context)) { "OpenGL non attivabile" }
        GL.createCapabilities()
    }

    override val isOpen: Boolean get() = context != 0L

    override fun close() {
        if (context != 0L) {
            GLX13.glXMakeContextCurrent(display, 0L, 0L, 0L)
            GLX.glXDestroyContext(display, context)
            context = 0L
        }
        if (pbuffer != 0L) { GLX13.glXDestroyPbuffer(display, pbuffer); pbuffer = 0L }
        if (display != 0L) { X11.XCloseDisplay(display); display = 0L }
        GL.setCapabilities(null)
    }
}
