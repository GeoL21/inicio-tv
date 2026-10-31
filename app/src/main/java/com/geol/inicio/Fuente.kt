package com.geol.inicio

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File

/**
 * Fuente personal opcional. Si existe fuente.otf o fuente.ttf en
 * /sdcard/Android/data/com.geol.inicio/files/, se usa esa; si no, Inter.
 * El archivo nunca va dentro de la app ni en GitHub.
 */
object Fuente {
    private var firmaCache = ""
    private var cache: Typeface? = null

    private fun archivo(ctx: Context): File? {
        val dir = ctx.getExternalFilesDir(null) ?: return null
        return listOf("fuente.otf", "fuente.ttf").map { File(dir, it) }.firstOrNull { it.exists() }
    }

    fun base(ctx: Context): Typeface {
        val f = archivo(ctx)
        val firma = f?.let { it.path + it.lastModified() } ?: "inter"
        if (firma != firmaCache || cache == null) {
            cache = f?.let { try { Typeface.createFromFile(it) } catch (e: Exception) { null } }
                ?: ctx.resources.getFont(R.font.inter)
            firmaCache = firma
        }
        return cache!!
    }

    /** Aplica la fuente a todos los textos, respetando el grosor de cada uno. */
    fun aplicar(vista: View) {
        val tf = base(vista.context)
        fun recorrer(v: View) {
            if (v is TextView) {
                val peso = (v.getTag(R.id.peso_texto) as? Int)
                    ?: (v.typeface?.weight ?: 400).also { v.setTag(R.id.peso_texto, it) }
                v.typeface = Typeface.create(tf, peso, false)
            } else if (v is ViewGroup) {
                for (i in 0 until v.childCount) recorrer(v.getChildAt(i))
            }
        }
        recorrer(vista)
    }
}
