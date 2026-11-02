package com.laberinto3d.juego.game.levels

import com.laberinto3d.juego.game.physics.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generador de la "Esfera III": una esfera tipo Perplexus con cuatro capas concéntricas. La pista (de 0,88 m de
 * ancho, cara de rodadura hacia el centro) serpentea por cada capa con curvas, horquillas y rectas, y pasa de una
 * capa a otra por rampas (hacia dentro) o por pozos de caída (hacia fuera). En las rectas hay más de cien
 * obstáculos: barreras, agujeros, eslálones, puertas estrechas, peines, puentes sin barandilla, pasarelas,
 * hielo, arena y saltos con trampolín. Es determinista (semillas fijas) y se genera una sola vez.
 */
object SphereThreeGenerator {
    const val NAME = "Esfera III"
    val RADII = floatArrayOf(2.0f, 2.8f, 3.6f, 4.4f, 5.2f)
    const val SHELL = 5.2f + 0.45f
    const val ROW = 0.20f
    const val LANES = 4
    const val LANE_W = 0.22f
    const val HALF_W = LANES * LANE_W / 2f
    const val RAIL_H = 0.30f
    const val RAIL_T = 0.08f
    const val GRAVITY_SCALE = 0.6f
    private const val START_SEED = 18
    private const val SAME_LAYER_GAP = 1.20f
    private const val CROSS_LAYER_GAP = 0.65f

    private val LAYER_COLORS = arrayOf(
        floatArrayOf(0.46f, 0.24f, 0.44f), floatArrayOf(0.18f, 0.40f, 0.48f),
        floatArrayOf(0.26f, 0.30f, 0.54f), floatArrayOf(0.28f, 0.46f, 0.30f), floatArrayOf(0.50f, 0.40f, 0.22f)
    )
    private val BARRIER_COLOR = floatArrayOf(0.95f, 0.56f, 0.26f, 1f)
    private val KICK_COLOR = floatArrayOf(0.95f, 0.80f, 0.30f, 1f)

    class Debug(
        val rows: Int,
        val ctr: FloatArray, val tan: FloatArray, val up: FloatArray, val right: FloatArray,
        val hole: Array<BooleanArray>, val barrier: Array<IntArray>, val barrier2: Array<IntArray>,
        val brk: BooleanArray,
        val cpRows: IntArray, val jumpLip: IntArray,
        val pathLength: Float, val counts: Map<String, Int>, val obstacles: Int, val seed: Int
    )

    class Result(val level: Level, val debug: Debug)

    private class Rng(var s: Long) {
        fun next(): Float {
            s = s * 6364136223846793005L + 1442695040888963407L
            return ((s ushr 33) and 0x7fffffL).toFloat() / 0x800000.toFloat()
        }
        fun pick(n: Int): Int = min(n - 1, (next() * n).toInt())
    }

    private class Row(val x: Float, val y: Float, val z: Float, val r: Float) {
        val hole = BooleanArray(LANES)
        val b1 = IntArray(2)
        val b2 = IntArray(2)
        var surf = 0
        var hv = 0f
        var gap = false
        var brk = false
        var railH = RAIL_H
        var railL = true
        var railR = true
        var cap = false
    }

    private class Turtle(var px: Float, var py: Float, var pz: Float, var tx: Float, var ty: Float, var tz: Float, var r: Float) {
        fun copy() = Turtle(px, py, pz, tx, ty, tz, r)
        fun set(o: Turtle) { px = o.px; py = o.py; pz = o.pz; tx = o.tx; ty = o.ty; tz = o.tz; r = o.r }

