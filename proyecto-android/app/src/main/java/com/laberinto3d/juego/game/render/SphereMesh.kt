package com.laberinto3d.juego.game.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generador de esfera UV de radio 1 (segmentos x anillos). Las mallas de GPU se crean en
 * [TexturedSphereMesh]; esta clase sólo produce los datos (se puede probar sin OpenGL).
 */
object SphereMesh {
    class Data(val vertices: FloatArray, val indices: ShortArray, val strideFloats: Int)

    /** Con [withUv] los vértices son px py pz nx ny nz u v; si no, px py pz nx ny nz. */
    fun generate(segments: Int, rings: Int, withUv: Boolean): Data {
        val stride = if (withUv) 8 else 6
        val vertCount = (segments + 1) * (rings + 1)
        require(vertCount <= 65535) { "demasiados vértices para índices de 16 bits" }
        val v = FloatArray(vertCount * stride)
        var k = 0
        for (i in 0..rings) {
            val phi = PI * i / rings
            val sp = sin(phi).toFloat()
            val cp = cos(phi).toFloat()
            for (j in 0..segments) {
                val theta = 2.0 * PI * j / segments
                val x = sp * cos(theta).toFloat()
                val y = cp
                val z = sp * sin(theta).toFloat()
                v[k++] = x; v[k++] = y; v[k++] = z
                v[k++] = x; v[k++] = y; v[k++] = z
                if (withUv) {
                    v[k++] = j.toFloat() / segments
                    v[k++] = i.toFloat() / rings
                }
            }
        }
        val idx = ShortArray(segments * rings * 6)
        var n = 0
        for (i in 0 until rings) {
            for (j in 0 until segments) {
                val a = i * (segments + 1) + j
                val b = a + segments + 1
                idx[n++] = a.toShort(); idx[n++] = (a + 1).toShort(); idx[n++] = b.toShort()
                idx[n++] = (a + 1).toShort(); idx[n++] = (b + 1).toShort(); idx[n++] = b.toShort()
            }
        }
        return Data(v, idx, stride)
    }
}
