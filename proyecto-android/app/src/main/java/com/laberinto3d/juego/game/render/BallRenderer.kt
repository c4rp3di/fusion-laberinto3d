package com.laberinto3d.juego.game.render

import android.opengl.GLES30
import com.laberinto3d.juego.game.physics.Ball
import com.laberinto3d.juego.game.physics.Quat
import com.laberinto3d.juego.game.physics.SphereState
import com.laberinto3d.juego.game.physics.Vec3

/** Dibuja la bola con textura de mármol e iluminación Blinn-Phong. Posición y giro se interpolan. */
class BallRenderer {
    private var shader: Shader? = null
    private var mesh: Mesh? = null
    private var texture = 0
    private val model = FloatArray(16)
    private val rotM = FloatArray(16)
    private val q = Quat()

    /** Posición interpolada del último draw (la usa la sombra). */
    var drawX = 0f; private set
    var drawY = 0f; private set
    var drawZ = 0f; private set

    fun create() {
        shader = Shader("ball", Shaders.BALL_VERT, Shaders.BALL_FRAG)
        mesh = TexturedSphereMesh.create()
        texture = ProceduralMarble.generate()
    }

    /** Calcula la posición interpolada (sin dibujar). */
    private val wv = Vec3()
    private var sph: SphereState? = null

    fun interpolate(ball: Ball, alpha: Float, sphere: SphereState? = null) {
        sph = sphere
        drawX = ball.prevPosition.x + (ball.position.x - ball.prevPosition.x) * alpha
        drawY = ball.prevPosition.y + (ball.position.y - ball.prevPosition.y) * alpha
        drawZ = ball.prevPosition.z + (ball.position.z - ball.prevPosition.z) * alpha
        if (sphere != null) {
            // la bola se simula en el sistema de la esfera: al mundo con el giro actual
            sphere.toWorld(drawX, drawY, drawZ, wv)
            drawX = wv.x; drawY = wv.y; drawZ = wv.z
        }
    }

    private val qw = Quat()

    fun draw(ball: Ball, alpha: Float, cam: Camera, lx: Float, ly: Float, lz: Float, ambient: Float) {
        val s = shader ?: return
        val m = mesh ?: return
        interpolate(ball, alpha, sph)
        q.setNlerp(ball.prevRotation, ball.rotation, alpha)
        val sp = sph
        if (sp != null) { qw.setMul(sp.q, q); qw.toMatrix(rotM) } else q.toMatrix(rotM)
        Matrix.setTrs(model, drawX, drawY, drawZ, rotM, ball.radius)
        s.use()
        s.setMat4("uViewProj", cam.viewProj)
        s.setMat4("uModel", model)
        s.setVec3("uLightDir", lx, ly, lz)
        s.setVec3("uCamPos", cam.eyeX, cam.eyeY, cam.eyeZ)
        s.setFloat("uAmbient", ambient)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        s.setInt("uTex", 0)
        m.draw()
        GLES30.glBindVertexArray(0)
    }

    fun destroy() {
        shader?.delete(); shader = null
        mesh?.delete(); mesh = null
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        texture = 0
    }
}
