package com.davidsant7e.emperatriz.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidsant7e.emperatriz.conduccion.AvisoConduccion
import com.davidsant7e.emperatriz.nucleo.Exportar
import com.davidsant7e.emperatriz.registro.Registro
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: EmperatrizViewModel by viewModels()

    private val dictar = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::dictado)
    }

    private val abrirArchivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { leerArchivo(it) }
    }

    private val pedirPermisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { si ->
        Registro.evento("CONDUCCION", "permiso_notificaciones", "concedido" to si)
        // Si ya lo negó antes, Android no vuelve a preguntar: se abren los ajustes de notificaciones de la app.
        if (!si) abrirAjustesNotificaciones()
        vm.refrescarConduccion()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) recibir(intent)
        setContent {
            TemaEmperatriz {
                val e by vm.estado.collectAsStateWithLifecycle()
                App(
                    e, vm,
                    Acciones(
                        dictar = ::dictar,
                        abrirArchivo = { abrirArchivo.launch(arrayOf("application/json", "text/plain", "text/*", "application/octet-stream")) },
                        pegar = ::pegar,
                        compartirTexto = ::compartirTexto,
                        compartirArchivo = ::compartirArchivo,
                        copiar = ::copiar,
                        abrirEnlace = ::abrirEnlace,
                        pedirAccesoNoMolestar = ::pedirAccesoNoMolestar,
                        pedirNotificaciones = ::pedirNotificaciones,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refrescarConduccion()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recibir(intent)
    }

    /** Compartido desde otra app: texto (lista, JSON de Claude, enlace de un producto) o un archivo .json/.txt. */
    private fun recibir(i: Intent?) {
        when (i?.action) {
            AvisoConduccion.VER_PENDIENTES -> {
                AvisoConduccion.cancelar(this)
                Registro.evento("CONDUCCION", "aviso_abierto")
                vm.verPendientes()
            }
            Intent.ACTION_SEND -> {
                val texto = i.getStringExtra(Intent.EXTRA_TEXT)
                val archivo = IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                when {
                    !texto.isNullOrBlank() -> vm.recibir(texto)
                    archivo != null -> leerArchivo(archivo)
                }
            }
            Intent.ACTION_VIEW -> i.data?.let { leerArchivo(it) }
        }
    }

    private fun leerArchivo(uri: Uri) {
        val texto = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().let { b -> String(b, 0, minOf(b.size, MAX_ARCHIVO), Charsets.UTF_8) } }
        } catch (e: Exception) {
            Registro.error("ARCHIVO", e)
            null
        }
        if (texto.isNullOrBlank()) Toast.makeText(this, "No pude leer el archivo.", Toast.LENGTH_LONG).show()
        else vm.agregarTexto(texto.removePrefix("﻿"))
    }

    private fun dictar() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta lo que vas a comprar: «dos kilos de arroz, leche y tres tornillos para el baño»")
        }
        try {
            dictar.launch(i)
        } catch (e: ActivityNotFoundException) {
            Registro.error("DICTADO", e)
            Toast.makeText(this, "Este celular no tiene dictado por voz (app de Google).", Toast.LENGTH_LONG).show()
        }
    }

    private fun pegar(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
    }

    private fun compartirArchivo(nombre: String, tipo: String, contenido: String) {
        val carpeta = File(cacheDir, "exportar").apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() }
        val archivo = File(carpeta, nombre).apply { writeText(contenido) }
        val uri = FileProvider.getUriForFile(this, "$packageName.archivos", archivo)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = tipo
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, nombre)
            clipData = ClipData.newRawUri(nombre, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Enviar $nombre"))
    }

    private fun compartirTexto(titulo: String, texto: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
            putExtra(Intent.EXTRA_SUBJECT, titulo)
        }
        startActivity(Intent.createChooser(i, titulo))
    }

    private fun copiar(texto: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("La Emperatriz", texto))
        Toast.makeText(this, "Copiado.", Toast.LENGTH_SHORT).show()
    }

    private fun abrirEnlace(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No hay una app para abrir el enlace.", Toast.LENGTH_LONG).show()
        }
    }

    /** Ajustes → Notificaciones → Acceso a No molestar (allí se activa La Emperatriz). */
    private fun pedirAccesoNoMolestar() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            Toast.makeText(this, "Busca «La Emperatriz» y permite el acceso.", Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            Registro.error("CONDUCCION", e, "fase" to "abrir_acceso")
            Toast.makeText(this, "Este celular no muestra el ajuste de Acceso a No molestar.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pedirNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33) pedirPermisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        else abrirAjustesNotificaciones()
    }

    private fun abrirAjustesNotificaciones() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }.onFailure { Registro.error("CONDUCCION", it, "fase" to "abrir_notificaciones") }
    }

    private companion object {
        const val MAX_ARCHIVO = 5_000_000
    }
}

/** Lo que la interfaz le pide a la actividad. */
class Acciones(
    val dictar: () -> Unit,
    val abrirArchivo: () -> Unit,
    val pegar: () -> String?,
    val compartirTexto: (titulo: String, texto: String) -> Unit,
    val compartirArchivo: (nombre: String, tipo: String, contenido: String) -> Unit,
    val copiar: (String) -> Unit,
    val abrirEnlace: (String) -> Unit,
    val pedirAccesoNoMolestar: () -> Unit,
    val pedirNotificaciones: () -> Unit,
)

/** Nombre de archivo de exportación con la fecha. */
fun archivo(base: String, hoy: String, ext: String) = Exportar.nombreArchivo(base, hoy, ext)
