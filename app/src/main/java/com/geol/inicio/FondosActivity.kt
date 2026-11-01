package com.geol.inicio

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Outline
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Fondos: pestaña "Mis fondos" (incluidos y guardados en la TV) y "En línea" (Wallhaven).
 * OK aplica el fondo al instante; en línea, primero lo descarga y lo guarda en la TV.
 * Mantener OK sobre uno de Mis fondos lo borra (siempre queda al menos uno).
 */
class FondosActivity : Activity() {

    private val dp by lazy { resources.displayMetrics.density }

    // ---------- Mis fondos ----------
    private lateinit var fondos: List<Fondos.Fondo>
    private val miniaturas = HashMap<String, Bitmap>()
    private lateinit var adaptador: AdaptadorMios
    private lateinit var vistaFondo: ImageView
    private lateinit var cuadricula: GridView

    // ---------- En línea ----------
    private lateinit var cuadriculaLinea: GridView
    private lateinit var adaptadorLinea: AdaptadorLinea
    private lateinit var filaCategorias: View
    private lateinit var categorias: LinearLayout
    private lateinit var mensaje: TextView
    private lateinit var ayuda: TextView
    private lateinit var tabMios: TextView
    private lateinit var tabLinea: TextView

    private var enLinea = false
    private val fotos = mutableListOf<Wallhaven.Foto>()
    private var busqueda: String? = null          // en inglés; null = destacados
    private var etiqueta = "Destacados"          // para el nombre del archivo guardado
    private var pagina = 0
    private var ultima = 1
    private var cargando = false
    private var turno = 0
    private var yaCargoLinea = false
    private val descargas = HashMap<String, Float>()  // id → avance 0..1

    /** Miniaturas de internet en memoria: hasta 10 MB, se sueltan al cerrar la pantalla. */
    private val minisLinea = object : LruCache<String, Bitmap>(10 shl 20) {
        override fun sizeOf(k: String, v: Bitmap) = v.byteCount
    }
    private val pidiendo = HashSet<String>()

    private val esquinas = object : ViewOutlineProvider() {
        override fun getOutline(v: View, o: Outline) =
            o.setRoundRect(0, 0, v.width, v.height, 12 * v.resources.displayMetrics.density)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fondos)
        vistaFondo = findViewById(R.id.fondo)
        cuadricula = findViewById(R.id.cuadricula)
        cuadriculaLinea = findViewById(R.id.cuadricula_linea)
        filaCategorias = findViewById(R.id.fila_categorias)
        categorias = findViewById(R.id.categorias)
        mensaje = findViewById(R.id.mensaje_linea)
        ayuda = findViewById(R.id.ayuda)
        tabMios = findViewById(R.id.tab_mios)
        tabLinea = findViewById(R.id.tab_linea)
        mostrarFondoActual()

        // Mis fondos
        fondos = Fondos.todos(this)
        adaptador = AdaptadorMios()
        cuadricula.adapter = adaptador
        cuadricula.setOnItemClickListener { _, _, pos, _ ->
            Fondos.elegir(this, fondos[pos].clave)
            mostrarFondoActual()
            adaptador.notifyDataSetChanged()
        }
        cuadricula.setOnItemLongClickListener { _, _, pos, _ -> preguntarBorrar(fondos[pos]); true }
        val actual = fondos.indexOfFirst { it.clave == Fondos.actual(this) }
        if (actual >= 0) cuadricula.setSelection(actual)

