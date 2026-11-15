package com.davidsant7e.emperatriz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davidsant7e.emperatriz.datos.Ajustes
import com.davidsant7e.emperatriz.datos.Almacen
import com.davidsant7e.emperatriz.datos.Boveda
import com.davidsant7e.emperatriz.datos.Red
import com.davidsant7e.emperatriz.nucleo.Datos
import com.davidsant7e.emperatriz.nucleo.Exportar
import com.davidsant7e.emperatriz.nucleo.Gemini
import com.davidsant7e.emperatriz.nucleo.Lista
import com.davidsant7e.emperatriz.nucleo.Pedido
import com.davidsant7e.emperatriz.nucleo.Producto
import com.davidsant7e.emperatriz.registro.Registro
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Estado(
    val datos: Datos = Datos(),
    /** Lo que está escrito (o dictado) en el campo para agregar. */
    val entrada: String = "",
    val seccion: String? = null,
    val proyecto: String? = null,
    /** Proyecto que se asigna a lo nuevo que se agrega ("" = ninguno). */
    val proyectoNuevo: String = "",
    val verComprados: Boolean = false,
    /** Producto abierto en el detalle. */
    val abierto: String? = null,
    /** Texto de lo que se está haciendo con Gemini (null = nada). */
    val ocupado: String? = null,
    val busqueda: Pair<String, Gemini.Busqueda>? = null,
    /** Receta de «hacerlo en casa»: (id del producto, receta). */
    val receta: Pair<String, Gemini.Receta>? = null,
    /** Lo que llegó para importar y espera confirmación (respaldo completo: combinar o reemplazar). */
    val porImportar: Pedido.Resultado? = null,
    val hayClave: Boolean = false,
    val modelo: String = Gemini.MODELO,
    val lugar: Gemini.Lugar = Gemini.Lugar(),
    val aviso: String? = null,
)

class EmperatrizViewModel(app: Application) : AndroidViewModel(app) {
    private val almacen = Almacen(app)
    private val ajustes = Ajustes(app)
    private val gemini = Gemini(Red::postJson)

    private val _estado = MutableStateFlow(
        Estado(
            datos = almacen.cargar(hoy()),
            proyectoNuevo = ajustes.proyectoActual,
            hayClave = Boveda.hay(app),
            modelo = ajustes.modelo,
            lugar = ajustes.lugar,
        ),
    )
    val estado: StateFlow<Estado> = _estado

    private val ctx get() = getApplication<Application>()

    fun hoy(): String = LocalDate.now().toString()

    private fun cambiar(f: (Datos) -> Datos) {
        val nuevo = f(_estado.value.datos)
        _estado.update { it.copy(datos = nuevo) }
        viewModelScope.launch(Dispatchers.IO) { almacen.guardar(nuevo) }
    }

    private fun avisar(t: String) = _estado.update { it.copy(aviso = t) }
    fun cerrarAviso() = _estado.update { it.copy(aviso = null) }

    // ---- Agregar ----

    fun escribir(t: String) = _estado.update { it.copy(entrada = t) }

    /** Dictado: se suma a lo que ya está escrito. */
    fun dictado(t: String) = _estado.update { e -> e.copy(entrada = listOf(e.entrada.trim(), t.trim()).filter { it.isNotEmpty() }.joinToString(", ")) }

    /** Agrega lo que está en el campo y lo limpia si sirvió. */
    fun agregarEntrada() {
        if (agregarTexto(_estado.value.entrada)) escribir("")
    }

    fun ordenarEntrada() = ordenarConGemini(_estado.value.entrada) { escribir("") }

    /** Texto escrito o dictado. Si es un JSON (de Claude o un respaldo) se importa. */
    fun agregarTexto(texto: String): Boolean {
        val e = _estado.value
        val r = Pedido.leer(texto, hoy(), proyecto = e.proyectoNuevo, seccion = e.seccion)
        if (r.productos.isEmpty() && r.proyectos.isEmpty()) {
            avisar("No encontré productos en el texto"); return false
        }
        if (Pedido.extraerJson(texto) != null && r.respaldo) {
            _estado.update { it.copy(porImportar = r) }; return true
        }
        val imp = Lista.importar(e.datos, r, hoy())
        cambiar { imp.datos }
        Registro.evento("LISTA", "agregar", "agregados" to imp.agregados, "actualizados" to imp.actualizados, "json" to (Pedido.extraerJson(texto) != null))
        avisar(resumen(imp.agregados, imp.actualizados))
        return true
    }

