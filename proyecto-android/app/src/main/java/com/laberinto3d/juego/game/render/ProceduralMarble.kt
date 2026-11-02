package com.laberinto3d.juego.game.render

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.diag.GameLog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Textura de mármol generada en CPU: ruido de valor 2D a dos escalas (4 y 16) con pesos
 * 0.7/0.3, blanco hueso con vetas grises. El ruido es periódico, así que se enrolla
 * sin costura alrededor de la bola (wrap REPEAT).
 */
object ProceduralMarble {
    private const val BASE = 0xF2EDE4
    private const val VEIN = 0x6E6E6E

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    /** Ruido de valor periódico con [period] celdas en cada eje. u, v en [0,1). */
    private fun noise(u: Float, v: Float, period: Int, seed: Int): Float {
        val x = u * period
        val y = v * period
        val x0 = x.toInt()
        val y0 = y.toInt()
        val fx = smooth(x - x0)
        val fy = smooth(y - y0)
        val xa = x0 % period
        val xb = (x0 + 1) % period
        val ya = y0 % period
        val yb = (y0 + 1) % period
        val a = hash(xa, ya, seed)
        val b = hash(xb, ya, seed)
        val c = hash(xa, yb, seed)
        val d = hash(xb, yb, seed)
        val top = a + (b - a) * fx
        val bot = c + (d - c) * fx
        return top + (bot - top) * fy
    }

    /** Píxeles ARGB de la textura (size x size). Sin dependencias de Android. */
    fun pixels(size: Int): IntArray {
        val out = IntArray(size * size)
        val br = (BASE shr 16) and 0xFF; val bg = (BASE shr 8) and 0xFF; val bb = BASE and 0xFF
        val vr = (VEIN shr 16) and 0xFF; val vg = (VEIN shr 8) and 0xFF; val vb = VEIN and 0xFF
        for (py in 0 until size) {
            val v = py.toFloat() / size
            for (px in 0 until size) {
                val u = px.toFloat() / size
                val n = 0.7f * noise(u, v, 4, 11) + 0.3f * noise(u, v, 16, 23)
                // veta principal y veta fina
                val t1 = 3f * u + 2.4f * n
                val vein1 = (1f - abs(sin(PI * t1).toFloat())).pow(7f)
                val t2 = 5f * u + 1.3f * v * 2f + 3.1f * n
                val vein2 = (1f - abs(sin(PI * t2).toFloat())).pow(12f) * 0.55f
                val vein = (vein1 + vein2).coerceIn(0f, 1f)
                // manchas suaves para que se note la rotación
                val blot = (noise(u, v, 4, 5) - 0.5f) * 0.16f
                val k = (vein * 0.88f).coerceIn(0f, 1f)
                var r = br + (vr - br) * k + blot * 255f
                var g = bg + (vg - bg) * k + blot * 255f
                var b = bb + (vb - bb) * k + blot * 255f
                r = r.coerceIn(0f, 255f); g = g.coerceIn(0f, 255f); b = b.coerceIn(0f, 255f)
                out[py * size + px] = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
            }
        }
        return out
    }

    /** Genera la textura, la sube a GL con mipmaps y devuelve su id. */
    fun generate(): Int {
        val size = Config.MARBLE_SIZE
        val t0 = System.nanoTime()
        val px = pixels(size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        bmp.recycle()
        GlUtil.check("ProceduralMarble")
        GameLog.i("Marble", "Textura ${size}x$size generada en ${(System.nanoTime() - t0) / 1_000_000}ms (id=${ids[0]})")
        return ids[0]
    }
}
