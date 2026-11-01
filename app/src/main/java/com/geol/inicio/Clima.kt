package com.geol.inicio

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Clima actual desde Open-Meteo (gratis, sin clave). Por defecto, Barranquilla. */
object Clima {
    const val LATITUD = 10.96
    const val LONGITUD = -74.80

    data class Dato(val grados: Int, val descripcion: String, val icono: Int)

    /** Se llama en un hilo aparte; devuelve null si no hay internet. */
    fun consultar(): Dato? = try {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$LATITUD&longitude=$LONGITUD" +
                "&current=temperature_2m,weather_code&timezone=auto"
        )
        val con = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 8000
        }
        if (con.responseCode != 200) { con.disconnect(); throw java.io.IOException() }
        val texto = con.inputStream.bufferedReader().use { it.readText() }
        con.disconnect()
        val actual = JSONObject(texto).getJSONObject("current")
        val codigo = actual.getInt("weather_code")
        val (descripcion, icono) = traducir(codigo)
        Dato(actual.getDouble("temperature_2m").roundToInt(), descripcion, icono)
    } catch (e: Exception) { null }

    /** Códigos WMO a texto en español. */
    private fun traducir(codigo: Int): Pair<String, Int> = when (codigo) {
        0 -> "Despejado" to R.drawable.ic_sol
        1, 2 -> "Parcialmente nublado" to R.drawable.ic_nube
        3 -> "Nublado" to R.drawable.ic_nube
        45, 48 -> "Neblina" to R.drawable.ic_nube
        in 51..67, in 80..82 -> "Lluvia" to R.drawable.ic_lluvia
        in 95..99 -> "Tormenta" to R.drawable.ic_lluvia
        else -> "" to R.drawable.ic_nube
    }
}
