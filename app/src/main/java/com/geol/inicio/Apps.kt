package com.geol.inicio

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/** Una app que se puede abrir desde el inicio. */
data class App(val paquete: String, val nombre: String, val icono: Drawable)

object Apps {
    private const val PREFS = "inicio"
    private const val CLAVE_FIJADAS = "fijadas"

    /** Las apps que vienen fijadas la primera vez: Netflix y Play Store. */
    private val FIJADAS_POR_DEFECTO = listOf("com.netflix.ninja", "com.android.vending")

    /** Todas las apps que se pueden abrir (de TV y normales), sin repetir y en orden alfabético. */
    fun todas(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val vistas = LinkedHashMap<String, App>()
        for (categoria in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(categoria)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val paquete = info.activityInfo.packageName
                if (paquete == ctx.packageName || vistas.containsKey(paquete)) continue
                vistas[paquete] = App(paquete, info.loadLabel(pm).toString(), info.loadIcon(pm))
            }
        }
        return vistas.values.sortedBy { it.nombre.lowercase() }
    }

    fun fijadas(ctx: Context): List<String> {
        val guardadas = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CLAVE_FIJADAS, null) ?: return FIJADAS_POR_DEFECTO
        return guardadas.split(",").filter { it.isNotBlank() }
    }

    /** Fija o quita una app del inicio. Devuelve true si quedó fijada. */
    fun alternarFijada(ctx: Context, paquete: String): Boolean {
        val lista = fijadas(ctx).toMutableList()
        val fijada = if (lista.remove(paquete)) false else { lista.add(paquete); true }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(CLAVE_FIJADAS, lista.joinToString(",")).apply()
        return fijada
    }

    fun abrir(ctx: Context, paquete: String): Boolean {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(paquete)
            ?: pm.getLaunchIntentForPackage(paquete) ?: return false
        return try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: Exception) { false }
    }
}