        /** Gira el rumbo [ang] rad (curvatura) y avanza [dist] m por la geodésica, variando el radio en [dr]. */
        fun advance(ang: Float, dist: Float, dr: Float) {
            // T = T cos + (p x T) sin
            val cx = py * tz - pz * ty; val cy = pz * tx - px * tz; val cz = px * ty - py * tx
            val ca = cos(ang); val sa = sin(ang)
            var ntx = tx * ca + cx * sa; var nty = ty * ca + cy * sa; var ntz = tz * ca + cz * sa
            val phi = dist / r
            val cp = cos(phi); val sp = sin(phi)
            val npx = px * cp + ntx * sp; val npy = py * cp + nty * sp; val npz = pz * cp + ntz * sp
            val ttx = -px * sp + ntx * cp; val tty = -py * sp + nty * cp; val ttz = -pz * sp + ntz * cp
            px = npx; py = npy; pz = npz; tx = ttx; ty = tty; tz = ttz
            val pl = sqrt(px * px + py * py + pz * pz); px /= pl; py /= pl; pz /= pl
            val d = tx * px + ty * py + tz * pz
            tx -= px * d; ty -= py * d; tz -= pz * d
            val tl = sqrt(tx * tx + ty * ty + tz * tz); tx /= tl; ty /= tl; tz /= tl
            r += dr
        }
    }

    private class Builder(val rng: Rng) {
        val rows = ArrayList<Row>(2000)
        val cur = Turtle(0f, -1f, 0f, 1f, 0f, 0f, RADII[0])
        val grid = HashMap<Long, ArrayList<Int>>()
        val counts = HashMap<String, Int>()
        var obstacles = 0
        val cps = ArrayList<Int>()
        val lips = ArrayList<Int>()

        fun key(cx: Int, cy: Int, cz: Int): Long = ((cx + 64).toLong() shl 40) or ((cy + 64).toLong() shl 20) or (cz + 64).toLong()
        fun cellOf(v: Float): Int = floor(v / 1.4f).toInt()

        fun count(k: String, n: Int) { counts[k] = (counts[k] ?: 0) + 1; obstacles += n }

        fun conflict(idx: Int, x: Float, y: Float, z: Float, r: Float): Boolean {
            val cx = cellOf(x); val cy = cellOf(y); val cz = cellOf(z)
            for (a in -1..1) for (b in -1..1) for (c in -1..1) {
                val l = grid[key(cx + a, cy + b, cz + c)] ?: continue
                for (o in l) {
                    if (idx - o <= 8) continue
                    val ro = rows[o]
                    val dx = ro.x - x; val dy = ro.y - y; val dz = ro.z - z
                    val need = if (abs(ro.r - r) < 0.35f) SAME_LAYER_GAP else CROSS_LAYER_GAP
                    if (dx * dx + dy * dy + dz * dz < need * need) return true
                }
            }
            return false
        }

        fun commit(t: Turtle): Row {
            val row = Row(t.px * t.r, t.py * t.r, t.pz * t.r, t.r)
            rows.add(row)
            grid.getOrPut(key(cellOf(row.x), cellOf(row.y), cellOf(row.z))) { ArrayList() }.add(rows.size - 1)
            return row
        }

        fun truncate(n: Int) {
            while (rows.size > n) {
                val i = rows.size - 1
                val row = rows[i]
                grid[key(cellOf(row.x), cellOf(row.y), cellOf(row.z))]?.remove(Integer.valueOf(i))
                rows.removeAt(i)
            }
        }

        /** Añade [n] filas con curvatura [kappa] (1/m) y variación de radio total [dTotal]. false si choca (se deshace). */
        fun chunk(kappa: Float, n: Int, dTotal: Float): Boolean {
            val saved = rows.size
            val st = cur.copy()
            for (k in 0 until n) {
                cur.advance(kappa * ROW, ROW, dTotal / n)
                val row = commit(cur)
                if (conflict(rows.size - 1, row.x, row.y, row.z, row.r)) {
                    truncate(saved); cur.set(st); return false
                }
            }
            return true
        }
    }

    // ---- obstáculos ----
    private val FEAT_ROWS = intArrayOf(4, 8, 19, 5, 16, 8, 10, 10, 10, 14)
    private val FEAT_NAME = arrayOf("barrera", "agujeros", "eslalon", "puerta", "peine", "puente", "pasarela", "hielo", "arena", "salto")

