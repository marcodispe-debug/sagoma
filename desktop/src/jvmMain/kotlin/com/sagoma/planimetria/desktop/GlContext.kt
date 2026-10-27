package com.sagoma.planimetria.desktop

/** Contesto OpenGL fuori schermo della vista 3D: va creato, usato e chiuso sempre dallo stesso thread. */
internal interface GlContext {
    val isOpen: Boolean
    fun close()

    companion object {
        /** Su Windows con le funzioni di Windows ([WglContext]), su Linux con X11 ([GlxContext]). */
        fun create(): GlContext =
            if (System.getProperty("os.name").orEmpty().startsWith("Linux")) GlxContext() else WglContext()
    }
}
