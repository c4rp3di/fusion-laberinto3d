package com.laberinto3d.juego.game.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Capa de pantallas modales sobre el juego. Bloquea los toques hacia el GLSurfaceView
 * mientras hay alguna pantalla. Mantiene una pila: las subpantallas (Ajustes, Log...)
 * se apilan sobre la pantalla de estado (Pausa, Victoria...) y "Volver" las saca.
 */
class ScreenOverlay(context: Context) : FrameLayout(context) {
    private val stack = ArrayList<View>()

    init {
        setBackgroundColor(UiKit.COLOR_DIM)
        isClickable = true
        isFocusable = false
        visibility = GONE
    }

    val isShowing: Boolean get() = visibility == VISIBLE
    val depth: Int get() = stack.size

    /** Sustituye toda la pila por [view]. */
    fun setRoot(view: View) {
        stack.clear()
        stack.add(view)
        show()
    }

    /** Apila una subpantalla. */
    fun push(view: View) {
        stack.add(view)
        show()
    }

    /** Quita la subpantalla superior. Devuelve false si sólo queda la raíz (no se quita). */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        show()
        return true
    }

    /** Sustituye la subpantalla superior (o la raíz si no hay más) por [view]. */
    fun replaceTop(view: View) {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        stack.add(view)
        show()
    }

    fun clear() {
        stack.clear()
        removeAllViews()
        visibility = GONE
    }

    private fun show() {
        removeAllViews()
        val top = stack.lastOrNull()
        if (top == null) {
            visibility = GONE
            return
        }
        (top.parent as? ViewGroup)?.removeView(top)
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = VISIBLE
    }
}
