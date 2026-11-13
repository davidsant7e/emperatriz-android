package com.davidsant7e.emperatriz.nucleo

/** Lo que sale de la app: la lista para compartir, el texto para Claude y la hoja de cálculo. */
object Exportar {

    fun linea(p: Producto): String {
        val cant = if (p.cantidad == 1.0 && p.unidad.isBlank()) "" else Unidades.texto(p.cantidad, p.unidad) + " "
        val extra = listOfNotNull(p.proyecto.takeIf { it.isNotBlank() }, p.notas.takeIf { it.isNotBlank() }).joinToString(" · ")
        return cant + p.nombre + if (extra.isNotEmpty()) " ($extra)" else ""
    }

    /** Lista para WhatsApp o notas: por sección, con casillas. */
    fun texto(d: Datos, seccion: String? = null, proyecto: String? = null): String {
        val grupos = Lista.pendientesPorSeccion(d, seccion, proyecto)
        if (grupos.isEmpty()) return "Lista vacía"
        val titulo = listOfNotNull("🛒 Lista de compras", proyecto?.let { "· $it" }).joinToString(" ")
        return buildString {
            append(titulo).append('\n')
            grupos.forEach { (s, ps) ->
                append('\n').append(Secciones.nombre(s).uppercase()).append('\n')
                ps.forEach { append("☐ ").append(linea(it)).append('\n') }
            }
        }.trim()
    }

    /**
     * Para pegar o compartir con Claude: un resumen legible y el JSON que la app sabe leer de vuelta.
     * Así Claude puede analizarlo, cruzarlo con otros proyectos y devolver un JSON para importar.
     */
    fun paraClaude(d: Datos, hoy: String, soloPendientes: Boolean = false): String {
        val ps = if (soloPendientes) d.productos.filter { !it.comprado } else d.productos
        val compacto = linkedMapOf(
            "app" to Datos.APP, "version" to Datos.VERSION, "actualizado" to hoy,
            "proyectos" to d.proyectos.filter { !it.archivado }.map { it.aMapa() },
            "productos" to ps.map { p ->
                linkedMapOf<String, Any?>(
                    "id" to p.id, "nombre" to p.nombre, "cantidad" to p.cantidad, "unidad" to p.unidad,
                    "seccion" to p.seccion, "proyecto" to p.proyecto, "notas" to p.notas, "enlace" to p.enlace,
                    "estado" to p.estado.clave,
                    "veces_comprado" to p.historial.size,
                    "ultima_compra" to p.historial.lastOrNull()?.aMapa(),
                    "mejor_precio" to p.mejorPrecio?.aMapa(),
                ).filterValues { it != null && it != "" }
            },
        )
        val pendientes = d.productos.count { !it.comprado }
        return buildString {
            append("Mi lista de La Emperatriz (").append(hoy).append("): ")
            append(pendientes).append(" pendientes, ").append(d.productos.size - pendientes).append(" comprados")
            if (d.nombresProyectos.isNotEmpty()) append(", proyectos: ").append(d.nombresProyectos.joinToString(", "))
            append(".\n\n")
            append(texto(d)).append("\n\n")
            append("Si me devuelves cambios, responde con un JSON {\"productos\":[{\"nombre\",\"cantidad\",\"unidad\",\"seccion\",\"proyecto\",\"notas\",\"enlace\"}]} ")
            append("(conserva el \"id\" de los que modifiques; secciones: ").append(Secciones.CLAVES.joinToString(", ")).append(").\n\n")
            append("```json\n").append(Json.escribir(compacto)).append("\n```")
        }
    }

    /** CSV para Excel / Google Sheets (BOM UTF-8, separador coma, sin fórmulas). */
    fun csv(d: Datos): String {
        val filas = mutableListOf(listOf("Producto", "Cantidad", "Unidad", "Sección", "Proyecto", "Estado", "Veces comprado", "Última compra", "Último precio pagado", "Mejor precio", "Tienda", "Fecha del precio", "Enlace", "Notas"))
        d.productos.sortedWith(compareBy({ it.comprado }, { it.seccion }, { Texto.simple(it.nombre) })).forEach { p ->
            val mp = p.mejorPrecio
            filas += listOf(
                p.nombre, Numeros.texto(p.cantidad), p.unidad, Secciones.nombre(p.seccion), p.proyecto, p.estado.clave,
                p.historial.size.toString(), p.historial.lastOrNull()?.fecha.orEmpty(),
                p.ultimoPagado?.precio?.let { Numeros.texto(it) }.orEmpty(),
                mp?.precio?.let { Numeros.texto(it) }.orEmpty(), mp?.tienda.orEmpty(), mp?.fecha ?: mp?.consultado.orEmpty(),
                p.enlace.orEmpty(), p.notas,
            )
        }
        return "﻿" + filas.joinToString("\r\n") { f -> f.joinToString(",") { celda(it) } }
    }

    private fun celda(v: String): String {
        val seguro = if (v.isNotEmpty() && v[0] in "=+-@\t\r") "'$v" else v
        return if (seguro.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + seguro.replace("\"", "\"\"") + "\"" else seguro
    }

    fun nombreArchivo(base: String, hoy: String, ext: String) = "emperatriz-$base-$hoy.$ext"
}
