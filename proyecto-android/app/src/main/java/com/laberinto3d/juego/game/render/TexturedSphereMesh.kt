package com.laberinto3d.juego.game.render

import com.laberinto3d.juego.game.core.Config

/** Malla de GPU de la bola: esfera con posición, normal y coordenadas UV (indexada). */
object TexturedSphereMesh {
    fun create(): Mesh {
        val d = SphereMesh.generate(Config.SPHERE_SEGMENTS, Config.SPHERE_RINGS, true)
        return Mesh(
            d.vertices, d.strideFloats,
            listOf(Attrib(0, 3, 0), Attrib(1, 3, 3), Attrib(2, 2, 6)),
            d.indices
        )
    }
}
