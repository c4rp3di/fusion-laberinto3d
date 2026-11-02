package com.laberinto3d.juego.game.render

import android.opengl.GLES30
import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.diag.GameLog
import com.laberinto3d.juego.game.physics.World

/**
 * Dibuja los triángulos del mundo (rampas) con el mismo fragment shader que las cajas.
 * Vértice = pos(3) + normal(3) + color(4). El color sigue la altura como los suelos planos.
 */
class TriMeshRenderer {
    private var shader: Shader? = null
    private var mesh: Mesh? = null

    fun create() {
        shader = Shader("mesh", Shaders.MESH_VERT, Shaders.WORLD_FRAG)
        mesh = null
    }

    fun setWorld(w: World) {
        mesh?.delete(); mesh = null
        val n = w.triCount
        if (n == 0) return
        val hasColors = w.triColors.size == n * 4
        val data = FloatArray(n * 3 * 10)
        var o = 0
        for (t in 0 until n) {
            val nx = w.triNormals[t * 3]; val ny = w.triNormals[t * 3 + 1]; val nz = w.triNormals[t * 3 + 2]
            for (v in 0 until 3) {
                val x = w.tris[t * 9 + v * 3]; val y = w.tris[t * 9 + v * 3 + 1]; val z = w.tris[t * 9 + v * 3 + 2]
                data[o++] = x; data[o++] = y; data[o++] = z
                data[o++] = nx; data[o++] = ny; data[o++] = nz
                if (hasColors) {
                    data[o++] = w.triColors[t * 4]; data[o++] = w.triColors[t * 4 + 1]
                    data[o++] = w.triColors[t * 4 + 2]; data[o++] = w.triColors[t * 4 + 3]
                } else {
                    val l = 0.04f * (y / Config.HEIGHT_STEP)
                    data[o++] = 0.17f + l; data[o++] = 0.17f + l; data[o++] = 0.28f + l * 1.2f
                    data[o++] = if (ny > 0.5f) 0.5f else 1.0f
                }
            }
        }
        mesh = Mesh(data, 10, listOf(Attrib(0, 3, 0), Attrib(1, 3, 3), Attrib(6, 4, 6)))
        GameLog.i("World", "Malla de rampas subida: $n triángulos")
    }

    fun draw(cam: Camera, lx: Float, ly: Float, lz: Float, ambient: Float) {
        val s = shader ?: return
        val m = mesh ?: return
        s.use()
        s.setMat4("uViewProj", cam.viewProj)
        s.setVec3("uLightDir", lx, ly, lz)
        s.setFloat("uAmbient", ambient)
        SceneParams.apply(s)
        s.setMat4("uModel", SceneParams.model)
        m.draw()
        GLES30.glBindVertexArray(0)
    }

    fun destroy() {
        shader?.delete(); shader = null
        mesh?.delete(); mesh = null
    }
}
