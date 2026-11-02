package com.laberinto3d.juego.game.levels

import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.physics.Box
import com.laberinto3d.juego.game.physics.Vec3

/** Los tres niveles de la v1.0. Todo en metros; ver sección 10 del plan. */
object Levels {
    const val COUNT = 13
    const val SPHERE_ID = 12
    /** Esfera II: la esfera gira con el dedo (prototipo). */
    const val SPHERE2_ID = 13

    private const val T = Config.WALL_THICKNESS
    private val H = Config.WALL_HEIGHT

    private val FLOOR_COLOR = floatArrayOf(0.17f, 0.17f, 0.28f, 1f)
    private val WALL_COLOR = floatArrayOf(0.45f, 0.40f, 0.86f, 1f)
    private val BLOCK_COLOR = floatArrayOf(0.55f, 0.42f, 0.78f, 1f)

    private val cache = arrayOfNulls<Level>(COUNT + 1)

    /** Devuelve el nivel [id] (1..3). Ids fuera de rango se acotan. */
    fun nivel(id: Int): Level {
        val i = id.coerceIn(1, COUNT)
        var l = cache[i]
        if (l == null) {
            l = when (i) {
                1 -> buildLevel1()
                2 -> buildLevel2()
                3 -> buildLevel3()
                4 -> MapParser.build(4, MapLevels.NAME_4, MapLevels.MAP_4)
                5 -> MapParser.build(5, MapLevels.NAME_5, MapLevels.MAP_5)
                6 -> MapParser.build(6, MapLevels.NAME_6, MapLevels.MAP_6)
                7 -> MapParser.build(7, MapLevels.NAME_7, MapLevels.MAP_7)
                8 -> MapParser.build(8, MapLevels.NAME_8, MapLevels.MAP_8)
                9 -> MapParser.build(9, MapLevels.NAME_9, MapLevels.MAP_9)
                10 -> MapParser.build(10, MapLevels.NAME_10, MapLevels.MAP_10)
                11 -> MapParser.build(11, MapLevels.NAME_11, MapLevels.MAP_11)
                12 -> SphereGenerator.generate().level
                else -> SphereTwoGenerator.generate().level
            }
            cache[i] = l
        }
        return l
    }

    /** Nombre del nivel sin construirlo (la gran esfera tarda unos 150 ms en generarse). */
    fun nameOf(id: Int): String = when (id) {
        SPHERE_ID -> SphereGenerator.NAME
        SPHERE2_ID -> SphereTwoGenerator.NAME
        else -> nivel(id).name
    }

    fun bestTimeKey(id: Int): String = "best_time_$id"

    // ------------------------------------------------------------------
    private class Builder(val innerW: Float, val innerH: Float) {
        val boxes = ArrayList<Box>()
        val hx = innerW / 2f
        val hz = innerH / 2f

        init {
            // Suelo (cara superior en y = 0)
            boxes.add(Box(Vec3(-hx - T, -0.5f, -hz - T), Vec3(hx + T, 0f, hz + T), FLOOR_COLOR, true))
            // Perímetro (fuera del área útil, que es exactamente innerW x innerH)
            rect(-hx - T, -hz - T, hx + T, -hz)          // arriba
            rect(-hx - T, hz, hx + T, hz + T)            // abajo
            rect(-hx - T, -hz, -hx, hz)                  // izquierda
            rect(hx, -hz, hx + T, hz)                    // derecha
        }

        fun rect(x0: Float, z0: Float, x1: Float, z1: Float, color: FloatArray = WALL_COLOR) {
            boxes.add(
                Box(
                    Vec3(minOf(x0, x1), 0f, minOf(z0, z1)),
                    Vec3(maxOf(x0, x1), H, maxOf(z0, z1)),
                    color
                )
            )
        }

        /** Pared horizontal (a lo largo de X) centrada en z, entre xa y xb. */
        fun hWall(z: Float, xa: Float, xb: Float) = rect(xa, z - T / 2f, xb, z + T / 2f)

        /** Pared vertical (a lo largo de Z) centrada en x, entre za y zb. */
        fun vWall(x: Float, za: Float, zb: Float) = rect(x - T / 2f, za, x + T / 2f, zb)

