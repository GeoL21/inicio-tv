package com.geol.inicio

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** Entradas de la TV: cable/antena y HDMI. En la TV box no hay ninguna. */
object Entradas {

    /** Una entrada para el panel: cable o HDMI. */
    data class Entrada(
        val id: String,
        val nombre: String,
        val detalle: String?,
        val conectada: Boolean,
        val esCable: Boolean,
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("inicio", Context.MODE_PRIVATE)

    /** Nombre que el usuario le puso a una entrada (por ejemplo "PC"), o null. */
    fun nombrePropio(ctx: Context, id: String): String? =
        prefs(ctx).getString("nombre_entrada:$id", null)?.takeIf { it.isNotBlank() }

    /** Guarda el nombre; vacío lo borra. */
    fun ponerNombre(ctx: Context, id: String, nombre: String) {
        val e = prefs(ctx).edit()
        if (nombre.isBlank()) e.remove("nombre_entrada:$id") else e.putString("nombre_entrada:$id", nombre.trim())
        e.apply()
    }

    private fun tim(ctx: Context) = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

    /**
     * App de TV en vivo (antena/cable) del equipo, si la tiene.
     * En la Hisense 55Q6N es com.vt.tv.tvcenter ("Live TV").
     */
    fun paqueteTv(ctx: Context): String? {
        val pm = ctx.packageManager
        val abrible = { p: String -> pm.getLeanbackLaunchIntentForPackage(p) != null || pm.getLaunchIntentForPackage(p) != null }
        return listOf(
            "com.vt.tv.tvcenter",          // Hisense (Live TV)
            "com.mediatek.wwtv.tvcenter",  // otras TV con MediaTek
            "com.android.tv",              // TV en vivo de Android
            "com.google.android.tv",       // Canales en vivo de Google
        ).firstOrNull(abrible)
    }

    /**
     * El sintonizador físico de cable/antena (HW0 en la Hisense).
     * Ojo: entradas por software como AirPlay también se registran como "sintonizador",
     * así que se prefieren las de hardware (id terminado en /HW<n>) y nunca AirPlay ni Cast.
     */
    private fun sintonizador(ctx: Context): TvInputInfo? = try {
        val candidatos = tim(ctx)?.tvInputList.orEmpty()
            .filter { it.type == TvInputInfo.TYPE_TUNER && it.parentId == null }
            .filterNot { i -> listOf("airplay", "cast", "miracast", "dlna").any { i.id.contains(it, ignoreCase = true) } }
        val hardware = Regex("/HW\\d+$")
        candidatos.sortedWith(
            compareByDescending<TvInputInfo> { hardware.containsMatchIn(it.id) }
                .thenByDescending { it.id.contains("tuner", ignoreCase = true) }
                .thenBy { it.id }
        ).firstOrNull()
    } catch (e: Exception) { null }

    /** Cable primero; luego los HDMI, con su estado y el nombre del equipo si lo informa. */
    fun lista(ctx: Context): List<Entrada> {
        val tim = tim(ctx) ?: return emptyList()
        val todas = try { tim.tvInputList } catch (e: Exception) { return emptyList() }
        val conectada = { id: String ->
            try { tim.getInputState(id) == TvInputManager.INPUT_STATE_CONNECTED } catch (e: Exception) { false }
        }
        val lista = mutableListOf<Entrada>()
        if (paqueteTv(ctx) != null) sintonizador(ctx)?.let {
            lista += Entrada(it.id, "Canales TV", "Cable", true, true)
        }
        lista += todas
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !it.isHidden(ctx) }
            .map { info ->
                val equipo = todas.firstOrNull { it.parentId == info.id }
                    ?.let { (it.loadCustomLabel(ctx) ?: it.loadLabel(ctx)).toString() }
                val nombre = (info.loadCustomLabel(ctx) ?: info.loadLabel(ctx)).toString()
                // El nombre propio gana; si no hay, el que informa el equipo por HDMI-CEC
                Entrada(info.id, nombre, nombrePropio(ctx, info.id) ?: equipo, conectada(info.id), false)
            }
            .sortedBy { it.nombre }
        return lista
    }

    fun tieneHdmi(ctx: Context) = lista(ctx).any { !it.esCable }

    /**
     * Abre una entrada igual que lo hace la TV: Live TV con el enlace directo
     * a esa entrada. Si la TV no lo acepta, abre Live TV normal.
     */
    fun abrir(ctx: Context, id: String): Boolean {
        val tv = paqueteTv(ctx) ?: return false
        return try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
                    .setPackage(tv)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ); true
        } catch (e: Exception) {
            Apps.abrir(ctx, tv)
        }
    }

    /** Canales TV: directo al sintonizador de cable. */
    fun abrirCable(ctx: Context): Boolean {
        val s = sintonizador(ctx)
        if (s != null) return abrir(ctx, s.id)
        return paqueteTv(ctx)?.let { Apps.abrir(ctx, it) } ?: false
    }
}
