package com.davidsant7e.emperatriz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davidsant7e.emperatriz.nucleo.Lista
import com.davidsant7e.emperatriz.nucleo.Numeros
import com.davidsant7e.emperatriz.nucleo.Pedido
import com.davidsant7e.emperatriz.nucleo.Producto
import com.davidsant7e.emperatriz.nucleo.Secciones
import com.davidsant7e.emperatriz.nucleo.Unidades

enum class Ventana { AJUSTES, PROYECTOS, IMPORTAR, EXPORTAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(e: Estado, vm: EmperatrizViewModel, a: Acciones) {
    val snack = remember { SnackbarHostState() }
    var ventana by remember { mutableStateOf<Ventana?>(null) }
    LaunchedEffect(e.aviso) {
        e.aviso?.let { snack.showSnackbar(it); vm.cerrarAviso() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("III · La Emperatriz") },
                actions = {
                    TextButton(onClick = { ventana = Ventana.PROYECTOS }) { Text("Proyectos", color = Durazno) }
                    Menu(
                        "⋮",
                        listOf(
                            "Importar (Claude, PC, archivo)" to { ventana = Ventana.IMPORTAR },
                            "Exportar / enviar" to { ventana = Ventana.EXPORTAR },
                            "Ajustes" to { ventana = Ventana.AJUSTES },
                        ),
                        color = Durazno,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzulNoche, titleContentColor = Durazno),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val d = e.datos
        val grupos = Lista.pendientesPorSeccion(d, e.seccion, e.proyecto)
        val comprados = Lista.comprados(d, e.seccion, e.proyecto)
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Entrada(e, vm, a) }
            e.ocupado?.let { t ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(t, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            val frecuentes = Lista.frecuentes(d)
            if (frecuentes.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Lo de siempre (toca para volver a comprar)", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            frecuentes.forEach { p -> AssistChip(onClick = { vm.volverAComprar(p.id) }, label = { Text(p.nombre) }) }
                        }
                    }
                }
            }
            item { Filtros(e, vm) }
            if (e.proyecto != null && e.proyecto.isNotBlank()) item { ResumenProyecto(e) }
            if (grupos.isEmpty()) {
                item {
                    Text(
                        if (d.productos.isEmpty()) "La lista está vacía. Escribe o dicta lo que vas a comprar, por ejemplo: «2 kg de arroz, leche y 3 tornillos para el baño»."
                        else "Nada pendiente aquí. 🎉",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            grupos.forEach { (s, ps) ->
                item(key = "s-$s") {
                    Text(
                        "${Secciones.nombre(s)} · ${ps.size}", Modifier.padding(start = 16.dp, top = 8.dp),
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary,
                    )
                }
                items(ps, key = { it.id }) { p -> FilaPendiente(p, e.proyecto == null, vm) }
            }
            if (comprados.isNotEmpty()) {
                item(key = "comprados") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.verComprados(!e.verComprados) }) {
                            Text((if (e.verComprados) "▾ " else "▸ ") + "Comprados (${comprados.size})")
                        }
                        Spacer(Modifier.weight(1f))
                        if (e.verComprados) TextButton(onClick = vm::borrarComprados) { Text("Borrar comprados") }
                    }
                }
                if (e.verComprados) items(comprados, key = { "c-" + it.id }) { p -> FilaComprado(p, vm) }
            }
        }
    }

    e.abierto?.let { id -> e.datos.productos.firstOrNull { it.id == id }?.let { DialogoProducto(it, e, vm, a) } }
    e.busqueda?.let { (id, b) -> DialogoPrecios(e.datos.productos.firstOrNull { it.id == id }, b, vm, a) }
    e.receta?.let { (_, r) -> DialogoReceta(r, vm, a) }
    e.porImportar?.let { DialogoRespaldo(it, vm) }
    when (ventana) {
        Ventana.AJUSTES -> DialogoAjustes(e, vm, a) { ventana = null }
        Ventana.PROYECTOS -> DialogoProyectos(e, vm) { ventana = null }
        Ventana.IMPORTAR -> DialogoImportar(vm, a) { ventana = null }
        Ventana.EXPORTAR -> DialogoExportar(e, vm, a) { ventana = null }
        null -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Entrada(e: Estado, vm: EmperatrizViewModel, a: Acciones) {
    Card(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = e.entrada, onValueChange = vm::escribir,
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6,
                label = { Text("¿Qué hay que comprar?") },
                placeholder = { Text("2 kg de arroz, leche y 3 tornillos para el baño") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = vm::agregarEntrada, enabled = e.entrada.isNotBlank()) { Text("Agregar") }
                OutlinedButton(onClick = a.dictar) { Text("🎤 Dictar") }
                OutlinedButton(onClick = vm::ordenarEntrada, enabled = e.entrada.isNotBlank() && e.ocupado == null) { Text("✦ Ordenar con Gemini") }
                TextButton(onClick = {
                    val t = a.pegar()?.trim().orEmpty()
                    when {
                        t.isEmpty() -> {}
                        Pedido.extraerJson(t) != null || t.startsWith("http") -> vm.recibir(t)
                        else -> vm.dictado(t)
                    }
                }) { Text("Pegar") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Proyecto de lo nuevo:", style = MaterialTheme.typography.bodySmall)
                Menu(
                    e.proyectoNuevo.ifBlank { "ninguno" } + " ▾",
                    listOf("Ninguno" to { vm.proyectoNuevo("") }) + e.datos.nombresProyectos.map { n -> n to { vm.proyectoNuevo(n) } },
                )
            }
            Text(
                "También puedes compartirle a La Emperatriz el enlace de un producto (lee la página y busca precios), " +
                    "un JSON de Claude o una lista copiada de WhatsApp.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun Filtros(e: Estado, vm: EmperatrizViewModel) {
    val pendientes = e.datos.productos.filter { !it.comprado }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = e.seccion == null, onClick = { vm.filtrarSeccion(null) }, label = { Text("Todo · ${pendientes.size}") })
            Secciones.CLAVES.forEach { s ->
                val n = pendientes.count { it.seccion == s }
                if (n > 0 || e.seccion == s) {
                    FilterChip(selected = e.seccion == s, onClick = { vm.filtrarSeccion(if (e.seccion == s) null else s) }, label = { Text("${Secciones.nombre(s)} · $n") })
                }
            }
        }
        val proyectos = e.datos.nombresProyectos
        if (proyectos.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = e.proyecto == null, onClick = { vm.filtrarProyecto(null) }, label = { Text("Todos") })
                FilterChip(selected = e.proyecto == "", onClick = { vm.filtrarProyecto(if (e.proyecto == "") null else "") }, label = { Text("General") })
                proyectos.forEach { p ->
                    FilterChip(selected = e.proyecto == p, onClick = { vm.filtrarProyecto(if (e.proyecto == p) null else p) }, label = { Text(p) })
                }
            }
        }
    }
}

