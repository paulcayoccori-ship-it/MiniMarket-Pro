package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.MetodoPago;
import com.ejemplo.crudpersona.model.Persona;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.TipoComprobante;
import com.ejemplo.crudpersona.model.Venta;
import com.ejemplo.crudpersona.service.CalculadoraIgv;
import com.ejemplo.crudpersona.service.ComprobantePdfService;
import com.ejemplo.crudpersona.service.ImagenProductoService;
import com.ejemplo.crudpersona.service.InventarioService;
import com.ejemplo.crudpersona.service.PersonaService;
import com.ejemplo.crudpersona.service.ProductoService;
import com.ejemplo.crudpersona.service.VentaService;
import com.ejemplo.crudpersona.service.VentaService.LineaCarrito;
import com.ejemplo.crudpersona.service.VentaService.NuevaVenta;
import com.ejemplo.crudpersona.ui.Autocompletar;
import com.ejemplo.crudpersona.ui.Exportador;
import com.ejemplo.crudpersona.ui.Formularios;
import com.ejemplo.crudpersona.ui.Notificador;
import com.ejemplo.crudpersona.ui.Refrescable;
import com.ejemplo.crudpersona.ui.Tablas;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.Normalizer;
import java.util.*;

/**
 * Punto de venta rapido.
 * <p>Pantalla de venta: categorias, catalogo con fotos (clic para agregar), buscador que
 * tambien recibe el lector de codigo de barras, carrito con teclado numerico (cantidad,
 * descuento y precio) y botones Pagar / Pago rapido.</p>
 * <p>Pantalla de pago: tipo de comprobante, cliente (generico o por DNI), metodo de pago
 * (efectivo, tarjeta, Yape) e importe recibido con calculo de vuelto.</p>
 * <p>Rendimiento: las fotos se decodifican una sola vez, reducidas y en segundo plano; las
 * tarjetas del catalogo se crean al cargar los productos y el filtro solo las muestra u oculta.</p>
 */
@Component
@RequiredArgsConstructor
public class PuntoVentaController implements Refrescable {

    private static final String TODAS = "Todas las categorias";
    private static final String SIN_CATEGORIA = "Sin categoria";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final VentaService ventaService;
    private final PersonaService personaService;
    private final ProductoService productoService;
    private final ComprobantePdfService pdfService;
    private final Exportador exportador;
    private final Notificador notificador;
    private final ProductoFormController formularioProducto;
    private final ImagenProductoService imagenService;

    // ------------------------------------------------------------ vista venta
    @FXML private StackPane raiz;
    @FXML private HBox pantallaVenta;
    @FXML private VBox listaCategorias;
    @FXML private TextField txtBuscar;
    @FXML private ScrollPane scrollCatalogo;
    @FXML private TilePane catalogo;
    @FXML private VBox sinResultados;
    @FXML private Label lblSinResultados;
    @FXML private Label lblClienteVenta;
    @FXML private ListView<ItemCarrito> listaCarrito;
    @FXML private Label lblNroProductos;
    @FXML private Label lblTotal;
    @FXML private ToggleGroup grupoModo;
    @FXML private ToggleButton tglCantidad;
    @FXML private ToggleButton tglDescuento;
    @FXML private ToggleButton tglPrecio;
    @FXML private Label lblEntrada;
    @FXML private GridPane teclado;
    @FXML private Button btnPagar;
    @FXML private Button btnPagoRapido;

    // ------------------------------------------------------------- vista pago
    @FXML private HBox pantallaPago;
    @FXML private Label lblPagoTotal;
    @FXML private ToggleGroup grupoComprobante;
    @FXML private ToggleButton tglNotaVenta;
    @FXML private ToggleButton tglBoleta;
    @FXML private Label lblNumeroComprobante;
    @FXML private TextField txtClientePago;
    @FXML private Button btnCambiarCliente;
    @FXML private VBox panelCliente;
    @FXML private TextField txtDniCliente;
    @FXML private TextField txtNombreCliente;
    @FXML private VBox panelNuevoCliente;
    @FXML private Label lblNuevoCliente;
    @FXML private TextField txtNuevoNombre;
    @FXML private TextField txtNuevoApellido;
    @FXML private Hyperlink lnkResumen;
    @FXML private Hyperlink lnkComentario;
    @FXML private VBox panelResumen;
    @FXML private TextField txtComentario;
    @FXML private ToggleGroup grupoMetodo;
    @FXML private ToggleButton tglEfectivo;
    @FXML private ToggleButton tglTarjeta;
    @FXML private ToggleButton tglYape;
    @FXML private Label lblTituloImporte;
    @FXML private Label lblRecibido;
    @FXML private Label lblVuelto;
    @FXML private FlowPane montosRapidos;
    @FXML private GridPane tecladoPago;
    @FXML private Button btnConfirmarPago;

    // ----------------------------------------------------------------- estado
    /** La lista observa cantidad y precio de cada linea para recalcular el total al editarlas. */
    private final ObservableList<ItemCarrito> carrito =
            FXCollections.observableArrayList(i -> new Observable[]{i.cantidad, i.precio});
    private final ObjectProperty<Persona> cliente = new SimpleObjectProperty<>();

    private List<Producto> productos = List.of();
    /** Tarjeta del catalogo por id de producto (se reutilizan al filtrar). */
    private final Map<Long, Tarjeta> tarjetas = new LinkedHashMap<>();
    /** Fotos ya decodificadas (reducidas) por codigo de producto; null = no tiene foto. */
    private final Map<String, Optional<Image>> fotos = new HashMap<>();
    private final ToggleGroup grupoCategorias = new ToggleGroup();
    private final PauseTransition esperaFiltro = new PauseTransition(Duration.millis(140));
    private String categoriaActual = TODAS;

