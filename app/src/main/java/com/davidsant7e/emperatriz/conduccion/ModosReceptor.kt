package com.davidsant7e.emperatriz.conduccion

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.davidsant7e.emperatriz.registro.Registro

/**
 * Recibe los cambios de No molestar / Modos aunque la app esté cerrada (Android los manda a las apps con
 * «Acceso a No molestar»). No confía en el contenido del aviso: [Vigia] lee el estado real del celular.
 */
class ModosReceptor : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val accion = intent.action ?: return
        if (accion == NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED) {
            Registro.evento("CONDUCCION", "permiso", "acceso_no_molestar" to Vigia.accesoNoMolestar(context))
            return
        }
        if (accion != NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED && accion != ACCION_POLITICA) return
        val origen = if (accion == ACCION_POLITICA) "politica" else "filtro"
        val pendiente = goAsync()
        Thread {
            try {
                Vigia.revisar(context.applicationContext, origen)
            } catch (e: Exception) {
                Registro.error("CONDUCCION", e, "fase" to "revisar")
            } finally {
                pendiente.finish()
            }
        }.start()
    }

    private companion object {
        /** NotificationManager.ACTION_CONSOLIDATED_NOTIFICATION_POLICY_CHANGED (Android 15, API 35). */
        const val ACCION_POLITICA = "android.app.action.CONSOLIDATED_NOTIFICATION_POLICY_CHANGED"
    }
}
