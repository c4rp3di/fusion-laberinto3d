package com.laberinto3d.juego.game.render

import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.physics.World
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Cámara con perspectiva que mira al tablero desde un punto elevado. La distancia base se calcula
 * para que el tablero entero quepa en la zona útil de pantalla (hueco arriba para el HUD) para el giro y
 * la elevación actuales; [CameraControl.zoom] la multiplica. Con zoom < 1 la cámara va pasando del
 * centro del tablero a seguir a la bola.
 */
class Camera {
    val view = FloatArray(16)
    val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    var eyeX = 0f; private set
    var eyeY = 0f; private set
    var eyeZ = 0f; private set
    /** Distancia final cámara-objetivo. */
    var distance = 0f; private set
    /** Distancia base que encuadra el tablero (zoom = 1). */
    var fitDistance = 0f; private set

    private val tmp = FloatArray(3)
    private val ext = FloatArray(4)

    // caché del ajuste
    private var cWorld: World? = null
    private var cAspect = 0f
    private var cYaw = 1e9f
    private var cPitch = 1e9f

    private fun measure(world: World, aspect: Float, yaw: Float, pitchDeg: Float, d: Float,
                        tx: Float, ty: Float, tz: Float, out: FloatArray) {
        val e = Math.toRadians(pitchDeg.toDouble())
        val dy = sin(e).toFloat()
        val dh = cos(e).toFloat()
        val ex = tx + sin(yaw) * dh * d
        val ey = ty + dy * d
        val ez = tz + cos(yaw) * dh * d
        Matrix.lookAt(view, ex, ey, ez, tx, ty, tz, 0f, 1f, 0f)
        Matrix.perspective(proj, Config.CAMERA_FOV_DEG, aspect, max(0.1f, d * 0.2f), d * 3f + 10f)
        Matrix.multiply(viewProj, proj, view)
        var minX = 9f; var maxX = -9f; var minY = 9f; var maxY = -9f
        val xs = floatArrayOf(world.minX, world.maxX)
        val ys = floatArrayOf(0f, world.topY)
        val zs = floatArrayOf(world.minZ, world.maxZ)
        for (x in xs) for (y in ys) for (z in zs) {
            Matrix.project(viewProj, x, y, z, tmp)
            minX = min(minX, tmp[0]); maxX = max(maxX, tmp[0])
            minY = min(minY, tmp[1]); maxY = max(maxY, tmp[1])
        }
        out[0] = minX; out[1] = maxX; out[2] = minY; out[3] = maxY
    }

    /** Calcula (si cambió algo) la distancia base que encuadra el tablero. */
    private fun fit(world: World, aspect: Float, yaw: Float, pitchDeg: Float) {
        if (cWorld === world && cAspect == aspect && cYaw == yaw && cPitch == pitchDeg) return
        cWorld = world; cAspect = aspect; cYaw = yaw; cPitch = pitchDeg
        val tx = world.centerX
        val ty = 0.05f
        val tz = world.centerZ
        val allowedW = Config.FIT_MAX_X - Config.FIT_MIN_X
        val allowedH = Config.FIT_MAX_Y - Config.FIT_MIN_Y
        var lo = 1.0f
        var hi = 120f
        for (i in 0 until 40) {
            val mid = (lo + hi) * 0.5f
            measure(world, aspect, yaw, pitchDeg, mid, tx, ty, tz, ext)
            if (ext[1] - ext[0] <= allowedW && ext[3] - ext[2] <= allowedH) hi = mid else lo = mid
        }
        fitDistance = hi
    }

    /** Cámara de seguimiento (gran esfera): siempre centrada en la bola a una distancia fija por el zoom. */
    private fun configureFollow(aspect: Float, yaw: Float, pitchDeg: Float, zoom: Float, bx: Float, by: Float, bz: Float) {
        val d = SPHERE_DISTANCE * zoom
        distance = d
        fitDistance = SPHERE_DISTANCE
        val e = Math.toRadians(pitchDeg.toDouble())
        val dh = cos(e).toFloat()
        eyeX = bx + sin(yaw) * dh * d
        eyeY = by + sin(e).toFloat() * d
        eyeZ = bz + cos(yaw) * dh * d
        Matrix.lookAt(view, eyeX, eyeY, eyeZ, bx, by, bz, 0f, 1f, 0f)
        Matrix.perspective(proj, Config.CAMERA_FOV_DEG, aspect, max(0.1f, d * 0.2f), d * 3f + 40f)
        // la bola algo por debajo del centro (el HUD ocupa la parte de arriba)
        Matrix.shiftNdc(proj, 0f, -0.10f)
        Matrix.multiply(viewProj, proj, view)
    }

