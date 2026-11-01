package com.geol.inicio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val manejador = Handler(Looper.getMainLooper())
    private val espanol = Locale("es", "CO")
    private var ultimoClima = 0L

    private lateinit var fecha: TextView
    private lateinit var saludo: TextView
    private lateinit var clima: TextView
    private lateinit var climaIcono: ImageView
    private lateinit var fila: LinearLayout
    private lateinit var pista: TextView
    private lateinit var cajon: View
    private lateinit var cuadricula: GridView

    private var todas: List<App> = emptyList()
    private var cajonAbierto = false

    /** App de la fila que se está moviendo con las flechas (null si ninguna). */
    private var moviendo: View? = null
    private var ignorarSoltarOk = false


    /** Actualiza fecha y saludo cada minuto (el reloj se actualiza solo). */
    private val cadaMinuto = object : Runnable {
        override fun run() {
            actualizarFechaYSaludo()
            aplicarModo(forzar = false)   // a las 6 p. m. y 6 a. m. cambia solo
            if (System.currentTimeMillis() - ultimoClima > 30 * 60_000L) actualizarClima()
            manejador.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fecha = findViewById(R.id.fecha)
        saludo = findViewById(R.id.saludo)
        clima = findViewById(R.id.clima)
        climaIcono = findViewById(R.id.clima_icono)
        fila = findViewById(R.id.fila)
        pista = findViewById(R.id.pista)
        cajon = findViewById(R.id.cajon)
        cuadricula = findViewById(R.id.cuadricula)

        findViewById<ImageButton>(R.id.btn_fondos).setOnClickListener {
            startActivity(Intent(this, FondosActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btn_red).setOnClickListener {
            abrirAjuste(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
        }
        findViewById<ImageButton>(R.id.btn_ajustes).setOnClickListener {
            abrirAjuste(Settings.ACTION_SETTINGS)
        }

        // Día / noche / automático. La "A" va dentro del círculo y cambia de color con el foco
        val insignia = findViewById<TextView>(R.id.insignia_auto)
        findViewById<ImageButton>(R.id.btn_modo).setOnFocusChangeListener { _, foco ->
            insignia.setTextColor(if (foco) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
        }
        findViewById<ImageButton>(R.id.btn_modo).setOnClickListener {
            aviso(when (Modo.siguiente(this)) {
                Modo.Estado.AUTOMATICO -> R.string.modo_auto
                Modo.Estado.NOCHE -> R.string.modo_noche
                Modo.Estado.DIA -> R.string.modo_dia
            })
            aplicarModo(forzar = true)
        }

        // HDMI: se oculta en equipos que no lo tienen (la TV box)
        val btnHdmi = findViewById<ImageButton>(R.id.btn_hdmi)
        if (!Entradas.tieneHdmi(this)) btnHdmi.visibility = View.GONE
        btnHdmi.setOnClickListener { abrirPanelEntradas() }

        cuadricula.setOnItemClickListener { _, _, pos, _ ->
            if (!Apps.abrir(this, todas[pos].paquete)) aviso(R.string.no_abre)
        }
        cuadricula.setOnItemLongClickListener { _, _, pos, _ ->
            val r = Apps.alternarFijada(this, todas[pos].paquete, todas.map { it.paquete }.toSet())
            aviso(when (r) {
                Apps.Resultado.FIJADA -> R.string.fijada
                Apps.Resultado.QUITADA -> R.string.quitada
                Apps.Resultado.LLENO -> R.string.dock_lleno
            })
            if (r != Apps.Resultado.LLENO) armarFila(enfocar = false)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        revisarFondo()
        todas = Apps.todas(this)
        noche = Modo.esNoche(this)
        cuadricula.adapter = AdaptadorCajon()
        if (moviendo == null) armarFila(enfocar = !cajonAbierto)
        Fuente.aplicar(window.decorView)
        manejador.removeCallbacks(cadaMinuto)
        manejador.post(cadaMinuto)
        SeguirViendo.diagnosticar(this)
    }

    override fun onPause() {
        super.onPause()
        manejador.removeCallbacks(cadaMinuto)
        terminarMover()
    }

    // Es la pantalla de inicio: Atrás solo cierra el cajón o termina de mover.
    @Deprecated("Se mantiene para Android TV")
    override fun onBackPressed() {
        when {
            moviendo != null -> terminarMover()
            panelAbierto -> cerrarPanelEntradas()
            cajonAbierto -> cerrarCajon()
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val m = moviendo
        if (m != null) {
            val ok = e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER
            if (e.action == KeyEvent.ACTION_DOWN) when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> moverA(m, -1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> moverA(m, +1)
            } else if (e.action == KeyEvent.ACTION_UP && (ok || e.keyCode == KeyEvent.KEYCODE_BACK)) {
                // El OK que se soltó tras mantenerlo presionado no termina el modo mover
                if (ok && ignorarSoltarOk) ignorarSoltarOk = false else terminarMover()
            }
            return true
        }
        if (e.action == KeyEvent.ACTION_DOWN) {
            val foco = currentFocus
            if (!cajonAbierto && e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && foco?.parent == fila) {
                abrirCajon(); return true
            }
            if (cajonAbierto && e.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                cuadricula.selectedItemPosition < maxOf(1, cuadricula.numColumns)) {
                cerrarCajon(); return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    // ---------- Fila de apps fijadas ----------

    /** Tamaño del icono según cuántas apps hay: grandes hasta 4, luego se achican hasta 8. */
    private fun tamanoIconoDp(n: Int) = when {
        n <= 4 -> 80; n == 5 -> 72; n == 6 -> 66; n == 7 -> 60; else -> 56
    }

    private fun armarFila(enfocar: Boolean) {
        val porPaquete = todas.associateBy { it.paquete }
        val enfocado = (currentFocus?.tag as? String)
        fila.removeAllViews()
        val apps = Apps.fijadas(this).mapNotNull { porPaquete[it] }.take(Apps.MAXIMO)
        val n = apps.size
        val dock = findViewById<View>(R.id.dock)
        dock.visibility = if (n == 0) View.GONE else View.VISIBLE

        val iconoDp = tamanoIconoDp(n)
        val anchoDp = iconoDp + 34                       // espacio entre iconos proporcional
        val px = { v: Int -> (v * dp).toInt() }
        dock.setPadding(px(iconoDp / 5), px(14), px(iconoDp / 5), px(10))
        val inflador = LayoutInflater.from(this)
        for (app in apps) {
            val vista = inflador.inflate(R.layout.icono_app, fila, false)
            vista.tag = app.paquete
            vista.layoutParams.width = px(anchoDp)
            vista.findViewById<View>(R.id.marco).apply {
                layoutParams.apply { width = px(iconoDp + 10); height = px(iconoDp + 10) }
                background = anillo(px(iconoDp + 10))
            }
            vista.findViewById<ImageView>(R.id.icono).setImageBitmap(Iconos.de(this, app, px(iconoDp), nocheIconos(), paletaIconos()))
            val etiqueta = vista.findViewById<TextView>(R.id.nombre)
            etiqueta.textSize = if (n <= 6) 13f else 12f
            vista.setOnClickListener { if (!Apps.abrir(this, app.paquete)) aviso(R.string.no_abre) }
            vista.setOnLongClickListener { empezarMover(it); true }
            vista.setOnFocusChangeListener { v, foco ->
                val escala = if (foco) 1.1f else 1f
                v.animate().scaleX(escala).scaleY(escala).setDuration(140).setInterpolator(suave).start()
            }
            fila.addView(vista)
            Fuente.aplicar(vista)
            etiqueta.text = cortarPorPalabras(app.nombre, etiqueta, px(anchoDp - 8).toFloat())
        }
        if (enfocar) {
            val destino = (0 until fila.childCount).map { fila.getChildAt(it) }
                .firstOrNull { it.tag == enfocado } ?: fila.getChildAt(0)
            destino?.requestFocus()
        }
    }

    /**
     * Nombre cortado por palabras completas, sin "…":
     * "Xuper Crystal Clear" → "Xuper"; si el nombre cabe entero, se deja igual.
     */
    private fun cortarPorPalabras(nombre: String, vista: TextView, ancho: Float): String {
        val pintura = vista.paint
        if (pintura.measureText(nombre) <= ancho) return nombre
        val palabras = nombre.split(" ").filter { it.isNotBlank() }
        var texto = palabras.firstOrNull() ?: return nombre
        for (p in palabras.drop(1)) {
            val probar = "$texto $p"
            if (pintura.measureText(probar) > ancho) break
            texto = probar
        }
        // Si una sola palabra no cabe (p. ej. "Downloader"), se achica un poco la letra
        while (pintura.measureText(texto) > ancho && vista.textSize > 10 * dp) {
            vista.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, vista.textSize - dp)
        }
        return texto
    }

    private fun empezarMover(v: View) {
        moviendo = v
        ignorarSoltarOk = true
        v.isActivated = true
        pista.setText(R.string.pista_mover)
        pista.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
    }

    private fun moverA(v: View, paso: Int) {
        val desde = fila.indexOfChild(v)
        val hasta = desde + paso
        if (hasta < 0 || hasta >= fila.childCount) return
        fila.removeViewAt(desde)
        fila.addView(v, hasta)
        v.requestFocus()
    }

    private fun terminarMover() {
        val v = moviendo ?: return
        moviendo = null
        v.isActivated = false
        val orden = (0 until fila.childCount).mapNotNull { fila.getChildAt(it).tag as? String }
        val ocultas = Apps.fijadas(this).filter { it !in orden } // fijadas que hoy no están instaladas
        Apps.guardarFijadas(this, orden + ocultas)
        pista.setText(R.string.todas_las_apps)
        pista.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_flecha_abajo, 0, 0, 0)
    }

    // ---------- Cajón con todas las apps ----------

    /** Lo que forma el inicio: se desvanece cuando suben las apps. */
    private val portada by lazy {
        listOf(R.id.bloque_reloj, R.id.barra, R.id.dock, R.id.pista).map { findViewById<View>(it) }
    }
    private val suave = android.view.animation.DecelerateInterpolator(2f)
    private val dp by lazy { resources.displayMetrics.density }

    private fun abrirCajon() {
        cajonAbierto = true
        for (v in portada) {
            v.animate().cancel()
            v.animate().alpha(0f).translationY(-24 * dp).setDuration(220).setInterpolator(suave)
                .withEndAction { if (cajonAbierto) v.visibility = View.INVISIBLE }.start()
        }
        cajon.animate().cancel()
        cajon.alpha = 0f
        cajon.translationY = 56 * dp
        cajon.visibility = View.VISIBLE
        cajon.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(suave).start()
        cuadricula.setSelection(0)
        cuadricula.requestFocus()
    }

    private fun cerrarCajon() {
        cajonAbierto = false
        cajon.animate().cancel()
        cajon.animate().alpha(0f).translationY(56 * dp).setDuration(240).setInterpolator(suave)
            .withEndAction { if (!cajonAbierto) cajon.visibility = View.GONE }.start()
        for (v in portada) {
            v.animate().cancel()
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(suave).start()
        }
        fila.getChildAt(0)?.requestFocus()
    }

    private inner class AdaptadorCajon : BaseAdapter() {
        private val tam = (60 * resources.displayMetrics.density).toInt()
        override fun getCount() = todas.size
        override fun getItem(pos: Int) = todas[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, reciclada: View?, padre: ViewGroup): View {
            val vista = reciclada ?: LayoutInflater.from(padre.context)
                .inflate(R.layout.icono_app, padre, false).apply {
                    // En la cuadrícula el foco lo maneja la cuadrícula, no cada icono
                    isFocusable = false
                    isClickable = false
                    findViewById<View>(R.id.marco).apply {
                        val lado = tam + (10 * resources.displayMetrics.density).toInt()
                        layoutParams.apply { width = lado; height = lado }
                        background = anillo(lado)
                    }
                    findViewById<TextView>(R.id.nombre).textSize = 12f
                    Fuente.aplicar(this)
                }
            val app = todas[pos]
            vista.findViewById<ImageView>(R.id.icono).setImageBitmap(Iconos.de(this@MainActivity, app, tam, nocheIconos(), paletaIconos()))
            val etiqueta = vista.findViewById<TextView>(R.id.nombre)
            etiqueta.textSize = 12f
            etiqueta.text = cortarPorPalabras(app.nombre, etiqueta, 96 * dp)
            return vista
        }
    }

    // ---------- Fondo, fecha, clima, entradas ----------

    /** Vuelve a cargar el fondo solo si se eligió otro o cambió el archivo. */
    private var firmaFondo = ""
    private fun revisarFondo() {
        val firma = Fondos.firma(this)
        if (firma == firmaFondo) return
        firmaFondo = firma
        val fondo = findViewById<ImageView>(R.id.fondo)
        val bmp = Fondos.cargar(this, Fondos.actual(this), 1920)
        if (bmp != null) fondo.setImageBitmap(bmp) else fondo.setImageResource(R.drawable.fondo)
        paleta = Paleta.desde(bmp ?: Fondos.cargar(this, "res:noche", 480))
        aplicarPaleta()
    }

    /** Colores Material You sacados del fondo actual. */
    private var paleta = Paleta.PREDETERMINADA

    /** Solo los iconos temáticos cambian de noche; los originales se ven siempre igual. */
    private fun nocheIconos() = noche && EstiloIconos.tematicos(this)

    /** Paleta para los iconos: solo si están en modo temático. */
    private fun paletaIconos(): Paleta? = if (EstiloIconos.tematicos(this)) paleta else null

    /** Velo muy suave del color del fondo en el dock y en los botones de arriba. */
    private fun aplicarPaleta() {
        findViewById<View>(R.id.dock).background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 36 * dp; setColor(paleta.veloDock())
        }
        for (id in listOf(R.id.btn_fondos, R.id.btn_red, R.id.btn_modo, R.id.btn_hdmi, R.id.btn_ajustes)) {
            findViewById<View>(id).background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt())
                })
                addState(intArrayOf(), android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(paleta.veloBoton())
                })
            }
        }
    }

    /** Anillo de foco con la forma de los iconos (squircle). */
    private fun anillo(ladoPx: Int): android.graphics.drawable.Drawable {
        fun forma(trazo: Boolean, discontinuo: Boolean) = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = ladoPx * 0.34f
            if (discontinuo) { setColor(0x33FFFFFF); setStroke((2.5f * dp).toInt(), 0xFFFFFFFF.toInt(), 6 * dp, 4 * dp) }
            else if (trazo) setStroke((2.5f * dp).toInt(), 0xFFFFFFFF.toInt())
            else setColor(0)
        }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_activated), forma(true, true))
            addState(intArrayOf(android.R.attr.state_focused), forma(true, false))
            addState(intArrayOf(android.R.attr.state_selected), forma(true, false))
            addState(intArrayOf(), forma(false, false))
        }
    }

    /** ¿Los iconos se dibujan en modo noche? */
    private var noche = false

    /** Actualiza el botón y, si cambió día/noche, vuelve a dibujar los iconos. */
    private fun aplicarModo(forzar: Boolean) {
        val estado = Modo.estado(this)
        val ahora = Modo.esNoche(this)
        val boton = findViewById<ImageButton>(R.id.btn_modo)
        boton.setImageResource(if (ahora) R.drawable.ic_luna else R.drawable.ic_dia)
        // En automático el icono se corre un poco hacia arriba a la izquierda para dejarle sitio a la "A"
        val p = { v: Int -> (v * dp).toInt() }
        if (estado == Modo.Estado.AUTOMATICO) boton.setPadding(p(10), p(10), p(14), p(14))
        else boton.setPadding(p(12), p(12), p(12), p(12))
        findViewById<View>(R.id.insignia_auto).visibility =
            if (estado == Modo.Estado.AUTOMATICO) View.VISIBLE else View.GONE
        if (ahora != noche || forzar) {
            noche = ahora
            if (moviendo == null) armarFila(enfocar = currentFocus?.parent == fila)
            (cuadricula.adapter as? BaseAdapter)?.notifyDataSetChanged()
        }
    }

    private fun actualizarFechaYSaludo() {
        val texto = SimpleDateFormat("EEEE d 'de' MMMM", espanol).format(Date())
        fecha.text = texto.replaceFirstChar { it.titlecase(espanol) }
        saludo.text = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    private fun actualizarClima() {
        ultimoClima = System.currentTimeMillis()
        Thread {
            val dato = Clima.consultar() ?: return@Thread
            runOnUiThread {
                climaIcono.setImageResource(dato.icono)
                climaIcono.visibility = View.VISIBLE
                clima.text = if (dato.descripcion.isEmpty()) "${dato.grados}°"
                             else "${dato.grados}° · ${dato.descripcion}"
            }
        }.start()
    }

    // ---------- Panel de entradas (cable y HDMI) ----------

    private var panelAbierto = false
    private val panel by lazy { findViewById<View>(R.id.panel_entradas) }
    private val hoja by lazy { findViewById<View>(R.id.hoja_entradas) }
    private val listaEntradas by lazy { findViewById<LinearLayout>(R.id.lista_entradas) }

    private fun abrirPanelEntradas(enfocar: String? = null) {
        val enfocarId = enfocar
        val entradas = Entradas.lista(this)
        if (entradas.none { !it.esCable }) { aviso(R.string.sin_hdmi); return }
        listaEntradas.removeAllViews()
        val inflador = LayoutInflater.from(this)
        var primeraConectada: View? = null
        for (e in entradas) {
            val fila = inflador.inflate(R.layout.fila_entrada, listaEntradas, false)
            fila.tag = e.id
            fila.findViewById<ImageView>(R.id.icono_entrada)
                .setImageResource(if (e.esCable) R.drawable.ic_tv else R.drawable.ic_hdmi)
            fila.findViewById<TextView>(R.id.nombre_entrada).text = e.nombre
            fila.findViewById<TextView>(R.id.detalle_entrada).apply {
                text = e.detalle ?: ""
                visibility = if (e.detalle.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            fila.findViewById<TextView>(R.id.estado_entrada).apply {
                visibility = if (e.esCable) View.GONE else View.VISIBLE
                setText(if (e.conectada) R.string.conectado else R.string.sin_senal)
            }
            fila.findViewById<View>(R.id.punto_entrada).visibility =
                if (!e.esCable && e.conectada) View.VISIBLE else View.GONE
            if (!e.esCable && !e.conectada) fila.alpha = 0.5f
            fila.setOnClickListener {
                cerrarPanelEntradas()
                if (!Entradas.abrir(this, e.id)) aviso(R.string.no_abre)
            }
            if (!e.esCable) fila.setOnLongClickListener { nombrarEntrada(e); true }
            if (!e.esCable && e.conectada && primeraConectada == null) primeraConectada = fila
            listaEntradas.addView(fila)
        }
        Fuente.aplicar(panel)

        panelAbierto = true
        bloquearInicio(true)
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        hoja.translationX = 80 * dp
        panel.animate().alpha(1f).setDuration(220).setInterpolator(suave).start()
        hoja.animate().translationX(0f).setDuration(300).setInterpolator(suave).start()
        barra.animate().alpha(0f).setDuration(200).setInterpolator(suave).start()
        (enfocarId?.let { id -> (0 until listaEntradas.childCount).map { listaEntradas.getChildAt(it) }.firstOrNull { it.tag == id } }
            ?: primeraConectada ?: listaEntradas.getChildAt(0))?.requestFocus()
    }

    private val barra by lazy { findViewById<View>(R.id.barra) }

    /** Ventana para escribir el nombre de una entrada (por ejemplo "PC" o "TV Box"). */
    private fun nombrarEntrada(e: Entradas.Entrada) {
        val campo = android.widget.EditText(this).apply {
            setText(Entradas.nombrePropio(this@MainActivity, e.id) ?: "")
            setSelection(text.length)
            hint = getString(R.string.nombre_ejemplo)
            setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val marco = android.widget.FrameLayout(this).apply {
            val m = (24 * dp).toInt()
            setPadding(m, (8 * dp).toInt(), m, 0)
            addView(campo)
        }
        val guardar = {
            Entradas.ponerNombre(this, e.id, campo.text.toString())
            abrirPanelEntradas(enfocar = e.id)
        }
        val dialogo = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.nombrar_entrada, e.nombre))
            .setView(marco)
            .setPositiveButton(R.string.guardar) { _, _ -> guardar() }
            .setNeutralButton(R.string.quitar_nombre) { _, _ -> campo.setText(""); guardar() }
            .setNegativeButton(R.string.cancelar, null)
            .create()
        campo.setOnEditorActionListener { _, _, _ -> guardar(); dialogo.dismiss(); true }
        dialogo.setOnShowListener { campo.requestFocus() }
        dialogo.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialogo.show()
    }

    /** Mientras el panel está abierto, el foco no se escapa al inicio de atrás. */
    private fun bloquearInicio(bloquear: Boolean) {
        val modo = if (bloquear) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        listOf(R.id.barra, R.id.dock).forEach { findViewById<ViewGroup>(it).descendantFocusability = modo }
    }

    private fun cerrarPanelEntradas() {
        if (!panelAbierto) return
        panelAbierto = false
        bloquearInicio(false)
        hoja.animate().translationX(80 * dp).setDuration(220).setInterpolator(suave).start()
        barra.animate().alpha(1f).setDuration(260).setInterpolator(suave).start()
        panel.animate().alpha(0f).setDuration(220).setInterpolator(suave)
            .withEndAction { if (!panelAbierto) panel.visibility = View.GONE }.start()
        findViewById<View>(R.id.btn_hdmi).requestFocus()
    }

    private fun abrirAjuste(vararg acciones: String) {
        for (accion in acciones.toList() + Settings.ACTION_SETTINGS) {
            try {
                startActivity(Intent(accion).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            } catch (_: Exception) { }
        }
        aviso(R.string.no_abre)
    }

    private fun aviso(texto: Int) = Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
}
