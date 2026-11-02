package com.laberinto3d.juego.game.ui

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.laberinto3d.juego.R
import com.laberinto3d.juego.game.audio.AudioManager
import com.laberinto3d.juego.game.audio.SoundIds
import com.laberinto3d.juego.game.core.GameState
import com.laberinto3d.juego.game.core.Session
import com.laberinto3d.juego.game.core.Tuning
import com.laberinto3d.juego.game.diag.CrashHandler
import com.laberinto3d.juego.game.diag.DebugOverlay
import com.laberinto3d.juego.game.diag.GameLog
import com.laberinto3d.juego.game.diag.LogViewer
import com.laberinto3d.juego.game.diag.Report
import com.laberinto3d.juego.game.input.TiltMapping
import com.laberinto3d.juego.game.levels.Levels
import com.laberinto3d.juego.game.levels.SphereGenerator
import com.laberinto3d.juego.game.lifecycle.GameLifecycle
import com.laberinto3d.juego.game.storage.Prefs
import java.io.File

/** Construye todas las pantallas modales (siempre por código, sin AppCompat). */
class MenuFactory(private val a: Activity, private val lc: GameLifecycle) {
    private val main = Handler(Looper.getMainLooper())

    private fun str(id: Int) = a.getString(id)
    private fun str(id: Int, vararg args: Any) = a.getString(id, *args)
    private fun btn(id: Int, kind: UiKit.Kind = UiKit.Kind.SECONDARY, f: () -> Unit): Button =
        UiKit.button(a, str(id), kind, f)

    private fun wrap(card: LinearLayout, widthDp: Int = 440): View = UiKit.scrollCentered(a, card, widthDp)

    private fun full(card: LinearLayout, v: View, topDp: Int = 10) {
        card.addView(v, UiKit.vParams(a, topDp))
    }

    // ------------------------------------------------------------------ Calibración
    /** Referencia al texto en vivo de la pantalla de calibración (se refresca desde onHud). */
    var calibLive: TextView? = null
        private set
    private val angTmp = FloatArray(2)

    fun refreshCalibration() {
        val t = calibLive ?: return
        if (!t.isAttachedToWindow) return
        val acc = lc.accel
        if (!acc.hasData) {
            t.text = str(R.string.calibrate_no_data)
            return
        }
        TiltMapping.angles(acc.filtered, acc.rotation, angTmp)
        val r = Math.toDegrees(angTmp[0].toDouble())
        val p = Math.toDegrees(angTmp[1].toDouble())
        t.text = str(R.string.calibrate_live, String.format(java.util.Locale.US, "%.1f", r), String.format(java.util.Locale.US, "%.1f", p))
    }

    fun calibration(s: Session): View {
        val c = UiKit.card(a)
        c.addView(UiKit.title(a, str(R.string.calibrating_title)))
        c.addView(UiKit.body(a, str(R.string.calibrating_text), secondary = true, sizeSp = 15f))
        val live = UiKit.body(a, "", sizeSp = 14f)
        live.setTextColor(UiKit.COLOR_SUCCESS)
        live.typeface = android.graphics.Typeface.MONOSPACE
        calibLive = live
        full(c, live, 8)
        full(c, btn(R.string.calibrate_button, UiKit.Kind.PRIMARY) { lc.calibrateAndPlay() }, 14)
        refreshCalibration()
        return wrap(c)
    }