    /** Esfera giratoria: cámara fija mirando al centro de la esfera, que cabe entera en pantalla. */
    private fun configureOrbit(world: World, aspect: Float, yaw: Float, pitchDeg: Float, zoom: Float) {
        val tanV = kotlin.math.tan(Math.toRadians(Config.CAMERA_FOV_DEG * 0.5)).toFloat() * 0.80f
        val tanH = kotlin.math.tan(Math.toRadians(Config.CAMERA_FOV_DEG * 0.5)).toFloat() * aspect * 0.95f
        val t = min(tanV, tanH)
        val rs = world.shellRadius
        val fitD = rs * kotlin.math.sqrt(1f + 1f / (t * t))
        fitDistance = fitD
        val d = fitD * zoom
        distance = d
        val e = Math.toRadians(pitchDeg.toDouble())
        val dh = cos(e).toFloat()
        eyeX = sin(yaw) * dh * d
        eyeY = sin(e).toFloat() * d
        eyeZ = cos(yaw) * dh * d
        Matrix.lookAt(view, eyeX, eyeY, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.perspective(proj, Config.CAMERA_FOV_DEG, aspect, max(0.1f, d - rs * 1.5f), d + rs * 2f + 10f)
        Matrix.shiftNdc(proj, 0f, -0.08f)
        Matrix.multiply(viewProj, proj, view)
    }

    /**
     * Recalcula vista y proyección. [bx],[by],[bz]: posición (interpolada) de la bola, usada por
     * el seguimiento cuando el zoom es menor que 1.
     */
    fun configure(world: World, aspect: Float, bx: Float = 0f, by: Float = 0f, bz: Float = 0f) {
        val yaw = CameraControl.yaw
        val pitch = CameraControl.pitchDeg
        val zoom = CameraControl.zoom
        if (world.rotating) { configureOrbit(world, aspect, yaw, pitch, zoom); return }
        if (world.isSphere) { configureFollow(aspect, yaw, pitch, zoom, bx, by, bz); return }
        fit(world, aspect, yaw, pitch)
        val d = fitDistance * zoom
        distance = d
        // seguimiento: 0 con zoom >= 1, 1 con zoom <= 0.5
        val t = ((1f - zoom) / 0.5f).coerceIn(0f, 1f)
        val tx = world.centerX + (bx - world.centerX) * t
        val ty = 0.05f + (by - 0.05f) * t
        val tz = world.centerZ + (bz - world.centerZ) * t
        measure(world, aspect, yaw, pitch, d, tx, ty, tz, ext)
        val e = Math.toRadians(pitch.toDouble())
        eyeX = tx + sin(yaw) * cos(e).toFloat() * d
        eyeY = ty + sin(e).toFloat() * d
        eyeZ = tz + cos(yaw) * cos(e).toFloat() * d
        // centrar el tablero en la zona útil (se desvanece al seguir a la bola)
        val cxNow = (ext[0] + ext[1]) * 0.5f
        val cyNow = (ext[2] + ext[3]) * 0.5f
        val cxWant = (Config.FIT_MIN_X + Config.FIT_MAX_X) * 0.5f
        val cyWant = (Config.FIT_MIN_Y + Config.FIT_MAX_Y) * 0.5f
        val k = 1f - t
        Matrix.shiftNdc(proj, (cxWant - cxNow) * k, (cyWant - cyNow) * k)
        Matrix.multiply(viewProj, proj, view)
    }

    companion object {
        /** Distancia cámara-bola de la gran esfera con zoom = 1. */
        const val SPHERE_DISTANCE = 4.8f
    }
}