    private fun applyFeature(b: Builder, type: Int, a: Int) {
        val rows = b.rows
        val lo = if (b.rng.next() < 0.5f) 0 else 2
        val hi = 2 - lo
        fun hole(r: Int, l0: Int) { rows[r].hole[l0] = true; rows[r].hole[l0 + 1] = true }
        fun bar(r: Int, l0: Int) { rows[r].b1[0] = l0; rows[r].b1[1] = l0 + 2 }
        when (type) {
            0 -> { bar(a + 1, lo); b.count(FEAT_NAME[0], 1) }
            1 -> { hole(a + 1, lo); hole(a + 2, lo); hole(a + 5, hi); hole(a + 6, hi); b.count(FEAT_NAME[1], 2) }
            2 -> { bar(a + 1, lo); bar(a + 6, hi); bar(a + 11, lo); bar(a + 16, hi); b.count(FEAT_NAME[2], 4) }
            3 -> {
                rows[a + 1].b1[0] = 0; rows[a + 1].b1[1] = 1
                rows[a + 1].b2[0] = 3; rows[a + 1].b2[1] = 4
                rows[a + 2].b1[0] = 0; rows[a + 2].b1[1] = 1
                rows[a + 2].b2[0] = 3; rows[a + 2].b2[1] = 4
                b.count(FEAT_NAME[3], 2)
            }
            4 -> { hole(a + 1, lo); hole(a + 2, lo); hole(a + 5, hi); hole(a + 6, hi); hole(a + 9, lo); hole(a + 10, lo); hole(a + 13, hi); hole(a + 14, hi); b.count(FEAT_NAME[4], 4) }
            5 -> {
                val side = lo == 0
                for (r in a until a + 8) {
                    rows[r].hole[if (side) 0 else 3] = true
                    if (side) rows[r].railL = false else rows[r].railR = false
                }
                b.count(FEAT_NAME[5], 1)
            }
            6 -> {
                for (r in a until a + 10) {
                    rows[r].hole[0] = true; rows[r].hole[3] = true; rows[r].railL = false; rows[r].railR = false
                }
                b.count(FEAT_NAME[6], 1)
            }
            7 -> { for (r in a until a + 10) rows[r].surf = 1; bar(a + 4, lo); b.count(FEAT_NAME[7], 2) }
            8 -> { for (r in a until a + 10) rows[r].surf = 2; hole(a + 3, lo); hole(a + 4, lo); hole(a + 7, hi); b.count(FEAT_NAME[8], 2) }
            else -> {
                val k0 = a + 5
                for (q in 0..3) rows[k0 + q].hv = 0.2f * q / 3f
                val kt = k0 + 3
                rows[kt].gap = true; rows[kt + 1].gap = true
                for (q in (k0 - 1)..(kt + 4)) rows[q].railH = 0.55f
                b.lips.add(kt)
                b.count(FEAT_NAME[9], 1)
            }
        }
    }

    private fun placeFeature(b: Builder, type: Int, wantCp: Boolean): Boolean {
        val a0 = b.rows.size
        if (!b.chunk(0f, FEAT_ROWS[type] + 4, 0f)) return false
        applyFeature(b, type, a0 + 2)
        if (wantCp) b.cps.add(a0 + 1)
        return true
    }

    private val KAPPAS = floatArrayOf(0.40f, 0.60f, 0.80f, 0.95f)
    private val ANGLES = floatArrayOf(50f, 70f, 90f, 120f, 150f, 180f)

    private fun lookaheadOk(b: Builder, n: Int): Boolean {
        val saved = b.rows.size
        val st = b.cur.copy()
        val ok = b.chunk(0f, n, 0f)
        b.truncate(saved); b.cur.set(st)
        return ok
    }

    /** Curva (o un tramo corto recto) tras la cual aún cabe una recta larga: así no se cierra el camino. */
    private fun connector(b: Builder): Boolean {
        for (att in 0 until 40) {
            val saved = b.rows.size
            val st = b.cur.copy()
            val ok: Boolean
            if (att < 3 && b.rng.next() < 0.2f) ok = b.chunk(0f, 3, 0f)
            else {
                val kap = KAPPAS[b.rng.pick(KAPPAS.size)] * (if (b.rng.next() < 0.5f) -1f else 1f)
                val ang = Math.toRadians(ANGLES[b.rng.pick(ANGLES.size)].toDouble()).toFloat()
                val n = min(26, max(4, (ang / abs(kap) / ROW).toInt()))
                ok = b.chunk(kap, n, 0f)
            }
            if (ok) {
                if (lookaheadOk(b, 26)) return true
                b.truncate(saved); b.cur.set(st)
            }
        }
        return false
    }

