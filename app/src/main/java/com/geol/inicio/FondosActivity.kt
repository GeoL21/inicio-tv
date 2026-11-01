package com.geol.inicio

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Outline
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView

/** Cuadrícula con miniaturas de fondos. OK aplica el fondo al instante. */
class FondosActivity : Activity() {

    private lateinit var fondos: List<Fondos.Fondo>
    private val miniaturas = HashMap<String, Bitmap>()
    private lateinit var adaptador: Adaptador
    private lateinit var vistaFondo: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fondos)
        vistaFondo = findViewById(R.id.fondo)
        mostrarFondoActual()

        fondos = Fondos.todos(this)
        adaptador = Adaptador()
        val cuadricula = findViewById<GridView>(R.id.cuadricula)
        cuadricula.adapter = adaptador
        cuadricula.setOnItemClickListener { _, _, pos, _ ->
            Fondos.elegir(this, fondos[pos].clave)
            mostrarFondoActual()
            adaptador.notifyDataSetChanged()
        }
        val actual = fondos.indexOfFirst { it.clave == Fondos.actual(this) }
        if (actual >= 0) cuadricula.setSelection(actual)
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

        // Miniaturas pequeñas en segundo plano para no trabar el control remoto
        Thread {
            for (f in fondos) {
                val bmp = Fondos.cargar(this, f.clave, 480) ?: continue
                runOnUiThread { miniaturas[f.clave] = bmp; adaptador.notifyDataSetChanged() }
            }
        }.start()
    }

    private fun mostrarFondoActual() {
        val bmp = Fondos.cargar(this, Fondos.actual(this), 1920)
        if (bmp != null) vistaFondo.setImageBitmap(bmp) else vistaFondo.setImageResource(R.drawable.fondo)
    }

    private inner class Adaptador : BaseAdapter() {
        private val esquinas = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) =
                o.setRoundRect(0, 0, v.width, v.height, 12 * v.resources.displayMetrics.density)
        }

        override fun getCount() = fondos.size
        override fun getItem(pos: Int) = fondos[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, reciclada: View?, padre: ViewGroup): View {
            val vista = reciclada ?: LayoutInflater.from(padre.context)
                .inflate(R.layout.miniatura_fondo, padre, false).also {
                    it.findViewById<View>(R.id.imagen).apply { outlineProvider = esquinas; clipToOutline = true }
                    Fuente.aplicar(it)
                }
            val f = fondos[pos]
            vista.findViewById<ImageView>(R.id.imagen).setImageBitmap(miniaturas[f.clave])
            vista.findViewById<TextView>(R.id.nombre).text = f.nombre
            vista.findViewById<View>(R.id.marca).visibility =
                if (f.clave == Fondos.actual(this@FondosActivity)) View.VISIBLE else View.GONE
            return vista
        }
    }
}
