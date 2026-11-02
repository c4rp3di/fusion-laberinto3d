package com.laberinto3d.juego.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.diag.GameLog
import java.io.File

/**
 * Efectos de sonido con SoundPool y vibración. Si algo falla, el juego sigue sin sonido.
 * Los WAV se sintetizan en código y se guardan en cacheDir al primer arranque.
 */
object AudioManager {
    private var pool: SoundPool? = null
    private val soundIds = IntArray(SoundIds.COUNT) { 0 }
    private val loaded = BooleanArray(SoundIds.COUNT)
    private var vibrator: Vibrator? = null
    private val main = Handler(Looper.getMainLooper())
    private var lastBounceMs = 0L

    @Volatile var soundEnabled = true
    @Volatile var vibrationEnabled = true
    @Volatile var ready = false
        private set

    val loadedCount: Int get() = loaded.count { it }

    fun init(context: Context) {
        release()
        val ctx = context.applicationContext
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val sp = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
            sp.setOnLoadCompleteListener { _, sampleId, status ->
                val idx = soundIds.indexOf(sampleId)
                if (idx >= 0) {
                    loaded[idx] = status == 0
                    if (status != 0) GameLog.e("Audio", "Carga fallida de ${SoundIds.LABELS[idx]} (status=$status)")
                }
            }
            pool = sp
            val dir = File(ctx.cacheDir, "sfx_v1")
            dir.mkdirs()
            for (i in 0 until SoundIds.COUNT) {
                try {
                    val f = File(dir, SoundIds.FILE_NAMES[i])
                    if (!f.exists() || f.length() < 100) f.writeBytes(SoundSynth.generate(i))
                    soundIds[i] = sp.load(f.absolutePath, 1)
                } catch (t: Throwable) {
                    GameLog.e("Audio", "No se pudo preparar ${SoundIds.LABELS[i]}", t)
                }
            }
            ready = true
        } catch (t: Throwable) {
            GameLog.e("Audio", "SoundPool no disponible", t)
            pool = null
        }
        try {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (t: Throwable) {
            GameLog.e("Audio", "Vibrador no disponible", t)
        }
    }

    private fun play(id: Int, volume: Float, retry: Boolean = true) {
        if (!soundEnabled) return
        val sp = pool ?: return
        val sid = soundIds[id]
        if (sid == 0) return
        if (!loaded[id]) {
            if (retry) main.postDelayed({ play(id, volume, false) }, 200)
            return
        }
        try {
            sp.play(sid, volume, volume, 1, 0, 1f)
        } catch (t: Throwable) {
            GameLog.e("Audio", "play falló", t)
        }
    }

    /** Volumen modulado por la velocidad de impacto (clamp(v/5, 0.15, 1)). */
    fun playBounce(speed: Float) {
        val now = System.currentTimeMillis()
        if (now - lastBounceMs < Config.BOUNCE_SOUND_COOLDOWN_MS) return
        lastBounceMs = now
        val vol = (speed / Config.BOUNCE_VOLUME_REF_SPEED).coerceIn(Config.BOUNCE_VOLUME_MIN, 1f)
        play(SoundIds.BOUNCE, vol)
    }

    fun playGoal() = play(SoundIds.GOAL, 1f)
    fun playTap() = play(SoundIds.TAP, 0.8f)
    fun playCheckpoint() = play(SoundIds.CHECKPOINT, 0.9f)
    fun playFall() = play(SoundIds.FALL, 0.8f)

    /** Reproduce un sonido aunque el usuario tenga el sonido apagado (prueba desde depuración). */
    fun playTest(id: Int) {
        val prev = soundEnabled
        soundEnabled = true
        play(id, 1f)
        soundEnabled = prev
    }

    fun vibrate(ms: Long = Config.VIBRATE_MS) {
        if (!vibrationEnabled) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (t: Throwable) {
            GameLog.e("Audio", "vibrate falló", t)
        }
    }

    fun vibrateTest(ms: Long = 120L) {
        val prev = vibrationEnabled
        vibrationEnabled = true
        vibrate(ms)
        vibrationEnabled = prev
    }

    val hasVibrator: Boolean get() = try { vibrator?.hasVibrator() == true } catch (_: Throwable) { false }

    fun pauseAll() {
        try { pool?.autoPause() } catch (_: Throwable) { }
    }

    fun resumeAll() {
        try { pool?.autoResume() } catch (_: Throwable) { }
    }

    fun release() {
        try { pool?.release() } catch (_: Throwable) { }
        pool = null
        for (i in soundIds.indices) { soundIds[i] = 0; loaded[i] = false }
        ready = false
    }
}
