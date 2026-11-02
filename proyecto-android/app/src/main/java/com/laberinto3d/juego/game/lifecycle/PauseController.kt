package com.laberinto3d.juego.game.lifecycle

import android.os.SystemClock
import android.widget.Toast
import com.laberinto3d.juego.R
import com.laberinto3d.juego.game.core.Phase

/** Lógica del botón "atrás" según la fase y de la pausa automática al salir de la app. */
class PauseController(private val lc: GameLifecycle) {
    private var lastBackMs = 0L

    /** Devuelve true si la pulsación se ha consumido; false si hay que cerrar la actividad. */
    fun onBack(): Boolean {
        if (lc.overlayDepth > 1) {
            lc.pop()
            return true
        }
        when (lc.currentPhase) {
            Phase.PLAYING -> { lc.pause(); return true }
            Phase.WON, Phase.LOST -> { lc.openLevels(); return true }
            Phase.CALIBRATING -> if (lc.cancelCalibration()) return true
            Phase.MENU -> {
                // si el menú se abrió desde la pausa, atrás vuelve a la pausa
                val s = com.laberinto3d.juego.game.core.GameState.currentSession
                if (s != null && s.returnPhase == Phase.PAUSED) {
                    if (com.laberinto3d.juego.game.core.GameState.transition(Phase.PAUSED, "atrás desde niveles")) return true
                }
            }
            else -> {}
        }
        // PAUSED, MENU, CALIBRATING inicial o sin sesión: doble pulsación para salir
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackMs < 2000L) return false
        lastBackMs = now
        Toast.makeText(lc.activity, R.string.exit_again, Toast.LENGTH_SHORT).show()
        return true
    }
}
