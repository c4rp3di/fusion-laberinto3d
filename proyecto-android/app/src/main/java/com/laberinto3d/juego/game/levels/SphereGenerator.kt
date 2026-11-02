package com.laberinto3d.juego.game.levels

import com.laberinto3d.juego.game.physics.Vec3
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Generador de la "Gran Esfera": una pista de 1,2 m de ancho que recorre el interior de una esfera
 * subiendo y bajando de polo a polo mientras el radio se va cerrando hacia el centro (una espiral
 * loxodrómica con radio decreciente). La sección de la pista es siempre horizontal; sólo tiene pendiente
 * en el sentido de la marcha (unos 6-7 grados).
 *
 * El resultado es 100% determinista (semilla fija) y se construye una sola vez por ejecución.
 * Sobre la pista hay 100 checkpoints y la dificultad crece con el avance: agujeros, hielo, arena,
 * barreras y tramos sin barandilla.
 */
object SphereGenerator {
    const val NAME = "Esfera I"
    /** Nº de checkpoints (se colocan al cambiar de bloque de dificultad; lo comprueba la prueba T6). */
    const val CHECKPOINTS = 28
    private const val SEGMENTS = 80

    // ---- Geometría ----
    const val R0 = 12.5f            // radio de la línea central al empezar
    const val R_END = 2.6f          // radio de la línea central al terminar
    const val SLOPE_DEG = 6.5f      // pendiente objetivo
    const val LAT_MAX = 0.80f       // |y| máximo = LAT_MAX * radio
    const val ROW = 0.30f           // longitud de una fila de la pista
    const val LANES = 4
    const val LANE_W = 0.30f
    const val HALF_W = LANES * LANE_W / 2f
    const val RAIL_H = 0.32f
    const val RAIL_T = 0.08f

    private val RAIL_COLOR = floatArrayOf(0.45f, 0.40f, 0.86f, 1f)
    private val BARRIER_COLOR = floatArrayOf(0.86f, 0.52f, 0.30f, 1f)

    /** Datos extra para pruebas (no se usan en el juego). */
    class Debug(
        val rows: Int,
        val centers: FloatArray,       // xyz por fila
        val rightX: FloatArray, val rightZ: FloatArray,
        val safeOffset: FloatArray,    // desplazamiento lateral seguro por fila (m)
        val cpRows: IntArray,
        val pathLength: Float,
        val maxSlopeDeg: Float,
        val featureCounts: Map<String, Int>,
        val rowKind: IntArray = IntArray(0)
    )

    class Result(val level: Level, val debug: Debug)

    private class Rng(var s: Long) {
        fun next(): Float {
            s = s * 6364136223846793005L + 1442695040888963407L
            return ((s ushr 33) and 0x7fffffL).toFloat() / 0x800000.toFloat()
        }
    }

    // ---- Acumuladores de triángulos ----
    internal class Mesh {
        val tris = ArrayList<Float>(1 shl 16)
        val norms = ArrayList<Float>(1 shl 14)
        val surf = ArrayList<Int>(1 shl 14)
        val cols = ArrayList<Float>(1 shl 16)

        fun tri(a: FloatArray, b: FloatArray, c: FloatArray, wx: Float, wy: Float, wz: Float, sf: Int, col: FloatArray) {
            var bb = b; var cc = c
            val ux = bb[0] - a[0]; val uy = bb[1] - a[1]; val uz = bb[2] - a[2]
            val vx = cc[0] - a[0]; val vy = cc[1] - a[1]; val vz = cc[2] - a[2]
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            if (nx * wx + ny * wy + nz * wz < 0f) { val t = bb; bb = cc; cc = t; nx = -nx; ny = -ny; nz = -nz }
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            if (l < 1e-9f) return
            for (p in arrayOf(a, bb, cc)) { tris.add(p[0]); tris.add(p[1]); tris.add(p[2]) }
            norms.add(nx / l); norms.add(ny / l); norms.add(nz / l)
            surf.add(sf)
            cols.add(col[0]); cols.add(col[1]); cols.add(col[2]); cols.add(col[3])
        }

        fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, wx: Float, wy: Float, wz: Float, sf: Int, col: FloatArray) {
            tri(a, b, c, wx, wy, wz, sf, col)
            tri(a, c, d, wx, wy, wz, sf, col)
        }
    }

    private fun pt(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

    /** Pared (caja delgada) entre a0 y a1 a nivel de suelo, extruida [thick] hacia (nx,nz) y [h] hacia arriba. */
    private fun wall(m: Mesh, a0: FloatArray, a1: FloatArray, nx: Float, nz: Float, thick: Float, h: Float, col: FloatArray) {
        val b0 = pt(a0[0] + nx * thick, a0[1], a0[2] + nz * thick)
        val b1 = pt(a1[0] + nx * thick, a1[1], a1[2] + nz * thick)
        val a0t = pt(a0[0], a0[1] + h, a0[2]); val a1t = pt(a1[0], a1[1] + h, a1[2])
        val b0t = pt(b0[0], b0[1] + h, b0[2]); val b1t = pt(b1[0], b1[1] + h, b1[2])
        val dark = floatArrayOf(col[0] * 0.8f, col[1] * 0.8f, col[2] * 0.8f, 1f)
        m.quad(a0, a1, a1t, a0t, -nx, 0f, -nz, 0, col)       // cara en a (normal contraria a la extrusión)
        m.quad(b0, b1, b1t, b0t, nx, 0f, nz, 0, dark)        // cara exterior
        m.quad(a0t, a1t, b1t, b0t, 0f, 1f, 0f, 0, col)       // tapa
    }

    fun generate(): Result {
        val tanA = tan(Math.toRadians(SLOPE_DEG.toDouble())).toFloat()

        // ---- 1) Recorrido en (phi, y) con radio decreciente ----
        var c = 0.30f
        var phiEnd = 0f
        var fy = FloatArray(0); var fr = FloatArray(0)
        val dphi = 0.002f
        for (iter in 0 until 8) {
            val ys = ArrayList<Float>(40000)
            var y = -LAT_MAX * R0
            var d = 1f
            var target = 1f
            var passes = 0
            var phi = 0f
            var done = false
            while (!done && phi < 80f) {
                val r = R0 - c * phi
                val rho = sqrt(max(r * r - y * y, 0.01f))
                y += d * tanA * rho * dphi
                d += (target - d) * min(1f, dphi / 0.32f)
                phi += dphi
                ys.add(y)
                val rr = R0 - c * phi
                if (target > 0f && y >= LAT_MAX * rr) { target = -1f }
                else if (target < 0f && y <= -LAT_MAX * rr) { passes++; if (passes >= 1) done = true }
            }
            phiEnd = phi
            fy = ys.toFloatArray()
            val rEndNow = R0 - c * phiEnd
            val k = (R0 - R_END) / (R0 - rEndNow)
            c *= k
            if (abs(k - 1f) < 0.003f) break
        }
        val n = fy.size
        // puntos finos
        val px = FloatArray(n); val py = FloatArray(n); val pz = FloatArray(n)
        for (i in 0 until n) {
            val phi = (i + 1) * dphi
            val r = R0 - c * phi
            val y = fy[i]
            val rho = sqrt(max(r * r - y * y, 0.01f))
            px[i] = rho * cos(phi); py[i] = y; pz[i] = rho * sin(phi)
        }
        // longitud de arco y remuestreo cada ROW metros
        val cx = ArrayList<Float>(); val cy = ArrayList<Float>(); val cz = ArrayList<Float>()
        cx.add(px[0]); cy.add(py[0]); cz.add(pz[0])
        var acc = 0f
        var total = 0f
        for (i in 1 until n) {
            val dx = px[i] - px[i - 1]; val dy = py[i] - py[i - 1]; val dz = pz[i] - pz[i - 1]
            val seg = sqrt(dx * dx + dy * dy + dz * dz)
            acc += seg; total += seg
            if (acc >= ROW) {
                cx.add(px[i]); cy.add(py[i]); cz.add(pz[i])
                acc -= ROW
            }
        }
        val rows = cx.size
        val ctr = FloatArray(rows * 3)
        for (i in 0 until rows) { ctr[i * 3] = cx[i]; ctr[i * 3 + 1] = cy[i]; ctr[i * 3 + 2] = cz[i] }

        // marco local por fila: dirección horizontal y vector "derecha"
        val hx = FloatArray(rows); val hz = FloatArray(rows)
        val rx = FloatArray(rows); val rz = FloatArray(rows)
        var maxSlope = 0f
        for (i in 0 until rows) {
            val a = max(0, i - 2); val b = min(rows - 1, i + 2)
            var tx = ctr[b * 3] - ctr[a * 3]; var tz = ctr[b * 3 + 2] - ctr[a * 3 + 2]
            val ty = ctr[b * 3 + 1] - ctr[a * 3 + 1]
            val hl = sqrt(tx * tx + tz * tz)
            tx /= hl; tz /= hl
            hx[i] = tx; hz[i] = tz
            rx[i] = -tz; rz[i] = tx
            val sl = Math.toDegrees(atan(abs(ty) / hl.toDouble())).toFloat()
            if (sl > maxSlope) maxSlope = sl
        }

        // ---- 2) Obstáculos por fila y carril ----
        val hole = Array(rows) { BooleanArray(LANES) }
        val surf = Array(rows) { IntArray(LANES) }
        val rail = Array(rows) { BooleanArray(2) { true } }     // [0]=izquierda (-), [1]=derecha (+)
        val barrier = Array(rows) { IntArray(2) }                // [a,b) carriles bloqueados (a==b: ninguno)
        val safe = FloatArray(rows)
        val laneCenter = { k: Int -> -HALF_W + LANE_W * (k + 0.5f) }
        val rowKind = IntArray(rows)
        val counts = HashMap<String, Int>()
        fun count(k: String) { counts[k] = (counts[k] ?: 0) + 1 }

        // el recorrido se divide en SEGMENTS tramos; los obstáculos se agrupan en bloques temáticos y
        // los checkpoints se colocan al empezar cada bloque nuevo (o si pasan demasiados tramos sin uno)
        val seg = IntArray(SEGMENTS + 1) { i -> 4 + ((rows - 16) * i / SEGMENTS) }
        val segKind = IntArray(SEGMENTS)
        val rng = Rng(20260101L)
        var prevKind = 0
        for (k in 0 until SEGMENTS) {
            val a = seg[k] + 4
            val b = seg[k + 1]               // exclusive
            val len = b - a
            if (len < 4) continue
            val d = k / (SEGMENTS - 1f)
            if (k < 5) { count("libre"); continue }
            // pesos por tipo según dificultad
            val w = floatArrayOf(
                max(0.6f, 2.6f - 2.2f * d),                       // 0 libre
                0.9f,                                             // 1 hielo
                0.9f,                                             // 2 arena
                if (d > 0.08f) 1.1f else 0f,                      // 3 chicane de agujeros
                if (d > 0.20f) 1.0f else 0f,                      // 4 barreras
                if (d > 0.35f) 0.5f + 1.2f * d else 0f,           // 5 puente sin barandilla
                if (d > 0.55f) 1.2f * d else 0f,                  // 6 chicane sobre hielo
                if (d > 0.70f) 1.0f * d else 0f                   // 7 puente sobre arena/hielo estrecho
            )
            var sum = 0f; for (x in w) sum += x
            var pick = rng.next() * sum
            var kind = 0
            for (i in w.indices) { if (pick < w[i]) { kind = i; break }; pick -= w[i]; kind = i }
            // bloques: con cierta probabilidad se repite el tipo anterior
            if (prevKind != 0 && w[prevKind] > 0f && rng.next() < 0.40f) kind = prevKind
            prevKind = kind
            segKind[k] = kind
            for (rr in a until b) rowKind[rr] = kind
            val flip = rng.next() < 0.5f
            fun holesL(r: Int) { if (flip) { hole[r][2] = true; hole[r][3] = true } else { hole[r][0] = true; hole[r][1] = true } }
            fun holesR(r: Int) { if (flip) { hole[r][0] = true; hole[r][1] = true } else { hole[r][2] = true; hole[r][3] = true } }
            fun safeL(r: Int) { safe[r] = if (flip) -0.3f else 0.3f }
            fun safeR(r: Int) { safe[r] = if (flip) 0.3f else -0.3f }
            when (kind) {
                0 -> count("libre")
                1 -> { for (r in a until b) for (l in 0 until LANES) surf[r][l] = 1; count("hielo") }
                2 -> { for (r in a until b) for (l in 0 until LANES) surf[r][l] = 2; count("arena") }
                3 -> {
                    holesL(a + 1); holesL(a + 2); safeL(a); safeL(a + 1); safeL(a + 2)
                    if (a + 5 < b) { holesR(a + 4); holesR(a + 5); safeR(a + 3); safeR(a + 4); safeR(a + 5) }
                    count("chicane")
                }
                4 -> {
                    barrier[a + 1] = if (flip) intArrayOf(2, 4) else intArrayOf(0, 2)
                    safeL(a + 1)
                    count("barreras")
                }
                5 -> {
                    for (r in a until b) { hole[r][if (flip) 0 else 3] = true; rail[r][if (flip) 0 else 1] = false
                        safe[r] = if (flip) 0.15f else -0.15f }
                    count("puente")
                }
                6 -> {
                    for (r in a until b) for (l in 0 until LANES) surf[r][l] = 1
                    holesL(a + 1); holesL(a + 2); safeL(a); safeL(a + 1); safeL(a + 2)
                    if (a + 5 < b) { holesR(a + 4); holesR(a + 5); safeR(a + 3); safeR(a + 4); safeR(a + 5) }
                    count("chicane-hielo")
                }
                else -> {
                    for (r in a until b) {
                        hole[r][0] = true; hole[r][3] = true; rail[r][0] = false; rail[r][1] = false
                        surf[r][1] = if (flip) 1 else 2; surf[r][2] = if (flip) 1 else 2
                    }
                    count("pasarela")
                }
            }
        }
        // checkpoints: inicio, cambios de bloque (separación mínima 2 tramos) y como mucho 4 tramos sin ninguno
        val cpList = ArrayList<Int>()
        cpList.add(0)
        var lastCp = 0
        for (k in 1 until SEGMENTS - 1) {
            val changed = segKind[k] != segKind[k - 1]
            if ((changed && k - lastCp >= 2) || k - lastCp >= 4) { cpList.add(k); lastCp = k }
        }
        val cpRows = IntArray(cpList.size) { seg[cpList[it]] }
        // un padding final seguro
        for (r in rows - 12 until rows) { for (l in 0 until LANES) { hole[r][l] = false; surf[r][l] = 0 }; barrier[r] = intArrayOf(0, 0); safe[r] = 0f }

        // ---- 3) Geometría ----
        val m = Mesh()
        fun lane(i: Int, off: Float): FloatArray = pt(ctr[i * 3] + rx[i] * off, ctr[i * 3 + 1], ctr[i * 3 + 2] + rz[i] * off)
        for (i in 0 until rows - 1) {
            // brillo del suelo según el avance
            val prog = i / (rows - 1f)
            val lum = 0.05f * (1f + 2.5f * prog)
            for (l in 0 until LANES) {
                if (hole[i][l]) continue
                val sf = surf[i][l]
                val col = when (sf) {
                    1 -> floatArrayOf(0.30f + lum, 0.50f + lum, 0.64f + lum, 0.5f)
                    2 -> floatArrayOf(0.50f + lum, 0.42f + lum, 0.26f + lum, 0.5f)
                    else -> floatArrayOf(0.17f + lum + (if ((i / 3 + l) % 2 == 0) 0.015f else 0f), 0.17f + lum, 0.28f + lum * 1.2f, 0.5f)
                }
                val o0 = -HALF_W + LANE_W * l
                val o1 = o0 + LANE_W
                m.quad(lane(i, o0), lane(i, o1), lane(i + 1, o1), lane(i + 1, o0), 0f, 1f, 0f, sf, col)
            }
            for (s in 0..1) {
                if (!rail[i][s]) continue
                val sg = if (s == 0) -1f else 1f
                val a0 = lane(i, sg * HALF_W); val a1 = lane(i + 1, sg * HALF_W)
                val nx = sg * (rx[i] + rx[i + 1]) * 0.5f; val nz = sg * (rz[i] + rz[i + 1]) * 0.5f
                val nl = sqrt(nx * nx + nz * nz)
                wall(m, a0, a1, nx / nl, nz / nl, RAIL_T, RAIL_H, RAIL_COLOR)
            }
            val bar = barrier[i]
            if (bar[1] > bar[0]) {
                val o0 = -HALF_W + LANE_W * bar[0]; val o1 = -HALF_W + LANE_W * bar[1]
                wall(m, lane(i, o0), lane(i, o1), hx[i], hz[i], RAIL_T * 1.5f, 0.26f, BARRIER_COLOR)
            }
        }
        // tapas de inicio y final (cierran la pista)
        wall(m, lane(0, -HALF_W), lane(0, HALF_W), -hx[0], -hz[0], RAIL_T, 0.6f, RAIL_COLOR)
        wall(m, lane(rows - 1, -HALF_W), lane(rows - 1, HALF_W), hx[rows - 1], hz[rows - 1], RAIL_T, 0.6f, RAIL_COLOR)

        val tris = m.tris.toFloatArray()
        val norms = m.norms.toFloatArray()
        val surfArr = m.surf.toIntArray()
        val colArr = m.cols.toFloatArray()

        // ---- 4) Objetos ----
        fun floorPos(i: Int) = Vec3(ctr[i * 3], ctr[i * 3 + 1], ctr[i * 3 + 2])
        val startRow = 3
        val sp = floorPos(startRow)
        val start = Vec3(sp.x, sp.y + 0.15f, sp.z)
        val goalRow = rows - 6
        val goal = floorPos(goalRow)
        val cps = ArrayList<Vec3>()
        for (k in cpRows.indices) cps.add(floorPos(cpRows[k]))
        val bound = R0 + 1.5f
        var topY = -1000f
        run { var i = 1; while (i < tris.size) { if (tris[i] > topY) topY = tris[i]; i += 3 } }

        val level = Level(
            id = 12, name = NAME,
            boxes = emptyList(),
            start = start, goal = goal, goalRadius = 0.42f,
            bestTimeKey = Levels.bestTimeKey(Levels.SPHERE_ID),
            minX = -bound, maxX = bound, minZ = -bound, maxZ = bound,
            checkpoints = cps, topY = topY,
            tris = tris, triNormals = norms,
            triSurf = surfArr, triColors = colArr,
            shellRadius = R0 + 0.9f, pathPts = ctr, fallDrop = 6f, offPathDist = 1.4f
        )
        return Result(level, Debug(rows, ctr, rx, rz, safe, cpRows, total, maxSlope, counts, rowKind))
    }
}
