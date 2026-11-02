package com.laberinto3d.juego.game.diag

import android.content.Context
import android.os.Build
import com.laberinto3d.juego.game.core.Config
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captura excepciones no controladas de cualquier hilo y las vuelca a
 * filesDir/crash-YYYYMMDD-HHMMSS.txt con el stack y las últimas líneas del log.
 * Después delega en el manejador anterior para que el sistema muestre "app stopped".
 */
object CrashHandler {
    private var previous: Thread.UncaughtExceptionHandler? = null
    private var dir: File? = null
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        dir = context.filesDir
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                writeCrash(thread, error)
            } catch (_: Throwable) {
            }
            val prev = previous
            if (prev != null) prev.uncaughtException(thread, error)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }
        installed = true
    }

    fun uninstall() {
        if (installed) {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            installed = false
        }
    }

    private fun writeCrash(thread: Thread, error: Throwable) {
        val d = dir ?: return
        GameLog.e("CRASH", "Excepción no controlada en hilo ${thread.name}", error)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(d, "crash-$stamp.txt")
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val sb = StringBuilder()
        sb.append("CRASH ").append(Date()).append('\n')
        sb.append("Hilo: ").append(thread.name).append('\n')
        sb.append("Dispositivo: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ")
            .append(Build.VERSION.SDK_INT).append(")\n\n")
        sb.append(sw.toString()).append("\n--- Últimas líneas del log ---\n")
        sb.append(GameLog.text(Config.CRASH_LOG_LINES)).append('\n')
        f.writeText(sb.toString())
        GameLog.flushBlocking()
        // conservar sólo los 5 más recientes
        val all = crashFiles(d)
        for (i in 5 until all.size) all[i].delete()
    }

    private fun crashFiles(d: File): List<File> =
        (d.listFiles { _, n -> n.startsWith("crash-") && n.endsWith(".txt") } ?: emptyArray())
            .sortedByDescending { it.lastModified() }

    /** Último archivo de crash (el más reciente) o null. */
    fun latestCrash(): File? = dir?.let { crashFiles(it).firstOrNull() }

    /** Último crash que el usuario aún no ha visto (más reciente que [lastSeen]), o null. */
    fun unseenCrash(lastSeen: Long): File? {
        val f = latestCrash() ?: return null
        return if (f.lastModified() > lastSeen) f else null
    }

    fun read(f: File): String = try { f.readText() } catch (t: Throwable) { "No se pudo leer: $t" }
}
