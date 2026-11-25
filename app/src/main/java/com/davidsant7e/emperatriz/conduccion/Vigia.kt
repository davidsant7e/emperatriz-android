package com.davidsant7e.emperatriz.conduccion

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.davidsant7e.emperatriz.datos.Almacen
import com.davidsant7e.emperatriz.nucleo.Conduccion
import com.davidsant7e.emperatriz.registro.Registro
import java.time.LocalDate

/**
 * Vigila los modos de Android (Ajustes → Modos: Conducción, Hora de dormir…) para avisar al terminar de conducir.
 *
 * Cómo se entera con la app cerrada: Android manda `ACTION_INTERRUPTION_FILTER_CHANGED` (y desde Android 15
 * `ACTION_CONSOLIDATED_NOTIFICATION_POLICY_CHANGED`) también a los receptores del manifiesto de las apps que tienen
 * «Acceso a No molestar» ([ModosReceptor]). Cada vez se lee el estado real (no lo que traiga el aviso) y las
 * decisiones las toma [Conduccion] (núcleo, con pruebas).
 */
object Vigia {
    private const val PREFS = "conduccion"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Ajustes ----

    fun activado(ctx: Context) = prefs(ctx).getBoolean("activado", false)

    fun activar(ctx: Context, si: Boolean) {
        prefs(ctx).edit().putBoolean("activado", si).apply {
            if (!si) remove("inicio").remove("huellas")
        }.apply()
        Registro.evento("CONDUCCION", if (si) "activado" else "desactivado", "acceso_no_molestar" to accesoNoMolestar(ctx), "notificaciones" to notificacionesPermitidas(ctx))
        if (si) revisar(ctx, "ajustes", puedeAvisar = false)
    }

    /** Huella del modo Conducción marcada por el usuario (null = cualquier modo cuenta). */
    fun huellaConduccion(ctx: Context): String? = prefs(ctx).getString("huella", null)

    /** Guarda la huella del modo activo ahora. Devuelve false si no hay ningún modo activo. */
    fun marcarModoActual(ctx: Context): Boolean {
        val filtro = filtro(ctx)
        if (!Conduccion.activo(filtro)) return false
        prefs(ctx).edit().putString("huella", huella(ctx, filtro)).apply()
        Registro.evento("CONDUCCION", "modo_marcado", "filtro" to Conduccion.nombreFiltro(filtro))
        revisar(ctx, "marcar", puedeAvisar = false)
        return true
    }

    fun olvidarModo(ctx: Context) {
        prefs(ctx).edit().remove("huella").apply()
        Registro.evento("CONDUCCION", "modo_olvidado")
    }

    // ---- Estado del celular ----

    fun accesoNoMolestar(ctx: Context): Boolean =
        runCatching { nm(ctx).isNotificationPolicyAccessGranted }.getOrDefault(false)

    fun notificacionesPermitidas(ctx: Context): Boolean {
        val permiso = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permiso && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun modoActivoAhora(ctx: Context) = Conduccion.activo(filtro(ctx))

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    private fun filtro(ctx: Context): Int =
        runCatching { nm(ctx).currentInterruptionFilter }.getOrDefault(Conduccion.FILTRO_DESCONOCIDO)

    /** Filtro + reglas de la política que resulta de los modos activos (Android 11+). */
    private fun huella(ctx: Context, filtro: Int): String {
        if (Build.VERSION.SDK_INT < 30) return filtro.toString()
        val p = runCatching { nm(ctx).consolidatedNotificationPolicy }.getOrNull() ?: return filtro.toString()
        return Conduccion.huella(
            filtro, p.priorityCategories, p.priorityCallSenders, p.priorityMessageSenders,
            p.suppressedVisualEffects, p.priorityConversationSenders,
        )
    }

    // ---- Lo principal ----

    /**
     * Compara el estado actual con el guardado y, si un modo terminó, decide si avisar.
     * [puedeAvisar] es false cuando la revisión la hace la propia app abierta (no hace falta notificar).
     */
    @Synchronized
    fun revisar(ctx: Context, origen: String, puedeAvisar: Boolean = true) {
        if (!activado(ctx)) return
        val p = prefs(ctx)
        val filtro = filtro(ctx)
        val huella = if (Conduccion.activo(filtro)) huella(ctx, filtro) else null
        val antes = Conduccion.Memoria(
            sesion = p.getLong("inicio", -1L).takeIf { it > 0 }?.let { Conduccion.Sesion(it, p.getStringSet("huellas", null)?.toSet().orEmpty()) },
            ultimoAviso = p.getLong("ultimo_aviso", 0L),
        )
        val ahora = System.currentTimeMillis()
        val c = Conduccion.cambio(antes, filtro, huella, ahora)
        p.edit().apply {
            val s = c.memoria.sesion
            if (s == null) remove("inicio").remove("huellas") else putLong("inicio", s.inicio).putStringSet("huellas", s.huellas)
        }.apply()

        val marcada = huellaConduccion(ctx)
        when (c.tipo) {
            Conduccion.Tipo.INICIO -> Registro.evento(
                "CONDUCCION", "inicio", "filtro" to Conduccion.nombreFiltro(filtro), "origen" to origen,
                "es_conduccion" to marcada?.let { it == huella },
            )
            Conduccion.Tipo.SIGUE -> if (c.memoria.sesion?.huellas != antes.sesion?.huellas) Registro.evento(
                "CONDUCCION", "otro_modo_encima", "filtro" to Conduccion.nombreFiltro(filtro), "es_conduccion" to marcada?.let { it == huella },
            )
            Conduccion.Tipo.FIN -> terminar(ctx, c.terminada!!, ahora, antes.ultimoAviso, marcada, origen, puedeAvisar)
            Conduccion.Tipo.NADA -> Unit
        }
    }

    private fun terminar(ctx: Context, s: Conduccion.Sesion, ahora: Long, ultimoAviso: Long, marcada: String?, origen: String, puedeAvisar: Boolean) {
        val aviso = Conduccion.aviso(Almacen(ctx).cargar(LocalDate.now().toString()))
        var motivo = Conduccion.decidir(s, ahora, ultimoAviso, aviso?.pendientes ?: 0, marcada).clave
        if (motivo == Conduccion.Motivo.AVISAR.clave) {
            motivo = when {
                !puedeAvisar -> "app_abierta"
                !notificacionesPermitidas(ctx) -> "sin_permiso_notificaciones"
                else -> motivo
            }
        }
        Registro.evento(
            "CONDUCCION", "fin", "minutos" to (ahora - s.inicio) / 60_000, "origen" to origen,
            "es_conduccion" to marcada?.let { it in s.huellas }, "decision" to motivo,
        )
        if (motivo == Conduccion.Motivo.AVISAR.clave && aviso != null) {
            AvisoConduccion.mostrar(ctx, aviso)
            prefs(ctx).edit().putLong("ultimo_aviso", ahora).apply()
        }
    }

    /** «Probar aviso» en Ajustes: muestra la notificación ya, sin esperar a un modo (ni contar para los 30 minutos). */
    fun probar(ctx: Context): Boolean {
        val aviso = Conduccion.aviso(Almacen(ctx).cargar(LocalDate.now().toString()))
            ?: Conduccion.Aviso("¿Qué debes comprar?", "Nada pendiente: la lista está al día.", emptyList(), 0)
        if (!notificacionesPermitidas(ctx)) return false
        AvisoConduccion.mostrar(ctx, aviso, prueba = true)
        return true
    }
}
