package com.geol.inicio

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** Entradas de la TV: cable/antena y HDMI. En la TV box no hay ninguna. */
object Entradas {

    /** App de TV en vivo (antena/cable) del equipo, si la tiene. */
    fun paqueteTv(ctx: Context): String? = listOf(
        "com.mediatek.wwtv.tvcenter", // Hisense y otras TV con MediaTek
        "com.android.tv",             // TV en vivo de Android
        "com.google.android.tv"       // Canales en vivo de Google
    ).firstOrNull { p ->
        ctx.packageManager.getLeanbackLaunchIntentForPackage(p) != null ||
            ctx.packageManager.getLaunchIntentForPackage(p) != null
    }

    /**
     * Abre los canales por cable. Si el equipo tiene una entrada de sintonizador,
     * se intenta abrir esa; si no, la app de TV (que abre la última entrada usada).
     */
    fun abrirCable(ctx: Context): Boolean {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val sintonizador = try {
            tim?.tvInputList?.firstOrNull { it.type == TvInputInfo.TYPE_TUNER && it.parentId == null }
        } catch (e: Exception) { null }
        val tv = paqueteTv(ctx)
        if (sintonizador != null && tv != null) {
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(sintonizador.id))
                        .setPackage(tv)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (_: Exception) { }
        }
        return tv != null && Apps.abrir(ctx, tv)
    }

    /** Entradas HDMI como (id, nombre). Las de equipos HDMI-CEC se agrupan en su puerto. */
    fun hdmi(ctx: Context): List<Pair<String, String>> {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return try {
            tim.tvInputList
                .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !it.isHidden(ctx) }
                .map { it.id to (it.loadCustomLabel(ctx) ?: it.loadLabel(ctx)).toString() }
                .sortedBy { it.second }
        } catch (e: Exception) { emptyList() }
    }

    fun abrirHdmi(ctx: Context, id: String): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ); true
    } catch (e: Exception) {
        paqueteTv(ctx)?.let { Apps.abrir(ctx, it) } ?: false
    }
}