    private fun resumen(agregados: Int, actualizados: Int) = listOfNotNull(
        agregados.takeIf { it > 0 }?.let { if (it == 1) "1 producto agregado" else "$it productos agregados" },
        actualizados.takeIf { it > 0 }?.let { if (it == 1) "1 actualizado" else "$it actualizados" },
    ).joinToString(" · ").ifEmpty { "Sin cambios" }

    /** Texto libre ordenado por Gemini (sección, proyecto y notas). */
    fun ordenarConGemini(texto: String, listo: () -> Unit) {
        if (texto.isBlank()) return
        conGemini("Gemini está ordenando la lista…") { clave ->
            val e = _estado.value
            val ps = gemini.ordenar(texto, e.datos.nombresProyectos, hoy(), clave, e.modelo)
                .map { if (it.proyecto.isBlank() && e.proyectoNuevo.isNotBlank()) it.copy(proyecto = e.proyectoNuevo) else it }
            withContext(Dispatchers.Main) {
                if (ps.isEmpty()) avisar("Gemini no encontró productos en el texto")
                else {
                    val imp = Lista.importar(_estado.value.datos, Pedido.Resultado(ps), hoy())
                    cambiar { imp.datos }
                    avisar(resumen(imp.agregados, imp.actualizados))
                    listo()
                }
            }
        }
    }

    /** Lo que llega compartido desde otra app: un enlace de producto, un JSON o una lista de texto. */
    fun recibir(texto: String) {
        val t = texto.trim()
        val enlace = Regex("""https?://\S+""").find(t)?.value
        if (enlace != null && Pedido.extraerJson(t) == null && t.length - enlace.length < 200) agregarDesdeEnlace(enlace, t.replace(enlace, "").trim())
        else agregarTexto(t)
    }

    /** Un producto a partir de su página: Gemini la lee, lo identifica y busca precios en otras tiendas. */
    fun agregarDesdeEnlace(enlace: String, descripcion: String = "") {
        conGemini("Leyendo la página y buscando precios…") { clave ->
            val e = _estado.value
            val b = gemini.buscarPrecios(descripcion, "", enlace, e.lugar, hoy(), clave, e.modelo)
            val p = Producto(
                nombre = b.producto.ifBlank { descripcion.ifBlank { "Producto del enlace" } },
                seccion = e.seccion ?: Pedido.adivinar(b.producto),
                proyecto = e.proyectoNuevo,
                notas = b.descripcion.orEmpty(),
                enlace = enlace,
                creado = hoy(),
                precios = b.precios.take(12),
            )
            withContext(Dispatchers.Main) {
                cambiar { Lista.agregar(it, listOf(p), hoy()) }
                val id = _estado.value.datos.productos.firstOrNull { it.clave == p.clave }?.id ?: p.id
                _estado.update { it.copy(busqueda = id to b) }
            }
        }
    }

    fun confirmarImporte(reemplazar: Boolean) {
        val r = _estado.value.porImportar ?: return
        val imp = Lista.importar(_estado.value.datos, r, hoy(), reemplazar)
        cambiar { imp.datos }
        _estado.update { it.copy(porImportar = null) }
        avisar(if (reemplazar) "Lista reemplazada: ${imp.datos.productos.size} productos" else resumen(imp.agregados, imp.actualizados))
    }

    fun cancelarImporte() = _estado.update { it.copy(porImportar = null) }

    // ---- Lista ----

    fun comprar(id: String, precio: Double? = null, tienda: String? = null) = cambiar { Lista.comprar(it, id, hoy(), precio, tienda) }
    fun desmarcar(id: String) = cambiar { Lista.desmarcar(it, id, hoy()) }
    fun volverAComprar(id: String) {
        cambiar { Lista.volverAComprar(it, id, hoy()) }
        _estado.value.datos.productos.firstOrNull { it.id == id }?.let { avisar("${Exportar.linea(it)}: de nuevo en la lista") }
    }
    fun ajustar(id: String, signo: Int) = cambiar { Lista.ajustar(it, id, hoy(), signo) }
    fun guardar(p: Producto) = cambiar { Lista.editar(it, p, hoy()) }
    fun quitar(id: String) {
        cambiar { Lista.quitar(it, id, hoy()) }
        _estado.update { it.copy(abierto = null) }
    }
    fun borrarComprados() {
        cambiar { Lista.limpiarComprados(it, hoy()) }
        avisar("Comprados borrados")
    }

