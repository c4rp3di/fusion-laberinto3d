package com.laberinto3d.juego.game.input

/**
 * Arrastre con un dedo para girar la esfera (Esfera II). El hilo UI acumula ángulos (rad, ejes del mundo);
 * el hilo GL los consume cada frame y los convierte en velocidad angular.
 */
object SphereInput {
    @Volatile var touching = false
    private var ax = 0f
    private var ay = 0f
    private var az = 0f

    @Synchronized fun add(x: Float, y: Float, z: Float) { ax += x; ay += y; az += z }

    @Synchronized fun take(out: FloatArray) {
        out[0] = ax; out[1] = ay; out[2] = az
        ax = 0f; ay = 0f; az = 0f
    }

    @Synchronized fun clear() { ax = 0f; ay = 0f; az = 0f; touching = false }
}
