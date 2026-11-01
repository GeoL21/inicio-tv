package com.geol.inicio

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Hilos de trabajo fijos (en vez de crear uno nuevo cada vez):
 * - iconos: prepara los iconos del cajón mientras no los estás viendo.
 * - datos: consultas a Android (Seguir viendo).
 * - red: descargas (clima e imágenes), dos a la vez como máximo.
 */
object Hilos {
    private fun hilo(nombre: String, cantidad: Int = 1): ExecutorService =
        Executors.newFixedThreadPool(cantidad) { tarea ->
            Thread(tarea, nombre).apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }

    val iconos = hilo("iconos")
    val datos = hilo("datos")
    val red = hilo("red", 2)
}
