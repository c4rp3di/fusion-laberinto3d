package com.laberinto3d.juego.game.physics

import com.laberinto3d.juego.game.core.Config

/**
 * Cuerpo de la bola (masa 1). Sólo [PhysicsEngine] muta su estado.
 * [prevPosition] / [prevRotation] guardan el estado del paso anterior para que el
 * render pueda interpolar y ver movimiento suave a cualquier tasa de refresco.
 */
class Ball(
    @JvmField val radius: Float = Config.BALL_RADIUS
) {
    @JvmField val position = Vec3()
    @JvmField val velocity = Vec3()
    @JvmField val rotation = Quat()
    @JvmField val prevPosition = Vec3()
    @JvmField val prevRotation = Quat()

    /** Coeficientes iniciales; el motor los refresca desde Tuning en cada paso. */
    @JvmField var restitution = 0.35f
    @JvmField var friction = 0.15f
    @JvmField var linearDamping = 0.6f

    fun placeAt(p: Vec3) {
        position.set(p)
        prevPosition.set(p)
        velocity.set(0f, 0f, 0f)
        rotation.setIdentity()
        prevRotation.setIdentity()
    }

    fun speed(): Float = velocity.length()
}
