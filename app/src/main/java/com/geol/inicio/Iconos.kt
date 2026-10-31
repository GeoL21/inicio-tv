package com.geol.inicio

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.LruCache

/**
 * Iconos circulares al estilo del launcher de Google Pixel:
 * - Icono moderno (adaptativo): su diseño llena todo el círculo.
 * - Icono ya redondo: se ajusta al círculo.
 * - Icono cuadrado o con transparencias: logo centrado sobre un círculo blanco.
 * Cada icono se pide en la máxima resolución y se dibuja al tamaño exacto,
 * con bordes suavizados, para que no pierda calidad.
 */
object Iconos {
    private val cache = LruCache<String, Bitmap>(80)

    /** Icono de cualquier app de la lista, incluidos los accesos propios. */
    fun de(ctx: Context, app: App, tamPx: Int): Bitmap =
        app.info?.let { circular(ctx, it, tamPx) } ?: canalesTv(ctx, tamPx)

    /** Canales TV: círculo blanco con un televisor azul, como los iconos de Google. */
    private fun canalesTv(ctx: Context, tamPx: Int): Bitmap {
        val clave = "canales_tv/$tamPx"
        cache.get(clave)?.let { return it }
        val bmp = Bitmap.createBitmap(tamPx, tamPx, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bmp)
        val s = tamPx.toFloat()
        lienzo.drawCircle(s / 2, s / 2, s / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        val tv = ctx.getDrawable(R.drawable.ic_tv)!!.mutate()
        tv.setTint(0xFF1A73E8.toInt())
        val lado = (s * 0.52f).toInt()
        val x = (tamPx - lado) / 2
        tv.setBounds(x, x, x + lado, x + lado)
        tv.draw(lienzo)
        cache.put(clave, bmp)
        return bmp
    }

    fun circular(ctx: Context, info: ActivityInfo, tamPx: Int): Bitmap {
        val version = try {
            ctx.packageManager.getPackageInfo(info.packageName, 0).lastUpdateTime
        } catch (e: Exception) { 0L }
        val clave = "${info.packageName}/${info.name}/$tamPx/$version"
        cache.get(clave)?.let { return it }

        val contenido = Bitmap.createBitmap(tamPx, tamPx, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(contenido)
        val s = tamPx.toFloat()
        val d = original(ctx, info)

        if (d is AdaptiveIconDrawable) {
            // Las capas miden 1,5 veces el icono; el círculo muestra el centro
            val extra = tamPx / 4
            for (capa in listOf(d.background, d.foreground)) {
                capa ?: continue
                capa.setBounds(-extra, -extra, tamPx + extra, tamPx + extra)
                capa.draw(lienzo)
            }
        } else {
            val prueba = dibujar(d, tamPx, 1f)
            val alfa = { x: Float, y: Float -> Color.alpha(prueba.getPixel((x * s).toInt(), (y * s).toInt())) }
            val esquinaVacia = alfa(0.07f, 0.07f) < 40
            val bordeLleno = alfa(0.5f, 0.04f) > 200 && alfa(0.04f, 0.5f) > 200
            if (esquinaVacia && bordeLleno) {
                // Ya es redondo: se agranda un poco para llenar el círculo
                lienzo.drawBitmap(dibujar(d, tamPx, 1.06f), 0f, 0f, null)
            } else {
                lienzo.drawColor(Color.WHITE)
                val factor = if (!esquinaVacia) 0.62f else 0.72f
                lienzo.drawBitmap(dibujar(d, tamPx, factor), 0f, 0f, null)
            }
        }

        // Recorte circular con borde suavizado (clipPath no suaviza)
        val final = Bitmap.createBitmap(tamPx, tamPx, Bitmap.Config.ARGB_8888)
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(contenido, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(final).drawCircle(s / 2, s / 2, s / 2, pincel)
        cache.put(clave, final)
        return final
    }

    /** Dibuja el drawable centrado, con su proporción, ocupando `factor` del lado. */
    private fun dibujar(d: Drawable, tamPx: Int, factor: Float): Bitmap {
        val bmp = Bitmap.createBitmap(tamPx, tamPx, Bitmap.Config.ARGB_8888)
        val ancho = d.intrinsicWidth.takeIf { it > 0 } ?: tamPx
        val alto = d.intrinsicHeight.takeIf { it > 0 } ?: tamPx
        val lado = tamPx * factor
        val escala = lado / maxOf(ancho, alto)
        val w = (ancho * escala).toInt()
        val h = (alto * escala).toInt()
        val x = (tamPx - w) / 2
        val y = (tamPx - h) / 2
        d.setBounds(x, y, x + w, y + h)
        d.draw(Canvas(bmp))
        return bmp
    }

    /** El icono en la mayor densidad disponible; si la app de TV no tiene icono, su banner. */
    private fun original(ctx: Context, info: ActivityInfo): Drawable {
        val pm = ctx.packageManager
        val recurso = info.iconResource
        if (recurso != 0) {
            try {
                val res = pm.getResourcesForApplication(info.applicationInfo)
                res.getDrawableForDensity(recurso, DisplayMetrics.DENSITY_XXXHIGH, null)?.let { return it }
            } catch (_: Exception) { }
        } else {
            info.loadBanner(pm)?.let { return it }
        }
        return info.loadIcon(pm)
    }
}
