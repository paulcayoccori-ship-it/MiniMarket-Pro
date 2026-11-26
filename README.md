# MiniMarket Pro

Sistema de escritorio para minimarkets y bodegas: punto de venta rápido con lector de código de barras,
productos con código o a granel (por kilo), inventario con kardex, compras, clientes y reportes.

**Tecnologías:** Java 17 · Spring Boot 3.3 · JavaFX 21 · AtlantaFX · SQLite · Maven

## Requisitos

- JDK 17 o superior (probado con Amazon Corretto 17).
- Internet la primera vez, para que Maven descargue las dependencias.
- No necesita servidor de base de datos: usa el archivo `minimarket.db` (SQLite).

## Cómo ejecutarlo

macOS / Linux:

```bash
./mvnw javafx:run
```

Windows:

```bash
mvnw.cmd javafx:run
```

Pruebas automáticas:

```bash
./mvnw test
```

## Módulos

- **Punto de venta (Nueva venta)**
  - Catálogo con fotos, categorías y buscador. El lector de código de barras escribe en el buscador y agrega el producto al instante.
  - Carrito con teclado numérico: cantidad (acepta decimales en productos por kilo), descuento % y cambio de precio.
  - **Pagar:** nota de venta o boleta; cliente genérico o por DNI (si no existe, se registra en el momento); pago en efectivo (con vuelto), tarjeta o Yape.
  - **Pago rápido:** cobra el monto exacto en efectivo con un clic.
  - Atajos: `F4` buscar, `F9` pagar, `F10` pago rápido, `Esc` volver.
- **Productos**
  - Con código de barras (se escanea o se escribe) o a granel / sin código (código interno automático `GR-0001`).
  - Unidad de venta: Unidad, Kilo, o unidades nuevas como Six pack o Caja.
  - Precio de compra y de venta con cálculo de ganancia, stock inicial, categoría (se puede crear desde el mismo formulario) e imagen opcional.
- **Inventario:** stock, alertas de stock bajo y kardex por producto.
- **Compras:** registro de compras a proveedores (actualiza el stock y el costo promedio).
- **Ventas:** historial, anulación y comprobante en PDF.
- **Clientes, categorías y resumen (dashboard)**, con reportes en Excel.

## Diseño

Interfaz en blanco y negro (modo claro y modo oscuro). Las fotos de los productos se muestran con sus colores originales.

## Datos

- `minimarket.db` incluye datos de demostración (productos, clientes y ventas ficticias).
- Para empezar con una base vacía: borra `minimarket.db` y pon `app.seeder.enabled=false` en
  `src/main/resources/application.properties` antes de iniciar.
- Los datos de la empresa que aparecen en los comprobantes se configuran en ese mismo archivo (`app.empresa.*`).
- Las fotos que subes desde la app se guardan reducidas en la carpeta `imagenes-productos/`.
- Los PDF son comprobantes internos; no hay integración con SUNAT.
