package com.davidsant7e.emperatriz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.davidsant7e.emperatriz.nucleo.Exportar
import com.davidsant7e.emperatriz.nucleo.Gemini
import com.davidsant7e.emperatriz.nucleo.Lista
import com.davidsant7e.emperatriz.nucleo.Numeros
import com.davidsant7e.emperatriz.nucleo.Pedido
import com.davidsant7e.emperatriz.nucleo.Precio
import com.davidsant7e.emperatriz.nucleo.Producto
import com.davidsant7e.emperatriz.nucleo.Secciones
import com.davidsant7e.emperatriz.nucleo.Texto
import com.davidsant7e.emperatriz.nucleo.Unidades

@Composable
private fun desplazable(): Modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())

@Composable
private fun Titulo(t: String) = Text(t, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

// ---------------- Producto ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoProducto(p: Producto, e: Estado, vm: EmperatrizViewModel, a: Acciones) {
    var nombre by remember(p.id) { mutableStateOf(p.nombre) }
    var cantidad by remember(p.id) { mutableStateOf(Numeros.texto(p.cantidad)) }
    var unidad by remember(p.id) { mutableStateOf(p.unidad) }
    var seccion by remember(p.id) { mutableStateOf(p.seccion) }
    var proyecto by remember(p.id) { mutableStateOf(p.proyecto) }
    var notas by remember(p.id) { mutableStateOf(p.notas) }
    var enlace by remember(p.id) { mutableStateOf(p.enlace.orEmpty()) }
    var precio by remember(p.id) { mutableStateOf("") }
    var tienda by remember(p.id) { mutableStateOf(p.mejorPrecio?.tienda.orEmpty()) }

    fun editado() = p.copy(
        nombre = nombre.trim().ifEmpty { p.nombre },
        cantidad = Numeros.cantidad(cantidad)?.takeIf { it > 0 } ?: p.cantidad,
        unidad = Unidades.normalizar(unidad),
        seccion = seccion,
        proyecto = proyecto.trim(),
        notas = notas.trim(),
        enlace = enlace.trim().takeIf { it.startsWith("http") },
    )

    AlertDialog(
        onDismissRequest = { vm.abrir(null) },
        title = { Text(if (p.comprado) "${p.nombre} ✓" else p.nombre) },
        text = {
            Column(desplazable(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Producto") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(cantidad, { cantidad = it }, label = { Text("Cantidad") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(unidad, { unidad = it }, label = { Text("Unidad") }, modifier = Modifier.weight(1f), singleLine = true,
                        placeholder = { Text("kg, und, galón…") })
                }
                Text("Sección", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Secciones.CLAVES.forEach { s -> FilterChip(selected = seccion == s, onClick = { seccion = s }, label = { Text(Secciones.nombre(s)) }) }
                }
                OutlinedTextField(proyecto, { proyecto = it }, label = { Text("Proyecto (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                val otros = e.datos.nombresProyectos.filter { Texto.simple(it) != Texto.simple(proyecto) }
                if (otros.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    otros.forEach { n -> FilterChip(selected = false, onClick = { proyecto = n }, label = { Text(n) }) }
                }
                OutlinedTextField(notas, { notas = it }, label = { Text("Notas (marca, medida, referencia)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(enlace, { enlace = it }, label = { Text("Enlace del producto") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (enlace.startsWith("http")) TextButton(onClick = { a.abrirEnlace(enlace) }) { Text("Abrir la página") }

                Titulo("Gemini")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.guardar(editado()); vm.buscarPrecios(p.id) }, enabled = e.ocupado == null) { Text("Buscar precios") }
                    OutlinedButton(onClick = { vm.guardar(editado()); vm.casero(p.id) }, enabled = e.ocupado == null) { Text("¿Cómo hacerlo en casa?") }
                }
                e.ocupado?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                if (p.precios.isNotEmpty()) {
                    Titulo("Precios encontrados")
                    p.precios.sortedBy { it.precio ?: Double.MAX_VALUE }.forEach { FilaPrecio(it, a) }
                }

                Titulo(if (p.comprado) "Comprado" else "Lo compré")
                if (p.comprado) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.volverAComprar(p.id); vm.abrir(null) }) { Text("Volver a comprar") }
                        TextButton(onClick = { vm.desmarcar(p.id) }) { Text("Desmarcar") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(precio, { precio = it }, label = { Text("Precio pagado") }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(tienda, { tienda = it }, label = { Text("Tienda") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Button(onClick = {
                        vm.guardar(editado())
                        vm.comprar(p.id, Numeros.precio(precio), tienda)
                        vm.abrir(null)
                    }) { Text("Marcar comprado") }
                }
                if (p.historial.isNotEmpty()) {
                    Titulo("Historial")
                    p.historial.reversed().forEach { c ->
                        Text(
                            listOfNotNull(c.fecha, Unidades.texto(c.cantidad, p.unidad), c.precio?.let { Numeros.dinero(it) }, c.tienda).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                TextButton(onClick = { vm.quitar(p.id) }) { Text("Quitar de la lista", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { vm.guardar(editado()); vm.abrir(null) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { vm.abrir(null) }) { Text("Cerrar") } },
    )
}

@Composable
private fun FilaPrecio(pr: Precio, a: Acciones) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pr.tienda + ": " + (pr.precio?.let { Numeros.dinero(it, pr.moneda) } ?: "sin precio") +
                    (pr.presentacion?.let { " · $it" } ?: ""),
                Modifier.weight(1f), fontWeight = FontWeight.Medium,
            )
            pr.enlace?.let { url -> TextButton(onClick = { a.abrirEnlace(url) }) { Text("Ver") } }
        }
        Text(
            listOfNotNull(pr.fecha?.let { "precio del $it" }, "consultado ${pr.consultado}", pr.nota).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------------- Precios (resultado de la búsqueda) ----------------

@Composable
fun DialogoPrecios(p: Producto?, b: Gemini.Busqueda, vm: EmperatrizViewModel, a: Acciones) {
    AlertDialog(
        onDismissRequest = vm::cerrarBusqueda,
        title = { Text(b.producto) },
        text = {
            Column(desplazable(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                b.descripcion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                b.resumen?.let { Text(it, fontWeight = FontWeight.Medium) }
                if (b.precios.isEmpty()) Text("Gemini no encontró precios publicados para este producto. Prueba con un nombre más concreto (marca, tamaño) o con el enlace de la tienda.")
                b.precios.forEach { FilaPrecio(it, a) }
                if (b.fuentes.isNotEmpty()) {
                    Titulo("Fuentes")
                    b.fuentes.take(8).forEach { f -> TextButton(onClick = { a.abrirEnlace(f.enlace) }) { Text(f.titulo, maxLines = 1) } }
                }
                Text(
                    "Precios hallados por Gemini con la búsqueda de Google: pueden cambiar o no incluir promociones; confírmalos en la tienda." +
                        if (p != null && b.precios.isNotEmpty()) " Quedaron guardados en «${p.nombre}»." else "",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = vm::cerrarBusqueda) { Text("Listo") } },
    )
}

// ---------------- Hacerlo en casa ----------------

@Composable
fun DialogoReceta(r: Gemini.Receta, vm: EmperatrizViewModel, a: Acciones) {
    val elegidos = remember(r) { mutableStateListOf<Gemini.Ingrediente>().apply { addAll(r.ingredientes) } }
    AlertDialog(
        onDismissRequest = vm::cerrarReceta,
        title = { Text(r.titulo) },
        text = {
            Column(desplazable(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!r.factible) Text("No conviene hacerlo en casa.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                val meta = listOfNotNull(r.dificultad?.let { "Dificultad: $it" }, r.tiempo?.let { "Tiempo: $it" }, r.costo?.let { "Costo aprox.: $it" })
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                r.comparacion?.let { Text(it, fontWeight = FontWeight.Medium) }
                if (r.ingredientes.isNotEmpty()) {
                    Titulo("Necesitas")
                    r.ingredientes.forEach { i ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = i in elegidos, onCheckedChange = { c -> if (c) elegidos.add(i) else elegidos.remove(i) })
                            Text("${Unidades.texto(i.cantidad, i.unidad)} ${i.nombre} · ${Secciones.nombre(i.seccion)}")
                        }
                    }
                }
                if (r.herramientas.isNotEmpty()) {
                    Titulo("Herramientas")
                    Text(r.herramientas.joinToString(", "))
                }
                if (r.pasos.isNotEmpty()) {
                    Titulo("Pasos")
                    r.pasos.forEachIndexed { n, paso -> Text("${n + 1}. $paso") }
                }
                if (r.advertencias.isNotEmpty()) {
                    Titulo("Cuidado")
                    r.advertencias.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (r.fuentes.isNotEmpty()) {
                    Titulo("Fuentes")
                    r.fuentes.take(6).forEach { f -> TextButton(onClick = { a.abrirEnlace(f.enlace) }) { Text(f.titulo, maxLines = 1) } }
                }
            }
        },
        confirmButton = {
            if (r.factible && elegidos.isNotEmpty()) {
                Button(onClick = { vm.agregarIngredientes(elegidos.toList()); vm.cerrarReceta() }) { Text("Agregar ${elegidos.size} a la lista") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    a.compartirTexto(r.titulo, textoReceta(r))
                }) { Text("Compartir") }
                TextButton(onClick = vm::cerrarReceta) { Text("Cerrar") }
            }
        },
    )
}

fun textoReceta(r: Gemini.Receta) = buildString {
    append(r.titulo).append('\n')
    listOfNotNull(r.dificultad, r.tiempo, r.costo).takeIf { it.isNotEmpty() }?.let { append(it.joinToString(" · ")).append('\n') }
    if (r.ingredientes.isNotEmpty()) {
        append("\nNecesitas:\n"); r.ingredientes.forEach { append("- ").append(Unidades.texto(it.cantidad, it.unidad)).append(' ').append(it.nombre).append('\n') }
    }
    if (r.pasos.isNotEmpty()) {
        append("\nPasos:\n"); r.pasos.forEachIndexed { i, p -> append(i + 1).append(". ").append(p).append('\n') }
    }
    if (r.advertencias.isNotEmpty()) {
        append("\nCuidado:\n"); r.advertencias.forEach { append("- ").append(it).append('\n') }
    }
}.trim()

// ---------------- Importar / exportar ----------------

@Composable
fun DialogoRespaldo(r: Pedido.Resultado, vm: EmperatrizViewModel) {
    AlertDialog(
        onDismissRequest = vm::cancelarImporte,
        title = { Text("Llegó un respaldo completo") },
        text = {
            Text(
                "Trae ${r.productos.size} productos y ${r.proyectos.size} proyectos. «Combinar» suma lo nuevo y actualiza lo que ya tienes " +
                    "(lo normal al pasar del PC al celular). «Reemplazar» deja la lista exactamente como en el respaldo.",
            )
        },
        confirmButton = { Button(onClick = { vm.confirmarImporte(false) }) { Text("Combinar") } },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.confirmarImporte(true) }) { Text("Reemplazar todo", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = vm::cancelarImporte) { Text("Cancelar") }
            }
        },
    )
}

@Composable
fun DialogoImportar(vm: EmperatrizViewModel, a: Acciones, cerrar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Importar") },
        text = {
            Column(desplazable(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Pega lo que te dio Claude (el JSON, aunque venga con texto alrededor), un respaldo del PC o una lista " +
                        "(una cosa por línea o separadas por comas).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(texto, { texto = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 10, label = { Text("Texto o JSON") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { a.pegar()?.let { texto = it } }) { Text("Pegar") }
                    OutlinedButton(onClick = { a.abrirArchivo(); cerrar() }) { Text("Abrir archivo…") }
                }
            }
        },
        confirmButton = { Button(onClick = { if (vm.agregarTexto(texto)) cerrar() }, enabled = texto.isNotBlank()) { Text("Importar") } },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoExportar(e: Estado, vm: EmperatrizViewModel, a: Acciones, cerrar: () -> Unit) {
    val hoy = vm.hoy()
    val d = e.datos
    val filtro = listOfNotNull(e.seccion?.let { Secciones.nombre(it) }, e.proyecto?.ifBlank { "General" }).joinToString(" · ")
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Exportar / enviar") },
        text = {
            Column(desplazable(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Titulo("Para Claude")
                Text("Resumen + JSON: Claude lo analiza, lo cruza con tus otros proyectos y te devuelve un JSON que importas aquí.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { a.compartirTexto("Lista para Claude", Exportar.paraClaude(d, hoy)); cerrar() }) { Text("Enviar") }
                    OutlinedButton(onClick = { a.copiar(Exportar.paraClaude(d, hoy)); cerrar() }) { Text("Copiar") }
                }
                Titulo("Lista para compartir")
                Text("Texto con casillas por sección (WhatsApp, notas)" + if (filtro.isNotEmpty()) ", solo lo filtrado: $filtro." else ".", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { a.compartirTexto("Lista de compras", Exportar.texto(d, e.seccion, e.proyecto)); cerrar() }) { Text("Enviar") }
                    OutlinedButton(onClick = { a.copiar(Exportar.texto(d, e.seccion, e.proyecto)); cerrar() }) { Text("Copiar") }
                }
                Titulo("Archivos")
                Text("El respaldo JSON abre en la página del PC y en otro celular con La Emperatriz.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { a.compartirArchivo(archivo("respaldo", hoy, "json"), "application/json", d.aJson()); cerrar() }) { Text("Respaldo JSON") }
                    OutlinedButton(onClick = { a.compartirArchivo(archivo("lista", hoy, "csv"), "text/csv", Exportar.csv(d)); cerrar() }) { Text("Hoja (CSV)") }
                }
            }
        },
        confirmButton = { TextButton(onClick = cerrar) { Text("Cerrar") } },
    )
}

// ---------------- Proyectos ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoProyectos(e: Estado, vm: EmperatrizViewModel, cerrar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    var origen by remember { mutableStateOf("") }
    val d = e.datos
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Proyectos") },
        text = {
            Column(desplazable(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Agrupa compras de un arreglo o plan (pintar la sala, el baño, una fiesta). Toca uno para ver solo sus compras y lo que lleva gastado.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val activos = d.nombresProyectos
                activos.forEach { n ->
                    val ps = d.productos.filter { Texto.simple(it.proyecto) == Texto.simple(n) }
                    val c = Lista.costo(d, n)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            TextButton(onClick = { vm.filtrarProyecto(n); cerrar() }) { Text(n, fontWeight = FontWeight.Bold) }
                            Text(
                                "${ps.count { !it.comprado }} pendientes · ${ps.count { it.comprado }} comprados · pagado ${Numeros.dinero(c.pagado, e.lugar.moneda)}",
                                Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { vm.archivarProyecto(n, true) }) { Text("Archivar") }
                    }
                }
                val archivados = d.proyectos.filter { it.archivado }
                if (archivados.isNotEmpty()) {
                    Titulo("Archivados")
                    archivados.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.nombre, Modifier.weight(1f))
                            TextButton(onClick = { vm.archivarProyecto(p.nombre, false) }) { Text("Restaurar") }
                        }
                    }
                }
                Titulo("Nuevo proyecto")
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(notas, { notas = it }, label = { Text("Notas (medidas, colores, qué falta)") }, modifier = Modifier.fillMaxWidth())
                Text("Viene de", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Proyecto de la casa (Claude)", "El Emperador", "Otro").forEach { o ->
                        FilterChip(selected = origen == o, onClick = { origen = if (origen == o) "" else o }, label = { Text(o) })
                    }
                }
                Button(onClick = { vm.agregarProyecto(nombre, notas, origen); nombre = ""; notas = ""; origen = "" }, enabled = nombre.isNotBlank()) { Text("Crear") }
            }
        },
        confirmButton = { TextButton(onClick = cerrar) { Text("Cerrar") } },
    )
}

