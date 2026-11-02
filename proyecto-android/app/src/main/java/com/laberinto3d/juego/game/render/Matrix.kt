package com.laberinto3d.juego.game.render

import kotlin.math.sqrt
import kotlin.math.tan

/** Matemática 4x4 column-major (convención OpenGL) sobre FloatArray(16). Sin dependencias de Android. */
object Matrix {
    fun identity(m: FloatArray) {
        for (i in 0 until 16) m[i] = 0f
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
    }

    /** out = a * b. [out] puede ser a o b. */
    fun multiply(out: FloatArray, a: FloatArray, b: FloatArray) {
        val r = FloatArray(16)
        for (c in 0 until 4) {
            for (row in 0 until 4) {
                var s = 0f
                for (k in 0 until 4) s += a[k * 4 + row] * b[c * 4 + k]
                r[c * 4 + row] = s
            }
        }
        System.arraycopy(r, 0, out, 0, 16)
    }

    /** Perspectiva simétrica (fovy en grados). */
    fun perspective(m: FloatArray, fovyDeg: Float, aspect: Float, near: Float, far: Float) {
        val f = 1f / tan(Math.toRadians(fovyDeg.toDouble()) * 0.5).toFloat()
        for (i in 0 until 16) m[i] = 0f
        m[0] = f / aspect
        m[5] = f
        m[10] = (far + near) / (near - far)
        m[11] = -1f
        m[14] = 2f * far * near / (near - far)
    }

    fun lookAt(
        m: FloatArray,
        ex: Float, ey: Float, ez: Float,
        cx: Float, cy: Float, cz: Float,
        ux: Float, uy: Float, uz: Float
    ) {
        var fx = cx - ex; var fy = cy - ey; var fz = cz - ez
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl; fy /= fl; fz /= fl
        // s = f x up
        var sx = fy * uz - fz * uy
        var sy = fz * ux - fx * uz
        var sz = fx * uy - fy * ux
        val sl = sqrt(sx * sx + sy * sy + sz * sz)
        sx /= sl; sy /= sl; sz /= sl
        // u = s x f
        val tx = sy * fz - sz * fy
        val ty = sz * fx - sx * fz
        val tz = sx * fy - sy * fx
        m[0] = sx; m[1] = tx; m[2] = -fx; m[3] = 0f
        m[4] = sy; m[5] = ty; m[6] = -fy; m[7] = 0f
        m[8] = sz; m[9] = tz; m[10] = -fz; m[11] = 0f
        m[12] = -(sx * ex + sy * ey + sz * ez)
        m[13] = -(tx * ex + ty * ey + tz * ez)
        m[14] = (fx * ex + fy * ey + fz * ez)
        m[15] = 1f
    }

    /** m = T(tx,ty,tz) * S(sx,sy,sz). */
    fun setTranslateScale(m: FloatArray, tx: Float, ty: Float, tz: Float, sx: Float, sy: Float, sz: Float) {
        for (i in 0 until 16) m[i] = 0f
        m[0] = sx; m[5] = sy; m[10] = sz; m[15] = 1f
        m[12] = tx; m[13] = ty; m[14] = tz
    }

    /** m = T(tx,ty,tz) * R * S(s), con R = matriz de rotación 4x4 [rot]. */
    fun setTrs(m: FloatArray, tx: Float, ty: Float, tz: Float, rot: FloatArray, s: Float) {
        for (c in 0 until 3) {
            for (r in 0 until 3) m[c * 4 + r] = rot[c * 4 + r] * s
            m[c * 4 + 3] = 0f
        }
        m[12] = tx; m[13] = ty; m[14] = tz; m[15] = 1f
    }

    /** Traslación en NDC aplicada después de la proyección: out = T(dx,dy,0) * p. */
    fun shiftNdc(p: FloatArray, dx: Float, dy: Float) {
        val t = FloatArray(16)
        identity(t)
        t[12] = dx; t[13] = dy
        multiply(p, t, p)
    }

    /** Proyecta un punto con la matriz [vp]; devuelve (ndcX, ndcY, w) en [out]. */
    fun project(vp: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        val cx = vp[0] * x + vp[4] * y + vp[8] * z + vp[12]
        val cy = vp[1] * x + vp[5] * y + vp[9] * z + vp[13]
        val cw = vp[3] * x + vp[7] * y + vp[11] * z + vp[15]
        out[0] = cx / cw
        out[1] = cy / cw
        out[2] = cw
    }
}
