package com.geol.inicio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Fondos de pantalla: los incluidos en la app y los propios, que se copian a
 * /sdcard/Android/data/com.geol.inicio/files/fondos/ (JPG, PNG o WEBP).
 */
object Fondos {
    data class Fondo(val clave: String, val nombre: String)

    private const val PREFS = "inicio"
    private const val CLAVE = "fondo"

    private val INCLUIDOS = listOf(
        Triple("res:noche", "Noche", R.drawable.fondo),
        Triple("res:oceano", "Océano", R.drawable.fondo_oceano),
        Triple("res:atardecer", "Atardecer", R.drawable.fondo_atardecer),
        Triple("res:bosque", "Bosque", R.drawable.fondo_bosque),
        Triple("res:grafito", "Grafito", R.drawable.fondo_grafito),
    )
    private val EXTENSIONES = setOf("jpg", "jpeg", "png", "webp")

    private const val OCULTOS = "fondos_ocultos"

    /** Incluidos que el usuario borró (siguen dentro del APK, solo no se muestran). */
    private fun ocultos(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(OCULTOS, emptySet()) ?: emptySet()

    fun carpeta(ctx: Context): File? =
        ctx.getExternalFilesDir(null)?.let { File(it, "fondos").apply { mkdirs() } }

    private fun archivo(ctx: Context, clave: String): File? =
        if (clave.startsWith("archivo:")) carpeta(ctx)?.let { File(it, clave.removePrefix("archivo:")) } else null

    fun todos(ctx: Context): List<Fondo> {
        val propios = carpeta(ctx)?.listFiles { f -> f.extension.lowercase() in EXTENSIONES }
            ?.sortedBy { it.name.lowercase() }
            ?.map { Fondo("archivo:${it.name}", it.nameWithoutExtension) } ?: emptyList()
        val ocultos = ocultos(ctx)
        return INCLUIDOS.filter { it.first !in ocultos }.map { Fondo(it.first, it.second) } + propios
    }

    /**
     * Borra un fondo: los descargados o copiados se eliminan de la TV; los incluidos se ocultan.
     * Siempre debe quedar al menos uno. Si se borra el que está puesto, pasa al primero que quede.
     */
    fun borrar(ctx: Context, clave: String): Boolean {
        val lista = todos(ctx)
        if (lista.size <= 1 || lista.none { it.clave == clave }) return false
        val f = archivo(ctx, clave)
        if (f != null) { if (!f.delete()) return false }
        else {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(OCULTOS, ocultos(ctx) + clave).apply()
        }
        if (actual(ctx) == clave || ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLAVE, null) == clave)
            elegir(ctx, todos(ctx).first().clave)
        return true
    }

    /** Primer fondo disponible (por si el elegido ya no existe). */
    private fun primero(ctx: Context): String = todos(ctx).firstOrNull()?.clave ?: INCLUIDOS[0].first

    /** El fondo elegido; si el archivo elegido ya no existe, vuelve al original. */
    fun actual(ctx: Context): String {
        val clave = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CLAVE, null) ?: return primero(ctx)
        val f = archivo(ctx, clave)
        if (f != null && !f.exists()) return primero(ctx)
        if (f == null && clave in ocultos(ctx)) return primero(ctx)
        return clave
    }

    fun elegir(ctx: Context, clave: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CLAVE, clave).apply()
    }

    /** Cambia si se elige otro fondo o si se reemplaza el archivo elegido. */
    fun firma(ctx: Context): String {
        val clave = actual(ctx)
        return clave + (archivo(ctx, clave)?.lastModified() ?: 0L)
    }

    /** Carga un fondo reducido a un ancho máximo para ahorrar memoria. */
    fun cargar(ctx: Context, clave: String, anchoMax: Int): Bitmap? {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opciones = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val recurso = INCLUIDOS.firstOrNull { it.first == clave }?.third
        val f = archivo(ctx, clave)
        if (recurso == null && (f == null || !f.exists())) return null

        try {
            if (recurso != null) BitmapFactory.decodeResource(ctx.resources, recurso, medidas)
            else BitmapFactory.decodeFile(f!!.path, medidas)
        } catch (e: Throwable) { return null }
        if (medidas.outWidth <= 0) return null          // archivo dañado o que no es imagen
        var escala = 1
        while (medidas.outWidth / (escala * 2) >= anchoMax) escala *= 2
        opciones.inSampleSize = escala

        return try {
            if (recurso != null) BitmapFactory.decodeResource(ctx.resources, recurso, opciones)
            else BitmapFactory.decodeFile(f!!.path, opciones)
        } catch (e: Throwable) { null }
    }
}
