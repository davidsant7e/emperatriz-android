package com.davidsant7e.emperatriz.nucleo

/** Reglas de la lista: agregar sin duplicar, comprar, volver a comprar, importar y exportar. Todo puro. */
object Lista {

    /**
     * Agrega productos. Si ya hay uno igual (mismo nombre, unidad, sección y proyecto):
     * - pendiente → se suma la cantidad;
     * - comprado → vuelve a pendiente con la cantidad nueva (conserva su historial y precios).
     */
    fun agregar(d: Datos, nuevos: List<Producto>, hoy: String): Datos {
        var productos = d.productos
        for (n in nuevos) {
            val i = productos.indexOfFirst { it.clave == n.clave }
            productos = if (i < 0) {
                productos + n.copy(estado = EstadoProducto.PENDIENTE)
            } else {
                val e = productos[i]
                val unido = if (e.comprado) {
                    e.copy(estado = EstadoProducto.PENDIENTE, cantidad = n.cantidad)
                } else {
                    e.copy(cantidad = e.cantidad + n.cantidad)
                }.copy(
                    notas = e.notas.ifBlank { n.notas },
                    enlace = e.enlace ?: n.enlace,
                    precios = (e.precios + n.precios).distinctBy { listOf(it.tienda, it.precio, it.consultado) },
                )
                productos.toMutableList().also { it[i] = unido }
            }
        }
        return conProyectos(d.copy(productos = productos, actualizado = hoy), nuevos.map { it.proyecto })
    }

    private fun conProyectos(d: Datos, nombres: List<String>): Datos {
        val existentes = d.proyectos.map { Texto.simple(it.nombre) }.toSet()
        val faltan = nombres.filter { it.isNotBlank() && Texto.simple(it) !in existentes }.distinctBy { Texto.simple(it) }
        return if (faltan.isEmpty()) d else d.copy(proyectos = d.proyectos + faltan.map { Proyecto(it) })
    }

    private fun cambia(d: Datos, id: String, hoy: String, f: (Producto) -> Producto) =
        d.copy(productos = d.productos.map { if (it.id == id) f(it) else it }, actualizado = hoy)

    /** Marca como comprado y lo anota en el historial (con precio y tienda si se dan). */
    fun comprar(d: Datos, id: String, hoy: String, precio: Double? = null, tienda: String? = null) = cambia(d, id, hoy) {
        if (it.comprado) it
        else it.copy(estado = EstadoProducto.COMPRADO, historial = it.historial + Compra(hoy, it.cantidad, precio, tienda?.takeIf { t -> t.isNotBlank() }))
    }

    /** Deshace «comprado» (quita la última compra si fue hoy). */
    fun desmarcar(d: Datos, id: String, hoy: String) = cambia(d, id, hoy) {
        if (!it.comprado) it
        else it.copy(estado = EstadoProducto.PENDIENTE, historial = if (it.historial.lastOrNull()?.fecha == hoy) it.historial.dropLast(1) else it.historial)
    }

    /**
     * «Volver a comprar»: si ya está pendiente, aumenta la cantidad; si estaba comprado, vuelve a la lista
     * con la cantidad indicada (por defecto, la de la última compra).
     */
    fun volverAComprar(d: Datos, id: String, hoy: String, cantidad: Double? = null) = cambia(d, id, hoy) {
        if (it.comprado) it.copy(estado = EstadoProducto.PENDIENTE, cantidad = cantidad ?: it.historial.lastOrNull()?.cantidad ?: it.cantidad)
        else it.copy(cantidad = it.cantidad + (cantidad ?: paso(it.unidad)))
    }

    /** Suma o resta de a un paso (1 unidad; 0,5 en kg, lb y l; 100 en g y ml). Nunca baja de un paso. */
    fun ajustar(d: Datos, id: String, hoy: String, signo: Int) = cambia(d, id, hoy) {
        val p = paso(it.unidad)
        it.copy(cantidad = (it.cantidad + signo * p).coerceAtLeast(p))
    }

    fun paso(unidad: String) = when (unidad) {
        "kg", "lb", "l" -> 0.5
        "g", "ml" -> 100.0
        else -> 1.0
    }

    fun editar(d: Datos, p: Producto, hoy: String): Datos {
        val existe = d.productos.any { it.id == p.id }
        val nuevos = if (existe) d.productos.map { if (it.id == p.id) p else it } else d.productos + p
        return conProyectos(d.copy(productos = nuevos, actualizado = hoy), listOf(p.proyecto))
    }

    fun quitar(d: Datos, id: String, hoy: String) = d.copy(productos = d.productos.filter { it.id != id }, actualizado = hoy)

    /** Borra los comprados con su historial (ya no se ofrecerán para «volver a comprar»). */
    fun limpiarComprados(d: Datos, hoy: String) = d.copy(productos = d.productos.filter { !it.comprado }, actualizado = hoy)

