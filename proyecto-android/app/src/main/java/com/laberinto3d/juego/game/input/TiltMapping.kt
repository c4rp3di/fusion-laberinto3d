package com.laberinto3d.juego.game.input

import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.core.Tuning
import com.laberinto3d.juego.game.physics.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Convierte la lectura del acelerómetro en el vector de gravedad del mundo.
 *
 * 1. Pasa la lectura de coordenadas del dispositivo a coordenadas de pantalla según la
 *    rotación real (así vale para cualquiera de los dos landscape).
 * 2. Calcula los ángulos de alabeo (lado derecho arriba) y cabeceo (borde superior arriba).
 * 3. Resta los ángulos del pose neutral calibrado, aplica zona muerta suave, sensibilidad
 *    y limita a la inclinación máxima.
 * 4. gravedad.x = -G sin(alabeo), gravedad.z = +G sin(cabeceo), gravedad.y = -sqrt(G² - x² - z²).
 *
 * Mundo: X = derecha de la pantalla, Z = hacia el jugador (abajo en pantalla).
 */
object TiltMapping {

    /** Ángulos (rad) en el sistema de pantalla: [0] = alabeo, [1] = cabeceo. */
    fun angles(v: Vec3, rotation: Int, out: FloatArray) {
        val sx: Float
        val sy: Float
        when (rotation) {
            1 -> { sx = -v.y; sy = v.x }
            2 -> { sx = -v.x; sy = -v.y }
            3 -> { sx = v.y; sy = -v.x }
            else -> { sx = v.x; sy = v.y }
        }
        val sz = v.z
        out[0] = atan2(sx, sqrt(sy * sy + sz * sz))
        out[1] = atan2(sy, sqrt(sx * sx + sz * sz))
    }

    private val cur = FloatArray(2)
    private val neu = FloatArray(2)

    /**
     * Escribe en [out] la gravedad (m/s²) y devuelve en [angleOut] (si no es null) los
     * ángulos efectivos en grados [alabeo, cabeceo] ya con zona muerta y sensibilidad.
     */
    @Synchronized
    fun compute(
        filtered: Vec3,
        neutral: Vec3,
        rotation: Int,
        out: Vec3,
        angleOut: FloatArray? = null
    ) {
        angles(filtered, rotation, cur)
        angles(neutral, rotation, neu)
        var roll = cur[0] - neu[0]
        var pitch = cur[1] - neu[1]

        if (Tuning.swapXY) { val t = roll; roll = pitch; pitch = t }
        if (Tuning.invertX) roll = -roll
        if (Tuning.invertY) pitch = -pitch

        val dz = Math.toRadians(Tuning.deadZoneDeg.toDouble()).toFloat()
        val maxT = Math.toRadians(Tuning.maxTiltDeg.toDouble()).toFloat()
        val k = Tuning.sensitivity
        roll = shape(roll, dz, k, maxT)
        pitch = shape(pitch, dz, k, maxT)

        val g = Config.GRAVITY
        val gx = -g * sin(roll)
        val gz = g * sin(pitch)
        val gy = -sqrt(max(0f, g * g - gx * gx - gz * gz))
        out.set(gx, gy, gz)
        if (angleOut != null && angleOut.size >= 2) {
            angleOut[0] = Math.toDegrees(roll.toDouble()).toFloat()
            angleOut[1] = Math.toDegrees(pitch.toDouble()).toFloat()
        }
    }

    /** Zona muerta suave (resta el umbral), escala por sensibilidad y satura. */
    private fun shape(a: Float, dead: Float, sens: Float, maxAngle: Float): Float {
        val m = abs(a)
        if (m <= dead) return 0f
        val r = (m - dead) * sens
        return sign(a) * if (r > maxAngle) maxAngle else r
    }
}
