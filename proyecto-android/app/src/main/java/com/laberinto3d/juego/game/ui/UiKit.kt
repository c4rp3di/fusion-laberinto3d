package com.laberinto3d.juego.game.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.laberinto3d.juego.game.audio.AudioManager

/** Paleta y constructores de vistas (la interfaz se construye por código, sin AppCompat). */
object UiKit {
    val COLOR_PANEL = Color.argb(235, 20, 20, 32)
    val COLOR_HUD_BG = Color.argb(191, 20, 20, 32)
    val COLOR_DIM = Color.argb(150, 8, 8, 16)
    val COLOR_TEXT = Color.parseColor("#F2F0FF")
    val COLOR_TEXT2 = Color.parseColor("#A8A6C0")
    val COLOR_PRIMARY = Color.parseColor("#6C5CE7")
    val COLOR_SECONDARY = Color.parseColor("#3D3D62")
    val COLOR_SUCCESS = Color.parseColor("#7FE0BC")
    val COLOR_DANGER = Color.parseColor("#E59AA6")

    enum class Kind { PRIMARY, SECONDARY, DANGER }

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dp(ctx: Context, v: Int): Int = dp(ctx, v.toFloat())

    fun roundRect(color: Int, radiusPx: Float): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = radiusPx
        g.setColor(color)
        return g
    }

    fun title(ctx: Context, text: CharSequence): TextView {
        val t = TextView(ctx)
        t.text = text
        t.setTextColor(COLOR_TEXT)
        t.textSize = 24f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setPadding(0, 0, 0, dp(ctx, 6))
        return t
    }

    fun body(ctx: Context, text: CharSequence, secondary: Boolean = false, sizeSp: Float = 16f): TextView {
        val t = TextView(ctx)
        t.text = text
        t.setTextColor(if (secondary) COLOR_TEXT2 else COLOR_TEXT)
        t.textSize = sizeSp
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(ctx, 2), 0, dp(ctx, 2))
        return t
    }

    fun mono(ctx: Context, sizeSp: Float = 11f): TextView {
        val t = TextView(ctx)
        t.setTextColor(COLOR_TEXT)
        t.textSize = sizeSp
        t.typeface = Typeface.MONOSPACE
        t.setTextIsSelectable(false)
        return t
    }

    /** Botón redondeado de al menos 48dp de alto. [tap] reproduce el click del juego. */
    fun button(ctx: Context, text: CharSequence, kind: Kind = Kind.SECONDARY, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = text
        b.isAllCaps = false
        b.setTextColor(COLOR_TEXT)
        b.textSize = 16f
        b.typeface = Typeface.DEFAULT_BOLD
        b.minHeight = dp(ctx, 48)
        b.minimumHeight = dp(ctx, 48)
        b.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
        b.stateListAnimator = null
        val base = when (kind) {
            Kind.PRIMARY -> COLOR_PRIMARY
            Kind.SECONDARY -> COLOR_SECONDARY
            Kind.DANGER -> Color.parseColor("#7A3B48")
        }
        val shape = roundRect(base, dp(ctx, 14).toFloat())
        b.background = RippleDrawable(
            ColorStateList.valueOf(Color.argb(90, 255, 255, 255)), shape, roundRect(Color.WHITE, dp(ctx, 14).toFloat())
        )
        b.setOnClickListener {
            AudioManager.playTap()
            onClick()
        }
        return b
    }

    fun space(ctx: Context, heightDp: Int): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(ctx, heightDp))
        return v
    }

    fun vParams(ctx: Context, topDp: Int = 0): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ctx, topDp)
        return lp
    }

    /** Tarjeta vertical con fondo redondeado. */
    fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.background = roundRect(COLOR_PANEL, dp(ctx, 22).toFloat())
        val p = dp(ctx, 20)
        c.setPadding(p, dp(ctx, 16), p, dp(ctx, 16))
        return c
    }

    /** Fila con las vistas repartidas a partes iguales y separación de 10dp. */
    fun row(ctx: Context, vararg views: View, topDp: Int = 10): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.layoutParams = vParams(ctx, topDp)
        for ((i, v) in views.withIndex()) {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.leftMargin = dp(ctx, 10)
            r.addView(v, lp)
        }
        return r
    }

    fun toggleRow(ctx: Context, label: CharSequence, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.layoutParams = vParams(ctx, 8)
        r.minimumHeight = dp(ctx, 48)
        val t = TextView(ctx)
        t.text = label
        t.setTextColor(COLOR_TEXT)
        t.textSize = 16f
        r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(ctx)
        sw.isChecked = checked
        sw.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(COLOR_SUCCESS, COLOR_TEXT2)
        )
        sw.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.argb(140, 127, 224, 188), Color.argb(110, 168, 166, 192))
        )
        sw.setOnCheckedChangeListener { _, v ->
            AudioManager.playTap()
            onChange(v)
        }
        r.addView(sw)
        return r
    }

    /**
     * Deslizador con etiqueta y valor. [min]..[max] en pasos de [step]; [format] da el texto del valor.
     * Devuelve la fila; [onChange] se llama al mover.
     */
    fun sliderRow(
        ctx: Context, label: CharSequence, min: Float, max: Float, step: Float, value: Float,
        format: (Float) -> String, onChange: (Float) -> Unit
    ): LinearLayout {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = vParams(ctx, 8)
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        val l = TextView(ctx)
        l.text = label
        l.setTextColor(COLOR_TEXT)
        l.textSize = 16f
        head.addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val v = TextView(ctx)
        v.setTextColor(COLOR_SUCCESS)
        v.textSize = 16f
        v.typeface = Typeface.DEFAULT_BOLD
        v.text = format(value)
        head.addView(v)
        col.addView(head)
        val steps = Math.round((max - min) / step)
        val sb = SeekBar(ctx)
        sb.max = steps
        sb.progress = Math.round((value - min) / step).coerceIn(0, steps)
        sb.progressTintList = ColorStateList.valueOf(COLOR_PRIMARY)
        sb.thumbTintList = ColorStateList.valueOf(COLOR_SUCCESS)
        sb.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                val nv = min + p * step
                v.text = format(nv)
                if (fromUser) onChange(nv)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        col.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return col
    }

    /** Envuelve [content] en un ScrollView que lo centra si cabe y permite desplazarlo si no. */
    fun scrollCentered(ctx: Context, content: View, maxWidthDp: Int): ScrollView {
        val sv = ScrollView(ctx)
        sv.isFillViewport = true
        sv.isVerticalScrollBarEnabled = false
        val holder = FrameLayout(ctx)
        val lp = FrameLayout.LayoutParams(dp(ctx, maxWidthDp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        lp.topMargin = dp(ctx, 12)
        lp.bottomMargin = dp(ctx, 12)
        holder.addView(content, lp)
        sv.addView(holder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return sv
    }

    /** mm:ss.mmm */
    fun formatTime(ms: Long): String {
        val t = if (ms < 0) 0L else ms
        val m = t / 60000
        val s = (t / 1000) % 60
        val r = t % 1000
        return String.format(java.util.Locale.US, "%02d:%02d.%03d", m, s, r)
    }

    /** mm:ss.cc (centésimas) para el cronómetro del HUD. */
    fun formatTimeHud(ms: Long): String {
        val t = if (ms < 0) 0L else ms
        val m = t / 60000
        val s = (t / 1000) % 60
        val c = (t % 1000) / 10
        return String.format(java.util.Locale.US, "%02d:%02d.%02d", m, s, c)
    }
}
