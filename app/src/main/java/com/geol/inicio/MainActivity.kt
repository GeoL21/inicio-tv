package com.geol.inicio

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.HorizontalScrollView
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
    private lateinit var desplazar: HorizontalScrollView
    private lateinit var pista: TextView
    private lateinit var cajon: View
    private lateinit var cuadricula: GridView

    private var todas: List<App> = emptyList()
    private var cajonAbierto = false

    /** App de la fila que se está moviendo con las flechas (null si ninguna). */
    private var moviendo: View? = null
    private var ignorarSoltarOk = false

    private val tamFila by lazy { (72 * resources.displayMetrics.density).toInt() }

    /** Actualiza fecha y saludo cada minuto (el reloj se actualiza solo). */
    private val cadaMinuto = object : Runnable {
        override fun run() {
            actualizarFechaYSaludo()
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
        desplazar = findViewById(R.id.desplazar)
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

        // Cable/antena y HDMI: se ocultan en equipos que no los tienen (la TV box)
        val btnTv = findViewById<ImageButton>(R.id.btn_entradas)
        if (Entradas.paqueteTv(this) == null) btnTv.visibility = View.GONE
        btnTv.setOnClickListener {
            val p = Entradas.paqueteTv(this)
            if (p == null || !Apps.abrir(this, p)) aviso(R.string.sin_entradas)
        }
        val btnHdmi = findViewById<ImageButton>(R.id.btn_hdmi)
        if (Entradas.hdmi(this).isEmpty()) btnHdmi.visibility = View.GONE
        btnHdmi.setOnClickListener { elegirHdmi() }

        cuadricula.setOnItemClickListener { _, _, pos, _ ->
            if (!Apps.abrir(this, todas[pos].paquete)) aviso(R.string.no_abre)
        }
        cuadricula.setOnItemLongClickListener { _, _, pos, _ ->
            val fijada = Apps.alternarFijada(this, todas[pos].paquete)
            aviso(if (fijada) R.string.fijada else R.string.quitada)
            armarFila(enfocar = false)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        revisarFondo()
        todas = Apps.todas(this)
        cuadricula.adapter = AdaptadorCajon()
        if (moviendo == null) armarFila(enfocar = !cajonAbierto)
        Fuente.aplicar(window.decorView)
        manejador.removeCallbacks(cadaMinuto)
        manejador.post(cadaMinuto)
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

    private fun armarFila(enfocar: Boolean) {
        val porPaquete = todas.associateBy { it.paquete }
        val enfocado = (currentFocus?.tag as? String)
        fila.removeAllViews()
        val inflador = LayoutInflater.from(this)
        for (paquete in Apps.fijadas(this)) {
            val app = porPaquete[paquete] ?: continue
            val vista = inflador.inflate(R.layout.icono_app, fila, false)
            vista.tag = app.paquete
            vista.findViewById<ImageView>(R.id.icono).setImageBitmap(Iconos.de(this, app, tamFila))
            vista.findViewById<TextView>(R.id.nombre).text = app.nombre
            vista.setOnClickListener { if (!Apps.abrir(this, app.paquete)) aviso(R.string.no_abre) }
            vista.setOnLongClickListener { empezarMover(it); true }
            vista.setOnFocusChangeListener { v, foco ->
                val escala = if (foco) 1.1f else 1f
                v.animate().scaleX(escala).scaleY(escala).setDuration(120).start()
            }
            fila.addView(vista)
        }
        Fuente.aplicar(fila)
        if (enfocar) {
            val destino = (0 until fila.childCount).map { fila.getChildAt(it) }
                .firstOrNull { it.tag == enfocado } ?: fila.getChildAt(0)
            destino?.requestFocus()
        }
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
        desplazar.post { desplazar.requestChildFocus(fila, v) }
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

    private fun abrirCajon() {
        cajonAbierto = true
        cajon.visibility = View.VISIBLE
        cajon.translationY = cajon.height.toFloat().takeIf { it > 0 } ?: 600f
        cajon.animate().translationY(0f).setDuration(220).start()
        pista.visibility = View.INVISIBLE
        cuadricula.requestFocus()
        cuadricula.setSelection(0)
    }

    private fun cerrarCajon() {
        cajonAbierto = false
        cajon.animate().translationY(cajon.height.toFloat()).setDuration(200)
            .withEndAction { cajon.visibility = View.GONE }.start()
        pista.visibility = View.VISIBLE
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
                    findViewById<View>(R.id.marco).layoutParams.apply {
                        val lado = tam + (10 * resources.displayMetrics.density).toInt()
                        width = lado; height = lado
                    }
                    findViewById<TextView>(R.id.nombre).textSize = 12f
                    Fuente.aplicar(this)
                }
            val app = todas[pos]
            vista.findViewById<ImageView>(R.id.icono).setImageBitmap(Iconos.de(this@MainActivity, app, tam))
            vista.findViewById<TextView>(R.id.nombre).text = app.nombre
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

    private fun elegirHdmi() {
        val entradas = Entradas.hdmi(this)
        if (entradas.isEmpty()) { aviso(R.string.sin_hdmi); return }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.hdmi)
            .setItems(entradas.map { it.second }.toTypedArray()) { _, i ->
                if (!Entradas.abrirHdmi(this, entradas[i].first)) aviso(R.string.no_abre)
            }
            .show()
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
