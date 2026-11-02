package com.laberinto3d.juego.game.physics

import kotlin.math.abs

/** Rampa plana (para la sombra y el render). [dir]: 0 = sube hacia -Z, 1 = hacia +Z, 2 = hacia -X, 3 = hacia +X. */
class Ramp(
    @JvmField val x0: Float, @JvmField val z0: Float, @JvmField val x1: Float, @JvmField val z1: Float,
    @JvmField val dir: Int, @JvmField val yLow: Float, @JvmField val yHigh: Float
) {
    /** Altura de la superficie en (x, z) o NaN si queda fuera. */
    fun heightAt(x: Float, z: Float): Float {
        if (x < x0 || x > x1 || z < z0 || z > z1) return Float.NaN
        val t = when (dir) {
            0 -> (z1 - z) / (z1 - z0)
            1 -> (z - z0) / (z1 - z0)
            2 -> (x1 - x) / (x1 - x0)
            else -> (x - x0) / (x1 - x0)
        }
        return yLow + (yHigh - yLow) * t
    }
}

/** Plataforma móvil: oscila entre su posición base y base + (dx, dy, dz) con un periodo [period] s. */
class Mover(
    @JvmField val box: Box,
    @JvmField val dx: Float, @JvmField val dy: Float, @JvmField val dz: Float,
    @JvmField val period: Float, @JvmField val phase: Float
) {
    private val bminX = box.min.x; private val bminY = box.min.y; private val bminZ = box.min.z
    private val bmaxX = box.max.x; private val bmaxY = box.max.y; private val bmaxZ = box.max.z
    private var ox = 0f; private var oy = 0f; private var oz = 0f

    init { box.dynamic = true; update(0f, 0f) }

    /** Factor 0..1 de la posición en el instante [t] (con una parada del 25% del periodo en cada extremo). */
    fun factor(t: Float): Float {
        var u = (t / period + phase) % 1f
        if (u < 0f) u += 1f
        val tri = 1f - abs(2f * u - 1f)
        val a = ((tri - 0.25f) / 0.5f).coerceIn(0f, 1f)
        return a * a * (3f - 2f * a)
    }

    fun update(t: Float, dt: Float) {
        val s = factor(t)
        val nx = dx * s; val ny = dy * s; val nz = dz * s
        box.min.set(bminX + nx, bminY + ny, bminZ + nz)
        box.max.set(bmaxX + nx, bmaxY + ny, bmaxZ + nz)
        if (dt > 0f) {
            val nvx = (nx - ox) / dt; val nvz = (nz - oz) / dt
            box.dvx = nvx - box.vel.x; box.dvz = nvz - box.vel.z
            box.vel.set(nvx, (ny - oy) / dt, nvz)
        } else { box.vel.set(0f, 0f, 0f); box.dvx = 0f; box.dvz = 0f }
        ox = nx; oy = ny; oz = nz
    }
}

/**
 * Conjunto de colisionadores estáticos (cajas y triángulos) y dinámicos (plataformas móviles)
 * del nivel + datos de la meta. La meta no tiene colisión: sólo un punto y un radio.
 * Las plataformas móviles van las primeras de [boxes].
 */
