package com.laberinto3d.juego.game.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.laberinto3d.juego.game.core.Config

/**
 * Enrutado de toques especiales. Por ahora: la zona oculta de la esquina superior izquierda
 * que abre/cierra el panel de depuración con una pulsación larga de 3 s.
 * No consume más toques que los que empiezan dentro de la zona.
 */
class TouchRouter(private val onDebugGesture: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var fired = false
    private val trigger = Runnable {
        fired = true
        onDebugGesture()
    }

    /** Vista invisible que se coloca en la esquina superior izquierda. */
    @SuppressLint("ClickableViewAccessibility")
    fun createHotspot(ctx: Context): View {
        val v = View(ctx)
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    handler.postDelayed(trigger, Config.DEBUG_LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(trigger)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // si el dedo sale mucho de la zona se cancela
                    if (e.x < -80f || e.y < -80f || e.x > v.width + 80f || e.y > v.height + 80f) {
                        handler.removeCallbacks(trigger)
                    }
                    true
                }
                else -> true
            }
        }
        return v
    }

    fun cancel() {
        handler.removeCallbacks(trigger)
    }
}
