package com.laberinto3d.juego.game.core

import com.laberinto3d.juego.game.audio.AudioManager
import com.laberinto3d.juego.game.diag.GameLog
import com.laberinto3d.juego.game.input.AccelerometerController
import com.laberinto3d.juego.game.input.CalibrationStore
import com.laberinto3d.juego.game.input.SphereInput
import com.laberinto3d.juego.game.input.TiltMapping
import com.laberinto3d.juego.game.physics.Vec3
import com.laberinto3d.juego.game.levels.Levels
import com.laberinto3d.juego.game.render.CameraControl
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.laberinto3d.juego.game.storage.Prefs
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.exp

/**
 * Orquestador del update. Se llama una vez por frame desde el hilo GL:
 * ejecuta las órdenes pendientes, lee el sensor, avanza la física con paso fijo,
 * detecta victoria/caída y publica datos al HUD. Devuelve el factor de interpolación.
 */
class GameLoop(private val accel: AccelerometerController) {
    private val commands = ConcurrentLinkedQueue<Runnable>()
    private val gravityTarget = Vec3(0f, -Config.GRAVITY, 0f)
    private val gravityNow = Vec3(0f, -Config.GRAVITY, 0f)
    private val angles = FloatArray(2)
    private val pend = FloatArray(3)
    private val gTmp = Vec3()

    private var lastNanos = 0L
    private var acc = 0f
    private var lastSession: Session? = null
    private var lastHudMs = 0L
    private var physAccumNs = 0L
    private var physFrames = 0

    /** Encola una orden para ejecutarse en el hilo GL al inicio del siguiente frame. */
    fun post(r: Runnable) {
        commands.add(r)
    }

