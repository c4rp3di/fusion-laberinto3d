package com.laberinto3d.juego.game.levels

import com.laberinto3d.juego.game.physics.Vec3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generador de la "Esfera II": prototipo de esfera tipo Perplexus. Una pista de 1 m de ancho recorre la
 * superficie interior de una esfera (de un polo al otro, en espiral) con la cara de rodadura mirando al
 * centro. El jugador no inclina un tablero: hace GIRAR la esfera con el dedo y la bola, que siempre está en
 * la parte baja, rueda por la pista. Lleva unos diez obstáculos (barreras, agujeros y tres saltos) y
 * checkpoints entre ellos. Determinista y rápido (< 100 ms).
 */
object SphereTwoGenerator {
    const val NAME = "Esfera II"
    const val R = 3.0f              // radio del suelo de la pista
    const val SHELL = R + 0.45f     // cáscara transparente
    const val TURNS = 3.5f          // vueltas de la espiral
    const val LAT = 1.08f           // latitud máxima (rad)
    const val ROW = 0.20f
    const val LANES = 4
    const val LANE_W = 0.25f
    const val HALF_W = LANES * LANE_W / 2f
    const val RAIL_H = 0.30f
    const val RAIL_T = 0.08f
    const val GRAVITY_SCALE = 0.6f

    private val RAIL_COLOR = floatArrayOf(0.45f, 0.40f, 0.86f, 1f)
    private val BARRIER_COLOR = floatArrayOf(0.92f, 0.55f, 0.28f, 1f)
    private val KICK_COLOR = floatArrayOf(0.95f, 0.78f, 0.30f, 1f)

    class Debug(
        val rows: Int,
        val ctr: FloatArray, val tan: FloatArray, val up: FloatArray, val right: FloatArray,
        val cpRows: IntArray,
        val pathLength: Float,
        val counts: Map<String, Int>,
        /** filas de inicio de cada salto (vértice del borde del trampolín) */
        val jumpLip: IntArray,
        val hole: Array<BooleanArray>,
        val barrier: Array<IntArray>
    )

    class Result(val level: Level, val debug: Debug)