    /** Lo tecleado en el teclado del carrito (se aplica a la linea seleccionada). */
    private String entrada = "";
    /** Lo tecleado en el teclado de cobro. */
    private String entradaPago = "";

    private Autocompletar<Persona> autoNombre;

    // =================================================================== inicio

    @FXML
    public void initialize() {
        configurarCatalogo();
        configurarCarrito();
        configurarPago();
        configurarAtajos();
        cliente.addListener((o, a, p) -> {
            String nombre = p == null ? "CLIENTE GENERICO" : p.getNombreCompleto().toUpperCase(Tablas.LOCALE);
            lblClienteVenta.setText(nombre);
            txtClientePago.setText(p == null || PersonaService.DNI_GENERICO.equals(p.getDni())
                    ? nombre : nombre + "  ·  DNI " + p.getDni());
        });
    }

    @Override
    public void refrescar() {
        cargarProductos();
        if (cliente.get() == null) {
            usarClienteGenerico();
        }
        mostrarVenta();
    }

    // ================================================================= catalogo

    private void configurarCatalogo() {
        esperaFiltro.setOnFinished(e -> filtrar());
        txtBuscar.textProperty().addListener((o, a, b) -> esperaFiltro.playFromStart());
        // Enter: lo envia el lector de codigo de barras al terminar de "escribir" el codigo.
        txtBuscar.setOnAction(e -> onEnterBuscador());
        grupoCategorias.selectedToggleProperty().addListener((o, anterior, actual) -> {
            if (actual == null) {
                grupoCategorias.selectToggle(anterior);
                return;
            }
            categoriaActual = (String) actual.getUserData();
            filtrar();
            scrollCatalogo.setVvalue(0);
        });
    }

    private void cargarProductos() {
        productos = productoService.catalogoVisual();
        // Las tarjetas existentes se reutilizan (su foto ya esta cargada); solo cambian sus datos.
        Map<Long, Tarjeta> anteriores = new HashMap<>(tarjetas);
        tarjetas.clear();
        for (Producto p : productos) {
            Tarjeta previa = anteriores.get(p.getId());
            Tarjeta t = previa != null && previa.claveFoto.equals(claveFoto(p)) ? previa : new Tarjeta(p);
            t.actualizar(p);
            tarjetas.put(p.getId(), t);
        }
        catalogo.getChildren().setAll(tarjetas.values().stream().map(t -> t.nodo).toList());
        // Las lineas del carrito apuntan al producto con el stock actualizado.
        for (ItemCarrito i : carrito) {
            Tarjeta t = tarjetas.get(i.producto.getId());
            if (t != null) {
                i.producto = t.producto;
            }
        }
        listaCarrito.refresh();
        construirCategorias();
        filtrar();
        marcarEnCarrito();
    }

    private void construirCategorias() {
        SortedSet<String> nombres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean haySinCategoria = false;
        for (Producto p : productos) {
            if (p.getCategoria() == null) {
                haySinCategoria = true;
            } else {
                nombres.add(p.getCategoria().getNombre());
            }
        }
        List<String> opciones = new ArrayList<>();
        opciones.add(TODAS);
        opciones.addAll(nombres);
        if (haySinCategoria) {
            opciones.add(SIN_CATEGORIA);
        }
        if (!opciones.contains(categoriaActual)) {
            categoriaActual = TODAS;
        }
        listaCategorias.getChildren().clear();
        grupoCategorias.getToggles().clear();
        for (String nombre : opciones) {
            ToggleButton b = new ToggleButton(nombre);
            b.setUserData(nombre);
            b.setToggleGroup(grupoCategorias);
            b.getStyleClass().add("pos-categoria");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setWrapText(true);
            b.setFocusTraversable(false);
            listaCategorias.getChildren().add(b);
            if (nombre.equals(categoriaActual)) {
                b.setSelected(true);
            }
        }
    }

    /** Muestra solo las tarjetas que coinciden con la categoria y el texto (sin recrearlas). */
    private void filtrar() {
        String[] palabras = normalizar(txtBuscar.getText()).split("\\s+");
        int visibles = 0;
        for (Tarjeta t : tarjetas.values()) {
            Producto p = t.producto;
            boolean enCategoria = switch (categoriaActual) {
                case TODAS -> true;
                case SIN_CATEGORIA -> p.getCategoria() == null;
                default -> p.getCategoria() != null && categoriaActual.equals(p.getCategoria().getNombre());
            };
            boolean coincide = enCategoria;
            for (String palabra : palabras) {
                if (coincide && !palabra.isEmpty() && !t.clave.contains(palabra)) {
                    coincide = false;
                }
            }
            t.nodo.setVisible(coincide);
            t.nodo.setManaged(coincide);
            if (coincide) {
                visibles++;
            }
        }
        boolean vacio = visibles == 0;
        sinResultados.setVisible(vacio);
        sinResultados.setManaged(vacio);
        lblSinResultados.setText(productos.isEmpty()
                ? "Aun no hay productos. Usa \"Nuevo producto\" para registrar el primero."
                : "No hay productos que coincidan con \"" + txtBuscar.getText().trim() + "\"");
    }

