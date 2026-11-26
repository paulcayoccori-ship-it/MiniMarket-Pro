package com.ejemplo.crudpersona.controller;

import atlantafx.base.controls.ToggleSwitch;
import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.Compra;
import com.ejemplo.crudpersona.service.InventarioService;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.Proveedor;
import com.ejemplo.crudpersona.model.TipoDocumentoCompra;
import com.ejemplo.crudpersona.service.CalculadoraIgv;
import com.ejemplo.crudpersona.service.CalculadoraIgv.Totales;
import com.ejemplo.crudpersona.service.CompraService;
import com.ejemplo.crudpersona.service.CompraService.LineaCompra;
import com.ejemplo.crudpersona.service.CompraService.NuevaCompra;
import com.ejemplo.crudpersona.service.NumeroALetras;
import com.ejemplo.crudpersona.service.ProductoService;
import com.ejemplo.crudpersona.service.ProveedorService;
import com.ejemplo.crudpersona.ui.Autocompletar;
import com.ejemplo.crudpersona.ui.CeldasCarrito;
import com.ejemplo.crudpersona.ui.Formularios;
import com.ejemplo.crudpersona.ui.Notificador;
import com.ejemplo.crudpersona.ui.Refrescable;
import com.ejemplo.crudpersona.ui.Tablas;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Registro de compras a proveedores: cada linea suma stock al guardar. */
@Component
@RequiredArgsConstructor
public class CompraFormController implements Refrescable {

    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Tablas.LOCALE);
    private static final BigDecimal FACTOR_IGV = BigDecimal.ONE.add(CalculadoraIgv.TASA_IGV);

    private final CompraService compraService;
    private final ProveedorService proveedorService;
    private final ProductoService productoService;
    private final Notificador notificador;
    private final MainController mainController;

    @FXML private VBox raiz;
    @FXML private TextField txtRuc;
    @FXML private TextField txtRazonSocial;
    @FXML private Label lblEstadoProveedor;
    @FXML private ComboBox<TipoDocumentoCompra> cmbTipoDocumento;
    @FXML private TextField txtNumeroDocumento;
    @FXML private DatePicker dpFechaDocumento;

    @FXML private TextField txtProducto;
    @FXML private Spinner<Integer> spnCantidad;
    @FXML private Label lblContadorItems;
    @FXML private TableView<ItemCompra> tablaItems;
    @FXML private TableColumn<ItemCompra, ItemCompra> colItem;
    @FXML private TableColumn<ItemCompra, String> colProducto;
    @FXML private TableColumn<ItemCompra, String> colStock;
    @FXML private TableColumn<ItemCompra, ItemCompra> colCantidad;
    @FXML private TableColumn<ItemCompra, ItemCompra> colCosto;
    @FXML private TableColumn<ItemCompra, BigDecimal> colImporte;
    @FXML private TableColumn<ItemCompra, ItemCompra> colQuitar;

    @FXML private Label lblFecha;
    @FXML private Label lblCodigo;
    @FXML private ToggleSwitch swIgv;
    @FXML private ToggleSwitch swIncluyeIgv;
    @FXML private Label lblModoIgv;
    @FXML private TextField txtObservacion;
    @FXML private Label lblUnidades;
    @FXML private Label lblEtiquetaBase;
    @FXML private Label lblBase;
    @FXML private Label lblEtiquetaIgv;
    @FXML private Label lblIgv;
    @FXML private Label lblTotal;
    @FXML private Label lblEnLetras;
    @FXML private Button btnRegistrar;

    private final ObservableList<ItemCompra> items =
            FXCollections.observableArrayList(i -> new Observable[]{i.cantidad, i.costo});

    private Autocompletar<Proveedor> autoRuc;
    private Autocompletar<Proveedor> autoRazon;
    private Autocompletar<Producto> autoProducto;
    /** Proveedor elegido de las sugerencias; null si se esta escribiendo uno nuevo. */
    private Proveedor proveedor;

    @FXML
    public void initialize() {
        configurarProveedor();
        configurarItems();
        configurarResumen();

        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F9 && !btnRegistrar.isDisabled()) {
                e.consume();
                onRegistrar();
            }
        });
    }

    @Override
    public void refrescar() {
        String hoy = LocalDateTime.now().format(FECHA_LARGA);
        lblFecha.setText(hoy.substring(0, 1).toUpperCase(Locale.ROOT) + hoy.substring(1));
        lblCodigo.setText(compraService.siguienteCodigo());
        if (dpFechaDocumento.getValue() == null) {
            dpFechaDocumento.setValue(LocalDate.now());
        }
        Platform.runLater(() -> (txtRuc.getText().isBlank() ? txtRuc : txtProducto).requestFocus());
    }

    // ------------------------------------------------------------ proveedor

    private void configurarProveedor() {
        Formularios.soloDigitos(txtRuc, 11);
        Formularios.limpiarAlEditar(txtRuc, txtRazonSocial, txtNumeroDocumento);

        autoRuc = new Autocompletar<>(txtRuc, proveedorService::autocompletar, this::seleccionarProveedor)
                .presentacion(Proveedor::getRuc, Proveedor::getRazonSocial, p -> null)
                .icono(p -> new FontIcon(Feather.TRUCK))
                .minimoCaracteres(2);
        autoRazon = new Autocompletar<>(txtRazonSocial, proveedorService::autocompletar, this::seleccionarProveedor)
                .presentacion(Proveedor::getRazonSocial, p -> "RUC " + p.getRuc(), p -> null)
                .icono(p -> new FontIcon(Feather.TRUCK))
                .minimoCaracteres(2);

        // Si el usuario cambia el RUC a mano, deja de ser el proveedor elegido.
        txtRuc.textProperty().addListener((obs, a, b) -> {
            if (proveedor != null && !proveedor.getRuc().equals(b)) {
                proveedor = null;
            }
            actualizarEstadoProveedor();
        });
        txtRuc.setOnAction(e -> buscarRucExacto());
        actualizarEstadoProveedor();

        cmbTipoDocumento.getItems().setAll(TipoDocumentoCompra.values());
        cmbTipoDocumento.setValue(TipoDocumentoCompra.FACTURA);
        dpFechaDocumento.setValue(LocalDate.now());
        dpFechaDocumento.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate fecha, boolean vacio) {
                super.updateItem(fecha, vacio);
                setDisable(vacio || fecha.isAfter(LocalDate.now()));
            }
        });
    }

    private void buscarRucExacto() {
        autoRuc.ocultar();
        proveedorService.buscarPorRuc(txtRuc.getText()).ifPresentOrElse(this::seleccionarProveedor, () -> {
            if (txtRuc.getText().length() == 11) {
                txtRazonSocial.requestFocus();
            }
        });
    }

    private void seleccionarProveedor(Proveedor p) {
        proveedor = p;
        autoRuc.setTexto(p.getRuc());
        autoRazon.setTexto(p.getRazonSocial());
        Formularios.limpiarErrores(txtRuc, txtRazonSocial);
        actualizarEstadoProveedor();
        Platform.runLater(txtNumeroDocumento::requestFocus);
    }

    private void actualizarEstadoProveedor() {
        boolean vacio = txtRuc.getText() == null || txtRuc.getText().isBlank();
        lblEstadoProveedor.setText(proveedor != null ? "Proveedor registrado" : vacio ? "Sin proveedor" : "Proveedor nuevo");
        lblEstadoProveedor.getStyleClass().setAll("chip", proveedor != null ? "exito" : vacio ? "neutro" : "advertencia");
    }

    // ------------------------------------------------------------- productos

    private void configurarItems() {
        spnCantidad.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 1));
        Formularios.soloDigitos(spnCantidad.getEditor(), 4);

        autoProducto = new Autocompletar<>(txtProducto, productoService::autocompletar, this::agregarProducto)
                .presentacion(Producto::getNombre,
                        p -> p.getCodigoProducto() + "  ·  Stock " + InventarioService.texto(p.getStock())
                                + (p.getCostoPromedio() != null ? "  ·  Costo prom. " + Tablas.MONEDA.format(p.getCostoPromedio()) : ""),
                        p -> null)
                .icono(p -> new FontIcon(Feather.PACKAGE))
                .minimoCaracteres(1);

        colItem.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colItem.setCellFactory(CeldasCarrito.numeroFila());
        colProducto.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().producto.getCodigoProducto() + "  " + c.getValue().producto.getNombre()));
        colStock.setCellValueFactory(c -> Bindings.createStringBinding(
                () -> InventarioService.texto(c.getValue().producto.getStock()) + " → "
                        + InventarioService.texto(c.getValue().producto.getStock().add(java.math.BigDecimal.valueOf(c.getValue().cantidad.get()))),
                c.getValue().cantidad));
        colStock.getStyleClass().add("columna-stock");
        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colCantidad.setCellFactory(CeldasCarrito.cantidad(i -> i.cantidad, i -> 9999, i -> "", notificador::advertencia));
        colCosto.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colCosto.setCellFactory(CeldasCarrito.importe(i -> i.costo, notificador::advertencia));
        colImporte.setCellValueFactory(c -> c.getValue().importe);
        colImporte.setCellFactory(Tablas.celdaMoneda());
        colQuitar.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colQuitar.setCellFactory(CeldasCarrito.quitar(items::remove));

        Tablas.configurar(tablaItems, Feather.PACKAGE, "Sin productos",
                "Busca un producto arriba para agregarlo a la compra.", i -> { });
        tablaItems.setItems(items);
        tablaItems.getStyleClass().add("tabla-carrito");
        items.addListener((ListChangeListener<ItemCompra>) c -> recalcular());
    }

    /** Usado desde Inventario ("Reponer"): agrega el producto con la cantidad sugerida. */
    public void agregarDesdeInventario(Producto p, int cantidad) {
        spnCantidad.getValueFactory().setValue(Math.min(9999, Math.max(1, cantidad)));
        agregarProducto(p);
        notificador.info("Se agrego " + p.getNombre() + " a la compra. Completa el proveedor y el documento.");
        Platform.runLater(txtRuc::requestFocus);
    }

    private void agregarProducto(Producto p) {
        int cantidad = spnCantidad.getValue() == null ? 1 : spnCantidad.getValue();
        ItemCompra existente = items.stream().filter(i -> i.producto.getId().equals(p.getId())).findFirst().orElse(null);
        if (existente != null) {
            existente.cantidad.set(Math.min(9999, existente.cantidad.get() + cantidad));
            tablaItems.getSelectionModel().select(existente);
        } else {
            ItemCompra nuevo = new ItemCompra(p, cantidad, costoSugerido(p));
            items.add(nuevo);
            tablaItems.getSelectionModel().select(nuevo);
            tablaItems.scrollTo(nuevo);
        }
        autoProducto.setTexto("");
        spnCantidad.getValueFactory().setValue(1);
        Platform.runLater(txtProducto::requestFocus);
    }

    /**
     * Costo propuesto: el costo promedio actual (sin IGV) expresado en la misma modalidad
     * del documento; si el producto nunca se compro, 70% del precio de venta sin IGV.
     */
    private BigDecimal costoSugerido(Producto p) {
        BigDecimal sinIgv = p.getCostoPromedio() != null
                ? p.getCostoPromedio()
                : p.getPrecio().divide(FACTOR_IGV, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("0.70"));
        BigDecimal costo = swIgv.isSelected() && swIncluyeIgv.isSelected() ? sinIgv.multiply(FACTOR_IGV) : sinIgv;
        return costo.setScale(2, RoundingMode.HALF_UP).max(new BigDecimal("0.01"));
    }

    // --------------------------------------------------------------- resumen

    private void configurarResumen() {
        swIncluyeIgv.disableProperty().bind(swIgv.selectedProperty().not());
        swIgv.selectedProperty().addListener((obs, a, b) -> recalcular());
        swIncluyeIgv.selectedProperty().addListener((obs, a, b) -> recalcular());
        btnRegistrar.disableProperty().bind(Bindings.isEmpty(items));
        recalcular();
    }

    private void recalcular() {
        boolean aplica = swIgv.isSelected();
        boolean incluye = swIncluyeIgv.isSelected();
        Totales t = CalculadoraIgv.calcular(items.stream().map(i -> i.importe.get()).toList(), aplica, incluye);
        int unidades = items.stream().mapToInt(i -> i.cantidad.get()).sum();

        lblUnidades.setText(String.valueOf(unidades));
        lblContadorItems.setText(items.size() == 1 ? "1 item" : items.size() + " items");
        lblEtiquetaBase.setText(aplica ? "Base imponible (Op. gravada)" : "Op. no gravada");
        lblBase.setText(Tablas.MONEDA.format(t.baseImponible()));
        lblEtiquetaIgv.setText(aplica ? "IGV (18%) · credito fiscal" : "IGV (no aplica)");
        lblIgv.setText(Tablas.MONEDA.format(t.igv()));
        lblTotal.setText(Tablas.MONEDA.format(t.total()));
        lblEnLetras.setText("SON: " + NumeroALetras.importe(t.total()));
        lblModoIgv.setText(!aplica ? "Sin IGV: el total es la suma de importes"
                : incluye ? "El IGV se extrae del costo (total ÷ 1.18)"
                : "El IGV (18%) se suma a los costos");
    }

    // -------------------------------------------------------------- acciones

    @FXML
    private void onRegistrar() {
        Formularios.limpiarErrores(txtRuc, txtRazonSocial, txtNumeroDocumento, dpFechaDocumento);
        boolean valido = true;
        if (!txtRuc.getText().matches("(10|15|17|20)\\d{9}")) {
            Formularios.marcarError(txtRuc, "RUC de 11 digitos (inicia en 10, 15, 17 o 20)");
            valido = false;
        }
        if (txtRazonSocial.getText().isBlank()) {
            Formularios.marcarError(txtRazonSocial, "Ingresa la razon social");
            valido = false;
        }
        if (txtNumeroDocumento.getText().isBlank()) {
            Formularios.marcarError(txtNumeroDocumento, "Ingresa la serie y numero del documento");
            valido = false;
        }
        if (dpFechaDocumento.getValue() == null || dpFechaDocumento.getValue().isAfter(LocalDate.now())) {
            Formularios.marcarError(dpFechaDocumento, "Fecha invalida");
            valido = false;
        }
        if (!valido) {
            return;
        }

        List<LineaCompra> lineas = items.stream()
                .map(i -> new LineaCompra(i.producto.getId(), i.cantidad.get(), i.costo.get()))
                .toList();
        try {
            Compra compra = compraService.registrar(new NuevaCompra(
                    txtRuc.getText(), txtRazonSocial.getText(), cmbTipoDocumento.getValue(),
                    txtNumeroDocumento.getText(), dpFechaDocumento.getValue(),
                    swIgv.isSelected(), swIncluyeIgv.isSelected(), txtObservacion.getText(), lineas));
            String unidades = InventarioService.texto(lineas.stream().map(LineaCompra::cantidad)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
            notificador.exitoConAcciones("Compra registrada",
                    "Se registro " + compra.getCodigo() + " (" + compra.getNumeroDocumento() + ") por "
                            + Tablas.MONEDA.format(compra.getTotal()) + ". Ingresaron " + unidades
                            + " unidades al inventario.",
                    "Nueva compra", this::limpiar,
                    "Ver inventario", () -> {
                        limpiar();
                        mainController.irA("inventario-view");
                    });
        } catch (NegocioException e) {
            notificador.advertencia(e.getMessage());
        } catch (Exception e) {
            notificador.error("No se pudo registrar la compra: " + e.getMessage());
        }
    }

    @FXML
    private void onLimpiar() {
        if (items.isEmpty() && txtRuc.getText().isBlank()) {
            limpiar();
            return;
        }
        notificador.confirmar("¿Vaciar la compra en curso?",
                "Se quitaran el proveedor, el documento y los " + items.size() + " productos.",
                "Vaciar", this::limpiar);
    }

    private void limpiar() {
        items.clear();
        proveedor = null;
        autoRuc.setTexto("");
        autoRazon.setTexto("");
        autoProducto.setTexto("");
        txtNumeroDocumento.clear();
        txtObservacion.clear();
        cmbTipoDocumento.setValue(TipoDocumentoCompra.FACTURA);
        dpFechaDocumento.setValue(LocalDate.now());
        swIgv.setSelected(true);
        swIncluyeIgv.setSelected(false);
        Formularios.limpiarErrores(txtRuc, txtRazonSocial, txtNumeroDocumento, dpFechaDocumento);
        actualizarEstadoProveedor();
        refrescar();
    }

    /** Linea de la compra con propiedades observables para editar en la tabla. */
    static final class ItemCompra {
        final Producto producto;
        final IntegerProperty cantidad;
        final ObjectProperty<BigDecimal> costo;
        final ObjectBinding<BigDecimal> importe;

        ItemCompra(Producto producto, int cantidad, BigDecimal costo) {
            this.producto = producto;
            this.cantidad = new SimpleIntegerProperty(cantidad);
            this.costo = new SimpleObjectProperty<>(costo);
            this.importe = Bindings.createObjectBinding(
                    () -> CalculadoraIgv.importe(this.cantidad.get(), this.costo.get()), this.cantidad, this.costo);
        }
    }
}
