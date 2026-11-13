package com.davidsant7e.emperatriz.nucleo

/** Números escritos como los escribe la gente: «2», «2,5», «1/2», «1 1/2», «dos», «media»; y precios «$ 12.900». */
object Numeros {
    private val PALABRAS = mapOf(
        "un" to 1.0, "uno" to 1.0, "una" to 1.0, "dos" to 2.0, "tres" to 3.0, "cuatro" to 4.0, "cinco" to 5.0,
        "seis" to 6.0, "siete" to 7.0, "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0, "once" to 11.0, "doce" to 12.0,
        "quince" to 15.0, "veinte" to 20.0, "treinta" to 30.0, "cincuenta" to 50.0, "cien" to 100.0,
        "medio" to 0.5, "media" to 0.5, "par" to 2.0,
    )
    private val mixto = Regex("""^(\d+)\s+(\d+)/(\d+)$""")
    private val fraccion = Regex("""^(\d+)/(\d+)$""")
    private val decimal = Regex("""^\d+([.,]\d+)?$""")

    fun cantidad(t: String?): Double? {
        val s = Texto.simple(t.orEmpty()).removePrefix("x").trim()
        if (s.isEmpty()) return null
        PALABRAS[s]?.let { return it }
        if (s == "un par") return 2.0
        mixto.find(s)?.let { m -> return m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / m.groupValues[3].toDouble().coerceAtLeast(1.0) }
        fraccion.find(s)?.let { m -> return m.groupValues[1].toDouble() / m.groupValues[2].toDouble().coerceAtLeast(1.0) }
        if (decimal.matches(s)) return s.replace(',', '.').toDoubleOrNull()
        return null
    }

    /** «$ 12.900», «12,900», «COP 4.500,50», «4.99» → número; null si no hay dígitos. */
    fun precio(t: String?): Double? {
        val s = t.orEmpty().replace(Regex("[^0-9.,]"), "")
        if (s.none { it.isDigit() }) return null
        val punto = s.lastIndexOf('.')
        val coma = s.lastIndexOf(',')
        val limpio = when {
            punto >= 0 && coma >= 0 -> {
                val dec = if (punto > coma) '.' else ','
                val mil = if (dec == '.') ',' else '.'
                s.replace(mil.toString(), "").replace(dec, '.')
            }
            punto >= 0 || coma >= 0 -> {
                val sep = if (punto >= 0) '.' else ','
                val partes = s.split(sep)
                // Varias veces, o una sola con 3 cifras detrás: separador de miles (12.900 = doce mil novecientos).
                if (partes.size > 2 || partes.last().length == 3) s.replace(sep.toString(), "") else s.replace(sep, '.')
            }
            else -> s
        }
        return limpio.toDoubleOrNull()
    }

    /** 2.0 → «2», 2.5 → «2,5», 0.25 → «0,25». */
    fun texto(n: Double): String =
        if (n == Math.rint(n)) n.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, n).trimEnd('0').trimEnd('.').replace('.', ',')

    /** 12900.0 → «$12.900» (pesos sin decimales); con otra moneda, «12,50 USD». */
    fun dinero(n: Double, moneda: String = "COP"): String {
        val m = moneda.uppercase()
        return if (m == "COP" || m == "CLP") {
            "$" + "%,d".format(java.util.Locale.ROOT, Math.round(n)).replace(',', '.')
        } else {
            "%,.2f".format(java.util.Locale.ROOT, n).replace(",", "_").replace('.', ',').replace("_", ".") + " " + m
        }
    }
}

object Unidades {
    /** Variante → forma corta que se guarda. */
    private val MAPA: Map<String, String> = buildMap {
        fun pon(forma: String, vararg variantes: String) { (variantes.toList() + forma).forEach { put(Texto.simple(it), forma) } }
        pon("kg", "kilo", "kilos", "kilogramo", "kilogramos", "kgs")
        pon("g", "gr", "grs", "gramo", "gramos")
        pon("lb", "libra", "libras", "lbs")
        pon("l", "lt", "lts", "litro", "litros")
        pon("ml", "mililitro", "mililitros", "cc")
        pon("und", "unidad", "unidades", "u", "un.", "unds")
        pon("paquete", "paquetes", "paq", "pqt")
        pon("caja", "cajas")
        pon("bolsa", "bolsas")
        pon("docena", "docenas")
        pon("m", "metro", "metros", "mts", "mt")
        pon("cm", "centimetro", "centimetros")
        pon("mm", "milimetro", "milimetros")
        pon("galón", "galon", "galones", "gal")
        pon("cuarto", "cuartos")
        pon("rollo", "rollos")
        pon("tarro", "tarros")
        pon("botella", "botellas")
        pon("lata", "latas")
        pon("bulto", "bultos")
        pon("frasco", "frascos")
        pon("sobre", "sobres")
        pon("atado", "atados")
        pon("pliego", "pliegos")
        pon("lámina", "lamina", "laminas", "láminas")
        pon("tubo", "tubos")
        pon("par", "pares")
        pon("juego", "juegos")
        pon("caneca", "canecas")
        pon("bandeja", "bandejas")
        pon("racimo", "racimos")
        pon("manojo", "manojos")
    }
    private val PLURAL = mapOf(
        "galón" to "galones", "par" to "pares", "lámina" to "láminas",
    )
    private val ABREVIADAS = setOf("kg", "g", "lb", "l", "ml", "und", "m", "cm", "mm")

