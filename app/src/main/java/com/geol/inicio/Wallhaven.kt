package com.geol.inicio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Fondos en línea desde Wallhaven (sin clave). Solo contenido general y apto para todos,
 * en 16:9 y de al menos 1920×1080. Lo que se elige se descarga una vez, se reduce a
 * 1920 px y se guarda en la TV como un fondo propio: después no usa internet.
 */
object Wallhaven {

    data class Foto(val id: String, val miniatura: String, val imagen: String, val ancho: Int, val alto: Int)
    data class Pagina(val fotos: List<Foto>, val ultima: Int)

    /** Falla con el código HTTP (429 = demasiadas búsquedas; 0 = sin conexión). */
    class Falla(val codigo: Int) : Exception()

    /** Categorías: nombre en pantalla → búsqueda en inglés (null = lo más popular del mes). */
    val CATEGORIAS = listOf(
        "Destacados" to null,
        "Paisajes" to "landscape",
        "Playa" to "beach",
        "Ciudades" to "city",
        "Espacio" to "space",
        "Bosque" to "forest",
        "Abstracto" to "abstract",
        "Oscuros" to "dark",
    )

    private const val BASE = "https://wallhaven.cc/api/v1/search?categories=100&purity=100&ratios=16x9&atleast=1920x1080"
    private const val AGENTE = "InicioTV/1.0 (Android TV)"

    private fun conectar(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 15000
            setRequestProperty("User-Agent", AGENTE)
        }

    /** Una página de resultados (24 fotos). Se llama en segundo plano. */
    fun buscar(busqueda: String?, pagina: Int): Pagina {
        val url = BASE + "&page=$pagina" +
            if (busqueda == null) "&sorting=toplist&topRange=1M"
            else "&sorting=favorites&q=" + URLEncoder.encode(busqueda, "UTF-8")
        val con = try { conectar(url) } catch (e: Exception) { throw Falla(0) }
        try {
            val codigo = try { con.responseCode } catch (e: Exception) { throw Falla(0) }
            if (codigo != 200) throw Falla(codigo)
            val json = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
            val datos = json.getJSONArray("data")
            val fotos = (0 until datos.length()).mapNotNull { i ->
                val d = datos.getJSONObject(i)
                val mini = d.optJSONObject("thumbs")?.optString("large").orEmpty()
                val img = d.optString("path")
                if (mini.isEmpty() || img.isEmpty()) null
                else Foto(d.getString("id"), mini, img, d.optInt("dimension_x"), d.optInt("dimension_y"))
            }
            val ultima = json.optJSONObject("meta")?.optInt("last_page", pagina) ?: pagina
            return Pagina(fotos, ultima)
        } finally { con.disconnect() }
    }

    /** Miniatura pequeña para la cuadrícula (o null si falla). */
    fun miniatura(url: String): Bitmap? = try {
        val con = conectar(url)
        try { con.inputStream.use { BitmapFactory.decodeStream(it) } } finally { con.disconnect() }
    } catch (e: Exception) { null }

