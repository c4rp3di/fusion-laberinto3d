package com.laberinto3d.juego.game.render

import android.opengl.GLES30

/** Marca de la meta: disco con anillo que pulsa suavemente. No tiene colisión. */
class GoalRenderer {
    private var shader: Shader? = null
    private var quad: Mesh? = null

    fun create() {
        shader = Shader("goal", Shaders.QUAD_VERT, Shaders.GOAL_FRAG)
        quad = Mesh.quad()
    }

    /** [gy]: cara superior del suelo bajo la marca. [a],[b]: colores del relleno y del anillo; [pulse] 0..1. */
    fun draw(cam: Camera, gx: Float, gy: Float, gz: Float, radius: Float, timeSec: Float,
             a: FloatArray = GOAL_A, b: FloatArray = GOAL_B, pulse: Float = 1f,
             ux: Float = 1f, uy: Float = 0f, uz: Float = 0f, vx: Float = 0f, vy: Float = 0f, vz: Float = 1f) {
        val s = shader ?: return
        val q = quad ?: return
        s.use()
        s.setMat4("uViewProj", cam.viewProj)
        s.setVec4("uCenterRadius", gx, gy + 0.002f, gz, radius)
        s.setVec3("uAxisU", ux, uy, uz)
        s.setVec3("uAxisV", vx, vy, vz)
        s.setFloat("uTime", timeSec)
        s.setVec3("uColA", a[0], a[1], a[2])
        s.setVec3("uColB", b[0], b[1], b[2])
        s.setFloat("uPulse", pulse)
        q.draw(GLES30.GL_TRIANGLE_STRIP)
        GLES30.glBindVertexArray(0)
    }

    companion object {
        val GOAL_A = floatArrayOf(0.50f, 0.88f, 0.74f)
        val GOAL_B = floatArrayOf(0.85f, 1.0f, 0.92f)
        /** Checkpoint pendiente (ámbar) y alcanzado (azul claro). */
        val CP_PENDING_A = floatArrayOf(0.95f, 0.70f, 0.25f)
        val CP_PENDING_B = floatArrayOf(1.0f, 0.90f, 0.60f)
        val CP_DONE_A = floatArrayOf(0.35f, 0.55f, 0.95f)
        val CP_DONE_B = floatArrayOf(0.70f, 0.82f, 1.0f)
    }

    fun destroy() {
        shader?.delete(); shader = null
        quad?.delete(); quad = null
    }
}
