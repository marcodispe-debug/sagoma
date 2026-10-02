package com.sagoma.planimetria.ui

import com.sagoma.planimetria.geometry.Mat4
import com.sagoma.planimetria.geometry.Vec3

/**
 * Shader del renderer 3D semplice (colori della scena con una luce principale e una di riempimento), uguali
 * su tutte le piattaforme: OpenGL ES 2 su Android, OpenGL sul computer, WebGL nel browser. Cambia solo
 * l'intestazione (`#version` o `precision`).
 */
object SimpleShaders {
    fun vertex(header: String) = header + """
        uniform mat4 uMvp;
        attribute vec3 aPos;
        attribute vec3 aNormal;
        attribute vec4 aColor;
        varying vec4 vColor;
        varying vec3 vNormal;
        varying float vHeight;
        void main() {
            vColor = aColor;
            vNormal = aNormal;
            vHeight = aPos.y;
            gl_Position = uMvp * vec4(aPos, 1.0);
        }
    """

    // Luce principale dall'alto di lato più una luce di riempimento: le facce dei muri si distinguono.
    fun fragment(header: String) = header + """
        varying vec4 vColor;
        varying vec3 vNormal;
        varying float vHeight;
        void main() {
            float len = length(vNormal);
            if (len < 0.1) { gl_FragColor = vColor; return; }
            vec3 n = vNormal / len;
            float key = max(dot(n, normalize(vec3(0.45, 0.85, 0.30))), 0.0);
            float fill = max(dot(n, normalize(vec3(-0.5, 0.3, -0.6))), 0.0);
            float light = min(1.0, 0.76 + 0.26 * key + 0.10 * fill);
            // Sulle pareti un poco più scuro in basso: angoli e spigoli si leggono meglio.
            float wallShade = abs(n.y) < 0.5 ? mix(0.86, 1.0, clamp(vHeight / 240.0, 0.0, 1.0)) : 1.0;
            gl_FragColor = vec4(vColor.rgb * light * wallShade, vColor.a);
        }
    """

    /** Intestazioni: OpenGL ES 2 e WebGL 1 vogliono la precisione, OpenGL desktop la versione. */
    const val ES_HEADER = "precision mediump float;\n"
    const val DESKTOP_HEADER = "#version 120\n"

    /** Colore di sfondo della vista 3D (cielo chiaro). */
    val clearColor = floatArrayOf(0.93f, 0.95f, 0.97f, 1f)

    /** Stato della telecamera condiviso dai renderer semplici: matrice vista e proiezione per proporzioni. */
    class CameraState {
        var view: FloatArray = Mat4.lookAt(Vec3(0.0, 1000.0, 1000.0), Vec3.Zero, Vec3.Up)
        var fovY = 60.0
        var near = 5.0
        fun mvp(aspect: Float): FloatArray = Mat4.multiply(Mat4.perspective(fovY, aspect.toDouble(), near, 100_000.0), view)
        fun set(eye: Vec3, center: Vec3, up: Vec3, fovY: Double, near: Double) {
            view = Mat4.lookAt(eye, center, up)
            this.fovY = fovY
            this.near = near
        }
    }
}
