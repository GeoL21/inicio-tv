package com.geol.inicio

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File

/**
 * Fuente personal opcional, desde /sdcard/Android/data/com.geol.inicio/files/:
 *   fuente.otf          → textos normales (grosor 400)
 *   fuente-medium.otf   → reloj, saludo y nombres de apps (grosor 500)
 * También sirven con extensión .ttf. Si falta la media, se engrosa la normal;
 * si faltan las dos, se usa Inter. Los archivos nunca van en la app ni en GitHub.
 */
object Fuente {
    private var firmaCache = ""
    private var normal: Typeface? = null
    private var media: Typeface? = null

    private fun archivo(ctx: Context, nombre: String): File? {
        val dir = ctx.getExternalFilesDir(null) ?: return null
        return listOf("$nombre.otf", "$nombre.ttf").map { File(dir, it) }.firstOrNull { it.exists() }
    }

    private fun leer(f: File?): Typeface? =
        f?.let { try { Typeface.createFromFile(it) } catch (e: Exception) { null } }

    private fun cargar(ctx: Context) {
        val fNormal = archivo(ctx, "fuente")
        val fMedia = archivo(ctx, "fuente-medium")
        val firma = listOf(fNormal, fMedia).joinToString { it?.let { f -> f.path + f.lastModified() } ?: "-" }
        if (firma == firmaCache && normal != null) return
        normal = leer(fNormal)
        media = leer(fMedia)
        if (normal == null && media == null) normal = ctx.resources.getFont(R.font.inter)
        if (normal == null) normal = media
        firmaCache = firma
    }

    private fun para(peso: Int): Typeface {
        val m = media
        return if (peso >= 500 && m != null) m else Typeface.create(normal!!, peso, false)
    }

    /** Aplica la fuente a todos los textos, respetando el grosor de cada uno. */
    fun aplicar(vista: View) {
        cargar(vista.context)
        fun recorrer(v: View) {
            if (v is TextView) {
                val peso = (v.getTag(R.id.peso_texto) as? Int)
                    ?: (v.typeface?.weight ?: 400).also { v.setTag(R.id.peso_texto, it) }
                v.typeface = para(peso)
            } else if (v is ViewGroup) {
                for (i in 0 until v.childCount) recorrer(v.getChildAt(i))
            }
        }
        recorrer(vista)
    }
}
