package com.laberinto3d.juego.game.render

import android.opengl.GLES30
import kotlin.math.max

/** Sombra de mancha (blob shadow) bajo la bola: quad en el suelo con caída radial. Coste casi nulo. */
class ShadowRenderer {
    private var shader: Shader? = null
    private var quad: Mesh? = null

    fun create() {
        shader = Shader("shadow", Shaders.QUAD_VERT, Shaders.SHADOW_FRAG)
        quad = Mesh.quad()
    }

    /** [bx],[bz]: posición de la bola; [height]: altura de la base de la bola sobre el suelo. */
    fun draw(cam: Camera, bx: Float, floorY: Float, bz: Float, ballRadius: Float, height: Float) {
        val s = shader ?: return
        val q = quad ?: return
        val alpha = 0.42f * max(0f, 1f - height / 0.9f)
        if (alpha <= 0.01f) return
        s.use()
        s.setMat4("uViewProj", cam.viewProj)
        s.setVec4("uCenterRadius", bx, floorY + 0.004f, bz, ballRadius * 1.35f)
        s.setVec3("uAxisU", 1f, 0f, 0f)
        s.setVec3("uAxisV", 0f, 0f, 1f)
        s.setFloat("uAlpha", alpha)
        q.draw(GLES30.GL_TRIANGLE_STRIP)
        GLES30.glBindVertexArray(0)
    }

    fun destroy() {
        shader?.delete(); shader = null
        quad?.delete(); quad = null
    }
}