        // En línea
        adaptadorLinea = AdaptadorLinea()
        cuadriculaLinea.adapter = adaptadorLinea
        cuadriculaLinea.setOnItemClickListener { _, _, pos, _ -> fotos.getOrNull(pos)?.let { descargar(it) } }
        cuadriculaLinea.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: AbsListView, estado: Int) {}
            override fun onScroll(v: AbsListView, primero: Int, visibles: Int, total: Int) {
                if (total > 0 && primero + visibles >= total - 6) cargarMas()
            }
        })
        armarCategorias()

        // Pestañas: cambian al pasar por ellas, como en Google TV
        tabMios.setOnFocusChangeListener { _, foco -> if (foco) mostrarMios() }
        tabLinea.setOnFocusChangeListener { _, foco -> if (foco) mostrarLinea() }
        tabMios.setOnClickListener { cuadricula.requestFocus() }
        tabLinea.setOnClickListener { (categorias.getChildAt(1) ?: cuadriculaLinea).requestFocus() }
        mostrarMios()
        cuadricula.requestFocus()

        // Iconos: Originales | Temáticos (el elegido queda marcado)
        val chipOriginales = findViewById<TextView>(R.id.chip_originales)
        val chipTematicos = findViewById<TextView>(R.id.chip_tematicos)
        fun marcar() {
            val t = EstiloIconos.tematicos(this)
            chipOriginales.isActivated = !t
            chipTematicos.isActivated = t
        }
        marcar()
        chipOriginales.setOnClickListener { EstiloIconos.elegir(this, false); marcar() }
        chipTematicos.setOnClickListener { EstiloIconos.elegir(this, true); marcar() }
        Fuente.aplicar(window.decorView)

        cargarMiniaturasMias()
    }

    // ---------- Pestañas ----------

    private fun mostrarMios() {
        enLinea = false
        tabMios.isActivated = true; tabLinea.isActivated = false
        filaCategorias.visibility = View.GONE
        cuadriculaLinea.visibility = View.GONE
        mensaje.visibility = View.GONE
        cuadricula.visibility = View.VISIBLE
        ayuda.setText(R.string.ayuda_fondos)
    }

    private fun mostrarLinea() {
        enLinea = true
        tabMios.isActivated = false; tabLinea.isActivated = true
        cuadricula.visibility = View.GONE
        filaCategorias.visibility = View.VISIBLE
        cuadriculaLinea.visibility = View.VISIBLE
        ayuda.setText(R.string.ayuda_linea)
        if (!yaCargoLinea) { yaCargoLinea = true; elegirCategoria(0) } else actualizarMensaje()
    }

    // ---------- Categorías y buscador ----------

    private fun armarCategorias() {
        categorias.removeAllViews()
        val chip = { texto: String, alPulsar: (TextView) -> Unit ->
            (LayoutInflater.from(this).inflate(R.layout.chip_categoria, categorias, false) as TextView).apply {
                text = texto
                id = View.generateViewId()
                nextFocusUpId = R.id.tab_linea          // subir siempre vuelve a "En línea", no a Mis fondos
                setOnClickListener { alPulsar(this) }
                categorias.addView(this)
            }
        }
        chip(getString(R.string.buscar)) { pedirBusqueda() }.apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_buscar, 0, 0, 0)
            compoundDrawablePadding = (6 * dp).toInt()
            compoundDrawableTintList = getColorStateList(R.color.chip_texto)
        }
        Wallhaven.CATEGORIAS.forEachIndexed { i, _ -> chip(Wallhaven.CATEGORIAS[i].first) { elegirCategoria(i) } }
        tabLinea.nextFocusDownId = categorias.getChildAt(1).id
        tabMios.nextFocusDownId = R.id.cuadricula
    }

    private fun marcarCategoria(indice: Int?) {
        for (i in 1 until categorias.childCount) categorias.getChildAt(i).isActivated = (i - 1 == indice)
    }

    private fun elegirCategoria(i: Int) {
        val (nombre, ingles) = Wallhaven.CATEGORIAS[i]
        marcarCategoria(i)
        empezar(ingles, nombre)
    }

    private fun pedirBusqueda() {
        val campo = EditText(this).apply {
            hint = getString(R.string.buscar_ejemplo)
            setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        val marco = FrameLayout(this).apply {
            val m = (24 * dp).toInt()
            setPadding(m, (8 * dp).toInt(), m, 0)
            addView(campo)
        }
        lateinit var dialogo: AlertDialog
        val buscar = {
            val texto = campo.text.toString().trim()
            if (texto.isNotEmpty()) {
                marcarCategoria(null)
                empezar(Wallhaven.traducir(texto), texto)
            }
            dialogo.dismiss()
        }
        dialogo = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.buscar_titulo)
            .setView(marco)
            .setPositiveButton(R.string.buscar) { _, _ -> buscar() }
            .setNegativeButton(R.string.cancelar, null)
            .create()
        campo.setOnEditorActionListener { _, _, _ -> buscar(); true }
        dialogo.setOnShowListener { campo.requestFocus() }
        dialogo.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialogo.show()
    }

    // ---------- Resultados ----------

    /** Empieza una búsqueda nueva (categoría o texto). */
    private fun empezar(ingles: String?, nombre: String) {
        turno++
        busqueda = ingles; etiqueta = nombre
        fotos.clear(); pagina = 0; ultima = 1; cargando = false
        adaptadorLinea.notifyDataSetChanged()
        cuadriculaLinea.setSelection(0)
        cargarMas()
    }

    private fun cargarMas() {
        if (cargando || pagina >= ultima) return
        cargando = true
        val miTurno = turno; val siguiente = pagina + 1; val q = busqueda
        mostrarMensaje(if (fotos.isEmpty()) getString(R.string.cargando) else null)
        Hilos.red.execute {
            val resultado = try { Result.success(Wallhaven.buscar(q, siguiente)) } catch (e: Exception) { Result.failure(e) }
            runOnUiThread {
                if (isDestroyed || miTurno != turno) return@runOnUiThread
                cargando = false
                resultado.onSuccess { p ->
                    pagina = siguiente; ultima = maxOf(1, p.ultima)
                    val nuevas = p.fotos.filter { f -> fotos.none { it.id == f.id } }
                    fotos += nuevas
                    adaptadorLinea.notifyDataSetChanged()
                    if (p.fotos.isEmpty()) ultima = pagina
                    actualizarMensaje()
                }.onFailure { e ->
                    if (fotos.isEmpty()) mostrarMensaje(getString(
                        if ((e as? Wallhaven.Falla)?.codigo == 429) R.string.muchas_busquedas else R.string.sin_conexion))
                }
            }
        }
    }

    private fun actualizarMensaje() {
        if (!enLinea) return
        mostrarMensaje(when {
            fotos.isNotEmpty() -> null
            cargando -> getString(R.string.cargando)
            pagina > 0 -> getString(R.string.sin_resultados)
            else -> null
        })
    }

    private fun mostrarMensaje(texto: String?) {
        mensaje.text = texto ?: ""
        mensaje.visibility = if (texto == null || !enLinea) View.GONE else View.VISIBLE
    }

    /** OK sobre una foto: se descarga, se guarda en la TV y queda puesta. */
    private fun descargar(foto: Wallhaven.Foto) {
        if (descargas.containsKey(foto.id)) return
        descargas[foto.id] = 0f
        adaptadorLinea.notifyDataSetChanged()
        val nombre = etiqueta
        var ultimoAviso = 0f
        Hilos.red.execute {
            val clave = Wallhaven.descargar(applicationContext, foto, nombre) { avance ->
                if (avance - ultimoAviso >= 0.05f || avance >= 1f) {
                    ultimoAviso = avance
                    runOnUiThread { if (!isDestroyed) { descargas[foto.id] = avance; adaptadorLinea.notifyDataSetChanged() } }
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                descargas.remove(foto.id)
                if (clave != null) {
                    Fondos.elegir(this, clave)
                    mostrarFondoActual()
                    recargarMios()
                    aviso(R.string.fondo_guardado)
                } else aviso(R.string.no_descargo)
                adaptadorLinea.notifyDataSetChanged()
            }
        }
    }

    // ---------- Mis fondos: borrar y recargar ----------

    private fun preguntarBorrar(f: Fondos.Fondo) {
        if (fondos.size <= 1) { aviso(R.string.ultimo_fondo); return }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.borrar_fondo, f.nombre))
            .setPositiveButton(R.string.borrar) { _, _ ->
                if (Fondos.borrar(this, f.clave)) {
                    miniaturas.remove(f.clave)
                    recargarMios()
                    mostrarFondoActual()
                    aviso(R.string.fondo_borrado)
                } else aviso(R.string.ultimo_fondo)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun recargarMios() {
        fondos = Fondos.todos(this)
        adaptador.notifyDataSetChanged()
        cargarMiniaturasMias()
    }

    /** Miniaturas pequeñas en segundo plano para no trabar el control remoto. */
    private fun cargarMiniaturasMias() {
        val faltan = fondos.filter { it.clave !in miniaturas }
        if (faltan.isEmpty()) return
        val ctx = applicationContext
        Hilos.datos.execute {
            for (f in faltan) {
                val bmp = Fondos.cargar(ctx, f.clave, 480) ?: continue
                runOnUiThread { if (!isDestroyed) { miniaturas[f.clave] = bmp; adaptador.notifyDataSetChanged() } }
            }
        }
    }

    /** El fondo de esta pantalla, cargado en segundo plano. */
    private fun mostrarFondoActual() {
        val ctx = applicationContext
        Hilos.datos.execute {
            val bmp = Fondos.cargar(ctx, Fondos.actual(ctx), 1920)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (bmp != null) vistaFondo.setImageBitmap(bmp) else vistaFondo.setImageResource(R.drawable.fondo)
            }
        }
    }

    private fun aviso(texto: Int) = Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()

    private fun nuevaMiniatura(padre: ViewGroup): View =
        LayoutInflater.from(padre.context).inflate(R.layout.miniatura_fondo, padre, false).also {
            it.findViewById<View>(R.id.imagen).apply { outlineProvider = esquinas; clipToOutline = true }
            it.findViewById<View>(R.id.descarga).apply { outlineProvider = esquinas; clipToOutline = true }
            Fuente.aplicar(it)
        }

    private inner class AdaptadorMios : BaseAdapter() {
        override fun getCount() = fondos.size
        override fun getItem(pos: Int) = fondos[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, reciclada: View?, padre: ViewGroup): View {
            val vista = reciclada ?: nuevaMiniatura(padre)
            val f = fondos[pos]
            val actual = Fondos.actual(this@FondosActivity)
            vista.findViewById<ImageView>(R.id.imagen).setImageBitmap(miniaturas[f.clave])
            vista.findViewById<TextView>(R.id.nombre).text = f.nombre
            vista.findViewById<View>(R.id.marca).visibility = if (f.clave == actual) View.VISIBLE else View.GONE
            return vista
        }
    }

    private inner class AdaptadorLinea : BaseAdapter() {
        override fun getCount() = fotos.size
        override fun getItem(pos: Int) = fotos[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, reciclada: View?, padre: ViewGroup): View {
            val vista = reciclada ?: nuevaMiniatura(padre)
            val f = fotos[pos]
            val imagen = vista.findViewById<ImageView>(R.id.imagen)
            val mini = minisLinea.get(f.miniatura)
            imagen.setImageBitmap(mini)
            if (mini == null) pedirMiniatura(f.miniatura)
            vista.findViewById<TextView>(R.id.nombre).text = when {
                f.ancho >= 3840 -> "4K"
                f.ancho >= 2560 -> "2K"
                else -> "HD"
            }
            vista.findViewById<View>(R.id.marca).visibility = View.GONE
            val avance = descargas[f.id]
            vista.findViewById<View>(R.id.descarga).visibility = if (avance == null) View.GONE else View.VISIBLE
            if (avance != null) {
                val barra = vista.findViewById<View>(R.id.progreso_descarga)
                barra.layoutParams.width = ((222 * dp) * avance).toInt()
                barra.requestLayout()
            }
            return vista
        }
    }

    private fun pedirMiniatura(url: String) {
        if (!pidiendo.add(url)) return
        Hilos.red.execute {
            val bmp = Wallhaven.miniatura(url)
            runOnUiThread {
                pidiendo.remove(url)
                if (bmp != null && !isDestroyed) { minisLinea.put(url, bmp); adaptadorLinea.notifyDataSetChanged() }
            }
        }
    }
}
