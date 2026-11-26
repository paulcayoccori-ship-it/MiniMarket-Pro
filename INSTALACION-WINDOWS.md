# Guía de instalación de MiniMarket Pro en Windows

Esta guía explica cómo descargar MiniMarket Pro desde GitHub e instalarlo en una PC con
**Windows 10 u 11**. No necesitas saber programar: solo sigue los pasos en orden.

**Tiempo aproximado:** 20 a 30 minutos, la mayor parte esperando descargas.

---

## Qué vas a instalar

| Programa | Para qué sirve | ¿Se paga? |
|---|---|---|
| **Java (JDK 17)** | Es el motor que ejecuta el sistema | Gratis |
| **Git** | Descarga el proyecto desde GitHub y lo actualiza | Gratis |

No necesitas instalar Maven ni una base de datos: el proyecto ya trae todo lo demás.

**Requisitos de la PC**
- Windows 10 u 11 de 64 bits.
- 4 GB de RAM como mínimo (8 GB recomendado).
- 1 GB libre en el disco.
- Internet **solo durante la instalación** y la primera vez que abres el sistema. Después funciona sin internet.

---

## Paso 1. Instalar Java (JDK 17)

1. Entra a **https://adoptium.net/es/temurin/releases/?version=17**
2. Elige:
   - Sistema operativo: **Windows**
   - Arquitectura: **x64**
   - Tipo de paquete: **JDK**
   - Versión: **17**
3. Descarga el archivo **.msi** y ábrelo.
4. En el instalador, en la pantalla **"Custom Setup"**, activa estas dos opciones haciendo clic en el ícono de cada una y eligiendo
   **"Will be installed on local hard drive"**:
   - **Add to PATH**
   - **Set JAVA_HOME variable** ← **muy importante**
5. Continúa con **Next** → **Install** → **Finish**.

**Comprobar que quedó bien:**

1. Abre el menú Inicio, escribe **cmd** y abre **Símbolo del sistema**.
2. Escribe este comando y presiona Enter:

```bash
java -version
```

