package com.laberinto3d.juego.game.lifecycle

import android.app.Activity
import android.view.View
import com.laberinto3d.juego.game.audio.AudioManager
import com.laberinto3d.juego.game.core.GameEvents
import com.laberinto3d.juego.game.core.GameLoop
import com.laberinto3d.juego.game.core.GameState
import com.laberinto3d.juego.game.core.Phase
import com.laberinto3d.juego.game.core.Session
import com.laberinto3d.juego.game.diag.GameLog
import com.laberinto3d.juego.game.input.AccelerometerController
import com.laberinto3d.juego.game.input.CalibrationStore
import com.laberinto3d.juego.game.levels.Levels
import com.laberinto3d.juego.game.storage.Prefs
import com.laberinto3d.juego.game.physics.Vec3
import com.laberinto3d.juego.game.ui.MenuFactory
import com.laberinto3d.juego.game.ui.RootLayout
import android.widget.Toast
import com.laberinto3d.juego.R

/**
 * Acciones de alto nivel del juego (pausar, reiniciar, cambiar de nivel, calibrar...) y
 * traducción de cada fase a la pantalla que debe verse. Siempre se usa desde el hilo UI.
 */
class GameLifecycle(
    val activity: Activity,
    val accel: AccelerometerController,
    private val loop: GameLoop,
    val root: RootLayout
) : GameEvents {
    val menus = MenuFactory(activity, this)
    private val overlay get() = root.overlay

    init {
        root.hud.setOnPause { pause() }
    }

    val currentPhase: Phase? get() = GameState.currentSession?.phase
    val overlayDepth: Int get() = overlay.depth

    // ------------------------------------------------------------------ GameEvents
    override fun onPhaseChanged(session: Session, from: Phase, to: Phase) {
        if (session !== GameState.currentSession) return // evento de una sesión ya sustituida
        showForPhase(session)
    }

    override fun onHud(levelId: Int, levelName: String, elapsedMs: Long, bestMs: Long,
                       checkpoints: Int, checkpointsTotal: Int, falls: Int) {
        root.hud.update(levelId, levelName, elapsedMs, bestMs, checkpoints, checkpointsTotal, falls)
        if (currentPhase == Phase.CALIBRATING) menus.refreshCalibration()
    }

    /** Muestra la pantalla que corresponde a la fase de [s]. */
    fun showForPhase(s: Session) {
        when (s.phase) {
            Phase.PLAYING -> overlay.clear()
            Phase.PAUSED -> overlay.setRoot(menus.pause(s))
            Phase.CALIBRATING -> overlay.setRoot(menus.calibration(s))
            Phase.WON -> overlay.setRoot(menus.victory(s))
            Phase.LOST -> overlay.setRoot(menus.lost(s))
            Phase.MENU -> overlay.setRoot(menus.levels())
        }
        root.hud.update(s.levelId, s.level.name, s.elapsedMs, s.bestMs, s.checkpointsReached, s.checkpointsTotal, s.falls)
    }

    // ------------------------------------------------------------------ Navegación de pantallas
    fun push(v: View) = overlay.push(v)
    fun pop(): Boolean = overlay.pop()
    fun replaceTop(v: View) = overlay.replaceTop(v)
    fun dismissCrashNotice() { overlay.pop() }

    // ------------------------------------------------------------------ Acciones de juego
    fun pause() {
        GameState.transition(Phase.PAUSED, "usuario")
    }

    fun resume() {
        val s = GameState.currentSession ?: return
        if (s.physics.won || s.physics.fell) { restart(); return }
        GameState.transition(Phase.PLAYING, "continuar")
    }

    /** Reinicia el nivel actual con una sesión nueva. */
    fun restart() {
        val s = GameState.currentSession
        // reiniciar la gran esfera = empezar de cero (se descarta la partida guardada)
        if (s?.levelId == Levels.SPHERE_ID) try { Prefs.clearSphereProgress() } catch (_: Throwable) { }
        startLevel(s?.levelId ?: 1)
    }

    fun startLevel(id: Int) {
        val lvl = id.coerceIn(1, Levels.COUNT)
        if (lvl > GameState.unlockedLevel) {
            GameLog.w("Life", "Nivel $lvl bloqueado")
            return
        }
        val ns = GameState.newSession(lvl, Phase.PLAYING)
        if (lvl == Levels.SPHERE_ID) {
            val saved = try { Prefs.getSphereCheckpoint() } catch (_: Throwable) { -1 }
            if (saved >= 0 && ns.physics.restoreCheckpoint(saved)) {
                ns.elapsedNanos = Prefs.getSphereElapsedMs() * 1_000_000L
                GameLog.i("Life", "Esfera reanudada en el checkpoint ${saved + 1}")
            }
        }
        ns.returnPhase = Phase.CALIBRATING
        GameState.announce(ns, "iniciar nivel $lvl")
    }

    fun nextLevel() {
        val s = GameState.currentSession ?: return
        val n = (s.levelId + 1).coerceAtMost(Levels.COUNT)
        startLevel(n)
    }

    fun openLevels() {
        val s = GameState.currentSession ?: return
        if (s.phase != Phase.MENU) s.returnPhase = s.phase
        if (s.phase == Phase.MENU) {
            showForPhase(s)
        } else if (!GameState.transition(Phase.MENU, "menú de niveles")) {
            GameLog.w("Life", "No se pudo abrir el menú desde ${s.phase}")
        }
    }

    /** Vuelve al último checkpoint (o al inicio) sin perder el tiempo ni contar caída. */
    fun backToCheckpoint() {
        val s = GameState.currentSession ?: return
        loop.post(Runnable { s.physics.resetBall() })
        GameLog.i("Life", "Volver al checkpoint ${s.physics.checkpointIndex + 1}")
        if (s.phase == Phase.PAUSED) GameState.transition(Phase.PLAYING, "volver al checkpoint")
    }

    fun recalibrate() {
        val s = GameState.currentSession ?: return
        if (s.phase != Phase.CALIBRATING) s.returnPhase = s.phase
        accel.resetFilter()
        if (!GameState.transition(Phase.CALIBRATING, "recalibrar")) {
            GameLog.w("Life", "No se pudo recalibrar desde ${s.phase}")
        }
    }

    fun recalibrateFromSettings() = recalibrate()

    /** Botón "Calibrar y jugar". */
    fun calibrateAndPlay() {
        val s = GameState.currentSession ?: return
        if (!accel.hasData) {
            Toast.makeText(activity, R.string.calibrate_no_data, Toast.LENGTH_SHORT).show()
            GameLog.w("Life", "Calibrar sin datos del sensor")
            return
        }
        CalibrationStore.capture(accel)
        Toast.makeText(activity, R.string.calibrate_done, Toast.LENGTH_SHORT).show()
        if (s.returnPhase == Phase.MENU || s.physics.won || s.physics.fell) {
            startLevel(s.levelId)
        } else {
            GameState.transition(Phase.PLAYING, "calibrado")
        }
    }

    /** Botón atrás dentro de CALIBRATING cuando se llegó desde otra pantalla. */
    fun cancelCalibration(): Boolean {
        val s = GameState.currentSession ?: return false
        val back = s.returnPhase
        if (s.phase != Phase.CALIBRATING || back == Phase.CALIBRATING) return false
        return GameState.transition(back, "cancelar calibración")
    }

    fun exitGame() {
        GameLog.i("Life", "Salir por menú")
        activity.finish()
    }

    // ------------------------------------------------------------------ Depuración
    fun debugOverlayOn(): Boolean = root.debug.enabledNow
    fun setDebugOverlay(on: Boolean) = root.debug.setEnabledOverlay(on)
    fun toggleDebugOverlay() = root.debug.toggle()

    fun debugResetBall() {
        val s = GameState.currentSession ?: return
        loop.post(Runnable { s.physics.resetBall() })
        GameLog.i("Debug", "Bola recolocada")
    }

    fun debugForceWin() {
        val s = GameState.currentSession ?: return
        if (s.phase == Phase.PAUSED) GameState.transition(Phase.PLAYING, "forzar victoria")
        val g = s.world.goalPosition
        loop.post(Runnable { s.ball.placeAt(Vec3(g.x, s.ball.radius, g.z)) })
        GameLog.i("Debug", "Victoria forzada")
    }

    // ------------------------------------------------------------------ Ciclo de vida de la actividad
    fun onHostPause() {
        AudioManager.pauseAll()
        root.debug.onHostPause()
        com.laberinto3d.juego.game.render.CameraControl.gesturing = false
        if (currentPhase == Phase.PLAYING) GameState.transition(Phase.PAUSED, "app en segundo plano")
    }

    fun onHostResume() {
        AudioManager.resumeAll()
        root.debug.onHostResume()
        if (currentPhase == Phase.CALIBRATING) accel.resetFilter()
    }
}