    /**
     * Descarga la foto, la reduce a 1920 px de ancho y la guarda en la carpeta de fondos.
     * `avance` recibe de 0 a 1. Devuelve la clave del fondo ("archivo:…") o null si falla.
     * Si ya se había descargado antes, no la vuelve a bajar.
     */
    fun descargar(ctx: Context, foto: Foto, etiqueta: String, avance: (Float) -> Unit): String? {
        val prefs = ctx.getSharedPreferences("inicio", Context.MODE_PRIVATE)
        val carpeta = Fondos.carpeta(ctx) ?: return null
        prefs.getString("wallhaven:${foto.id}", null)?.let { nombre ->
            if (File(carpeta, nombre).exists()) return "archivo:$nombre"
        }
        val temporal = File(ctx.cacheDir, "descarga-${foto.id}")
        try {
            val con = conectar(foto.imagen)
            try {
                if (con.responseCode != 200) return null
                val total = con.contentLengthLong
                con.inputStream.use { entrada ->
                    temporal.outputStream().use { salida ->
                        val buf = ByteArray(32 * 1024); var leido = 0L
                        while (true) {
                            val n = entrada.read(buf); if (n < 0) break
                            salida.write(buf, 0, n); leido += n
                            if (total > 0) avance((leido.toFloat() / total).coerceAtMost(0.95f))
                        }
                    }
                }
            } finally { con.disconnect() }

            // Reducir a 1920 px: es lo que muestra el inicio, y ocupa mucho menos
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporal.path, medidas)
            if (medidas.outWidth <= 0) return null
            var escala = 1
            while (medidas.outWidth / (escala * 2) >= 1920) escala *= 2
            var bmp = BitmapFactory.decodeFile(temporal.path, BitmapFactory.Options().apply {
                inSampleSize = escala; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return null
            if (bmp.width > 1920) {
                val reducida = Bitmap.createScaledBitmap(bmp, 1920, (bmp.height * 1920f / bmp.width).toInt(), true)
                if (reducida !== bmp) bmp.recycle()
                bmp = reducida
            }

            val nombre = nombreLibre(carpeta, etiqueta)
            File(carpeta, nombre).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bmp.recycle()
            prefs.edit().putString("wallhaven:${foto.id}", nombre).apply()
            avance(1f)
            return "archivo:$nombre"
        } catch (e: Throwable) {
            return null
        } finally {
            temporal.delete()
        }
    }

    /** "Playa 1.jpg", "Playa 2.jpg"… (así se ve bonito en Mis fondos). */
    private fun nombreLibre(carpeta: File, etiqueta: String): String {
        val base = etiqueta.replace(Regex("[^\\p{L}\\p{N} ]"), "").trim().take(30)
            .replaceFirstChar { it.uppercase() }.ifBlank { "Fondo" }
        var n = 1
        while (File(carpeta, "$base $n.jpg").exists()) n++
        return "$base $n.jpg"
    }

    // ---------- Buscador: español → inglés ----------

    private val PALABRAS = mapOf(
        "montana" to "mountains", "montanas" to "mountains", "playa" to "beach", "playas" to "beach",
        "mar" to "sea", "oceano" to "ocean", "ciudad" to "city", "ciudades" to "city", "noche" to "night",
        "dia" to "day", "bosque" to "forest", "bosques" to "forest", "espacio" to "space", "galaxia" to "galaxy",
        "estrellas" to "stars", "estrella" to "star", "lago" to "lake", "rio" to "river", "nieve" to "snow",
        "invierno" to "winter", "otono" to "autumn", "primavera" to "spring", "verano" to "summer",
        "atardecer" to "sunset", "amanecer" to "sunrise", "cielo" to "sky", "nubes" to "clouds",
        "flores" to "flowers", "flor" to "flower", "arbol" to "tree", "arboles" to "trees",
        "desierto" to "desert", "cascada" to "waterfall", "cascadas" to "waterfall", "naturaleza" to "nature",
        "animales" to "animals", "perro" to "dog", "perros" to "dogs", "gato" to "cat", "gatos" to "cats",
        "carro" to "car", "carros" to "cars", "auto" to "car", "autos" to "cars", "coche" to "car", "coches" to "cars",
        "moto" to "motorcycle", "motos" to "motorcycles", "avion" to "airplane", "aviones" to "airplanes",
        "barco" to "boat", "barcos" to "boats", "tren" to "train", "minimalista" to "minimalist",
        "oscuro" to "dark", "oscuros" to "dark", "abstracto" to "abstract", "paisaje" to "landscape",
        "paisajes" to "landscape", "isla" to "island", "islas" to "islands", "puente" to "bridge",
        "faro" to "lighthouse", "luna" to "moon", "sol" to "sun", "tierra" to "earth", "planeta" to "planet",
        "planetas" to "planets", "lluvia" to "rain", "tormenta" to "storm", "rayo" to "lightning",
        "volcan" to "volcano", "selva" to "jungle", "colores" to "colorful", "colorido" to "colorful",
        "azul" to "blue", "rojo" to "red", "verde" to "green", "negro" to "black", "blanco" to "white",
        "morado" to "purple", "amarillo" to "yellow", "naranja" to "orange", "rosa" to "pink", "dorado" to "golden",
        "japon" to "japan", "arquitectura" to "architecture", "carretera" to "road", "calle" to "street",
        "edificios" to "buildings", "edificio" to "building", "neon" to "neon", "agua" to "water",
        "olas" to "waves", "costa" to "coast", "campo" to "field", "pradera" to "meadow", "valle" to "valley",
        "colinas" to "hills", "rocas" to "rocks", "fuego" to "fire", "hielo" to "ice", "futbol" to "football",
        "videojuegos" to "video games", "juegos" to "games", "aurora" to "aurora", "boreal" to "borealis",
        "niebla" to "fog", "neblina" to "mist", "otonal" to "autumn", "tropical" to "tropical", "palmeras" to "palm trees",
        "castillo" to "castle", "templo" to "temple", "pueblo" to "village", "campo de flores" to "flower field",
        "pantalla" to "", "fondo" to "", "fondos" to "", "bonito" to "", "bonitos" to "", "hermoso" to "",
    )
    private val VACIAS = setOf("de", "del", "la", "el", "los", "las", "con", "en", "al", "y", "un", "una", "a", "por", "para")

    /** Traduce las palabras conocidas; las demás (nombres, marcas) pasan igual. */
    fun traducir(texto: String): String {
        val limpio = Normalizer.normalize(texto.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return limpio.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in VACIAS }
            .map { PALABRAS[it] ?: it }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { limpio }
    }
}
