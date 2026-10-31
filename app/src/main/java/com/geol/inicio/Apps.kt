package com.geol.inicio

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo

/** Una app que se puede abrir desde el inicio. `info` es null en accesos propios (Canales TV). */
data class App(val paquete: String, val nombre: String, val info: ActivityInfo?)

object Apps {
    private const val PREFS = "inicio"
    private const val CLAVE_FIJADAS = "fijadas"
    private const val CLAVE_CABLE_AGREGADO = "canales_tv_agregado"

    /** Acceso directo propio a los canales por cable (solo en equipos con entrada de TV). */
    const val CANALES_TV = "interno:canales_tv"

    /** Las apps que vienen fijadas la primera vez. */
    private val FIJADAS_POR_DEFECTO = listOf(CANALES_TV, "com.netflix.ninja", "com.android.vending")

    /** Todas las apps que se pueden abrir (de TV y normales), sin repetir y en orden alfabético. */
    fun todas(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val vistas = LinkedHashMap<String, App>()
        for (categoria in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(categoria)
            for (r in pm.queryIntentActivities(intent, 0)) {
                val paquete = r.activityInfo.packageName
                if (paquete == ctx.packageName || vistas.containsKey(paquete)) continue
                vistas[paquete] = App(paquete, r.loadLabel(pm).toString(), r.activityInfo)
            }
        }
        if (Entradas.paqueteTv(ctx) != null) vistas[CANALES_TV] = App(CANALES_TV, "Canales TV", null)
        return vistas.values.sortedBy { it.nombre.lowercase() }
    }

    fun fijadas(ctx: Context): List<String> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val guardadas = prefs.getString(CLAVE_FIJADAS, null) ?: return FIJADAS_POR_DEFECTO
        val lista = guardadas.split(",").filter { it.isNotBlank() }
        // Quien ya tenía apps fijadas recibe Canales TV una sola vez, al principio
        if (!prefs.getBoolean(CLAVE_CABLE_AGREGADO, false)) {
            prefs.edit().putBoolean(CLAVE_CABLE_AGREGADO, true).apply()
            if (CANALES_TV !in lista) {
                val nueva = listOf(CANALES_TV) + lista
                guardarFijadas(ctx, nueva)
                return nueva
            }
        }
        return lista
    }

    fun guardarFijadas(ctx: Context, lista: List<String>) {
        // Guardar a mano cuenta como decisión propia: Canales TV no se vuelve a agregar solo
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(CLAVE_FIJADAS, lista.joinToString(","))
            .putBoolean(CLAVE_CABLE_AGREGADO, true).apply()
    }

    /** Fija o quita una app del inicio. Devuelve true si quedó fijada. */
    fun alternarFijada(ctx: Context, paquete: String): Boolean {
        val lista = fijadas(ctx).toMutableList()
        val fijada = if (lista.remove(paquete)) false else { lista.add(paquete); true }
        guardarFijadas(ctx, lista)
        return fijada
    }

    fun abrir(ctx: Context, paquete: String): Boolean {
        if (paquete == CANALES_TV) return Entradas.abrirCable(ctx)
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(paquete)
            ?: pm.getLaunchIntentForPackage(paquete) ?: return false
        return try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: Exception) { false }
    }
}
