package com.davidsant7e.emperatriz.nucleo

import java.text.Normalizer
import java.util.UUID

fun nuevoId(): String = UUID.randomUUID().toString().replace("-", "").take(10)

/** Dónde se compra: separa el mercado de la ferretería y lo demás. */
object Secciones {
    val NOMBRES = linkedMapOf(
        "mercado" to "Mercado",
        "aseo" to "Aseo",
        "farmacia" to "Farmacia",
        "ferreteria" to "Ferretería",
        "hogar" to "Hogar",
        "tecnologia" to "Tecnología",
        "otro" to "Otro",
    )
    val CLAVES = NOMBRES.keys.toList()

    fun nombre(clave: String) = NOMBRES[clave] ?: NOMBRES.getValue("otro")

    /** Acepta la clave, el nombre o variantes («Ferretería», "ferreteria", "FERRE…"). */
    fun normalizar(texto: String?): String {
        val t = Texto.simple(texto.orEmpty())
        if (t.isEmpty()) return "mercado"
        CLAVES.firstOrNull { it == t }?.let { return it }
        NOMBRES.entries.firstOrNull { Texto.simple(it.value) == t }?.let { return it.key }
        return when {
            t.startsWith("ferre") || "herramient" in t || "construcc" in t -> "ferreteria"
            t.startsWith("merca") || "super" in t || "comida" in t || "aliment" in t || "viveres" in t -> "mercado"
            "aseo" in t || "limpi" in t -> "aseo"
            "farma" in t || "drogu" in t || "medic" in t -> "farmacia"
            "tecno" in t || "electron" in t -> "tecnologia"
            "hogar" in t || "casa" in t -> "hogar"
            else -> "otro"
        }
    }
}

enum class EstadoProducto(val clave: String) {
    PENDIENTE("pendiente"), COMPRADO("comprado");

    companion object {
        fun de(t: String?) = entries.firstOrNull { it.clave == Texto.simple(t.orEmpty()) } ?: PENDIENTE
    }
}

