package com.davidsant7e.emperatriz.nucleo

import java.io.IOException
import java.net.URLEncoder

/**
 * Las consultas a Gemini: precios con búsqueda de Google (y la página del producto si hay enlace),
 * «cómo hacerlo en casa» y ordenar un texto libre como lista.
 *
 * El transporte se inyecta para probar sin red: (url, cuerpo, cabeceras) → (código HTTP, texto).
 */
class Gemini(
    private val transporte: (String, String, Map<String, String>) -> Pair<Int, String>,
    private val base: String = "https://generativelanguage.googleapis.com/v1beta/models/",
) {
    class SinClaveException : IOException("Falta la clave de Gemini (⚙ Ajustes)")

    data class Fuente(val titulo: String, val enlace: String)

    data class Busqueda(
        val producto: String,
        val descripcion: String?,
        val precios: List<Precio>,
        val resumen: String?,
        val fuentes: List<Fuente>,
    )

    data class Ingrediente(val nombre: String, val cantidad: Double, val unidad: String, val seccion: String)

    data class Receta(
        val titulo: String,
        val factible: Boolean,
        val dificultad: String?,
        val tiempo: String?,
        val costo: String?,
        val ingredientes: List<Ingrediente>,
        val herramientas: List<String>,
        val pasos: List<String>,
        val advertencias: List<String>,
        val comparacion: String?,
        val fuentes: List<Fuente>,
    )

    /** Dónde se compra (para que los precios sean del país y la ciudad correctos). */
    data class Lugar(val pais: String = "Colombia", val ciudad: String = "", val moneda: String = "COP") {
        val texto get() = listOf(ciudad, pais).filter { it.isNotBlank() }.joinToString(", ")
    }

    fun buscarPrecios(nombre: String, notas: String, enlace: String?, lugar: Lugar, hoy: String, clave: String?, modelo: String = MODELO): Busqueda {
        val pedido = buildString {
            append("Hoy es ").append(hoy).append(". Compro en ").append(lugar.texto).append(" (moneda ").append(lugar.moneda).append(").\n")
            append("Producto: ").append(nombre.ifBlank { "(el de la página)" }).append('\n')
            if (notas.isNotBlank()) append("Detalles: ").append(notas).append('\n')
            if (!enlace.isNullOrBlank()) append("Página del producto: ").append(enlace).append("\nLee esa página para saber exactamente qué producto es (marca, referencia, presentación) y su precio ahí.\n")
        }
        val (datos, fuentes) = consultar(INSTR_PRECIOS, pedido, ESQUEMA_PRECIOS, clave, modelo, conHerramientas = true, conPagina = !enlace.isNullOrBlank())
        val precios = datos.lista("precios").orEmpty().mapNotNull { Precio.deMapa(it, hoy) }
            .map { if (it.moneda.isBlank()) it.copy(moneda = lugar.moneda) else it }
            .sortedBy { it.precio ?: Double.MAX_VALUE }
        return Busqueda(
            producto = datos.texto("producto")?.trim()?.takeIf { it.isNotEmpty() } ?: nombre,
            descripcion = datos.texto("descripcion")?.trim()?.takeIf { it.isNotEmpty() },
            precios = precios,
            resumen = datos.texto("resumen")?.trim()?.takeIf { it.isNotEmpty() },
            fuentes = fuentes,
        )
    }

    fun casero(nombre: String, notas: String, lugar: Lugar, clave: String?, modelo: String = MODELO): Receta {
        val pedido = "Producto: $nombre\n" + (if (notas.isNotBlank()) "Detalles: $notas\n" else "") + "Vivo en ${lugar.texto} (moneda ${lugar.moneda})."
        val (d, fuentes) = consultar(INSTR_CASERO, pedido, ESQUEMA_CASERO, clave, modelo, conHerramientas = true, conPagina = false)
        return Receta(
            titulo = d.texto("titulo")?.trim()?.takeIf { it.isNotEmpty() } ?: "$nombre casero",
            factible = (d as? Map<*, *>)?.get("factible") != false,
            dificultad = d.texto("dificultad")?.takeIf { it.isNotBlank() },
            tiempo = d.texto("tiempo")?.takeIf { it.isNotBlank() },
            costo = d.texto("costo")?.takeIf { it.isNotBlank() },
            ingredientes = d.lista("ingredientes").orEmpty().mapNotNull { m ->
                val n = m.texto("nombre")?.trim().orEmpty()
                if (n.isEmpty()) null else Ingrediente(
                    n.replaceFirstChar { it.uppercase() }, m.num("cantidad")?.takeIf { it > 0 } ?: 1.0,
                    Unidades.normalizar(m.texto("unidad")), Secciones.normalizar(m.texto("seccion") ?: Pedido.adivinar(n)),
                )
            },
            herramientas = d.lista("herramientas").orEmpty().mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } },
            pasos = d.lista("pasos").orEmpty().mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } },
            advertencias = d.lista("advertencias").orEmpty().mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } },
            comparacion = d.texto("comparacion")?.takeIf { it.isNotBlank() },
            fuentes = fuentes,
        )
    }

    /** Un texto libre (dictado, lista copiada, mensaje) como productos con sección y proyecto. */
    fun ordenar(texto: String, proyectos: List<String>, hoy: String, clave: String?, modelo: String = MODELO): List<Producto> {
        val pedido = "Proyectos que ya existen: " + (proyectos.joinToString(", ").ifBlank { "(ninguno)" }) + "\n\nTexto:\n" + texto
        val (d, _) = consultar(INSTR_ORDENAR, pedido, ESQUEMA_ORDENAR, clave, modelo, conHerramientas = false, conPagina = false)
        return d.lista("productos").orEmpty().mapNotNull { Producto.deMapa(it, hoy) }
    }

    /** Hace la consulta. Si el modelo no acepta herramientas con esquema, repite sin esquema (el JSON se pide en el texto). */
    private fun consultar(
        instrucciones: String, pedido: String, esquema: Map<String, Any?>, clave: String?, modelo: String,
        conHerramientas: Boolean, conPagina: Boolean,
    ): Pair<Any?, List<Fuente>> {
        if (clave.isNullOrBlank()) throw SinClaveException()
        val url = base + URLEncoder.encode(modelo.trim().ifBlank { MODELO }, "UTF-8") + ":generateContent"
        val cab = mapOf("x-goog-api-key" to clave.trim())
        var (codigo, json) = transporte(url, cuerpo(instrucciones, pedido, esquema, conHerramientas, conPagina, conEsquema = true), cab)
        if (codigo == 400 && conHerramientas) {
            val r = transporte(url, cuerpo(instrucciones, pedido, esquema, conHerramientas, conPagina, conEsquema = false), cab)
            codigo = r.first; json = r.second
        }
        if (codigo !in 200..299) throw IOException(mensajeDeError(codigo, json))
        return leer(json)
    }

    companion object {
        const val MODELO = "gemini-3.5-flash"

        private val REGLA_JSON = "Responde SOLO con un objeto JSON (sin texto alrededor) con esta forma: "

        private val INSTR_PRECIOS = """
            Eres La Emperatriz, una asistente de compras. Busca en Google el precio ACTUAL del producto en tiendas
            del país y la ciudad indicados (supermercados, ferreterías, droguerías, tiendas en línea y marketplaces).
            - Devuelve hasta 8 precios distintos, cada uno con: "tienda", "precio" (número, sin símbolos ni puntos de miles,
              en la moneda indicada), "moneda", "presentacion" (tamaño o empaque: «500 g», «galón», «x 6»),
              "fecha" (AAAA-MM-DD de publicación/actualización que muestre la página o el resultado; vacío si no se ve),
              "enlace" (la página donde está el precio) y "nota" (oferta, agotado, precio por kilo, envío…).
            - Solo precios que veas en los resultados o en la página; nunca inventes ni estimes uno. Si no encuentras, lista vacía.
            - Si hay una página del producto, primero identifica el producto exacto ("producto": nombre con marca y presentación;
              "descripcion": 1–2 frases) y busca ese mismo producto o su equivalente más cercano en otras tiendas.
            - "resumen": una o dos frases: dónde está más barato, rango de precios, si hay diferencias de presentación.
            Escribe en español.
        """.trimIndent() + "\n" + REGLA_JSON +
            "{\"producto\":\"\",\"descripcion\":\"\",\"resumen\":\"\",\"precios\":[{\"tienda\":\"\",\"precio\":0,\"moneda\":\"\",\"presentacion\":\"\",\"fecha\":\"\",\"enlace\":\"\",\"nota\":\"\"}]}"

        private val INSTR_CASERO = """
            Eres La Emperatriz. La persona quiere saber cómo HACER EN CASA un producto que normalmente compra
            (comida, bebida, salsa, producto de aseo sencillo, arreglo o pieza de ferretería, etc.).
            - Si es razonable y seguro hacerlo en casa: "factible": true, con "ingredientes" (nombre, cantidad, unidad, seccion
              donde se compra: mercado, aseo, farmacia, ferreteria, hogar, tecnologia u otro), "herramientas", "pasos" claros y numerables,
              "tiempo", "dificultad" (fácil, media o difícil), "costo" aproximado en la moneda indicada (dilo como aproximado)
              y "comparacion" (una frase: ¿sale más barato o mejor que comprarlo?).
            - "advertencias": de seguridad e higiene que apliquen (conservación, alergias, ventilación, guantes).
            - Si NO es seguro o no es práctico (medicamentos, mezclas de químicos peligrosas, electricidad de alto riesgo,
              cosas que exigen equipo industrial), "factible": false, explica por qué en "advertencias" y no des pasos.
            - Nunca indiques mezclar cloro con amoníaco, vinagre u otros ácidos, ni procesos que generen gases tóxicos.
            Escribe en español, concreto y con cantidades para una tanda casera normal.
        """.trimIndent() + "\n" + REGLA_JSON +
            "{\"titulo\":\"\",\"factible\":true,\"dificultad\":\"\",\"tiempo\":\"\",\"costo\":\"\",\"comparacion\":\"\",\"ingredientes\":[{\"nombre\":\"\",\"cantidad\":1,\"unidad\":\"\",\"seccion\":\"\"}],\"herramientas\":[\"\"],\"pasos\":[\"\"],\"advertencias\":[\"\"]}"

        private val INSTR_ORDENAR = """
            Convierte el texto en una lista de compras. Cada producto con "nombre" (corto, en español, con mayúscula inicial),
            "cantidad" (número; 1 si no se dice), "unidad" (kg, g, lb, l, ml, und, paquete, caja, bolsa, m, galón… o vacío),
            "seccion" (mercado, aseo, farmacia, ferreteria, hogar, tecnologia u otro), "proyecto" (si el texto lo asocia a un
            proyecto o arreglo; usa el nombre exacto de un proyecto existente si coincide; si no, vacío) y "notas" (marca,
            tamaño, referencia o para qué es, si se dice). No inventes productos que no estén en el texto.
        """.trimIndent() + "\n" + REGLA_JSON +
            "{\"productos\":[{\"nombre\":\"\",\"cantidad\":1,\"unidad\":\"\",\"seccion\":\"\",\"proyecto\":\"\",\"notas\":\"\"}]}"

        private fun cadena() = mapOf("type" to "STRING")
        private fun numero() = mapOf("type" to "NUMBER")
        private fun lista(item: Map<String, Any?>) = mapOf("type" to "ARRAY", "items" to item)
        private fun objeto(props: Map<String, Any?>, requeridos: List<String>) = mapOf("type" to "OBJECT", "properties" to props, "required" to requeridos)

        val ESQUEMA_PRECIOS = objeto(
            mapOf(
                "producto" to cadena(), "descripcion" to cadena(), "resumen" to cadena(),
                "precios" to lista(
                    objeto(
                        mapOf(
                            "tienda" to cadena(), "precio" to numero(), "moneda" to cadena(), "presentacion" to cadena(),
                            "fecha" to cadena(), "enlace" to cadena(), "nota" to cadena(),
                        ),
                        listOf("tienda", "precio"),
                    ),
                ),
            ),
            listOf("precios"),
        )

        val ESQUEMA_CASERO = objeto(
            mapOf(
                "titulo" to cadena(), "factible" to mapOf("type" to "BOOLEAN"), "dificultad" to cadena(), "tiempo" to cadena(),
                "costo" to cadena(), "comparacion" to cadena(),
                "ingredientes" to lista(objeto(mapOf("nombre" to cadena(), "cantidad" to numero(), "unidad" to cadena(), "seccion" to cadena()), listOf("nombre"))),
                "herramientas" to lista(cadena()), "pasos" to lista(cadena()), "advertencias" to lista(cadena()),
            ),
            listOf("factible", "titulo"),
        )

        val ESQUEMA_ORDENAR = objeto(
            mapOf(
                "productos" to lista(
                    objeto(
                        mapOf(
                            "nombre" to cadena(), "cantidad" to numero(), "unidad" to cadena(), "seccion" to cadena(),
                            "proyecto" to cadena(), "notas" to cadena(),
                        ),
                        listOf("nombre"),
                    ),
                ),
            ),
            listOf("productos"),
        )

        fun cuerpo(
            instrucciones: String, pedido: String, esquema: Map<String, Any?>,
            conHerramientas: Boolean, conPagina: Boolean, conEsquema: Boolean,
        ): String {
            val config = linkedMapOf<String, Any?>("temperature" to 0.2)
            if (conEsquema) {
                config["responseMimeType"] = "application/json"
                config["responseSchema"] = esquema
            }
            val cuerpo = linkedMapOf<String, Any?>(
                "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to instrucciones))),
                "contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to pedido)))),
                "generationConfig" to config,
            )
            if (conHerramientas) {
                cuerpo["tools"] = listOfNotNull(mapOf("google_search" to emptyMap<String, Any?>()), if (conPagina) mapOf("url_context" to emptyMap<String, Any?>()) else null)
            }
            return Json.escribir(cuerpo)
        }

        /** El JSON de la respuesta y las páginas que usó la búsqueda de Google. */
        fun leer(respuesta: String): Pair<Any?, List<Fuente>> {
            val raiz = Json.parse(respuesta)
            val candidato = raiz.lista("candidates")?.firstOrNull()
            val interno = candidato.obj("content").lista("parts").orEmpty()
                .filter { (it as? Map<*, *>)?.get("thought") != true }
                .joinToString("") { it.texto("text").orEmpty() }.trim()
            if (interno.isEmpty()) {
                val motivo = candidato.texto("finishReason") ?: raiz.obj("promptFeedback").texto("blockReason")
                throw IOException("Gemini no devolvió respuesta" + (motivo?.let { " ($it)" } ?: ""))
            }
            val datos = try {
                Json.parse(Pedido.extraerJson(interno) ?: interno)
            } catch (e: IllegalArgumentException) {
                throw IOException("La respuesta de Gemini no se entendió")
            }
            val fuentes = candidato.obj("groundingMetadata").lista("groundingChunks").orEmpty().mapNotNull { c ->
                val w = c.obj("web") ?: return@mapNotNull null
                val uri = w.texto("uri") ?: return@mapNotNull null
                Fuente(w.texto("title") ?: w.texto("domain") ?: uri, uri)
            }.distinctBy { it.enlace }
            return datos to fuentes
        }

        fun mensajeDeError(codigo: Int, json: String): String {
            val detalle = runCatching { Json.parse(json).obj("error").texto("message") }.getOrNull()
            return when (codigo) {
                400, 403 -> "Gemini rechazó la clave o la consulta" + (detalle?.let { ": $it" } ?: "")
                404 -> "No existe el modelo de Gemini indicado (revísalo en Ajustes)"
                429 -> "Se acabó el cupo de Gemini por ahora (la búsqueda de Google tiene un límite diario); intenta en un rato"
                else -> "Gemini respondió $codigo" + (detalle?.let { ": $it" } ?: "")
            }
        }
    }
}
