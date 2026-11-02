package com.laberinto3d.juego.game.physics

import kotlin.math.sqrt

/**
 * Vector 3D. Es mutable para no generar basura en el bucle de física, pero los
 * vectores que se publican entre hilos (p. ej. lecturas del acelerómetro) se
 * tratan como inmutables: se crean nuevos y nunca se modifican tras publicarlos.
 */
class Vec3(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f, @JvmField var z: Float = 0f) {

    operator fun plus(o: Vec3): Vec3 = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3): Vec3 = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float): Vec3 = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun lengthSq(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSq())

    /** Devuelve una copia normalizada (o el vector nulo si la longitud es ~0). */
    fun normalized(): Vec3 {
        val l = length()
        return if (l > 1e-9f) Vec3(x / l, y / l, z / l) else Vec3(0f, 0f, 0f)
    }

    /** Limita cada componente al rango [-m, m]. */
    fun clampAxes(m: Float): Vec3 = Vec3(
        x.coerceIn(-m, m), y.coerceIn(-m, m), z.coerceIn(-m, m)
    )

    fun set(nx: Float, ny: Float, nz: Float): Vec3 {
        x = nx; y = ny; z = nz
        return this
    }

    fun set(o: Vec3): Vec3 {
        x = o.x; y = o.y; z = o.z
        return this
    }

    fun copy(): Vec3 = Vec3(x, y, z)

    override fun toString(): String = "(${fmt(x)}, ${fmt(y)}, ${fmt(z)})"

    private fun fmt(v: Float): String = String.format(java.util.Locale.US, "%.3f", v)
}