    private void onEnterBuscador() {
        esperaFiltro.stop();
        String texto = txtBuscar.getText().trim();
        if (texto.isEmpty()) {
            return;
        }
        // 1) Codigo exacto (lector de codigo de barras o codigo escrito a mano).
        Optional<Producto> exacto = productos.stream()
                .filter(p -> p.getCodigoProducto().equalsIgnoreCase(texto)).findFirst();
        if (exacto.isEmpty()) {
            filtrar();
            // 2) Un unico resultado por nombre.
            List<Tarjeta> visibles = tarjetas.values().stream().filter(t -> t.nodo.isVisible()).toList();
            if (visibles.size() == 1) {
                exacto = Optional.of(visibles.get(0).producto);
            } else if (visibles.isEmpty()) {
                notificador.advertencia("No se encontro ningun producto con el codigo o nombre \"" + texto + "\".");
                txtBuscar.selectAll();
                return;
            } else {
                notificador.info(visibles.size() + " productos coinciden: toca el que quieras agregar.");
                return;
            }
        }
        agregarProducto(exacto.get());
        txtBuscar.clear();
        filtrar();
    }

    @FXML
    private void onRecargar() {
        cargarProductos();
        notificador.info("Productos y stock actualizados.");
    }

    @FXML
    private void onNuevoProducto() {
        formularioProducto.nuevo(p -> {
            cargarProductos();
            Platform.runLater(txtBuscar::requestFocus);
        });
    }

    /** Identifica la foto de un producto: la subida por el usuario o la de recursos por codigo. */
    private static String claveFoto(Producto p) {
        return p.getImagen() != null ? "archivo:" + p.getImagen() : "recurso:" + p.getCodigoProducto();
    }