    private fun pt(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

    fun generate(): Result {
        // ---- 1) línea central: espiral entre polos sobre la esfera de radio R, remuestreada cada ROW ----
        val fine = 6000
        val fx = FloatArray(fine + 1); val fy = FloatArray(fine + 1); val fz = FloatArray(fine + 1)
        for (i in 0..fine) {
            val t = i / fine.toFloat()
            val th = -LAT + 2f * LAT * t
            val la = 2f * Math.PI.toFloat() * TURNS * t
            fx[i] = R * cos(th) * cos(la); fy[i] = R * sin(th); fz[i] = R * cos(th) * sin(la)
        }
        val cx = ArrayList<Float>(); val cy = ArrayList<Float>(); val cz = ArrayList<Float>()
        cx.add(fx[0]); cy.add(fy[0]); cz.add(fz[0])
        var acc = 0f
        var total = 0f
        for (i in 1..fine) {
            val dx = fx[i] - fx[i - 1]; val dy = fy[i] - fy[i - 1]; val dz = fz[i] - fz[i - 1]
            val sg = sqrt(dx * dx + dy * dy + dz * dz)
            acc += sg; total += sg
            if (acc >= ROW) { cx.add(fx[i]); cy.add(fy[i]); cz.add(fz[i]); acc -= ROW }
        }
        val rowsAll = cx.size
        val ctr = FloatArray(rowsAll * 3)
        for (i in 0 until rowsAll) {
            // proyectar de nuevo a la esfera (las cuerdas quedan dentro)
            val x = cx[i]; val y = cy[i]; val z = cz[i]
            val l = sqrt(x * x + y * y + z * z)
            ctr[i * 3] = x / l * R; ctr[i * 3 + 1] = y / l * R; ctr[i * 3 + 2] = z / l * R
        }
        // marco por fila: T (sentido), U (hacia el centro), Rt = T x U
        val tan = FloatArray(rowsAll * 3); val up = FloatArray(rowsAll * 3); val rt = FloatArray(rowsAll * 3)
        for (i in 0 until rowsAll) {
            val a = max(0, i - 2); val b = min(rowsAll - 1, i + 2)
            var dx = ctr[b * 3] - ctr[a * 3]; var dy = ctr[b * 3 + 1] - ctr[a * 3 + 1]; var dz = ctr[b * 3 + 2] - ctr[a * 3 + 2]
            val ux = -ctr[i * 3] / R; val uy = -ctr[i * 3 + 1] / R; val uz = -ctr[i * 3 + 2] / R
            val dd = dx * ux + dy * uy + dz * uz
            dx -= ux * dd; dy -= uy * dd; dz -= uz * dd
            val l = sqrt(dx * dx + dy * dy + dz * dz)
            dx /= l; dy /= l; dz /= l
            tan[i * 3] = dx; tan[i * 3 + 1] = dy; tan[i * 3 + 2] = dz
            up[i * 3] = ux; up[i * 3 + 1] = uy; up[i * 3 + 2] = uz
            rt[i * 3] = dy * uz - dz * uy; rt[i * 3 + 1] = dz * ux - dx * uz; rt[i * 3 + 2] = dx * uy - dy * ux
        }

        // ---- 2) obstáculos ----
        val hole = Array(rowsAll) { BooleanArray(LANES) }
        val barrier = Array(rowsAll) { IntArray(2) }
        val hv = FloatArray(rowsAll)                  // altura del vértice de la fila (trampolines)
        val gap = BooleanArray(rowsAll)               // sin suelo entre la fila i y la i+1
        val railH = FloatArray(rowsAll) { RAIL_H }
        val counts = HashMap<String, Int>()
        fun count(k: String) { counts[k] = (counts[k] ?: 0) + 1 }
        val cpList = ArrayList<Int>()
        val lips = ArrayList<Int>()
        cpList.add(0)

        var r = 12
        // tipos: 0 barrera, 1 agujeros, 2 eslalon, 3 salto, 4 agujeros+barrera
        val plan = intArrayOf(0, 1, 2, 3, 1, 0, 3, 4, 2, 3)
        var side = false
        for (k in plan.indices) {
            val a = r
            side = !side
            val lo = if (side) 0 else 2          // carriles [lo, lo+2)
            val hi = 2 - lo
            val need = when (plan[k]) { 0 -> 6; 1 -> 8; 2 -> 9; 3 -> 14; else -> 10 }
            if (a + need + 14 >= rowsAll) break
            // checkpoint antes de cada obstáculo importante (en tramo libre)
            if (k % 2 == 1 || plan[k] == 3) cpList.add(a - 3)
            when (plan[k]) {
                0 -> { barrier[a + 1] = intArrayOf(lo, lo + 2); count("barrera") }
                1 -> {
                    hole[a + 1][lo] = true; hole[a + 1][lo + 1] = true; hole[a + 2][lo] = true; hole[a + 2][lo + 1] = true
                    hole[a + 5][hi] = true; hole[a + 5][hi + 1] = true; hole[a + 6][hi] = true; hole[a + 6][hi + 1] = true
                    count("agujeros")
                }
                2 -> {
                    barrier[a + 1] = intArrayOf(lo, lo + 2)
                    barrier[a + 6] = intArrayOf(hi, hi + 2)
                    count("eslalon")
                }
                3 -> {
                    // carrera de impulso (a+0..a+5), trampolín 3 filas hasta el borde, hueco de 2 filas
                    val k0 = a + 5
                    for (q in 0..3) hv[k0 + q] = 0.2f * q / 3f
                    val kt = k0 + 3
                    gap[kt] = true; gap[kt + 1] = true
                    for (q in (k0 - 1)..(kt + 4)) railH[q] = 0.55f
                    lips.add(kt)
                    count("salto")
                }
                else -> {
                    hole[a + 1][lo] = true; hole[a + 1][lo + 1] = true; hole[a + 2][lo] = true; hole[a + 2][lo + 1] = true
                    barrier[a + 6] = intArrayOf(hi, hi + 2)
                    count("agujeros+barrera")
                }
            }
            r = a + need + 8
        }
        val usedEnd = r
        // pista libre al final y meta
        val goalRow = min(rowsAll - 6, usedEnd + 6)
        val rows = min(rowsAll, goalRow + 8)
        val cpRows = cpList.distinct().toIntArray()

        // ---- 3) geometría ----
        val m = SphereGenerator.Mesh()
        fun lane(i: Int, off: Float, h: Float): FloatArray = pt(
            ctr[i * 3] + rt[i * 3] * off + up[i * 3] * h,
            ctr[i * 3 + 1] + rt[i * 3 + 1] * off + up[i * 3 + 1] * h,
            ctr[i * 3 + 2] + rt[i * 3 + 2] * off + up[i * 3 + 2] * h
        )
        fun wall3(a0: FloatArray, a1: FloatArray, nx: Float, ny: Float, nz: Float,
                  ux: Float, uy: Float, uz: Float, thick: Float, h: Float, col: FloatArray) {
            val a0t = pt(a0[0] + ux * h, a0[1] + uy * h, a0[2] + uz * h)
            val a1t = pt(a1[0] + ux * h, a1[1] + uy * h, a1[2] + uz * h)
            val b0 = pt(a0[0] + nx * thick, a0[1] + ny * thick, a0[2] + nz * thick)
            val b1 = pt(a1[0] + nx * thick, a1[1] + ny * thick, a1[2] + nz * thick)
            val b0t = pt(b0[0] + ux * h, b0[1] + uy * h, b0[2] + uz * h)
            val b1t = pt(b1[0] + ux * h, b1[1] + uy * h, b1[2] + uz * h)
            val dark = floatArrayOf(col[0] * 0.8f, col[1] * 0.8f, col[2] * 0.8f, 1f)
            m.quad(a0, a1, a1t, a0t, -nx, -ny, -nz, 0, col)
            m.quad(b0, b1, b1t, b0t, nx, ny, nz, 0, dark)
            m.quad(a0t, a1t, b1t, b0t, ux, uy, uz, 0, col)
        }
        for (i in 0 until rows - 1) {
            val prog = i / (rows - 1f)
            val lum = 0.05f * (1f + 1.5f * prog)
            if (!gap[i]) {
                for (l in 0 until LANES) {
                    if (hole[i][l]) continue
                    val kick = hv[i + 1] > hv[i] + 1e-4f
                    val col = if (kick) KICK_COLOR
                    else floatArrayOf(0.22f + lum + (if ((i / 3 + l) % 2 == 0) 0.02f else 0f), 0.24f + lum, 0.40f + lum, 1f)
                    val o0 = -HALF_W + LANE_W * l
                    val o1 = o0 + LANE_W
                    m.quad(lane(i, o0, hv[i]), lane(i, o1, hv[i]), lane(i + 1, o1, hv[i + 1]), lane(i + 1, o0, hv[i + 1]),
                        (up[i * 3] + up[i * 3 + 3]), (up[i * 3 + 1] + up[i * 3 + 4]), (up[i * 3 + 2] + up[i * 3 + 5]), 0, col)
                }
            }
            for (s in 0..1) {
                val sg = if (s == 0) -1f else 1f
                val a0 = lane(i, sg * HALF_W, 0f); val a1 = lane(i + 1, sg * HALF_W, 0f)
                var nx = sg * (rt[i * 3] + rt[i * 3 + 3]); var ny = sg * (rt[i * 3 + 1] + rt[i * 3 + 4]); var nz = sg * (rt[i * 3 + 2] + rt[i * 3 + 5])
                val nl = sqrt(nx * nx + ny * ny + nz * nz); nx /= nl; ny /= nl; nz /= nl
                val ux = up[i * 3]; val uy = up[i * 3 + 1]; val uz = up[i * 3 + 2]
                wall3(a0, a1, nx, ny, nz, ux, uy, uz, RAIL_T, railH[i], RAIL_COLOR)
            }
            val bar = barrier[i]
            if (bar[1] > bar[0]) {
                val o0 = -HALF_W + LANE_W * bar[0]; val o1 = -HALF_W + LANE_W * bar[1]
                wall3(lane(i, o0, 0f), lane(i, o1, 0f), tan[i * 3], tan[i * 3 + 1], tan[i * 3 + 2],
                    up[i * 3], up[i * 3 + 1], up[i * 3 + 2], RAIL_T * 1.5f, 0.24f, BARRIER_COLOR)
            }
        }
        // cara vertical del borde de cada trampolín (sólo estética)
        for (kt in lips) {
            val a = lane(kt, -HALF_W, 0f); val b = lane(kt, HALF_W, 0f)
            val at = lane(kt, -HALF_W, hv[kt]); val bt = lane(kt, HALF_W, hv[kt])
            m.quad(a, b, bt, at, tan[kt * 3], tan[kt * 3 + 1], tan[kt * 3 + 2], 0, KICK_COLOR)
        }
        // tapas de inicio y final
        wall3(lane(0, -HALF_W, 0f), lane(0, HALF_W, 0f), -tan[0], -tan[1], -tan[2], up[0], up[1], up[2], RAIL_T, 0.6f, RAIL_COLOR)
        val le = rows - 1
        wall3(lane(le, -HALF_W, 0f), lane(le, HALF_W, 0f), tan[le * 3], tan[le * 3 + 1], tan[le * 3 + 2], up[le * 3], up[le * 3 + 1], up[le * 3 + 2], RAIL_T, 0.6f, RAIL_COLOR)

        val tris = m.tris.toFloatArray()
        val norms = m.norms.toFloatArray()
        val surf = m.surf.toIntArray()
        val cols = m.cols.toFloatArray()

        // ---- 4) objetos ----
        fun floorPos(i: Int) = Vec3(ctr[i * 3], ctr[i * 3 + 1], ctr[i * 3 + 2])
        val startRow = 2
        val sp = floorPos(startRow)
        val ballR = 0.15f
        val start = Vec3(sp.x + up[startRow * 3] * (ballR + 0.01f), sp.y + up[startRow * 3 + 1] * (ballR + 0.01f), sp.z + up[startRow * 3 + 2] * (ballR + 0.01f))
        val cpAll = ArrayList<Vec3>()
        // el checkpoint 0 coincide con el inicio: no se usa; los demás sí
        val cpPos = cpRows.filter { it > 4 }
        for (c in cpPos) cpAll.add(floorPos(c))
        val frameT = FloatArray((cpAll.size + 1) * 3)
        frameT[0] = tan[startRow * 3]; frameT[1] = tan[startRow * 3 + 1]; frameT[2] = tan[startRow * 3 + 2]
        for (k in cpPos.indices) {
            val c = cpPos[k]
            frameT[(k + 1) * 3] = tan[c * 3]; frameT[(k + 1) * 3 + 1] = tan[c * 3 + 1]; frameT[(k + 1) * 3 + 2] = tan[c * 3 + 2]
        }
        val goal = floorPos(goalRow)
        val bound = SHELL + 1.5f

        val level = Level(
            id = Levels.SPHERE2_ID, name = NAME,
            boxes = emptyList(),
            start = start, goal = goal, goalRadius = 0.55f,
            bestTimeKey = Levels.bestTimeKey(Levels.SPHERE2_ID),
            minX = -bound, maxX = bound, minZ = -bound, maxZ = bound,
            checkpoints = cpAll, topY = R,
            tris = tris, triNormals = norms,
            triSurf = surf, triColors = cols,
            shellRadius = SHELL, pathPts = ctr.copyOf(rows * 3), fallDrop = 0f, offPathDist = 0.8f,
            rotating = true, gravityScale = GRAVITY_SCALE, frameT = frameT
        )
        return Result(level, Debug(rows, ctr, tan, up, rt, cpPos.toIntArray(), total, counts, lips.toIntArray(), hole, barrier))
    }
}
