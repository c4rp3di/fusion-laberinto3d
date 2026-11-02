package com.laberinto3d.juego

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.os.Build
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.laberinto3d.juego.R
import com.laberinto3d.juego.game.audio.AudioManager
import com.laberinto3d.juego.game.core.GameLoop
import com.laberinto3d.juego.game.core.GameState
import com.laberinto3d.juego.game.core.Phase
import com.laberinto3d.juego.game.diag.CrashHandler
import com.laberinto3d.juego.game.diag.GameLog
import com.laberinto3d.juego.game.diag.Report
import com.laberinto3d.juego.game.input.AccelerometerController
import com.laberinto3d.juego.game.input.CalibrationStore
import com.laberinto3d.juego.game.lifecycle.GameLifecycle
import com.laberinto3d.juego.game.lifecycle.PauseController
import com.laberinto3d.juego.game.render.GameRenderer
import com.laberinto3d.juego.game.render.GameSurfaceView
import com.laberinto3d.juego.game.storage.Prefs
import com.laberinto3d.juego.game.ui.MenuFactory
import com.laberinto3d.juego.game.ui.RootLayout
import com.laberinto3d.juego.game.ui.TouchRouter

/**
 * Punto de entrada del juego. MainActivity sólo reenvía aquí sus callbacks.
 * Orden de arranque: log → manejador de fallos → preferencias → autodiagnóstico →
 * ventana → vistas → primera sesión (calibración) → aviso de fallo anterior.
 */
object AppLogic {
    private var accel: AccelerometerController? = null
    private var glView: GameSurfaceView? = null
    private var lifecycle: GameLifecycle? = null
    private var pauseController: PauseController? = null
    private var touch: TouchRouter? = null
    private var started = false
    private var sensorOk = false

