package com.laberinto3d.juego.game.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.WindowManager
import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.core.Tuning
import com.laberinto3d.juego.game.diag.GameLog
import com.laberinto3d.juego.game.physics.Vec3
import kotlin.math.exp

/**
 * Lee el acelerómetro en un hilo propio, aplica un filtro paso bajo exponencial
 * dependiente del tiempo y publica el último vector (inmutable) en campos @Volatile.
 * No interpreta el significado de los datos: eso lo hace [TiltMapping].
 */
class AccelerometerController(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val available: Boolean get() = sensor != null

    /** Última lectura cruda (coordenadas del dispositivo). */
    @Volatile var raw: Vec3 = Vec3(Config.NEUTRAL_DEFAULT_X, Config.NEUTRAL_DEFAULT_Y, Config.NEUTRAL_DEFAULT_Z)
        private set
    /** Lectura filtrada (coordenadas del dispositivo). */
    @Volatile var filtered: Vec3 = Vec3(Config.NEUTRAL_DEFAULT_X, Config.NEUTRAL_DEFAULT_Y, Config.NEUTRAL_DEFAULT_Z)
        private set
    /** Rotación de la pantalla: Surface.ROTATION_0..270 convertido a 0..3. */
    @Volatile var rotation: Int = 1
        private set
    /** Eventos por segundo (calculado cada segundo). */
    @Volatile var sampleRateHz: Int = 0
        private set
    @Volatile var hasData = false
        private set
    @Volatile var totalEvents = 0L
        private set

    private var thread: HandlerThread? = null
    private var running = false
    private var lastTs = 0L
    private var fx = 0f
    private var fy = 0f
    private var fz = 0f
    private var first = true
    private var rateCount = 0
    private var rateStart = 0L
    private var rotCheckAt = 0L

    /** Registra el listener. Devuelve false si no hay acelerómetro o el registro falla. */
    @Synchronized
    fun start(): Boolean {
        if (running) return true
        val s = sensor ?: run {
            GameLog.e("Sensor", "El dispositivo no tiene acelerómetro")
            return false
        }
        val t = HandlerThread("sensor-thread")
        t.start()
        thread = t
        first = true
        lastTs = 0L
        rateCount = 0
        rateStart = 0L
        updateRotation()
        val ok = try {
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME, Handler(t.looper))
        } catch (e: Throwable) {
            GameLog.e("Sensor", "registerListener falló", e)
            false
        }
        running = ok
        if (!ok) {
            t.quitSafely()
            thread = null
        } else {
            GameLog.i("Sensor", "Acelerómetro registrado: ${s.name} (max ${s.maximumRange}, delay ${s.minDelay} us)")
        }
        return ok
    }

    @Synchronized
    fun stop() {
        if (!running) return
        try {
            sm.unregisterListener(this)
        } catch (_: Throwable) {
        }
        thread?.quitSafely()
        thread = null
        running = false
        GameLog.i("Sensor", "Acelerómetro liberado")
    }

    val isRunning: Boolean get() = running

    /** Reinicia el filtro con la lectura actual (se usa tras calibrar). */
    fun resetFilter() {
        first = true
    }

    @Suppress("DEPRECATION")
    private fun updateRotation() {
        rotation = try {
            when (wm.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> 1
                Surface.ROTATION_180 -> 2
                Surface.ROTATION_270 -> 3
                else -> 0
            }
        } catch (_: Throwable) {
            rotation
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val ts = event.timestamp
        raw = Vec3(x, y, z)

        if (first || lastTs == 0L) {
            fx = x; fy = y; fz = z
            first = false
        } else {
            var dt = (ts - lastTs) * 1e-9f
            if (dt < 0.001f) dt = 0.001f
            if (dt > 0.2f) dt = 0.2f
            val tau = (Tuning.sensorTauMs / 1000f).coerceAtLeast(0.001f)
            val a = exp(-dt / tau)
            fx = a * fx + (1f - a) * x
            fy = a * fy + (1f - a) * y
            fz = a * fz + (1f - a) * z
        }
        lastTs = ts
        filtered = Vec3(fx, fy, fz)
        hasData = true
        totalEvents++

        // tasa de muestreo y rotación de pantalla (cada ~0.5 s)
        rateCount++
        val nowMs = ts / 1_000_000L
        if (rateStart == 0L) rateStart = nowMs
        if (nowMs - rateStart >= 1000L) {
            sampleRateHz = rateCount
            rateCount = 0
            rateStart = nowMs
        }
        if (nowMs - rotCheckAt > 500L) {
            rotCheckAt = nowMs
            updateRotation()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        GameLog.d("Sensor", "Precisión cambiada: $accuracy")
    }
}