    // ------------------------------------------------------------------ Pausa
    fun pause(s: Session): View {
        val c = UiKit.card(a)
        c.addView(UiKit.title(a, str(R.string.pause_title)))
        c.addView(UiKit.body(a, str(R.string.level_label, s.levelId, s.level.name), secondary = true, sizeSp = 14f))
        full(c, btn(R.string.resume_button, UiKit.Kind.PRIMARY) { lc.resume() }, 12)
        if (s.checkpointsTotal > 0) {
            full(c, btn(R.string.checkpoint_button) { lc.backToCheckpoint() }, 10)
        }
        c.addView(UiKit.row(a, btn(R.string.restart_button) { lc.restart() }, btn(R.string.recalibrate_button) { lc.recalibrate() }))
        c.addView(UiKit.row(a, btn(R.string.levels_button) { lc.openLevels() }, btn(R.string.settings_button) { lc.push(settings()) }))
        c.addView(UiKit.row(a, btn(R.string.view_log_button) { lc.push(logViewer()) }, btn(R.string.exit_button, UiKit.Kind.DANGER) { lc.exitGame() }))
        return wrap(c, 480)
    }

    // ------------------------------------------------------------------ Victoria / derrota
    fun victory(s: Session): View {
        val c = UiKit.card(a)
        val t = UiKit.title(a, str(R.string.won_title))
        t.setTextColor(UiKit.COLOR_SUCCESS)
        c.addView(t)
        val time = UiKit.body(a, str(R.string.won_time, UiKit.formatTime(s.lastResultMs)), sizeSp = 22f)
        time.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.addView(time)
        if (s.falls > 0) c.addView(UiKit.body(a, str(R.string.won_falls, s.falls), secondary = true, sizeSp = 14f))
        if (s.newRecord) {
            val r = UiKit.body(a, str(R.string.won_record), sizeSp = 18f)
            r.setTextColor(Color.parseColor("#FFD36B"))
            r.typeface = android.graphics.Typeface.DEFAULT_BOLD
            c.addView(r)
        } else if (s.bestMs > 0) {
            c.addView(UiKit.body(a, str(R.string.won_best, UiKit.formatTime(s.bestMs)), secondary = true))
        }
        val hasNext = s.levelId < Levels.COUNT
        if (hasNext) {
            full(c, btn(R.string.next_level_button, UiKit.Kind.PRIMARY) { lc.nextLevel() }, 14)
        } else {
            c.addView(UiKit.space(a, 6))
            c.addView(UiKit.body(a, str(R.string.won_all_done), sizeSp = 16f))
        }
        c.addView(UiKit.row(a, btn(R.string.repeat_button) { lc.restart() }, btn(R.string.menu_button) { lc.openLevels() }))
        return wrap(c)
    }

    fun lost(s: Session): View {
        val c = UiKit.card(a)
        val t = UiKit.title(a, str(R.string.lost_title))
        t.setTextColor(UiKit.COLOR_DANGER)
        c.addView(t)
        c.addView(UiKit.body(a, str(R.string.lost_text), secondary = true))
        full(c, btn(R.string.retry_button, UiKit.Kind.PRIMARY) { lc.restart() }, 14)
        full(c, btn(R.string.menu_button) { lc.openLevels() }, 10)
        return wrap(c)
    }

    // ------------------------------------------------------------------ Niveles
    fun levels(): View {
        val c = UiKit.card(a)
        c.addView(UiKit.title(a, str(R.string.levels_title)))
        val unlocked = GameState.unlockedLevel
        for (id in 1..Levels.COUNT) {
            val lvName = Levels.nameOf(id)
            val best = Prefs.getBestTime(id)
            val locked = id > unlocked
            val saved = if (id == Levels.SPHERE_ID) Prefs.getSphereCheckpoint() else -1
            val sub = if (locked) str(R.string.level_locked)
            else if (saved >= 0) str(R.string.level_resume, saved + 1, SphereGenerator.CHECKPOINTS)
            else if (best > 0) str(R.string.level_best, UiKit.formatTime(best)) else str(R.string.level_no_best)
            val b = UiKit.button(a, str(R.string.level_label, id, lvName) + "\n" + sub,
                if (locked) UiKit.Kind.SECONDARY else UiKit.Kind.PRIMARY) {
                if (!locked) lc.startLevel(id)
            }
            b.alpha = if (locked) 0.45f else 1f
            b.isEnabled = !locked
            full(c, b, 10)
        }
        c.addView(UiKit.row(a, btn(R.string.settings_button) { lc.push(settings()) }, btn(R.string.exit_button, UiKit.Kind.DANGER) { lc.exitGame() }, topDp = 14))
        return wrap(c)
    }