    /** Un frame. Devuelve alpha (0..1) para interpolar el render entre el paso anterior y el actual. */
    fun update(nowNanos: Long): Float {
        while (true) {
            val c = commands.poll() ?: break
            try { c.run() } catch (t: Throwable) { GameLog.e("Loop", "Orden falló", t) }
        }
        val s = GameState.currentSession ?: return 1f
        if (s !== lastSession) {
            lastSession = s
            acc = 0f
            lastNanos = nowNanos
            hookSession(s)
        }

        var dt = if (lastNanos == 0L) 0f else (nowNanos - lastNanos) * 1e-9f
        lastNanos = nowNanos
        if (dt < 0f) dt = 0f
        if (dt > Config.MAX_FRAME_TIME) {
            Telemetry.droppedTimeMs += ((dt - Config.MAX_FRAME_TIME) * 1000f).toLong()
            dt = Config.MAX_FRAME_TIME
        }

        // gran esfera: la vista gira sola siguiendo el recorrido
        if (s.world.isSphere && !s.world.rotating && Tuning.autoCamera && !CameraControl.autoSuspended && s.phase == Phase.PLAYING && !CameraControl.gesturing) autoYaw(s, dt)

        // Sensor -> gravedad objetivo (cada frame, también fuera de PLAYING para el overlay)
        TiltMapping.compute(accel.filtered, CalibrationStore.neutral, accel.rotation, gravityTarget, angles)
        applyViewAndGain()
        Telemetry.tiltRollDeg = angles[0]
        Telemetry.tiltPitchDeg = angles[1]

        // fin de un gesto de dos dedos: los controles se realinean con la nueva vista
        if (CameraControl.gestureEnded && !CameraControl.gesturing) {
            CameraControl.gestureEnded = false
            accel.resetFilter()
            gravityNow.set(gravityTarget)
            acc = 0f
            GameLog.i("Loop", "Vista nueva: yaw=${"%.0f".format(Math.toDegrees(CameraControl.yaw.toDouble()))}° elev=${"%.0f".format(CameraControl.pitchDeg)}° zoom=${"%.2f".format(CameraControl.zoom)}")
        }

        var steps = 0
        var alpha = 1f
        if (s.phase == Phase.PLAYING && !CameraControl.gesturing) {
            val t0 = System.nanoTime()
            acc += dt
            val k = 1f - exp(-Config.STEP / Config.GRAVITY_SMOOTH_TAU)
            val p = s.physics
            val sph = s.sphere
            var tw0 = 0f; var tw1 = 0f; var tw2 = 0f
            if (sph != null) {
                SphereInput.take(pend)
                val fd = dt.coerceAtLeast(0.004f)
                tw0 = pend[0] / fd; tw1 = pend[1] / fd; tw2 = pend[2] / fd
            }
            while (acc >= Config.STEP) {
                gravityNow.x += (gravityTarget.x - gravityNow.x) * k
                gravityNow.y += (gravityTarget.y - gravityNow.y) * k
                gravityNow.z += (gravityTarget.z - gravityNow.z) * k
                if (sph != null) {
                    sph.step(Config.STEP, tw0, tw1, tw2, SphereInput.touching, 0.06f, 0.3f)
                    // inclinación del móvil: empuje horizontal como en los niveles planos (sin reducir por gravityScale)
                    val g = Config.GRAVITY
                    val fx = gravityNow.x * TILT_FINE; val fz = gravityNow.z * TILT_FINE
                    val fy = -g * s.world.gravityScale
                    val gs = 1f
                    sph.toBody(fx * gs, fy * gs, fz * gs, gTmp)
                    p.gravity.set(gTmp)
                    p.omega.set(sph.omegaB); p.alpha.set(sph.alphaB)
                } else p.gravity.set(gravityNow)
                p.step(Config.STEP)
                s.elapsedNanos += (Config.STEP * 1e9f).toLong()
                acc -= Config.STEP
                steps++
                if (p.won) { onWon(s); acc = 0f; break }
                if (p.fell) { onFell(s); acc = 0f; break }
            }
            alpha = (acc / Config.STEP).coerceIn(0f, 1f)
            physAccumNs += System.nanoTime() - t0
            physFrames++
            if (physFrames >= 30) {
                Telemetry.physicsMsAvg = physAccumNs / physFrames / 1e6f
                physAccumNs = 0L
                physFrames = 0
            }
        } else {
            acc = 0f
        }

        val b = s.ball
        Telemetry.stepsLastFrame = steps
        Telemetry.gx = gravityNow.x
        Telemetry.gy = gravityNow.y
        Telemetry.gz = gravityNow.z
        Telemetry.contactsLastStep = s.physics.contactsLastStep
        Telemetry.totalContacts = s.physics.totalContacts
        Telemetry.maxImpact = s.physics.maxImpactSeen
        Telemetry.stuckResets = s.physics.resetsByStuck

        // HUD ~cada 100 ms
        val nowMs = nowNanos / 1_000_000L
        if (nowMs - lastHudMs >= Config.HUD_PERIOD_MS) {
            lastHudMs = nowMs
            GameState.postHud(s)
        }
        if (b.position.y.isNaN()) {
            GameLog.e("Loop", "Posición NaN: se reinicia la bola")
            s.physics.resetBall()
        }
        return alpha
    }

    /**
     * Aplica a la gravedad calculada: compensación de perspectiva en el eje vertical de pantalla,
     * ganancia de usuario y giro por la orientación de la cámara (los controles siguen a la vista).
     */
    private fun applyViewAndGain() {
        val g = Config.GRAVITY
        val pitchRad = Math.toRadians(CameraControl.pitchDeg.toDouble())
        val persp = (1.0 / sin(pitchRad)).toFloat().coerceIn(1f, 1.5f)
        var gx = gravityTarget.x
        var gz = gravityTarget.z * persp * Tuning.verticalGain
        val lim = g * 0.8f
        val h = sqrt(gx * gx + gz * gz)
        if (h > lim) { val k = lim / h; gx *= k; gz *= k }
        val yaw = CameraControl.yaw
        val c = cos(yaw)
        val sn = sin(yaw)
        val wx = gx * c + gz * sn
        val wz = -gx * sn + gz * c
        val wy = -sqrt(maxOf(0f, g * g - wx * wx - wz * wz))
        gravityTarget.set(wx, wy, wz)
    }

    private var pathHint = 0

