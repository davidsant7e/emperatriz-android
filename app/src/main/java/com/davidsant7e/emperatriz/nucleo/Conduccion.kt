package com.davidsant7e.emperatriz.nucleo

/**
 * «Avisarme al terminar de conducir»: las decisiones, sin Android (para probarlas).
 *
 * Android no le dice a otra app qué modo está activo (Conducción, Hora de dormir…): solo deja ver el
 * filtro de interrupciones global (No molestar) y la política de notificaciones que resulta de los modos
 * activos. Por eso:
 *  - Un modo «empieza» cuando el filtro pasa de TODO a PRIORIDAD/SILENCIO/ALARMAS y «termina» al volver a TODO.
 *  - Para reconocer el modo Conducción entre otros se guarda su **huella** (filtro + reglas de la política):
 *    el usuario la marca una vez con el modo Conducción activo. Sin huella, sirve cualquier modo.
 */
object Conduccion {
    // Valores de NotificationManager.INTERRUPTION_FILTER_* (copiados para que el núcleo no dependa de Android).
    const val FILTRO_DESCONOCIDO = 0
    const val FILTRO_TODO = 1
    const val FILTRO_PRIORIDAD = 2
    const val FILTRO_SILENCIO = 3
    const val FILTRO_ALARMAS = 4

    const val MINUTO = 60_000L
    /** No más de un aviso cada 30 minutos (paradas cortas, el modo que se prende y apaga en un trancón). */
    const val ESPERA_ENTRE_AVISOS = 30 * MINUTO
    /** Un modo prendido y apagado en menos de 2 minutos fue sin querer: no se avisa. */
    const val DURACION_MINIMA = 2 * MINUTO
    /** Más de 6 horas no es un trayecto (suele ser Hora de dormir o un modo olvidado). */
    const val DURACION_MAXIMA = 6 * 60 * MINUTO

    fun activo(filtro: Int) = filtro == FILTRO_PRIORIDAD || filtro == FILTRO_SILENCIO || filtro == FILTRO_ALARMAS

    fun nombreFiltro(filtro: Int) = when (filtro) {
        FILTRO_TODO -> "todo"
        FILTRO_PRIORIDAD -> "prioridad"
        FILTRO_SILENCIO -> "silencio"
        FILTRO_ALARMAS -> "alarmas"
        else -> "desconocido"
    }

    /**
     * Huella de un modo: el filtro y las reglas de NotificationManager.Policy (categorías permitidas, quién puede
     * llamar o escribir, efectos visuales). Son números de configuración, nada personal.
     */
    fun huella(filtro: Int, categorias: Int, llamadas: Int, mensajes: Int, efectos: Int, conversaciones: Int?): String =
        listOfNotNull(filtro, categorias, llamadas, mensajes, efectos, conversaciones).joinToString(".")

    /** Una vez con un modo activo: desde cuándo y qué huellas se vieron (puede haber varios modos encima). */
    data class Sesion(val inicio: Long, val huellas: Set<String> = emptySet())

    /** Lo que se guarda entre un aviso del sistema y el siguiente. */
    data class Memoria(val sesion: Sesion? = null, val ultimoAviso: Long = 0L)

    enum class Tipo(val clave: String) { INICIO("inicio"), SIGUE("sigue"), FIN("fin"), NADA("nada") }

    data class Cambio(val memoria: Memoria, val tipo: Tipo, val terminada: Sesion? = null)

    /** Qué significa el filtro actual frente a lo que se sabía: empezó, sigue, terminó o nada. */
    fun cambio(m: Memoria, filtro: Int, huella: String?, ahora: Long): Cambio {
        if (filtro == FILTRO_DESCONOCIDO) return Cambio(m, Tipo.NADA)
        val s = m.sesion
        return when {
            activo(filtro) && s == null -> Cambio(m.copy(sesion = Sesion(ahora, setOfNotNull(huella))), Tipo.INICIO)
            activo(filtro) && s != null -> Cambio(m.copy(sesion = s.copy(huellas = s.huellas + listOfNotNull(huella))), Tipo.SIGUE)
            s != null -> Cambio(m.copy(sesion = null), Tipo.FIN, s)
            else -> Cambio(m, Tipo.NADA)
        }
    }

    enum class Motivo(val clave: String) {
        AVISAR("avisar"),
        MUY_CORTO("muy_corto"),
        MUY_LARGO("muy_largo"),
        OTRO_MODO("otro_modo"),
        RECIENTE("aviso_reciente"),
        SIN_PENDIENTES("sin_pendientes"),
    }

    /**
     * Al terminar una sesión: ¿se avisa? [huellaConduccion] es la huella marcada por el usuario (null = cualquier modo).
     * No se avisa si no hay nada pendiente: un aviso vacío solo enseña a ignorarlos.
     */
    fun decidir(s: Sesion, fin: Long, ultimoAviso: Long, pendientes: Int, huellaConduccion: String?): Motivo {
        val duracion = fin - s.inicio
        return when {
            duracion < DURACION_MINIMA -> Motivo.MUY_CORTO
            duracion > DURACION_MAXIMA -> Motivo.MUY_LARGO
            huellaConduccion != null && huellaConduccion !in s.huellas -> Motivo.OTRO_MODO
            ultimoAviso > 0 && fin - ultimoAviso in 0 until ESPERA_ENTRE_AVISOS -> Motivo.RECIENTE
            pendientes <= 0 -> Motivo.SIN_PENDIENTES
            else -> Motivo.AVISAR
        }
    }

    /** El texto de la notificación. */
    data class Aviso(val titulo: String, val texto: String, val lineas: List<String>, val pendientes: Int)

    /** Resumen de lo pendiente (en el orden de la lista: por sección y nombre); null si no hay nada. */
    fun aviso(d: Datos, maxLineas: Int = 5): Aviso? {
        val ps = Lista.pendientesPorSeccion(d).flatMap { it.second }
        if (ps.isEmpty()) return null
        val n = ps.size
        val nombres = ps.take(3).joinToString(", ") { it.nombre }
        val resto = if (n > 3) " y ${n - 3} más" else ""
        val texto = (if (n == 1) "1 producto: " else "$n productos: ") + nombres + resto

        val proyectos = ps.filter { it.proyecto.isNotBlank() }.groupBy { Texto.simple(it.proyecto) }.values
            .sortedByDescending { it.size }
        val lineas = ArrayList<String>()
        ps.take(maxLineas).forEach { lineas += "• " + Exportar.linea(it.copy(notas = "")) }
        if (n > maxLineas) lineas += "… y ${n - maxLineas} más"
        if (proyectos.isNotEmpty()) {
            lineas += "Proyectos: " + proyectos.joinToString(", ") { g -> "${g.first().proyecto} (${g.size})" }
        }
        return Aviso("¿Qué debes comprar?", texto, lineas, n)
    }
}
