package com.geol.inicio

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.LruCache
import java.io.File

/**
 * Iconos de apps en forma de squircle (cuadrado de esquinas muy suaves), en dos estilos:
 * originales (los colores de cada app) o temáticos (estilo Material You: un solo tono
 * tomado del fondo de pantalla, claro de día y oscuro de noche).
 * Originales:
 * - Icono moderno (adaptativo): su diseño llena todo el círculo.
 * - Icono ya redondo: se ajusta al círculo.
 * - Icono cuadrado o con transparencias: logo centrado sobre un círculo blanco.
 * Cada icono se pide en la máxima resolución y se dibuja al tamaño exacto,
 * con bordes suavizados, para que no pierda calidad.
 */
object Iconos {
    /** Iconos ya dibujados, medidos en bytes (1/8 de la memoria de la app, entre 8 y 24 MB). */
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(8L shl 20, 24L shl 20).toInt()
    ) {
        override fun sizeOf(clave: String, bmp: Bitmap) = bmp.byteCount
    }

    /** Versión de cada app (cambia al actualizarla); se olvida cuando se instala o quita algo. */
    private val versiones = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private fun version(ctx: Context, paquete: String): Long = versiones.getOrPut(paquete) {
        try { ctx.packageManager.getPackageInfo(paquete, 0).lastUpdateTime } catch (e: Exception) { 0L }
    }
    fun olvidarVersiones() = versiones.clear()

    /** Si Android pide memoria con la TV en otra app, se suelta parte o todo lo guardado. */
    fun recortar(nivel: Int) {
        if (nivel >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) cache.evictAll()
        else if (nivel >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            nivel == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) cache.trimToSize(cache.maxSize() / 2)
    }

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

    /** Forma squircle (superelipse |x|⁴+|y|⁴=1), la misma para todas las apps. */
    private val formas = HashMap<Int, Path>()
    fun forma(tamPx: Int): Path = synchronized(formas) { formas.getOrPut(tamPx) {
        val r = tamPx / 2.0
        Path().apply {
            for (i in 0..96) {
                val t = 2 * Math.PI * i / 96
                val c = Math.cos(t); val sn = Math.sin(t)
                val x = r + r * Math.signum(c) * Math.pow(Math.abs(c), 0.5)
                val y = r + r * Math.signum(sn) * Math.pow(Math.abs(sn), 0.5)
                if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
            }
            close()
        }
    } }

    /**
     * Nunca falla: si el icono de alguna app viene dañado o falta memoria, se usa su icono
     * simple. Así una app mal hecha no puede dejar la TV sin pantalla de inicio.
     */
    fun de(ctx: Context, app: App, tamPx: Int, noche: Boolean = false, paleta: Paleta? = null): Bitmap =
        try { dibujarIcono(ctx, app, tamPx, noche, paleta) } catch (t: Throwable) { respaldo(ctx, app, tamPx) }

    private fun respaldo(ctx: Context, app: App, tamPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(tamPx, tamPx, Bitmap.Config.ARGB_8888)
        try {
            val lienzo = Canvas(bmp)
            lienzo.drawPath(forma(tamPx), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            val d = app.info?.loadIcon(ctx.packageManager) ?: ctx.getDrawable(R.drawable.ic_tv)
            val m = tamPx / 6
            d?.setBounds(m, m, tamPx - m, tamPx - m); d?.draw(lienzo)
        } catch (_: Throwable) { }
        return bmp
    }

    private fun dibujarIcono(ctx: Context, app: App, tamPx: Int, noche: Boolean, paleta: Paleta?): Bitmap {
        if (paleta != null && (app.info == null || iconoPropio(ctx, app.paquete) == null)) {
            tematico(ctx, app, tamPx, noche, paleta)?.let { return it }
        }
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
        lienzo.drawPath(forma(tamPx), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = if (noche) OSCURO else Color.WHITE })
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
        val version = version(ctx, info.packageName)
        val propio = iconoPropio(ctx, info.packageName)
        val clave = "${info.packageName}/${info.name}/$tamPx/$version/${propio?.let { propios?.get(info.packageName)?.second } ?: 0}/$noche"
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
            val dibujado = dibujar(banner, tamPx, 0.74f)
            if (noche && Color.alpha(fondoBanner) > 200 && esClaro(fondoBanner)) {
                // De noche: el blanco del borde se cambia por el círculo oscuro (Netflix queda rojo sobre oscuro)
                lienzo.drawColor(OSCURO)
                lienzo.drawBitmap(quitarFondoDelBorde(dibujado, fondoBanner), 0f, 0f, null)
            } else {
                lienzo.drawColor(if (Color.alpha(fondoBanner) <= 200) blanco else fondoBanner)
                lienzo.drawBitmap(dibujado, 0f, 0f, null)
            }
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
                // Icono cuadrado con fondo propio (Netflix, Xuper, YouTube…)
                val grande = dibujar(d, tamPx * 2, 1f)   // al doble, para que el recorte no pierda nitidez
                val caja = cajaContenido(grande)
                val recorte = Bitmap.createBitmap(grande, caja.left, caja.top, caja.width(), caja.height())
                val pincel = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                if (!esParejo(prueba)) {
                    // Fondo con degradado (Xuper): el icono cubre todo el círculo, sin costuras
                    lienzo.drawBitmap(escalarBien(recorte, tamPx, tamPx), null,
                        android.graphics.Rect(0, 0, tamPx, tamPx), pincel)
                } else {
                    val lado = (s * 0.86f).toInt()
                    val o = (tamPx - lado) / 2
                    var imagen = escalarBien(recorte, lado, lado)
                    if (noche && esClaro(fondoIcono)) {
                        // De noche: el blanco del borde se cambia por el círculo oscuro
                        lienzo.drawColor(OSCURO)
                        imagen = quitarFondoDelBorde(imagen, fondoIcono)
                    } else {
                        lienzo.drawColor(fondoIcono)   // el círculo toma el color del icono
                    }
                    lienzo.drawBitmap(imagen, null, android.graphics.Rect(o, o, o + lado, o + lado), pincel)
                }
            } else {
                // Logo con transparencia: sobre círculo blanco (de día) u oscuro (de noche)
                lienzo.drawColor(blanco)
                lienzo.drawBitmap(dibujar(d, tamPx, 0.72f), 0f, 0f, null)
            }
        }

        // Recorte en squircle con borde suavizado (clipPath no suaviza)
        val final = Bitmap.createBitmap(tamPx, tamPx, Bitmap.Config.ARGB_8888)
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(contenido, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(final).drawPath(forma(tamPx), pincel)
        cache.put(clave, final)
        return final
    }

    // ---------- Temáticos (Material You) ----------

    /** Squircle del tono del fondo con la silueta del logo en otro tono del mismo color. */
    private fun tematico(ctx: Context, app: App, tamPx: Int, noche: Boolean, paleta: Paleta): Bitmap? {
        val fondo = paleta.fondoIcono(noche)
        val logo = paleta.logoIcono(noche)
        val version = app.info?.let { version(ctx, it.packageName) } ?: 0L
        val clave = "tema/${app.paquete}/$tamPx/$fondo/$logo/$version"
        cache.get(clave)?.let { return it }

        val grande = tamPx * 2
        val mascara: Bitmap = when {
            app.info == null -> vectorComoMascara(ctx, R.drawable.ic_tv, grande)
            SIMBOLOS.containsKey(app.paquete) -> vectorComoMascara(ctx, SIMBOLOS[app.paquete]!!.first, grande)
            else -> silueta(ctx, app.info, grande) ?: return null
        }
        val caja = cajaContenido(mascara)
        if (caja.width() < 4 || caja.height() < 4) return null
        val recorte = Bitmap.createBitmap(mascara, caja.left, caja.top, caja.width(), caja.height())

        val bmp = Bitmap.createBitmap(tamPx, tamPx, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bmp)
        lienzo.drawPath(forma(tamPx), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fondo })
        // El logo ocupa como máximo el 56 % del icono, con su proporción
        val maximo = tamPx * 0.56f
        val escala = maximo / maxOf(recorte.width, recorte.height)
        val w = maxOf(1, (recorte.width * escala).toInt()); val h = maxOf(1, (recorte.height * escala).toInt())
        val x = (tamPx - w) / 2; val y = (tamPx - h) / 2
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(logo, PorterDuff.Mode.SRC_IN)
        }
        lienzo.drawBitmap(escalarBien(recorte, w, h), null, android.graphics.Rect(x, y, x + w, y + h), pincel)
        cache.put(clave, bmp)
        return bmp
    }

    private fun vectorComoMascara(ctx: Context, dibujo: Int, tam: Int): Bitmap {
        val bmp = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888)
        ctx.getDrawable(dibujo)!!.mutate().apply { setBounds(0, 0, tam, tam); draw(Canvas(bmp)) }
        return bmp
    }

    /**
     * Silueta del logo de una app: solo lo que es logo queda opaco.
     * Usa el icono monocromo oficial si la app lo trae (Android 13+); si no, la saca
     * quitando el fondo del icono o del banner.
     */
    private fun silueta(ctx: Context, info: ActivityInfo, tam: Int): Bitmap? {
        val d = original(ctx, info)
        if (d is AdaptiveIconDrawable) {
            if (android.os.Build.VERSION.SDK_INT >= 33) d.monochrome?.let { mono ->
                val b = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888)
                val extra = tam / 4
                mono.setBounds(-extra, -extra, tam + extra, tam + extra); mono.draw(Canvas(b))
                return b
            }
            val fg = d.foreground ?: return null
            val b = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888)
            val extra = tam / 4
            fg.setBounds(-extra, -extra, tam + extra, tam + extra); fg.draw(Canvas(b))
            // Si la capa del frente llena todo (sin transparencias), se separa el logo por color
            return colorDeFondo(b)?.let { quitarColor(b, it) } ?: b
        }
        val banner = if (esBajaResolucion(d, tam / 2)) banner(ctx, info) else null
        val b = dibujar(banner ?: d, tam, 1f)
        val fondo = colorDeFondo(b) ?: return b          // logo con transparencia: ya es la silueta
        return if (esParejo(b)) quitarColor(b, fondo) else quitarPorContraste(b, fondo)
    }

    /** Quita de toda la imagen el color de fondo (también los huecos, como el triángulo de YouTube). */
    private fun quitarColor(src: Bitmap, fondo: Int): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            if (Color.alpha(c) < 40) { px[i] = 0; continue }
            val d = maxOf(Math.abs(Color.red(c) - Color.red(fondo)),
                Math.abs(Color.green(c) - Color.green(fondo)), Math.abs(Color.blue(c) - Color.blue(fondo)))
            val a = ((d - 40) * 255 / 90).coerceIn(0, 255)
            px[i] = (minOf(a, Color.alpha(c)) shl 24) or 0x00FFFFFF
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Para fondos con degradado (Xuper): queda solo lo que contrasta mucho con el fondo. */
    private fun quitarPorContraste(src: Bitmap, fondo: Int): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val luzFondo = 0.3 * Color.red(fondo) + 0.59 * Color.green(fondo) + 0.11 * Color.blue(fondo)
        for (i in px.indices) {
            val c = px[i]
            if (Color.alpha(c) < 40) { px[i] = 0; continue }
            val luz = 0.3 * Color.red(c) + 0.59 * Color.green(c) + 0.11 * Color.blue(c)
            val a = (((Math.abs(luz - luzFondo)) - 50) * 255 / 70).toInt().coerceIn(0, 255)
            px[i] = (a shl 24) or 0x00FFFFFF
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Zona del icono que no es transparente. */
    private fun cajaContenido(b: Bitmap): android.graphics.Rect {
        val w = b.width
        val px = IntArray(w * b.height); b.getPixels(px, 0, w, 0, 0, w, b.height)   // de una vez, no punto por punto
        var l = b.width; var t = b.height; var r = 0; var bo = 0
        for (y in 0 until b.height step 2) for (x in 0 until w step 2) {
            if ((px[y * w + x] ushr 24) > 40) {
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

    /** ¿El fondo del icono es de un solo color (sin degradado)? */
    private fun esParejo(b: Bitmap): Boolean {
        val c = cajaContenido(b)
        val dx = maxOf(2, c.width() / 12); val dy = maxOf(2, c.height() / 12)
        val m = listOf(
            b.getPixel(c.centerX(), c.top + dy), b.getPixel(c.centerX(), c.bottom - 1 - dy),
            b.getPixel(c.left + dx, c.centerY()), b.getPixel(c.right - 1 - dx, c.centerY()),
            b.getPixel(c.left + dx, c.top + dy), b.getPixel(c.right - 1 - dx, c.bottom - 1 - dy),
        ).filter { Color.alpha(it) > 200 }
        if (m.size < 4) return false
        val r = m.map { Color.red(it) }; val g = m.map { Color.green(it) }; val bl = m.map { Color.blue(it) }
        return r.max() - r.min() < 16 && g.max() - g.min() < 16 && bl.max() - bl.min() < 16
    }

    /**
     * Vuelve transparente el fondo que toca los bordes de la imagen (relleno desde afuera),
     * sin tocar lo que queda encerrado: el triángulo blanco de YouTube se conserva.
     * Los bordes del logo quedan semitransparentes para que no se vea dentado.
     */
    private fun quitarFondoDelBorde(src: Bitmap, fondo: Int): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val dist = { c: Int ->
            maxOf(Math.abs(Color.red(c) - Color.red(fondo)),
                Math.abs(Color.green(c) - Color.green(fondo)), Math.abs(Color.blue(c) - Color.blue(fondo)))
        }
        val visto = BooleanArray(w * h)
        val cola = ArrayDeque<Int>()
        for (x in 0 until w) { cola.add(x); cola.add((h - 1) * w + x) }
        for (y in 0 until h) { cola.add(y * w); cola.add(y * w + w - 1) }
        while (cola.isNotEmpty()) {
            val i = cola.removeFirst()
            if (visto[i]) continue
            visto[i] = true
            val c = px[i]
            val transparente = Color.alpha(c) < 40
            val d = if (transparente) 0 else dist(c)
            when {
                d <= 40 -> px[i] = 0                               // fondo: fuera
                d < 130 -> {                                         // borde suave del logo
                    val a = ((d - 40) * 255 / 90).coerceIn(0, 255)
                    px[i] = (minOf(a, Color.alpha(c)) shl 24) or (c and 0x00FFFFFF)
                    continue
                }
                else -> continue                                     // logo: no se sigue
            }
            val x = i % w; val y = i / w
            if (x > 0) cola.add(i - 1); if (x < w - 1) cola.add(i + 1)
            if (y > 0) cola.add(i - w); if (y < h - 1) cola.add(i + w)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Icono propio: /sdcard/Android/data/com.geol.inicio/files/iconos/<paquete>.png (o .jpg/.webp). */
    @Volatile private var propios: Map<String, Pair<File, Long>>? = null

    /** Lee la carpeta de iconos propios una sola vez (al volver al inicio), no por cada icono. */
    fun releerPropios(ctx: Context): String {
        val dir = ctx.getExternalFilesDir(null)?.let { File(it, "iconos") }
        val mapa = HashMap<String, Pair<File, Long>>()
        dir?.listFiles()?.sortedBy { listOf("png", "jpg", "webp").indexOf(it.extension.lowercase()).let { i -> if (i < 0) 9 else i } }
            ?.forEach { f ->
                if (f.extension.lowercase() in setOf("png", "jpg", "webp") && f.nameWithoutExtension !in mapa)
                    mapa[f.nameWithoutExtension] = f to f.lastModified()
            }
        propios = mapa
        return mapa.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value.second}" }
    }

    private fun iconoPropio(ctx: Context, paquete: String): File? =
        (propios ?: run { releerPropios(ctx); propios })?.get(paquete)?.first

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