Debe aparecer algo como `openjdk version "17.0.x"`. Si dice *"java no se reconoce como un comando"*, revisa la
sección [Problemas frecuentes](#problemas-frecuentes).

> También sirve **Amazon Corretto 17** (https://aws.amazon.com/es/corretto/), que es el que se usó para desarrollar el sistema.

---

## Paso 2. Instalar Git

1. Entra a **https://git-scm.com/download/win** y descarga **"64-bit Git for Windows Setup"**.
2. Ábrelo y presiona **Next** en todas las pantallas, sin cambiar nada. Las opciones por defecto están bien.
3. Al terminar, **cierra y vuelve a abrir** el Símbolo del sistema.

**Comprobar:**

```bash
git --version
```

Debe mostrar algo como `git version 2.x`.

---

## Paso 3. Descargar (clonar) el proyecto desde GitHub

Vamos a guardar el sistema en `C:\MiniMarket`. Es mejor una ruta corta, sin espacios ni tildes.

En el Símbolo del sistema, escribe estos comandos **uno por uno**, presionando Enter después de cada uno:

```bash
cd C:\
```

```bash
git clone https://github.com/paulcayoccori-ship-it/MiniMarket-Pro.git MiniMarket
```

```bash
cd C:\MiniMarket
```

Al terminar tendrás la carpeta **C:\MiniMarket** con todo el sistema.

Por último, ejecuta este comando. Le dice a Git que **nunca reemplace tu base de datos** con la de GitHub cuando
actualices el sistema; así no pierdes tus ventas ni tus productos:

```bash
git update-index --skip-worktree minimarket.db
```

---

## Paso 4. Abrir el sistema por primera vez

En el mismo Símbolo del sistema, dentro de `C:\MiniMarket`, escribe:

```bash
mvnw.cmd javafx:run
```

- **La primera vez tarda entre 5 y 15 minutos**, según tu internet, porque descarga las librerías del sistema
  (unos 150 MB). Verás muchas líneas de texto: es normal.
- Cuando termine, se abre la ventana de **MiniMarket Pro**.
- Desde la segunda vez abre en unos 10 a 20 segundos y ya no necesita internet.

> **No cierres la ventana negra** del Símbolo del sistema mientras uses MiniMarket Pro: si la cierras, el sistema también se cierra.

---

## Paso 5. Crear un acceso directo en el Escritorio

Para no escribir comandos cada vez:

1. Abre la carpeta **C:\MiniMarket** en el Explorador de archivos.
2. Busca el archivo **`iniciar-minimarket.bat`**.
3. Haz clic derecho sobre él → **Mostrar más opciones** (en Windows 11) → **Enviar a** → **Escritorio (crear acceso directo)**.
4. Opcional: en el Escritorio, haz clic derecho en el acceso directo → **Cambiar nombre** → escribe **MiniMarket Pro**.

Desde ahora, abres el sistema con **doble clic** en ese acceso directo.

---

## Paso 6. Conectar el lector de código de barras

- Conecta el lector por USB. **No necesita drivers**: Windows lo reconoce como si fuera un teclado.
- En **Nueva venta**, escanea un producto: el código aparece en el buscador y el producto se agrega al carrito solo.
- Para registrar productos nuevos: **Productos → Nuevo producto → Con código de barras**, haz clic en el campo del código y escanéalo.

**Prueba rápida del lector:** abre el Bloc de notas y escanea cualquier producto. Si aparecen los números y el cursor
baja a la línea siguiente, el lector está bien configurado.

---

## Antes de usarlo en tu negocio

El sistema viene con **datos de demostración**: productos, clientes y ventas ficticios. Para empezar con tus datos reales:

1. Cierra MiniMarket Pro.
2. En **C:\MiniMarket**, borra el archivo **`minimarket.db`**.
3. Abre con el Bloc de notas el archivo `C:\MiniMarket\src\main\resources\application.properties` y cambia:
   - `app.seeder.enabled=true` por `app.seeder.enabled=false` (para que no vuelva a cargar datos de prueba).
   - Los datos de tu negocio, que se imprimen en los comprobantes:

```properties
app.empresa.razon-social=NOMBRE DE TU NEGOCIO
app.empresa.nombre-comercial=Tu Minimarket
app.empresa.ruc=10XXXXXXXXX
app.empresa.direccion=Tu direccion, Juliaca
app.empresa.telefono=9XXXXXXXX
app.empresa.email=
```

4. Guarda el archivo y vuelve a abrir MiniMarket Pro. Se creará una base de datos nueva y vacía.

---

## Copia de seguridad (muy recomendado)

Toda la información de tu negocio está en **dos lugares** dentro de `C:\MiniMarket`:

- **`minimarket.db`**: productos, clientes, ventas, compras e inventario.
- La carpeta **`imagenes-productos`**: las fotos que subiste.

**Una vez por semana**, con el sistema cerrado, copia ambos a una memoria USB o a Google Drive.
Para recuperar la información, vuelve a pegarlos en `C:\MiniMarket`.

---

## Actualizar el sistema cuando haya una versión nueva

Cuando se suban mejoras a GitHub:

1. Cierra MiniMarket Pro.
2. Haz una copia de seguridad (ver la sección anterior).
3. Abre el Símbolo del sistema y escribe:

```bash
cd C:\MiniMarket
```

```bash
git pull
```

4. Abre el sistema como siempre.

> Si `git pull` muestra un error sobre `application.properties` (porque cambiaste los datos de tu negocio):
> anota tus datos, ejecuta `git checkout -- src/main/resources/application.properties`, vuelve a ejecutar
> `git pull` y escribe otra vez tus datos en ese archivo.

---

## Atajos de teclado en Nueva venta

| Tecla | Acción |
|---|---|
| **F4** | Ir al buscador |
| **F9** | Pagar (en la pantalla de pago: confirmar) |
| **F10** | Pago rápido (efectivo, monto exacto) |
| **Esc** | Volver de la pantalla de pago al carrito |
| **Enter** | En la pantalla de pago: confirmar el pago |

---

## Problemas frecuentes

**"java no se reconoce como un comando interno o externo"** o **"JAVA_HOME not found"**

Java no quedó registrado en Windows. Repite el Paso 1 y activa **Add to PATH** y **Set JAVA_HOME variable**.
Después **cierra y vuelve a abrir** el Símbolo del sistema. Si sigue fallando, reinicia la PC.

**"git no se reconoce como un comando"**

Cierra y vuelve a abrir el Símbolo del sistema. Si sigue igual, reinstala Git (Paso 2) y reinicia la PC.

**La primera vez se queda mucho tiempo descargando o sale "Could not resolve dependencies"**

Revisa tu conexión a internet y vuelve a ejecutar `mvnw.cmd javafx:run`. Retoma la descarga donde se quedó.

**Windows muestra "Windows protegió su PC" al abrir el .bat**

Haz clic en **Más información** → **Ejecutar de todas formas**. Pasa porque el archivo no tiene firma digital.

**El antivirus o el Firewall pregunta por "Java(TM) Platform SE binary"**

Puedes permitirlo. MiniMarket Pro no usa internet una vez instalado.

**El lector escanea pero no agrega el producto**

- Verifica que el producto esté registrado con ese mismo código (**Productos → buscar el código**).
- Verifica que tenga stock. Los productos "Sin stock" no se pueden vender.
- Prueba el lector en el Bloc de notas (Paso 6): debe escribir el número y pasar a la línea siguiente (Enter).

**La ventana se ve muy pequeña o muy grande**

En Windows: **Configuración → Sistema → Pantalla → Escala**. Con 100 % o 125 % se ve bien.

**Se cerró el sistema y no abre de nuevo**

Abre el Símbolo del sistema y ejecuta:

```bash
cd C:\MiniMarket
```

```bash
mvnw.cmd javafx:run
```

Lee las últimas líneas de texto: ahí aparece el motivo del error.
