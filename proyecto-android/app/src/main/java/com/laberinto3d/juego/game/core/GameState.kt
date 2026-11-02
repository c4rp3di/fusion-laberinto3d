package com.laberinto3d.juego.game.core

import android.os.Handler
import android.os.Looper
import com.laberinto3d.juego.game.diag.GameLog
import com.laberinto3d.juego.game.levels.Levels
import com.laberinto3d.juego.game.storage.Prefs

/** Avisos del núcleo hacia la interfaz. Siempre se invocan en el hilo UI. */
interface GameEvents {
    fun onPhaseChanged(session: Session, from: Phase, to: Phase)
    fun onHud(levelId: Int, levelName: String, elapsedMs: Long, bestMs: Long, checkpoints: Int, checkpointsTotal: Int, falls: Int)
}

/**
 * Máquina de estados global + ajustes de usuario cargados de preferencias.
 * Es un singleton; las transiciones se validan y se serializan con un cerrojo.
 */
object GameState {
    @Volatile var currentSession: Session? = null
    @Volatile var unlockedLevel: Int = 1
    @Volatile var soundEnabled: Boolean = true
    @Volatile var vibrationEnabled: Boolean = true
    @Volatile var sensitivity: Float = 1.0f

    @Volatile var events: GameEvents? = null
    private val main = Handler(Looper.getMainLooper())

    private val allowed: Map<Phase, Set<Phase>> = mapOf(
        Phase.CALIBRATING to setOf(Phase.PLAYING, Phase.PAUSED, Phase.MENU),
        Phase.PLAYING to setOf(Phase.PAUSED, Phase.WON, Phase.LOST),
        Phase.PAUSED to setOf(Phase.PLAYING, Phase.CALIBRATING, Phase.MENU),
        Phase.WON to setOf(Phase.MENU, Phase.PAUSED, Phase.PLAYING),
        Phase.LOST to setOf(Phase.MENU, Phase.PAUSED, Phase.PLAYING),
        Phase.MENU to setOf(Phase.PLAYING, Phase.PAUSED, Phase.WON, Phase.LOST, Phase.CALIBRATING)
    )

    fun loadFromPrefs() {
        unlockedLevel = Prefs.getUnlockedLevel()
        soundEnabled = Prefs.getSoundEnabled()
        vibrationEnabled = Prefs.getVibrationEnabled()
        sensitivity = Prefs.getSensitivity()
    }

    /** Crea una sesión nueva para [levelId] y la deja como actual (sin avisar a la UI). */
    fun newSession(levelId: Int, initial: Phase): Session {
        val lv = Levels.nivel(levelId)
        val s = Session(lv, Prefs.getBestTime(lv.id), initial)
        currentSession = s
        GameLog.i("State", "Sesión nueva: nivel ${lv.id} '${lv.name}' fase inicial $initial mejor=${s.bestMs}ms")
        return s
    }

    /**
     * Intenta pasar la sesión actual a [to]. Devuelve false si la transición no está
     * permitida o si ya estaba en ese estado. Notifica a la UI en su hilo.
     */
    @Synchronized
    fun transition(to: Phase, reason: String): Boolean {
        val s = currentSession ?: return false
        val from = s.phase
        if (from == to) return false
        if (allowed[from]?.contains(to) != true) {
            GameLog.w("State", "Transición rechazada $from -> $to ($reason)")
            return false
        }
        s.phase = to
        GameLog.i("State", "$from -> $to ($reason)")
        notifyPhase(s, from, to)
        return true
    }

    /** Fija la fase de una sesión recién creada y avisa a la UI (no valida la tabla). */
    @Synchronized
    fun announce(session: Session, reason: String) {
        GameLog.i("State", "Pantalla para fase ${session.phase} ($reason)")
        notifyPhase(session, session.phase, session.phase)
    }

    private fun notifyPhase(s: Session, from: Phase, to: Phase) {
        val ev = events ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) ev.onPhaseChanged(s, from, to)
        else main.post { ev.onPhaseChanged(s, from, to) }
    }

    fun postHud(s: Session) {
        val ev = events ?: return
        val id = s.levelId
        val name = s.level.name
        val el = s.elapsedMs
        val best = s.bestMs
        val cp = s.checkpointsReached
        val cpt = s.checkpointsTotal
        val fl = s.falls
        main.post { ev.onHud(id, name, el, best, cp, cpt, fl) }
    }

    fun postToMain(r: Runnable) {
        main.post(r)
    }
}
