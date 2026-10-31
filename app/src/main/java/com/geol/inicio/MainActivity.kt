package com.geol.inicio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
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

        val fondo = findViewById<ImageView>(R.id.fondo)
        Apps.fondo(this)?.let { fondo.setImageBitmap(it) } ?: fondo.setImageResource(R.drawable.fondo)

        findViewById<ImageButton>(R.id.btn_red).setOnClickListener {
            abrirAjuste(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
        }
        findViewById<ImageButton>(R.id.btn_ajustes).setOnClickListener {
            abrirAjuste(Settings.ACTION_SETTINGS)
        }
        val btnEntradas = findViewById<ImageButton>(R.id.btn_entradas)
        if (paqueteTv() == null) btnEntradas.visibility = View.GONE // la TV box no tiene antena
        btnEntradas.setOnClickListener {
            val p = paqueteTv()
            if (p == null || !Apps.abrir(this, p)) aviso(R.string.sin_entradas)
        }
    }

    override fun onResume() {
        super.onResume()
        armarFila()
        manejador.removeCallbacks(cadaMinuto)
        manejador.post(cadaMinuto)
    }

    override fun onPause() {
        super.onPause()
        manejador.removeCallbacks(cadaMinuto)
    }

    // Es la pantalla de inicio: el botón Atrás no hace nada aquí.
    @Deprecated("Se mantiene para Android TV")
    override fun onBackPressed() {}

    private fun armarFila() {
        fila.removeAllViews()
        val instaladas = Apps.todas(this).associateBy { it.paquete }
        val inflador = LayoutInflater.from(this)
        val margen = (20 * resources.displayMetrics.density).toInt()

        fun agregar(nombre: String, icono: android.graphics.drawable.Drawable, alAbrir: () -> Unit) {
            val vista = inflador.inflate(R.layout.mosaico, fila, false)
            vista.findViewById<ImageView>(R.id.icono).setImageDrawable(icono)
            vista.findViewById<TextView>(R.id.nombre).text = nombre
            vista.setOnClickListener { alAbrir() }
            vista.setOnFocusChangeListener { v, foco ->
                val escala = if (foco) 1.08f else 1f
                v.animate().scaleX(escala).scaleY(escala).setDuration(120).start()
            }
            (vista.layoutParams as LinearLayout.LayoutParams).marginEnd = margen
            fila.addView(vista)
        }

        for (paquete in Apps.fijadas(this)) {
            val app = instaladas[paquete] ?: continue
            agregar(app.nombre, app.icono) { if (!Apps.abrir(this, app.paquete)) aviso(R.string.no_abre) }
        }
        agregar(getString(R.string.todas_las_apps), getDrawable(R.drawable.ic_apps)!!) {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        fila.getChildAt(0)?.requestFocus()
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

    /** App de TV en vivo (antena/cable/HDMI) del equipo, si la tiene. */
    private fun paqueteTv(): String? = listOf(
        "com.mediatek.wwtv.tvcenter", // Hisense y otras TV con MediaTek
        "com.android.tv",             // TV en vivo de Android
        "com.google.android.tv"       // Canales en vivo de Google
    ).firstOrNull { p ->
        packageManager.getLeanbackLaunchIntentForPackage(p) != null ||
            packageManager.getLaunchIntentForPackage(p) != null
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
