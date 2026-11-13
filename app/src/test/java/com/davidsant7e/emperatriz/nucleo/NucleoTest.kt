package com.davidsant7e.emperatriz.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NucleoTest {
    private val hoy = "2026-11-13"

    @Test fun numerosYPrecios() {
        assertEquals(2.0, Numeros.cantidad("2")!!, 0.0)
        assertEquals(1.5, Numeros.cantidad("1,5")!!, 0.0)
        assertEquals(0.5, Numeros.cantidad("1/2")!!, 0.0)
        assertEquals(1.5, Numeros.cantidad("1 1/2")!!, 0.0)
        assertEquals(3.0, Numeros.cantidad("tres")!!, 0.0)
        assertEquals(0.5, Numeros.cantidad("media")!!, 0.0)
        assertNull(Numeros.cantidad("arroz"))
        assertEquals(12900.0, Numeros.precio("$ 12.900")!!, 0.0)
        assertEquals(12900.0, Numeros.precio("12,900")!!, 0.0)
        assertEquals(4500.5, Numeros.precio("COP 4.500,50")!!, 0.001)
        assertEquals(4.99, Numeros.precio("US$4.99")!!, 0.001)
        assertEquals(1250000.0, Numeros.precio("1.250.000")!!, 0.0)
        assertEquals("$12.900", Numeros.dinero(12900.0))
        assertEquals("2,5", Numeros.texto(2.5))
    }

    @Test fun lineasDeTexto() {
        val a = Pedido.linea("2 kg de arroz", hoy)!!
        assertEquals("Arroz", a.nombre); assertEquals(2.0, a.cantidad, 0.0); assertEquals("kg", a.unidad); assertEquals("mercado", a.seccion)
        val b = Pedido.linea("arroz 2 kilos", hoy)!!
        assertEquals("Arroz", b.nombre); assertEquals("kg", b.unidad)
        val c = Pedido.linea("leche x3", hoy)!!
        assertEquals("Leche", c.nombre); assertEquals(3.0, c.cantidad, 0.0)
        val d = Pedido.linea("media docena de huevos", hoy)!!
        assertEquals("Huevos", d.nombre); assertEquals(6.0, d.cantidad, 0.0)
        val e = Pedido.linea("- 20 tornillos drywall 1/4 (proyecto: Baño)", hoy)!!
        assertEquals("Tornillos drywall 1/4", e.nombre); assertEquals(20.0, e.cantidad, 0.0)
        assertEquals("ferreteria", e.seccion); assertEquals("Baño", e.proyecto)
        val f = Pedido.linea("1/4 de galón de pintura blanca para el proyecto Sala", hoy)!!
        assertEquals("Pintura blanca", f.nombre); assertEquals(0.25, f.cantidad, 0.0); assertEquals("galón", f.unidad); assertEquals("Sala", f.proyecto)
        assertEquals("aseo", Pedido.linea("jabón rey", hoy)!!.seccion)
        assertEquals("tecnologia", Pedido.linea("cable usb c", hoy)!!.seccion)
        assertNull(Pedido.linea("  - ", hoy))
    }

    @Test fun textoDictado() {
        val r = Pedido.leer("2 kg de arroz, leche y 3 tornillos; 1,5 lb de queso\n• pan", hoy)
        assertEquals(listOf("Arroz", "Leche", "Tornillos", "Queso", "Pan"), r.productos.map { it.nombre })
        assertEquals(1.5, r.productos[3].cantidad, 0.0)
        val s = Pedido.leer("pintura, brocha", hoy, proyecto = "Cocina", seccion = "ferreteria")
        assertTrue(s.productos.all { it.proyecto == "Cocina" && it.seccion == "ferreteria" })
    }

    @Test fun jsonDeClaude() {
        val texto = """
            Aquí está tu lista:
            ```json
            {"proyecto":"Baño","productos":[
              {"nombre":"silicona antihongos","cantidad":2,"unidad":"tubos"},
              {"nombre":"Acetaminofén","seccion":"farmacia"},
              "3 kg de papa"
            ]}
            ```
        """.trimIndent()
        val r = Pedido.leer(texto, hoy)
        assertEquals(3, r.productos.size)
        assertEquals("Silicona antihongos", r.productos[0].nombre)
        assertEquals("tubo", r.productos[0].unidad)
        assertEquals("ferreteria", r.productos[0].seccion)
        assertEquals("Baño", r.productos[0].proyecto)
        assertEquals("farmacia", r.productos[1].seccion)
        assertEquals("Papa", r.productos[2].nombre)
        assertEquals(listOf("Baño"), r.proyectos.map { it.nombre })
        assertFalse(r.respaldo)
        // Un arreglo suelto también vale.
        assertEquals(2, Pedido.leer("""[{"nombre":"Pan"},{"name":"Café","cantidad":"1/2","unidad":"libra"}]""", hoy).productos.size)
    }

    @Test fun agregarSinDuplicar() {
        var d = Datos()
        d = Lista.agregar(d, Pedido.leer("2 kg de arroz, leche", hoy).productos, hoy)
        d = Lista.agregar(d, Pedido.leer("1 kilo de Arróz", hoy).productos, hoy)
        assertEquals(2, d.productos.size)
        assertEquals(3.0, d.productos.first { it.nombre == "Arroz" }.cantidad, 0.0)
        // Otro proyecto = otro ítem.
        d = Lista.agregar(d, Pedido.leer("arroz 1 kg #Fiesta", hoy).productos, hoy)
        assertEquals(3, d.productos.size)
        assertEquals(listOf("Fiesta"), d.proyectos.map { it.nombre })
    }

    @Test fun comprarYVolverAComprar() {
        var d = Lista.agregar(Datos(), Pedido.leer("2 kg de arroz", hoy).productos, hoy)
        val id = d.productos[0].id
        d = Lista.comprar(d, id, hoy, precio = 9800.0, tienda = "D1")
        val p = d.productos[0]
        assertTrue(p.comprado); assertEquals(1, p.historial.size); assertEquals(9800.0, p.ultimoPagado!!.precio!!, 0.0)
        d = Lista.volverAComprar(d, id, "2026-11-20")
        assertFalse(d.productos[0].comprado); assertEquals(2.0, d.productos[0].cantidad, 0.0)
        d = Lista.volverAComprar(d, id, "2026-11-20") // ya pendiente: aumenta
        assertEquals(2.5, d.productos[0].cantidad, 0.0)
        d = Lista.ajustar(d, id, hoy, -1); d = Lista.ajustar(d, id, hoy, -1); d = Lista.ajustar(d, id, hoy, -1); d = Lista.ajustar(d, id, hoy, -1)
        assertEquals(0.5, d.productos[0].cantidad, 0.0)
        // Agregar uno igual que ya estaba comprado lo devuelve a la lista con la cantidad nueva y conserva el historial.
        d = Lista.comprar(d, id, "2026-11-21")
        d = Lista.agregar(d, Pedido.leer("arroz 5 kg", hoy).productos, hoy)
        assertEquals(1, d.productos.size); assertEquals(5.0, d.productos[0].cantidad, 0.0); assertEquals(2, d.productos[0].historial.size)
        // Desmarcar quita la compra del mismo día.
        d = Lista.comprar(d, id, hoy); d = Lista.desmarcar(d, id, hoy)
        assertEquals(2, d.productos[0].historial.size)
    }

    @Test fun respaldoIdaYVuelta() {
        var d = Lista.agregar(Datos(), Pedido.leer("2 kg de arroz, 3 tornillos #Baño", hoy).productos, hoy)
        d = Lista.guardarPrecios(d, d.productos[0].id, listOf(Precio("Éxito", 10500.0, consultado = hoy, fecha = "2026-11-10", enlace = "https://exito.com/x")), hoy)
        d = Lista.comprar(d, d.productos[1].id, hoy, 300.0, "Homecenter")
        val vuelta = Datos.deJson(d.aJson(), hoy)
        assertEquals(d.productos, vuelta.productos)
        assertEquals(d.proyectos, vuelta.proyectos)
        val r = Pedido.leer(d.aJson(), hoy)
        assertTrue(r.respaldo)
    }

    @Test fun importarMezcla() {
        var d = Lista.agregar(Datos(), Pedido.leer("2 kg de arroz, leche", hoy).productos, hoy)
        val arroz = d.productos.first { it.nombre == "Arroz" }
        // Claude devuelve el arroz (mismo id) con otra cantidad, leche otra vez y un producto nuevo.
        val json = """{"productos":[{"id":"${arroz.id}","nombre":"Arroz","cantidad":4,"unidad":"kg","seccion":"mercado"},{"nombre":"Leche"},{"nombre":"Café"}]}"""
        val imp = Lista.importar(d, Pedido.leer(json, hoy), hoy)
        assertEquals(1, imp.agregados); assertEquals(2, imp.actualizados)
        assertEquals(4.0, imp.datos.productos.first { it.id == arroz.id }.cantidad, 0.0)
        assertEquals(2.0, imp.datos.productos.first { it.nombre == "Leche" }.cantidad, 0.0)
        assertEquals(3, imp.datos.productos.size)
    }

    @Test fun exportes() {
        var d = Lista.agregar(Datos(), Pedido.leer("2 kg de arroz, 3 tornillos #Baño, =HACK()", hoy).productos, hoy)
        val t = Exportar.texto(d)
        assertTrue(t.contains("MERCADO")); assertTrue(t.contains("☐ 2 kg Arroz")); assertTrue(t.contains("☐ 3 Tornillos (Baño)"))
        val c = Exportar.paraClaude(d, hoy)
        val json = Pedido.extraerJson(c)!!
        assertEquals(3, Pedido.leer(json, hoy).productos.size)
        val csv = Exportar.csv(d)
        assertTrue(csv.startsWith("﻿Producto,"))
        assertTrue(csv.contains("'=HACK()"))
        assertEquals("Lista vacía", Exportar.texto(Datos()))
        assertEquals(3, Lista.pendientesPorSeccion(d).flatMap { it.second }.size)
        assertEquals(listOf("mercado", "ferreteria"), Lista.pendientesPorSeccion(d).map { it.first })
    }

    @Test fun costoDeProyecto() {
        var d = Lista.agregar(Datos(), Pedido.leer("2 galones de pintura #Sala, 1 brocha #Sala", hoy).productos, hoy)
        val pintura = d.productos.first { it.nombre == "Pintura" }
        d = Lista.guardarPrecios(d, pintura.id, listOf(Precio("A", 80000.0, consultado = hoy), Precio("B", 70000.0, consultado = hoy)), hoy)
        d = Lista.comprar(d, d.productos.first { it.nombre == "Brocha" }.id, hoy, 12000.0)
        val c = Lista.costo(d, "sala")
        assertEquals(12000.0, c.pagado, 0.0); assertEquals(140000.0, c.estimadoPendiente, 0.0); assertEquals(0, c.sinPrecio)
    }

    // ---- Gemini (sin red) ----

    private fun respuesta(json: String, fuentes: String = "") =
        """{"candidates":[{"content":{"parts":[{"text":${Json.escribir(json)}}]},"groundingMetadata":{"groundingChunks":[$fuentes]}}]}"""

    @Test fun preciosConFuentes() {
        var cuerpoEnviado = ""
        val g = Gemini({ _, cuerpo, cab ->
            cuerpoEnviado = cuerpo
            assertEquals("k", cab["x-goog-api-key"])
            200 to respuesta(
                """{"producto":"Arroz Diana 5 kg","descripcion":"Arroz blanco","resumen":"Más barato en D1","precios":[
                    {"tienda":"Éxito","precio":"$ 21.900","moneda":"COP","fecha":"10/11/2026","enlace":"https://exito.com/a"},
                    {"tienda":"D1","precio":18500,"presentacion":"5 kg","enlace":"no-es-url"},
                    {"tienda":"","precio":1}]}""",
                """{"web":{"uri":"https://exito.com/a","title":"exito.com"}},{"web":{"uri":"https://exito.com/a","title":"dup"}}""",
            )
        })
        val b = g.buscarPrecios("Arroz", "", "https://tienda.com/arroz", Gemini.Lugar(ciudad = "Bogotá"), hoy, "k")
        assertEquals("Arroz Diana 5 kg", b.producto)
        assertEquals(listOf("D1", "Éxito"), b.precios.map { it.tienda })
        assertEquals(21900.0, b.precios[1].precio!!, 0.0)
        assertEquals("2026-11-10", b.precios[1].fecha)
        assertEquals(hoy, b.precios[0].consultado)
        assertNull(b.precios[0].enlace)
        assertEquals(1, b.fuentes.size)
        assertTrue(cuerpoEnviado.contains("google_search")); assertTrue(cuerpoEnviado.contains("url_context"))
        assertTrue(cuerpoEnviado.contains("Bogotá, Colombia"))
    }

    @Test fun reintentaSinEsquemaSiHay400() {
        val cuerpos = mutableListOf<String>()
        val g = Gemini({ _, c, _ ->
            cuerpos += c
            if (cuerpos.size == 1) 400 to """{"error":{"message":"tools + schema"}}"""
            else 200 to respuesta("Claro: ```json\n{\"precios\":[{\"tienda\":\"Ara\",\"precio\":3200}]}\n```")
        })
        val b = g.buscarPrecios("Leche", "", null, Gemini.Lugar(), hoy, "k")
        assertEquals(2, cuerpos.size)
        assertTrue(cuerpos[0].contains("responseSchema")); assertFalse(cuerpos[1].contains("responseSchema"))
        assertFalse(cuerpos[0].contains("url_context"))
        assertEquals(3200.0, b.precios[0].precio!!, 0.0)
    }

    @Test fun caseroYOrdenar() {
        val g = Gemini({ _, c, _ ->
            if (c.contains("HACER EN CASA")) 200 to respuesta(
                """{"titulo":"Mayonesa casera","factible":true,"dificultad":"fácil","ingredientes":[{"nombre":"huevo","cantidad":1,"unidad":"und","seccion":"mercado"},{"nombre":"aceite","cantidad":250,"unidad":"mililitros"}],"pasos":["Licúa","Agrega el aceite en hilo"],"advertencias":["Refrigera y consume en 3 días"]}""",
            ) else 200 to respuesta("""{"productos":[{"nombre":"tornillos","cantidad":10,"seccion":"ferretería","proyecto":"Baño"}]}""")
        })
        val r = g.casero("Mayonesa", "", Gemini.Lugar(), "k")
        assertTrue(r.factible); assertEquals(2, r.ingredientes.size); assertEquals("ml", r.ingredientes[1].unidad); assertEquals(2, r.pasos.size)
        val ps = g.ordenar("diez tornillos para el baño", listOf("Baño"), hoy, "k")
        assertEquals("Tornillos", ps[0].nombre); assertEquals("ferreteria", ps[0].seccion); assertEquals("Baño", ps[0].proyecto)
    }

    @Test fun erroresDeGemini() {
        val sinClave = runCatching { Gemini({ _, _, _ -> 200 to "" }).ordenar("x", emptyList(), hoy, null) }.exceptionOrNull()
        assertTrue(sinClave is Gemini.SinClaveException)
        val cupo = runCatching { Gemini({ _, _, _ -> 429 to "{}" }).ordenar("x", emptyList(), hoy, "k") }.exceptionOrNull()
        assertNotNull(cupo); assertTrue(cupo!!.message!!.contains("cupo"))
        val vacio = runCatching { Gemini({ _, _, _ -> 200 to """{"candidates":[{"finishReason":"SAFETY"}]}""" }).ordenar("x", emptyList(), hoy, "k") }.exceptionOrNull()
        assertTrue(vacio!!.message!!.contains("SAFETY"))
    }
}