    // ------------------------------------------------------------------ Ajustes
    private var resetArmedAt = 0L

    fun settings(): View {
        val c = UiKit.card(a)
        c.addView(UiKit.title(a, str(R.string.settings_title)))
        c.addView(UiKit.toggleRow(a, str(R.string.settings_sound), GameState.soundEnabled) { v ->
            GameState.soundEnabled = v
            AudioManager.soundEnabled = v
            Prefs.setSoundEnabled(v)
            GameLog.i("Settings", "Sonido=$v")
        })
        c.addView(UiKit.toggleRow(a, str(R.string.settings_vibration), GameState.vibrationEnabled) { v ->
            GameState.vibrationEnabled = v
            AudioManager.vibrationEnabled = v
            Prefs.setVibrationEnabled(v)
            GameLog.i("Settings", "Vibración=$v")
            if (v) AudioManager.vibrateTest(40L)
        })
        c.addView(UiKit.sliderRow(a, str(R.string.settings_sensitivity), 0.5f, 2.0f, 0.05f, Tuning.sensitivity,
            { String.format(java.util.Locale.US, "%.2f×", it) }) { v ->
            Tuning.sensitivity = v
            GameState.sensitivity = v
            Prefs.setSensitivity(v)
        })
        c.addView(UiKit.toggleRow(a, str(R.string.settings_auto_camera), Tuning.autoCamera) { v ->
            Tuning.autoCamera = v
            Prefs.setAutoCamera(v)
            GameLog.i("Settings", "Cámara automática=$v")
        })
        c.addView(UiKit.row(a,
            btn(R.string.settings_advanced) { lc.push(advanced()) },
            btn(R.string.settings_debug) { lc.push(debug()) }))
        c.addView(UiKit.row(a,
            btn(R.string.settings_recalibrate) { lc.recalibrateFromSettings() },
            btn(R.string.settings_reset_progress, UiKit.Kind.DANGER) {
                val now = System.currentTimeMillis()
                if (now - resetArmedAt < 4000L) {
                    Prefs.resetProgress()
                    GameState.unlockedLevel = Prefs.getUnlockedLevel()
                    GameLog.i("Settings", "Progreso borrado")
                    Toast.makeText(a, R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
                    resetArmedAt = 0L
                } else {
                    resetArmedAt = now
                    Toast.makeText(a, R.string.settings_reset_confirm, Toast.LENGTH_LONG).show()
                }
            }))
        full(c, btn(R.string.settings_back, UiKit.Kind.PRIMARY) { lc.pop() }, 12)
        return wrap(c, 500)
    }

    // ------------------------------------------------------------------ Física avanzada
    fun advanced(): View {
        val c = UiKit.card(a)
        c.addView(UiKit.title(a, str(R.string.advanced_title)))
        c.addView(UiKit.body(a, str(R.string.advanced_hint), secondary = true, sizeSp = 13f))
        fun deg(v: Float) = String.format(java.util.Locale.US, "%.1f°", v)
        fun num(v: Float) = String.format(java.util.Locale.US, "%.2f", v)
        c.addView(UiKit.sliderRow(a, str(R.string.advanced_dead_zone), 0f, 5f, 0.5f, Tuning.deadZoneDeg, ::deg) { Tuning.deadZoneDeg = it; Prefs.saveTuning() })
        c.addView(UiKit.sliderRow(a, str(R.string.advanced_max_tilt), 8f, 30f, 1f, Tuning.maxTiltDeg, { String.format(java.util.Locale.US, "%.0f°", it) }) { Tuning.maxTiltDeg = it; Prefs.saveTuning() })
        c.addView(UiKit.sliderRow(a, str(R.string.advanced_damping), 0f, 2f, 0.05f, Tuning.damping, ::num) { Tuning.damping = it; Prefs.saveTuning() })
        c.addView(UiKit.sliderRow(a, str(R.string.advanced_restitution), 0f, 0.8f, 0.05f, Tuning.restitution, ::num) { Tuning.restitution = it; Prefs.saveTuning() })
        c.addView(UiKit.sliderRow(a, str(R.string.advanced_wall_friction), 0f, 0.6f, 0.05f, Tuning.wallFriction, ::num) { Tuning.wallFriction = it; Prefs.saveTuning() })
        c.addView(UiKit.sliderRow(a, str(R.string.advanced_sensor_tau), 0f, 300f, 10f, Tuning.sensorTauMs, { String.format(java.util.Locale.US, "%.0f ms", it) }) { Tuning.sensorTauMs = it; Prefs.saveTuning() })
        c.addView(UiKit.sliderRow(a, str(R.string.advanced_vertical_gain), 0.6f, 2.0f, 0.05f, Tuning.verticalGain, ::num) { Tuning.verticalGain = it; Prefs.saveTuning() })
        c.addView(UiKit.toggleRow(a, str(R.string.advanced_invert_x), Tuning.invertX) { Tuning.invertX = it; Prefs.saveTuning(); GameLog.i("Settings", "invertX=$it") })
        c.addView(UiKit.toggleRow(a, str(R.string.advanced_invert_y), Tuning.invertY) { Tuning.invertY = it; Prefs.saveTuning(); GameLog.i("Settings", "invertY=$it") })
        c.addView(UiKit.toggleRow(a, str(R.string.advanced_swap), Tuning.swapXY) { Tuning.swapXY = it; Prefs.saveTuning(); GameLog.i("Settings", "swapXY=$it") })
        c.addView(UiKit.row(a,
            btn(R.string.advanced_reset) {
                Tuning.resetPhysics()
                Prefs.saveTuning()
                GameLog.i("Settings", "Física restaurada")
                lc.replaceTop(advanced())
            },
            btn(R.string.settings_back, UiKit.Kind.PRIMARY) { lc.pop() }, topDp = 12))
        return wrap(c, 520)
    }

    // ------------------------------------------------------------------ Depuración
    fun debug(): View {
        val c = UiKit.card(a)
        c.addView(UiKit.title(a, str(R.string.debug_title)))
        c.addView(UiKit.toggleRow(a, str(R.string.debug_overlay), lc.debugOverlayOn()) { lc.setDebugOverlay(it) })
        c.addView(UiKit.body(a, str(R.string.debug_hint), secondary = true, sizeSp = 12f))

        val liveTitle = UiKit.body(a, str(R.string.debug_live), secondary = true, sizeSp = 13f)
        full(c, liveTitle, 8)
        val live = UiKit.mono(a, 10f)
        live.background = UiKit.roundRect(Color.argb(200, 0, 0, 0), UiKit.dp(a, 8).toFloat())
        live.setPadding(UiKit.dp(a, 8), UiKit.dp(a, 6), UiKit.dp(a, 8), UiKit.dp(a, 6))
        full(c, live, 4)
        val tick = object : Runnable {
            override fun run() {
                if (!live.isAttachedToWindow) return
                live.text = DebugOverlay.buildText(lc.accel)
                main.postDelayed(this, 250L)
            }
        }
        live.text = DebugOverlay.buildText(lc.accel)
        live.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { main.removeCallbacks(tick); main.post(tick) }
            override fun onViewDetachedFromWindow(v: View) { main.removeCallbacks(tick) }
        })

