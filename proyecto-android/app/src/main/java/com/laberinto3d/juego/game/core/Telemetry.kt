package com.laberinto3d.juego.game.core

/**
 * Valores de diagnóstico que publican el hilo GL y el de sensores y lee el overlay de
 * depuración. Las lecturas son informativas: no importa si alguna llega un frame tarde.
 */
object Telemetry {
    @Volatile @JvmField var fps = 0f
    @Volatile @JvmField var frameMsAvg = 0f
    @Volatile @JvmField var physicsMsAvg = 0f
    @Volatile @JvmField var renderMsAvg = 0f
    @Volatile @JvmField var stepsLastFrame = 0
    @Volatile @JvmField var droppedTimeMs = 0L
    @Volatile @JvmField var glVersion = "?"
    @Volatile @JvmField var glRenderer = "?"
    @Volatile @JvmField var glVendor = "?"
    @Volatile @JvmField var glslVersion = "?"
    @Volatile @JvmField var msaaSamples = 0
    @Volatile @JvmField var surfaceW = 0
    @Volatile @JvmField var surfaceH = 0
    @Volatile @JvmField var glErrors = 0

    @Volatile @JvmField var gx = 0f
    @Volatile @JvmField var gy = 0f
    @Volatile @JvmField var gz = 0f
    @Volatile @JvmField var tiltRollDeg = 0f
    @Volatile @JvmField var tiltPitchDeg = 0f
    @Volatile @JvmField var contactsLastStep = 0
    @Volatile @JvmField var totalContacts = 0L
    @Volatile @JvmField var maxImpact = 0f
    @Volatile @JvmField var stuckResets = 0
    @Volatile @JvmField var camDistance = 0f
}
