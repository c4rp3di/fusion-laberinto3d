package com.laberinto3d.juego.game.audio

/** Identificadores lógicos de los efectos de sonido. */
object SoundIds {
    const val BOUNCE = 0
    const val GOAL = 1
    const val TAP = 2
    const val CHECKPOINT = 3
    const val FALL = 4
    const val COUNT = 5

    val FILE_NAMES = arrayOf("sfx_bounce.wav", "sfx_goal.wav", "sfx_tap.wav", "sfx_checkpoint.wav", "sfx_fall.wav")
    val LABELS = arrayOf("rebote", "meta", "toque", "checkpoint", "caída")
}
