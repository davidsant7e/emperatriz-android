package com.davidsant7e.emperatriz.datos

import android.content.Context
import com.davidsant7e.emperatriz.nucleo.Gemini

/** Preferencias simples (la clave de Gemini va aparte, cifrada, en Boveda). */
class Ajustes(ctx: Context) {
    private val p = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var modelo: String
        get() = p.getString("modelo", null)?.takeIf { it.isNotBlank() } ?: Gemini.MODELO
        set(v) = p.edit().putString("modelo", v.trim()).apply()

    var lugar: Gemini.Lugar
        get() = Gemini.Lugar(
            pais = p.getString("pais", null) ?: "Colombia",
            ciudad = p.getString("ciudad", null) ?: "",
            moneda = p.getString("moneda", null) ?: "COP",
        )
        set(v) = p.edit().putString("pais", v.pais.trim()).putString("ciudad", v.ciudad.trim())
            .putString("moneda", v.moneda.trim().uppercase()).apply()

    /** Proyecto que se pone por defecto a lo que se agrega (vacío = ninguno). */
    var proyectoActual: String
        get() = p.getString("proyecto", null) ?: ""
        set(v) = p.edit().putString("proyecto", v).apply()
}
