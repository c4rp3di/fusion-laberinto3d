package com.laberinto3d.juego

import android.app.Activity
import android.os.Bundle


/** Actividad única. Toda la lógica vive en [AppLogic]; aquí sólo se reenvían los callbacks. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppLogic.onIniciar(this)
    }

    override fun onResume() {
        super.onResume()
        AppLogic.onResume(this)
    }

    override fun onPause() {
        AppLogic.onPause(this)
        super.onPause()
    }

    override fun onDestroy() {
        AppLogic.onDestroy(this)
        super.onDestroy()
    }

    @Deprecated("Se usa el botón atrás clásico")
    override fun onBackPressed() {
        if (!AppLogic.onBack()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        AppLogic.onWindowFocusChanged(this, hasFocus)
    }
}
