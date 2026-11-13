# Formato JSON de La Emperatriz

Lo que La Emperatriz acepta (compartido a la app, pegado o abierto como archivo), y lo que exporta.

## Lo mínimo (lo que arma Claude)

```json
{
  "proyecto": "Baño",
  "productos": [
    {"nombre": "Silicona antihongos", "cantidad": 2, "unidad": "tubo", "seccion": "ferreteria", "notas": "transparente"},
    {"nombre": "Grifo monocontrol", "seccion": "ferreteria", "enlace": "https://…"},
    "3 kg de papa"
  ]
}
```

- Solo `nombre` es obligatorio. Un producto también puede ser un texto («3 kg de papa»).
- `cantidad` (número, por defecto 1), `unidad` (kg, g, lb, l, ml, und, paquete, caja, bolsa, docena, m, cm, galón, rollo, tarro, botella, lata, bulto, frasco, tubo…).
- `seccion`: `mercado`, `aseo`, `farmacia`, `ferreteria`, `hogar`, `tecnologia`, `otro`. Si falta, la app la adivina por el nombre.
- `proyecto` en cada producto o una vez arriba para todos. Los proyectos nuevos se crean solos.
- `notas`, `enlace` (página del producto).
- También vale un arreglo suelto `[...]`, y las llaves `items` o `lista` en vez de `productos`.
- El JSON puede venir con texto alrededor o dentro de ```json … ```.

## Cómo se mezcla

- Si trae `id` de un producto que ya existe, lo reemplaza (es el mismo, editado en otro lado).
- Si ya hay uno igual (nombre sin tildes/mayúsculas + unidad + sección + proyecto): pendiente → suma la cantidad; comprado → vuelve a la lista con la cantidad nueva.
- Si no, lo agrega.

## Respaldo completo

Lo que exporta la app («Respaldo JSON») y la página del PC: `{"app":"la-emperatriz","version":1,"actualizado":"AAAA-MM-DD","proyectos":[…],"productos":[…]}`. Cada producto además lleva `id`, `estado` (`pendiente`/`comprado`), `creado`, `historial` (`fecha`, `cantidad`, `precio`, `tienda`) y `precios` (`tienda`, `precio`, `moneda`, `fecha`, `consultado`, `enlace`, `presentacion`, `nota`). Al abrir un respaldo, la app pregunta si combinar o reemplazar.

## Para pedírselo a Claude

> «Hazme un JSON para La Emperatriz con lo que necesito para [arreglo/receta/plan]. Usa el formato de FORMATO.md.»