        fun build(id: Int, name: String, start: Vec3, goal: Vec3, goalRadius: Float): Level =
            Level(
                id = id, name = name, boxes = boxes.toList(),
                start = start, goal = goal, goalRadius = goalRadius,
                bestTimeKey = bestTimeKey(id),
                minX = -hx - T, maxX = hx + T, minZ = -hz - T, maxZ = hz + T
            )
    }

    // ------------------------------------------------------------------
    /** Nivel 1 "Primer paso": habitación con un bloque central. Meta abajo a la derecha. */
    private fun buildLevel1(): Level {
        val b = Builder(6.0f, 3.6f)
        b.rect(-0.5f, -0.5f, 0.5f, 0.5f, BLOCK_COLOR)
        return b.build(
            1, "Primer paso",
            start = Vec3(-2.2f, Config.BALL_RADIUS, -0.9f),
            goal = Vec3(2.3f, 0f, 1.2f),
            goalRadius = 0.30f
        )
    }

    /**
     * Nivel 2 "Serpiente": 5 columnas separadas por 4 paredes verticales alternadas.
     * Cada pared deja un hueco de 0.6 m en un extremo. Salida abajo a la derecha,
     * meta arriba a la izquierda.
     */
    private fun buildLevel2(): Level {
        val b = Builder(6.8f, 3.6f)
        val z0 = -b.hz
        val z1 = b.hz
        val gap = 0.6f
        // x de los centros de pared (columnas de 1.2 m libres)
        b.vWall(-2.1f, z0, z1 - gap)      // hueco abajo
        b.vWall(-0.7f, z0 + gap, z1)      // hueco arriba
        b.vWall(0.7f, z0, z1 - gap)       // hueco abajo
        b.vWall(2.1f, z0 + gap, z1)       // hueco arriba
        return b.build(
            2, "Serpiente",
            start = Vec3(2.8f, Config.BALL_RADIUS, 1.2f),
            goal = Vec3(-2.8f, 0f, -1.2f),
            goalRadius = 0.30f
        )
    }

    /**
     * Nivel 3 "Cruce": cuadrícula 3x3 de salas de 2.0 x 1.0 m con aberturas de 0.5 m.
     * Recorrido en espiral desde la sala superior izquierda hasta la sala central.
     */
    private fun buildLevel3(): Level {
        val b = Builder(6.4f, 3.4f)
        // Columnas: [-3.2,-1.2] [-1.0,1.0] [1.2,3.2]   (paredes en x=-1.1 y x=1.1)
        // Filas:    [-1.7,-0.7] [-0.5,0.5] [0.7,1.7]   (paredes en z=-0.6 y z=0.6)
        val xa = -1.1f
        val xb = 1.1f
        val za = -0.6f
        val zb = 0.6f
        val left = -b.hx
        val right = b.hx

        // Paredes horizontales completas con aberturas de 0.5 m
        // za: abierta sólo en la columna derecha, x in [1.4, 1.9]
        b.hWall(za, left, 1.4f)
        b.hWall(za, 1.9f, right)
        // zb: abierta en la columna izquierda x in [-3.2,-2.7] y en la derecha x in [2.7,3.2]
        b.hWall(zb, -2.7f, 2.7f)

        // Paredes verticales por filas (entre las horizontales: z in [-1.7,-0.7], [-0.5,0.5], [0.7,1.7])
        // fila 0: xa abierta z in [-1.7,-1.2]; xb abierta z in [-1.2,-0.7]
        b.vWall(xa, -1.2f, -0.7f)
        b.vWall(xb, -1.7f, -1.2f)
        // fila 1: xa abierta centrada z in [-0.25,0.25]; xb cerrada
        b.vWall(xa, -0.5f, -0.25f)
        b.vWall(xa, 0.25f, 0.5f)
        b.vWall(xb, -0.5f, 0.5f)
        // fila 2: xa abierta z in [0.7,1.2]; xb abierta z in [1.2,1.7]
        b.vWall(xa, 1.2f, 1.7f)
        b.vWall(xb, 0.7f, 1.2f)

        return b.build(
            3, "Cruce",
            start = Vec3(-2.5f, Config.BALL_RADIUS, -1.2f),
            goal = Vec3(0f, 0f, 0f),
            goalRadius = 0.30f
        )
    }
}