    /**
     * Foto del producto (a color): primero la subida por el usuario (carpeta imagenes-productos),
     * si no, /productos/CODIGO.png|jpg. Se decodifica reducida, en segundo plano y una sola vez.
     */
    private Optional<Image> foto(Producto p) {
        return fotos.computeIfAbsent(claveFoto(p), clave -> {
            var archivo = imagenService.ruta(p.getImagen());
            if (archivo.isPresent()) {
                return Optional.of(new Image(archivo.get().toUri().toString(), 280, 200, true, true, true));
            }
            for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
                URL url = getClass().getResource("/productos/" + p.getCodigoProducto() + ext);
                if (url != null) {
                    return Optional.of(new Image(url.toExternalForm(), 280, 200, true, true, true));
                }
            }
            return Optional.empty();
        });
    }

    /** Tarjeta del catalogo: precio arriba, foto con stock y nombre debajo. */
    private final class Tarjeta {
        final VBox nodo = new VBox();
        final Label precio = new Label();
        final Label stock = new Label();
        final Label nombre = new Label();
        final StackPane imagen = new StackPane();
        final String claveFoto;
        Producto producto;
        String clave = "";

        Tarjeta(Producto p) {
            nodo.getStyleClass().add("pos-producto");
            precio.getStyleClass().add("pos-precio");
            stock.getStyleClass().add("pos-stock");
            nombre.getStyleClass().add("pos-producto-nombre");
            nombre.setWrapText(true);
            imagen.getStyleClass().add("pos-producto-imagen");

            claveFoto = claveFoto(p);
            Optional<Image> img = foto(p);
            if (img.isPresent()) {
                ImageView vista = new ImageView(img.get());
                vista.setFitWidth(140);
                vista.setFitHeight(112);
                vista.setPreserveRatio(true);
                vista.setSmooth(true);
                imagen.getChildren().add(vista);
            } else {
                FontIcon icono = new FontIcon(Boolean.TRUE.equals(p.getGranel()) ? Feather.SHOPPING_BAG : Feather.PACKAGE);
                icono.getStyleClass().add("pos-producto-icono");
                imagen.getChildren().add(icono);
            }
            StackPane.setAlignment(stock, Pos.BOTTOM_CENTER);
            imagen.getChildren().add(stock);

            nodo.getChildren().addAll(precio, imagen, nombre);
            nodo.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    agregarProducto(producto);
                }
            });
        }

        void actualizar(Producto p) {
            producto = p;
            clave = normalizar(p.getNombre() + " " + p.getCodigoProducto()
                    + (p.getCategoria() == null ? "" : " " + p.getCategoria().getNombre()));
            precio.setText(Tablas.MONEDA.format(p.getPrecio()) + " x " + p.getAbreviaturaUnidad());
            stock.setText(p.isSinStock() ? "Sin stock"
                    : "Stock: " + InventarioService.texto(p.getStock()) + (p.isVentaFraccionada() ? " " + p.getAbreviaturaUnidad() : ""));
            nombre.setText(p.getNombre());
            nodo.getStyleClass().remove("sin-stock");
            if (p.isSinStock()) {
                nodo.getStyleClass().add("sin-stock");
            }
        }
    }

    // ================================================================== carrito

    private void configurarCarrito() {
        listaCarrito.setItems(carrito);
        listaCarrito.setCellFactory(lv -> new CeldaCarrito());
        VBox vacio = new VBox(8, new FontIcon(Feather.SHOPPING_CART),
                new Label("Carrito vacio"),
                new Label("Toca un producto o escanea su codigo"));
        vacio.getStyleClass().add("pos-carrito-vacio");
        listaCarrito.setPlaceholder(vacio);

        listaCarrito.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            entrada = "";
            actualizarEntrada();
        });
        grupoModo.selectedToggleProperty().addListener((o, anterior, actual) -> {
            if (actual == null) {
                grupoModo.selectToggle(anterior);
                return;
            }
            entrada = "";
            actualizarEntrada();
        });

        carrito.addListener((ListChangeListener<ItemCarrito>) c -> {
            recalcular();
            marcarEnCarrito();
        });
        btnPagar.disableProperty().bind(Bindings.isEmpty(carrito));
        btnPagoRapido.disableProperty().bind(Bindings.isEmpty(carrito));

        construirTeclado(teclado, new String[][]{
                {"7", "8", "9", "⌫"},
                {"4", "5", "6", "+"},
                {"1", "2", "3", "−"},
                {"0", ".", "00", ""}}, this::teclaCarrito);
        recalcular();
        actualizarEntrada();
    }

    private void agregarProducto(Producto p) {
        if (p.isSinStock()) {
            notificador.advertencia(p.getNombre() + " no tiene stock. Registra una compra para reponerlo.");
            return;
        }
        ItemCarrito existente = carrito.stream()
                .filter(i -> i.producto.getId().equals(p.getId())).findFirst().orElse(null);
        if (existente != null) {
            cambiarCantidad(existente, existente.cantidad.get().add(BigDecimal.ONE));
            seleccionar(existente);
        } else {
            ItemCarrito nuevo = new ItemCarrito(p);
            // Por kilo con menos de 1 kg en stock: se agrega lo que hay.
            nuevo.cantidad.set(BigDecimal.ONE.min(p.getStock()));
            carrito.add(nuevo);
            seleccionar(nuevo);
            if (p.isVentaFraccionada()) {
                notificador.info(p.getNombre() + ": teclea el peso en " + p.getAbreviaturaUnidad()
                        + " (ej. 0.5) con el teclado del carrito.");
            }
        }
    }

    private void seleccionar(ItemCarrito item) {
        listaCarrito.getSelectionModel().select(item);
        listaCarrito.scrollTo(item);
        tglCantidad.setSelected(true);
        entrada = "";
        actualizarEntrada();
    }

    /** Teclado del carrito: aplica lo tecleado a la linea seleccionada segun el modo. */
    private void teclaCarrito(String tecla) {
        ItemCarrito item = listaCarrito.getSelectionModel().getSelectedItem();
        if (item == null) {
            notificador.info("Primero toca una linea del carrito.");
            return;
        }
        switch (tecla) {
            case "+" -> {
                cambiarCantidad(item, item.cantidad.get().add(BigDecimal.ONE));
                entrada = "";
            }
            case "−" -> {
                if (item.cantidad.get().compareTo(BigDecimal.ONE) <= 0) {
                    quitar(item);
                    return;
                }
                cambiarCantidad(item, item.cantidad.get().subtract(BigDecimal.ONE));
                entrada = "";
            }
            case "⌫" -> {
                if (entrada.isEmpty()) {
                    if (tglCantidad.isSelected()) {
                        quitar(item);
                        return;
                    }
                } else {
                    entrada = entrada.substring(0, entrada.length() - 1);
                    aplicarEntrada(item);
                }
            }
            case "." -> {
                boolean cantidadEntera = tglCantidad.isSelected() && !item.producto.isVentaFraccionada();
                if (cantidadEntera || entrada.contains(".")) {
                    return;
                }
                entrada = entrada.isEmpty() ? "0." : entrada + ".";
            }
            default -> {
                int decimales = entrada.contains(".") ? entrada.length() - entrada.indexOf('.') - 1 : 0;
                int maxDecimales = tglCantidad.isSelected() ? 3 : 2;
                if (entrada.length() >= 8 || (entrada.contains(".") && decimales + tecla.length() > maxDecimales)) {
                    return;
                }
                entrada = entrada + tecla;
                aplicarEntrada(item);
            }
        }
        actualizarEntrada();
    }

    private void aplicarEntrada(ItemCarrito item) {
        if (tglCantidad.isSelected()) {
            BigDecimal cantidad = entrada.isEmpty() || entrada.equals("0.") ? BigDecimal.ONE : new BigDecimal(entrada);
            if (cantidad.signum() > 0) {
                cambiarCantidad(item, cantidad);
            }
        } else if (tglDescuento.isSelected()) {
            BigDecimal pct = entrada.isEmpty() ? BigDecimal.ZERO : new BigDecimal(entrada);
            if (pct.compareTo(CIEN) >= 0) {
                notificador.advertencia("El descuento debe ser menor a 100%.");
                entrada = entrada.substring(0, entrada.length() - 1);
                return;
            }
            item.descuento = pct;
            item.precio.set(item.precioBase.multiply(CIEN.subtract(pct))
                    .divide(CIEN, 2, RoundingMode.HALF_UP).max(new BigDecimal("0.01")));
        } else {
            BigDecimal precio = entrada.isEmpty() ? item.precioBase : new BigDecimal(entrada);
            if (precio.signum() > 0) {
                item.descuento = BigDecimal.ZERO;
                item.precio.set(precio.setScale(2, RoundingMode.HALF_UP));
            }
        }
        listaCarrito.refresh();
    }

    private void cambiarCantidad(ItemCarrito item, BigDecimal cantidad) {
        BigDecimal stock = item.producto.getStock();
        if (cantidad.compareTo(stock) > 0) {
            String disponible = InventarioService.texto(stock);
            notificador.advertencia("Solo hay " + disponible + " " + item.producto.getAbreviaturaUnidad()
                    + " de " + item.producto.getNombre() + ".");
            cantidad = stock;
            entrada = disponible;
        }
        item.cantidad.set(cantidad);
        listaCarrito.refresh();
    }

    private void quitar(ItemCarrito item) {
        int indice = carrito.indexOf(item);
        carrito.remove(item);
        entrada = "";
        if (!carrito.isEmpty()) {
            listaCarrito.getSelectionModel().select(Math.min(indice, carrito.size() - 1));
        }
        actualizarEntrada();
    }

    private void actualizarEntrada() {
        ItemCarrito item = listaCarrito.getSelectionModel().getSelectedItem();
        String modo = tglCantidad.isSelected() ? "Cantidad" : tglDescuento.isSelected() ? "Descuento %" : "Precio S/";
        lblEntrada.setText(item == null
                ? "Toca una linea del carrito para editarla con el teclado"
                : modo + ":  " + (entrada.isEmpty() ? "_" : entrada) + "      ·  " + item.producto.getNombre());
    }

    private void recalcular() {
        BigDecimal total = totalCarrito();
        long unidades = carrito.stream().mapToLong(i -> i.producto.isVentaFraccionada() ? 1 : i.cantidad.get().longValue()).sum();
        lblTotal.setText(Tablas.MONEDA.format(total));
        lblNroProductos.setText("Nro de productos: " + unidades);
        if (pantallaPago != null && pantallaPago.isVisible()) {
            actualizarCobro();
        }
    }

    private BigDecimal totalCarrito() {
        return carrito.stream().map(i -> i.importe.get()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Resalta en el catalogo los productos que ya estan en el carrito. */
    private void marcarEnCarrito() {
        Set<Long> enCarrito = new HashSet<>();
        carrito.forEach(i -> enCarrito.add(i.producto.getId()));
        tarjetas.forEach((id, t) -> {
            t.nodo.getStyleClass().remove("en-carrito");
            if (enCarrito.contains(id)) {
                t.nodo.getStyleClass().add("en-carrito");
            }
        });
    }

    @FXML
    private void onVaciar() {
        if (carrito.isEmpty()) {
            return;
        }
        notificador.confirmar("¿Vaciar el carrito?",
                "Se quitaran los " + carrito.size() + " productos de la venta en curso.",
                "Vaciar", this::nuevaVenta);
    }

    /** Linea del carrito: muestra stock, nombre, cantidad x precio e importe. */
    private final class CeldaCarrito extends ListCell<ItemCarrito> {
        private final Label stock = new Label();
        private final Label nombre = new Label();
        private final Label detalle = new Label();
        private final Label importe = new Label();
        private final Button quitar = new Button();
        private final HBox fila;

        CeldaCarrito() {
            stock.getStyleClass().add("pos-linea-stock");
            nombre.getStyleClass().add("pos-linea-nombre");
            detalle.getStyleClass().add("pos-linea-detalle");
            importe.getStyleClass().add("pos-linea-importe");
            quitar.setGraphic(new FontIcon(Feather.X_CIRCLE));
            quitar.getStyleClass().addAll("flat", "button-icon", "pos-linea-quitar");
            quitar.setFocusTraversable(false);
            quitar.setOnAction(e -> {
                if (getItem() != null) {
                    PuntoVentaController.this.quitar(getItem());
                }
            });
            VBox textos = new VBox(1, stock, nombre, detalle);
            HBox.setHgrow(textos, Priority.ALWAYS);
            textos.setMinWidth(0);
            fila = new HBox(10, quitar, textos, importe);
            fila.setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("pos-linea");
        }

        @Override
        protected void updateItem(ItemCarrito item, boolean vacio) {
            super.updateItem(item, vacio);
            if (vacio || item == null) {
                setGraphic(null);
                return;
            }
            String unidad = item.producto.getUnidad() == null ? "UNIDAD" : item.producto.getUnidad().getNombre().toUpperCase(Tablas.LOCALE);
            String abrev = item.producto.getAbreviaturaUnidad();
            stock.setText("Stock: " + InventarioService.texto(item.producto.getStock()) + " (" + unidad + ")");
            nombre.setText(item.producto.getNombre());
            String det = InventarioService.texto(item.cantidad.get()) + " " + abrev + "  -  "
                    + item.precio.get().toPlainString() + "/" + abrev;
            if (item.descuento.signum() > 0) {
                det += "  ·  desc. " + item.descuento.stripTrailingZeros().toPlainString() + "%";
            } else if (item.precio.get().compareTo(item.precioBase) != 0) {
                det += "  ·  precio cambiado";
            }
            detalle.setText(det);
            importe.setText(item.importe.get().toPlainString());
            setGraphic(fila);
        }
    }

    /** Linea del carrito con propiedades observables. */
    static final class ItemCarrito {
        Producto producto;
        final BigDecimal precioBase;
        BigDecimal descuento = BigDecimal.ZERO;
        final ObjectProperty<BigDecimal> cantidad = new SimpleObjectProperty<>(BigDecimal.ONE);
        final ObjectProperty<BigDecimal> precio;
        final ObjectBinding<BigDecimal> importe;

        ItemCarrito(Producto producto) {
            this.producto = producto;
            this.precioBase = producto.getPrecio().setScale(2, RoundingMode.HALF_UP);
            this.precio = new SimpleObjectProperty<>(precioBase);
            this.importe = Bindings.createObjectBinding(
                    () -> CalculadoraIgv.importe(cantidad.get(), precio.get()), cantidad, precio);
        }
    }

    // ===================================================================== pago

    private void configurarPago() {
        grupoComprobante.selectedToggleProperty().addListener((o, anterior, actual) -> {
            if (actual == null) {
                grupoComprobante.selectToggle(anterior);
                return;
            }
            actualizarNumeroComprobante();
        });
        grupoMetodo.selectedToggleProperty().addListener((o, anterior, actual) -> {
            if (actual == null) {
                grupoMetodo.selectToggle(anterior);
                return;
            }
            entradaPago = "";
            actualizarCobro();
        });

        Formularios.soloDigitos(txtDniCliente, 8);
        Formularios.limpiarAlEditar(txtDniCliente, txtNuevoNombre, txtNuevoApellido);
        txtDniCliente.setOnAction(e -> onBuscarDni());
        autoNombre = new Autocompletar<>(txtNombreCliente, personaService::autocompletar, this::elegirCliente)
                .presentacion(Persona::getNombreCompleto, p -> "DNI " + p.getDni(), t -> null)
                .icono(p -> new FontIcon(Feather.USER))
                .minimoCaracteres(2);
        txtNuevoApellido.setOnAction(e -> onRegistrarCliente());

        construirTeclado(tecladoPago, new String[][]{
                {"7", "8", "9", "⌫"},
                {"4", "5", "6", "C"},
                {"1", "2", "3", ""},
                {"0", ".", "00", ""}}, this::teclaPago);
        for (String m : new String[]{"Exacto", "10", "20", "50", "100", "200"}) {
            Button b = new Button(m.equals("Exacto") ? "Exacto" : "S/ " + m);
            b.getStyleClass().add("pos-monto");
            b.setFocusTraversable(false);
            b.setOnAction(e -> {
                entradaPago = m.equals("Exacto") ? totalCarrito().toPlainString() : m;
                actualizarCobro();
            });
            montosRapidos.getChildren().add(b);
        }
    }

    @FXML
    private void onPagar() {
        if (carrito.isEmpty()) {
            return;
        }
        entradaPago = "";
        tglEfectivo.setSelected(true);
        cerrarPanelCliente();
        panelResumen.setVisible(false);
        panelResumen.setManaged(false);
        lnkResumen.setText("Ver resumen de compra");
        actualizarNumeroComprobante();
        lblPagoTotal.setText("Total: " + Tablas.MONEDA.format(totalCarrito()));
        alternar(pantallaPago, pantallaVenta);
        actualizarCobro();
        raiz.requestFocus();
    }

    @FXML
    private void onVolverAVenta() {
        mostrarVenta();
    }

    private void mostrarVenta() {
        alternar(pantallaVenta, pantallaPago);
        Platform.runLater(txtBuscar::requestFocus);
    }

    /** Teclado del cobro: arma el importe recibido. */
    private void teclaPago(String tecla) {
        if (!tglEfectivo.isSelected()) {
            return;
        }
        switch (tecla) {
            case "C" -> entradaPago = "";
            case "⌫" -> entradaPago = entradaPago.isEmpty() ? "" : entradaPago.substring(0, entradaPago.length() - 1);
            case "." -> {
                if (!entradaPago.contains(".")) {
                    entradaPago = entradaPago.isEmpty() ? "0." : entradaPago + ".";
                }
            }
            default -> {
                boolean permitido = entradaPago.contains(".")
                        ? entradaPago.length() - entradaPago.indexOf('.') - 1 + tecla.length() <= 2
                        : entradaPago.length() + tecla.length() <= 7;
                if (permitido) {
                    entradaPago = entradaPago + tecla;
                }
            }
        }
        actualizarCobro();
    }

    private BigDecimal recibido() {
        if (!tglEfectivo.isSelected()) {
            return totalCarrito();
        }
        try {
            return entradaPago.isEmpty() ? BigDecimal.ZERO : new BigDecimal(entradaPago).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private void actualizarCobro() {
        BigDecimal total = totalCarrito();
        BigDecimal recibido = recibido();
        boolean efectivo = tglEfectivo.isSelected();
        lblPagoTotal.setText("Total: " + Tablas.MONEDA.format(total));
        lblTituloImporte.setText(efectivo ? "IMPORTE RECIBIDO"
                : tglTarjeta.isSelected() ? "PAGO CON TARJETA" : "PAGO CON YAPE");
        lblRecibido.setText(Tablas.MONEDA.format(recibido));
        montosRapidos.setDisable(!efectivo);
        tecladoPago.setDisable(!efectivo);

        boolean alcanza = recibido.compareTo(total) >= 0;
        if (!efectivo) {
            lblVuelto.setText("Se cobra el total exacto");
        } else if (recibido.signum() == 0) {
            lblVuelto.setText("Teclea lo que entrega el cliente o usa \"Exacto\"");
        } else if (alcanza) {
            lblVuelto.setText("Vuelto: " + Tablas.MONEDA.format(recibido.subtract(total)));
        } else {
            lblVuelto.setText("Falta: " + Tablas.MONEDA.format(total.subtract(recibido)));
        }
        btnConfirmarPago.setDisable(carrito.isEmpty() || !alcanza);
    }

    private void actualizarNumeroComprobante() {
        lblNumeroComprobante.setText(ventaService.siguienteComprobante(tipoComprobante()));
    }

    private TipoComprobante tipoComprobante() {
        return tglBoleta.isSelected() ? TipoComprobante.BOLETA : TipoComprobante.NOTA_VENTA;
    }

    private MetodoPago metodoPago() {
        return tglTarjeta.isSelected() ? MetodoPago.TARJETA : tglYape.isSelected() ? MetodoPago.YAPE : MetodoPago.EFECTIVO;
    }

    @FXML
    private void onConfirmarPago() {
        if (btnConfirmarPago.isDisabled()) {
            return;
        }
        registrar(tipoComprobante(), metodoPago(), recibido());
    }

    @FXML
    private void onPagoRapido() {
        if (carrito.isEmpty()) {
            return;
        }
        registrar(TipoComprobante.NOTA_VENTA, MetodoPago.EFECTIVO, totalCarrito());
    }

    private void registrar(TipoComprobante tipo, MetodoPago metodo, BigDecimal recibido) {
        if (cliente.get() == null) {
            usarClienteGenerico();
        }
        List<LineaCarrito> lineas = carrito.stream()
                .map(i -> new LineaCarrito(i.producto.getId(), i.cantidad.get(), i.precio.get())).toList();
        String comentario = txtComentario.isVisible() ? txtComentario.getText() : null;
        try {
            Venta venta = ventaService.registrar(new NuevaVenta(tipo, cliente.get().getId(), true, true,
                    comentario, lineas, metodo, recibido));
            mostrarVentaExitosa(venta);
        } catch (NegocioException e) {
            notificador.advertencia(e.getMessage());
        } catch (Exception e) {
            notificador.error("No se pudo registrar la venta: " + e.getMessage());
        }
    }

    private void mostrarVentaExitosa(Venta venta) {
        FontIcon check = new FontIcon(Feather.CHECK);
        StackPane circulo = new StackPane(check);
        circulo.getStyleClass().add("pos-exito-icono");

        Label titulo = new Label("VENTA EXITOSA");
        titulo.getStyleClass().add("pos-exito-titulo");
        Label detalle = new Label(venta.getComprobante() + "  ·  " + venta.getMetodoPago()
                + "\nTotal " + Tablas.MONEDA.format(venta.getTotal())
                + (venta.getMetodoPago() == MetodoPago.EFECTIVO
                ? "   ·   Recibido " + Tablas.MONEDA.format(venta.getMontoRecibido()) : ""));
        detalle.getStyleClass().add("pos-exito-detalle");
        detalle.setWrapText(true);

        VBox contenido = new VBox(14, circulo, titulo, detalle);
        if (venta.getVuelto() != null && venta.getVuelto().signum() > 0) {
            Label vuelto = new Label("Vuelto: " + Tablas.MONEDA.format(venta.getVuelto()));
            vuelto.getStyleClass().add("pos-exito-vuelto");
            contenido.getChildren().add(vuelto);
        }

        Button otra = new Button("Realizar otra venta");
        otra.getStyleClass().addAll("accent", "pos-exito-principal");
        otra.setMaxWidth(Double.MAX_VALUE);
        otra.setDefaultButton(true);
        otra.setOnAction(e -> {
            notificador.cerrarDialogo();
            Platform.runLater(txtBuscar::requestFocus);
        });

        Long id = venta.getId();
        String comprobante = venta.getComprobante();
        Button imprimir = new Button("Imprimir venta", new FontIcon(Feather.PRINTER));
        imprimir.getStyleClass().add("button-outlined");
        imprimir.setMaxWidth(Double.MAX_VALUE);
        imprimir.setOnAction(e -> exportador.abrirTemporal(comprobante, Exportador.Formato.PDF,
                destino -> pdfService.generar(id, destino)));

        Button cerrar = new Button(null, new FontIcon(Feather.X));
        cerrar.getStyleClass().addAll("flat", "button-circle");
        cerrar.setCancelButton(true);
        cerrar.setOnAction(e -> otra.fire());
        HBox barra = new HBox(cerrar);
        barra.setAlignment(Pos.TOP_RIGHT);

        VBox dialogo = new VBox(12, barra, contenido, otra, imprimir);
        contenido.setAlignment(Pos.CENTER);
        dialogo.getStyleClass().addAll("dialogo", "pos-exito");
        dialogo.setMaxSize(420, Region.USE_PREF_SIZE);

        // La venta ya quedo guardada: el carrito se limpia aunque cierren el dialogo de otra forma.
        nuevaVenta();
        notificador.mostrarDialogo(dialogo);
        Platform.runLater(otra::requestFocus);
    }

    /** Deja el punto de venta listo para el siguiente cliente. */
    private void nuevaVenta() {
        carrito.clear();
        entrada = "";
        entradaPago = "";
        txtComentario.clear();
        txtComentario.setVisible(false);
        txtComentario.setManaged(false);
        tglNotaVenta.setSelected(true);
        usarClienteGenerico();
        cargarProductos();
        actualizarEntrada();
        mostrarVenta();
    }

    // ------------------------------------------------------------------ cliente

    private void usarClienteGenerico() {
        cliente.set(personaService.clienteGenerico());
    }

    @FXML
    private void onCambiarCliente() {
        boolean abrir = !panelCliente.isVisible();
        if (abrir) {
            panelCliente.setVisible(true);
            panelCliente.setManaged(true);
            panelNuevoCliente.setVisible(false);
            panelNuevoCliente.setManaged(false);
            txtDniCliente.clear();
            autoNombre.setTexto("");
            btnCambiarCliente.setText("Cerrar");
            Platform.runLater(txtDniCliente::requestFocus);
        } else {
            cerrarPanelCliente();
        }
    }

    private void cerrarPanelCliente() {
        panelCliente.setVisible(false);
        panelCliente.setManaged(false);
        btnCambiarCliente.setText("Cambiar");
        Formularios.limpiarErrores(txtDniCliente, txtNuevoNombre, txtNuevoApellido);
    }

    @FXML
    private void onBuscarDni() {
        String dni = txtDniCliente.getText().trim();
        if (dni.length() != 8) {
            Formularios.marcarError(txtDniCliente, "Ingresa los 8 digitos del DNI");
            return;
        }
        Optional<Persona> encontrado = personaService.buscarPorDni(dni);
        if (encontrado.isPresent()) {
            elegirCliente(encontrado.get());
            return;
        }
        // No registrado: se piden sus nombres para darlo de alta en el momento.
        lblNuevoCliente.setText("El DNI " + dni + " no esta registrado. Escribe sus nombres y apellidos para registrarlo.");
        txtNuevoNombre.clear();
        txtNuevoApellido.clear();
        panelNuevoCliente.setVisible(true);
        panelNuevoCliente.setManaged(true);
        txtNuevoNombre.requestFocus();
    }

    @FXML
    private void onRegistrarCliente() {
        String dni = txtDniCliente.getText().trim();
        if (dni.length() != 8) {
            Formularios.marcarError(txtDniCliente, "Ingresa los 8 digitos del DNI");
            return;
        }
        if (txtNuevoNombre.getText().isBlank()) {
            Formularios.marcarError(txtNuevoNombre, "Escribe los nombres");
            return;
        }
        if (txtNuevoApellido.getText().isBlank()) {
            Formularios.marcarError(txtNuevoApellido, "Escribe los apellidos");
            return;
        }
        try {
            Persona nueva = personaService.guardar(Persona.builder()
                    .dni(dni)
                    .nombre(txtNuevoNombre.getText().trim().toUpperCase(Tablas.LOCALE))
                    .apellido(txtNuevoApellido.getText().trim().toUpperCase(Tablas.LOCALE))
                    .build());
            notificador.exito("Cliente " + nueva.getNombreCompleto() + " registrado.");
            elegirCliente(nueva);
        } catch (NegocioException e) {
            Formularios.marcarError(txtDniCliente, e.getMessage());
        } catch (Exception e) {
            notificador.error("No se pudo registrar el cliente: " + e.getMessage());
        }
    }

    @FXML
    private void onClienteGenerico() {
        usarClienteGenerico();
        cerrarPanelCliente();
    }

    private void elegirCliente(Persona p) {
        cliente.set(p);
        cerrarPanelCliente();
        raiz.requestFocus();
    }

    @FXML
    private void onVerResumen() {
        boolean mostrar = !panelResumen.isVisible();
        panelResumen.getChildren().clear();
        if (mostrar) {
            for (ItemCarrito i : carrito) {
                Label nombre = new Label(InventarioService.texto(i.cantidad.get()) + " " + i.producto.getAbreviaturaUnidad()
                        + " x " + i.producto.getNombre());
                nombre.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(nombre, Priority.ALWAYS);
                Label importe = new Label(Tablas.MONEDA.format(i.importe.get()));
                HBox fila = new HBox(10, nombre, importe);
                fila.getStyleClass().add("pos-resumen-fila");
                panelResumen.getChildren().add(fila);
            }
        }
        panelResumen.setVisible(mostrar);
        panelResumen.setManaged(mostrar);
        lnkResumen.setText(mostrar ? "Ocultar resumen de compra" : "Ver resumen de compra");
    }

    @FXML
    private void onAnadirComentario() {
        boolean mostrar = !txtComentario.isVisible();
        txtComentario.setVisible(mostrar);
        txtComentario.setManaged(mostrar);
        if (mostrar) {
            txtComentario.requestFocus();
        }
    }

    // =================================================================== atajos

    private void configurarAtajos() {
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            boolean enVenta = pantallaVenta.isVisible();
            switch (e.getCode()) {
                case F4 -> {
                    if (enVenta) {
                        txtBuscar.requestFocus();
                        txtBuscar.selectAll();
                        e.consume();
                    }
                }
                case F9 -> {
                    if (enVenta) {
                        onPagar();
                    } else {
                        onConfirmarPago();
                    }
                    e.consume();
                }
                case F10 -> {
                    if (enVenta) {
                        onPagoRapido();
                        e.consume();
                    }
                }
                case ESCAPE -> {
                    if (!enVenta && !(enfocado() instanceof TextInputControl)) {
                        onVolverAVenta();
                        e.consume();
                    }
                }
                case ENTER -> {
                    if (!enVenta && !(enfocado() instanceof TextInputControl)) {
                        onConfirmarPago();
                        e.consume();
                    }
                }
                case BACK_SPACE -> {
                    if (!enVenta && !(enfocado() instanceof TextInputControl)) {
                        teclaPago("⌫");
                        e.consume();
                    }
                }
                default -> { }
            }
        });
        // Teclado fisico: en la venta, cualquier tecla (p. ej. el lector de codigos) va al buscador;
        // en el cobro, los numeros arman el importe recibido.
        raiz.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            String c = e.getCharacter();
            if (c == null || c.isEmpty() || Character.isISOControl(c.charAt(0))
                    || enfocado() instanceof TextInputControl) {
                return;
            }
            if (pantallaVenta.isVisible()) {
                txtBuscar.requestFocus();
                txtBuscar.appendText(c);
                txtBuscar.positionCaret(txtBuscar.getText().length());
                e.consume();
            } else if (c.matches("[0-9.]")) {
                teclaPago(c);
                e.consume();
            }
        });
    }

    private Node enfocado() {
        return raiz.getScene() == null ? null : raiz.getScene().getFocusOwner();
    }

    // ==================================================================== apoyo

    private static void construirTeclado(GridPane grid, String[][] teclas, java.util.function.Consumer<String> accion) {
        grid.getChildren().clear();
        for (int fila = 0; fila < teclas.length; fila++) {
            for (int col = 0; col < teclas[fila].length; col++) {
                String t = teclas[fila][col];
                if (t.isEmpty()) {
                    continue;
                }
                Button b = new Button();
                switch (t) {
                    case "⌫" -> b.setGraphic(new FontIcon(Feather.DELETE));
                    case "+" -> b.setGraphic(new FontIcon(Feather.PLUS));
                    case "−" -> b.setGraphic(new FontIcon(Feather.MINUS));
                    default -> b.setText(t);
                }
                b.getStyleClass().add("pos-tecla");
                if (!t.matches("[0-9.]+")) {
                    b.getStyleClass().add("pos-tecla-funcion");
                }
                b.setFocusTraversable(false);
                b.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                GridPane.setHgrow(b, Priority.ALWAYS);
                GridPane.setVgrow(b, Priority.ALWAYS);
                b.setOnAction(e -> accion.accept(t));
                grid.add(b, col, fila);
            }
        }
    }

    /** Minusculas y sin tildes, para que "pina" encuentre "Piña". */
    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.toLowerCase(Tablas.LOCALE), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim();
    }

    private static void alternar(Node mostrar, Node ocultar) {
        mostrar.setVisible(true);
        mostrar.setManaged(true);
        ocultar.setVisible(false);
        ocultar.setManaged(false);
    }
}
