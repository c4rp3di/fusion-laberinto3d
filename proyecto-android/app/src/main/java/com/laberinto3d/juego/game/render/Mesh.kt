package com.laberinto3d.juego.game.render

import android.opengl.GLES30
import com.laberinto3d.juego.game.core.Telemetry
import com.laberinto3d.juego.game.diag.GameLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/** Utilidades de buffers y errores de OpenGL. */
object GlUtil {
    fun floatBuffer(a: FloatArray): FloatBuffer {
        val b = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        b.put(a)
        b.position(0)
        return b
    }

    fun shortBuffer(a: ShortArray): ShortBuffer {
        val b = ByteBuffer.allocateDirect(a.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        b.put(a)
        b.position(0)
        return b
    }

    private var reported = 0

    /** Registra (máximo 20 veces) los errores de GL pendientes. */
    fun check(tag: String) {
        var e = GLES30.glGetError()
        while (e != GLES30.GL_NO_ERROR) {
            Telemetry.glErrors++
            if (reported < 20) {
                reported++
                GameLog.e("GL", "glGetError=0x${Integer.toHexString(e)} en $tag")
            }
            e = GLES30.glGetError()
        }
    }
}

/** Descripción de un atributo de vértice: location, nº de floats y desplazamiento en floats. */
class Attrib(val location: Int, val size: Int, val offsetFloats: Int)

/**
 * VAO + VBO (+ EBO opcional). Vértices intercalados de [strideFloats] floats.
 * Se construye en el hilo GL; hay que recrearlo si se pierde el contexto.
 */
class Mesh(
    vertices: FloatArray,
    private val strideFloats: Int,
    attribs: List<Attrib>,
    indices: ShortArray? = null
) {
    val vao: Int
    val vbo: Int
    private val ebo: Int
    val vertexCount: Int = vertices.size / strideFloats
    val indexCount: Int = indices?.size ?: 0

    init {
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, GlUtil.floatBuffer(vertices), GLES30.GL_STATIC_DRAW)
        for (a in attribs) {
            GLES30.glEnableVertexAttribArray(a.location)
            GLES30.glVertexAttribPointer(a.location, a.size, GLES30.GL_FLOAT, false, strideFloats * 4, a.offsetFloats * 4)
        }
        if (indices != null) {
            GLES30.glGenBuffers(1, ids, 0)
            ebo = ids[0]
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, GlUtil.shortBuffer(indices), GLES30.GL_STATIC_DRAW)
        } else {
            ebo = 0
        }
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GlUtil.check("Mesh.init")
    }

    fun bind() {
        GLES30.glBindVertexArray(vao)
    }

    fun draw(mode: Int = GLES30.GL_TRIANGLES) {
        GLES30.glBindVertexArray(vao)
        if (ebo != 0) GLES30.glDrawElements(mode, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        else GLES30.glDrawArrays(mode, 0, vertexCount)
    }

    fun drawInstanced(count: Int, mode: Int = GLES30.GL_TRIANGLES) {
        GLES30.glBindVertexArray(vao)
        if (ebo != 0) GLES30.glDrawElementsInstanced(mode, indexCount, GLES30.GL_UNSIGNED_SHORT, 0, count)
        else GLES30.glDrawArraysInstanced(mode, 0, vertexCount, count)
    }

    fun delete() {
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        val bufs = if (ebo != 0) intArrayOf(vbo, ebo) else intArrayOf(vbo)
        GLES30.glDeleteBuffers(bufs.size, bufs, 0)
    }

    companion object {
        /** Quad de 4 vértices (TRIANGLE_STRIP) en [-1,1]^2 para sombra y meta. */
        fun quad(): Mesh = Mesh(
            floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f),
            2,
            listOf(Attrib(0, 2, 0))
        )
    }
}
