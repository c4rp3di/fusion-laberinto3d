package com.laberinto3d.juego.game.levels

import com.laberinto3d.juego.game.physics.Box
import com.laberinto3d.juego.game.physics.Mover
import com.laberinto3d.juego.game.physics.Ramp
import com.laberinto3d.juego.game.physics.Vec3
import com.laberinto3d.juego.game.physics.World

/** Plantilla de plataforma móvil (se instancia en cada partida, porque su posición cambia). */
class MoverDef(
    val min: Vec3, val max: Vec3, val color: FloatArray,
    val dx: Float, val dy: Float, val dz: Float, val period: Float, val phase: Float
)

/**
 * Definición inmutable de un nivel. Las coordenadas están en metros, con el centro
 * del tablero en el origen, X hacia la derecha de la pantalla y Z hacia el jugador
 * (arriba de la pantalla = -Z). El suelo tiene su cara superior en y = 0.
 */
class Level(
    val id: Int,
    val name: String,
    /** Suelo + perímetro + paredes interiores. */
    val boxes: List<Box>,
    /** Posición inicial del CENTRO de la bola. */
    val start: Vec3,
    /** Centro de la meta (y = 0, a ras de suelo). */
    val goal: Vec3,
    val goalRadius: Float,
    /** Clave de preferencias del mejor tiempo. */
    val bestTimeKey: String,
    /** Rectángulo exterior del tablero (incluye perímetro). */
    val minX: Float,
    val maxX: Float,
    val minZ: Float,
    val maxZ: Float,
    /** Checkpoints en orden (y = cara superior del suelo). */
    val checkpoints: List<Vec3> = emptyList(),
    val topY: Float = 0.2f,
    val tris: FloatArray = FloatArray(0),
    val triNormals: FloatArray = FloatArray(0),
    val ramps: List<Ramp> = emptyList(),
    val moverDefs: List<MoverDef> = emptyList(),
    val triSurf: IntArray = IntArray(0),
    val triColors: FloatArray = FloatArray(0),
    val shellRadius: Float = 0f,
    val pathPts: FloatArray = FloatArray(0),
    val fallDrop: Float = 0f,
    val offPathDist: Float = 0f,
    val rotating: Boolean = false,
    val gravityScale: Float = 1f,
    val frameT: FloatArray = FloatArray(0)
) {
    fun buildWorld(): World {
        val movers = moverDefs.map {
            Mover(Box(it.min.copy(), it.max.copy(), it.color, true), it.dx, it.dy, it.dz, it.period, it.phase)
        }
        return World(
        boxes = movers.map { it.box } + boxes,
        floorY = 0f,
        goalPosition = goal,
        goalRadius = goalRadius,
        minX = minX, maxX = maxX, minZ = minZ, maxZ = maxZ,
        checkpoints = checkpoints, topY = topY,
        tris = tris, triNormals = triNormals, ramps = ramps, movers = movers,
        triSurf = triSurf, triColors = triColors, shellRadius = shellRadius, pathPts = pathPts, fallDrop = fallDrop, offPathDist = offPathDist,
        rotating = rotating, gravityScale = gravityScale, frameT = frameT
        )
    }
}
