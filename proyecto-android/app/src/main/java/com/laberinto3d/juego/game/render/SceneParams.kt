package com.laberinto3d.juego.game.render

/**
 * Parámetros de escena compartidos por los renderers de mundo (cajas y malla): corte de lo que tapa a la
 * bola (gran esfera) y sombreado lateral. Lo escribe y lo lee sólo el hilo GL.
 */
object SceneParams {
    @JvmField var cutOn = false
    @JvmField var ballX = 0f
    @JvmField var ballY = 0f
    @JvmField var ballZ = 0f
    @JvmField var dirX = 0f
    @JvmField var dirY = 1f
    @JvmField var dirZ = 0f
    @JvmField var cutRadius = 2.4f
    /** Sombreado lateral: (lo, hi) en metros de altura; hi <= lo = constante. */
    @JvmField var sideLo = -0.4f
    @JvmField var sideHi = 0.3f

    /** Corte: sólo lo que está a más de cutTt metros hacia la cámara y más de cutY por encima de la bola. */
    @JvmField var cutTt = 0.7f
    @JvmField var cutY = 0.35f
    /** Giro cuerpo -> mundo de la esfera giratoria (identidad si no hay). */
    @JvmField val model = FloatArray(16).also { Matrix.identity(it) }

    fun apply(s: Shader) {
        s.setVec2("uCutLim", cutTt, cutY)
        s.setVec4("uCutBall", ballX, ballY, ballZ, if (cutOn) cutRadius * cutRadius else 0f)
        s.setVec3("uCutDir", dirX, dirY, dirZ)
        s.setVec2("uSide", sideLo, sideHi)
    }
}