    fun es(palabra: String) = Texto.simple(palabra) in MAPA

    fun normalizar(t: String?): String {
        val s = Texto.simple(t.orEmpty())
        if (s.isEmpty()) return ""
        return MAPA[s] ?: s
    }

    /** «2 kg», «3 cajas», «1 galón», «4» (sin unidad). */
    fun texto(cantidad: Double, unidad: String): String {
        val n = Numeros.texto(cantidad)
        if (unidad.isBlank()) return n
        val u = if (cantidad > 1 && unidad !in ABREVIADAS) PLURAL[unidad] ?: (unidad + "s") else unidad
        return "$n $u"
    }
}

/**
 * Lo que llega para agregar a la lista: texto dictado o escrito («2 kg de arroz, leche y 3 tornillos para el baño»),
 * el JSON que arma Claude o un respaldo completo de la app.
 */
object Pedido {
    data class Resultado(
        val productos: List<Producto>,
        val proyectos: List<Proyecto> = emptyList(),
        /** true = es un respaldo completo de la app (se puede reemplazar todo). */
        val respaldo: Boolean = false,
    )

    /** Separa ítems por saltos de línea, «;», comas (no las decimales: «1,5 kg») y « y ». */
    private val separador = Regex("""\n|;|,(?!\d)|\s+y\s+""", RegexOption.IGNORE_CASE)
    private val viñeta = Regex("""^\s*(?:[-*•·]|\d+[.)]\s|\[\s?[xX ]?\s?]|☐|✓|✔)\s*""")
    private val proyectoFinal = Regex("""\s*(?:\(\s*proyecto\s*:?\s*([^)]+)\)|\bpara (?:el |la )?proyecto\s+(.+)$|#(\S+)$)""", RegexOption.IGNORE_CASE)
    private val porFinal = Regex("""\s*(?:x|por)\s*(\d+(?:[.,]\d+)?)\s*$""", RegexOption.IGNORE_CASE)

    fun leer(texto: String, hoy: String, proyecto: String = "", seccion: String? = null): Resultado {
        val t = texto.trim()
        if (t.isEmpty()) return Resultado(emptyList())
        extraerJson(t)?.let { json ->
            runCatching { deJson(json, hoy, proyecto, seccion) }.getOrNull()?.let { return it }
        }
        val productos = t.split(separador).mapNotNull { linea(it, hoy) }
            .map { p -> p.copy(proyecto = p.proyecto.ifBlank { proyecto }, seccion = seccion ?: p.seccion) }
        return Resultado(productos)
    }

    /** El JSON aunque venga con texto alrededor o dentro de ```json … ```. */
    fun extraerJson(t: String): String? {
        val sinCerca = (if ("```" in t) t.substringAfter("```").removePrefix("json").substringBefore("```") else t).trim()
        val inicio = sinCerca.indexOfFirst { it == '{' || it == '[' }
        if (inicio < 0) return null
        val cierre = if (sinCerca[inicio] == '{') '}' else ']'
        val fin = sinCerca.lastIndexOf(cierre)
        if (fin <= inicio) return null
        return sinCerca.substring(inicio, fin + 1)
    }

    private fun deJson(json: String, hoy: String, proyecto: String, seccion: String?): Resultado {
        val raiz = Json.parse(json)
        val proyPorDefecto = raiz.texto("proyecto")?.trim().orEmpty().ifBlank { proyecto }
        val seccPorDefecto = raiz.texto("seccion")
        val lista: List<Any?> = when (raiz) {
            is List<*> -> raiz
            else -> raiz.lista("productos") ?: raiz.lista("items") ?: raiz.lista("lista") ?: emptyList()
        }
        val productos = lista.mapNotNull { m ->
            val p = Producto.deMapa(m, hoy) ?: return@mapNotNull null
            val secc = if (m is Map<*, *> && m["seccion"] == null && m["categoria"] == null) {
                if (seccPorDefecto != null) Secciones.normalizar(seccPorDefecto) else seccion ?: adivinar(p.nombre)
            } else p.seccion
            p.copy(proyecto = p.proyecto.ifBlank { proyPorDefecto }, seccion = secc)
        }
        val proyectos = raiz.lista("proyectos").orEmpty().mapNotNull { Proyecto.deMapa(it) } +
            listOfNotNull(proyPorDefecto.takeIf { it.isNotBlank() }?.let { Proyecto(it) })
        require(productos.isNotEmpty() || proyectos.isNotEmpty())
        return Resultado(productos, proyectos.distinctBy { Texto.simple(it.nombre) }, respaldo = raiz.texto("app") == Datos.APP)
    }

