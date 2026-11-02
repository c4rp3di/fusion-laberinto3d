package com.laberinto3d.juego.game.diag

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.laberinto3d.juego.R
import com.laberinto3d.juego.game.core.Config
import com.laberinto3d.juego.game.ui.UiKit

/** Pantalla de registro: sesión actual, sesión anterior y último informe de fallo. */
object LogViewer {
    enum class Source { CURRENT, PREVIOUS, CRASH }

    fun create(activity: Activity, onBack: () -> Unit, initial: Source = Source.CURRENT): View {
        val ctx = activity
        var source = initial

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.background = UiKit.roundRect(UiKit.COLOR_PANEL, UiKit.dp(ctx, 0).toFloat())
        root.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 10), UiKit.dp(ctx, 14), UiKit.dp(ctx, 10))

        val title = UiKit.title(ctx, ctx.getString(R.string.log_title))
        root.addView(title)

        val text = UiKit.mono(ctx, 10f)
        text.setPadding(UiKit.dp(ctx, 8), UiKit.dp(ctx, 6), UiKit.dp(ctx, 8), UiKit.dp(ctx, 6))
        text.background = UiKit.roundRect(android.graphics.Color.argb(200, 0, 0, 0), UiKit.dp(ctx, 8).toFloat())
        val sv = ScrollView(ctx)
        sv.addView(text, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun content(): String = when (source) {
            Source.CURRENT -> GameLog.text(Config.LOG_VIEW_LINES)
            Source.PREVIOUS -> GameLog.previousSessionText()
            Source.CRASH -> {
                val f = CrashHandler.latestCrash()
                if (f == null) ctx.getString(R.string.log_no_crash) else CrashHandler.read(f)
            }
        }

        fun refresh(scrollToEnd: Boolean) {
            val t = content()
            text.text = if (t.isEmpty()) ctx.getString(R.string.log_empty) else t
            if (scrollToEnd && source != Source.CRASH) sv.post { sv.fullScroll(View.FOCUS_DOWN) }
            else sv.post { sv.scrollTo(0, 0) }
        }

        fun tab(label: Int, s: Source) = UiKit.button(ctx, ctx.getString(label)) {
            source = s
            refresh(true)
        }

        root.addView(UiKit.row(ctx,
            tab(R.string.log_current, Source.CURRENT),
            tab(R.string.log_previous, Source.PREVIOUS),
            tab(R.string.log_crash, Source.CRASH),
            UiKit.button(ctx, ctx.getString(R.string.log_refresh)) { refresh(true) },
            topDp = 8))
        root.addView(UiKit.row(ctx,
            UiKit.button(ctx, ctx.getString(R.string.log_copy)) {
                Report.copy(activity, text.text.toString())
                Toast.makeText(ctx, R.string.debug_copied, Toast.LENGTH_SHORT).show()
            },
            UiKit.button(ctx, ctx.getString(R.string.log_share)) {
                Report.share(activity, ctx.getString(R.string.log_title), text.text.toString(), ctx.getString(R.string.share_chooser))
            },
            UiKit.button(ctx, ctx.getString(R.string.settings_back), UiKit.Kind.PRIMARY) { onBack() },
            topDp = 8))

        refresh(true)
        return root
    }
}
