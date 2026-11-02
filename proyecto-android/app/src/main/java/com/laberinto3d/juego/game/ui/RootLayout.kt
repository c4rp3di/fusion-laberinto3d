package com.laberinto3d.juego.game.ui

import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import com.laberinto3d.juego.game.diag.DebugOverlay
import com.laberinto3d.juego.game.input.AccelerometerController
import com.laberinto3d.juego.game.render.GameSurfaceView

/**
 * Raíz de la pantalla: superficie GL a pantalla completa, HUD, panel de depuración y capa de menús.
 * Los márgenes de recorte de pantalla (notch) se aplican como padding a HUD, panel y menús.
 */
class RootLayout(
    context: Context,
    val glView: GameSurfaceView,
    accel: AccelerometerController,
    touch: TouchRouter
) : FrameLayout(context) {
    val hud = HudView(context, touch.createHotspot(context))
    val debug = DebugOverlay(context, accel)
    val overlay = ScreenOverlay(context)

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        addView(glView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(hud, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(debug, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var l = 0
        var t = 0
        var r = 0
        var b = 0
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                val c = insets.displayCutout
                if (c != null) {
                    l = c.safeInsetLeft; t = c.safeInsetTop; r = c.safeInsetRight; b = c.safeInsetBottom
                }
            }
            l = maxOf(l, insets.systemWindowInsetLeft)
            t = maxOf(t, insets.systemWindowInsetTop)
            r = maxOf(r, insets.systemWindowInsetRight)
            b = maxOf(b, insets.systemWindowInsetBottom)
        } catch (_: Throwable) { }
        for (v in arrayOf<View>(hud, debug, overlay)) v.setPadding(l, t, r, b)
        return insets
    }
}