    /** «2 kg de arroz», «arroz 2 kg», «leche x3», «media docena de huevos», «tornillos 1/4 (proyecto: Baño)». */
    fun linea(original: String, hoy: String): Producto? {
        var s = original.replace(viñeta, "").trim().trimEnd('.', ',')
        if (s.isEmpty()) return null
        var proyecto = ""
        proyectoFinal.find(s)?.let { m ->
            proyecto = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim()?.trimEnd(')').orEmpty()
            s = s.removeRange(m.range).trim()
        }
        var cantidad: Double? = null
        var unidad = ""
        porFinal.find(s)?.let { m ->
            cantidad = Numeros.cantidad(m.groupValues[1]); s = s.removeRange(m.range).trim()
        }
        val palabras = s.split(Regex("\\s+")).toMutableList()
        if (cantidad == null) {
            // Cantidad al principio: «2», «1/2», «1 1/2», «dos», «media», «un par de».
            val dos = palabras.take(2).joinToString(" ")
            val n2 = if (palabras.size > 2) Numeros.cantidad(dos)?.takeIf { dos.contains('/') || Texto.simple(dos) == "un par" } else null
            if (n2 != null) {
                cantidad = n2; repeat(2) { palabras.removeAt(0) }
            } else if (palabras.size > 1) {
                Numeros.cantidad(palabras[0])?.let { cantidad = it; palabras.removeAt(0) }
            }
            // «1/4 de galón de pintura»: el «de» antes de la unidad sobra.
            if (cantidad != null && palabras.size > 2 && Texto.simple(palabras[0]) == "de" && Unidades.es(palabras[1])) palabras.removeAt(0)
            if (cantidad != null && palabras.size > 1 && Unidades.es(palabras[0])) {
                unidad = Unidades.normalizar(palabras.removeAt(0))
            }
            if (cantidad != null && palabras.size > 1 && Texto.simple(palabras[0]) == "de") palabras.removeAt(0)
        }
        if (cantidad == null && palabras.size > 2) {
            // Cantidad al final con unidad: «arroz 2 kg».
            val u = palabras.last()
            val n = Numeros.cantidad(palabras[palabras.size - 2])
            if (Unidades.es(u) && n != null) {
                cantidad = n; unidad = Unidades.normalizar(u); repeat(2) { palabras.removeAt(palabras.size - 1) }
            }
        }
        if (unidad == "docena" && cantidad != null && palabras.isNotEmpty()) {
            // «media docena de huevos» = 6 huevos.
            cantidad = cantidad!! * 12; unidad = "und"
        }
        val nombre = palabras.joinToString(" ").trim().trim('-', ':').trim()
        if (nombre.isEmpty() || nombre.none { it.isLetter() }) return null
        return Producto(
            nombre = nombre.replaceFirstChar { it.uppercase() },
            cantidad = cantidad?.takeIf { it > 0 } ?: 1.0,
            unidad = unidad,
            seccion = adivinar(nombre),
            proyecto = proyecto,
            creado = hoy,
        )
    }

    private val PISTAS = listOf(
        "tecnologia" to listOf("cable usb", "cargador", "pila", "bateria", "audifono", "memoria usb", "mouse", "teclado", "bombillo inteligente"),
        "ferreteria" to listOf(
            "tornillo", "puntilla", "clavo", "chazo", "pintura", "brocha", "rodillo", "cemento", "pegante", "tubo", "pvc",
            "cable", "bombillo", "cinta", "silicona", "lija", "taladro", "broca", "llave", "masilla", "estuco", "codo",
            "teflon", "tuerca", "arandela", "bisagra", "cerradura", "candado", "manguera", "thinner", "varsol", "drywall",
            "tomacorriente", "interruptor", "breaker", "toma ", "boquilla", "grifo", "sifon", "abrazadera", "martillo",
            "destornillador", "alicate", "metro de", "nivel", "escuadra", "guantes de trabajo", "impermeabilizante", "anclaje",
        ),
        "aseo" to listOf(
            "jabon", "detergente", "cloro", "limpiador", "papel higienico", "esponja", "suavizante", "desinfectante",
            "lavaloza", "trapero", "escoba", "bolsas de basura", "toallas de cocina", "servilleta", "shampoo", "champu",
            "crema dental", "cepillo de dientes", "desodorante", "ambientador", "limpido", "axion", "fabuloso",
        ),
        "farmacia" to listOf(
            "acetaminofen", "ibuprofeno", "curita", "alcohol antiseptico", "gasa", "vitamina", "suero", "loratadina",
            "omeprazol", "antiacido", "termometro", "tapabocas", "preservativo", "bloqueador solar",
        ),
        "hogar" to listOf("cortina", "sabana", "almohada", "toalla de bano", "vaso", "plato", "olla", "sarten", "organizador", "tapete", "cojin"),
    )

    /** Sección según el nombre; si no hay pista, mercado. */
    fun adivinar(nombre: String): String {
        val n = " " + Texto.simple(nombre) + " "
        return PISTAS.firstOrNull { (_, ps) -> ps.any { n.contains(" $it") } }?.first ?: "mercado"
    }
}
