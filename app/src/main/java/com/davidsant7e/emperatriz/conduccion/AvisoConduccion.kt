package com.davidsant7e.emperatriz.conduccion

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.davidsant7e.emperatriz.R
import com.davidsant7e.emperatriz.nucleo.Conduccion
import com.davidsant7e.emperatriz.registro.Registro
import com.davidsant7e.emperatriz.ui.MainActivity

/**
 * La notificación «¿Qué debes comprar?». Android no deja abrir una pantalla desde segundo plano, así que se avisa
 * con prioridad alta (sale arriba, como un aviso emergente) y al tocarla se abre La Emperatriz en lo pendiente.
 */
object AvisoConduccion {
    const val CANAL = "conduccion"
    const val ID = 3003
    /** Acción del intent que abre la app en la lista de pendientes. */
    const val VER_PENDIENTES = "com.davidsant7e.emperatriz.VER_PENDIENTES"
    const val AHORA_NO = "com.davidsant7e.emperatriz.AHORA_NO"
    private const val DOS_HORAS = 2 * 60 * 60 * 1000L

    fun crearCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val canal = NotificationChannel(CANAL, "Al terminar de conducir", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Lo que te falta comprar, cuando se desactiva el modo Conducción."
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    @SuppressLint("MissingPermission") // Vigia revisa el permiso antes de llamar.
    fun mostrar(ctx: Context, a: Conduccion.Aviso, prueba: Boolean = false) {
        crearCanal(ctx)
        val abrir = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java).setAction(VER_PENDIENTES)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ahoraNo = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, AhoraNoReceptor::class.java).setAction(AHORA_NO),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val estilo = NotificationCompat.InboxStyle().setBigContentTitle(a.titulo).setSummaryText(a.texto.substringBefore(':'))
        a.lineas.forEach { estilo.addLine(it) }
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_aviso)
            .setColor(0xFF1F2A5C.toInt())
            .setContentTitle(a.titulo)
            .setContentText(a.texto)
            .setStyle(if (a.lineas.isEmpty()) NotificationCompat.BigTextStyle().bigText(a.texto) else estilo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setTimeoutAfter(DOS_HORAS)
            .addAction(0, "Ver lista", abrir)
            .addAction(0, "Ahora no", ahoraNo)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID, n) }
            .onSuccess { Registro.evento("CONDUCCION", "aviso", "pendientes" to a.pendientes, "prueba" to prueba.takeIf { it }) }
            .onFailure { Registro.error("CONDUCCION", it, "fase" to "aviso") }
    }

    fun cancelar(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}

/** Botón «Ahora no» de la notificación. */
class AhoraNoReceptor : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AvisoConduccion.AHORA_NO) return
        AvisoConduccion.cancelar(context)
        Registro.evento("CONDUCCION", "aviso_descartado")
    }
}
