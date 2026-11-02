package com.laberinto3d.juego.game.levels

import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.physics.Box
import com.laberinto3d.juego.game.physics.Ramp
import com.laberinto3d.juego.game.physics.Vec3
import kotlin.math.sqrt

/**
 * Convierte un mapa de texto en un [Level]. Una celda = Config.CELL metros. Secciones:
 *
 *   [pisos]       altura del suelo de cada celda:  '.' o '0' = a ras,  '1'..'9' = peldaños (Config.HEIGHT_STEP m),
 *                 '-', '_' o ' ' = vacío (agujero). Rampas: '^' 'v' '<' '>' = rampa que SUBE hacia arriba / abajo /
 *                 izquierda / derecha de la pantalla; una rampa es un rectángulo del mismo carácter entre dos
 *                 celdas planas de distinta altura (la de abajo y la de arriba).
 *   [muros]       '#' = muro sobre la celda (WALL_HEIGHT de alto sobre su suelo). No sobre rampas.
 *   [superficies] 'h' = hielo, 'r' = arena (sólo celdas de suelo plano)
 *   [objetos]     'S' inicio, 'G' meta, 'a'..'z' checkpoints (por orden alfabético)
 *   [moviles]     una plataforma por línea:  fila0 col0 fila1 col1 | altura | dx dy dz | periodo | fase
 *                 (celdas inclusivas; altura en peldaños; dx/dz en celdas, dy en peldaños; periodo en s;
 *                 fase 0..1). Las celdas de la plataforma deben ser agujero en [pisos].
 *
 * La primera fila del texto es la de ARRIBA en pantalla. Las secciones opcionales pueden omitirse.
 */
object MapParser {
    class MapException(msg: String) : RuntimeException(msg)

    private val WALL_COLOR = floatArrayOf(0.45f, 0.40f, 0.86f, 1f)
    private val MOVER_COLOR = floatArrayOf(0.20f, 0.52f, 0.58f, 0.5f)
    const val RAMP_CELL = -2

    class MoverSpec(
        val r0: Int, val c0: Int, val r1: Int, val c1: Int, val h: Int,
        val dx: Float, val dy: Float, val dz: Float, val period: Float, val phase: Float
    )

    /** Cuadrícula resultante, útil también para pruebas. */
    class Grid(
        val w: Int, val h: Int,
        /** altura en peldaños por celda; -1 = agujero; RAMP_CELL = rampa */
        val floor: Array<IntArray>,
        val ramp: Array<CharArray>,
        val surf: Array<IntArray>,
        val wall: Array<BooleanArray>,
        val startCell: IntArray,       // [fila, col]
        val goalCell: IntArray,
        val checkpointCells: List<IntArray>,
        val movers: List<MoverSpec>
    )

