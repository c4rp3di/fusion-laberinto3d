package com.laberinto3d.juego.game.audio

import java.io.ByteArrayOutputStream
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Genera los efectos de sonido como WAV PCM 16 bit mono 44.1 kHz directamente en código.
 * Así el APK no necesita assets binarios (la opción D del plan).
 */
object SoundSynth {
    const val SAMPLE_RATE = 44100

    /** Golpe seco de madera, ~150 ms. */
    fun bounce(): ByteArray {
        val n = (SAMPLE_RATE * 0.15).toInt()
        val rnd = Random(7)
        val s = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val body = 0.6f * sin(2.0 * PI * 190.0 * t).toFloat() * exp(-t * 30f)
            val knock = 0.35f * sin(2.0 * PI * 430.0 * t).toFloat() * exp(-t * 48f)
            val click = 0.30f * (rnd.nextFloat() * 2f - 1f) * exp(-t * 130f)
            s[i] = body + knock + click
        }
        return toWav(s, 0.85f)
    }

    /** Arpegio ascendente de 3 notas, ~600 ms. */
    fun goal(): ByteArray {
        val n = (SAMPLE_RATE * 0.6).toInt()
        val s = FloatArray(n)
        val freqs = doubleArrayOf(523.25, 659.25, 783.99)
        for (k in freqs.indices) {
            val start = (k * 0.14 * SAMPLE_RATE).toInt()
            for (i in start until n) {
                val t = (i - start).toFloat() / SAMPLE_RATE
                val env = exp(-t * 5.5f) * (1f - exp(-t * 400f))
                val f = freqs[k]
                s[i] += (sin(2.0 * PI * f * t).toFloat() + 0.3f * sin(4.0 * PI * f * t).toFloat()) * env * 0.5f
            }
        }
        return toWav(s, 0.8f)
    }

    /** Click corto de botón, ~40 ms. */
    fun tap(): ByteArray {
        val n = (SAMPLE_RATE * 0.04).toInt()
        val s = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            s[i] = sin(2.0 * PI * 1600.0 * t).toFloat() * exp(-t * 110f)
        }
        return toWav(s, 0.6f)
    }

    /** Dos notas suaves ascendentes, ~350 ms. */
    fun checkpoint(): ByteArray {
        val n = (SAMPLE_RATE * 0.35).toInt()
        val s = FloatArray(n)
        val freqs = doubleArrayOf(880.0, 1318.5)
        for (k in freqs.indices) {
            val start = (k * 0.09 * SAMPLE_RATE).toInt()
            for (i in start until n) {
                val t = (i - start).toFloat() / SAMPLE_RATE
                val env = exp(-t * 9f) * (1f - exp(-t * 600f))
                s[i] += sin(2.0 * PI * freqs[k] * t).toFloat() * env * 0.6f
            }
        }
        return toWav(s, 0.7f)
    }

    /** Silbido descendente corto, ~350 ms. */
    fun fall(): ByteArray {
        val n = (SAMPLE_RATE * 0.35).toInt()
        val s = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val f = 900.0 * exp(-t * 4.0) + 120.0
            phase += 2.0 * PI * f / SAMPLE_RATE
            s[i] = sin(phase).toFloat() * (1f - t / 0.35f) * 0.8f
        }
        return toWav(s, 0.65f)
    }

    fun generate(id: Int): ByteArray = when (id) {
        SoundIds.BOUNCE -> bounce()
        SoundIds.GOAL -> goal()
        SoundIds.CHECKPOINT -> checkpoint()
        SoundIds.FALL -> fall()
        else -> tap()
    }

    /** Normaliza a [peak] y empaqueta como WAV. */
    fun toWav(samples: FloatArray, peak: Float): ByteArray {
        var max = 1e-6f
        for (v in samples) if (abs(v) > max) max = abs(v)
        val g = peak / max
        val data = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i] * g * 32767f).toInt().coerceIn(-32768, 32767)
            data[2 * i] = (v and 0xFF).toByte()
            data[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        val out = ByteArrayOutputStream(44 + data.size)
        fun w32(v: Int) { for (b in 0 until 4) out.write((v shr (8 * b)) and 0xFF) }
        fun w16(v: Int) { for (b in 0 until 2) out.write((v shr (8 * b)) and 0xFF) }
        out.write("RIFF".toByteArray())
        w32(36 + data.size)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        w32(16); w16(1); w16(1)
        w32(SAMPLE_RATE); w32(SAMPLE_RATE * 2); w16(2); w16(16)
        out.write("data".toByteArray())
        w32(data.size)
        out.write(data)
        return out.toByteArray()
    }
}