// ---------------- Ajustes ----------------

/** «Avisarme al terminar de conducir»: interruptor, permisos y el modo Conducción. */
@Composable
private fun AjustesConduccion(c: EstadoConduccion, vm: EmperatrizViewModel, a: Acciones) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Avisarme al terminar de conducir", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(checked = c.activado, onCheckedChange = { si ->
            vm.avisarAlConducir(si)
            if (si && !c.notificaciones) a.pedirNotificaciones()
        })
    }
    Text(
        "Cuando se desactive el modo Conducción (Ajustes → Modos), te llega la notificación «¿Qué debes comprar?» con lo " +
            "pendiente; al tocarla se abre la lista. No avisa si no hay nada pendiente, ni más de una vez cada 30 minutos.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!c.activado) return
    EstadoPermiso(
        "Acceso a No molestar", c.accesoNoMolestar,
        "Para enterarse, con la app cerrada, de que un modo se apagó. La Emperatriz no cambia tus modos.",
        "Dar acceso", a.pedirAccesoNoMolestar,
    )
    EstadoPermiso("Notificaciones", c.notificaciones, "Para mostrar el aviso.", "Permitir", a.pedirNotificaciones)
    Text(
        if (c.modoMarcado) "Modo Conducción reconocido: los demás modos (Hora de dormir…) no avisan."
        else "Ahora avisa al terminar cualquier modo (de 2 minutos a 6 horas). Si usas otros modos, activa el modo Conducción y toca «Este es mi modo Conducción».",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::marcarModoConduccion, enabled = c.modoActivo && c.accesoNoMolestar) { Text("Este es mi modo Conducción") }
        if (c.modoMarcado) TextButton(onClick = vm::olvidarModoConduccion) { Text("Olvidar") }
    }
    TextButton(onClick = vm::probarAvisoConduccion) { Text("Probar el aviso") }
}

