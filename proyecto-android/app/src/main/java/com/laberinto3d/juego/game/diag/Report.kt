package com.laberinto3d.juego.game.diag

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.util.DisplayMetrics
import com.laberinto3d.juego.game.audio.AudioManager
import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.core.GameState
import com.laberinto3d.juego.game.core.Tuning
import com.laberinto3d.juego.game.input.AccelerometerController
import com.laberinto3d.juego.game.storage.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Informe de diagnóstico completo en texto plano para copiar o compartir. */
object Report {
    /** Resultados del autodiagnóstico de arranque (los rellena AppLogic). */
    val selfChecks = ArrayList<String>()

    fun selfCheckText(): String =
        if (selfChecks.isEmpty()) "(sin datos)" else selfChecks.joinToString("\n")

    @Suppress("DEPRECATION")
    fun build(activity: Activity, accel: AccelerometerController?): String {
        val sb = StringBuilder(8192)
        sb.append("=== INFORME LABERINTO 3D ===\n")
        sb.append("Fecha: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
        try {
            val pi = activity.packageManager.getPackageInfo(activity.packageName, 0)
            sb.append("Versión: ").append(pi.versionName).append(" (").append(pi.packageName).append(")\n")
        } catch (_: Throwable) { }
        sb.append("Dispositivo: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" | Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        try {
            val dm = DisplayMetrics()
            val d = activity.windowManager.defaultDisplay
            d.getRealMetrics(dm)
            sb.append("Pantalla: ").append(dm.widthPixels).append('x').append(dm.heightPixels)
                .append(" densidad ").append(dm.density).append(" refresco ").append(d.refreshRate).append(" Hz rotación ").append(d.rotation).append('\n')
        } catch (_: Throwable) { }
        try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            sb.append("reqGlEsVersion: 0x").append(Integer.toHexString(am.deviceConfigurationInfo.reqGlEsVersion)).append('\n')
        } catch (_: Throwable) { }
        sb.append("Acelerómetro: ")
        try {
            val sm = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (s == null) sb.append("NO DISPONIBLE\n")
            else sb.append(s.name).append(" | rango ").append(s.maximumRange).append(" | resolución ").append(s.resolution)
                .append(" | retardo mín ").append(s.minDelay).append("us\n")
        } catch (t: Throwable) { sb.append("error ").append(t).append('\n') }
        sb.append("GL: ").append(com.laberinto3d.juego.game.core.Telemetry.glVersion).append(" | ")
            .append(com.laberinto3d.juego.game.core.Telemetry.glRenderer).append(" | ")
            .append(com.laberinto3d.juego.game.core.Telemetry.glVendor).append(" | GLSL ")
            .append(com.laberinto3d.juego.game.core.Telemetry.glslVersion).append(" | MSAA ")
            .append(com.laberinto3d.juego.game.core.Telemetry.msaaSamples).append("x\n")
        sb.append("Audio: listo=").append(AudioManager.ready).append(" sonidos cargados=").append(AudioManager.loadedCount).append('\n')
        sb.append("\n--- Autodiagnóstico ---\n").append(selfCheckText()).append('\n')
        sb.append("\n--- Ajustes y física ---\n")
        sb.append("sensibilidad=").append(Tuning.sensitivity).append(" zonaMuerta=").append(Tuning.deadZoneDeg)
            .append(" inclMax=").append(Tuning.maxTiltDeg).append(" amort=").append(Tuning.damping)
            .append(" rebote=").append(Tuning.restitution).append(" rozPared=").append(Tuning.wallFriction)
            .append(" tauSensor=").append(Tuning.sensorTauMs).append('\n')
        sb.append("invertX=").append(Tuning.invertX).append(" invertY=").append(Tuning.invertY).append(" swapXY=").append(Tuning.swapXY).append('\n')
        sb.append("sonido=").append(GameState.soundEnabled).append(" vibración=").append(GameState.vibrationEnabled)
            .append(" nivelDesbloqueado=").append(GameState.unlockedLevel).append('\n')
        if (accel != null) {
            sb.append("sensor: rot=").append(accel.rotation).append(" Hz=").append(accel.sampleRateHz).append(" datos=").append(accel.hasData)
                .append(" raw=").append(accel.raw).append(" filt=").append(accel.filtered).append('\n')
        }
        sb.append("\n--- Telemetría ---\n")
        if (accel != null) sb.append(DebugOverlay.buildText(accel)).append('\n')
        sb.append("\n--- Preferencias ---\n")
        try { sb.append(Prefs.dump()) } catch (t: Throwable) { sb.append("error $t\n") }
        sb.append("\n--- Registro (últimas ").append(Config.LOG_VIEW_LINES).append(" líneas) ---\n")
        sb.append(GameLog.text(Config.LOG_VIEW_LINES)).append('\n')
        return sb.toString()
    }

    /** Lanza el selector de compartir con [text] (recortado a ~120 000 caracteres). */
    fun share(activity: Activity, title: String, text: String, chooser: String) {
        try {
            val body = if (text.length > 120000) text.substring(text.length - 120000) else text
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_SUBJECT, title)
            i.putExtra(Intent.EXTRA_TEXT, body)
            activity.startActivity(Intent.createChooser(i, chooser))
        } catch (t: Throwable) {
            GameLog.e("Report", "No se pudo compartir", t)
        }
    }

    fun copy(activity: Activity, text: String) {
        try {
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Laberinto 3D", text))
        } catch (t: Throwable) {
            GameLog.e("Report", "No se pudo copiar", t)
        }
    }
}
