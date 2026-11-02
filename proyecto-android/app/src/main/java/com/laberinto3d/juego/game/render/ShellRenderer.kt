package com.laberinto3d.juego.game.render

import android.opengl.GLES30

/** Cáscara transparente de la gran esfera (sólo se ven las caras lejanas, como un cristal por dentro). */
class ShellRenderer {
    private var shader: Shader? = null
    private var mesh: Mesh? = null

    fun create() {
        shader = Shader("shell", Shaders.SHELL_VERT, Shaders.SHELL_FRAG)
        val d = SphereMesh.generate(96, 48, false)
        mesh = Mesh(d.vertices, d.strideFloats, listOf(Attrib(0, 3, 0)), d.indices)
    }

    fun draw(cam: Camera, radius: Float) {
        val s = shader ?: return
        val m = mesh ?: return
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_FRONT)
        s.use()
        s.setMat4("uViewProj", cam.viewProj)
        s.setFloat("uRadius", radius)
        s.setMat4("uModel", SceneParams.model)
        m.draw()
        GLES30.glBindVertexArray(0)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun destroy() {
        shader?.delete(); shader = null
        mesh?.delete(); mesh = null
    }
}
