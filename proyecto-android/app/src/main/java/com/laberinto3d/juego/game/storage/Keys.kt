package com.laberinto3d.juego.game.storage

/** Claves tipadas de SharedPreferences (sección 14.3 del plan + ajustes de física). */
object Keys {
    const val FILE = "laberinto3d_prefs"

    const val SENSOR_NEUTRAL_X = "sensor_neutral_x"
    const val SENSOR_NEUTRAL_Y = "sensor_neutral_y"
    const val SENSOR_NEUTRAL_Z = "sensor_neutral_z"
    const val HAS_NEUTRAL = "sensor_has_neutral"

    const val UNLOCKED_LEVEL = "unlocked_level"

    const val SETTING_SOUND = "setting_sound"
    const val SETTING_VIBRATION = "setting_vibration"
    const val SETTING_SENSITIVITY = "setting_sensitivity"

    const val LAST_CRASH_SEEN = "last_crash_seen"
    const val DEBUG_OVERLAY = "debug_overlay"
    const val SETTING_AUTO_CAMERA = "setting_auto_camera"
    const val SPHERE_CP = "sphere1_checkpoint"
    const val SPHERE_MS = "sphere1_elapsed_ms"

    // Ajustes avanzados / depuración
    const val TUNE_DEAD_ZONE = "tune_dead_zone_deg"
    const val TUNE_MAX_TILT = "tune_max_tilt_deg"
    const val TUNE_DAMPING = "tune_damping"
    const val TUNE_RESTITUTION = "tune_restitution"
    const val TUNE_WALL_FRICTION = "tune_wall_friction"
    const val TUNE_SENSOR_TAU = "tune_sensor_tau_ms"
    const val TUNE_INVERT_X = "tune_invert_x"
    const val TUNE_INVERT_Y = "tune_invert_y"
    const val TUNE_SWAP_XY = "tune_swap_xy"
    const val TUNE_VERTICAL_GAIN = "tune_vertical_gain"

    fun bestTime(levelId: Int): String = "best_time_$levelId"
}