    /** Gira la cámara suavemente para que "arriba en pantalla" sea el sentido del recorrido, algo por delante de la bola. */
    private fun autoYaw(s: Session, dt: Float) {
        val pts = s.world.pathPts
        val n = pts.size / 3
        if (n < 12) return
        val b = s.ball.position
        var best = pathHint.coerceIn(0, n - 1)
        var bd = Float.MAX_VALUE
        val lo = maxOf(0, pathHint - 60); val hi = minOf(n - 1, pathHint + 60)
        for (i in lo..hi) {
            val dx = pts[i * 3] - b.x; val dy = pts[i * 3 + 1] - b.y; val dz = pts[i * 3 + 2] - b.z
            val d = dx * dx + dy * dy * 4f + dz * dz
            if (d < bd) { bd = d; best = i }
        }
        if (bd > 9f) {   // lejos del camino (caída, reaparición): búsqueda completa
            for (i in 0 until n) {
                val dx = pts[i * 3] - b.x; val dy = pts[i * 3 + 1] - b.y; val dz = pts[i * 3 + 2] - b.z
                val d = dx * dx + dy * dy * 4f + dz * dz
                if (d < bd) { bd = d; best = i }
            }
        }
        pathHint = best
        val j = minOf(n - 1, best + 9)
        val fx = pts[j * 3] - pts[best * 3]
        val fz = pts[j * 3 + 2] - pts[best * 3 + 2]
        if (fx * fx + fz * fz < 0.04f) return
        val target = kotlin.math.atan2(-fx, -fz)
        var diff = target - CameraControl.yaw
        val twoPi = (2.0 * Math.PI).toFloat()
        while (diff > Math.PI) diff -= twoPi
        while (diff < -Math.PI) diff += twoPi
        val step = (diff * 1.8f * dt).coerceIn(-1.2f * dt, 1.2f * dt)
        CameraControl.yaw += step
    }

    private fun alignSphere(s: Session) {
        val sph = s.sphere ?: return
        val p = s.physics
        sph.alignTo(p.spawnT.x, p.spawnT.y, p.spawnT.z, p.spawnU.x, p.spawnU.y, p.spawnU.z)
        SphereInput.clear()
    }

    private fun hookSession(s: Session) {
        pathHint = 0
        alignSphere(s)
        s.physics.respawnListener = { alignSphere(s) }
        s.physics.impactListener = { speed, _, _ ->
            AudioManager.playBounce(speed)
            if (speed > Config.VIBRATE_MIN_SPEED) AudioManager.vibrate()
        }
        s.physics.warnListener = { msg -> GameLog.w("Physics", msg) }
        s.physics.checkpointListener = { i ->
            GameLog.i("Loop", "Checkpoint ${i + 1}/${s.checkpointsTotal}")
            AudioManager.playCheckpoint()
            AudioManager.vibrate(40L)
            if (s.levelId == Levels.SPHERE_ID) try { Prefs.setSphereProgress(i, s.elapsedMs) } catch (_: Throwable) { }
            if (s.levelId == Levels.SPHERE3_ID) try { Prefs.setSphere3Progress(i, s.checkpointsTotal, s.elapsedMs) } catch (_: Throwable) { }
        }
        gravityNow.set(gravityTarget)
    }

    private fun onWon(s: Session) {
        val ms = s.elapsedMs
        val record = try { Prefs.setBestTime(s.levelId, ms) } catch (_: Throwable) { false }
        if (s.levelId == Levels.SPHERE_ID) try { Prefs.clearSphereProgress() } catch (_: Throwable) { }
        if (s.levelId == Levels.SPHERE3_ID) try { Prefs.clearSphere3Progress() } catch (_: Throwable) { }
        if (record) s.bestMs = ms
        s.lastResultMs = ms
        s.newRecord = record
        try {
            Prefs.unlockLevel(s.levelId + 1)
            GameState.unlockedLevel = Prefs.getUnlockedLevel()
        } catch (_: Throwable) { }
        GameLog.i("Loop", "Victoria nivel ${s.levelId}: ${ms}ms récord=$record")
        AudioManager.playGoal()
        AudioManager.vibrate(60L)
        GameState.transition(Phase.WON, "bola en la meta")
    }

    private fun onFell(s: Session) {
        s.falls++
        GameLog.w("Loop", "Caída #${s.falls} en ${s.ball.position}: reaparece (checkpoint ${s.physics.checkpointIndex + 1})")
        AudioManager.playFall()
        s.physics.resetBall()
        alignSphere(s)
        gravityNow.set(gravityTarget)
    }

    companion object {
        /** Esfera giratoria: fracción de la inclinación del móvil que se suma (1 = igual que en los niveles planos). */
        const val TILT_FINE = 1.0f
    }
}