    fun abrir(id: String?) = _estado.update { it.copy(abierto = id) }
    fun filtrarSeccion(s: String?) = _estado.update { it.copy(seccion = s) }
    fun filtrarProyecto(p: String?) = _estado.update { it.copy(proyecto = p) }
    fun verComprados(v: Boolean) = _estado.update { it.copy(verComprados = v) }
    fun proyectoNuevo(p: String) {
        ajustes.proyectoActual = p
        _estado.update { it.copy(proyectoNuevo = p) }
    }

    // ---- Proyectos ----

    fun agregarProyecto(nombre: String, notas: String, origen: String) = cambiar { Lista.agregarProyecto(it, nombre, notas, origen, hoy()) }
    fun archivarProyecto(nombre: String, archivado: Boolean) = cambiar { Lista.archivarProyecto(it, nombre, archivado, hoy()) }

    // ---- Gemini ----

    fun buscarPrecios(id: String) {
        val p = _estado.value.datos.productos.firstOrNull { it.id == id } ?: return
        conGemini("Buscando precios de ${p.nombre}…") { clave ->
            val e = _estado.value
            val nombre = listOf(Exportar.linea(p.copy(proyecto = "", notas = "")), p.notas).filter { it.isNotBlank() }.joinToString(" · ")
            val b = gemini.buscarPrecios(nombre, p.notas, p.enlace, e.lugar, hoy(), clave, e.modelo)
            withContext(Dispatchers.Main) {
                Registro.evento("GEMINI", "precios", "precios" to b.precios.size, "fuentes" to b.fuentes.size, "con_enlace" to (p.enlace != null), "modelo" to e.modelo)
                if (b.precios.isNotEmpty()) cambiar { Lista.guardarPrecios(it, id, b.precios, hoy()) }
                _estado.update { it.copy(busqueda = id to b) }
            }
        }
    }

    fun cerrarBusqueda() = _estado.update { it.copy(busqueda = null) }

    fun casero(id: String) {
        val p = _estado.value.datos.productos.firstOrNull { it.id == id } ?: return
        conGemini("Buscando cómo hacer ${p.nombre} en casa…") { clave ->
            val e = _estado.value
            val r = gemini.casero(p.nombre, p.notas, e.lugar, clave, e.modelo)
            Registro.evento("GEMINI", "casero", "factible" to r.factible, "ingredientes" to r.ingredientes.size, "pasos" to r.pasos.size)
            withContext(Dispatchers.Main) { _estado.update { it.copy(receta = id to r) } }
        }
    }

    fun cerrarReceta() = _estado.update { it.copy(receta = null) }

    /** Agrega a la lista los ingredientes elegidos de la receta, en el proyecto del producto. */
    fun agregarIngredientes(ingredientes: List<Gemini.Ingrediente>) {
        val (id, r) = _estado.value.receta ?: return
        val origen = _estado.value.datos.productos.firstOrNull { it.id == id }
        val ps = ingredientes.map {
            Producto(
                nombre = it.nombre, cantidad = it.cantidad, unidad = it.unidad, seccion = it.seccion,
                proyecto = origen?.proyecto.orEmpty(), notas = "para ${r.titulo.lowercase()}", creado = hoy(),
            )
        }
        val imp = Lista.importar(_estado.value.datos, Pedido.Resultado(ps), hoy())
        cambiar { imp.datos }
        avisar(resumen(imp.agregados, imp.actualizados))
    }

    private fun conGemini(texto: String, trabajo: suspend (String?) -> Unit) {
        if (_estado.value.ocupado != null) return
        if (!_estado.value.hayClave) {
            avisar("Primero pon tu clave de Gemini en ⚙ Ajustes"); return
        }
        _estado.update { it.copy(ocupado = texto) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                trabajo(Boveda.leer(ctx))
            } catch (e: IOException) {
                Registro.error("GEMINI", e)
                withContext(Dispatchers.Main) { avisar(e.message ?: "No se pudo conectar con Gemini") }
            } catch (e: Exception) {
                Registro.error("GEMINI", e)
                withContext(Dispatchers.Main) { avisar("Error: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                _estado.update { it.copy(ocupado = null) }
            }
        }
    }

    // ---- Ajustes ----

    fun guardarClave(clave: String) {
        if (clave.isBlank()) return
        Boveda.guardar(ctx, clave.trim())
        _estado.update { it.copy(hayClave = true) }
        avisar("Clave guardada (cifrada)")
    }

    fun borrarClave() {
        Boveda.borrar(ctx)
        _estado.update { it.copy(hayClave = false) }
    }

    fun guardarAjustes(modelo: String, lugar: Gemini.Lugar) {
        ajustes.modelo = modelo
        ajustes.lugar = lugar
        _estado.update { it.copy(modelo = ajustes.modelo, lugar = ajustes.lugar) }
    }
}