    fun guardarPrecios(d: Datos, id: String, precios: List<Precio>, hoy: String, enlace: String? = null) = cambia(d, id, hoy) { p ->
        // Los nuevos reemplazan a los de la misma tienda; se guardan hasta 12.
        val tiendas = precios.map { Texto.simple(it.tienda) }.toSet()
        p.copy(precios = (precios + p.precios.filter { Texto.simple(it.tienda) !in tiendas }).take(12), enlace = p.enlace ?: enlace)
    }

    // ---- Proyectos ----

    fun agregarProyecto(d: Datos, nombre: String, notas: String = "", origen: String = "", hoy: String): Datos {
        val n = nombre.trim()
        if (n.isEmpty() || d.proyectos.any { Texto.simple(it.nombre) == Texto.simple(n) }) return d
        return d.copy(proyectos = d.proyectos + Proyecto(n, notas, origen), actualizado = hoy)
    }

    fun archivarProyecto(d: Datos, nombre: String, archivado: Boolean, hoy: String) =
        d.copy(proyectos = d.proyectos.map { if (it.nombre == nombre) it.copy(archivado = archivado) else it }, actualizado = hoy)

    /** Costo de un proyecto: lo pagado (historial) y lo que falta según el mejor precio conocido. */
    data class Costo(val pagado: Double, val estimadoPendiente: Double, val sinPrecio: Int)

    fun costo(d: Datos, proyecto: String): Costo {
        val ps = d.productos.filter { Texto.simple(it.proyecto) == Texto.simple(proyecto) }
        val pagado = ps.sumOf { p -> p.historial.sumOf { it.precio ?: 0.0 } }
        val pendientes = ps.filter { !it.comprado }
        val estimado = pendientes.sumOf { p -> (p.mejorPrecio?.precio ?: 0.0) * p.cantidad }
        return Costo(pagado, estimado, pendientes.count { it.mejorPrecio == null })
    }

    // ---- Vistas ----

    /** Pendientes por sección, en el orden de Secciones y por nombre. */
    fun pendientesPorSeccion(d: Datos, seccion: String? = null, proyecto: String? = null): List<Pair<String, List<Producto>>> =
        filtrar(d.productos.filter { !it.comprado }, seccion, proyecto)
            .groupBy { it.seccion }
            .toSortedMap(compareBy { Secciones.CLAVES.indexOf(it).let { i -> if (i < 0) 99 else i } })
            .map { (k, v) -> k to v.sortedBy { Texto.simple(it.nombre) } }

    fun comprados(d: Datos, seccion: String? = null, proyecto: String? = null): List<Producto> =
        filtrar(d.productos.filter { it.comprado }, seccion, proyecto).sortedByDescending { it.historial.lastOrNull()?.fecha ?: "" }

    /** Los que más se compran (para agregarlos con un toque). */
    fun frecuentes(d: Datos, max: Int = 12): List<Producto> =
        d.productos.filter { it.comprado && it.historial.size >= 2 }.sortedByDescending { it.historial.size }.take(max)

    private fun filtrar(ps: List<Producto>, seccion: String?, proyecto: String?) = ps.filter {
        (seccion == null || it.seccion == seccion) &&
            (proyecto == null || Texto.simple(it.proyecto) == Texto.simple(proyecto))
    }

    // ---- Importar ----

    data class Importe(val datos: Datos, val agregados: Int, val actualizados: Int)

    /**
     * Mezcla lo que llega (de Claude, del PC o de otro celular):
     * - mismo id → se toma lo que llega (es el mismo producto editado en otro lado);
     * - mismo producto (nombre/unidad/sección/proyecto) → como «agregar» (suma o vuelve a pendiente);
     * - si no, se agrega.
     * Con reemplazar = true (respaldo completo) se cambia todo.
     */
    fun importar(d: Datos, r: Pedido.Resultado, hoy: String, reemplazar: Boolean = false): Importe {
        if (reemplazar) return Importe(Datos(r.productos, r.proyectos, hoy), r.productos.size, 0)
        var datos = d
        var agregados = 0
        var actualizados = 0
        val porId = r.productos.filter { n -> d.productos.any { it.id == n.id } }
        porId.forEach { n -> datos = datos.copy(productos = datos.productos.map { if (it.id == n.id) n else it }); actualizados++ }
        r.productos.filter { it !in porId }.forEach { n ->
            if (datos.productos.any { it.clave == n.clave }) actualizados++ else agregados++
            datos = agregar(datos, listOf(n), hoy)
        }
        r.proyectos.forEach { p -> if (datos.proyectos.none { Texto.simple(it.nombre) == Texto.simple(p.nombre) }) datos = datos.copy(proyectos = datos.proyectos + p) }
        return Importe(conProyectos(datos.copy(actualizado = hoy), r.productos.map { it.proyecto }), agregados, actualizados)
    }
}
