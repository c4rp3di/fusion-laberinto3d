package com.laberinto3d.juego.game.physics

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Orientación y giro de una esfera que el jugador hace rodar (Esfera II). La física corre en el sistema de
 * referencia de la esfera (la pista queda quieta); este objeto guarda cómo está girada la esfera respecto
 * al mundo ([q]: cuerpo -> mundo) y su velocidad angular, para obtener la gravedad en el cuerpo y las
 * fuerzas ficticias (Coriolis, centrífuga, Euler). Sólo se usa desde el hilo GL.
 */
class SphereState {
    /** Rotación cuerpo -> mundo. */
    @JvmField val q = Quat()
    /** Velocidad angular en el mundo (rad/s). */
    @JvmField val omegaW = Vec3()
    /** Velocidad angular y aceleración angular expresadas en el cuerpo. */
    @JvmField val omegaB = Vec3()
    @JvmField val alphaB = Vec3()

    private val dq = Quat()
    private val prevOmegaW = Vec3()

    fun reset() {
        q.setIdentity()
        omegaW.set(0f, 0f, 0f); omegaB.set(0f, 0f, 0f); alphaB.set(0f, 0f, 0f); prevOmegaW.set(0f, 0f, 0f)
    }

    /**
     * Gira la esfera de modo que el vector del cuerpo [t] (sentido de la pista) quede hacia +X del mundo y
     * [u] (hacia arriba de la pista) hacia +Y. Los dos deben ser unitarios y perpendiculares.
     */
    fun alignTo(tx: Float, ty: Float, tz: Float, ux: Float, uy: Float, uz: Float) {
        // z = t x u
        val zx = ty * uz - tz * uy
        val zy = tz * ux - tx * uz
        val zz = tx * uy - ty * ux
        // matriz R (mundo = R * cuerpo): filas = imágenes de ejes: R*t = X, R*u = Y, R*z = Z  =>  R = [t u z]^T
        val m00 = tx; val m01 = ty; val m02 = tz
        val m10 = ux; val m11 = uy; val m12 = uz
        val m20 = zx; val m21 = zy; val m22 = zz
        setFromMatrix(m00, m01, m02, m10, m11, m12, m20, m21, m22)
        omegaW.set(0f, 0f, 0f); omegaB.set(0f, 0f, 0f); alphaB.set(0f, 0f, 0f); prevOmegaW.set(0f, 0f, 0f)
    }

    private fun setFromMatrix(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float, g: Float, h: Float, i: Float) {
        val tr = a + e + i
        if (tr > 0f) {
            val s = sqrt(tr + 1f) * 2f
            q.w = 0.25f * s; q.x = (h - f) / s; q.y = (c - g) / s; q.z = (d - b) / s
        } else if (a > e && a > i) {
            val s = sqrt(1f + a - e - i) * 2f
            q.w = (h - f) / s; q.x = 0.25f * s; q.y = (b + d) / s; q.z = (c + g) / s
        } else if (e > i) {
            val s = sqrt(1f + e - a - i) * 2f
            q.w = (c - g) / s; q.x = (b + d) / s; q.y = 0.25f * s; q.z = (f + h) / s
        } else {
            val s = sqrt(1f + i - a - e) * 2f
            q.w = (d - b) / s; q.x = (c + g) / s; q.y = (f + h) / s; q.z = 0.25f * s
        }
        q.normalize()
    }

    /**
     * Avanza [dt] s. [tx],[ty],[tz]: velocidad angular deseada (mundo). Con el dedo puesto ([touching]) la
     * esfera sigue al dedo con constante de tiempo [followTau]; sin dedo conserva el impulso y lo pierde
     * con constante [decayTau].
     */
    fun step(dt: Float, tx: Float, ty: Float, tz: Float, touching: Boolean, followTau: Float, decayTau: Float) {
        prevOmegaW.set(omegaW)
        if (touching) {
            val k = 1f - exp(-dt / followTau)
            omegaW.x += (tx - omegaW.x) * k
            omegaW.y += (ty - omegaW.y) * k
            omegaW.z += (tz - omegaW.z) * k
        } else {
            val k = exp(-dt / decayTau)
            omegaW.x *= k; omegaW.y *= k; omegaW.z *= k
        }
        val w = sqrt(omegaW.x * omegaW.x + omegaW.y * omegaW.y + omegaW.z * omegaW.z)
        if (w > MAX_OMEGA) { val s = MAX_OMEGA / w; omegaW.x *= s; omegaW.y *= s; omegaW.z *= s }
        val w2 = sqrt(omegaW.x * omegaW.x + omegaW.y * omegaW.y + omegaW.z * omegaW.z)
        if (w2 > 1e-5f) {
            dq.setAxisAngle(omegaW.x / w2, omegaW.y / w2, omegaW.z / w2, w2 * dt)
            q.setMul(dq, q).normalize()
        }
        // al cuerpo
        toBody(omegaW.x, omegaW.y, omegaW.z, omegaB)
        toBody((omegaW.x - prevOmegaW.x) / dt, (omegaW.y - prevOmegaW.y) / dt, (omegaW.z - prevOmegaW.z) / dt, alphaB)
    }

    /** out = q^-1 * v (vector del mundo -> cuerpo). */
    fun toBody(vx: Float, vy: Float, vz: Float, out: Vec3) {
        // rotación inversa = conjugado
        rot(-q.x, -q.y, -q.z, q.w, vx, vy, vz, out)
    }

    /** out = q * v (vector del cuerpo -> mundo). */
    fun toWorld(vx: Float, vy: Float, vz: Float, out: Vec3) {
        rot(q.x, q.y, q.z, q.w, vx, vy, vz, out)
    }

    private fun rot(qx: Float, qy: Float, qz: Float, qw: Float, vx: Float, vy: Float, vz: Float, out: Vec3) {
        // v' = v + 2*w*(u x v) + 2*(u x (u x v))
        val cx = qy * vz - qz * vy
        val cy = qz * vx - qx * vz
        val cz = qx * vy - qy * vx
        val dx = qy * cz - qz * cy
        val dy = qz * cx - qx * cz
        val dz = qx * cy - qy * cx
        out.x = vx + 2f * (qw * cx + dx)
        out.y = vy + 2f * (qw * cy + dy)
        out.z = vz + 2f * (qw * cz + dz)
    }

    /** Matriz 4x4 (column-major) cuerpo -> mundo. */
    fun toMatrix(m: FloatArray) = q.toMatrix(m)

    companion object {
        const val MAX_OMEGA = 3.5f
    }
}