    private fun ramp(b: Builder, toLayer: Int): Boolean {
        val n = 26
        for (att in 0 until 12) {
            val kap = if (att == 0) 0f else (if (att % 2 == 0) 1f else -1f) * (0.15f + 0.05f * (att / 2))
            val dr = RADII[toLayer] - b.cur.r
            val saved = b.rows.size
            if (b.chunk(kap, n, dr)) return true
            if (b.rows.size != saved) return false
            if (att >= 4 && !connector(b)) return false
        }
        return false
    }

    private fun drop(b: Builder, toLayer: Int): Boolean {
        for (att in 0 until 10) {
            val saved = b.rows.size
            val st = b.cur.copy()
            val pad = b.cur.copy()
            pad.advance(0f, -0.6f, 0f)
            pad.r = RADII[toLayer]
            b.cur.set(pad)
            val first = b.commit(b.cur)
            val firstIdx = b.rows.size - 1
            var ok = !b.conflict(firstIdx, first.x, first.y, first.z, first.r)
            if (ok) {
                b.rows[saved - 1].brk = true
                first.cap = true
                ok = b.chunk(0f, 11, 0f)
                if (!ok) b.rows[saved - 1].brk = false
            }
            if (ok) { b.cps.add(firstIdx + 5); return true }
            b.truncate(saved); b.cur.set(st)
            if (!connector(b)) return false
        }
        return false
    }

    private fun pickType(b: Builder, f: Int, total: Int, prev: Int): Int {
        if (f % 7 == 5) return 9
        val d = f / (total - 1f)
        val w = floatArrayOf(
            max(0.6f, 2f - 1.6f * d), 1.2f, if (d > 0.08f) 1.5f else 0f, if (d > 0.15f) 1.2f else 0f,
            if (d > 0.22f) 1.5f else 0f, if (d > 0.20f) 0.5f + d else 0f, if (d > 0.50f) 1.2f * d else 0f,
            if (d > 0.12f) 0.9f else 0f, if (d > 0.05f) 0.9f else 0f
        )
        if (prev in 0..8) w[prev] = 0f
        var sum = 0f; for (x in w) sum += x
        var pick = b.rng.next() * sum
        for (i in w.indices) { if (pick < w[i]) return i; pick -= w[i] }
        return 0
    }

    private val STAGE_LAYER = intArrayOf(0, 1, 2, 3, 4)
    private val STAGE_FEATS = intArrayOf(3, 7, 11, 16, 19)

    /** Para pruebas: dónde falló el último intento (etapa, característica). */
    @JvmField var failInfo = ""
    @JvmField var failHist = IntArray(40)

    private fun build(seed: Int): Result? {
        val b = Builder(Rng(20261102L + seed * 7919L))
        b.commit(b.cur)
        b.chunk(0f, 11, 0f)
        b.rows[0].cap = true
        val total = STAGE_FEATS.sum()
        var f = 0
        var prev = -1
        var sinceCp = 0
        for (s in STAGE_LAYER.indices) {
            val snapRows = b.rows.size
            val snapCur = b.cur.copy()
            val snapCounts = HashMap(b.counts)
            val snapObst = b.obstacles
            val snapCps = b.cps.size
            val snapLips = b.lips.size
            val snapF = f; val snapPrev = prev; val snapSince = sinceCp
            var tries = 0
            while (true) {
                var ok = true
                for (k in 0 until STAGE_FEATS[s]) {
                    var done = false
                    for (att in 0 until 8) {
                        if (!connector(b)) break
                        val type = pickType(b, f, total, prev)
                        val wantCp = sinceCp >= 8
                        if (placeFeature(b, type, wantCp)) {
                            sinceCp = if (wantCp) 0 else sinceCp
                            sinceCp += when (type) { 0 -> 1; 1 -> 2; 2 -> 4; 3 -> 2; 4 -> 4; 5 -> 1; 6 -> 1; 7 -> 2; 8 -> 2; else -> 1 }
                            prev = type; f++; done = true; break
                        }
                    }
                    if (!done) { ok = false; failInfo = "feat s=$s f=$f"; break }
                }
                if (ok && s + 1 < STAGE_LAYER.size) {
                    val to = STAGE_LAYER[s + 1]
                    val useRamp = (s % 2 == 0)
                    ok = if (useRamp) (connector(b) && ramp(b, to)) else drop(b, to)
                    if (!ok) failInfo = "trans s=$s ${if (useRamp) "ramp" else "drop"}"
                    else { if (useRamp) b.cps.add(b.rows.size - 3); sinceCp = 0 }
                }
                if (ok) break
                failHist[min(39, s)]++
                tries++
                if (tries > 60) return null
                b.truncate(snapRows); b.cur.set(snapCur)
                b.counts.clear(); b.counts.putAll(snapCounts); b.obstacles = snapObst
                while (b.cps.size > snapCps) b.cps.removeAt(b.cps.size - 1)
                while (b.lips.size > snapLips) b.lips.removeAt(b.lips.size - 1)
                f = snapF; prev = snapPrev; sinceCp = snapSince
            }
        }
        if (!b.chunk(0f, 14, 0f)) { failInfo = "final"; return null }
        return finish(b, seed)
    }

