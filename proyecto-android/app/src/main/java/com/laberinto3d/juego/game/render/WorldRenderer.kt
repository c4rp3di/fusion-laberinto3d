package com.laberinto3d.juego.game.render

import android.opengl.GLES30
import com.laberinto3d.juego.game.diag.GameLog
import com.laberinto3d.juego.game.physics.World

/**
 * Dibuja todas las cajas del nivel con un solo draw call instanciado.
 * Instancia = mat4 (16 floats, locations 2..5) + color rgba (4 floats, location 6).
 * El alfa del color codifica el material: 0.5 suelo con rejilla, 1.0 pared.
 * Las cajas son estáticas: el buffer de instancias se sube una sola vez por nivel.
 */
class WorldRenderer {
    private var shader: Shader? = null
    private var box: Mesh? = null
    private var instanceVbo = 0
    private var count = 0

    fun create() {
        shader = Shader("world", Shaders.WORLD_VERT, Shaders.WORLD_FRAG)
        box = BoxMesh.create()
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        instanceVbo = ids[0]
        count = 0
        GlUtil.check("WorldRenderer.create")
    }

    /** Sube las instancias del mundo [w]. Hay que llamarlo tras [create] y en cada cambio de nivel. */
    fun setWorld(w: World) {
        val mesh = box ?: return
        val n = w.boxes.size
        val data = FloatArray(n * 20)
        val m = FloatArray(16)
        for (i in 0 until n) {
            val b = w.boxes[i]
            Matrix.setTranslateScale(m, b.centerX, b.centerY, b.centerZ, b.sizeX, b.sizeY, b.sizeZ)
            System.arraycopy(m, 0, data, i * 20, 16)
            data[i * 20 + 16] = b.color[0]
            data[i * 20 + 17] = b.color[1]
            data[i * 20 + 18] = b.color[2]
            data[i * 20 + 19] = if (b.isFloor) 0.5f else 1.0f
        }
        mesh.bind()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, GlUtil.floatBuffer(data), GLES30.GL_STATIC_DRAW)
        val stride = 20 * 4
        for (c in 0 until 4) {
            val loc = 2 + c
            GLES30.glEnableVertexAttribArray(loc)
            GLES30.glVertexAttribPointer(loc, 4, GLES30.GL_FLOAT, false, stride, c * 16)
            GLES30.glVertexAttribDivisor(loc, 1)
        }
        GLES30.glEnableVertexAttribArray(6)
        GLES30.glVertexAttribPointer(6, 4, GLES30.GL_FLOAT, false, stride, 16 * 4)
        GLES30.glVertexAttribDivisor(6, 1)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        count = n
        GlUtil.check("WorldRenderer.setWorld")
        GameLog.i("World", "Instancias subidas: $n cajas")
    }

    private var moverData = FloatArray(0)

    /** Sube de nuevo las instancias de las plataformas móviles (las primeras cajas del mundo). */
    fun updateMovers(w: World) {
        val m = w.movers.size
        if (m == 0 || instanceVbo == 0 || count < m) return
        if (moverData.size != m * 20) moverData = FloatArray(m * 20)
        val mat = FloatArray(16)
        for (i in 0 until m) {
            val b = w.boxes[i]
            Matrix.setTranslateScale(mat, b.centerX, b.centerY, b.centerZ, b.sizeX, b.sizeY, b.sizeZ)
            System.arraycopy(mat, 0, moverData, i * 20, 16)
            moverData[i * 20 + 16] = b.color[0]; moverData[i * 20 + 17] = b.color[1]
            moverData[i * 20 + 18] = b.color[2]; moverData[i * 20 + 19] = 0.5f
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, moverData.size * 4, GlUtil.floatBuffer(moverData))
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw(cam: Camera, lightX: Float, lightY: Float, lightZ: Float, ambient: Float) {
        val s = shader ?: return
        val mesh = box ?: return
        if (count == 0) return
        s.use()
        s.setMat4("uViewProj", cam.viewProj)
        s.setVec3("uLightDir", lightX, lightY, lightZ)
        s.setFloat("uAmbient", ambient)
        SceneParams.apply(s)
        mesh.drawInstanced(count)
        GLES30.glBindVertexArray(0)
    }

    fun destroy() {
        shader?.delete(); shader = null
        box?.delete(); box = null
        if (instanceVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(instanceVbo), 0)
        instanceVbo = 0
        count = 0
    }
}