@Composable
private fun ResumenProyecto(e: Estado) {
    val p = e.proyecto ?: return
    val c = Lista.costo(e.datos, p)
    val info = e.datos.proyectos.firstOrNull { it.nombre == p }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = AzulNoche)) {
        Column(Modifier.padding(12.dp)) {
            Text(p, color = Durazno, fontWeight = FontWeight.Bold)
            if (!info?.origen.isNullOrBlank()) Text("De: ${info!!.origen}", color = Color.White, style = MaterialTheme.typography.bodySmall)
            if (!info?.notas.isNullOrBlank()) Text(info!!.notas, color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text(
                "Pagado ${Numeros.dinero(c.pagado, e.lugar.moneda)} · falta aprox. ${Numeros.dinero(c.estimadoPendiente, e.lugar.moneda)}" +
                    if (c.sinPrecio > 0) " (+${c.sinPrecio} sin precio)" else "",
                color = Color.White, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** «2 kg · Baño · desde $18.500 en D1». */
fun detalle(p: Producto, conProyecto: Boolean = true): String = listOfNotNull(
    Unidades.texto(p.cantidad, p.unidad).takeIf { p.cantidad != 1.0 || p.unidad.isNotBlank() },
    p.proyecto.takeIf { conProyecto && it.isNotBlank() },
    p.mejorPrecio?.let { m -> "desde ${Numeros.dinero(m.precio!!, m.moneda)} en ${m.tienda}" }
        ?: p.ultimoPagado?.let { u -> "última vez ${Numeros.dinero(u.precio!!)}" },
    p.notas.takeIf { it.isNotBlank() },
).joinToString(" · ")

@Composable
private fun FilaPendiente(p: Producto, conProyecto: Boolean, vm: EmperatrizViewModel) {
    Row(
        Modifier.fillMaxWidth().clickable { vm.abrir(p.id) }.padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = false, onCheckedChange = { vm.comprar(p.id) })
        Column(Modifier.weight(1f)) {
            Text(p.nombre, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val det = detalle(p, conProyecto)
            if (det.isNotEmpty()) Text(det, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = { vm.ajustar(p.id, -1) }) { Text("−") }
        Text(Numeros.texto(p.cantidad), fontWeight = FontWeight.Bold)
        TextButton(onClick = { vm.ajustar(p.id, 1) }) { Text("+") }
    }
}

@Composable
private fun FilaComprado(p: Producto, vm: EmperatrizViewModel) {
    Row(
        Modifier.fillMaxWidth().clickable { vm.abrir(p.id) }.padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.nombre, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            val u = p.historial.lastOrNull()
            Text(
                listOfNotNull(
                    u?.fecha?.let { "comprado $it" },
                    if (p.historial.size > 1) "${p.historial.size} veces" else null,
                    u?.precio?.let { Numeros.dinero(it) },
                    u?.tienda,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = { vm.volverAComprar(p.id) }) { Text("Volver a comprar") }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/** Botón de texto con un menú desplegable. */
@Composable
fun Menu(titulo: String, opciones: List<Pair<String, () -> Unit>>, color: Color = Color.Unspecified) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { abierto = true }) { Text(titulo, color = color) }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (t, f) -> DropdownMenuItem(text = { Text(t) }, onClick = { abierto = false; f() }) }
        }
    }
}
