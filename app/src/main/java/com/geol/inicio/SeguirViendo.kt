package com.geol.inicio

import android.content.Context
import android.media.tv.TvContract
import java.io.File

/**
 * Diagnóstico de "Seguir viendo": lee la lista Watch Next del sistema (necesita el permiso
 * READ_TV_LISTINGS, que se da por ADB) y la guarda en
 * /sdcard/Android/data/com.geol.inicio/files/seguir-viendo.txt para revisar qué apps publican.
 */
object SeguirViendo {
    private var ultimo = 0L

    fun diagnosticar(ctx: Context) {
        if (System.currentTimeMillis() - ultimo < 60_000L) return
        ultimo = System.currentTimeMillis()
        Thread {
            val salida = StringBuilder()
            val permiso = ctx.checkSelfPermission("android.permission.READ_TV_LISTINGS") == 0
            salida.append("permiso READ_TV_LISTINGS: ").append(if (permiso) "sí" else "no").append('\n')
            fun consultar(nombre: String, uri: android.net.Uri, columnas: List<String>) {
                try {
                    ctx.contentResolver.query(uri, null, null, null, null).use { c ->
                        if (c == null) { salida.append("$nombre: sin respuesta\n"); return@use }
                        salida.append("$nombre: ${c.count} filas\n")
                        while (c.moveToNext() && c.position < 40) {
                            val fila = columnas.joinToString(" | ") { col ->
                                val i = c.getColumnIndex(col)
                                if (i < 0) "$col=?" else "$col=${c.getString(i)}"
                            }
                            salida.append("  ").append(fila).append('\n')
                        }
                    }
                } catch (e: Exception) {
                    salida.append("$nombre: error ${e.javaClass.simpleName}: ${e.message}\n")
                }
            }
            consultar("seguir viendo", TvContract.WatchNextPrograms.CONTENT_URI, listOf(
                "package_name", "title", "episode_title", "watch_next_type",
                "last_playback_position_millis", "duration_millis", "poster_art_uri", "intent_uri"))
            consultar("canales", TvContract.Channels.CONTENT_URI, listOf("package_name", "display_name"))
            consultar("recomendados", TvContract.PreviewPrograms.CONTENT_URI, listOf("package_name", "title"))
            try {
                File(ctx.getExternalFilesDir(null), "seguir-viendo.txt").writeText(salida.toString())
            } catch (_: Exception) { }
        }.start()
    }
}