    fun parseGrid(text: String): Grid {
        val sections = HashMap<String, ArrayList<String>>()
        var cur: ArrayList<String>? = null
        for (raw in text.split('\n')) {
            val line = raw.trimEnd('\r')
            val t = line.trim()
            if (t.startsWith("[") && t.endsWith("]")) {
                cur = ArrayList()
                sections[t.substring(1, t.length - 1).lowercase()] = cur
                continue
            }
            if (cur != null) cur.add(line)
        }
        fun clean(l: ArrayList<String>?): List<String> {
            if (l == null) return emptyList()
            var a = 0
            var b = l.size
            while (a < b && l[a].isEmpty()) a++
            while (b > a && l[b - 1].isEmpty()) b--
            return l.subList(a, b)
        }
        val pisos = clean(sections["pisos"])
        if (pisos.isEmpty()) throw MapException("Falta la sección [pisos]")
        val muros = clean(sections["muros"])
        val sup = clean(sections["superficies"])
        val objetos = clean(sections["objetos"])
        val h = pisos.size
        val w = pisos.maxOf { it.length }
        val ramp = Array(h) { CharArray(w) { ' ' } }
        val floor = Array(h) { r -> IntArray(w) { c ->
            val ch = pisos[r].getOrElse(c) { '-' }
            when (ch) {
                '.' -> 0
                in '0'..'9' -> ch - '0'
                '-', '_', ' ' -> -1
                '^', 'v', '<', '>' -> { ramp[r][c] = ch; RAMP_CELL }
                else -> throw MapException("Carácter de [pisos] no válido '$ch' en fila ${r + 1}, columna ${c + 1}")
            }
        } }
        val wall = Array(h) { r -> BooleanArray(w) { c -> muros.getOrNull(r)?.getOrNull(c) == '#' } }
        val surf = Array(h) { r -> IntArray(w) { c ->
            when (val ch = sup.getOrNull(r)?.getOrNull(c) ?: '.') {
                'h' -> 1
                'r' -> 2
                '.', ' ' -> 0
                else -> throw MapException("Carácter de [superficies] no válido '$ch' en fila ${r + 1}, columna ${c + 1}")
            }
        } }
        var start: IntArray? = null
        var goal: IntArray? = null
        val cps = java.util.TreeMap<Char, IntArray>()
        for (r in 0 until h) for (c in 0 until w) {
            when (val ch = objetos.getOrNull(r)?.getOrNull(c) ?: '.') {
                'S' -> { if (start != null) throw MapException("Más de un inicio 'S'"); start = intArrayOf(r, c) }
                'G' -> { if (goal != null) throw MapException("Más de una meta 'G'"); goal = intArrayOf(r, c) }
                in 'a'..'z' -> { if (cps.containsKey(ch)) throw MapException("Checkpoint '$ch' repetido"); cps[ch] = intArrayOf(r, c) }
                '.', ' ' -> {}
                else -> throw MapException("Carácter de [objetos] no válido '$ch' en fila ${r + 1}, columna ${c + 1}")
            }
        }
        if (start == null) throw MapException("Falta el inicio 'S'")
        if (goal == null) throw MapException("Falta la meta 'G'")
        for (cell in listOf(start, goal) + cps.values) {
            if (floor[cell[0]][cell[1]] < 0) throw MapException("Objeto sobre agujero o rampa en fila ${cell[0] + 1}, columna ${cell[1] + 1}")
        }
        for (r in 0 until h) for (c in 0 until w) {
            if (wall[r][c] && floor[r][c] == RAMP_CELL) throw MapException("Muro sobre una rampa en fila ${r + 1}, columna ${c + 1}")
        }
        val movers = ArrayList<MoverSpec>()
        for ((i, line) in clean(sections["moviles"]).withIndex()) {
            if (line.isBlank()) continue
            val parts = line.split('|').map { it.trim() }
            if (parts.size != 5) throw MapException("Línea [moviles] ${i + 1}: se esperan 5 campos separados por '|'")
            try {
                val rc = parts[0].split(Regex("\\s+")).map { it.toInt() }
                val d = parts[2].split(Regex("\\s+")).map { it.toFloat() }
                if (rc.size != 4 || d.size != 3) throw MapException("formato")
                val m = MoverSpec(rc[0], rc[1], rc[2], rc[3], parts[1].toInt(), d[0], d[1], d[2], parts[3].toFloat(), parts[4].toFloat())
                for (r in m.r0..m.r1) for (c in m.c0..m.c1) {
                    if (r !in 0 until h || c !in 0 until w) throw MapException("Plataforma fuera del mapa")
                    if (floor[r][c] != -1) throw MapException("La celda (${r + 1},${c + 1}) de la plataforma ${i + 1} debe ser agujero en [pisos]")
                }
                movers.add(m)
            } catch (e: NumberFormatException) {
                throw MapException("Línea [moviles] ${i + 1}: número no válido")
            }
        }
        return Grid(w, h, floor, ramp, surf, wall, start, goal, cps.values.toList(), movers)
    }

    fun topOf(heightIdx: Int): Float = heightIdx * Config.HEIGHT_STEP

    /** Una rampa ya resuelta: rectángulo de celdas, dirección (0..3) y alturas (en peldaños) de sus extremos. */
    class RampInfo(val r0: Int, val c0: Int, val r1: Int, val c1: Int, val dir: Int, val lowIdx: Int, val highIdx: Int)