@Composable
private fun EstadoPermiso(nombre: String, concedido: Boolean, para: String, boton: String, pedir: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text((if (concedido) "✓ " else "✗ ") + nombre + (if (concedido) ": concedido" else ": falta"),
                color = if (concedido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text(para, style = MaterialTheme.typography.labelSmall)
        }
        if (!concedido) Button(onClick = pedir) { Text(boton) }
    }
}

@Composable
fun DialogoAjustes(e: Estado, vm: EmperatrizViewModel, a: Acciones, cerrar: () -> Unit) {
    var clave by remember { mutableStateOf("") }
    var modelo by remember { mutableStateOf(e.modelo) }
    var pais by remember { mutableStateOf(e.lugar.pais) }
    var ciudad by remember { mutableStateOf(e.lugar.ciudad) }
    var moneda by remember { mutableStateOf(e.lugar.moneda) }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Ajustes") },
        text = {
            Column(desplazable(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Titulo("Clave de Gemini")
                Text(
                    if (e.hayClave) "Guardada (cifrada con el Android Keystore)." else "Sin clave: crea una gratis en aistudio.google.com/apikey.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(clave, { clave = it }, label = { Text(if (e.hayClave) "Cambiar clave" else "Pegar clave") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.guardarClave(clave); clave = "" }, enabled = clave.isNotBlank()) { Text("Guardar clave") }
                    OutlinedButton(onClick = { a.pegar()?.let { clave = it.trim() } }) { Text("Pegar") }
                    if (e.hayClave) TextButton(onClick = vm::borrarClave) { Text("Borrar") }
                }
                TextButton(onClick = { a.abrirEnlace("https://aistudio.google.com/apikey") }) { Text("Abrir aistudio.google.com/apikey") }
                OutlinedTextField(modelo, { modelo = it }, label = { Text("Modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Titulo("Dónde compras (para los precios)")
                OutlinedTextField(ciudad, { ciudad = it }, label = { Text("Ciudad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pais, { pais = it }, label = { Text("País") }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(moneda, { moneda = it }, label = { Text("Moneda") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text(
                    "A Gemini solo va el producto que consultas, la ciudad y el enlace (si lo hay). La lista se guarda solo en el celular.",
                    style = MaterialTheme.typography.labelSmall,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                AjustesConduccion(e.conduccion, vm, a)
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.guardarAjustes(modelo, Gemini.Lugar(pais.ifBlank { "Colombia" }, ciudad, moneda.ifBlank { "COP" }))
                cerrar()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
