package com.laberinto3d.juego.game.physics

/**
 * Caja alineada a los ejes del mundo.
 * Convención: min.x < max.x, min.y < max.y, min.z < max.z.
 * Las paredes son cajas altas y finas; el suelo es una caja plana ([isFloor] = true).
 * Las cajas son inmutables una vez construidas (se comparten entre nivel, mundo y render).
 */
class Box(
    @JvmField val min: Vec3,
    @JvmField val max: Vec3,
    /** RGBA para el render. */
    @JvmField val color: FloatArray,
    @JvmField val isFloor: Boolean = false,
    /** Superficie (sólo suelos): 0 normal, 1 hielo, 2 arena. */
    @JvmField val surface: Int = 0
) {
    /** true para plataformas móviles: su posición cambia en cada paso y [vel] es su velocidad (m/s). */
    @JvmField var dynamic: Boolean = false
    @JvmField val vel = Vec3()
    /** Cambio de velocidad horizontal de la plataforma en el último paso (lo hereda la bola que va encima). */
    @JvmField var dvx: Float = 0f
    @JvmField var dvz: Float = 0f

    init {
        require(min.x < max.x && min.y < max.y && min.z < max.z) { "Box inválida: min=$min max=$max" }
        require(color.size == 4) { "color debe tener 4 componentes" }
    }

    val centerX: Float get() = (min.x + max.x) * 0.5f
    val centerY: Float get() = (min.y + max.y) * 0.5f
    val centerZ: Float get() = (min.z + max.z) * 0.5f
    val sizeX: Float get() = max.x - min.x
    val sizeY: Float get() = max.y - min.y
    val sizeZ: Float get() = max.z - min.z

    override fun toString(): String = "Box($min .. $max${if (isFloor) " suelo" else ""})"
}
