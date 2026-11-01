package com.geol.inicio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Seguir viendo" de cada app, leído de las listas del sistema de Android TV
 * (necesita el permiso READ_TV_LISTINGS, que se da una vez por ADB):
 * - Netflix y otras publican en la lista "Watch Next" (con progreso y enlace directo).
 * - SmartTube publica un canal "Historial" con los últimos videos.
 * Las apps que no comparten nada (YouTube, Xuper) simplemente no muestran ventana.
 */
object SeguirViendo {

    data class Item(
        val titulo: String,
        val detalle: String?,
        val progreso: Float?,       // 0..1, o null si la app no lo da
        val imagen: String?,
        val intent: String?,
    )

    private data class Guardado(val cuando: Long, val items: List<Item>)
    private val datos = HashMap<String, Guardado>()
    private val imagenes = LruCache<String, Bitmap>(6)

    private val CANALES_HISTORIAL = setOf("historial", "history", "seguir viendo", "continue watching")

    fun tienePermiso(ctx: Context) = ctx.checkSelfPermission("android.permission.READ_TV_LISTINGS") == 0

    /** Lo último que viste en una app (máximo 5). Se guarda 1 minuto para no consultar a cada rato. */
    fun de(ctx: Context, paquete: String): List<Item> {
        if (!tienePermiso(ctx)) return emptyList()
        datos[paquete]?.let { if (System.currentTimeMillis() - it.cuando < 60_000L) return it.items }
        val items = watchNext(ctx, paquete).ifEmpty { historial(ctx, paquete) }.take(5)
        datos[paquete] = Guardado(System.currentTimeMillis(), items)
        return items
    }

    /** Lista "Watch Next": sin filtros en la consulta (el sistema no los permite); se filtra aquí. */
    private fun watchNext(ctx: Context, paquete: String): List<Item> {
        val lista = mutableListOf<Pair<Long, Item>>()
        try {
            ctx.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, null)?.use { c ->
                fun s(col: String) = c.getColumnIndex(col).let { if (it < 0) null else c.getString(it) }
                fun l(col: String) = c.getColumnIndex(col).let { if (it < 0 || c.isNull(it)) 0L else c.getLong(it) }
                while (c.moveToNext()) {
                    if (s("package_name") != paquete) continue
                    val titulo = s("title") ?: continue
                    val pos = l("last_playback_position_millis"); val dur = l("duration_millis")
                    val partes = mutableListOf<String>()
                    s("season_display_number")?.let { partes += "T$it" }
                    s("episode_display_number")?.let { partes += "E$it" }
                    s("episode_title")?.takeIf { it.isNotBlank() }?.let { partes += it }
                    if (dur > 0 && pos in 1 until dur) partes += "Quedan ${((dur - pos) / 60_000).coerceAtLeast(1)} min"
                    val item = Item(
                        titulo, partes.joinToString(" · ").ifBlank { null },
                        if (dur > 0 && pos > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else null,
                        s("poster_art_uri") ?: s("thumbnail_uri"), s("intent_uri"),
                    )
                    lista += l("last_engagement_time_utc_millis") to item
                }
            }
        } catch (_: Exception) { }
        return lista.sortedByDescending { it.first }.map { it.second }
    }

    /** Canal "Historial" de la app (SmartTube), en el orden en que la app lo publica. */
    private fun historial(ctx: Context, paquete: String): List<Item> {
        val canales = mutableSetOf<Long>()
        try {
            ctx.contentResolver.query(TvContract.Channels.CONTENT_URI, null, null, null, null)?.use { c ->
                val iPaq = c.getColumnIndex("package_name"); val iNom = c.getColumnIndex("display_name")
                val iId = c.getColumnIndex("_id")
                while (c.moveToNext()) {
                    if (c.getString(iPaq) == paquete &&
                        c.getString(iNom)?.trim()?.lowercase() in CANALES_HISTORIAL) canales += c.getLong(iId)
                }
            }
        } catch (_: Exception) { }
        if (canales.isEmpty()) return emptyList()
        val lista = mutableListOf<Item>()
        try {
            ctx.contentResolver.query(TvContract.PreviewPrograms.CONTENT_URI, null, null, null, null)?.use { c ->
                fun s(col: String) = c.getColumnIndex(col).let { if (it < 0) null else c.getString(it) }
                fun l(col: String) = c.getColumnIndex(col).let { if (it < 0 || c.isNull(it)) 0L else c.getLong(it) }
                while (c.moveToNext() && lista.size < 5) {
                    if (l("channel_id") !in canales) continue
                    val titulo = s("title") ?: continue
                    val pos = l("last_playback_position_millis"); val dur = l("duration_millis")
                    lista += Item(
                        titulo, s("author")?.takeIf { it.isNotBlank() },
                        if (dur > 0 && pos > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else null,
                        s("poster_art_uri") ?: s("thumbnail_uri"), s("intent_uri"),
                    )
                }
            }
        } catch (_: Exception) { }
        return lista
    }

    /** Imagen del programa, reducida a unos 640 px de ancho; se guardan las últimas 6. */
    fun imagen(ctx: Context, uri: String?): Bitmap? {
        uri ?: return null
        imagenes.get(uri)?.let { return it }
        return try {
            val bytes = if (uri.startsWith("http")) {
                val con = (URL(uri).openConnection() as HttpURLConnection).apply { connectTimeout = 6000; readTimeout = 6000 }
                con.inputStream.use { it.readBytes() }.also { con.disconnect() }
            } else ctx.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() } ?: return null
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
            var escala = 1
            while (medidas.outWidth / (escala * 2) >= 640) escala *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = escala })
                ?.also { imagenes.put(uri, it) }
        } catch (e: Exception) { null }
    }

    /** Abre justo lo que dejaste a medias; si el enlace falla, abre la app. */
    fun abrir(ctx: Context, paquete: String, item: Item): Boolean {
        item.intent?.let {
            try {
                val intent = if (it.startsWith("intent:") || it.contains("#Intent;")) Intent.parseUri(it, Intent.URI_INTENT_SCHEME)
                             else Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it)).setPackage(paquete)
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
            } catch (_: Exception) { }
        }
        return Apps.abrir(ctx, paquete)
    }
}
