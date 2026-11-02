package com.laberinto3d.juego.game.physics

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Cuaternión de rotación (x, y, z, w). Se usa para la orientación visual de la bola. */
class Quat(
    @JvmField var x: Float = 0f,
    @JvmField var y: Float = 0f,
    @JvmField var z: Float = 0f,
    @JvmField var w: Float = 1f
) {
    fun setIdentity(): Quat {
        x = 0f; y = 0f; z = 0f; w = 1f
        return this
    }

    fun set(o: Quat): Quat {
        x = o.x; y = o.y; z = o.z; w = o.w
        return this
    }

    fun copy(): Quat = Quat(x, y, z, w)

    /** this = a * b (aplica primero b y luego a). Seguro si a o b son this. */
    fun setMul(a: Quat, b: Quat): Quat {
        val nx = a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y
        val ny = a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x
        val nz = a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w
        val nw = a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z
        x = nx; y = ny; z = nz; w = nw
        return this
    }

    fun normalize(): Quat {
        val l = sqrt(x * x + y * y + z * z + w * w)
        if (l < 1e-9f) return setIdentity()
        val inv = 1f / l
        x *= inv; y *= inv; z *= inv; w *= inv
        return this
    }

    /** Rotación de [angle] radianes alrededor del eje (ax, ay, az), que debe estar normalizado. */
    fun setAxisAngle(ax: Float, ay: Float, az: Float, angle: Float): Quat {
        val h = angle * 0.5f
        val s = sin(h)
        x = ax * s; y = ay * s; z = az * s; w = cos(h)
        return this
    }

    /** Interpolación lineal normalizada entre a y b (suficiente para ángulos pequeños). */
    fun setNlerp(a: Quat, b: Quat, t: Float): Quat {
        val dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
        val sgn = if (dot < 0f) -1f else 1f
        x = a.x + (sgn * b.x - a.x) * t
        y = a.y + (sgn * b.y - a.y) * t
        z = a.z + (sgn * b.z - a.z) * t
        w = a.w + (sgn * b.w - a.w) * t
        return normalize()
    }

    /** Escribe la matriz 4x4 (column-major, estilo OpenGL) de esta rotación en [m]. */
    fun toMatrix(m: FloatArray) {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        m[0] = 1f - 2f * (yy + zz); m[1] = 2f * (xy + wz);       m[2] = 2f * (xz - wy);       m[3] = 0f
        m[4] = 2f * (xy - wz);      m[5] = 1f - 2f * (xx + zz);  m[6] = 2f * (yz + wx);       m[7] = 0f
        m[8] = 2f * (xz + wy);      m[9] = 2f * (yz - wx);       m[10] = 1f - 2f * (xx + yy); m[11] = 0f
        m[12] = 0f;                 m[13] = 0f;                  m[14] = 0f;                  m[15] = 1f
    }

    override fun toString(): String = "Quat($x, $y, $z, $w)"
}
