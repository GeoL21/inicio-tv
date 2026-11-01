package com.geol.inicio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

/**
 * Colores estilo Material You sacados del fondo de pantalla.
 * Del fondo se toma el tono dominante y con él se arman los colores de los
 * iconos temáticos (día y noche) y el velo suave del dock y de la barra.
 */
data class Paleta(val tono: Float, val intensidad: Float) {

    private fun hsv(s: Float, v: Float, alfa: Int = 255) =
        Color.HSVToColor(alfa, floatArrayOf(tono, (s * intensidad).coerceIn(0f, 1f), v))

    // Iconos temáticos
    fun fondoIcono(noche: Boolean) = if (noche) hsv(0.40f, 0.26f) else hsv(0.14f, 0.98f)
    fun logoIcono(noche: Boolean) = if (noche) hsv(0.22f, 0.96f) else hsv(0.62f, 0.42f)

    // Velo muy suave para el dock y los botones de arriba
    fun veloDock() = hsv(0.55f, 0.16f, 0x5C)
    fun veloBoton() = hsv(0.55f, 0.16f, 0x52)

    companion object {
        /** Azul suave para fondos sin color (grises, blanco y negro). */
        val PREDETERMINADA = Paleta(228f, 0.6f)

        /** Tono dominante del fondo: se pesan los colores vivos más que los apagados. */
        fun desde(fondo: Bitmap?): Paleta {
            fondo ?: return PREDETERMINADA
            val chico = Bitmap.createScaledBitmap(fondo, 48, 27, true)
            val cubetas = FloatArray(36)
            var satTotal = 0f
            val hsv = FloatArray(3)
            for (y in 0 until chico.height) for (x in 0 until chico.width) {
                Color.colorToHSV(chico.getPixel(x, y), hsv)
                satTotal += hsv[1]
                if (hsv[1] > 0.12f && hsv[2] > 0.12f) cubetas[(hsv[0] / 10f).toInt().coerceIn(0, 35)] += hsv[1] * hsv[2]
            }
            val max = cubetas.indices.maxByOrNull { cubetas[it] } ?: return PREDETERMINADA
            if (cubetas[max] <= 0f) return PREDETERMINADA
            // Promedio circular con las cubetas vecinas, para un tono más fino
            var sx = 0.0; var sy = 0.0
            for (k in -1..1) {
                val i = (max + k + 36) % 36
                val ang = Math.toRadians(i * 10.0 + 5.0)
                sx += Math.cos(ang) * cubetas[i]; sy += Math.sin(ang) * cubetas[i]
            }
            val tono = ((Math.toDegrees(Math.atan2(sy, sx)) + 360) % 360).toFloat()
            val satMedia = satTotal / (chico.width * chico.height)
            return Paleta(tono, (satMedia * 3f).coerceIn(0.35f, 1f))
        }
    }
}

/** Preferencia: iconos originales o temáticos. */
object EstiloIconos {
    private const val CLAVE = "iconos_tematicos"
    fun tematicos(ctx: Context) =
        ctx.getSharedPreferences("inicio", Context.MODE_PRIVATE).getBoolean(CLAVE, false)
    fun elegir(ctx: Context, tematicos: Boolean) =
        ctx.getSharedPreferences("inicio", Context.MODE_PRIVATE).edit().putBoolean(CLAVE, tematicos).apply()
}
