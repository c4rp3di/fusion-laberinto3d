package com.laberinto3d.juego.game.render

import com.laberinto3d.juego.game.core.Config

/**
 * Estado de la vista controlada por el jugador con dos dedos (lo escribe el hilo UI,
 * lo leen el hilo GL y GameLoop). Mientras [gesturing] es true la física queda congelada.
 */
object CameraControl {
    const val PITCH_MIN = 28f
    const val PITCH_MAX = 82f
    const val ZOOM_MIN = 0.35f
    const val ZOOM_MAX = 1.6f

    /** Giro alrededor de la vertical, en radianes. 0 = vista estándar (arriba en pantalla = -Z). */
    @Volatile var yaw = 0f
    /** Elevación de la cámara en grados (90 = desde arriba). */
    @Volatile var pitchDeg = Config.CAMERA_ELEVATION_DEG
    /** Multiplicador de la distancia ajustada al tablero (<1 acerca y sigue a la bola). */
    @Volatile var zoom = 1f
    /** true mientras hay dos dedos en la pantalla. */
    @Volatile var gesturing = false
    /** Se pone a true al terminar un gesto; GameLoop lo consume para realinear los controles. */
    @Volatile var gestureEnded = false

    /** true tras un gesto manual: la cámara automática de la gran esfera se detiene hasta el doble toque. */
    @Volatile var autoSuspended = false

    fun reset() {
        autoSuspended = false
        yaw = 0f
        pitchDeg = Config.CAMERA_ELEVATION_DEG
        zoom = 1f
    }

    val isDefault: Boolean
        get() = yaw == 0f && pitchDeg == Config.CAMERA_ELEVATION_DEG && zoom == 1f

    fun clamp() {
        if (pitchDeg < PITCH_MIN) pitchDeg = PITCH_MIN
        if (pitchDeg > PITCH_MAX) pitchDeg = PITCH_MAX
        if (zoom < ZOOM_MIN) zoom = ZOOM_MIN
        if (zoom > ZOOM_MAX) zoom = ZOOM_MAX
        // yaw acotado a (-pi, pi]
        val twoPi = (2.0 * Math.PI).toFloat()
        while (yaw > Math.PI) yaw -= twoPi
        while (yaw <= -Math.PI) yaw += twoPi
    }
}
