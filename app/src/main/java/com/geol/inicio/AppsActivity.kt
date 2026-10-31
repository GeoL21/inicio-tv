package com.geol.inicio

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/** Cuadrícula con todas las apps. OK abre; mantener OK fija o quita del inicio. */
class AppsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        val fondo = findViewById<ImageView>(R.id.fondo)
        Apps.fondo(this)?.let { fondo.setImageBitmap(it) } ?: fondo.setImageResource(R.drawable.fondo)

        val apps = Apps.todas(this)
        val cuadricula = findViewById<GridView>(R.id.cuadricula)
        cuadricula.adapter = Adaptador(apps)
        cuadricula.setOnItemClickListener { _, _, pos, _ ->
            if (!Apps.abrir(this, apps[pos].paquete))
                Toast.makeText(this, R.string.no_abre, Toast.LENGTH_SHORT).show()
        }
        cuadricula.setOnItemLongClickListener { _, _, pos, _ ->
            val fijada = Apps.alternarFijada(this, apps[pos].paquete)
            Toast.makeText(this, if (fijada) R.string.fijada else R.string.quitada,
                Toast.LENGTH_SHORT).show()
            true
        }
        cuadricula.requestFocus()
    }

    private inner class Adaptador(private val apps: List<App>) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(pos: Int) = apps[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, reciclada: View?, padre: ViewGroup): View {
            val vista = reciclada ?: LayoutInflater.from(padre.context)
                .inflate(R.layout.mosaico, padre, false).apply {
                    // En una cuadrícula el foco lo maneja la cuadrícula, no cada mosaico
                    isFocusable = false
                    isClickable = false
                }
            val app = apps[pos]
            vista.findViewById<ImageView>(R.id.icono).setImageDrawable(app.icono)
            vista.findViewById<TextView>(R.id.nombre).text = app.nombre
            return vista
        }
    }
}
