package com.sagoma.planimetria.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import org.w3c.dom.HTMLElement
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.WebElementView
import com.sagoma.planimetria.geometry.Scene3D
import com.sagoma.planimetria.geometry.Vec3
import com.sagoma.planimetria.ui.SceneRenderer
import com.sagoma.planimetria.ui.SimpleShaders
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import org.w3c.dom.HTMLCanvasElement

// WebGL in JavaScript: contesto, caricamento dei triangoli e disegno.

private fun glInit(canvas: HTMLCanvasElement, vs: String, fs: String, r: Float, g: Float, b: Float): JsAny = js(
    """(() => {
        const gl = canvas.getContext('webgl', { antialias: true, preserveDrawingBuffer: false });
        if (!gl) return null;
        const sh = (t, s) => { const x = gl.createShader(t); gl.shaderSource(x, s); gl.compileShader(x); return x; };
        const p = gl.createProgram();
        gl.attachShader(p, sh(gl.VERTEX_SHADER, vs));
        gl.attachShader(p, sh(gl.FRAGMENT_SHADER, fs));
        gl.linkProgram(p);
        return { gl: gl, p: p, aPos: gl.getAttribLocation(p, 'aPos'), aNormal: gl.getAttribLocation(p, 'aNormal'),
                 aColor: gl.getAttribLocation(p, 'aColor'), uMvp: gl.getUniformLocation(p, 'uMvp'),
                 bufs: [gl.createBuffer(), gl.createBuffer()], counts: [0, 0], clear: [r, g, b] };
    })()""",
)

private fun glUpload(ctx: JsAny, which: Int, data: Float32Array, count: Int): Unit = js(
    """{ const gl = ctx.gl; gl.bindBuffer(gl.ARRAY_BUFFER, ctx.bufs[which]); gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW); ctx.counts[which] = count; }""",
)

private fun glDraw(ctx: JsAny, canvas: HTMLCanvasElement, mvp: Float32Array, stride: Int): Unit = js(
    """{
        const gl = ctx.gl;
        const dpr = window.devicePixelRatio || 1;
        const w = Math.max(1, Math.round(canvas.clientWidth * dpr)), h = Math.max(1, Math.round(canvas.clientHeight * dpr));
        if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
        gl.viewport(0, 0, w, h);
        gl.clearColor(ctx.clear[0], ctx.clear[1], ctx.clear[2], 1);
        gl.enable(gl.DEPTH_TEST);
        gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
        gl.useProgram(ctx.p);
        gl.uniformMatrix4fv(ctx.uMvp, false, mvp);
        const draw = (i) => {
            if (ctx.counts[i] === 0) return;
            gl.bindBuffer(gl.ARRAY_BUFFER, ctx.bufs[i]);
            gl.enableVertexAttribArray(ctx.aPos); gl.vertexAttribPointer(ctx.aPos, 3, gl.FLOAT, false, stride, 0);
            gl.enableVertexAttribArray(ctx.aNormal); gl.vertexAttribPointer(ctx.aNormal, 3, gl.FLOAT, false, stride, 12);
            gl.enableVertexAttribArray(ctx.aColor); gl.vertexAttribPointer(ctx.aColor, 4, gl.FLOAT, false, stride, 24);
            gl.drawArrays(gl.TRIANGLES, 0, ctx.counts[i]);
        };
        gl.depthMask(true); gl.disable(gl.BLEND); draw(0);
        gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false); draw(1); gl.depthMask(true);
    }""",
)

private fun FloatArray.toFloat32Array(): Float32Array {
    val a = Float32Array(size)
    for (i in indices) a[i] = this[i]
    return a
}

/**
 * Vista 3D nel browser: WebGL in un elemento `<canvas>` della pagina, con gli stessi shader del renderer
 * semplice di Android. Il canvas lascia passare mouse e dita all'app (i gesti sono quelli comuni).
 */
class WebGlSceneRenderer : SceneRenderer {
    private val canvas = (document.createElement("canvas") as HTMLCanvasElement).apply {
        style.width = "100%"
        style.height = "100%"
        style.display = "block"
        style.setProperty("pointer-events", "none")
    }
    private val ctx: JsAny? = run {
        val c = SimpleShaders.clearColor
        glInit(canvas, SimpleShaders.vertex(SimpleShaders.ES_HEADER), SimpleShaders.fragment(SimpleShaders.ES_HEADER), c[0], c[1], c[2])
    }
    private val camera = SimpleShaders.CameraState()
    private var frameRequested = false

    /** Disegna al prossimo fotogramma del browser (più richieste diventano un solo disegno). */
    private fun redraw() {
        if (ctx == null || frameRequested) return
        frameRequested = true
        window.requestAnimationFrame {
            frameRequested = false
            val w = canvas.clientWidth.coerceAtLeast(1)
            val h = canvas.clientHeight.coerceAtLeast(1)
            glDraw(ctx, canvas, camera.mvp(w.toFloat() / h).toFloat32Array(), Scene3D.FLOATS_PER_VERTEX * 4)
        }
    }

    /**
     * Il canvas WebGL sta sotto l'app (che altrimenti coprirebbe i pulsanti della vista 3D) e l'app lascia
     * trasparente il riquadro della scena: così si vedono la scena e, sopra, i pulsanti come su Android.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Surface(modifier: Modifier) {
        WebElementView(
            factory = { canvas },
            modifier = modifier
                .onSizeChanged { redraw() }
                .drawBehind { drawRect(Color.Black, blendMode = BlendMode.Clear) },
        )
        LaunchedEffect(Unit) {
            // Il livello che contiene il canvas va sotto quello dell'app.
            (canvas.parentElement?.parentElement as? HTMLElement)?.style?.zIndex = "-1"
            redraw()
        }
        redraw()
    }

    override fun setScene(scene: Scene3D) {
        val c = ctx ?: return
        glUpload(c, 0, scene.opaque.toFloat32Array(), scene.opaque.size / Scene3D.FLOATS_PER_VERTEX)
        glUpload(c, 1, scene.transparent.toFloat32Array(), scene.transparent.size / Scene3D.FLOATS_PER_VERTEX)
        redraw()
    }

    override fun setCamera(eye: Vec3, center: Vec3, up: Vec3, fovY: Double, near: Double, walking: Boolean) {
        camera.set(eye, center, up, fovY, near)
        redraw()
    }

    /** Il browser non ha WebGL: si mostra l'avviso invece della scena. */
    val available: Boolean get() = ctx != null
}
