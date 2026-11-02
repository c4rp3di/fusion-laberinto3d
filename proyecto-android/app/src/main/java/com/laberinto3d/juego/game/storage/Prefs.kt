package com.laberinto3d.juego.game.storage

import android.content.Context
import android.content.SharedPreferences
import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.core.Tuning
import com.laberinto3d.juego.game.levels.Levels

/** Envoltorio de SharedPreferences (modo privado). Llamar a [init] antes de usarlo. */
object Prefs {
    private var sp: SharedPreferences? = null

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(Keys.FILE, Context.MODE_PRIVATE)
    }

    val ready: Boolean get() = sp != null

    private fun p(): SharedPreferences = sp ?: throw IllegalStateException("Prefs.init() no llamado")

    // ---- Mejores tiempos ----
    /** Mejor tiempo en ms (0 si no hay). */
    fun getBestTime(levelId: Int): Long = p().getLong(Keys.bestTime(levelId), 0L)

    /** Guarda el tiempo sólo si mejora el anterior. Devuelve true si es nuevo récord. */
    fun setBestTime(levelId: Int, ms: Long): Boolean {
        val old = getBestTime(levelId)
        if (old == 0L || ms < old) {
            p().edit().putLong(Keys.bestTime(levelId), ms).apply()
            return true
        }
        return false
    }

    // ---- Niveles ----
    /** Esfera II (prototipo) se abre a la vez que la Esfera I. */
    fun getUnlockedLevel(): Int {
        val v = p().getInt(Keys.UNLOCKED_LEVEL, 1).coerceIn(1, Levels.COUNT)
        return if (v >= Levels.SPHERE_ID) Levels.COUNT else v
    }

    fun unlockLevel(levelId: Int) {
        val id = levelId.coerceIn(1, Levels.COUNT)
        if (id > getUnlockedLevel()) p().edit().putInt(Keys.UNLOCKED_LEVEL, id).apply()
    }

    fun setUnlockedLevel(levelId: Int) {
        p().edit().putInt(Keys.UNLOCKED_LEVEL, levelId.coerceIn(1, Levels.COUNT)).apply()
    }

    // ---- Partida guardada de la gran esfera ----
    /** Índice del último checkpoint alcanzado (-1 = sin partida guardada). */
    fun getSphereCheckpoint(): Int = p().getInt(Keys.SPHERE_CP, -1)
    fun getSphereElapsedMs(): Long = p().getLong(Keys.SPHERE_MS, 0L)
    fun setSphereProgress(cp: Int, elapsedMs: Long) {
        p().edit().putInt(Keys.SPHERE_CP, cp).putLong(Keys.SPHERE_MS, elapsedMs).apply()
    }
    fun clearSphereProgress() {
        p().edit().remove(Keys.SPHERE_CP).remove(Keys.SPHERE_MS).apply()
    }

    fun getAutoCamera(): Boolean = p().getBoolean(Keys.SETTING_AUTO_CAMERA, true)
    fun setAutoCamera(v: Boolean) { p().edit().putBoolean(Keys.SETTING_AUTO_CAMERA, v).apply() }

    fun resetProgress() {
        val e = p().edit().putInt(Keys.UNLOCKED_LEVEL, 1).remove(Keys.SPHERE_CP).remove(Keys.SPHERE_MS)
        for (i in 1..Levels.COUNT) e.remove(Keys.bestTime(i))
        e.apply()
    }

    // ---- Calibración ----
    fun hasSensorNeutral(): Boolean = p().getBoolean(Keys.HAS_NEUTRAL, false)

    /** Neutral guardado o (0, 0, 9.81) por defecto. */
    fun getSensorNeutral(): FloatArray = floatArrayOf(
        p().getFloat(Keys.SENSOR_NEUTRAL_X, Config.NEUTRAL_DEFAULT_X),
        p().getFloat(Keys.SENSOR_NEUTRAL_Y, Config.NEUTRAL_DEFAULT_Y),
        p().getFloat(Keys.SENSOR_NEUTRAL_Z, Config.NEUTRAL_DEFAULT_Z)
    )

    fun setSensorNeutral(x: Float, y: Float, z: Float) {
        p().edit()
            .putFloat(Keys.SENSOR_NEUTRAL_X, x)
            .putFloat(Keys.SENSOR_NEUTRAL_Y, y)
            .putFloat(Keys.SENSOR_NEUTRAL_Z, z)
            .putBoolean(Keys.HAS_NEUTRAL, true)
            .apply()
    }

    // ---- Ajustes de usuario ----
    fun getSoundEnabled(): Boolean = p().getBoolean(Keys.SETTING_SOUND, true)
    fun setSoundEnabled(v: Boolean) { p().edit().putBoolean(Keys.SETTING_SOUND, v).apply() }

    fun getVibrationEnabled(): Boolean = p().getBoolean(Keys.SETTING_VIBRATION, true)
    fun setVibrationEnabled(v: Boolean) { p().edit().putBoolean(Keys.SETTING_VIBRATION, v).apply() }

    fun getSensitivity(): Float = p().getFloat(Keys.SETTING_SENSITIVITY, Tuning.DEF_SENSITIVITY)
    fun setSensitivity(v: Float) { p().edit().putFloat(Keys.SETTING_SENSITIVITY, v).apply() }

    // ---- Crash ----
    fun getDebugOverlay(): Boolean = p().getBoolean(Keys.DEBUG_OVERLAY, false)
    fun setDebugOverlay(v: Boolean) { p().edit().putBoolean(Keys.DEBUG_OVERLAY, v).apply() }

    fun getLastCrashSeen(): Long = p().getLong(Keys.LAST_CRASH_SEEN, 0L)
    fun setLastCrashSeen(ts: Long) { p().edit().putLong(Keys.LAST_CRASH_SEEN, ts).apply() }

    // ---- Tuning (física avanzada) ----
    fun loadTuning() {
        val s = p()
        Tuning.sensitivity = s.getFloat(Keys.SETTING_SENSITIVITY, Tuning.DEF_SENSITIVITY)
        Tuning.deadZoneDeg = s.getFloat(Keys.TUNE_DEAD_ZONE, Tuning.DEF_DEAD_ZONE_DEG)
        Tuning.maxTiltDeg = s.getFloat(Keys.TUNE_MAX_TILT, Tuning.DEF_MAX_TILT_DEG)
        Tuning.damping = s.getFloat(Keys.TUNE_DAMPING, Tuning.DEF_DAMPING)
        Tuning.restitution = s.getFloat(Keys.TUNE_RESTITUTION, Tuning.DEF_RESTITUTION)
        Tuning.wallFriction = s.getFloat(Keys.TUNE_WALL_FRICTION, Tuning.DEF_WALL_FRICTION)
        Tuning.sensorTauMs = s.getFloat(Keys.TUNE_SENSOR_TAU, Tuning.DEF_SENSOR_TAU_MS)
        Tuning.invertX = s.getBoolean(Keys.TUNE_INVERT_X, false)
        Tuning.invertY = s.getBoolean(Keys.TUNE_INVERT_Y, false)
        Tuning.swapXY = s.getBoolean(Keys.TUNE_SWAP_XY, false)
        Tuning.verticalGain = s.getFloat(Keys.TUNE_VERTICAL_GAIN, Tuning.DEF_VERTICAL_GAIN)
        Tuning.autoCamera = s.getBoolean(Keys.SETTING_AUTO_CAMERA, true)
    }

    fun saveTuning() {
        p().edit()
            .putFloat(Keys.SETTING_SENSITIVITY, Tuning.sensitivity)
            .putFloat(Keys.TUNE_DEAD_ZONE, Tuning.deadZoneDeg)
            .putFloat(Keys.TUNE_MAX_TILT, Tuning.maxTiltDeg)
            .putFloat(Keys.TUNE_DAMPING, Tuning.damping)
            .putFloat(Keys.TUNE_RESTITUTION, Tuning.restitution)
            .putFloat(Keys.TUNE_WALL_FRICTION, Tuning.wallFriction)
            .putFloat(Keys.TUNE_SENSOR_TAU, Tuning.sensorTauMs)
            .putBoolean(Keys.TUNE_INVERT_X, Tuning.invertX)
            .putBoolean(Keys.TUNE_INVERT_Y, Tuning.invertY)
            .putBoolean(Keys.TUNE_SWAP_XY, Tuning.swapXY)
            .putFloat(Keys.TUNE_VERTICAL_GAIN, Tuning.verticalGain)
            .apply()
    }

    /** Volcado legible para el informe de diagnóstico. */
    fun dump(): String {
        val sb = StringBuilder()
        for ((k, v) in p().all.toSortedMap()) sb.append(k).append('=').append(v).append('\n')
        return sb.toString()
    }
}
