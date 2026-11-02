package com.laberinto3d.juego.game.core

/**
 * Constantes fijas del juego (no ajustables en ejecución).
 * Todo lo que el usuario puede tocar desde Ajustes vive en [Tuning].
 */
object Config {
    // ---- Física (sección 8.5 del plan) ----
    const val GRAVITY = 9.81f
    const val BALL_RADIUS = 0.15f
    const val WALL_HEIGHT = 0.20f
    const val WALL_THICKNESS = 0.20f
    const val STEP = 1f / 120f
    const val COLLISION_ITERATIONS = 2
    const val SPEED_WARN = 15f
    const val SPEED_MAX = 20f
    /** Por debajo de esta velocidad de impacto no hay rebote (evita temblor en reposo). */
    const val REST_THRESHOLD = 0.6f
    /** Impactos más lentos que esto no suenan ni vibran. */
    const val IMPACT_MIN_SPEED = 0.35f
    const val GOAL_HEIGHT_TOLERANCE = 0.3f
    /** Si la bola baja de esta altura se considera caída (estado LOST). */
    const val FALL_Y = -1.5f
    /** Margen fuera del tablero a partir del cual se considera que la bola se escapó. */
    const val OUT_OF_BOUNDS_MARGIN = 1.5f
    /** Pasos seguidos con penetración grave antes de reiniciar la bola ("atascada"). */
    const val STUCK_STEPS = 40
    const val STUCK_DEPTH = 0.06f

    /** Radio horizontal para activar un checkpoint y tolerancia de altura. */
    const val CHECKPOINT_RADIUS = 0.30f
    const val CHECKPOINT_HEIGHT_TOL = 0.30f

    // ---- Mapas de texto ----
    const val CELL = 0.6f
    const val HEIGHT_STEP = 0.3f
    const val FLOOR_BASE_Y = -0.4f

    // ---- Tiempo ----
    /** Tiempo máximo de un frame; el exceso se descarta (evita la espiral de la muerte). */
    const val MAX_FRAME_TIME = 0.1f
    const val HUD_PERIOD_MS = 100L

    // ---- Cámara ----
    const val CAMERA_ELEVATION_DEG = 52f
    const val CAMERA_FOV_DEG = 35f
    /** Zona de NDC donde debe caber el tablero (deja hueco arriba para el HUD). */
    const val FIT_MIN_X = -0.95f
    const val FIT_MAX_X = 0.95f
    const val FIT_MIN_Y = -0.93f
    const val FIT_MAX_Y = 0.80f

    // ---- Malla ----
    const val SPHERE_SEGMENTS = 48
    const val SPHERE_RINGS = 24
    const val MARBLE_SIZE = 512

    // ---- Sensor ----
    const val NEUTRAL_DEFAULT_X = 0f
    const val NEUTRAL_DEFAULT_Y = 0f
    const val NEUTRAL_DEFAULT_Z = 9.81f
    /** Suavizado extra del vector de gravedad en el hilo GL (segundos). */
    const val GRAVITY_SMOOTH_TAU = 0.03f

    // ---- Audio / vibración ----
    const val BOUNCE_VOLUME_REF_SPEED = 5f
    const val BOUNCE_VOLUME_MIN = 0.15f
    const val VIBRATE_MIN_SPEED = 3f
    const val VIBRATE_MS = 30L
    const val BOUNCE_SOUND_COOLDOWN_MS = 70L

    // ---- Diagnóstico ----
    const val LOG_MEMORY_LINES = 500
    const val LOG_VIEW_LINES = 200
    const val CRASH_LOG_LINES = 100
    const val LOG_FILE_MAX_BYTES = 256 * 1024
    const val DEBUG_LONG_PRESS_MS = 3000L
}

/**
 * Parámetros ajustables en ejecución (Ajustes → Física avanzada).
 * Se cargan de [com.laberinto3d.juego.game.storage.Prefs] al arrancar.
 * Los campos son @Volatile: los escribe el hilo UI y los lee el hilo GL.
 */
object Tuning {
    const val DEF_SENSITIVITY = 1.0f
    const val DEF_DEAD_ZONE_DEG = 1.5f
    const val DEF_MAX_TILT_DEG = 15f
    const val DEF_DAMPING = 0.6f
    const val DEF_RESTITUTION = 0.35f
    const val DEF_WALL_FRICTION = 0.15f
    const val DEF_SENSOR_TAU_MS = 80f
    const val DEF_VERTICAL_GAIN = 1.0f

    @Volatile var sensitivity = DEF_SENSITIVITY
    @Volatile var deadZoneDeg = DEF_DEAD_ZONE_DEG
    @Volatile var maxTiltDeg = DEF_MAX_TILT_DEG
    @Volatile var damping = DEF_DAMPING
    @Volatile var restitution = DEF_RESTITUTION
    @Volatile var wallFriction = DEF_WALL_FRICTION
    @Volatile var sensorTauMs = DEF_SENSOR_TAU_MS
    @Volatile var invertX = false
    @Volatile var invertY = false
    @Volatile var swapXY = false
    /** Ganancia extra del eje vertical de pantalla (además de la compensación de perspectiva). */
    @Volatile var verticalGain = DEF_VERTICAL_GAIN
    /** Cámara automática en la gran esfera: el giro de la vista sigue el sentido del recorrido. */
    @Volatile var autoCamera = true

    /** Restaura sólo los parámetros de física/sensor (no sonido, vibración ni ejes). */
    fun resetPhysics() {
        sensitivity = DEF_SENSITIVITY
        deadZoneDeg = DEF_DEAD_ZONE_DEG
        maxTiltDeg = DEF_MAX_TILT_DEG
        damping = DEF_DAMPING
        restitution = DEF_RESTITUTION
        wallFriction = DEF_WALL_FRICTION
        sensorTauMs = DEF_SENSOR_TAU_MS
        verticalGain = DEF_VERTICAL_GAIN
    }
}
