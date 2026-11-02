package com.laberinto3d.juego.game.core

import com.laberinto3d.juego.game.levels.Level
import com.laberinto3d.juego.game.physics.Ball
import com.laberinto3d.juego.game.physics.PhysicsEngine
import com.laberinto3d.juego.game.physics.SphereState
import com.laberinto3d.juego.game.physics.World

/** Estados globales del juego (sección 11 del plan). */
enum class Phase { CALIBRATING, PLAYING, PAUSED, WON, LOST, MENU }

/**
 * Estado de una partida: un nivel, su mundo, la bola y el cronómetro.
 * Una sesión nueva sustituye a la anterior al cambiar de nivel o reiniciar; así el hilo GL
 * y el hilo UI nunca comparten un objeto que se esté mutando a la vez.
 */
class Session(
    val level: Level,
    @Volatile var bestMs: Long,
    initialPhase: Phase
) {
    val levelId: Int get() = level.id
    val world: World = level.buildWorld()
    val ball: Ball = Ball()
    val physics: PhysicsEngine = PhysicsEngine(world, ball, level.start)
    /** Esfera que el jugador hace girar (sólo en niveles con [World.rotating]). */
    val sphere: SphereState? = if (world.rotating) SphereState() else null

    /** Estado actual. Sólo se cambia mediante [GameState.transition]. */
    @Volatile var phase: Phase = initialPhase

    /** Tiempo jugado (nanosegundos de física: sólo avanza en PLAYING). */
    @Volatile var elapsedNanos: Long = 0L

    /** Caídas (reapariciones en el último checkpoint o inicio) de esta partida. */
    @Volatile var falls: Int = 0

    /** Checkpoints alcanzados y totales (para el HUD). */
    val checkpointsReached: Int get() = physics.checkpointIndex + 1
    val checkpointsTotal: Int get() = world.checkpoints.size

    /** Resultado de la última victoria. */
    @Volatile var lastResultMs: Long = 0L
    @Volatile var newRecord: Boolean = false

    val elapsedMs: Long get() = elapsedNanos / 1_000_000L

    /** Estado desde el que se llegó a CALIBRATING/MENU (para volver con "atrás"). */
    @Volatile var returnPhase: Phase = Phase.PAUSED
}
