package com.laberinto3d.juego.game.physics

import kotlin.math.sqrt

/** Resultado de un contacto esfera-caja. La normal apunta de la caja hacia la bola. */
class Contact {
    @JvmField var nx = 0f
    @JvmField var ny = 0f
    @JvmField var nz = 0f
    @JvmField var depth = 0f
}

/** Detección esfera vs. AABB por punto más cercano, con caso especial de centro dentro de la caja. */
object Collision {

    /**
     * Comprueba si la esfera (px,py,pz,r) penetra en [box]. Si es así rellena [out]
     * y devuelve true. Sin asignaciones de memoria.
     */
    fun sphereAabb(px: Float, py: Float, pz: Float, r: Float, box: Box, out: Contact): Boolean {
        val mn = box.min
        val mx = box.max
        // Rechazo rápido por AABB expandida
        if (px < mn.x - r || px > mx.x + r ||
            py < mn.y - r || py > mx.y + r ||
            pz < mn.z - r || pz > mx.z + r
        ) return false

        val cx = px.coerceIn(mn.x, mx.x)
        val cy = py.coerceIn(mn.y, mx.y)
        val cz = pz.coerceIn(mn.z, mx.z)
        val dx = px - cx
        val dy = py - cy
        val dz = pz - cz
        val distSq = dx * dx + dy * dy + dz * dz

        if (distSq > 1e-10f) {
            if (distSq >= r * r) return false
            val dist = sqrt(distSq)
            val inv = 1f / dist
            out.nx = dx * inv
            out.ny = dy * inv
            out.nz = dz * inv
            out.depth = r - dist
            return true
        }

        // Centro dentro de la caja: se empuja por la cara más cercana (nunca NaN).
        var best = px - mn.x; var nx = -1f; var ny = 0f; var nz = 0f
        var d = mx.x - px; if (d < best) { best = d; nx = 1f; ny = 0f; nz = 0f }
        d = py - mn.y; if (d < best) { best = d; nx = 0f; ny = -1f; nz = 0f }
        d = mx.y - py; if (d < best) { best = d; nx = 0f; ny = 1f; nz = 0f }
        d = pz - mn.z; if (d < best) { best = d; nx = 0f; ny = 0f; nz = -1f }
        d = mx.z - pz; if (d < best) { best = d; nx = 0f; ny = 0f; nz = 1f }
        out.nx = nx; out.ny = ny; out.nz = nz
        out.depth = best + r
        return true
    }

    /**
     * Esfera vs. triángulo (a,b,c) con normal unitaria (nx,ny,nz) hacia el lado "sólido visible".
     * Es de una sola cara: si el centro está más de 0.12 por detrás del plano no hay contacto
     * (evita que la bola salte al otro lado). Si el centro está algo por detrás, se empuja por la normal.
     * La normal de salida apunta del triángulo hacia la bola.
     */
    fun sphereTri(
        px: Float, py: Float, pz: Float, r: Float,
        t: FloatArray, o: Int, tnx: Float, tny: Float, tnz: Float, out: Contact
    ): Boolean {
        val ax = t[o]; val ay = t[o + 1]; val az = t[o + 2]
        val bx = t[o + 3]; val by = t[o + 4]; val bz = t[o + 5]
        val cx = t[o + 6]; val cy = t[o + 7]; val cz = t[o + 8]
        // distancia con signo al plano
        val pd = (px - ax) * tnx + (py - ay) * tny + (pz - az) * tnz
        if (pd > r || pd < -0.12f) return false

        // punto más cercano del triángulo (Ericson, Real-Time Collision Detection 5.1.5)
        val abx = bx - ax; val aby = by - ay; val abz = bz - az
        val acx = cx - ax; val acy = cy - ay; val acz = cz - az
        val apx = px - ax; val apy = py - ay; val apz = pz - az
        val d1 = abx * apx + aby * apy + abz * apz
        val d2 = acx * apx + acy * apy + acz * apz
        var qx: Float; var qy: Float; var qz: Float
        if (d1 <= 0f && d2 <= 0f) { qx = ax; qy = ay; qz = az }
        else {
            val bpx = px - bx; val bpy = py - by; val bpz = pz - bz
            val d3 = abx * bpx + aby * bpy + abz * bpz
            val d4 = acx * bpx + acy * bpy + acz * bpz
            if (d3 >= 0f && d4 <= d3) { qx = bx; qy = by; qz = bz }
            else {
                val vc = d1 * d4 - d3 * d2
                if (vc <= 0f && d1 >= 0f && d3 <= 0f) {
                    val v = d1 / (d1 - d3)
                    qx = ax + abx * v; qy = ay + aby * v; qz = az + abz * v
                } else {
                    val cpx = px - cx; val cpy = py - cy; val cpz = pz - cz
                    val d5 = abx * cpx + aby * cpy + abz * cpz
                    val d6 = acx * cpx + acy * cpy + acz * cpz
                    if (d6 >= 0f && d5 <= d6) { qx = cx; qy = cy; qz = cz }
                    else {
                        val vb = d5 * d2 - d1 * d6
                        if (vb <= 0f && d2 >= 0f && d6 <= 0f) {
                            val w = d2 / (d2 - d6)
                            qx = ax + acx * w; qy = ay + acy * w; qz = az + acz * w
                        } else {
                            val va = d3 * d6 - d5 * d4
                            if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) {
                                val w = (d4 - d3) / ((d4 - d3) + (d5 - d6))
                                qx = bx + (cx - bx) * w; qy = by + (cy - by) * w; qz = bz + (cz - bz) * w
                            } else {
                                val denom = 1f / (va + vb + vc)
                                val v = vb * denom
                                val w = vc * denom
                                qx = ax + abx * v + acx * w
                                qy = ay + aby * v + acy * w
                                qz = az + abz * v + acz * w
                            }
                        }
                    }
                }
            }
        }
        val dx = px - qx; val dy = py - qy; val dz = pz - qz
        val distSq = dx * dx + dy * dy + dz * dz
        if (distSq >= r * r) return false
        if (pd < 0f) {
            // centro detrás del plano pero cerca: se saca por la normal de la cara
            out.nx = tnx; out.ny = tny; out.nz = tnz
            out.depth = r - pd
            return true
        }
        val dist = sqrt(distSq)
        if (dist > 1e-5f) {
            val inv = 1f / dist
            out.nx = dx * inv; out.ny = dy * inv; out.nz = dz * inv
        } else {
            out.nx = tnx; out.ny = tny; out.nz = tnz
        }
        out.depth = r - dist
        return true
    }
}
