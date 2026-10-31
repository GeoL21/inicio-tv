package com.geol.inicio

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Fuente de la app: Google Sans Flex (licencia OFL, incluida en el APK).
 * Normal 400 y media 500; el reloj usa su propio corte para tamaños grandes.
 * Ya no se usan fuentes personales copiadas a la TV: se ignoran.
 */
object Fuente {
    private var familia: Typeface? = null

    /** Aplica la fuente a todos los textos, respetando el grosor de cada uno. */
    fun aplicar(vista: View) {
        val base = familia ?: vista.resources.getFont(R.font.google_sans).also { familia = it }
        fun recorrer(v: View) {
            if (v is TextView) {
                if (v.id == R.id.reloj) return
                val peso = (v.getTag(R.id.peso_texto) as? Int)
                    ?: (v.typeface?.weight ?: 400).also { v.setTag(R.id.peso_texto, it) }
                v.typeface = Typeface.create(base, if (peso >= 500) 500 else 400, false)
            } else if (v is ViewGroup) {
                for (i in 0 until v.childCount) recorrer(v.getChildAt(i))
            }
        }
        recorrer(vista)
    }
}
