package com.laberinto3d.juego.game.diag

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.laberinto3d.juego.game.core.GameState
import com.laberinto3d.juego.game.core.Telemetry
import com.laberinto3d.juego.game.input.AccelerometerController
import com.laberinto3d.juego.game.input.CalibrationStore
import com.laberinto3d.juego.game.storage.Prefs
import com.laberinto3d.juego.game.ui.UiKit
import java.util.Locale

/**
 * Panel de datos en vivo sobre la partida (esquina inferior izquierda). No recibe toques.
 * Se enciende/apaga desde Ajustes → Depuración o con pulsación larga en la esquina superior izquierda.
 */
class DebugOverlay(context: Context, private val accel: AccelerometerController) : FrameLayout(context) {
    private val text = TextView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var on = false

    private val tick = object : Runnable {
        override fun run() {
            if (!on) return
            text.text = buildText(accel)
            handler.postDelayed(this, 200L)
        }
    }

    init {
        text.setTextColor(Color.parseColor("#B8FFE0"))
        text.textSize = 9.5f
        text.typeface = Typeface.MONOSPACE
        text.setLineSpacing(0f, 1.0f)
        text.background = UiKit.roundRect(Color.argb(170, 0, 0, 0), UiKit.dp(context, 8).toFloat())
        text.setPadding(UiKit.dp(context, 8), UiKit.dp(context, 6), UiKit.dp(context, 8), UiKit.dp(context, 6))
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
        lp.setMargins(UiKit.dp(context, 8), 0, 0, UiKit.dp(context, 8))
        addView(text, lp)
        visibility = GONE
        isClickable = false
        isFocusable = false
    }

    val enabledNow: Boolean get() = on

    fun setEnabledOverlay(v: Boolean, persist: Boolean = true) {
        if (on == v) return
        on = v
        visibility = if (v) View.VISIBLE else View.GONE
        handler.removeCallbacks(tick)
        if (v) handler.post(tick)
        if (persist) try { Prefs.setDebugOverlay(v) } catch (_: Throwable) { }
        GameLog.i("Debug", "Panel de datos ${if (v) "activado" else "desactivado"}")
    }

    fun toggle() = setEnabledOverlay(!on)

    fun onHostPause() { handler.removeCallbacks(tick) }
    fun onHostResume() { if (on) { handler.removeCallbacks(tick); handler.post(tick) } }

    companion object {
        private fun f(v: Float, d: Int = 2): String = String.format(Locale.US, "%.${d}f", v)

        /** Texto de telemetría compartido con la pantalla de depuración. */
        fun buildText(accel: AccelerometerController): String {
            val s = GameState.currentSession
            val sb = StringBuilder(640)
            sb.append("FPS ").append(f(Telemetry.fps, 1)).append("  frame ").append(f(Telemetry.frameMsAvg, 1)).append("ms")
                .append("  fis ").append(f(Telemetry.physicsMsAvg, 2)).append("ms")
                .append("  ren ").append(f(Telemetry.renderMsAvg, 2)).append("ms\n")
            sb.append("pasos/frame ").append(Telemetry.stepsLastFrame).append("  tiempo descartado ").append(Telemetry.droppedTimeMs).append("ms\n")
            if (s != null) {
                val b = s.ball
                val sp = Math.sqrt((b.velocity.x * b.velocity.x + b.velocity.y * b.velocity.y + b.velocity.z * b.velocity.z).toDouble()).toFloat()
                sb.append("fase ").append(s.phase).append("  nivel ").append(s.levelId).append("  t=").append(s.elapsedMs).append("ms")
                    .append("  cp ").append(s.checkpointsReached).append('/').append(s.checkpointsTotal).append("  caídas ").append(s.falls).append('\n')
                sb.append("vista yaw ").append(f(Math.toDegrees(com.laberinto3d.juego.game.render.CameraControl.yaw.toDouble()).toFloat(), 0))
                    .append("°  elev ").append(f(com.laberinto3d.juego.game.render.CameraControl.pitchDeg, 0))
                    .append("°  zoom ").append(f(com.laberinto3d.juego.game.render.CameraControl.zoom))
                    .append(if (com.laberinto3d.juego.game.render.CameraControl.gesturing) "  [CONGELADO]" else "").append('\n')
                sb.append("pos ").append(f(b.position.x)).append(' ').append(f(b.position.y)).append(' ').append(f(b.position.z)).append('\n')
                sb.append("vel ").append(f(b.velocity.x)).append(' ').append(f(b.velocity.y)).append(' ').append(f(b.velocity.z))
                    .append("  |v|=").append(f(sp)).append('\n')
            }
            sb.append("g   ").append(f(Telemetry.gx)).append(' ').append(f(Telemetry.gy)).append(' ').append(f(Telemetry.gz)).append('\n')
            sb.append("inclin. alabeo ").append(f(Telemetry.tiltRollDeg, 1)).append("°  cabeceo ").append(f(Telemetry.tiltPitchDeg, 1)).append("°\n")
            sb.append("contactos paso ").append(Telemetry.contactsLastStep).append("  total ").append(Telemetry.totalContacts)
                .append("  impacto max ").append(f(Telemetry.maxImpact)).append("  atascos ").append(Telemetry.stuckResets).append('\n')
            val r = accel.raw
            val fl = accel.filtered
            val n = CalibrationStore.neutral
            sb.append("sensor raw  ").append(f(r.x)).append(' ').append(f(r.y)).append(' ').append(f(r.z)).append('\n')
            sb.append("sensor filt ").append(f(fl.x)).append(' ').append(f(fl.y)).append(' ').append(f(fl.z)).append('\n')
            sb.append("neutral     ").append(f(n.x)).append(' ').append(f(n.y)).append(' ').append(f(n.z)).append('\n')
            sb.append("rot ").append(accel.rotation).append("  ").append(accel.sampleRateHz).append(" Hz  eventos ").append(accel.totalEvents)
                .append("  datos ").append(if (accel.hasData) "sí" else "NO").append('\n')
            sb.append("GL ").append(Telemetry.glVersion).append('\n')
            sb.append(Telemetry.glRenderer).append("  MSAA ").append(Telemetry.msaaSamples).append("x\n")
            sb.append("superficie ").append(Telemetry.surfaceW).append('x').append(Telemetry.surfaceH)
                .append("  cam ").append(f(Telemetry.camDistance)).append("m  errGL ").append(Telemetry.glErrors).append("  errLog ").append(GameLog.errors)
            return sb.toString()
        }
    }
}