    fun onIniciar(a: Activity) {
        try {
            GameLog.init(a)
            CrashHandler.install(a)
            GameLog.i("App", "=== Arranque === ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Report.selfChecks.clear()
            val container = a.findViewById<FrameLayout>(R.id.root)

            // ---- Preferencias
            try {
                Prefs.init(a)
                Prefs.loadTuning()
                GameState.loadFromPrefs()
                CalibrationStore.load()
                Report.selfChecks.add("[OK] Preferencias cargadas (nivel desbloqueado ${GameState.unlockedLevel})")
            } catch (t: Throwable) {
                Report.selfChecks.add("[FALLO] Preferencias: $t")
                fail(a, container, a.getString(R.string.error_generic), t)
                return
            }

            // ---- Autodiagnóstico
            val ctrl = AccelerometerController(a)
            accel = ctrl
            if (!ctrl.available) {
                Report.selfChecks.add("[FALLO] Acelerómetro no disponible")
                fail(a, container, a.getString(R.string.error_no_accelerometer), null)
                return
            }
            Report.selfChecks.add("[OK] Acelerómetro presente")
            val glEs = try {
                (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.reqGlEsVersion
            } catch (_: Throwable) { 0 }
            if (glEs < 0x30000) {
                Report.selfChecks.add("[FALLO] OpenGL ES 3.0 no soportado (reqGlEsVersion=0x${Integer.toHexString(glEs)})")
                fail(a, container, a.getString(R.string.error_no_gl3), null)
                return
            }
            Report.selfChecks.add("[OK] OpenGL ES >= 3.0 (reqGlEsVersion=0x${Integer.toHexString(glEs)})")
            try {
                val v = a.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                Report.selfChecks.add(if (v != null && v.hasVibrator()) "[OK] Vibrador presente" else "[AVISO] Sin vibrador")
            } catch (_: Throwable) { Report.selfChecks.add("[AVISO] No se pudo consultar el vibrador") }

            // ---- Audio
            try {
                AudioManager.init(a)
                AudioManager.soundEnabled = GameState.soundEnabled
                AudioManager.vibrationEnabled = GameState.vibrationEnabled
                Report.selfChecks.add("[OK] Audio inicializado (SoundPool, sonidos sintetizados)")
            } catch (t: Throwable) {
                Report.selfChecks.add("[AVISO] Audio no disponible: $t")
                GameLog.e("App", "Audio", t)
            }

            // ---- Ventana
            configureWindow(a)

            // ---- Vistas
            val loop = GameLoop(ctrl)
            val renderer = GameRenderer(loop)
            val gl = GameSurfaceView(a, renderer)
            glView = gl
            lateinit var lc: GameLifecycle
            val tr = TouchRouter { lc.toggleDebugOverlay() }
            touch = tr
            val root = RootLayout(a, gl, ctrl, tr)
            lc = GameLifecycle(a, ctrl, loop, root)
            lifecycle = lc
            pauseController = PauseController(lc)
            container.removeAllViews()
            container.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            root.requestApplyInsets()
            if (Prefs.getDebugOverlay()) root.debug.setEnabledOverlay(true, false)

            // ---- Primera sesión: calibración
            GameState.events = lc
            val s = GameState.newSession(GameState.unlockedLevel, Phase.CALIBRATING)
            s.returnPhase = Phase.CALIBRATING
            GameState.announce(s, "arranque")

            // ---- Aviso de fallo anterior
            try {
                val crash = CrashHandler.unseenCrash(Prefs.getLastCrashSeen())
                if (crash != null) {
                    Prefs.setLastCrashSeen(crash.lastModified())
                    GameLog.w("App", "Hay un informe de fallo sin ver: ${crash.name}")
                    root.overlay.push(lc.menus.crashNotice(crash))
                }
            } catch (t: Throwable) { GameLog.e("App", "Comprobando fallos previos", t) }

            started = true
            GameLog.i("App", "Arranque completado")
        } catch (t: Throwable) {
            GameLog.e("App", "Fallo en el arranque", t)
            try {
                fail(a, a.findViewById(R.id.root), a.getString(R.string.error_generic), t)
            } catch (_: Throwable) { }
        }
    }

    private fun fail(a: Activity, container: FrameLayout, msg: String, t: Throwable?) {
        GameLog.e("App", "Error de arranque: $msg" + (t?.let { " :: $it" } ?: ""))
        container.removeAllViews()
        val detail = if (t != null) android.util.Log.getStackTraceString(t) else null
        container.addView(MenuFactory.errorScreen(a, msg, detail) { a.finish() })
        started = false
    }

    @Suppress("DEPRECATION")
    private fun configureWindow(a: Activity) {
        val w = a.window
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                val lp = w.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                w.attributes = lp
            }
        } catch (t: Throwable) { GameLog.w("App", "Recorte de pantalla: $t") }
        try {
            val d = a.windowManager.defaultDisplay
            val cur = d.mode
            var best = cur
            for (m in d.supportedModes) {
                if (m.physicalWidth == cur.physicalWidth && m.physicalHeight == cur.physicalHeight &&
                    m.refreshRate > best.refreshRate + 0.5f) best = m
            }
            if (best.modeId != cur.modeId) {
                val lp = w.attributes
                lp.preferredDisplayModeId = best.modeId
                w.attributes = lp
            }
            GameLog.i("App", "Refresco: actual ${cur.refreshRate} Hz, elegido ${best.refreshRate} Hz")
        } catch (t: Throwable) { GameLog.w("App", "Tasa de refresco: $t") }
        applyImmersive(a)
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive(a: Activity) {
        try {
            a.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        } catch (t: Throwable) { GameLog.w("App", "Modo inmersivo: $t") }
    }

    fun onResume(a: Activity) {
        applyImmersive(a)
        if (!started) return
        glView?.onResume()
        val ctrl = accel
        if (ctrl != null) {
            sensorOk = ctrl.start()
            if (!sensorOk) GameLog.e("App", "No se pudo registrar el acelerómetro")
            else GameLog.i("App", "Acelerómetro registrado")
        }
        lifecycle?.onHostResume()
    }

    fun onPause(a: Activity) {
        if (!started) return
        lifecycle?.onHostPause()
        glView?.onPause()
        accel?.stop()
        touch?.cancel()
        GameLog.i("App", "En segundo plano")
        GameLog.flushBlocking()
    }

    fun onDestroy(a: Activity) {
        GameLog.i("App", "onDestroy")
        try { accel?.stop() } catch (_: Throwable) { }
        try { AudioManager.release() } catch (_: Throwable) { }
        GameState.events = null
        started = false
        lifecycle = null
        glView = null
        pauseController = null
        GameLog.flushBlocking()
    }

    /** true = consumido; false = que la actividad se cierre. */
    fun onBack(): Boolean = pauseController?.onBack() ?: false

    fun onWindowFocusChanged(a: Activity, hasFocus: Boolean) {
        if (hasFocus) applyImmersive(a)
    }
}