class World(
    @JvmField val boxes: List<Box>,
    @JvmField val floorY: Float,
    @JvmField val goalPosition: Vec3,
    @JvmField val goalRadius: Float,
    /** Rectángulo exterior del tablero (incluye el perímetro), en el plano XZ. */
    @JvmField val minX: Float,
    @JvmField val maxX: Float,
    @JvmField val minZ: Float,
    @JvmField val maxZ: Float,
    /** Checkpoints en orden (y = cara superior del suelo donde están). */
    @JvmField val checkpoints: List<Vec3> = emptyList(),
    /** Altura máxima de cualquier caja (para encuadrar la cámara). */
    @JvmField val topY: Float = 0.2f,
    /** Triángulos de rampas: 9 floats por triángulo (a, b, c) y 3 por normal. */
    @JvmField val tris: FloatArray = FloatArray(0),
    @JvmField val triNormals: FloatArray = FloatArray(0),
    @JvmField val ramps: List<Ramp> = emptyList(),
    @JvmField val movers: List<Mover> = emptyList(),
    /** Superficie por triángulo (0 normal, 1 hielo, 2 arena); vacío = todo normal. */
    @JvmField val triSurf: IntArray = IntArray(0),
    /** Color rgba por triángulo para el render; vacío = color por altura (rampas). */
    @JvmField val triColors: FloatArray = FloatArray(0),
    /** Radio de la cáscara transparente de la gran esfera (0 = no hay). Su centro es el origen. */
    @JvmField val shellRadius: Float = 0f,
    /** Puntos xyz de la línea central del recorrido (para orientar la cámara). */
    @JvmField val pathPts: FloatArray = FloatArray(0),
    /** Si > 0, la bola cae al bajar esta distancia por debajo del último punto de reaparición. */
    @JvmField val fallDrop: Float = 0f,
    /** Si > 0, la bola cae al alejarse más de esta distancia (m) de la línea central del recorrido. */
    @JvmField val offPathDist: Float = 0f,
    /** Esfera que gira (Esfera II): la física corre en el cuerpo de la esfera y la gravedad se rota. */
    @JvmField val rotating: Boolean = false,
    /** Fracción de la gravedad normal que se aplica (esfera giratoria). */
    @JvmField val gravityScale: Float = 1f,
    /** Sentido de la pista (xyz) al empezar [0] y en cada checkpoint [i+1]; sirve para orientar la esfera al reaparecer. */
    @JvmField val frameT: FloatArray = FloatArray(0)
) {
    /** Rejilla de triángulos (sólo si hay muchos; con pocos se recorren todos). */
    @JvmField val triGrid: TriGrid? = if (tris.size >= 9 * 64) TriGrid(tris, 0.8f) else null
    val isSphere: Boolean get() = shellRadius > 0f

    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerZ: Float get() = (minZ + maxZ) * 0.5f
    val width: Float get() = maxX - minX
    val depth: Float get() = maxZ - minZ
    val triCount: Int get() = tris.size / 9

    private val queryBuf = IntArray(256)

    /** Altura de la cara superior del triángulo [t] en (x, z), o NaN si no está debajo o es vertical/invertido. */
    private fun triTopHeight(t: Int, x: Float, z: Float): Float {
        if (triNormals[t * 3 + 1] < 0.5f) return Float.NaN
        val o = t * 9
        val ax = tris[o]; val ay = tris[o + 1]; val az = tris[o + 2]
        val bx = tris[o + 3]; val by = tris[o + 4]; val bz = tris[o + 5]
        val cx = tris[o + 6]; val cy = tris[o + 7]; val cz = tris[o + 8]
        val d = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz)
        if (d > -1e-9f && d < 1e-9f) return Float.NaN
        val l1 = ((bz - cz) * (x - cx) + (cx - bx) * (z - cz)) / d
        val l2 = ((cz - az) * (x - cx) + (ax - cx) * (z - cz)) / d
        val l3 = 1f - l1 - l2
        if (l1 < -0.001f || l2 < -0.001f || l3 < -0.001f) return Float.NaN
        return l1 * ay + l2 * by + l3 * cy
    }

    fun updateMovers(t: Float, dt: Float) {
        for (i in movers.indices) movers[i].update(t, dt)
    }

    /**
     * Altura de la superficie que hay justo debajo de (x, z), a partir de la altura [y]
     * (sólo cuenta superficies con tope <= y + 0.05). Devuelve [NO_FLOOR] si no hay nada debajo.
     * Se usa para colocar la sombra de la bola.
     */
    fun floorBelow(x: Float, z: Float, y: Float): Float {
        var best = NO_FLOOR
        for (i in boxes.indices) {
            val b = boxes[i]
            if (!b.isFloor) continue
            if (x < b.min.x || x > b.max.x || z < b.min.z || z > b.max.z) continue
            val top = b.max.y
            if (top <= y + 0.05f && top > best) best = top
        }
        for (i in ramps.indices) {
            val h = ramps[i].heightAt(x, z)
            if (!h.isNaN() && h <= y + 0.05f && h > best) best = h
        }
        val g = triGrid
        if (g != null) {
            val n = g.query(x - 0.02f, y - 2.5f, z - 0.02f, x + 0.02f, y + 0.1f, z + 0.02f, queryBuf)
            for (k in 0 until n) {
                val t = queryBuf[k]
                val h = triTopHeight(t, x, z)
                if (!h.isNaN() && h <= y + 0.05f && h > best) best = h
            }
        }
        return best
    }

    companion object {
        const val NO_FLOOR = -1000f
    }
}
