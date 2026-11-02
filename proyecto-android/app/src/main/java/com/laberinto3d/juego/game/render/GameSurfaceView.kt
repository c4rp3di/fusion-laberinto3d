package com.laberinto3d.juego.game.render

import android.content.Context
import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.atan2
import kotlin.math.hypot
import com.laberinto3d.juego.game.core.GameState
import com.laberinto3d.juego.game.core.Phase
import com.laberinto3d.juego.game.core.Telemetry
import com.laberinto3d.juego.game.input.SphereInput
import kotlin.math.cos
import kotlin.math.sin
import com.laberinto3d.juego.game.diag.GameLog
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** GLSurfaceView con contexto OpenGL ES 3.x, MSAA 4x si el dispositivo lo ofrece y render continuo. */
class GameSurfaceView(context: Context, renderer: GameRenderer) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MsaaConfigChooser())
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // ---- Gestos de vista: dos dedos = pellizcar (zoom), girar (giro) y arrastrar vertical (elevación) ----
    private var lastDist = 0f
    private var lastAngle = 0f
    private var lastMidY = 0f
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            CameraControl.reset()
            CameraControl.gestureEnded = true
            GameLog.i("Input", "Vista restablecida (doble toque)")
            return true
        }
    })

    private fun beginGesture(e: MotionEvent) {
        val dx = e.getX(1) - e.getX(0)
        val dy = e.getY(1) - e.getY(0)
        lastDist = hypot(dx, dy).coerceAtLeast(1f)
        lastAngle = atan2(dy, dx)
        lastMidY = (e.getY(0) + e.getY(1)) * 0.5f
        CameraControl.gesturing = true
        CameraControl.autoSuspended = true
    }

    private fun endGesture() {
        if (CameraControl.gesturing) {
            CameraControl.gesturing = false
            CameraControl.gestureEnded = true
        }
    }

    // ---- Esfera giratoria: un dedo arrastra la cara visible de la esfera (trackball) ----
    private var dragX = 0f
    private var dragY = 0f
    private var dragOk = false

    private fun sphereActive(): Boolean {
        val s = GameState.currentSession ?: return false
        return s.world.rotating && s.phase == Phase.PLAYING
    }

    private fun sphereDrag(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragX = e.x; dragY = e.y; dragOk = true; SphereInput.touching = true }
            MotionEvent.ACTION_MOVE -> if (dragOk && e.pointerCount == 1) {
                val dx = e.x - dragX; val dy = e.y - dragY
                dragX = e.x; dragY = e.y
                val yaw = CameraControl.yaw
                val el = Math.toRadians(CameraControl.pitchDeg.toDouble())
                val ce = cos(el).toFloat(); val se = sin(el).toFloat()
                // n = hacia la cámara, r = derecha de pantalla, u = n x r (arriba de pantalla)
                val nx = sin(yaw) * ce; val ny = se; val nz = cos(yaw) * ce
                val rx = cos(yaw); val ry = 0f; val rz = -sin(yaw)
                val ux = ny * rz - nz * ry; val uy = nz * rx - nx * rz; val uz = nx * ry - ny * rx
                val vx = dx * rx - dy * ux; val vy = dx * ry - dy * uy; val vz = dx * rz - dy * uz
                val k = DRAG_GAIN / width.toFloat().coerceAtLeast(1f)
                // w = n x v
                SphereInput.add((ny * vz - nz * vy) * k, (nz * vx - nx * vz) * k, (nx * vy - ny * vx) * k)
            }
            MotionEvent.ACTION_POINTER_DOWN -> { dragOk = false; SphereInput.touching = false }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragOk = false; SphereInput.touching = false }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        detector.onTouchEvent(e)
        if (sphereActive() || e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) sphereDrag(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount >= 2) beginGesture(e)
            MotionEvent.ACTION_MOVE -> if (e.pointerCount >= 2 && CameraControl.gesturing) {
                val dx = e.getX(1) - e.getX(0)
                val dy = e.getY(1) - e.getY(0)
                val dist = hypot(dx, dy).coerceAtLeast(1f)
                val ang = atan2(dy, dx)
                val midY = (e.getY(0) + e.getY(1)) * 0.5f
                var da = ang - lastAngle
                val pi = Math.PI.toFloat()
                if (da > pi) da -= 2f * pi
                if (da < -pi) da += 2f * pi
                CameraControl.yaw += da
                CameraControl.zoom *= lastDist / dist
                CameraControl.pitchDeg += (midY - lastMidY) * PITCH_PER_PX / resources.displayMetrics.density
                CameraControl.clamp()
                lastDist = dist; lastAngle = ang; lastMidY = midY
            }
            MotionEvent.ACTION_POINTER_UP -> if (e.pointerCount - 1 < 2) endGesture()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endGesture()
        }
        return true
    }

    /** Elige RGBA8888 + profundidad 24 (o 16) + 4x MSAA, con varios planes de reserva. */
    companion object {
        /** Grados de elevación por dp de arrastre vertical con dos dedos. */
        private const val PITCH_PER_PX = 0.25f
        /** Radianes de giro de la esfera por cada anchura de pantalla arrastrada (la esfera ocupa ~80% del ancho). */
        private const val DRAG_GAIN = 2.6f
    }

    private class MsaaConfigChooser : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val plans = arrayOf(
                intArrayOf(24, 4), intArrayOf(16, 4), intArrayOf(24, 2),
                intArrayOf(24, 0), intArrayOf(16, 0)
            )
            for (plan in plans) {
                val depth = plan[0]
                val samples = plan[1]
                val spec = ArrayList<Int>()
                spec.addAll(listOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT
                ))
                if (samples > 0) spec.addAll(listOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples))
                spec.add(EGL10.EGL_NONE)
                val arr = spec.toIntArray()
                val num = IntArray(1)
                if (!egl.eglChooseConfig(display, arr, null, 0, num) || num[0] <= 0) continue
                val configs = arrayOfNulls<EGLConfig>(num[0])
                if (!egl.eglChooseConfig(display, arr, configs, num[0], num)) continue
                for (c in configs) {
                    if (c == null) continue
                    val d = attr(egl, display, c, EGL10.EGL_DEPTH_SIZE)
                    val r = attr(egl, display, c, EGL10.EGL_RED_SIZE)
                    val s = attr(egl, display, c, EGL10.EGL_SAMPLES)
                    if (d >= depth && r == 8) {
                        Telemetry.msaaSamples = s
                        GameLog.i("EGL", "Config elegida: depth=$d samples=$s (plan depth=$depth samples=$samples)")
                        return c
                    }
                }
            }
            throw IllegalArgumentException("No se encontró ninguna EGLConfig compatible con OpenGL ES 3")
        }

        private fun attr(egl: EGL10, d: EGLDisplay, c: EGLConfig, a: Int): Int {
            val v = IntArray(1)
            return if (egl.eglGetConfigAttrib(d, c, a, v)) v[0] else 0
        }

        companion object {
            const val EGL_OPENGL_ES3_BIT = 0x0040
        }
    }
}
