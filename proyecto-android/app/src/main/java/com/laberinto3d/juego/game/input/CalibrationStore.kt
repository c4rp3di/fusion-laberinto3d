package com.laberinto3d.juego.game.input

import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.diag.GameLog
import com.laberinto3d.juego.game.physics.Vec3
import com.laberinto3d.juego.game.storage.Prefs

/** Guarda y recupera el pose neutral del acelerómetro (coordenadas del dispositivo). */
object CalibrationStore {
    @Volatile
    var neutral: Vec3 = Vec3(Config.NEUTRAL_DEFAULT_X, Config.NEUTRAL_DEFAULT_Y, Config.NEUTRAL_DEFAULT_Z)
        private set

    /** Carga el neutral de preferencias (o el valor por defecto). */
    fun load() {
        val n = Prefs.getSensorNeutral()
        neutral = Vec3(n[0], n[1], n[2])
        GameLog.i("Calib", "Neutral cargado: $neutral (guardado=${Prefs.hasSensorNeutral()})")
    }

    /** Captura la lectura filtrada actual como nuevo neutral y reinicia el filtro. */
    fun capture(ctrl: AccelerometerController) {
        val f = ctrl.filtered
        neutral = Vec3(f.x, f.y, f.z)
        Prefs.setSensorNeutral(f.x, f.y, f.z)
        ctrl.resetFilter()
        GameLog.i("Calib", "Calibrado: neutral=$neutral rot=${ctrl.rotation}")
    }
}
