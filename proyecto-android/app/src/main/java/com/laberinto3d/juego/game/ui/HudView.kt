package com.laberinto3d.juego.game.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.laberinto3d.juego.R
import com.laberinto3d.juego.game.audio.AudioManager

/** Botón de pausa dibujado a mano (sin recursos): dos barras sobre un fondo redondeado. */
class PauseButton(context: Context) : View(context) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    init {
        bg.color = UiKit.COLOR_HUD_BG
        bar.color = UiKit.COLOR_TEXT
        contentDescription = context.getString(R.string.pause_title)
        isClickable = true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rad = h * 0.3f
        r.set(0f, 0f, w, h)
        c.drawRoundRect(r, rad, rad, bg)
        val bw = w * 0.11f
        val bh = h * 0.40f
        val gap = w * 0.09f
        val cx = w / 2f
        val cy = h / 2f
        r.set(cx - gap / 2f - bw, cy - bh / 2f, cx - gap / 2f, cy + bh / 2f)
        c.drawRoundRect(r, bw / 3f, bw / 3f, bar)
        r.set(cx + gap / 2f, cy - bh / 2f, cx + gap / 2f + bw, cy + bh / 2f)
        c.drawRoundRect(r, bw / 3f, bw / 3f, bar)
    }
}

/**
 * Barra superior: nivel (izquierda), cronómetro (centro), mejor tiempo y pausa (derecha).
 * La esquina superior izquierda lleva una zona táctil invisible para abrir la depuración.
 */
class HudView(context: Context, hotspot: View) : FrameLayout(context) {
    private val levelText = TextView(context)
    private val timerText = TextView(context)
    private val bestText = TextView(context)
    private val extraText = TextView(context)
    val pauseButton = PauseButton(context)

    private fun panel(t: TextView, mono: Boolean, sizeSp: Float) {
        t.setTextColor(UiKit.COLOR_TEXT)
        t.textSize = sizeSp
        t.gravity = Gravity.CENTER
        if (mono) t.typeface = android.graphics.Typeface.MONOSPACE
        t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.background = UiKit.roundRect(UiKit.COLOR_HUD_BG, UiKit.dp(context, 14).toFloat())
        t.setPadding(UiKit.dp(context, 12), UiKit.dp(context, 6), UiKit.dp(context, 12), UiKit.dp(context, 6))
        t.minHeight = UiKit.dp(context, 40)
    }

    init {
        val m = UiKit.dp(context, 10)
        panel(levelText, false, 14f)
        panel(timerText, true, 20f)
        panel(bestText, true, 13f)
        panel(extraText, false, 12f)
        extraText.minHeight = UiKit.dp(context, 28)
        extraText.setPadding(UiKit.dp(context, 10), UiKit.dp(context, 3), UiKit.dp(context, 10), UiKit.dp(context, 3))
        extraText.visibility = GONE
        timerText.text = UiKit.formatTimeHud(0)
        bestText.text = "—"

        addView(levelText, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).also {
            it.setMargins(m, m, m, m)
        })
        val center = LinearLayout(context)
        center.orientation = LinearLayout.VERTICAL
        center.gravity = Gravity.CENTER_HORIZONTAL
        center.addView(timerText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        center.addView(extraText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = UiKit.dp(context, 4)
        })
        addView(center, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).also {
            it.setMargins(m, m, m, m)
        })
        val right = LinearLayout(context)
        right.orientation = LinearLayout.HORIZONTAL
        right.gravity = Gravity.CENTER_VERTICAL
        right.addView(bestText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val size = UiKit.dp(context, 48)
        right.addView(pauseButton, LinearLayout.LayoutParams(size, size).also { it.leftMargin = UiKit.dp(context, 8) })
        addView(right, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).also {
            it.setMargins(m, m, m, m)
        })
        // zona oculta de depuración en la esquina superior izquierda
        addView(hotspot, LayoutParams(UiKit.dp(context, 110), UiKit.dp(context, 60), Gravity.TOP or Gravity.START))
    }

    @SuppressLint("SetTextI18n")
    fun update(levelId: Int, levelName: String, elapsedMs: Long, bestMs: Long,
               checkpoints: Int = 0, checkpointsTotal: Int = 0, falls: Int = 0) {
        val parts = ArrayList<String>(2)
        if (checkpointsTotal > 0) parts.add(context.getString(R.string.hud_checkpoint, checkpoints, checkpointsTotal))
        if (falls > 0) parts.add(context.getString(R.string.hud_falls, falls))
        if (parts.isEmpty()) {
            if (extraText.visibility != GONE) extraText.visibility = GONE
        } else {
            val t = parts.joinToString("  ·  ")
            if (extraText.text.toString() != t) extraText.text = t
            if (extraText.visibility != VISIBLE) extraText.visibility = VISIBLE
        }
        val lvl = context.getString(R.string.level_label, levelId, levelName)
        if (levelText.text.toString() != lvl) levelText.text = lvl
        timerText.text = UiKit.formatTimeHud(elapsedMs)
        bestText.text = if (bestMs > 0) "★ " + UiKit.formatTimeHud(bestMs) else "★ —"
    }

    fun setOnPause(l: () -> Unit) {
        pauseButton.setOnClickListener {
            AudioManager.playTap()
            l()
        }
    }
}
