package com.laberinto3d.juego.game.render

import android.opengl.GLES30
import com.laberinto3d.juego.game.diag.GameLog

/**
 * Compila y enlaza un programa GLSL. Si algo falla lanza RuntimeException con el info log
 * completo (y lo escribe en GameLog), de forma que aparece en el archivo de crash.
 */
class Shader(val name: String, vertexSrc: String, fragmentSrc: String) {
    val program: Int
    private val uniforms = HashMap<String, Int>()
    private val missing = HashSet<String>()

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSrc, "$name.vert")
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc, "$name.frag")
        val p = GLES30.glCreateProgram()
        if (p == 0) throw RuntimeException("Shader $name: glCreateProgram devolvió 0")
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        val status = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            GLES30.glDeleteProgram(p)
            GameLog.e("Shader", "Enlace de $name falló: $log")
            throw RuntimeException("Shader $name: enlace falló: $log")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        program = p
        GameLog.i("Shader", "Programa '$name' listo (id=$p)")
    }

    private fun compile(type: Int, src: String, label: String): Int {
        val s = GLES30.glCreateShader(type)
        if (s == 0) throw RuntimeException("Shader $label: glCreateShader devolvió 0")
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val status = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            GLES30.glDeleteShader(s)
            GameLog.e("Shader", "Compilación de $label falló: $log")
            throw RuntimeException("Shader $label: compilación falló: $log")
        }
        return s
    }

    fun use() {
        GLES30.glUseProgram(program)
    }

    private fun loc(u: String): Int {
        var l = uniforms[u]
        if (l == null) {
            l = GLES30.glGetUniformLocation(program, u)
            uniforms[u] = l
            if (l < 0 && missing.add(u)) GameLog.w("Shader", "$name: uniform '$u' no encontrado (¿optimizado?)")
        }
        return l
    }

    fun setMat4(u: String, m: FloatArray) {
        GLES30.glUniformMatrix4fv(loc(u), 1, false, m, 0)
    }

    fun setVec2(u: String, x: Float, y: Float) {
        GLES30.glUniform2f(loc(u), x, y)
    }

    fun setVec3(u: String, x: Float, y: Float, z: Float) {
        GLES30.glUniform3f(loc(u), x, y, z)
    }

    fun setVec4(u: String, x: Float, y: Float, z: Float, w: Float) {
        GLES30.glUniform4f(loc(u), x, y, z, w)
    }

    fun setFloat(u: String, v: Float) {
        GLES30.glUniform1f(loc(u), v)
    }

    fun setInt(u: String, v: Int) {
        GLES30.glUniform1i(loc(u), v)
    }

    fun delete() {
        GLES30.glDeleteProgram(program)
    }
}