    fun findRamps(g: Grid): List<RampInfo> {
        val seen = Array(g.h) { BooleanArray(g.w) }
        val out = ArrayList<RampInfo>()
        for (r in 0 until g.h) for (c in 0 until g.w) {
            val ch = g.ramp[r][c]
            if (ch == ' ' || seen[r][c]) continue
            var c1 = c
            while (c1 + 1 < g.w && g.ramp[r][c1 + 1] == ch) c1++
            var r1 = r
            while (r1 + 1 < g.h && (c..c1).all { g.ramp[r1 + 1][it] == ch }) r1++
            for (rr in r..r1) for (cc in c..c1) {
                if (g.ramp[rr][cc] != ch) throw MapException("Rampa no rectangular en fila ${r + 1}, columna ${c + 1}")
                seen[rr][cc] = true
            }
            // la celda anterior/posterior del mismo carácter fuera del rectángulo también sería no rectangular
            if (c > 0 && g.ramp[r][c - 1] == ch) throw MapException("Rampa no rectangular en fila ${r + 1}")
            val dir = when (ch) { '^' -> 0; 'v' -> 1; '<' -> 2; else -> 3 }
            // celdas vecinas en el extremo bajo y en el alto
            fun heights(cells: List<IntArray>): Int {
                var hh = -99
                for (cell in cells) {
                    val rr = cell[0]; val cc = cell[1]
                    val v = if (rr in 0 until g.h && cc in 0 until g.w) g.floor[rr][cc] else -1
                    if (v < 0) throw MapException("Rampa en fila ${r + 1}, columna ${c + 1}: falta suelo plano en uno de sus extremos")
                    if (hh != -99 && hh != v) throw MapException("Rampa en fila ${r + 1}, columna ${c + 1}: alturas distintas en un extremo")
                    hh = v
                }
                return hh
            }
            val top = (c..c1).map { intArrayOf(r - 1, it) }
            val bottom = (c..c1).map { intArrayOf(r1 + 1, it) }
            val left = (r..r1).map { intArrayOf(it, c - 1) }
            val right = (r..r1).map { intArrayOf(it, c1 + 1) }
            val (lowCells, highCells) = when (dir) { 0 -> bottom to top; 1 -> top to bottom; 2 -> right to left; else -> left to right }
            val lo = heights(lowCells)
            val hi = heights(highCells)
            if (hi <= lo) throw MapException("Rampa en fila ${r + 1}, columna ${c + 1}: el extremo alto no es más alto que el bajo")
            out.add(RampInfo(r, c, r1, c1, dir, lo, hi))
        }
        return out
    }

