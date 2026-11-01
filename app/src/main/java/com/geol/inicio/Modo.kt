package com.geol.inicio

import android.content.Context
import java.util.Calendar

/**
 * Modo de los iconos: automático (noche de 6 p. m. a 6 a. m.), noche fija o día fijo.
 * El botón de la barra recorre: Automático → Noche → Día → Automático.
 */
object Modo {
    enum class Estado { AUTOMATICO, NOCHE, DIA }

    private const val CLAVE = "modo_iconos"

    fun estado(ctx: Context): Estado = try {
        Estado.valueOf(ctx.getSharedPreferences("inicio", Context.MODE_PRIVATE)
            .getString(CLAVE, Estado.AUTOMATICO.name)!!)
    } catch (e: Exception) { Estado.AUTOMATICO }

    /** Pasa al siguiente estado y lo devuelve. */
    fun siguiente(ctx: Context): Estado {
        val nuevo = when (estado(ctx)) {
            Estado.AUTOMATICO -> Estado.NOCHE
            Estado.NOCHE -> Estado.DIA
            Estado.DIA -> Estado.AUTOMATICO
        }
        ctx.getSharedPreferences("inicio", Context.MODE_PRIVATE).edit().putString(CLAVE, nuevo.name).apply()
        return nuevo
    }

    fun esHoraDeNoche(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 18 || h < 6
    }

    fun esNoche(ctx: Context): Boolean = when (estado(ctx)) {
        Estado.AUTOMATICO -> esHoraDeNoche()
        Estado.NOCHE -> true
        Estado.DIA -> false
    }
}
