package com.davidsant7e.emperatriz.datos

import android.content.Context
import com.davidsant7e.emperatriz.nucleo.Datos
import com.davidsant7e.emperatriz.registro.Registro
import java.io.File

/** La lista se guarda como un JSON en la memoria privada de la app (el mismo formato del respaldo). */
class Almacen(private val ctx: Context) {
    private val archivo = File(ctx.filesDir, "lista.json")

    fun cargar(hoy: String): Datos = try {
        if (archivo.exists()) Datos.deJson(archivo.readText(), hoy) else Datos()
    } catch (e: Exception) {
        Registro.error("ALMACEN", e)
        // Un archivo dañado no debe impedir abrir la app: se aparta y se empieza de nuevo.
        archivo.renameTo(File(ctx.filesDir, "lista-danada-${System.currentTimeMillis()}.json"))
        Datos()
    }

    /** Escritura atómica: primero a un temporal y luego se renombra. */
    @Synchronized
    fun guardar(d: Datos) {
        val tmp = File(ctx.filesDir, "lista.json.tmp")
        tmp.writeText(d.aJson())
        if (!tmp.renameTo(archivo)) {
            archivo.delete()
            tmp.renameTo(archivo)
        }
    }
}