    fun build(id: Int, name: String, text: String): Level {
        val g = parseGrid(text)
        val cell = Config.CELL
        val x0 = -g.w * cell / 2f
        val z0 = -g.h * cell / 2f
        fun cx(c: Int) = x0 + (c + 0.5f) * cell
        fun cz(r: Int) = z0 + (r + 0.5f) * cell
        val boxes = ArrayList<Box>()
        var topY = Config.WALL_HEIGHT

        // Suelos planos: rectángulos fusionados de la misma altura y superficie
        val keysFloor = Array(g.h) { r -> IntArray(g.w) { c -> if (g.floor[r][c] >= 0) g.floor[r][c] + 16 * g.surf[r][c] else -1 } }
        for (m in mergeRects(keysFloor, g.w, g.h)) {
            val idx = m.key % 16
            val surface = m.key / 16
            val top = topOf(idx)
            val l = 0.04f * idx
            val col = when (surface) {
                1 -> floatArrayOf(0.30f + l, 0.50f + l, 0.64f + l, 1f)
                2 -> floatArrayOf(0.50f + l, 0.42f + l, 0.26f + l, 1f)
                else -> floatArrayOf(0.17f + l, 0.17f + l, 0.28f + l * 1.2f, 1f)
            }
            boxes.add(Box(
                Vec3(x0 + m.c0 * cell, Config.FLOOR_BASE_Y, z0 + m.r0 * cell),
                Vec3(x0 + (m.c1 + 1) * cell, top, z0 + (m.r1 + 1) * cell), col, true, surface))
            if (top > topY) topY = top
        }
        // Muros: rectángulos fusionados con la misma altura de base
        val keysWall = Array(g.h) { r -> IntArray(g.w) { c ->
            if (g.wall[r][c]) maxOf(g.floor[r][c], 0) + (if (g.floor[r][c] < 0) 100 else 0) else -1 } }
        for (m in mergeRects(keysWall, g.w, g.h)) {
            val hole = m.key >= 100
            val idx = if (hole) m.key - 100 else m.key
            val top = topOf(idx)
            val minY = if (hole) Config.FLOOR_BASE_Y else top
            boxes.add(Box(
                Vec3(x0 + m.c0 * cell, minY, z0 + m.r0 * cell),
                Vec3(x0 + (m.c1 + 1) * cell, top + Config.WALL_HEIGHT, z0 + (m.r1 + 1) * cell), WALL_COLOR))
            if (top + Config.WALL_HEIGHT > topY) topY = top + Config.WALL_HEIGHT
        }

        // Rampas: triángulos para colisión y render
        val ramps = ArrayList<Ramp>()
        val tris = ArrayList<Float>()
        val norms = ArrayList<Float>()
        fun addTri(a: FloatArray, b: FloatArray, c: FloatArray, wantX: Float, wantY: Float, wantZ: Float) {
            var bb = b; var cc = c
            val ux = bb[0] - a[0]; val uy = bb[1] - a[1]; val uz = bb[2] - a[2]
            val vx = cc[0] - a[0]; val vy = cc[1] - a[1]; val vz = cc[2] - a[2]
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            if (nx * wantX + ny * wantY + nz * wantZ < 0f) { val t = bb; bb = cc; cc = t; nx = -nx; ny = -ny; nz = -nz }
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            if (l < 1e-9f) return
            for (p in listOf(a, bb, cc)) { tris.add(p[0]); tris.add(p[1]); tris.add(p[2]) }
            norms.add(nx / l); norms.add(ny / l); norms.add(nz / l)
        }
        for (rp in findRamps(g)) {
            val xa = x0 + rp.c0 * cell
            val xb = x0 + (rp.c1 + 1) * cell
            val za = z0 + rp.r0 * cell
            val zb = z0 + (rp.r1 + 1) * cell
            val yl = topOf(rp.lowIdx)
            val yh = topOf(rp.highIdx)
            ramps.add(Ramp(xa, za, xb, zb, rp.dir, yl, yh))
            if (yh > topY) topY = yh
            // altura de la superficie en cada esquina (x, z)
            fun hAt(x: Float, z: Float): Float = ramps.last().heightAt(x, z)
            val base = Config.FLOOR_BASE_Y
            val p00 = floatArrayOf(xa, hAt(xa, za), za)
            val p10 = floatArrayOf(xb, hAt(xb, za), za)
            val p01 = floatArrayOf(xa, hAt(xa, zb), zb)
            val p11 = floatArrayOf(xb, hAt(xb, zb), zb)
            addTri(p00, p01, p11, 0f, 1f, 0f)
            addTri(p00, p11, p10, 0f, 1f, 0f)
            // laterales (hacia fuera)
            fun side(p: FloatArray, q: FloatArray, nx: Float, nz: Float) {
                val pb = floatArrayOf(p[0], base, p[2]); val qb = floatArrayOf(q[0], base, q[2])
                addTri(pb, qb, q, nx, 0f, nz)
                addTri(pb, q, p, nx, 0f, nz)
            }
            side(p00, p01, -1f, 0f)   // lado x = xa
            side(p10, p11, 1f, 0f)    // lado x = xb
            side(p00, p10, 0f, -1f)   // lado z = za
            side(p01, p11, 0f, 1f)    // lado z = zb
        }

        // Plataformas móviles
        val movers = ArrayList<MoverDef>()
        for (m in g.movers) {
            val top = topOf(m.h)
            movers.add(MoverDef(
                Vec3(x0 + m.c0 * cell, top - 0.25f, z0 + m.r0 * cell),
                Vec3(x0 + (m.c1 + 1) * cell, top, z0 + (m.r1 + 1) * cell),
                MOVER_COLOR,
                m.dx * cell, m.dy * Config.HEIGHT_STEP, m.dz * cell, m.period, m.phase))
            val hi = top + maxOf(0f, m.dy * Config.HEIGHT_STEP)
            if (hi > topY) topY = hi
        }

        val sy = topOf(g.floor[g.startCell[0]][g.startCell[1]])
        val start = Vec3(cx(g.startCell[1]), sy + Config.BALL_RADIUS, cz(g.startCell[0]))
        val goal = Vec3(cx(g.goalCell[1]), topOf(g.floor[g.goalCell[0]][g.goalCell[1]]), cz(g.goalCell[0]))
        val cps = g.checkpointCells.map { Vec3(cx(it[1]), topOf(g.floor[it[0]][it[1]]), cz(it[0])) }
        return Level(
            id = id, name = name, boxes = boxes, start = start, goal = goal, goalRadius = 0.30f,
            bestTimeKey = Levels.bestTimeKey(id),
            minX = x0, maxX = x0 + g.w * cell, minZ = z0, maxZ = z0 + g.h * cell,
            checkpoints = cps, topY = topY,
            tris = tris.toFloatArray(), triNormals = norms.toFloatArray(), ramps = ramps, moverDefs = movers
        )
    }

    class Rect(val r0: Int, val c0: Int, val r1: Int, val c1: Int, val key: Int)

    /** Fusión voraz de celdas con la misma clave (>= 0) en rectángulos. Claves < 0 se ignoran. */
    fun mergeRects(keys: Array<IntArray>, w: Int, h: Int): List<Rect> {
        val used = Array(h) { BooleanArray(w) }
        val out = ArrayList<Rect>()
        for (r in 0 until h) for (c in 0 until w) {
            val k = keys[r][c]
            if (k < 0 || used[r][c]) continue
            var c1 = c
            while (c1 + 1 < w && keys[r][c1 + 1] == k && !used[r][c1 + 1]) c1++
            var r1 = r
            while (r1 + 1 < h) {
                var ok = true
                for (cc in c..c1) if (keys[r1 + 1][cc] != k || used[r1 + 1][cc]) { ok = false; break }
                if (!ok) break
                r1++
            }
            for (rr in r..r1) for (cc in c..c1) used[rr][cc] = true
            out.add(Rect(r, c, r1, c1, k))
        }
        return out
    }
}
