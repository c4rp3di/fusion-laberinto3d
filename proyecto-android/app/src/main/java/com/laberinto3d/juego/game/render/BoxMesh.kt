package com.laberinto3d.juego.game.render

/** Cubo unitario centrado en el origen (de -0.5 a +0.5): 36 vértices con posición y normal. */
object BoxMesh {
    /** Datos intercalados: px py pz nx ny nz, 36 vértices. Útil también para pruebas sin GL. */
    fun vertexData(): FloatArray {
        val out = ArrayList<Float>(36 * 6)
        fun face(nx: Float, ny: Float, nz: Float, vararg p: Float) {
            // p = 4 vértices (12 floats) en orden antihorario visto desde fuera
            fun v(i: Int) {
                out.add(p[i * 3]); out.add(p[i * 3 + 1]); out.add(p[i * 3 + 2])
                out.add(nx); out.add(ny); out.add(nz)
            }
            v(0); v(1); v(2)
            v(0); v(2); v(3)
        }
        val h = 0.5f
        face(1f, 0f, 0f, h, -h, -h, h, h, -h, h, h, h, h, -h, h)       // +X
        face(-1f, 0f, 0f, -h, -h, h, -h, h, h, -h, h, -h, -h, -h, -h)  // -X
        face(0f, 1f, 0f, -h, h, -h, -h, h, h, h, h, h, h, h, -h)       // +Y
        face(0f, -1f, 0f, -h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h)  // -Y
        face(0f, 0f, 1f, -h, -h, h, h, -h, h, h, h, h, -h, h, h)       // +Z
        face(0f, 0f, -1f, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h)  // -Z
        return out.toFloatArray()
    }

    fun create(): Mesh = Mesh(
        vertexData(), 6,
        listOf(Attrib(0, 3, 0), Attrib(1, 3, 3))
    )
}