    private fun finish(b: Builder, seed: Int): Result {
        val n = b.rows.size
        val rows = b.rows
        val ctr = FloatArray(n * 3)
        for (i in 0 until n) { ctr[i * 3] = rows[i].x; ctr[i * 3 + 1] = rows[i].y; ctr[i * 3 + 2] = rows[i].z }
        // tramos continuos
        val segStart = IntArray(n); val segEnd = IntArray(n)
        var s0 = 0
        for (i in 0 until n) { segStart[i] = s0; if (rows[i].brk) s0 = i + 1 }
        var e0 = n - 1
        for (i in n - 1 downTo 0) { segEnd[i] = e0; if (i > 0 && rows[i - 1].brk) e0 = i - 1 }
        val tan = FloatArray(n * 3); val up = FloatArray(n * 3); val rt = FloatArray(n * 3)
        for (i in 0 until n) {
            val a = max(segStart[i], i - 2); val bb = min(segEnd[i], i + 2)
            var dx = ctr[bb * 3] - ctr[a * 3]; var dy = ctr[bb * 3 + 1] - ctr[a * 3 + 1]; var dz = ctr[bb * 3 + 2] - ctr[a * 3 + 2]
            val dl = sqrt(dx * dx + dy * dy + dz * dz); dx /= dl; dy /= dl; dz /= dl
            val pl = sqrt(ctr[i * 3] * ctr[i * 3] + ctr[i * 3 + 1] * ctr[i * 3 + 1] + ctr[i * 3 + 2] * ctr[i * 3 + 2])
            var ux = -ctr[i * 3] / pl; var uy = -ctr[i * 3 + 1] / pl; var uz = -ctr[i * 3 + 2] / pl
            val d = ux * dx + uy * dy + uz * dz
            ux -= dx * d; uy -= dy * d; uz -= dz * d
            val ul = sqrt(ux * ux + uy * uy + uz * uz); ux /= ul; uy /= ul; uz /= ul
            tan[i * 3] = dx; tan[i * 3 + 1] = dy; tan[i * 3 + 2] = dz
            up[i * 3] = ux; up[i * 3 + 1] = uy; up[i * 3 + 2] = uz
            rt[i * 3] = dy * uz - dz * uy; rt[i * 3 + 1] = dz * ux - dx * uz; rt[i * 3 + 2] = dx * uy - dy * ux
        }
        var length = 0f
        for (i in 1 until n) if (!rows[i - 1].brk) {
            val dx = ctr[i * 3] - ctr[i * 3 - 3]; val dy = ctr[i * 3 + 1] - ctr[i * 3 - 2]; val dz = ctr[i * 3 + 2] - ctr[i * 3 - 1]
            length += sqrt(dx * dx + dy * dy + dz * dz)
        }

        // ---- geometría ----
        val m = SphereGenerator.Mesh()
        fun pt(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
        fun lane(i: Int, off: Float, h: Float) = pt(
            ctr[i * 3] + rt[i * 3] * off + up[i * 3] * h,
            ctr[i * 3 + 1] + rt[i * 3 + 1] * off + up[i * 3 + 1] * h,
            ctr[i * 3 + 2] + rt[i * 3 + 2] * off + up[i * 3 + 2] * h
        )
        fun layerOf(r: Float): Int { var bi = 0; for (k in RADII.indices) if (abs(RADII[k] - r) < abs(RADII[bi] - r)) bi = k; return bi }
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
        fun barrierWall(i: Int, l0: Int, l1: Int) {
            val o0 = -HALF_W + LANE_W * l0; val o1 = -HALF_W + LANE_W * l1
            wall3(lane(i, o0, 0f), lane(i, o1, 0f), tan[i * 3], tan[i * 3 + 1], tan[i * 3 + 2],
                up[i * 3], up[i * 3 + 1], up[i * 3 + 2], RAIL_T * 1.5f, 0.24f, BARRIER_COLOR)
        }
        for (i in 0 until n - 1) {
            if (rows[i].brk) {
                // fin de tramo: sólo el borde de los barrotes
                continue
            }
            val lc = LAYER_COLORS[layerOf(rows[i].r)]
            val prog = i / (n - 1f)
            val lum = 0.04f * prog
            if (!rows[i].gap) {
                for (l in 0 until LANES) {
                    if (rows[i].hole[l]) continue
                    val sf = rows[i].surf
                    val kick = rows[i + 1].hv > rows[i].hv + 1e-4f
                    val chk = if ((i / 3 + l) % 2 == 0) 0.02f else 0f
                    val col = when {
                        kick -> KICK_COLOR
                        sf == 1 -> floatArrayOf(0.45f + lum, 0.68f + lum, 0.82f + lum, 1f)
                        sf == 2 -> floatArrayOf(0.66f + lum, 0.54f + lum, 0.32f + lum, 1f)
                        else -> floatArrayOf(lc[0] + lum + chk, lc[1] + lum + chk, lc[2] + lum + chk, 1f)
                    }
                    val o0 = -HALF_W + LANE_W * l
                    val o1 = o0 + LANE_W
                    m.quad(lane(i, o0, rows[i].hv), lane(i, o1, rows[i].hv), lane(i + 1, o1, rows[i + 1].hv), lane(i + 1, o0, rows[i + 1].hv),
                        up[i * 3] + up[i * 3 + 3], up[i * 3 + 1] + up[i * 3 + 4], up[i * 3 + 2] + up[i * 3 + 5], sf, col)
                }
            }
            val railCol = floatArrayOf(min(1f, lc[0] * 1.7f + 0.12f), min(1f, lc[1] * 1.7f + 0.12f), min(1f, lc[2] * 1.7f + 0.12f), 1f)
            for (sd in 0..1) {
                if (sd == 0 && !rows[i].railL) continue
                if (sd == 1 && !rows[i].railR) continue
                val sg = if (sd == 0) -1f else 1f
                val a0 = lane(i, sg * HALF_W, 0f); val a1 = lane(i + 1, sg * HALF_W, 0f)
                var nx = sg * (rt[i * 3] + rt[i * 3 + 3]); var ny = sg * (rt[i * 3 + 1] + rt[i * 3 + 4]); var nz = sg * (rt[i * 3 + 2] + rt[i * 3 + 5])
                val nl = sqrt(nx * nx + ny * ny + nz * nz); nx /= nl; ny /= nl; nz /= nl
                wall3(a0, a1, nx, ny, nz, up[i * 3], up[i * 3 + 1], up[i * 3 + 2], RAIL_T, rows[i].railH, railCol)
            }
            if (rows[i].b1[1] > rows[i].b1[0]) barrierWall(i, rows[i].b1[0], rows[i].b1[1])
            if (rows[i].b2[1] > rows[i].b2[0]) barrierWall(i, rows[i].b2[0], rows[i].b2[1])
        }
        for (kt in b.lips) {
            val a = lane(kt, -HALF_W, 0f); val bq = lane(kt, HALF_W, 0f)
            val at = lane(kt, -HALF_W, rows[kt].hv); val bt = lane(kt, HALF_W, rows[kt].hv)
            m.quad(a, bq, bt, at, tan[kt * 3], tan[kt * 3 + 1], tan[kt * 3 + 2], 0, KICK_COLOR)
        }
        for (i in 0 until n) if (rows[i].cap) {
            val lc = LAYER_COLORS[layerOf(rows[i].r)]
            val col = floatArrayOf(min(1f, lc[0] * 1.7f + 0.12f), min(1f, lc[1] * 1.7f + 0.12f), min(1f, lc[2] * 1.7f + 0.12f), 1f)
            wall3(lane(i, -HALF_W, 0f), lane(i, HALF_W, 0f), -tan[i * 3], -tan[i * 3 + 1], -tan[i * 3 + 2],
                up[i * 3], up[i * 3 + 1], up[i * 3 + 2], RAIL_T, 0.6f, col)
        }
        val le = n - 1
        run {
            val lc = LAYER_COLORS[layerOf(rows[le].r)]
            val col = floatArrayOf(min(1f, lc[0] * 1.7f + 0.12f), min(1f, lc[1] * 1.7f + 0.12f), min(1f, lc[2] * 1.7f + 0.12f), 1f)
            wall3(lane(le, -HALF_W, 0f), lane(le, HALF_W, 0f), tan[le * 3], tan[le * 3 + 1], tan[le * 3 + 2],
                up[le * 3], up[le * 3 + 1], up[le * 3 + 2], RAIL_T, 0.6f, col)
        }

        val tris = m.tris.toFloatArray(); val norms = m.norms.toFloatArray()
        val surf = m.surf.toIntArray(); val cols = m.cols.toFloatArray()

        // ---- objetos ----
        fun floorPos(i: Int) = Vec3(ctr[i * 3], ctr[i * 3 + 1], ctr[i * 3 + 2])
        val startRow = 2
        val sp = floorPos(startRow)
        val ballR = 0.15f
        val start = Vec3(sp.x + up[startRow * 3] * (ballR + 0.01f), sp.y + up[startRow * 3 + 1] * (ballR + 0.01f), sp.z + up[startRow * 3 + 2] * (ballR + 0.01f))
        val cpRows = b.cps.distinct().sorted().filter { it > 6 && it < n - 12 }.toIntArray()
        val cpAll = ArrayList<Vec3>()
        for (c in cpRows) cpAll.add(floorPos(c))
        val frameT = FloatArray((cpAll.size + 1) * 3)
        frameT[0] = tan[startRow * 3]; frameT[1] = tan[startRow * 3 + 1]; frameT[2] = tan[startRow * 3 + 2]
        for (k in cpRows.indices) {
            val c = cpRows[k]
            frameT[(k + 1) * 3] = tan[c * 3]; frameT[(k + 1) * 3 + 1] = tan[c * 3 + 1]; frameT[(k + 1) * 3 + 2] = tan[c * 3 + 2]
        }
        val goalRow = n - 7
        val bound = SHELL + 1.5f
        val level = Level(
            id = Levels.SPHERE3_ID, name = NAME, boxes = emptyList(),
            start = start, goal = floorPos(goalRow), goalRadius = 0.55f,
            bestTimeKey = Levels.bestTimeKey(Levels.SPHERE3_ID),
            minX = -bound, maxX = bound, minZ = -bound, maxZ = bound,
            checkpoints = cpAll, topY = SHELL,
            tris = tris, triNormals = norms, triSurf = surf, triColors = cols,
            shellRadius = SHELL, pathPts = ctr, fallDrop = 0f, offPathDist = 0.85f,
            rotating = true, gravityScale = GRAVITY_SCALE, frameT = frameT, viewZoom = 0.72f
        )
        val hole = Array(n) { rows[it].hole }
        val bar1 = Array(n) { rows[it].b1 }
        val bar2 = Array(n) { rows[it].b2 }
        val brk = BooleanArray(n) { rows[it].brk }
        return Result(level, Debug(n, ctr, tan, up, rt, hole, bar1, bar2, brk, cpRows, b.lips.toIntArray(), length, b.counts, b.obstacles, seed))
    }

    fun generate(): Result {
        // la semilla 18 es la primera que produce un recorrido válido (las anteriores se descartan); se empieza en ella
        for (seed in START_SEED until START_SEED + 2000) {
            val r = try { build(seed) } catch (t: Throwable) { failInfo = "exc " + t; null }
            if (r != null) return r
        }
        throw IllegalStateException("Esfera III: no se pudo generar")
    }
}