/** Un precio encontrado (en una tienda, en una fecha). */
data class Precio(
    val tienda: String,
    val precio: Double?,
    val moneda: String = "COP",
    /** Fecha que muestra la página (publicación/actualización), si la hay: AAAA-MM-DD. */
    val fecha: String? = null,
    /** Día en que se consultó: AAAA-MM-DD. */
    val consultado: String,
    val enlace: String? = null,
    val presentacion: String? = null,
    val nota: String? = null,
) {
    fun aMapa(): Map<String, Any?> = linkedMapOf(
        "tienda" to tienda, "precio" to precio, "moneda" to moneda, "fecha" to fecha, "consultado" to consultado,
        "enlace" to enlace, "presentacion" to presentacion, "nota" to nota,
    ).filterValues { it != null }

    companion object {
        fun deMapa(m: Any?, hoy: String): Precio? {
            val tienda = m.texto("tienda")?.trim().orEmpty()
            if (tienda.isEmpty()) return null
            return Precio(
                tienda = tienda,
                precio = m.num("precio")?.takeIf { it > 0 } ?: Numeros.precio(m.texto("precio")),
                moneda = m.texto("moneda")?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "COP",
                fecha = Fechas.normalizar(m.texto("fecha")),
                consultado = Fechas.normalizar(m.texto("consultado")) ?: hoy,
                enlace = m.texto("enlace")?.trim()?.takeIf { it.startsWith("http") },
                presentacion = m.texto("presentacion")?.trim()?.takeIf { it.isNotEmpty() },
                nota = m.texto("nota")?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

/** Una vez que se compró: cuánto, cuándo, dónde y a cómo. */
data class Compra(val fecha: String, val cantidad: Double, val precio: Double? = null, val tienda: String? = null) {
    fun aMapa(): Map<String, Any?> = linkedMapOf("fecha" to fecha, "cantidad" to cantidad, "precio" to precio, "tienda" to tienda)
        .filterValues { it != null }

    companion object {
        fun deMapa(m: Any?): Compra? {
            val fecha = Fechas.normalizar(m.texto("fecha")) ?: return null
            return Compra(fecha, m.num("cantidad") ?: 1.0, m.num("precio"), m.texto("tienda")?.takeIf { it.isNotBlank() })
        }
    }
}

data class Producto(
    val id: String = nuevoId(),
    val nombre: String,
    val cantidad: Double = 1.0,
    val unidad: String = "",
    val seccion: String = "mercado",
    /** Proyecto al que pertenece (por ejemplo «Baño» o «El Emperador · Cocina»); vacío = compra general. */
    val proyecto: String = "",
    val notas: String = "",
    /** Página del producto (tienda, ficha), si la hay. */
    val enlace: String? = null,
    val estado: EstadoProducto = EstadoProducto.PENDIENTE,
    val creado: String,
    val historial: List<Compra> = emptyList(),
    val precios: List<Precio> = emptyList(),
) {
    val comprado get() = estado == EstadoProducto.COMPRADO

    /** Clave para reconocer el mismo producto: nombre + unidad + sección + proyecto, sin tildes ni mayúsculas. */
    val clave get() = listOf(Texto.simple(nombre), Unidades.normalizar(unidad), seccion, Texto.simple(proyecto)).joinToString("|")

    /** El precio más bajo conocido (de la búsqueda) o el último pagado. */
    val mejorPrecio: Precio? get() = precios.filter { it.precio != null }.minByOrNull { it.precio!! }

    val ultimoPagado: Compra? get() = historial.lastOrNull { it.precio != null }

    fun aMapa(): Map<String, Any?> = linkedMapOf(
        "id" to id, "nombre" to nombre, "cantidad" to cantidad, "unidad" to unidad, "seccion" to seccion,
        "proyecto" to proyecto, "notas" to notas, "enlace" to enlace, "estado" to estado.clave, "creado" to creado,
        "historial" to historial.map { it.aMapa() }, "precios" to precios.map { it.aMapa() },
    ).filterValues { it != null }

    companion object {
        /** Lee un producto: completo (respaldo) o mínimo (lo que manda Claude: solo «nombre»). */
        fun deMapa(m: Any?, hoy: String): Producto? {
            if (m is String) return Texto.leerLinea(m, hoy)
            val nombre = (m.texto("nombre") ?: m.texto("producto") ?: m.texto("name"))?.trim().orEmpty()
            if (nombre.isEmpty()) return null
            return Producto(
                id = m.texto("id")?.takeIf { it.isNotBlank() } ?: nuevoId(),
                nombre = nombre.replaceFirstChar { it.uppercase() },
                cantidad = (m.num("cantidad") ?: Numeros.cantidad(m.texto("cantidad")) ?: 1.0).let { if (it > 0) it else 1.0 },
                unidad = Unidades.normalizar(m.texto("unidad")),
                seccion = Secciones.normalizar(m.texto("seccion") ?: m.texto("categoria")),
                proyecto = m.texto("proyecto")?.trim().orEmpty(),
                notas = (m.texto("notas") ?: m.texto("descripcion"))?.trim().orEmpty(),
                enlace = m.texto("enlace")?.trim()?.takeIf { it.startsWith("http") },
                estado = EstadoProducto.de(m.texto("estado")),
                creado = Fechas.normalizar(m.texto("creado")) ?: hoy,
                historial = m.lista("historial").orEmpty().mapNotNull { Compra.deMapa(it) },
                precios = m.lista("precios").orEmpty().mapNotNull { Precio.deMapa(it, hoy) },
            )
        }
    }
}

data class Proyecto(val nombre: String, val notas: String = "", val origen: String = "", val archivado: Boolean = false) {
    fun aMapa(): Map<String, Any?> = linkedMapOf("nombre" to nombre, "notas" to notas, "origen" to origen, "archivado" to archivado)

    companion object {
        fun deMapa(m: Any?): Proyecto? {
            if (m is String) return m.trim().takeIf { it.isNotEmpty() }?.let { Proyecto(it) }
            val n = m.texto("nombre")?.trim().orEmpty()
            if (n.isEmpty()) return null
            return Proyecto(n, m.texto("notas").orEmpty(), m.texto("origen").orEmpty(), (m as? Map<*, *>)?.get("archivado") == true)
        }
    }
}

/** Todo lo de la app. Es también el formato del respaldo y de lo que se intercambia con Claude. */
data class Datos(
    val productos: List<Producto> = emptyList(),
    val proyectos: List<Proyecto> = emptyList(),
    val actualizado: String = "",
) {
    fun aJson(): String = Json.escribir(
        linkedMapOf(
            "app" to APP, "version" to VERSION, "actualizado" to actualizado,
            "proyectos" to proyectos.map { it.aMapa() },
            "productos" to productos.map { it.aMapa() },
        ),
    )

    /** Nombres de proyectos: los registrados y los que aparecen en productos. */
    val nombresProyectos: List<String>
        get() = (proyectos.filter { !it.archivado }.map { it.nombre } + productos.map { it.proyecto })
            .filter { it.isNotBlank() }.distinctBy { Texto.simple(it) }

    companion object {
        const val APP = "la-emperatriz"
        const val VERSION = 1

        fun deJson(texto: String, hoy: String): Datos {
            val raiz = Json.parse(texto)
            return Datos(
                productos = raiz.lista("productos").orEmpty().mapNotNull { Producto.deMapa(it, hoy) },
                proyectos = raiz.lista("proyectos").orEmpty().mapNotNull { Proyecto.deMapa(it) },
                actualizado = raiz.texto("actualizado").orEmpty(),
            )
        }
    }
}

object Texto {
    /** Sin tildes, minúsculas y espacios simples: para comparar nombres. */
    fun simple(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

    /** Una línea suelta («2 kg de arroz») como producto; null si queda vacía. */
    fun leerLinea(linea: String, hoy: String): Producto? = Pedido.linea(linea, hoy)
}

object Fechas {
    private val iso = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val dmy = Regex("""(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})""")

    /** AAAA-MM-DD a partir de «2026-11-3», «3/11/2026» o un ISO con hora; null si no se reconoce. */
    fun normalizar(t: String?): String? {
        val s = t?.trim().orEmpty()
        iso.find(s)?.let { m -> return arma(m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
        dmy.find(s)?.let { m -> return arma(m.groupValues[3], m.groupValues[2], m.groupValues[1]) }
        return null
    }

    private fun arma(a: String, m: String, d: String): String? {
        val mes = m.toInt(); val dia = d.toInt()
        if (mes !in 1..12 || dia !in 1..31) return null
        return "%s-%02d-%02d".format(a, mes, dia)
    }
}
