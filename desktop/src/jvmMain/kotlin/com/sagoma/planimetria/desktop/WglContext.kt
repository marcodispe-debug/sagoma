package com.sagoma.planimetria.desktop

import org.lwjgl.opengl.GL
import org.lwjgl.opengl.WGL
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.windows.GDI32
import org.lwjgl.system.windows.PIXELFORMATDESCRIPTOR
import org.lwjgl.system.windows.User32
import org.lwjgl.system.windows.WNDCLASSEX
import org.lwjgl.system.windows.WindowsLibrary

/**
 * Contesto OpenGL su una finestra Windows nascosta, creato direttamente con le funzioni di Windows (user32,
 * gdi32, opengl32) invece che con GLFW: la libreria glfw.dll non è firmata e "Smart App Control" di Windows 11
 * può bloccarla. Si disegna sempre fuori schermo (framebuffer), la finestra serve solo a ospitare il contesto.
 * Va creato, usato e chiuso sempre dallo stesso thread.
 */
internal class WglContext {
    private var hwnd = 0L
    private var hdc = 0L
    private var hglrc = 0L

    init {
        MemoryStack.stackPush().use { stack ->
            val wc = WNDCLASSEX.calloc(stack)
                .cbSize(WNDCLASSEX.SIZEOF)
                .style(User32.CS_OWNDC)
                .hInstance(WindowsLibrary.HINSTANCE)
                .lpszClassName(stack.UTF16(CLASS_NAME))
            // Nessuna gestione dei messaggi: basta quella standard di Windows (DefWindowProcW).
            MemoryUtil.memPutAddress(wc.address() + WNDCLASSEX.LPFNWNDPROC, User32.Functions.DefWindowProc)
            // Se la classe esiste già (secondo renderer) la registrazione fallisce senza danni.
            User32.RegisterClassEx(null, wc)
        }
        hwnd = User32.CreateWindowEx(null, 0, CLASS_NAME, "Sagoma 3D", User32.WS_OVERLAPPEDWINDOW, 0, 0, 16, 16, 0L, 0L, WindowsLibrary.HINSTANCE, 0L)
        check(hwnd != 0L) { "Finestra OpenGL non creata" }
        hdc = User32.GetDC(hwnd)
        MemoryStack.stackPush().use { stack ->
            val pfd = PIXELFORMATDESCRIPTOR.calloc(stack)
                .nSize(PIXELFORMATDESCRIPTOR.SIZEOF.toShort())
                .nVersion(1)
                .dwFlags(GDI32.PFD_DRAW_TO_WINDOW or GDI32.PFD_SUPPORT_OPENGL or GDI32.PFD_DOUBLEBUFFER)
                .iPixelType(GDI32.PFD_TYPE_RGBA)
                .cColorBits(32)
                .cDepthBits(24)
                .cStencilBits(8)
                .iLayerType(GDI32.PFD_MAIN_PLANE)
            val format = GDI32.ChoosePixelFormat(null, hdc, pfd)
            check(format != 0 && GDI32.SetPixelFormat(null, hdc, format, pfd)) { "Formato OpenGL non disponibile" }
        }
        hglrc = WGL.wglCreateContext(null, hdc)
        check(hglrc != 0L) { "OpenGL non disponibile" }
        check(WGL.wglMakeCurrent(null, hdc, hglrc)) { "OpenGL non attivabile" }
        GL.createCapabilities()
    }

    val isOpen: Boolean get() = hglrc != 0L

    fun close() {
        if (hglrc != 0L) {
            WGL.wglMakeCurrent(null, 0L, 0L)
            WGL.wglDeleteContext(null, hglrc)
            hglrc = 0L
        }
        if (hdc != 0L) { User32.ReleaseDC(hwnd, hdc); hdc = 0L }
        if (hwnd != 0L) { User32.DestroyWindow(null, hwnd); hwnd = 0L }
        GL.setCapabilities(null)
    }

    private companion object {
        const val CLASS_NAME = "SagomaGL"
    }
}
