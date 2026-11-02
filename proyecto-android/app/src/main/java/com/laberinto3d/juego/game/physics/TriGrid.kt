package com.laberinto3d.juego.game.physics

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Rejilla espacial uniforme de triángulos (formato CSR: lista de índices por celda).
 * Sirve para consultar sólo los triángulos cercanos a la bola en niveles con miles de ellos
 * (la gran esfera). Un triángulo aparece en todas las celdas que toca su caja envolvente.
 */
class TriGrid(tris: FloatArray, private val cell: Float) {
    private val triCount = tris.size / 9
    private val minX: Float
    private val minY: Float
    private val minZ: Float
    private val nx: Int
    private val ny: Int
    private val nz: Int
    private val start: IntArray
    private val items: IntArray
    private val stamp = IntArray(triCount)
    private var cur = 0
    private var itemsTmp: IntArray? = null

    init {
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var z0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        var i = 0
        while (i < tris.size) {
            x0 = min(x0, tris[i]); x1 = max(x1, tris[i])
            y0 = min(y0, tris[i + 1]); y1 = max(y1, tris[i + 1])
            z0 = min(z0, tris[i + 2]); z1 = max(z1, tris[i + 2])
            i += 3
        }
        if (triCount == 0) { x0 = 0f; y0 = 0f; z0 = 0f; x1 = 1f; y1 = 1f; z1 = 1f }
        minX = x0 - MARGIN; minY = y0 - MARGIN; minZ = z0 - MARGIN
        nx = max(1, floor((x1 + MARGIN - minX) / cell).toInt() + 1)
        ny = max(1, floor((y1 + MARGIN - minY) / cell).toInt() + 1)
        nz = max(1, floor((z1 + MARGIN - minZ) / cell).toInt() + 1)
        val total = nx * ny * nz
        val counts = IntArray(total + 1)
        // pasada 1: contar; pasada 2: rellenar
        for (pass in 0..1) {
            if (pass == 1) {
                var acc = 0
                for (c in 0 until total) { val k = counts[c]; counts[c] = acc; acc += k }
                counts[total] = acc
            }
            val fill = if (pass == 1) counts.copyOf() else counts
            for (t in 0 until triCount) {
                val o = t * 9
                var a0 = Float.MAX_VALUE; var b0 = Float.MAX_VALUE; var c0 = Float.MAX_VALUE
                var a1 = -Float.MAX_VALUE; var b1 = -Float.MAX_VALUE; var c1 = -Float.MAX_VALUE
                for (v in 0 until 3) {
                    val x = tris[o + v * 3]; val y = tris[o + v * 3 + 1]; val z = tris[o + v * 3 + 2]
                    a0 = min(a0, x); a1 = max(a1, x); b0 = min(b0, y); b1 = max(b1, y); c0 = min(c0, z); c1 = max(c1, z)
                }
                val ix0 = ci(a0 - MARGIN, minX, nx); val ix1 = ci(a1 + MARGIN, minX, nx)
                val iy0 = ci(b0 - MARGIN, minY, ny); val iy1 = ci(b1 + MARGIN, minY, ny)
                val iz0 = ci(c0 - MARGIN, minZ, nz); val iz1 = ci(c1 + MARGIN, minZ, nz)
                for (ix in ix0..ix1) for (iy in iy0..iy1) for (iz in iz0..iz1) {
                    val c = (ix * ny + iy) * nz + iz
                    if (pass == 0) counts[c]++ else { itemsTmp!![fill[c]++] = t }
                }
            }
            if (pass == 0) {
                var s = 0
                for (c in 0 until total) s += counts[c]
                itemsTmp = IntArray(s)
            }
        }
        start = counts
        items = itemsTmp!!
        itemsTmp = null
    }


    private fun ci(v: Float, mn: Float, n: Int): Int {
        val i = floor((v - mn) / cell).toInt()
        return if (i < 0) 0 else if (i >= n) n - 1 else i
    }

    /**
     * Rellena [out] con los índices (sin repetir) de los triángulos cuyas celdas se solapan con la caja
     * dada. Devuelve cuántos (se trunca a out.size).
     */
    fun query(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, out: IntArray): Int {
        if (++cur == Int.MAX_VALUE) { java.util.Arrays.fill(stamp, 0); cur = 1 }
        var n = 0
        val ix0 = ci(x0, minX, nx); val ix1 = ci(x1, minX, nx)
        val iy0 = ci(y0, minY, ny); val iy1 = ci(y1, minY, ny)
        val iz0 = ci(z0, minZ, nz); val iz1 = ci(z1, minZ, nz)
        for (ix in ix0..ix1) for (iy in iy0..iy1) for (iz in iz0..iz1) {
            val c = (ix * ny + iy) * nz + iz
            var k = start[c]
            val e = start[c + 1]
            while (k < e) {
                val t = items[k++]
                if (stamp[t] != cur) {
                    stamp[t] = cur
                    if (n < out.size) out[n++] = t
                }
            }
        }
        return n
    }

    companion object {
        private const val MARGIN = 0.05f
    }
}
