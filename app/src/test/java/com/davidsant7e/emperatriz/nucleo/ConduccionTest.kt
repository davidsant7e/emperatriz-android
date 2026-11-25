package com.davidsant7e.emperatriz.nucleo

import com.davidsant7e.emperatriz.nucleo.Conduccion.FILTRO_ALARMAS
import com.davidsant7e.emperatriz.nucleo.Conduccion.FILTRO_DESCONOCIDO
import com.davidsant7e.emperatriz.nucleo.Conduccion.FILTRO_PRIORIDAD
import com.davidsant7e.emperatriz.nucleo.Conduccion.FILTRO_TODO
import com.davidsant7e.emperatriz.nucleo.Conduccion.MINUTO
import com.davidsant7e.emperatriz.nucleo.Conduccion.Memoria
import com.davidsant7e.emperatriz.nucleo.Conduccion.Motivo
import com.davidsant7e.emperatriz.nucleo.Conduccion.Sesion
import com.davidsant7e.emperatriz.nucleo.Conduccion.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConduccionTest {
    private val hoy = "2026-11-24"
    private val t0 = 1_800_000_000_000L
    private val conducir = Conduccion.huella(FILTRO_PRIORIDAD, 8, 2, 2, 0, 3)
    private val dormir = Conduccion.huella(FILTRO_ALARMAS, 32, 0, 0, 255, 3)

    @Test fun huellaYFiltros() {
        assertEquals("2.8.2.2.0.3", conducir)
        assertEquals("2.8.2.2.0", Conduccion.huella(FILTRO_PRIORIDAD, 8, 2, 2, 0, null))
        assertTrue(Conduccion.activo(FILTRO_PRIORIDAD))
        assertTrue(Conduccion.activo(Conduccion.FILTRO_SILENCIO))
        assertTrue(!Conduccion.activo(FILTRO_TODO))
        assertTrue(!Conduccion.activo(FILTRO_DESCONOCIDO))
        assertEquals("prioridad", Conduccion.nombreFiltro(FILTRO_PRIORIDAD))
    }

    @Test fun inicioSigueYFin() {
        var m = Memoria()
        val a = Conduccion.cambio(m, FILTRO_PRIORIDAD, conducir, t0)
        assertEquals(Tipo.INICIO, a.tipo)
        m = a.memoria
        assertEquals(t0, m.sesion!!.inicio)

        // Otro modo encima: la sesión sigue y guarda las dos huellas, sin cambiar el inicio.
        val b = Conduccion.cambio(m, FILTRO_ALARMAS, dormir, t0 + 5 * MINUTO)
        assertEquals(Tipo.SIGUE, b.tipo)
        m = b.memoria
        assertEquals(setOf(conducir, dormir), m.sesion!!.huellas)
        assertEquals(t0, m.sesion!!.inicio)

        val c = Conduccion.cambio(m, FILTRO_TODO, null, t0 + 40 * MINUTO)
        assertEquals(Tipo.FIN, c.tipo)
        assertNull(c.memoria.sesion)
        assertEquals(t0, c.terminada!!.inicio)

        // Ya terminado: otro «todo» no hace nada; un filtro desconocido tampoco.
        assertEquals(Tipo.NADA, Conduccion.cambio(c.memoria, FILTRO_TODO, null, t0 + 41 * MINUTO).tipo)
        assertEquals(Tipo.NADA, Conduccion.cambio(m, FILTRO_DESCONOCIDO, null, t0).tipo)
        assertEquals(m, Conduccion.cambio(m, FILTRO_DESCONOCIDO, null, t0).memoria)
    }

    @Test fun decidirSiAvisar() {
        val s = Sesion(t0, setOf(conducir))
        val fin = t0 + 25 * MINUTO
        assertEquals(Motivo.AVISAR, Conduccion.decidir(s, fin, 0L, 3, null))
        assertEquals(Motivo.AVISAR, Conduccion.decidir(s, fin, 0L, 3, conducir))
        assertEquals(Motivo.MUY_CORTO, Conduccion.decidir(s, t0 + MINUTO, 0L, 3, null))
        assertEquals(Motivo.MUY_LARGO, Conduccion.decidir(s, t0 + 7 * 60 * MINUTO, 0L, 3, null))
        assertEquals(Motivo.OTRO_MODO, Conduccion.decidir(Sesion(t0, setOf(dormir)), fin, 0L, 3, conducir))
        assertEquals(Motivo.SIN_PENDIENTES, Conduccion.decidir(s, fin, 0L, 0, null))
        // Un aviso hace 10 minutos: no se repite; hace 31, sí.
        assertEquals(Motivo.RECIENTE, Conduccion.decidir(s, fin, fin - 10 * MINUTO, 3, null))
        assertEquals(Motivo.AVISAR, Conduccion.decidir(s, fin, fin - 31 * MINUTO, 3, null))
        // Reloj cambiado (último aviso «en el futuro»): no bloquea para siempre.
        assertEquals(Motivo.AVISAR, Conduccion.decidir(s, fin, fin + 60 * MINUTO, 3, null))
        // Justo en los límites.
        assertEquals(Motivo.AVISAR, Conduccion.decidir(s, t0 + Conduccion.DURACION_MINIMA, 0L, 1, null))
        assertEquals(Motivo.AVISAR, Conduccion.decidir(s, t0 + Conduccion.DURACION_MAXIMA, 0L, 1, null))
    }

    @Test fun textoDelAviso() {
        assertNull(Conduccion.aviso(Datos()))
        val d = Datos(
            productos = listOf(
                Producto(nombre = "Leche", creado = hoy),
                Producto(nombre = "Arroz", cantidad = 2.0, unidad = "kg", creado = hoy),
                Producto(nombre = "Silicona", seccion = "ferreteria", proyecto = "Baño", notas = "transparente", creado = hoy),
                Producto(nombre = "Tornillos", cantidad = 3.0, seccion = "ferreteria", proyecto = "Baño", creado = hoy),
                Producto(nombre = "Jabón", seccion = "aseo", creado = hoy),
                Producto(nombre = "Pintura", seccion = "ferreteria", proyecto = "Sala", creado = hoy),
                Producto(nombre = "Café", creado = hoy, estado = EstadoProducto.COMPRADO),
            ),
        )
        val a = Conduccion.aviso(d)
        assertNotNull(a)
        a!!
        assertEquals("¿Qué debes comprar?", a.titulo)
        assertEquals(6, a.pendientes)
        // Por sección (mercado, aseo, ferretería) y nombre; el comprado no cuenta.
        assertEquals("6 productos: Arroz, Leche, Jabón y 3 más", a.texto)
        assertEquals("• 2 kg Arroz", a.lineas[0])
        assertEquals("• Silicona (Baño)", a.lineas[4])
        assertEquals("… y 1 más", a.lineas[5])
        assertEquals("Proyectos: Baño (2), Sala (1)", a.lineas.last())

        val uno = Conduccion.aviso(Datos(listOf(Producto(nombre = "Pan", creado = hoy))))!!
        assertEquals("1 producto: Pan", uno.texto)
        assertEquals(listOf("• Pan"), uno.lineas)
    }
}
