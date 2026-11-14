<p align="center"><img src="docs/carta.svg" alt="III · La Emperatriz" width="180"></p>

# III · La Emperatriz

Lista de compras para el **celular (Android)** y el **PC (página web)**: mercado, aseo, farmacia, **ferretería** y lo que haga falta, por **proyectos** (pintar la sala, el baño…). Habla con **Claude por JSON** en los dos sentidos y usa **Gemini con la búsqueda de Google** para encontrar precios (tienda, precio, fecha y enlace) y para explicar **cómo hacer un producto en casa**.

La Emperatriz provee la casa: lo que hay que comprar, dónde está más barato y para qué proyecto es.

## Qué hace (versión 0.1, fase 1)

- **Agregar escribiendo o dictando** (🎤, dictado de Google en español): «2 kg de arroz, leche y 3 tornillos para el proyecto baño». Entiende cantidades («1/2», «1 1/2», «dos», «media docena»), unidades (kg, lb, g, l, ml, und, galón, caja, bolsa, metro…), «x3» al final, `#Proyecto` o «(proyecto: Baño)». La sección la adivina por el nombre (tornillo → Ferretería, jabón → Aseo, acetaminofén → Farmacia).
- **✦ Ordenar con Gemini**: para textos largos o desordenados (un mensaje, una lista copiada), Gemini separa productos, cantidades, sección y proyecto.
- **Sin duplicados**: si agregas algo que ya está (mismo nombre, unidad, sección y proyecto) se **suma la cantidad**; si ya estaba comprado, **vuelve a la lista**.
- **Comprado**: la casilla lo marca; en el detalle puedes anotar **precio pagado y tienda** (queda en el historial).
- **Volver a comprar**: en «Comprados» o en «Lo de siempre» (lo que más compras), un toque lo devuelve a la lista con la cantidad de la última vez; si ya está pendiente, aumenta la cantidad. −/+ de a 1 (0,5 en kg/lb/l, 100 en g/ml).
- **Proyectos**: crea proyectos (con notas y de dónde vienen: el proyecto de la casa en Claude, El Emperador…), filtra la lista por proyecto y ve **cuánto llevas pagado y cuánto falta** según los precios encontrados.
- **Buscar precios** (detalle del producto): Gemini busca en Google precios actuales en tu ciudad y devuelve tienda, precio, presentación, **fecha del precio** (si la página la muestra), día de la consulta, enlace y notas (oferta, agotado). Se guardan en el producto («desde $18.500 en D1»).
- **Enlace de un producto**: compártele a La Emperatriz la página de un producto (desde el navegador o la app de la tienda). Gemini **lee la página**, identifica el producto exacto (marca, presentación), lo agrega y busca el mismo producto en otras tiendas.
- **¿Cómo hacerlo en casa?**: receta o procedimiento (ingredientes con cantidades, herramientas, pasos, tiempo, dificultad, costo aproximado y si sale más barato que comprarlo), con advertencias de seguridad. Si no es seguro o práctico (medicamentos, químicos peligrosos), lo dice. Los ingredientes se agregan a la lista con un toque.
- **Con Claude, en los dos sentidos**:
  - *Exportar → Para Claude*: resumen + JSON. Claude lo analiza o lo cruza con tus otros proyectos.
  - *Claude → La Emperatriz*: pídele a Claude «dame un JSON para La Emperatriz con…» y compártelo a la app (o pégalo). Acepta el JSON aunque venga con texto alrededor o dentro de ```json. Formato en [FORMATO.md](FORMATO.md).
- **Exportar**: lista con casillas para WhatsApp, respaldo JSON (archivo), hoja de cálculo CSV (Excel / Google Sheets).

## Página para el PC

`web/index.html` es una página sola (sin instalar nada): ábrela con doble clic en el PC.
- Lista, secciones, proyectos, comprado, volver a comprar, precios y historial, igual que en el celular.
- **Pasar datos**: en el celular *Exportar → Respaldo JSON* y en el PC «Abrir archivo…» (o pegar). Del PC al celular: «Descargar respaldo» y compartir el archivo a La Emperatriz. Al llegar un respaldo pregunta si **combinar** o **reemplazar**.
- **Copiar para Claude** igual que en el celular.
- **Buscar precios** con tu clave de Gemini (queda solo en ese navegador).
- La lista del PC se guarda en el navegador (localStorage).

También está publicada como página de Claude (para abrirla sin el archivo); ahí no hay búsqueda de precios ni descarga, porque el visor de Claude no deja salir a otros sitios.

## Lo que sigue

- **Fase 2 — sincronización en AWS (capa gratuita)**: la lista del celular y la del PC al día sin pasar archivos. Plan: API Gateway + Lambda + DynamoDB (cambios por producto con fecha, se mezclan igual que «Combinar»), con usuario de Cognito.
- Avisos de precio (si baja de cierto valor), escanear el código de barras, fotos de la factura para anotar precios.

## Instalar

**Sin el PC**: cada cambio en `main` se compila en GitHub Actions y el APK queda en [releases/ultima](https://github.com/davidsant7e/emperatriz-android/releases/tag/ultima) → descarga `la-emperatriz.apk` en el celular e instálalo.

**Con el PC**:

1. Abre la carpeta en **Android Studio** y deja que sincronice Gradle.
2. Conecta el celular con depuración USB y pulsa **Run**.
3. En la app: **⋮ → Ajustes → Clave de Gemini** (gratis en [aistudio.google.com/apikey](https://aistudio.google.com/apikey); se guarda **cifrada** con el Android Keystore). Pon tu **ciudad** para que los precios sean de ahí.

La llave de firma de prueba es la compartida de los Arcanos (`firma/`), así el APK del PC y uno compilado en GitHub se pueden instalar uno encima del otro.

## Privacidad

- A Gemini solo va lo que consultas: el nombre/notas del producto, tu ciudad y el enlace (si lo hay). La lista completa no sale del celular salvo cuando tú la exportas o compartes.
- Sin permisos: el dictado usa la app de Google; los archivos se comparten con el menú de Android.
- La lista se guarda en la memoria privada de la app (`lista.json`), sin copia en la nube (`allowBackup=false`): exporta el respaldo JSON.
- Los precios los encuentra Gemini en la web: pueden cambiar o no incluir promociones; confírmalos en la tienda.

## Cómo está hecho

- Kotlin + Jetpack Compose (Material 3), minSdk 26, compileSdk 37. Mismo esqueleto que El Emperador y El Carro (AGP 9.4.0, Kotlin 2.4.10, Gradle 9.6.0).
- `nucleo/` es Kotlin puro (sin Android) con pruebas:
  - `Modelos.kt`: producto, compra, precio, proyecto, `Datos` (también es el formato del respaldo).
  - `Pedido.kt`: lee texto dictado, JSON de Claude y respaldos; números, precios («$ 12.900») y unidades.
  - `Lista.kt`: agregar sin duplicar, comprar, volver a comprar, importar/combinar, costo por proyecto.
  - `Exportar.kt`: lista de texto, texto para Claude con JSON, CSV.
  - `Gemini.kt`: precios con `google_search` + `url_context`, «hacerlo en casa» y ordenar texto, con respuesta JSON (si el modelo no acepta herramientas con esquema, repite sin esquema).
- `datos/`: `Almacen.kt`, `Ajustes.kt`, `Boveda.kt` (clave cifrada), `Red.kt`.
- `ui/`: `MainActivity` (dictado, compartir, abrir archivos), `EmperatrizViewModel`, `Pantallas.kt`, `Dialogos.kt`.
- `web/index.html`: la página del PC (HTML + JS sin dependencias).

Pruebas del núcleo: `./gradlew test`.

Parte del mazo de [davidsant7e](https://github.com/davidsant7e).