        full(c, UiKit.body(a, str(R.string.debug_selfcheck), secondary = true, sizeSp = 13f), 10)
        val sc = UiKit.mono(a, 10f)
        sc.background = UiKit.roundRect(Color.argb(200, 0, 0, 0), UiKit.dp(a, 8).toFloat())
        sc.setPadding(UiKit.dp(a, 8), UiKit.dp(a, 6), UiKit.dp(a, 8), UiKit.dp(a, 6))
        sc.text = Report.selfCheckText()
        full(c, sc, 4)

        full(c, UiKit.body(a, str(R.string.debug_tools), secondary = true, sizeSp = 13f), 10)
        c.addView(UiKit.row(a,
            btn(R.string.debug_test_sound) { AudioManager.playTest(SoundIds.BOUNCE) },
            btn(R.string.debug_test_vibration) { AudioManager.vibrateTest() }))
        c.addView(UiKit.row(a,
            btn(R.string.debug_reset_ball) { lc.debugResetBall() },
            btn(R.string.debug_force_win) { lc.debugForceWin() }))
        c.addView(UiKit.row(a,
            btn(R.string.debug_unlock_all) {
                Prefs.setUnlockedLevel(Levels.COUNT)
                GameState.unlockedLevel = Levels.COUNT
                GameLog.i("Debug", "Todos los niveles desbloqueados")
            },
            btn(R.string.view_log_button) { lc.push(logViewer()) }))
        c.addView(UiKit.row(a,
            btn(R.string.debug_copy_report) {
                Report.copy(a, Report.build(a, lc.accel))
                Toast.makeText(a, R.string.debug_copied, Toast.LENGTH_SHORT).show()
            },
            btn(R.string.debug_share_report) {
                Report.share(a, str(R.string.app_name), Report.build(a, lc.accel), str(R.string.share_chooser))
            }))
        c.addView(UiKit.row(a, btn(R.string.debug_crash_test, UiKit.Kind.DANGER) {
            GameLog.w("Debug", "Fallo de prueba provocado por el usuario")
            main.post { throw RuntimeException("Fallo de prueba provocado desde Depuración") }
        }))
        full(c, btn(R.string.settings_back, UiKit.Kind.PRIMARY) { lc.pop() }, 12)
        return wrap(c, 560)
    }

    // ------------------------------------------------------------------ Registro
    fun logViewer(initial: LogViewer.Source = LogViewer.Source.CURRENT): View =
        LogViewer.create(a, { lc.pop() }, initial)

    // ------------------------------------------------------------------ Aviso de fallo anterior
    fun crashNotice(file: File): View {
        val c = UiKit.card(a)
        val t = UiKit.title(a, str(R.string.crash_title))
        t.setTextColor(UiKit.COLOR_DANGER)
        c.addView(t)
        c.addView(UiKit.body(a, str(R.string.crash_text), secondary = true, sizeSp = 15f))
        c.addView(UiKit.row(a,
            btn(R.string.crash_view) { lc.push(logViewer(LogViewer.Source.CRASH)) },
            btn(R.string.log_share) {
                Report.share(a, str(R.string.crash_title), CrashHandler.read(file), str(R.string.share_chooser))
            }, topDp = 14))
        full(c, btn(R.string.crash_continue, UiKit.Kind.PRIMARY) { lc.dismissCrashNotice() }, 10)
        return wrap(c)
    }

    companion object {
        /** Pantalla de error a pantalla completa (sin GL). */
        fun errorScreen(a: Activity, message: String, detail: String?, onExit: () -> Unit): View {
            val c = UiKit.card(a)
            val t = UiKit.title(a, a.getString(R.string.error_title))
            t.setTextColor(UiKit.COLOR_DANGER)
            c.addView(t)
            c.addView(UiKit.body(a, message, sizeSp = 16f))
            if (!detail.isNullOrEmpty()) {
                val d = UiKit.mono(a, 10f)
                d.text = detail
                c.addView(d, UiKit.vParams(a, 10))
            }
            c.addView(UiKit.button(a, a.getString(R.string.exit_button), UiKit.Kind.PRIMARY, onExit), UiKit.vParams(a, 14))
            val sv = UiKit.scrollCentered(a, c, 460)
            sv.setBackgroundColor(Color.BLACK)
            return sv
        }
    }
}
