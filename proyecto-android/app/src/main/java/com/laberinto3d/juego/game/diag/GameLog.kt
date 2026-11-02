package com.laberinto3d.juego.game.diag

import android.content.Context
import android.util.Log
import com.laberinto3d.juego.game.core.Config
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Log circular en memoria (500 líneas) con volcado a filesDir/game.log.
 * La sesión anterior se conserva en game.prev.log, útil tras un cierre inesperado.
 * Es seguro desde cualquier hilo; la escritura a disco va en un hilo aparte.
 */
object GameLog {
    private val lock = Any()
    private val buffer = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null
    private var prevFile: File? = null
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "game-log-io").apply { isDaemon = true }
    }
    @Volatile private var errorCount = 0

    fun init(context: Context) {
        val dir = context.filesDir
        val cur = File(dir, "game.log")
        val prev = File(dir, "game.prev.log")
        try {
            if (cur.exists() && cur.length() > 0) {
                if (prev.exists()) prev.delete()
                cur.renameTo(prev)
            }
        } catch (_: Throwable) {
        }
        logFile = cur
        prevFile = prev
        i("Log", "=== Nueva sesión · ${Date()} ===")
    }

    fun d(tag: String, msg: String) = add('D', tag, msg)
    fun i(tag: String, msg: String) = add('I', tag, msg)
    fun w(tag: String, msg: String) = add('W', tag, msg)
    fun e(tag: String, msg: String) = add('E', tag, msg)
    fun e(tag: String, msg: String, t: Throwable) = add('E', tag, msg + " :: " + t.toString())

    val errors: Int get() = errorCount

    private fun add(level: Char, tag: String, msg: String) {
        val stamp = synchronized(fmt) { fmt.format(Date()) }
        val line = "$stamp $level/$tag: $msg"
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > Config.LOG_MEMORY_LINES) buffer.removeFirst()
        }
        if (level == 'E') errorCount++
        try {
            when (level) {
                'D' -> Log.d("Laberinto3D/$tag", msg)
                'I' -> Log.i("Laberinto3D/$tag", msg)
                'W' -> Log.w("Laberinto3D/$tag", msg)
                else -> Log.e("Laberinto3D/$tag", msg)
            }
        } catch (_: Throwable) {
        }
        val f = logFile ?: return
        try {
            io.execute { append(f, line) }
        } catch (_: Throwable) {
        }
    }

    private fun append(f: File, line: String) {
        try {
            if (f.length() > Config.LOG_FILE_MAX_BYTES) {
                f.writeText("... (log truncado) ...\n")
            }
            FileWriter(f, true).use { it.write(line + "\n") }
        } catch (_: Throwable) {
        }
    }

    /** Últimas [n] líneas de la sesión actual. */
    fun lines(n: Int = Config.LOG_VIEW_LINES): List<String> = synchronized(lock) {
        val all = buffer.toList()
        if (all.size <= n) all else all.subList(all.size - n, all.size)
    }

    fun text(n: Int = Config.LOG_VIEW_LINES): String = lines(n).joinToString("\n")

    /** Texto de la sesión anterior (vacío si no hay). */
    fun previousSessionText(maxChars: Int = 60000): String {
        val f = prevFile ?: return ""
        return try {
            if (!f.exists()) "" else {
                val t = f.readText()
                if (t.length > maxChars) t.substring(t.length - maxChars) else t
            }
        } catch (_: Throwable) {
            ""
        }
    }

    /** Espera (brevemente) a que se vacíe la cola de escritura: se usa al crashear. */
    fun flushBlocking() {
        try {
            val f = io.submit { }
            f.get(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
        }
    }
}
