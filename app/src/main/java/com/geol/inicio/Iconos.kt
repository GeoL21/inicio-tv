package com.geol.inicio

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.LruCache
import java.io.File

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
    /** Apps del sistema con icono pobre en TV: se dibujan con un símbolo propio, estilo Google. */
    private val SIMBOLOS = mapOf(
        "com.android.tv.settings" to (R.drawable.ic_ajustes to 0xFF5F6368.toInt()),
    )

    /** Fondo de los círculos en modo noche (opción A: círculo oscuro, logo a color). */
    private const val OSCURO = 0xFF2A2A2E.toInt()

    private fun esClaro(color: Int): Boolean {
        val l = 0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)
        return Color.alpha(color) > 200 && l > 215
    }

    fun de(ctx: Context, app: App, tamPx: Int, noche: Boolean = false): Bitmap {
        val info = app.info ?: return simbolo(ctx, tamPx, R.drawable.ic_tv,
            if (noche) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt(), 0.52f, noche)
        // Un icono propio copiado a la TV siempre gana
        if (iconoPropio(ctx, app.paquete) == null) {
            SIMBOLOS[app.paquete]?.let { (dibujo, color) ->
                return simbolo(ctx, tamPx, dibujo, if (noche) 0xFFBDC1C6.toInt() else color, 0.5f, noche)
            }
        }
        return circular(ctx, info, tamPx, noche)
    }

    /** Círculo blanco con un símbolo de color, como los iconos de Google (Canales TV, Ajustes). */
    private fun simbolo(ctx: Context, tamPx: Int, dibujo: Int, color: Int, factor: Float, noche: Boolean): Bitmap {
        val clave = "simbolo/$dibujo/$tamPx/$noche"
        cache.get(clave)?.let { return it }
        val bmp = Bitmap.createBitmap(tamPx, tamPx, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bmp)
        val s = tamPx.toFloat()
        lienzo.drawCircle(s / 2, s / 2, s / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = if (noche) OSCURO else Color.WHITE })
        val d = ctx.getDrawable(dibujo)!!.mutate()
        d.setTint(color)
        val lado = (s * factor).toInt()
        val x = (tamPx - lado) / 2
        d.setBounds(x, x, x + lado, x + lado)
        d.draw(lienzo)
        cache.put(clave, bmp)
        return bmp
    }

    fun circular(ctx: Context, info: ActivityInfo, tamPx: Int, noche: Boolean = false): Bitmap {
        val version = try {
            ctx.packageManager.getPackageInfo(info.packageName, 0).lastUpdateTime
        } catch (e: Exception) { 0L }
        val propio = iconoPropio(ctx, info.packageName)
        val clave = "${info.packageName}/${info.name}/$tamPx/$version/${propio?.lastModified() ?: 0}/$noche"
        val blanco = if (noche) OSCURO else Color.WHITE
        cache.get(clave)?.let { return it }

        val contenido = Bitmap.createBitmap(tamPx, tamPx, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(contenido)
        val s = tamPx.toFloat()
        val d = original(ctx, info)
        val banner = if (d !is AdaptiveIconDrawable && esBajaResolucion(d, tamPx)) banner(ctx, info) else null
        val imagenPropia = propio?.let { BitmapFactory.decodeFile(it.path) }

        if (imagenPropia != null) {
            // Icono propio copiado a la TV: llena el círculo (recorte al centro)
            val lado = minOf(imagenPropia.width, imagenPropia.height)
            val cuadro = Bitmap.createBitmap(imagenPropia, (imagenPropia.width - lado) / 2,
                (imagenPropia.height - lado) / 2, lado, lado)
            lienzo.drawBitmap(escalarBien(cuadro, tamPx, tamPx), null,
                android.graphics.Rect(0, 0, tamPx, tamPx), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else if (banner != null) {
            // Icono viejo y pequeño (se vería pixelado): se usa el banner de TV, en alta resolución
            // El círculo toma el color de fondo del banner, así nunca se ve el rectángulo
            val fondoBanner = dibujar(banner, 64, 1f).getPixel(2, 32)
            lienzo.drawColor(if (Color.alpha(fondoBanner) <= 200) blanco else fondoBanner)
            lienzo.drawBitmap(dibujar(banner, tamPx, 0.74f), 0f, 0f, null)
        } else if (d is AdaptiveIconDrawable) {
            // Las capas miden 1,5 veces el icono; el círculo muestra el centro
            val extra = tamPx / 4
            val fondo = d.background
            // De noche, un fondo de icono blanco o muy claro se cambia por el círculo oscuro
            val fondoClaro = noche && fondo != null && run {
                val m = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                fondo.setBounds(0, 0, 8, 8); fondo.draw(Canvas(m))
                val c = Bitmap.createScaledBitmap(m, 1, 1, true).getPixel(0, 0)
                esClaro(c)
            }
            if (fondoClaro) lienzo.drawColor(OSCURO)
            for (capa in listOf(if (fondoClaro) null else fondo, d.foreground)) {
                capa ?: continue
                capa.setBounds(-extra, -extra, tamPx + extra, tamPx + extra)
                capa.draw(lienzo)
            }
        } else {
            val prueba = dibujar(d, tamPx, 1f)
            val alfa = { x: Float, y: Float -> Color.alpha(prueba.getPixel((x * s).toInt(), (y * s).toInt())) }
            val esquinaVacia = alfa(0.07f, 0.07f) < 40
            val bordeLleno = alfa(0.5f, 0.04f) > 200 && alfa(0.04f, 0.5f) > 200
            val fondoIcono = colorDeFondo(prueba)
            if (esquinaVacia && bordeLleno) {
                // Ya es redondo: se agranda un poco para llenar el círculo
                lienzo.drawBitmap(dibujar(d, tamPx, 1.06f), 0f, 0f, null)
            } else if (fondoIcono != null) {
                // Icono cuadrado con fondo propio (Netflix, Xuper, YouTube…): el círculo toma
                // ese color y el icono se recorta a su contenido, así no se ve el cuadrado
                lienzo.drawColor(fondoIcono)
                val grande = dibujar(d, tamPx * 2, 1f)   // al doble, para que el recorte no pierda nitidez
                val caja = cajaContenido(grande)
                val recorte = Bitmap.createBitmap(grande, caja.left, caja.top, caja.width(), caja.height())
                val lado = (s * 0.86f).toInt()
                val o = (tamPx - lado) / 2
                lienzo.drawBitmap(escalarBien(recorte, lado, lado), null,
                    android.graphics.Rect(o, o, o + lado, o + lado), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            } else {
                // Logo con transparencia: sobre círculo blanco (de día) u oscuro (de noche)
                lienzo.drawColor(blanco)
                lienzo.drawBitmap(dibujar(d, tamPx, 0.72f), 0f, 0f, null)
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

    /** Zona del icono que no es transparente. */
    private fun cajaContenido(b: Bitmap): android.graphics.Rect {
        var l = b.width; var t = b.height; var r = 0; var bo = 0
        for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) {
            if (Color.alpha(b.getPixel(x, y)) > 40) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > bo) bo = y
            }
        }
        if (r <= l || bo <= t) return android.graphics.Rect(0, 0, b.width, b.height)
        return android.graphics.Rect(l, t, minOf(b.width, r + 2), minOf(b.height, bo + 2))
    }

    /**
     * Color de fondo de un icono cuadrado: el de su borde interior, si es opaco y parejo
     * en los cuatro lados. Si el icono es un logo transparente, devuelve null.
     */
    private fun colorDeFondo(b: Bitmap): Int? {
        val c = cajaContenido(b)
        val dx = maxOf(2, c.width() / 12); val dy = maxOf(2, c.height() / 12)
        val muestras = listOf(
            b.getPixel(c.centerX(), c.top + dy), b.getPixel(c.centerX(), c.bottom - 1 - dy),
            b.getPixel(c.left + dx, c.centerY()), b.getPixel(c.right - 1 - dx, c.centerY()),
        )
        if (muestras.any { Color.alpha(it) < 230 }) return null
        val base = muestras[0]
        val parecido = muestras.all {
            Math.abs(Color.red(it) - Color.red(base)) < 40 &&
                Math.abs(Color.green(it) - Color.green(base)) < 40 &&
                Math.abs(Color.blue(it) - Color.blue(base)) < 40
        }
        return if (parecido) base or 0xFF000000.toInt() else null
    }

    /** Icono propio: /sdcard/Android/data/com.geol.inicio/files/iconos/<paquete>.png (o .jpg/.webp). */
    private fun iconoPropio(ctx: Context, paquete: String): File? {
        val dir = ctx.getExternalFilesDir(null)?.let { File(it, "iconos") } ?: return null
        return listOf("png", "jpg", "webp").map { File(dir, "$paquete.$it") }.firstOrNull { it.exists() }
    }

    /** ¿El icono es tan pequeño que se vería pixelado al agrandarlo? */
    private fun esBajaResolucion(d: Drawable, tamPx: Int): Boolean =
        d is BitmapDrawable && d.bitmap != null && maxOf(d.bitmap.width, d.bitmap.height) < tamPx * 0.62f * 1.5f

    private fun banner(ctx: Context, info: ActivityInfo): Drawable? {
        val recurso = info.bannerResource.takeIf { it != 0 } ?: info.applicationInfo.banner
        if (recurso == 0) return null
        return try {
            ctx.packageManager.getResourcesForApplication(info.applicationInfo)
                .getDrawableForDensity(recurso, DisplayMetrics.DENSITY_XXXHIGH, null)
        } catch (e: Exception) { null }
    }

    /**
     * Reduce una imagen a la mitad varias veces antes del tamaño final.
     * Reducir de golpe una imagen grande deja bordes en "dientes de sierra".
     */
    private fun escalarBien(src: Bitmap, w: Int, h: Int): Bitmap {
        var b = src
        while (b.width / 2 >= w && b.height / 2 >= h) {
            b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        }
        return if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, true)
    }

    /** Dibuja el drawable centrado, con su proporción, ocupando `factor` del lado. */
    private fun dibujar(d: Drawable, tamPx: Int, factor: Float): Bitmap {
        val bmp = Bitmap.createBitmap(tamPx, tamPx, Bitmap.Config.ARGB_8888)
        if (d is BitmapDrawable && d.bitmap != null) {
            // Imágenes: reducción de calidad en pasos, para bordes suaves
            val src = d.bitmap
            val lado = tamPx * factor
            val escala = lado / maxOf(src.width, src.height)
            val w = maxOf(1, (src.width * escala).toInt())
            val h = maxOf(1, (src.height * escala).toInt())
            val lienzo = Canvas(bmp)
            val pincel = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            // Con rectángulo de destino Android no reescala por "densidad" (eso desplazaba los iconos)
            val x = (tamPx - w) / 2
            val y = (tamPx - h) / 2
            lienzo.drawBitmap(escalarBien(src, w, h), null, android.graphics.Rect(x, y, x + w, y + h), pincel)
            return bmp
        }
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
